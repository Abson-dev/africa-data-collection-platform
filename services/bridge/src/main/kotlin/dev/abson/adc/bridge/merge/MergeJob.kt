package dev.abson.adc.bridge.merge

import dev.abson.adc.bridge.PartialFormRef
import dev.abson.adc.bridge.central.CentralClient
import dev.abson.adc.bridge.registry.Registry
import dev.abson.adc.bridge.store.PartialStatus
import dev.abson.adc.bridge.store.PartialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration

data class MergeStats(val retried: Int, val merged: Int)

/**
 * Every few minutes:
 * 1. retries partials that failed to be written to Central;
 * 2. marks partials MERGED once the full record (same instanceID) is in Central.
 */
class MergeJob(
    private val store: PartialStore,
    private val central: CentralClient,
    private val registry: Registry,
    private val partialForm: PartialFormRef,
) {
    private val log = LoggerFactory.getLogger(MergeJob::class.java)

    suspend fun runOnce(): MergeStats {
        var retried = 0
        for (p in store.withStatus(PartialStatus.FAILED)) {
            try {
                central.submit(partialForm.projectId, partialForm.formId, p.xml)
                store.setStatus(p.instanceId, PartialStatus.WRITTEN)
                retried++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.setStatus(p.instanceId, PartialStatus.FAILED, e.message)
            }
        }

        var merged = 0
        for (p in store.withStatus(PartialStatus.WRITTEN)) {
            val form = registry.form(p.formKey) ?: continue
            if (central.submissionExists(form.projectId, form.xmlFormId, "uuid:${p.instanceId}")) {
                store.setStatus(p.instanceId, PartialStatus.MERGED)
                merged++
            }
        }
        return MergeStats(retried, merged)
    }

    fun start(scope: CoroutineScope, interval: Duration): Job = scope.launch {
        while (isActive) {
            try {
                val stats = runOnce()
                if (stats.retried + stats.merged > 0) log.info("Merge job: {}", stats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Merge job failed; will retry", e)
            }
            delay(interval)
        }
    }
}
