package com.nowadays.events.data.sync

import com.nowadays.events.data.remote.EventSource
import com.nowadays.events.domain.repository.EventRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class SyncResult(val fetched: Int, val inserted: Int, val updated: Int, val probableDuplicatesSkipped: Int)

class EventSynchronizer @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards EventSource>,
    private val repository: EventRepository,
    private val deduplicator: EventDeduplicator,
) {
    suspend fun synchronize(): SyncResult {
        val known = repository.observeEvents().first().toMutableList()
        var fetched = 0
        var inserted = 0
        var updated = 0
        var skipped = 0
        sources.forEach { source ->
            source.fetchEvents().forEach { candidate ->
                fetched++
                val duplicate = deduplicator.find(candidate, known)
                when (duplicate.match) {
                    DuplicateMatch.PROBABLE -> skipped++
                    DuplicateMatch.NONE -> {
                        repository.save(candidate)
                        known += candidate
                        inserted++
                    }
                    else -> {
                        val current = requireNotNull(duplicate.event)
                        val merged = candidate.copy(
                            id = current.id,
                            goingCount = current.goingCount,
                            maybeCount = current.maybeCount,
                        )
                        repository.save(merged)
                        known[known.indexOf(current)] = merged
                        updated++
                    }
                }
            }
        }
        return SyncResult(fetched, inserted, updated, skipped)
    }
}

