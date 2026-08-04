package com.nowadays.events.map

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMarkerPolicyTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 7, 29)

    @Test fun `today and tomorrow are fully visible and largest`() {
        assertEquals(MarkerPriority(255, 26f), MapMarkerPolicy.priority(event("2026-07-29T10:00:00Z"), today, zone))
        assertEquals(MarkerPriority(255, 26f), MapMarkerPolicy.priority(event("2026-07-30T10:00:00Z"), today, zone))
    }

    @Test fun `distant events are strongly faded and smaller`() {
        assertEquals(MarkerPriority(70, 17f), MapMarkerPolicy.priority(event("2026-08-07T10:00:00Z"), today, zone))
    }

    @Test fun `long running threshold starts at thirty six hours`() {
        assertFalse(MapMarkerPolicy.isLongRunning(event("2026-07-29T10:00:00Z", "2026-07-30T21:59:59Z")))
        assertTrue(MapMarkerPolicy.isLongRunning(event("2026-07-29T10:00:00Z", "2026-07-30T22:00:00Z")))
    }

    @Test fun `recurring event uses today while it is active`() {
        val recurring = event("2026-07-01T10:00:00Z", "2026-08-31T18:00:00Z")
        assertTrue(MapMarkerPolicy.isRecurring(recurring))
        assertEquals(today, MapMarkerPolicy.displayDate(recurring, today, zone))
    }

    @Test fun `recurring event keeps start date before it begins`() {
        val recurring = event("2026-08-01T10:00:00Z", "2026-09-30T18:00:00Z")
        assertEquals(LocalDate.of(2026, 8, 1), MapMarkerPolicy.displayDate(recurring, today, zone))
    }

    private fun event(start: String, end: String = "2026-07-29T12:00:00Z") = Event(
        id = start, title = "Test", shortDescription = "Test", fullDescription = null,
        category = EventCategory.CULTURE, startsAt = Instant.parse(start), endsAt = Instant.parse(end),
        venueName = "Lieu", address = "Mont-de-Marsan", latitude = 43.89, longitude = -0.50,
        sourceUrl = "https://example.invalid", imageUrl = null, organizer = null,
        price = EventPrice.Free, updatedAt = Instant.EPOCH, origin = DataOrigin.DEMO,
    )
}
