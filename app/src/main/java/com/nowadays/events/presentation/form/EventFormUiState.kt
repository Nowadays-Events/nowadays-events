package com.nowadays.events.presentation.form

import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.EventInputErrors
import com.nowadays.events.data.importer.ImportedEvent
import com.nowadays.events.data.location.LocationSuggestion

data class EventFormUiState(
    val importUrl: String = "",
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val importCandidates: List<ImportedEvent> = emptyList(),
    val city: String = "",
    val citySuggestions: List<LocationSuggestion> = emptyList(),
    val venueSuggestions: List<LocationSuggestion> = emptyList(),
    val title: String = "",
    val shortDescription: String = "",
    val fullDescription: String = "",
    val category: EventCategory = EventCategory.CULTURE,
    val startsAt: String = "",
    val endsAt: String = "",
    val venueName: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val sourceUrl: String = "",
    val organizer: String = "",
    val imageUrl: String = "",
    val priceEuros: String = "",
    val isFree: Boolean = true,
    val errors: EventInputErrors = EventInputErrors(),
    val isSaving: Boolean = false,
)

enum class EventFormField {
    IMPORT_URL, CITY, TITLE, SHORT_DESCRIPTION, FULL_DESCRIPTION, STARTS_AT, ENDS_AT, VENUE, ADDRESS,
    LATITUDE, LONGITUDE, SOURCE_URL, ORGANIZER, IMAGE_URL, PRICE_EUROS,
}
