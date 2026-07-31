package com.nowadays.events.data.remote

import com.nowadays.events.domain.model.Event
import java.time.Instant

interface EventSource {
    val name: String
    suspend fun fetchEvents(updatedSince: Instant? = null): List<Event>
}

