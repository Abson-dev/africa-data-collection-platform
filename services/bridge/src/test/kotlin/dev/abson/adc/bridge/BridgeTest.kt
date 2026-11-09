package dev.abson.adc.bridge

import dev.abson.adc.bridge.central.CentralClient
import dev.abson.adc.bridge.merge.MergeJob
import dev.abson.adc.bridge.registry.DeviceKey
import dev.abson.adc.bridge.registry.FormInfo
import dev.abson.adc.bridge.registry.InMemoryRegistry
import dev.abson.adc.bridge.sms.InboundSmsHandler
import dev.abson.adc.bridge.store.InMemoryPartialStore
import dev.abson.adc.bridge.store.PartialStatus
import dev.abson.adc.smscodec.SmsCodec
import dev.abson.adc.smscodec.SmsFrame
import dev.abson.adc.smscodec.SmsMode
import dev.abson.adc.smscodec.SmsRecord
import dev.abson.adc.smscodec.toSmsBody
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeTest {
    private val random = SecureRandom()
    private val token = "t".repeat(40)
    private val key = ByteArray(SmsCodec.KEY_BYTES).also(random::nextBytes)
    private val form = FormInfo(
        projectId = 3,
        xmlFormId = "household_survey",
        version = "2026110901",
        smsFields = listOf("district", "village", "head_name", "members", "urgent_need", "gps"),
    )
    private val partialForm = PartialFormRef(projectId = 3, formId = "sms_partial", version = "2026110901")
    private val registry = InMemoryRegistry(listOf(form), listOf(DeviceKey(7, "enum-ouaga-01", key)))

    /** Records what the Bridge sends to Central. */
    private class FakeCentral : CentralClient {
        val submitted = mutableListOf<String>()
        val existing = mutableSetOf<String>()
        var failNext = false

        override suspend fun submit(projectId: Int, xmlFormId: String, xml: String) {
            if (failNext) {
                failNext = false
                error("Central unavailable")
            }
            submitted += xml
        }

        override suspend fun submissionExists(projectId: Int, xmlFormId: String, instanceId: String) =
            instanceId in existing
    }

    private val central = FakeCentral()
    private val store = InMemoryPartialStore()
    private val handler = InboundSmsHandler(registry, store, central, partialForm)

    // Incompressible values so the message needs more than one SMS.
    private fun values() = List(6) { random.nextLong().toString(36) + random.nextLong().toString(36) }

    private fun encode(instanceId: UUID, values: List<String?>, mode: SmsMode = SmsMode.SEPARATE) =
        SmsCodec.encode(SmsRecord(7, form.formKey, instanceId, values), key, mode)

    private suspend fun HttpClient.sms(text: String, from: String = "+22670000001", tok: String = token) =
        submitForm(
            url = "/sms/inbound?token=$tok",
            formParameters = parameters {
                append("from", from)
                append("to", "12345")
                append("text", text)
            },
        )

    @Test
    fun `multi-part SMS becomes one partial record in Central`() = testApplication {
        application { bridgeModule(handler, token) }
        val id = UUID.randomUUID()
        val vals = values()
        val frames = encode(id, vals)
        assertTrue(frames.size > 1)

        val labels = frames.map { client.sms(it.toText()).bodyAsText() }
        assertEquals(List(frames.size - 1) { "waiting_for_parts" } + "written", labels)

        assertEquals(1, central.submitted.size)
        val xml = central.submitted.single()
        assertContains(xml, "<source_instance_id>uuid:$id</source_instance_id>")
        assertContains(xml, "<app_user>enum-ouaga-01</app_user>")
        assertContains(xml, "\"district\":\"${vals[0]}\"")
        assertEquals(PartialStatus.WRITTEN, store.get(id)?.status)
    }

    @Test
    fun `pre-filled single SMS body works too`() = testApplication {
        application { bridgeModule(handler, token) }
        val id = UUID.randomUUID()
        val body = encode(id, values(), SmsMode.CONCATENATED).toSmsBody()
        assertEquals("written", client.sms(body).bodyAsText())
    }

    @Test
    fun `gateway resend is not written twice`() = testApplication {
        application { bridgeModule(handler, token) }
        val frames = encode(UUID.randomUUID(), values())
        repeat(2) { frames.forEach { client.sms(it.toText()) } }
        assertEquals(1, central.submitted.size)
    }

    @Test
    fun `wrong token is refused and ordinary SMS are ignored`() = testApplication {
        application { bridgeModule(handler, token) }
        assertEquals(HttpStatusCode.Forbidden, client.sms("#A11abcdAAAA", tok = "wrong").status)
        assertEquals("not_data", client.sms("Bonjour, envoi demain").bodyAsText())
        assertTrue(central.submitted.isEmpty())
    }

    @Test
    fun `SMS from a revoked key is rejected`() = testApplication {
        val revoked = InMemoryRegistry(listOf(form), listOf(DeviceKey(7, "enum-ouaga-01", key, revoked = true)))
        application { bridgeModule(InboundSmsHandler(revoked, store, central, partialForm), token) }
        val body = encode(UUID.randomUUID(), values(), SmsMode.CONCATENATED).toSmsBody()
        assertEquals("rejected", client.sms(body).bodyAsText())
        assertTrue(central.submitted.isEmpty())
    }

    @Test
    fun `merge job retries failed writes and detects the full record`() = runBlocking {
        val id = UUID.randomUUID()
        central.failNext = true
        val body = encode(id, values(), SmsMode.CONCATENATED).toSmsBody()
        assertEquals("write_failed", handler.handle("+22670000001", body).label)
        assertEquals(PartialStatus.FAILED, store.get(id)?.status)

        val job = MergeJob(store, central, registry, partialForm)
        assertEquals(1, job.runOnce().retried)
        assertEquals(PartialStatus.WRITTEN, store.get(id)?.status)

        assertEquals(0, job.runOnce().merged)
        central.existing += "uuid:$id"
        assertEquals(1, job.runOnce().merged)
        assertEquals(PartialStatus.MERGED, store.get(id)?.status)
    }

    @Test
    fun `frames parse from a gateway text with extra whitespace`() {
        val frames = encode(UUID.randomUUID(), values())
        val text = "  " + frames.joinToString("\r\n") { it.toText() } + " "
        assertEquals(frames, SmsFrame.parseAll(text))
        assertFalse(frames.isEmpty())
    }
}
