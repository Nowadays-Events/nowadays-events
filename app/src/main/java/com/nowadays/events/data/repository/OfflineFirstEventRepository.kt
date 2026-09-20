package com.nowadays.events.data.repository

import com.nowadays.events.data.local.EventDao
import com.nowadays.events.data.mapper.toDomain
import com.nowadays.events.data.mapper.toEntity
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.AttendanceResponse
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
}
