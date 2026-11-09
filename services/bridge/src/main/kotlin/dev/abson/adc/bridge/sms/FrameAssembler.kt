package dev.abson.adc.bridge.sms

import dev.abson.adc.smscodec.SmsFrame
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Collects the parts of multi-SMS messages until all have arrived.
 * Parts are grouped by sender + message ref; incomplete messages are dropped after [ttl].
 *
 * TODO(phase1, month 3): persist pending parts so a restart doesn't lose them.
 */
class FrameAssembler(
    private val ttl: Duration = Duration.ofHours(24),
    private val clock: Clock = Clock.systemUTC(),
) {
    private class Pending(val firstSeen: Instant) {
        val frames = HashMap<Int, SmsFrame>()
    }

    private val pending = HashMap<String, Pending>()

    /** @return all parts, in order, once the message is complete; otherwise null. */
    @Synchronized
    fun add(sender: String, frame: SmsFrame): List<SmsFrame>? {
        val now = clock.instant()
        pending.entries.removeIf { Duration.between(it.value.firstSeen, now) > ttl }

        val key = "$sender|${frame.ref}"
        val entry = pending.getOrPut(key) { Pending(now) }
        entry.frames[frame.index] = frame
        if (entry.frames.size < frame.total) return null

        pending.remove(key)
        return entry.frames.values.sortedBy { it.index }
    }
}
