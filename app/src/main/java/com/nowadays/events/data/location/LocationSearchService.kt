package com.nowadays.events.data.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocationSuggestion(val label: String, val latitude: Double, val longitude: Double)

class LocationSearchService @Inject constructor(@ApplicationContext context: Context) {
    private val geocoder = Geocoder(context, Locale.FRANCE)

    @Suppress("DEPRECATION")
    suspend fun search(query: String, near: String? = null): List<LocationSuggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 3 || !Geocoder.isPresent()) return@withContext emptyList()
        runCatching {
            geocoder.getFromLocationName(listOfNotNull(query.trim(), near?.takeIf { it.isNotBlank() }).joinToString(", "), 6)
                .orEmpty().map { address ->
                    LocationSuggestion(
                        label = listOfNotNull(address.featureName, address.thoroughfare, address.locality, address.postalCode)
                            .distinct().joinToString(", ").ifBlank { address.getAddressLine(0) },
                        latitude = address.latitude,
                        longitude = address.longitude,
                    )
                }.distinctBy { it.label }
        }.getOrDefault(emptyList())
    }
}
