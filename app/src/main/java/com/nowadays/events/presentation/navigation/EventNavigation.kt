package com.nowadays.events.presentation.navigation

/** An explicit map request: highlighting a marker must never imply opening its detail. */
data class MapFocusRequest(
    val eventId: String,
    val latitude: Double,
    val longitude: Double,
    val openDetail: Boolean = false,
)

internal fun detailRoute(eventId: String): String = "event/${android.net.Uri.encode(eventId)}"

internal fun mapRoute(request: MapFocusRequest): String = buildString {
    append("map?lat=${request.latitude}&lon=${request.longitude}")
    append("&eventId=${android.net.Uri.encode(request.eventId)}")
    append("&openDetail=${request.openDetail}")
}
