package com.nowadays.events.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nowadays.events.domain.model.AttendanceResponse
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE origin != 'AUTOMATIC' OR is_remote_visible = 1 ORDER BY starts_at")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND (origin != 'AUTOMATIC' OR is_remote_visible = 1)")
    fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events ORDER BY starts_at")
    suspend fun getAllIncludingHidden(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("UPDATE events SET missed_snapshots = 0, is_remote_visible = 1 WHERE id IN (:presentIds) AND origin = 'AUTOMATIC'")
    suspend fun markRemotePresent(presentIds: List<String>)

    @Query("UPDATE events SET missed_snapshots = missed_snapshots + 1 WHERE origin = 'AUTOMATIC' AND id NOT IN (:presentIds)")
    suspend fun incrementMissingRemote(presentIds: List<String>)

    @Query("UPDATE events SET is_remote_visible = 0 WHERE origin = 'AUTOMATIC' AND missed_snapshots >= :threshold")
    suspend fun hideMissingRemote(threshold: Int)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getSyncState(): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Transaction
    suspend fun reconcileRemoteSnapshot(events: List<EventEntity>, state: SyncStateEntity, absenceThreshold: Int) {
        upsertAll(events)
        val ids = events.map(EventEntity::id)
        if (ids.isNotEmpty()) {
            markRemotePresent(ids)
            incrementMissingRemote(ids)
            hideMissingRemote(absenceThreshold)
        }
        upsertSyncState(state)
    }

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: String)

    @Query("DELETE FROM event_attendance WHERE event_id IN (:eventIds)")
    suspend fun deleteAttendanceForEvents(eventIds: List<String>)

    @Query("DELETE FROM events WHERE id IN (:eventIds)")
    suspend fun deleteEvents(eventIds: List<String>)

    @Transaction
    suspend fun deleteEventFamily(eventIds: List<String>) {
        deleteAttendanceForEvents(eventIds)
        deleteEvents(eventIds)
    }

    @Query("SELECT * FROM events WHERE source_url = :sourceUrl AND title = :title AND starts_at = :startsAt LIMIT 1")
    suspend fun findMatching(sourceUrl: String, title: String, startsAt: Long): EventEntity?

    @Query("""
        DELETE FROM events
        WHERE rowid NOT IN (
            SELECT MIN(rowid) FROM events GROUP BY source_url, title, starts_at
        )
    """)
    suspend fun deleteExactDuplicates()

    @Query("SELECT * FROM event_attendance WHERE event_id = :eventId")
    fun observeAttendance(eventId: String): Flow<AttendanceEntity?>

    @Query("SELECT * FROM event_attendance WHERE event_id = :eventId")
    suspend fun getAttendance(eventId: String): AttendanceEntity?

    @Upsert
    suspend fun upsertAttendance(attendance: AttendanceEntity)

    @Query("DELETE FROM event_attendance WHERE event_id = :eventId")
    suspend fun deleteAttendance(eventId: String)

    @Query("UPDATE events SET going_count = MAX(0, going_count + :goingDelta), maybe_count = MAX(0, maybe_count + :maybeDelta) WHERE id = :eventId")
    suspend fun adjustAttendanceCounts(eventId: String, goingDelta: Int, maybeDelta: Int)

    @Transaction
    suspend fun setAttendance(eventId: String, response: AttendanceResponse, updatedAtEpochMillis: Long) {
        val previous = getAttendance(eventId)?.response?.let(AttendanceResponse::valueOf) ?: AttendanceResponse.NONE
        val effective = if (previous == response) AttendanceResponse.NONE else response
        adjustAttendanceCounts(
            eventId = eventId,
            goingDelta = effective.goingValue() - previous.goingValue(),
            maybeDelta = effective.maybeValue() - previous.maybeValue(),
        )
        if (effective == AttendanceResponse.NONE) deleteAttendance(eventId)
        else upsertAttendance(AttendanceEntity(eventId, effective.name, updatedAtEpochMillis))
    }
}

private fun AttendanceResponse.goingValue() = if (this == AttendanceResponse.GOING) 1 else 0
private fun AttendanceResponse.maybeValue() = if (this == AttendanceResponse.MAYBE) 1 else 0
