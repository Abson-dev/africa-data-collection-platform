package dev.abson.adc.bridge.central

import dev.abson.adc.bridge.PartialFormRef
import dev.abson.adc.bridge.registry.FormInfo
import dev.abson.adc.smscodec.SmsRecord
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.util.UUID

/**
 * Builds a submission for the `sms_partial` form (forms/templates/sms_partial.xlsx).
 * Field names here must match that form.
 */
object PartialXml {
    fun build(
        partialForm: PartialFormRef,
        source: FormInfo,
        record: SmsRecord,
        appUser: String,
        receivedAt: Instant,
    ): String {
        require(record.values.size == source.smsFields.size) {
            "SMS has ${record.values.size} values but form ${source.xmlFormId} v${source.version} " +
                "has ${source.smsFields.size} SMS fields"
        }
        val payload = buildJsonObject {
            source.smsFields.zip(record.values).forEach { (name, value) ->
                put(name, value?.let(::JsonPrimitive) ?: JsonNull)
            }
        }
        // Deterministic ID: retrying the same partial gives the same instanceID, so Central can't store it twice.
        val partialId = UUID.nameUUIDFromBytes("sms_partial:${record.instanceId}".toByteArray())

        return buildString {
            append("""<data id="${esc(partialForm.formId)}" version="${esc(partialForm.version)}">""")
            field("source_form_id", source.xmlFormId)
            field("source_form_version", source.version)
            field("source_instance_id", "uuid:${record.instanceId}")
            field("app_user", appUser)
            field("received_at", receivedAt.toString())
            field("payload_json", payload.toString())
            append("<meta><instanceID>uuid:$partialId</instanceID>")
            field("instanceName", "SMS ${source.xmlFormId} - $appUser")
            append("</meta>")
            append("</data>")
        }
    }

    private fun StringBuilder.field(name: String, value: String) {
        append('<').append(name).append('>').append(esc(value, attribute = false)).append("</").append(name).append('>')
    }

    /** Escapes for XML; quotes only need escaping inside attribute values. */
    private fun esc(s: String, attribute: Boolean = true) = buildString(s.length) {
        for (c in s) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                attribute && c == '"' -> append("&quot;")
                attribute && c == '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
