package com.nowadays.events.data.sync

import com.nowadays.events.data.remote.RemoteEventSnapshot
import com.nowadays.events.domain.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class SnapshotValidatorTest {
    private val validator = SnapshotValidator()
    private val now = Instant.parse("2026-09-23T12:00:00Z")

    @Test fun completeHealthySnapshotIsAccepted() = assertTrue(validator.validate(snapshot(10), 10).valid)
    @Test fun unexpectedEmptySnapshotIsRejected() = assertEquals("unexpected_empty_snapshot", validator.validate(snapshot(0), 0, 0).reason)
    @Test fun partialCollectorResultIsRejected() = assertEquals("collector_partial", validator.validate(snapshot(10, health = "partial"), 10).reason)
    @Test fun degradedCollectorResultIsRejected() = assertEquals("collector_degraded", validator.validate(snapshot(10, health = "degraded"), 10).reason)
    @Test fun partialDecodeIsRejected() = assertEquals("partial_decode", validator.validate(snapshot(10).copy(declaredEventCount = 11), 10).reason)
    @Test fun abnormalVolumeDropIsRejected() = assertEquals("abnormal_volume_drop", validator.validate(snapshot(10), 30).reason)
    @Test fun duplicateRemoteIdsAreRejected() {
        val events = List(10) { event(if (it == 9) 0 else it) }
        assertEquals("duplicate_remote_ids", validator.validate(RemoteEventSnapshot(events, now, "ok", 10), 10).reason)
    }
    @Test fun syncStateBecomesStaleDeterministically() {
        val state = SyncState(SyncStatus.SUCCESS, now, now, 10, 10)
        assertFalse(state.isPotentiallyStale(now.plus(Duration.ofHours(6))))
        assertTrue(state.isPotentiallyStale(now.plus(Duration.ofHours(7))))
    }

    private fun snapshot(count: Int, health: String = "ok") = RemoteEventSnapshot(List(count, ::event), now, health, count)
    private fun event(index: Int) = Event(
        id = "api-$index", title = "Event $index", shortDescription = "Description", fullDescription = null,
        category = EventCategory.COMMUNITY, startsAt = now.plusSeconds(index * 3600L), endsAt = now.plusSeconds(index * 3600L + 1800),
        venueName = "Lieu", address = "Adresse", latitude = 43.89, longitude = -0.50,
        sourceUrl = "https://example.invalid/$index", imageUrl = null, organizer = null,
        price = EventPrice.Unknown, updatedAt = now, origin = DataOrigin.AUTOMATIC,
    )
}
