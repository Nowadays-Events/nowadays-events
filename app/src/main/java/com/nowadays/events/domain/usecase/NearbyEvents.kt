package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.Instant

data class NearbyEvent(val event: Event, val distanceKm: Double)

object NearbyEvents {
    fun find(
        events: List<Event>, latitude: Double, longitude: Double, radiusKm: Double,
        now: Instant = Instant.now(),
    ): List<NearbyEvent> {
        if (!valid(latitude, longitude) || radiusKm < 0) return emptyList()
        return events.asSequence()
            .filter { valid(it.latitude, it.longitude) }
            .map { NearbyEvent(it, distanceKm(latitude, longitude, it.latitude, it.longitude)) }
            .filter { it.distanceKm <= radiusKm }
            .sortedWith(compareBy<NearbyEvent> { if (it.event.startsAt <= now && it.event.endsAt >= now) 0 else 1 }
                .thenBy { it.distanceKm }.thenBy { it.event.startsAt }.thenBy { it.event.id })
            .toList()
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadiusKm * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun valid(latitude: Double, longitude: Double) =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
