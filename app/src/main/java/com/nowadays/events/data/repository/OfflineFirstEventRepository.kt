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
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class OfflineFirstEventRepository @Inject constructor(
    private val dao: EventDao,
    private val clock: Clock,
) : EventRepository {
    override fun observeEvents(): Flow<List<Event>> = dao.observeAll().map { entities -> entities.map { it.toDomain().correctNamedWeekday() } }
    override fun observeEvent(id: String): Flow<Event?> = dao.observeById(id).map { it?.toDomain()?.correctNamedWeekday() }
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

private fun Event.correctNamedWeekday(zoneId: ZoneId = ZoneId.systemDefault()): Event {
    val normalized = title.lowercase()
    val expected = mapOf(
        "lundi" to DayOfWeek.MONDAY, "mardi" to DayOfWeek.TUESDAY, "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY, "vendredi" to DayOfWeek.FRIDAY, "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY,
    ).entries.firstOrNull { normalized.startsWith(it.key) }?.value ?: return this
    val currentDate = startsAt.atZone(zoneId).toLocalDate()
    if (currentDate.dayOfWeek == expected) return this
    val candidates = (-3L..3L).map { currentDate.plusDays(it) }
    val correctedDate = candidates.minByOrNull { date ->
        if (date.dayOfWeek == expected) kotlin.math.abs(ChronoUnit.DAYS.between(currentDate, date)) else Long.MAX_VALUE
    }?.takeIf { it.dayOfWeek == expected } ?: return this
    val shiftDays = ChronoUnit.DAYS.between(currentDate, correctedDate)
    return copy(startsAt = startsAt.plus(shiftDays, ChronoUnit.DAYS), endsAt = endsAt.plus(shiftDays, ChronoUnit.DAYS))
}
