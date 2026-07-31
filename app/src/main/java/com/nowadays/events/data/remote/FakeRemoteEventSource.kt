package com.nowadays.events.data.remote

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

class FakeRemoteEventSource @Inject constructor(private val clock: Clock) : EventSource {
    override val name = "fake-remote"

    override suspend fun fetchEvents(updatedSince: java.time.Instant?): List<Event> {
        val start = clock.instant().plus(Duration.ofDays(14))
        val event = Event(
            id = "fake-remote-event-1",
            title = "Événement distant simulé",
            shortDescription = "Donnée simulée pour tester la synchronisation.",
            fullDescription = "Cette donnée ne provient pas d’Internet et reste clairement fictive.",
            category = EventCategory.COMMUNITY,
            startsAt = start,
            endsAt = start.plus(Duration.ofHours(2)),
            venueName = "Hôtel de Ville",
            address = "Place de l'Hôtel de Ville, Paris",
            latitude = 48.8566,
            longitude = 2.3522,
            sourceUrl = "https://example.invalid/fake-remote/event-1",
            imageUrl = null,
            organizer = "Source distante simulée",
            price = EventPrice.Free,
            updatedAt = clock.instant(),
            origin = DataOrigin.AUTOMATIC,
            isFictional = true,
        )
        return if (updatedSince == null || event.updatedAt > updatedSince) listOf(event) else emptyList()
    }
}

