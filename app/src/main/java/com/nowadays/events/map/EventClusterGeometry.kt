package com.nowadays.events.map

import com.nowadays.events.domain.model.Event

internal object EventClusterGeometry {
    fun anchor(events: List<Event>): Event {
        require(events.isNotEmpty())
        val averageLatitude = events.map(Event::latitude).average()
        val averageLongitude = events.map(Event::longitude).average()
        return events.minBy { event ->
            val dLat = event.latitude - averageLatitude
            val dLon = event.longitude - averageLongitude
            dLat * dLat + dLon * dLon
        }
    }
}
