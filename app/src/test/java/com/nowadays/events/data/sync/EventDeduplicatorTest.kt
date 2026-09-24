package com.nowadays.events.data.sync

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventScheduleType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeduplicatorTest {
    private val deduplicator = EventDeduplicator()

    @Test fun matchesCanonicalSourceUrlForTheSameOccurrence() {
        val existing = event(id = "a", url = "https://example.invalid/event/")
        val candidate = event(id = "b", url = "https://example.invalid/event")
        assertEquals(DuplicateMatch.SAME_SOURCE_URL, deduplicator.find(candidate, listOf(existing)).match)
    }

    @Test fun `different events sharing a collection URL are not merged`() {
        val existing = event(id = "a", title = "Visite guidée", url = "https://example.invalid/agenda")
        val candidate = event(id = "b", title = "Concert", url = "https://example.invalid/agenda")

        assertEquals(DuplicateMatch.NONE, deduplicator.find(candidate, listOf(existing)).match)
    }

    @Test fun `two occurrences of a recurring activity remain distinct`() {
        val existing = event(id = "a", scheduleType = EventScheduleType.RECURRING)
        val candidate = event(
            id = "b",
            startsAt = Instant.parse("2026-08-01T19:00:00Z"),
            scheduleType = EventScheduleType.RECURRING,
        )

        assertEquals(DuplicateMatch.NONE, deduplicator.find(candidate, listOf(existing)).match)
    }

    @Test fun `same event from multiple sources is matched by its occurrence fingerprint`() {
        val existing = event(id = "a", url = "https://first.example/event")
        val candidate = event(id = "b", url = "https://second.example/event")

        assertEquals(DuplicateMatch.SAME_FINGERPRINT, deduplicator.find(candidate, listOf(existing)).match)
        val merged = deduplicator.merge(existing, candidate)
        assertEquals("a", merged.id)
        assertTrue(merged.sourceUrls.containsAll(listOf(existing.sourceUrl, candidate.sourceUrl)))
    }

    @Test fun detectsNormalizedFingerprint() {
        val existing = event(id = "a", title = "Fête d'été")
        val candidate = event(id = "b", title = "Fete d ete", url = "https://example.invalid/other")
        assertEquals(DuplicateMatch.SAME_FINGERPRINT, deduplicator.find(candidate, listOf(existing)).match)
    }

    @Test fun unrelatedEventIsNotDuplicate() {
        val existing = event(id = "a")
        val candidate = event(id = "b", title = "Autre titre", url = "https://example.invalid/other")
        assertEquals(DuplicateMatch.NONE, deduplicator.find(candidate, listOf(existing)).match)
    }

    private fun event(
        id: String,
        title: String = "Fête d'été",
        url: String = "https://example.invalid/event",
        startsAt: Instant = Instant.parse("2026-08-01T18:00:00Z"),
        scheduleType: EventScheduleType = EventScheduleType.SINGLE,
    ) = Event(
        id = id, title = title, shortDescription = "Description test", fullDescription = null,
        category = EventCategory.COMMUNITY, startsAt = startsAt,
        endsAt = startsAt.plusSeconds(7_200), venueName = "Place centrale", address = "Paris",
        latitude = 48.85, longitude = 2.35, sourceUrl = url, imageUrl = null, organizer = null,
        price = EventPrice.Free, updatedAt = Instant.parse("2026-07-22T10:00:00Z"), origin = DataOrigin.AUTOMATIC,
        scheduleType = scheduleType,
    )
}
