package com.nowadays.events.map

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventStatus

internal data class ClusterMarkerStyle(
    val hasCancelledEvent: Boolean,
    val allEventsCancelled: Boolean,
)

internal object ClusterMarkerPolicy {
    fun style(events: List<Event>) = ClusterMarkerStyle(
        hasCancelledEvent = events.any { it.status == EventStatus.CANCELLED },
        allEventsCancelled = events.isNotEmpty() && events.all { it.status == EventStatus.CANCELLED },
    )
}
