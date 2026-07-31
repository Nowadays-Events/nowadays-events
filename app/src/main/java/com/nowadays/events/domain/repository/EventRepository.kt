package com.nowadays.events.domain.repository

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.AttendanceResponse
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun observeEvents(): Flow<List<Event>>
    fun observeEvent(id: String): Flow<Event?>
    suspend fun seedIfEmpty()
    suspend fun save(event: Event)
    suspend fun delete(eventId: String)
    suspend fun deleteAll(eventIds: List<String>)
    fun observeAttendance(eventId: String): Flow<AttendanceResponse>
    suspend fun setAttendance(eventId: String, response: AttendanceResponse)
}
