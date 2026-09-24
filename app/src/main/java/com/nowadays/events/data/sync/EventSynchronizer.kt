package com.nowadays.events.data.sync

import com.nowadays.events.data.remote.EventSource
import com.nowadays.events.data.remote.RemoteEventSnapshot
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.repository.EventRepository
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SyncResult(val fetched: Int, val inserted: Int, val updated: Int, val probableDuplicatesSkipped: Int, val successful: Boolean)
data class SnapshotValidation(val valid: Boolean, val reason: String? = null)

class SnapshotValidator @Inject constructor() {
    fun validate(snapshot: RemoteEventSnapshot, lastSuccessfulCount: Int, minimumEvents: Int = 10): SnapshotValidation {
        if (snapshot.healthStatus != "ok") return SnapshotValidation(false, "collector_${snapshot.healthStatus}")
        if (snapshot.generatedAt == null) return SnapshotValidation(false, "missing_generated_at")
        if (snapshot.declaredEventCount != snapshot.events.size) return SnapshotValidation(false, "partial_decode")
        if (snapshot.events.isEmpty()) return SnapshotValidation(false, "unexpected_empty_snapshot")
        if (snapshot.events.size < minimumEvents) return SnapshotValidation(false, "below_minimum_count")
        if (lastSuccessfulCount > 0 && snapshot.events.size < lastSuccessfulCount * 0.5) return SnapshotValidation(false, "abnormal_volume_drop")
        if (snapshot.events.map(Event::id).distinct().size != snapshot.events.size) return SnapshotValidation(false, "duplicate_remote_ids")
        return SnapshotValidation(true)
    }
}

class EventSynchronizer @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards EventSource>,
    private val repository: EventRepository,
    private val deduplicator: EventDeduplicator,
    private val validator: SnapshotValidator,
    private val clock: Clock,
) {
    suspend fun synchronize(): SyncResult {
        val attemptAt = clock.instant()
        val previousState = repository.getSyncState()
        repository.recordSyncState(previousState.copy(status = SyncStatus.RUNNING, lastAttemptAt = attemptAt, technicalReason = null))
        return try {
            require(sources.isNotEmpty()) { "no_remote_source" }
            val snapshots = sources.map { it.fetchSnapshot() }
            val invalid = snapshots.map { validator.validate(it, previousState.lastSuccessfulCount) }.firstOrNull { !it.valid }
            if (invalid != null) return failed(previousState, attemptAt, invalid.reason ?: "invalid_snapshot", offline = false)
            reconcile(snapshots.flatMap(RemoteEventSnapshot::events), attemptAt)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { failed(previousState, attemptAt, "sync_cancelled", offline = false) }
            throw cancelled
        } catch (error: Throwable) {
            failed(previousState, attemptAt, technicalReason(error), offline = error is IOException)
        }
    }

    private suspend fun reconcile(remote: List<Event>, attemptAt: java.time.Instant): SyncResult {
        val known = repository.observeEvents().first().toMutableList()
        val reconciled = mutableListOf<Event>()
        var inserted = 0
        var updated = 0
        var skipped = 0
        remote.forEach { candidate ->
            val duplicate = deduplicator.find(candidate, known)
            when (duplicate.match) {
                DuplicateMatch.PROBABLE -> {
                    val current = requireNotNull(duplicate.event)
                    val merged = deduplicator.merge(current, candidate, preferCandidate = false)
                    reconciled += merged
                    known[known.indexOf(current)] = merged
                    skipped++
                }
                DuplicateMatch.NONE -> { reconciled += candidate; known += candidate; inserted++ }
                else -> {
                    val current = requireNotNull(duplicate.event)
                    val merged = deduplicator.merge(current, candidate)
                    reconciled += merged
                    known[known.indexOf(current)] = merged
                    updated++
                }
            }
        }
        val state = SyncState(SyncStatus.SUCCESS, attemptAt, attemptAt, remote.size, remote.size)
        repository.reconcileRemoteSnapshot(reconciled, state)
        return SyncResult(remote.size, inserted, updated, skipped, successful = true)
    }

    private suspend fun failed(previous: SyncState, attemptAt: java.time.Instant, reason: String, offline: Boolean): SyncResult {
        val hasCache = repository.observeEvents().first().isNotEmpty()
        repository.recordSyncState(previous.copy(
            status = when { offline && hasCache -> SyncStatus.OFFLINE_WITH_CACHE; hasCache -> SyncStatus.FAILED_WITH_CACHE; else -> SyncStatus.FAILED_EMPTY },
            lastAttemptAt = attemptAt, receivedCount = 0, technicalReason = reason,
        ))
        return SyncResult(0, 0, 0, 0, successful = false)
    }

    private fun technicalReason(error: Throwable): String = when (error) {
        is IOException -> "network_unavailable:${error.javaClass.simpleName}"
        is org.json.JSONException -> "json_decode_failed"
        is IllegalStateException -> "remote_contract_failed:${error.message.orEmpty().take(80)}"
        else -> "sync_failed:${error.javaClass.simpleName}"
    }
}
