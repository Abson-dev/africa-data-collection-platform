package dev.abson.adc.smscodec

import java.io.ByteArrayOutputStream

/**
 * Compact list-of-strings encoding: varint(count), then per value varint(byteLength + 1) + UTF-8 bytes.
 * A length of 0 means null (unanswered). XForm values are all text, so strings are enough.
 */
internal object ValueEncoding {
    private const val MAX_VALUES = 1000

    fun encode(values: List<String?>): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarint(out, values.size)
        for (value in values) {
            if (value == null) {
                writeVarint(out, 0)
            } else {
                val bytes = value.toByteArray(Charsets.UTF_8)
                writeVarint(out, bytes.size + 1)
                out.write(bytes)
            }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<String?> {
        var pos = 0

        fun readVarint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                if (pos >= bytes.size) throw SmsCodecException("Value data is truncated")
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 28) throw SmsCodecException("Malformed length in value data")
            }
        }

        val count = readVarint()
        if (count > MAX_VALUES) throw SmsCodecException("Too many values: $count")
        val values = ArrayList<String?>(count)
        repeat(count) {
            val len = readVarint()
            if (len == 0) {
                values.add(null)
            } else {
                val n = len - 1
                if (n > bytes.size - pos) throw SmsCodecException("Value data is truncated")
                values.add(String(bytes, pos, n, Charsets.UTF_8))
                pos += n
            }
        }
        if (pos != bytes.size) throw SmsCodecException("Unexpected bytes after values")
        return values
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v >= 0x80) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }
}
