package com.nowadays.events.presentation.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** An explicit map request: highlighting a marker must never imply opening its detail. */
data class MapFocusRequest(
    val eventId: String,
    val latitude: Double,
    val longitude: Double,
    val openDetail: Boolean = false,
)

private fun encodeRouteValue(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

internal fun detailRoute(eventId: String): String = "event/${encodeRouteValue(eventId)}"

internal fun mapRoute(request: MapFocusRequest): String = buildString {
    append("map?lat=${request.latitude}&lon=${request.longitude}")
    append("&eventId=${encodeRouteValue(request.eventId)}")
    append("&openDetail=${request.openDetail}")
}
