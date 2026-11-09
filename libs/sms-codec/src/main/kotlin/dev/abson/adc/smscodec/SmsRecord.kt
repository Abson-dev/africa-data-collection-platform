package dev.abson.adc.smscodec

import java.util.UUID

/**
 * The data carried by one SMS fallback message.
 *
 * @property keyId identifies the per-app-user AES key (0..65535), handed out at QR provisioning.
 * @property formKey [FormKey.of] the form ID + version, so the receiver knows which fields [values] map to.
 * @property instanceId the submission's instanceID; the full record later arrives with the same ID.
 * @property values values of the form's SMS-marked fields, in form order; null = not answered.
 */
data class SmsRecord(
    val keyId: Int,
    val formKey: Int,
    val instanceId: UUID,
    val values: List<String?>,
) {
    init {
        require(keyId in 0..0xFFFF) { "keyId must fit in 2 bytes, was $keyId" }
    }
}

/** Thrown when an SMS payload can't be built, parsed, decrypted or validated. */
class SmsCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)
