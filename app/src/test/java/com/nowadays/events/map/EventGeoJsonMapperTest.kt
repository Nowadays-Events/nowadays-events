package com.nowadays.events.map

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class EventGeoJsonMapperTest {
    @Test fun createsOnePointFeaturePerEvent() {
        val event = Event(
            id = "event-1", title = "Test", shortDescription = "Test", fullDescription = null,
            category = EventCategory.CULTURE, startsAt = Instant.parse("2026-07-20T10:00:00Z"),
            endsAt = Instant.parse("2026-07-20T12:00:00Z"), venueName = "Lieu", address = "Paris",
            latitude = 48.8566, longitude = 2.3522, sourceUrl = "https://example.invalid/1",
            imageUrl = null, organizer = null, price = EventPrice.Free,
            updatedAt = Instant.parse("2026-07-20T08:00:00Z"), origin = DataOrigin.DEMO,
        )
        val feature = EventGeoJsonMapper.map(listOf(event)).features()!!.single()
        val point = feature.geometry() as org.maplibre.geojson.Point
        assertEquals("event-1", feature.getStringProperty(EventGeoJsonMapper.EVENT_ID_PROPERTY))
        assertEquals(2.3522, point.longitude(), 0.0)
        assertEquals(48.8566, point.latitude(), 0.0)
    }

    @Test fun `separates events sharing the exact same coordinates`() {
        val first = event("one")
        val second = event("two")
        val features = EventGeoJsonMapper.map(listOf(first, second)).features().orEmpty()
        val points = features.map { it.geometry() as Point }
        assertTrue(points[0].latitude() != points[1].latitude() || points[0].longitude() != points[1].longitude())
    }

    @Test fun `exports cancellation status for map styling`() {
        val feature = EventGeoJsonMapper.map(listOf(event("cancelled").copy(status = EventStatus.CANCELLED)))
            .features().orEmpty().single()
        assertEquals("CANCELLED", feature.getStringProperty("status"))
        assertEquals(0.5, feature.getNumberProperty("event_opacity").toDouble(), 0.0)
    }

    private fun event(id: String) = Event(
        id = id, title = id, shortDescription = "demo", fullDescription = null,
        category = EventCategory.CULTURE, startsAt = Instant.parse("2026-07-20T10:00:00Z"),
        endsAt = Instant.parse("2026-07-20T12:00:00Z"), venueName = "Lieu", address = "Paris",
        latitude = 48.8566, longitude = 2.3522, sourceUrl = "https://example.invalid/$id",
        imageUrl = null, organizer = null, price = EventPrice.Free, updatedAt = Instant.EPOCH,
        origin = DataOrigin.DEMO,
    )
}
