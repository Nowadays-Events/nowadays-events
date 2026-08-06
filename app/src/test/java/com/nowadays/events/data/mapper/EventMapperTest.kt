package com.nowadays.events.data.mapper

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMapperTest {
    @Test fun roomRoundTripPreservesDomainModel() {
        val event = Event(
            id = "1", title = "Fictif", shortDescription = "Démo", fullDescription = "Description",
            category = EventCategory.MUSIC, startsAt = Instant.parse("2026-07-18T18:00:00Z"),
            endsAt = Instant.parse("2026-07-18T20:00:00Z"), venueName = "Salle", address = "Paris",
            latitude = 48.85, longitude = 2.35, sourceUrl = "https://example.invalid/1", imageUrl = null,
            organizer = "Démo", price = EventPrice.Paid(1200), updatedAt = Instant.parse("2026-07-18T10:00:00Z"),
            origin = DataOrigin.DEMO, goingCount = 12, maybeCount = 4, isFictional = true,
            status = EventStatus.CANCELLED,
        )
        assertEquals(event, event.toEntity().toDomain())
    }
}
