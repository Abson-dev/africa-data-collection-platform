package dev.abson.adc.smscodec

/**
 * How the phone will send the message.
 *
 * - [SEPARATE]: up to 3 independent SMS of ≤ 160 GSM-7 characters each (managed devices, auto-send).
 * - [CONCATENATED]: one body of ≤ 459 characters, which the SMS app splits into 3 segments
 *   (Play Store build: pre-filled SMS the enumerator sends with one tap).
 */
enum class SmsMode(val maxFrameChars: Int, val maxFrames: Int) {
    SEPARATE(160, 3),
    CONCATENATED(459, 1),
    ;

    /** Base64 characters available for payload across all frames. */
    val payloadChars: Int get() = (maxFrameChars - SmsFrame.HEADER_CHARS) * maxFrames

    /** Maximum payload bytes (before Base64) for this mode. */
    val maxPayloadBytes: Int get() = payloadChars * 6 / 8
}

/**
 * One SMS-sized piece of an encoded record.
 *
 * Text form: `#A` + index (1 digit) + total (1 digit) + ref (4 chars) + Base64 chunk.
 * Example: `#A12Xk9q...` is part 1 of 2 of message `Xk9q`.
 * Every character is in the GSM-7 default alphabet, so each SMS holds the full 160 characters.
 */
data class SmsFrame(val ref: String, val index: Int, val total: Int, val chunk: String) {
    init {
        require(ref.length == REF_CHARS) { "ref must be $REF_CHARS characters" }
        require(total in 1..9 && index in 1..total) { "invalid part $index of $total" }
    }

    fun toText(): String = "$MARKER$index$total$ref$chunk"

    companion object {
        const val MARKER = "#A"
        const val REF_CHARS = 4
        const val HEADER_CHARS = 2 + 1 + 1 + REF_CHARS

        private val BASE64 = Regex("^[A-Za-z0-9+/]+$")

        /**
         * Parse every frame in an inbound SMS text. Frames are separated by whitespace, so this
         * handles one-frame-per-SMS and a single concatenated body alike.
         * @throws SmsCodecException if the text has no frames or any token is not a valid frame.
         */
        fun parseAll(text: String): List<SmsFrame> {
            val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) throw SmsCodecException("Empty SMS")
            return tokens.map(::parse)
        }

        fun parse(token: String): SmsFrame {
            if (!token.startsWith(MARKER) || token.length <= HEADER_CHARS) {
                throw SmsCodecException("Not a data frame")
            }
            val index = token[2].digitToIntOrNull()
            val total = token[3].digitToIntOrNull()
            if (index == null || total == null || total !in 1..9 || index !in 1..total) {
                throw SmsCodecException("Invalid part numbers in frame")
            }
            val ref = token.substring(4, HEADER_CHARS)
            val chunk = token.substring(HEADER_CHARS)
            if (!BASE64.matches(ref) || !BASE64.matches(chunk)) {
                throw SmsCodecException("Frame contains invalid characters")
            }
            if (token.length > SmsMode.CONCATENATED.maxFrameChars) {
                throw SmsCodecException("Frame is too long")
            }
            return SmsFrame(ref, index, total, chunk)
        }
    }
}

/** The text to put in a single SMS body (used with [SmsMode.CONCATENATED]). */
fun List<SmsFrame>.toSmsBody(): String = joinToString(" ") { it.toText() }
