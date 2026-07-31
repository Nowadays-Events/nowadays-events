package com.nowadays.events.map

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test

class EventClusterGeometryTest {
    @Test
    fun `cluster anchor is always an actual event position`() {
        val events = listOf(event("west", -0.54), event("center", -0.50), event("east", -0.39))
        val anchor = EventClusterGeometry.anchor(events)
        assertTrue(events.any { it.latitude == anchor.latitude && it.longitude == anchor.longitude })
    }

    private fun event(id: String, longitude: Double) = Event(
        id = id,
        title = id,
        shortDescription = id,
        fullDescription = null,
        category = EventCategory.CULTURE,
        startsAt = Instant.parse("2026-07-29T10:00:00Z"),
        endsAt = Instant.parse("2026-07-29T12:00:00Z"),
        venueName = "Mont-de-Marsan",
        address = "Mont-de-Marsan",
        latitude = 43.89,
        longitude = longitude,
        sourceUrl = "https://example.invalid/$id",
        imageUrl = null,
        organizer = null,
        price = EventPrice.Free,
        updatedAt = Instant.EPOCH,
        origin = DataOrigin.DEMO,
    )
}
