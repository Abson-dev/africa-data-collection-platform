package dev.abson.adc.smscodec

import java.security.SecureRandom
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SmsCodecTest {
    private val random = SecureRandom()
    private val key = ByteArray(SmsCodec.KEY_BYTES).also(random::nextBytes)
    private val keys = mapOf(7 to key)
    private val formKey = FormKey.of("household_survey", "2026110901")

    private fun record(values: List<String?>) =
        SmsRecord(keyId = 7, formKey = formKey, instanceId = UUID.randomUUID(), values = values)

    /** Incompressible values, so the size of the message is predictable. */
    private fun randomValues(count: Int) =
        List(count) { random.nextLong().toString(36) + random.nextLong().toString(36) }

    // A realistic urgent case: a few key fields, including accents and non-Latin script.
    private val sample = listOf(
        "Ouagadougou", "Secteur 12", "Aminata Ouédraogo", "4", "yes", null,
        "12.3714 -1.5197 310 4.5", "ምሳሌ", "2026-11-09T10:15:00Z",
    )

    @Test
    fun `round trip in separate mode`() {
        val original = record(sample)
        val frames = SmsCodec.encode(original, key, SmsMode.SEPARATE)

        assertTrue(frames.size in 1..3)
        frames.forEach { assertTrue(it.toText().length <= 160, "frame longer than one SMS") }

        val decoded = SmsCodec.decode(frames, keys::get)
        assertEquals(original, decoded)
    }

    @Test
    fun `round trip in concatenated mode through a single SMS body`() {
        val original = record(sample)
        val frames = SmsCodec.encode(original, key, SmsMode.CONCATENATED)
        assertEquals(1, frames.size)

        val body = frames.toSmsBody()
        assertTrue(body.length <= 459, "body must fit in 3 concatenated SMS segments")

        assertEquals(original, SmsCodec.decode(SmsFrame.parseAll(body), keys::get))
    }

    @Test
    fun `frames use only GSM-7 safe characters`() {
        val frames = SmsCodec.encode(record(sample), key)
        frames.forEach { assertTrue(Regex("^[#A-Za-z0-9+/]+$").matches(it.toText())) }
    }

    @Test
    fun `parts can arrive out of order, duplicated, or joined in one text`() {
        val original = record(randomValues(6))
        val frames = SmsCodec.encode(original, key, SmsMode.SEPARATE)
        assertTrue(frames.size > 1, "test needs a multi-part message")

        val shuffled = (frames + frames.first()).reversed()
        assertEquals(original, SmsCodec.decode(shuffled, keys::get))

        val joined = SmsFrame.parseAll(frames.joinToString("\n") { it.toText() })
        assertEquals(original, SmsCodec.decode(joined, keys::get))
    }

    @Test
    fun `empty and all-null records work`() {
        listOf(emptyList(), listOf(null, null)).forEach { values ->
            val original = record(values)
            assertEquals(original, SmsCodec.decode(SmsCodec.encode(original, key), keys::get))
        }
    }

    @Test
    fun `altered text is rejected`() {
        val frames = SmsCodec.encode(record(sample), key)
        val first = frames.first()
        // Change a character in the middle (the last one may only carry ignored padding bits).
        val i = 40
        val flipped = if (first.chunk[i] == 'A') 'B' else 'A'
        val tampered = listOf(first.copy(chunk = first.chunk.replaceRange(i, i + 1, flipped.toString()))) +
            frames.drop(1)

        assertFailsWith<SmsCodecException> { SmsCodec.decode(tampered, keys::get) }
    }

    @Test
    fun `wrong or unknown key is rejected`() {
        val frames = SmsCodec.encode(record(sample), key)
        val otherKey = ByteArray(SmsCodec.KEY_BYTES).also(random::nextBytes)

        assertFailsWith<SmsCodecException> { SmsCodec.decode(frames) { otherKey } }
        val e = assertFailsWith<SmsCodecException> { SmsCodec.decode(frames) { null } }
        assertContains(e.message!!, "Unknown or revoked key 7")
    }

    @Test
    fun `missing part is reported`() {
        val frames = SmsCodec.encode(record(randomValues(6)), key)
        assertTrue(frames.size > 1)
        val e = assertFailsWith<SmsCodecException> { SmsCodec.decode(frames.drop(1), keys::get) }
        assertContains(e.message!!, "Missing parts")
    }

    @Test
    fun `record too large for 3 SMS is refused with a clear message`() {
        val tooBig = record(randomValues(40))
        val e = assertFailsWith<SmsCodecException> { SmsCodec.encode(tooBig, key) }
        assertContains(e.message!!, "Mark fewer fields")
    }

    @Test
    fun `capacity matches the spec`() {
        assertEquals(51, SmsCodec.OVERHEAD_BYTES)
        assertEquals(342, SmsMode.SEPARATE.maxPayloadBytes)
        assertEquals(338, SmsMode.CONCATENATED.maxPayloadBytes)
    }

    @Test
    fun `ordinary text messages are not mistaken for data`() {
        assertFailsWith<SmsCodecException> { SmsFrame.parseAll("Hello, I will send the data tomorrow") }
        assertFailsWith<SmsCodecException> { SmsFrame.parseAll("#A9") }
        assertFailsWith<SmsCodecException> { SmsFrame.parseAll("#A31abcdAAAA") }
    }

    @Test
    fun `form key depends on form ID and version`() {
        assertEquals(FormKey.of("f", "1"), FormKey.of("f", "1"))
        assertNotEquals(FormKey.of("f", "1"), FormKey.of("f", "2"))
        assertNotEquals(FormKey.of("f", "1"), FormKey.of("g", "1"))
    }
}
