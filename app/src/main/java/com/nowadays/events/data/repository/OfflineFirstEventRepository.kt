package com.nowadays.events.data.repository

import com.nowadays.events.data.local.EventDao
import com.nowadays.events.data.mapper.toDomain
import com.nowadays.events.data.mapper.toEntity
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.SyncState
import com.nowadays.events.domain.model.SyncStatus
import com.nowadays.events.data.local.SyncStateEntity
import com.nowadays.events.domain.repository.EventRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineFirstEventRepository @Inject constructor(
    private val dao: EventDao,
    private val clock: Clock,
) : EventRepository {
    override fun observeEvents(): Flow<List<Event>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }
    override fun observeEvent(id: String): Flow<Event?> = dao.observeById(id).map { it?.toDomain() }
    override suspend fun seedIfEmpty() {
        dao.deleteExactDuplicates()
    }
    override suspend fun save(event: Event) {
        val existing = dao.findMatching(event.sourceUrl, event.title, event.startsAt.toEpochMilli())
        dao.upsert(event.copy(id = existing?.id ?: event.id).toEntity())
    }
    override suspend fun delete(eventId: String) {
        dao.deleteAttendance(eventId)
        dao.deleteEvent(eventId)
    }
    override suspend fun deleteAll(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        dao.deleteEventFamily(eventIds)
    }
    override fun observeAttendance(eventId: String): Flow<AttendanceResponse> =
        dao.observeAttendance(eventId).map { entity -> entity?.response?.let(AttendanceResponse::valueOf) ?: AttendanceResponse.NONE }
    override suspend fun setAttendance(eventId: String, response: AttendanceResponse) =
        dao.setAttendance(eventId, response, clock.millis())

    override fun observeSyncState(): Flow<SyncState> = dao.observeSyncState().map { it?.toDomain() ?: SyncState() }

    override suspend fun getSyncState(): SyncState = dao.getSyncState()?.toDomain() ?: SyncState()

    override suspend fun recordSyncState(state: SyncState) = dao.upsertSyncState(state.toEntity())

    override suspend fun reconcileRemoteSnapshot(events: List<Event>, state: SyncState) {
        val existing = dao.getAllIncludingHidden().associateBy { it.id }
        val entities = events.map { event ->
            val current = existing[event.id]
            event.copy(
                goingCount = current?.goingCount ?: event.goingCount,
                maybeCount = current?.maybeCount ?: event.maybeCount,
            ).toEntity()
        }
        dao.reconcileRemoteSnapshot(entities, state.toEntity(), absenceThreshold = 2)
    }
}

private fun SyncStateEntity.toDomain() = SyncState(
    status = runCatching { SyncStatus.valueOf(status) }.getOrDefault(SyncStatus.NEVER),
    lastAttemptAt = lastAttemptAt?.let(java.time.Instant::ofEpochMilli),
    lastSuccessAt = lastSuccessAt?.let(java.time.Instant::ofEpochMilli),
    receivedCount = receivedCount,
    lastSuccessfulCount = lastSuccessfulCount,
    technicalReason = technicalReason,
)

private fun SyncState.toEntity() = SyncStateEntity(
    status = status.name,
    lastAttemptAt = lastAttemptAt?.toEpochMilli(),
    lastSuccessAt = lastSuccessAt?.toEpochMilli(),
    receivedCount = receivedCount,
    lastSuccessfulCount = lastSuccessfulCount,
    technicalReason = technicalReason,
)
