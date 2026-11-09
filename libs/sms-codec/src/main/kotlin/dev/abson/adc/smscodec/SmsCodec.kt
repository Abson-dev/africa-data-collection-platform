package dev.abson.adc.smscodec

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encodes an [SmsRecord] into encrypted, GSM-7-safe SMS frames and back.
 *
 * Binary payload (before Base64), protocol version 1:
 *
 * | bytes | field                                         |
 * |-------|-----------------------------------------------|
 * | 1     | protocol version                              |
 * | 2     | key ID                                        |
 * | 4     | form key                                      |
 * | 16    | instanceID (UUID)                             |
 * | 12    | AES-GCM nonce                                 |
 * | n     | ciphertext of: flag byte (0 raw, 1 deflate) + values |
 * | 16    | AES-GCM tag                                   |
 *
 * The first 23 bytes (the header) are authenticated as AES-GCM associated data,
 * so changing the form key or instanceID makes decryption fail.
 */
object SmsCodec {
    const val PROTOCOL_VERSION = 1
    const val KEY_BYTES = 32

    private const val HEADER_BYTES = 1 + 2 + 4 + 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8

    /** Fixed bytes per message: header + nonce + tag (51). */
    const val OVERHEAD_BYTES = HEADER_BYTES + NONCE_BYTES + TAG_BYTES

    private const val FLAG_RAW: Byte = 0
    private const val FLAG_DEFLATE: Byte = 1
    private const val MAX_INFLATED_BYTES = 8 * 1024

    private val b64Encoder = Base64.getEncoder().withoutPadding()
    private val b64Decoder = Base64.getDecoder()

    /**
     * @param key 32-byte AES-256 key for [SmsRecord.keyId].
     * @throws SmsCodecException if the record does not fit in [mode]'s SMS budget.
     */
    fun encode(
        record: SmsRecord,
        key: ByteArray,
        mode: SmsMode = SmsMode.SEPARATE,
        random: SecureRandom = SecureRandom(),
    ): List<SmsFrame> {
        requireKey(key)
        val header = header(record)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, key, nonce, header, plaintext(record.values))

        val payload = header + nonce + ciphertext
        if (payload.size > mode.maxPayloadBytes) {
            throw SmsCodecException(
                "Record needs ${payload.size} bytes but $mode SMS hold at most ${mode.maxPayloadBytes}. " +
                    "Mark fewer fields for SMS or shorten their values.",
            )
        }

        val text = b64Encoder.encodeToString(payload)
        val chunkChars = mode.maxFrameChars - SmsFrame.HEADER_CHARS
        val chunks = text.chunked(chunkChars)
        val ref = b64Encoder.encodeToString(ByteArray(3).also(random::nextBytes))
        return chunks.mapIndexed { i, chunk -> SmsFrame(ref, i + 1, chunks.size, chunk) }
    }

    /**
     * @param frames all frames of one message, in any order (duplicates of the same part are ignored).
     * @param keyFor returns the 32-byte key for a key ID, or null if unknown or revoked.
     */
    fun decode(frames: List<SmsFrame>, keyFor: (Int) -> ByteArray?): SmsRecord {
        val payload = reassemble(frames)
        if (payload.size < OVERHEAD_BYTES + 1) throw SmsCodecException("Payload is too short")

        val buf = ByteBuffer.wrap(payload)
        val version = buf.get().toInt() and 0xFF
        if (version != PROTOCOL_VERSION) throw SmsCodecException("Unsupported protocol version $version")
        val keyId = buf.short.toInt() and 0xFFFF
        val formKey = buf.int
        val instanceId = UUID(buf.long, buf.long)

        val key = keyFor(keyId) ?: throw SmsCodecException("Unknown or revoked key $keyId")
        requireKey(key)

        val header = payload.copyOfRange(0, HEADER_BYTES)
        val nonce = payload.copyOfRange(HEADER_BYTES, HEADER_BYTES + NONCE_BYTES)
        val ciphertext = payload.copyOfRange(HEADER_BYTES + NONCE_BYTES, payload.size)
        val plain = try {
            aesGcm(Cipher.DECRYPT_MODE, key, nonce, header, ciphertext)
        } catch (e: GeneralSecurityException) {
            throw SmsCodecException("Message failed authentication (wrong key or altered text)", e)
        }

        return SmsRecord(keyId, formKey, instanceId, values(plain))
    }

    private fun reassemble(frames: List<SmsFrame>): ByteArray {
        if (frames.isEmpty()) throw SmsCodecException("No frames")
        val ref = frames.first().ref
        val total = frames.first().total
        if (frames.any { it.ref != ref }) throw SmsCodecException("Frames belong to different messages")
        if (frames.any { it.total != total }) throw SmsCodecException("Frames disagree on the number of parts")

        val byIndex = frames.groupBy { it.index }
        for ((index, copies) in byIndex) {
            if (copies.map { it.chunk }.distinct().size > 1) {
                throw SmsCodecException("Conflicting copies of part $index")
            }
        }
        val missing = (1..total).filterNot { it in byIndex }
        if (missing.isNotEmpty()) throw SmsCodecException("Missing parts: $missing")

        val text = (1..total).joinToString("") { byIndex.getValue(it).first().chunk }
        return try {
            b64Decoder.decode(text)
        } catch (e: IllegalArgumentException) {
            throw SmsCodecException("Invalid Base64 in message", e)
        }
    }

    private fun header(record: SmsRecord): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES)
            .put(PROTOCOL_VERSION.toByte())
            .putShort(record.keyId.toShort())
            .putInt(record.formKey)
            .putLong(record.instanceId.mostSignificantBits)
            .putLong(record.instanceId.leastSignificantBits)
            .array()

    /** Flag byte + values, deflated only when that is actually smaller. */
    private fun plaintext(values: List<String?>): ByteArray {
        val raw = ValueEncoding.encode(values)
        val deflated = deflate(raw)
        return if (deflated.size < raw.size) byteArrayOf(FLAG_DEFLATE) + deflated else byteArrayOf(FLAG_RAW) + raw
    }

    private fun values(plain: ByteArray): List<String?> {
        if (plain.isEmpty()) throw SmsCodecException("Empty plaintext")
        val body = plain.copyOfRange(1, plain.size)
        return when (plain[0]) {
            FLAG_RAW -> ValueEncoding.decode(body)
            FLAG_DEFLATE -> ValueEncoding.decode(inflate(body))
            else -> throw SmsCodecException("Unknown compression flag ${plain[0]}")
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(512)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(512)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw SmsCodecException("Compressed data is truncated")
                }
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED_BYTES) throw SmsCodecException("Decompressed data too large")
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw SmsCodecException("Corrupt compressed data", e)
        } finally {
            inflater.end()
        }
    }

    private fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    private fun requireKey(key: ByteArray) {
        if (key.size != KEY_BYTES) throw SmsCodecException("Key must be $KEY_BYTES bytes, was ${key.size}")
    }
}
