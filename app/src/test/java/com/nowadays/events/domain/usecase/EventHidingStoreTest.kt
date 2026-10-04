package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.*
import com.nowadays.events.support.DeterministicEventFixtures.event
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EventHidingStoreTest {
    private class MemoryStorage : HiddenEventStorage {
        var persisted = emptySet<String>()
        override fun read() = persisted.toSet()
        override fun write(keys: Set<String>) { persisted = keys.toSet() }
    }
    private val clock = Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneId.of("Europe/Paris"))

    @Test fun hideIsReversibleAndDoesNotMutateTheEvent() {
        val storage = MemoryStorage(); val store = EventHidingStore(storage); val target = event("api-remote")
        store.hide(target)
        val result = EventHidingPolicy.partition(listOf(target), store.hiddenKeys.value)
        assertEquals(listOf(target), result.hidden)
        assertTrue(result.allResultsHidden)
        store.reveal(target)
        assertEquals(listOf(target), EventHidingPolicy.partition(listOf(target), store.hiddenKeys.value).visible)
    }

    @Test fun sameTitleSourceAndPlaceDoNotShareHiding() {
        val store = EventHidingStore(MemoryStorage())
        val first = event("api-first", "Repair Café", sourceUrl = "https://example.org/repair")
        val second = first.copy(id = "api-second")
        store.hide(first)
        assertEquals(listOf(second), EventHidingPolicy.partition(listOf(first, second), store.hiddenKeys.value).visible)
    }

    @Test fun updatedDataAndNextOccurrenceKeepTheSameIdentity() {
        val store = EventHidingStore(MemoryStorage()); val first = event("api-stable", occurrenceCount = 4)
        store.hide(first)
        val updated = first.copy(title = "Nouveau titre", sourceUrl = "https://example.org/new",
            startsAt = first.startsAt.plusSeconds(86400), nextOccurrenceAt = first.nextOccurrenceAt!!.plusSeconds(86400),
            updatedAt = clock.instant(), status = EventStatus.CANCELLED)
        assertEquals(listOf(updated), EventHidingPolicy.partition(listOf(updated), store.hiddenKeys.value).hidden)
    }

    @Test fun localUuidSurvivesLocalEdits() {
        val store = EventHidingStore(MemoryStorage())
        val local = event("276b6705-dce6-4a72-b67c-38f13de4f663").copy(origin = DataOrigin.MANUAL)
        store.hide(local)
        assertTrue(EventHidingPolicy.partition(listOf(local.copy(title = "Modifié")), store.hiddenKeys.value).allResultsHidden)
    }

    @Test fun preferencesSurviveStoreRecreation() {
        val storage = MemoryStorage(); val first = event("api-stable")
        EventHidingStore(storage).hide(first)
        val restarted = EventHidingStore(storage)
        assertTrue(EventHidingPolicy.partition(listOf(first), restarted.hiddenKeys.value).allResultsHidden)
        restarted.reveal(first)
        assertTrue(EventHidingStore(storage).hiddenKeys.value.isEmpty())
    }

    @Test fun disappearanceDoesNotEraseThePreference() {
        val store = EventHidingStore(MemoryStorage()); val target = event("api-removed")
        store.hide(target)
        assertTrue(EventHidingPolicy.partition(emptyList(), store.hiddenKeys.value).hidden.isEmpty())
        assertEquals(1, store.hiddenKeys.value.size)
        assertTrue(EventHidingPolicy.partition(listOf(target), store.hiddenKeys.value).allResultsHidden)
    }

    @Test fun revealAllAlsoClearsAbsentEvents() {
        val storage = MemoryStorage(); val store = EventHidingStore(storage)
        store.hide(event("api-absent")); store.hide(event("api-present")); store.revealAll()
        assertTrue(EventHidingStore(storage).hiddenKeys.value.isEmpty())
    }

    @Test fun hideAndRevealAreIdempotent() {
        val store = EventHidingStore(MemoryStorage()); val target = event("api-idempotent")
        store.hide(target); store.hide(target); assertEquals(1, store.hiddenKeys.value.size)
        store.reveal(target); store.reveal(target); assertTrue(store.hiddenKeys.value.isEmpty())
    }

    @Test fun hiddenCountFollowsTheActiveTemporalFilter() {
        val store = EventHidingStore(MemoryStorage()); val today = event("api-today")
        val tomorrow = today.copy(id = "api-tomorrow", startsAt = today.startsAt.plusSeconds(86400), endsAt = today.endsAt.plusSeconds(86400))
        store.hide(today); store.hide(tomorrow)
        val filters = EventTimeFilters(clock)
        for (filter in listOf(TimeFilter.TODAY, TimeFilter.TOMORROW)) {
            val result = EventHidingPolicy.partition(filters.apply(listOf(today, tomorrow), filter, clock.zone), store.hiddenKeys.value)
            assertEquals(1, result.hidden.size); assertTrue(result.allResultsHidden)
        }
        assertEquals(2, EventHidingPolicy.partition(filters.apply(listOf(today, tomorrow), TimeFilter.NEXT_7_DAYS, clock.zone), store.hiddenKeys.value).hidden.size)
    }

    @Test fun radiusCountDoesNotIncludeHiddenEventsOutsideTheRadius() {
        val store = EventHidingStore(MemoryStorage()); val near = event("near"); val far = near.copy(id = "far", latitude = 48.0)
        store.hide(near); store.hide(far)
        val result = EventHidingPolicy.partition(listOf(near, far), store.hiddenKeys.value)
        assertEquals(1, NearbyEvents.find(result.hidden, 43.89, -.5, 30.0, clock.instant()).size)
    }

    @Test fun mapAndListConsumeTheSameVisiblePartitionBeforeGrouping() {
        val store = EventHidingStore(MemoryStorage()); val cancelled = event("cancelled", status = EventStatus.CANCELLED)
        val recurrent = event("recurrent", occurrenceCount = 3)
        store.hide(cancelled)
        val result = EventHidingPolicy.partition(listOf(cancelled, recurrent), store.hiddenKeys.value)
        val mapIds = EventFamilyGrouper.group(result.visible).flatMap { it.events }.map { it.id }.toSet()
        val listIds = NearbyEvents.find(result.visible, 43.89, -.5, 30.0, clock.instant()).map { it.event.id }.toSet()
        assertEquals(setOf(recurrent.id), mapIds); assertEquals(mapIds, listIds)
    }

    @Test fun noMatchesIsDifferentFromAllMatchesHidden() {
        val store = EventHidingStore(MemoryStorage()); store.hide(event("absent"))
        assertFalse(EventHidingPolicy.partition(emptyList(), store.hiddenKeys.value).allResultsHidden)
        assertFalse(EventHidingPolicy.partition(listOf(event("visible")), store.hiddenKeys.value).allResultsHidden)
    }
}
