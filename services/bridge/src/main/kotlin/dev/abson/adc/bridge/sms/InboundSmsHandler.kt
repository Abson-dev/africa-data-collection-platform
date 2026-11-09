package dev.abson.adc.bridge.sms

import dev.abson.adc.bridge.PartialFormRef
import dev.abson.adc.bridge.central.CentralClient
import dev.abson.adc.bridge.central.PartialXml
import dev.abson.adc.bridge.registry.Registry
import dev.abson.adc.bridge.store.PartialRecord
import dev.abson.adc.bridge.store.PartialStatus
import dev.abson.adc.bridge.store.PartialStore
import dev.abson.adc.smscodec.SmsCodec
import dev.abson.adc.smscodec.SmsCodecException
import dev.abson.adc.smscodec.SmsFrame
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.util.UUID

/** What happened to one inbound SMS. Never contains field values (they may be personal data). */
sealed interface SmsOutcome {
    val label: String

    data class NotData(val reason: String) : SmsOutcome { override val label = "not_data" }
    data object Waiting : SmsOutcome { override val label = "waiting_for_parts" }
    data class Rejected(val reason: String) : SmsOutcome { override val label = "rejected" }
    data class Duplicate(val instanceId: UUID) : SmsOutcome { override val label = "duplicate" }
    data class Written(val instanceId: UUID) : SmsOutcome { override val label = "written" }
    data class Failed(val instanceId: UUID, val reason: String) : SmsOutcome { override val label = "write_failed" }
}

/** Turns inbound SMS into partial records in Central. */
class InboundSmsHandler(
    private val registry: Registry,
    private val store: PartialStore,
    private val central: CentralClient,
    private val partialForm: PartialFormRef,
    private val assembler: FrameAssembler = FrameAssembler(),
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun handle(sender: String, text: String): SmsOutcome {
        val frames = try {
            SmsFrame.parseAll(text)
        } catch (e: SmsCodecException) {
            return SmsOutcome.NotData(e.message ?: "unparseable")
        }

        var complete: List<SmsFrame>? = null
        for (frame in frames) assembler.add(sender, frame)?.let { complete = it }
        val parts = complete ?: return SmsOutcome.Waiting

        val record = try {
            SmsCodec.decode(parts) { keyId -> registry.key(keyId)?.takeUnless { it.revoked }?.key }
        } catch (e: SmsCodecException) {
            return SmsOutcome.Rejected(e.message ?: "decode failed")
        }

        val form = registry.form(record.formKey)
            ?: return SmsOutcome.Rejected("Unknown form key ${record.formKey}")
        val appUser = registry.key(record.keyId)?.appUser
            ?: return SmsOutcome.Rejected("Unknown key ${record.keyId}")

        val xml = try {
            PartialXml.build(partialForm, form, record, appUser, clock.instant())
        } catch (e: IllegalArgumentException) {
            return SmsOutcome.Rejected(e.message ?: "field mismatch")
        }

        // Stored as FAILED until Central confirms: if the process dies mid-write, the merge job retries it.
        val partial = PartialRecord(
            instanceId = record.instanceId,
            formKey = record.formKey,
            keyId = record.keyId,
            receivedAt = clock.instant(),
            xml = xml,
            status = PartialStatus.FAILED,
            error = "not yet written",
        )
        if (!store.insertIfAbsent(partial)) return SmsOutcome.Duplicate(record.instanceId)

        return try {
            central.submit(partialForm.projectId, partialForm.formId, xml)
            store.setStatus(record.instanceId, PartialStatus.WRITTEN)
            SmsOutcome.Written(record.instanceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.setStatus(record.instanceId, PartialStatus.FAILED, e.message)
            SmsOutcome.Failed(record.instanceId, e.message ?: e.javaClass.simpleName)
        }
    }
}
