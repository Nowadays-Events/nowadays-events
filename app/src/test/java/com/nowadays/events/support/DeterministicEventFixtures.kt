package com.nowadays.events.support

import com.nowadays.events.domain.model.*
import java.time.Instant

object DeterministicEventFixtures {
    private val base = Instant.parse("2026-09-01T10:00:00Z")

    fun events(count: Int): List<Event> = List(count) { index ->
        val concentration = index % 5
        val offset = if (index % 4 == 0) 0.0 else (index % 17) * 0.00003
        event(
            id = "load-$index",
            latitude = 43.86 + concentration * 0.02 + offset,
            longitude = -0.54 + concentration * 0.02 + offset,
            status = if (index % 97 == 0) EventStatus.CANCELLED else EventStatus.ACTIVE,
            occurrenceCount = if (index % 31 == 0) 8 else 1,
        )
    }

    fun family(): List<Event> = listOf(
        event("parent", title = "Festival principal", sourceUrl = "https://example.invalid/festival"),
        event("child-1", title = "Festival principal - concert", sourceUrl = "https://example.invalid/festival"),
        event("child-2", title = "Festival principal - clôture", sourceUrl = "https://example.invalid/festival"),
    )

    fun event(
        id: String,
        title: String = id,
        latitude: Double = 43.89,
        longitude: Double = -0.50,
        sourceUrl: String = "https://example.invalid/$id",
        status: EventStatus = EventStatus.ACTIVE,
        occurrenceCount: Int = 1,
    ) = Event(
        id, title, "Description déterministe", null, EventCategory.CULTURE,
        base, base.plusSeconds(7200), "Lieu", "Mont-de-Marsan", latitude, longitude,
        sourceUrl, null, null, EventPrice.Free, base, DataOrigin.DEMO,
        status = status, occurrenceCount = occurrenceCount,
        nextOccurrenceAt = if (occurrenceCount > 1) base.plusSeconds(86400) else null,
    )
}
