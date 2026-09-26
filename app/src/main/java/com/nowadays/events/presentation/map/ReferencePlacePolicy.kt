package com.nowadays.events.presentation.map

import com.nowadays.events.data.location.LocationSuggestion

internal object ReferencePlacePolicy {
    fun fromSuggestion(suggestion: LocationSuggestion): ReferencePlace = ReferencePlace(
        label = suggestion.label.substringBefore(',').trim().ifBlank { suggestion.label },
        latitude = suggestion.latitude,
        longitude = suggestion.longitude,
    )

    fun fromGps(latitude: Double, longitude: Double): ReferencePlace =
        ReferencePlace("moi", latitude, longitude, "gps")

    fun title(place: ReferencePlace): String =
        if (place.kind == "gps") "Autour de moi" else "Autour de ${place.label}"

    fun gpsFailureMessage(permissionGranted: Boolean): String = if (permissionGranted) {
        "Position indisponible. Réessayez ou choisissez une ville."
    } else {
        "Localisation refusée. Recherchez une ville ou choisissez un point sur la carte."
    }
}
