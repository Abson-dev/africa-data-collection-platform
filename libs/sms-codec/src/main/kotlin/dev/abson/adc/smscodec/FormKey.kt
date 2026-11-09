package dev.abson.adc.smscodec

import java.security.MessageDigest

/** 4-byte identifier of a form version, used in the SMS header instead of the (long) form ID. */
object FormKey {
    fun of(xmlFormId: String, version: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$xmlFormId\u0000$version".toByteArray(Charsets.UTF_8))
        return ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
    }
}
