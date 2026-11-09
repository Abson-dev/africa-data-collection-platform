package dev.abson.adc.bridge.store

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class PartialStatus {
    /** Written to Central's sms_partial form. Waiting for the full record. */
    WRITTEN,

    /** The full record (same instanceID) has arrived in Central. */
    MERGED,

    /** Writing to Central failed; the merge job retries it. */
    FAILED,
}

data class PartialRecord(
    /** instanceID of the original submission (without the `uuid:` prefix). */
    val instanceId: UUID,
    val formKey: Int,
    val keyId: Int,
    val receivedAt: Instant,
    /** The sms_partial submission XML, kept so a failed write can be retried. */
    val xml: String,
    val status: PartialStatus,
    val error: String? = null,
)

interface PartialStore {
    /** @return false if a partial for this instanceID already exists (e.g. the gateway resent it). */
    fun insertIfAbsent(record: PartialRecord): Boolean
    fun setStatus(instanceId: UUID, status: PartialStatus, error: String? = null)
    fun get(instanceId: UUID): PartialRecord?
    fun withStatus(status: PartialStatus): List<PartialRecord>
}

/** TODO(phase1, month 3): PostgreSQL implementation (table `partial_record`, see the Phase 1 spec, section 3.3). */
class InMemoryPartialStore : PartialStore {
    private val records = ConcurrentHashMap<UUID, PartialRecord>()

    override fun insertIfAbsent(record: PartialRecord) = records.putIfAbsent(record.instanceId, record) == null

    override fun setStatus(instanceId: UUID, status: PartialStatus, error: String?) {
        records.computeIfPresent(instanceId) { _, r -> r.copy(status = status, error = error) }
    }

    override fun get(instanceId: UUID) = records[instanceId]

    override fun withStatus(status: PartialStatus) = records.values.filter { it.status == status }
}
