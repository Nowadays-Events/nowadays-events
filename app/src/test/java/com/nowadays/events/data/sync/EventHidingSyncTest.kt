package com.nowadays.events.data.sync

import com.nowadays.events.data.remote.*
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.repository.EventRepository
import com.nowadays.events.domain.usecase.*
import com.nowadays.events.support.DeterministicEventFixtures.event
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EventHidingSyncTest {
    @Test fun successfulSyncRemovalAndReappearanceNeverEraseLocalHiding() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneId.of("Europe/Paris"))
        val target = event("api-target").copy(origin = DataOrigin.AUTOMATIC)
        val fillers = List(10) { event("api-filler-$it").copy(origin = DataOrigin.AUTOMATIC) }
        var snapshotEvents = fillers + target
        val source = object : EventSource {
            override val name = "Deterministic snapshot"
            override suspend fun fetchSnapshot() = RemoteEventSnapshot(snapshotEvents, clock.instant(), "ok", snapshotEvents.size)
        }
        val repository = FakeRepository()
        val sync = EventSynchronizer(setOf(source), repository, EventDeduplicator(), SnapshotValidator(), clock)
        val storage = object : HiddenEventStorage {
            var value = emptySet<String>()
            override fun read() = value
            override fun write(keys: Set<String>) { value = keys.toSet() }
        }
        val hiding = EventHidingStore(storage)
        assertTrue(sync.synchronize().successful)
        hiding.hide(target)
        assertEquals(11, repository.events.value.size) // No deletion or alteration of sync counts.
        snapshotEvents = fillers
        assertTrue(sync.synchronize().successful)
        snapshotEvents = fillers + target.copy(title = "Updated", nextOccurrenceAt = clock.instant().plusSeconds(86400))
        assertTrue(sync.synchronize().successful)
        val restarted = EventHidingStore(storage)
        val result = EventHidingPolicy.partition(repository.events.value, restarted.hiddenKeys.value)
        assertEquals(listOf(target.id), result.hidden.map { it.id })
        assertEquals(10, result.visible.size)
        assertEquals(11, repository.sync.value.receivedCount)
        restarted.reveal(target)
        assertEquals(11, EventHidingPolicy.partition(repository.events.value, restarted.hiddenKeys.value).visible.size)
    }

    private class FakeRepository : EventRepository {
        val events = MutableStateFlow(emptyList<Event>())
        val sync = MutableStateFlow(SyncState())
        override fun observeEvents(): Flow<List<Event>> = events
        override fun observeEvent(id: String): Flow<Event?> = events.map { it.firstOrNull { event -> event.id == id } }
        override suspend fun seedIfEmpty() {}
        override suspend fun save(event: Event) { events.value = events.value.filterNot { it.id == event.id } + event }
        override suspend fun delete(eventId: String) { error("Hiding must not delete") }
        override suspend fun deleteAll(eventIds: List<String>) { error("Hiding must not delete") }
        override fun observeAttendance(eventId: String): Flow<AttendanceResponse> = flowOf(AttendanceResponse.NONE)
        override suspend fun setAttendance(eventId: String, response: AttendanceResponse) {}
        override fun observeSyncState(): Flow<SyncState> = sync
        override suspend fun getSyncState() = sync.value
        override suspend fun recordSyncState(state: SyncState) { sync.value = state }
        override suspend fun reconcileRemoteSnapshot(events: List<Event>, state: SyncState) { this.events.value = events; sync.value = state }
    }
}
