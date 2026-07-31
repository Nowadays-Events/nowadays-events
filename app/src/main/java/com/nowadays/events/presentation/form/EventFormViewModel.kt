package com.nowadays.events.presentation.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.data.importer.EventLinkImporter
import com.nowadays.events.data.importer.ImportedEvent
import com.nowadays.events.data.location.LocationSearchService
import com.nowadays.events.data.location.LocationSuggestion
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.repository.EventRepository
import com.nowadays.events.domain.usecase.EventInput
import com.nowadays.events.domain.usecase.EventInputErrors
import com.nowadays.events.domain.usecase.ValidateEventInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@HiltViewModel
class EventFormViewModel @Inject constructor(
    private val repository: EventRepository,
    private val validator: ValidateEventInput,
    private val clock: Clock,
    private val linkImporter: EventLinkImporter,
    private val locationSearch: LocationSearchService,
) : ViewModel() {
    private var citySearchJob: Job? = null
    private var venueSearchJob: Job? = null
    private val _state = MutableStateFlow(EventFormUiState())
    val state = _state.asStateFlow()
    private val savedEvents = Channel<Pair<Double, Double>>(Channel.BUFFERED)
    val saved = savedEvents.receiveAsFlow()

    fun update(field: EventFormField, value: String) = _state.update { current ->
        when (field) {
            EventFormField.IMPORT_URL -> current.copy(importUrl = value, importMessage = null)
            EventFormField.CITY -> current.copy(city = value)
            EventFormField.TITLE -> current.copy(title = value)
            EventFormField.SHORT_DESCRIPTION -> current.copy(shortDescription = value)
            EventFormField.FULL_DESCRIPTION -> current.copy(fullDescription = value)
            EventFormField.STARTS_AT -> current.copy(startsAt = value)
            EventFormField.ENDS_AT -> current.copy(endsAt = value)
            EventFormField.VENUE -> current.copy(venueName = value)
            EventFormField.ADDRESS -> current.copy(address = value)
            EventFormField.LATITUDE -> current.copy(latitude = value)
            EventFormField.LONGITUDE -> current.copy(longitude = value)
            EventFormField.SOURCE_URL -> current.copy(sourceUrl = value)
            EventFormField.ORGANIZER -> current.copy(organizer = value)
            EventFormField.IMAGE_URL -> current.copy(imageUrl = value)
            EventFormField.PRICE_EUROS -> current.copy(priceEuros = value)
        }
    }

    fun selectCategory(category: EventCategory) = _state.update { it.copy(category = category) }
    fun setFree(isFree: Boolean) = _state.update { it.copy(isFree = isFree, priceEuros = if (isFree) "" else it.priceEuros) }

    fun searchCity(value: String) {
        _state.update { it.copy(city = value, citySuggestions = emptyList()) }
        citySearchJob?.cancel()
        citySearchJob = viewModelScope.launch {
            delay(350)
            val results = locationSearch.search(value)
            _state.update { it.copy(citySuggestions = results) }
        }
    }

    fun selectCity(suggestion: LocationSuggestion) = _state.update {
        it.copy(city = suggestion.label, address = suggestion.label, latitude = suggestion.latitude.toString(),
            longitude = suggestion.longitude.toString(), citySuggestions = emptyList())
    }

    fun searchVenue(value: String) {
        _state.update { it.copy(venueName = value, venueSuggestions = emptyList()) }
        venueSearchJob?.cancel()
        venueSearchJob = viewModelScope.launch {
            delay(350)
            val results = locationSearch.search(value, _state.value.city)
            _state.update { it.copy(venueSuggestions = results) }
        }
    }

    fun selectVenue(suggestion: LocationSuggestion) = _state.update {
        it.copy(venueName = suggestion.label.substringBefore(','), address = suggestion.label,
            latitude = suggestion.latitude.toString(), longitude = suggestion.longitude.toString(), venueSuggestions = emptyList())
    }

    fun setMapLocation(latitude: Double, longitude: Double) = _state.update {
        it.copy(latitude = latitude.toString(), longitude = longitude.toString(), address = it.address.ifBlank { "Point sélectionné sur la carte" })
    }

    fun importFromLink() {
        val url = _state.value.importUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(importMessage = "Collez d’abord le lien de l’événement") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importMessage = null) }
            linkImporter.import(url).fold(
                onSuccess = { candidates ->
                    _state.update { it.copy(isImporting = false, importCandidates = candidates,
                        importMessage = if (candidates.size > 1) "${candidates.size} événements possibles trouvés" else null) }
                    if (candidates.size == 1) applyCandidate(candidates.first())
                },
                onFailure = { error ->
                    _state.update { it.copy(isImporting = false, importMessage = error.message ?: "Impossible d’analyser ce lien") }
                },
            )
        }
    }

    fun selectImportCandidate(index: Int) {
        _state.value.importCandidates.getOrNull(index)?.let(::applyCandidate)
    }

    private fun applyCandidate(data: ImportedEvent) {
        val url = _state.value.importUrl.trim()
        _state.update { current -> current.copy(
                            title = data.title.ifBlank { current.title },
                            shortDescription = data.description.ifBlank { current.shortDescription }.take(240),
                            fullDescription = data.description.ifBlank { current.fullDescription },
                            startsAt = data.startsAt.ifBlank { current.startsAt },
                            endsAt = data.endsAt.ifBlank { current.endsAt },
                            venueName = data.venue.ifBlank { current.venueName },
                            city = data.address.ifBlank { data.venue }.ifBlank { current.city },
                            address = data.address.ifBlank { current.address },
                            latitude = data.latitude.ifBlank { current.latitude },
                            longitude = data.longitude.ifBlank { current.longitude },
                            sourceUrl = url,
                            organizer = data.organizer.ifBlank { current.organizer },
                            imageUrl = data.imageUrl.ifBlank { current.imageUrl },
                            priceEuros = data.priceEuros.ifBlank { current.priceEuros },
                            isFree = data.priceEuros.isBlank(),
                            isImporting = false,
                            importMessage = "Informations trouvées — vérifiez puis complétez si nécessaire",
                            importCandidates = emptyList(),
                        ) }
        if (_state.value.latitude.isBlank() && _state.value.city.isNotBlank()) viewModelScope.launch {
            val current = _state.value
            locationSearch.search(current.venueName.ifBlank { current.city }, current.city).firstOrNull()?.let(::selectVenue)
        }
    }

    fun saveAllImported() {
        val candidates = _state.value.importCandidates
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, importMessage = "Création des événements…") }
            var savedCount = 0
            var lastSavedLocation: Pair<Double, Double>? = null
            candidates.forEach { data ->
                val location = when {
                    data.latitude.toDoubleOrNull() != null && data.longitude.toDoubleOrNull() != null ->
                        data.latitude.toDouble() to data.longitude.toDouble()
                    else -> locationSearch.search(data.venue.ifBlank { data.address }, data.address).firstOrNull()?.let { it.latitude to it.longitude }
                } ?: return@forEach
                val start = data.startsAt.toInstantOrNull() ?: return@forEach
                val end = data.endsAt.toInstantOrNull() ?: return@forEach
                repository.save(Event(
                    id = UUID.randomUUID().toString(), title = data.title.ifBlank { "Événement importé" },
                    shortDescription = data.description.take(240).ifBlank { "Événement importé depuis un programme en ligne." },
                    fullDescription = data.description.ifBlank { null }, category = _state.value.category,
                    startsAt = start, endsAt = end, venueName = data.venue.ifBlank { data.address },
                    address = data.address.ifBlank { data.venue }, latitude = location.first, longitude = location.second,
                    sourceUrl = _state.value.importUrl, imageUrl = data.imageUrl.ifBlank { null },
                    organizer = data.organizer.ifBlank { null }, price = if (data.priceEuros.isBlank()) EventPrice.Free
                        else EventPrice.Paid(data.priceEuros.toCentsOrNull()), updatedAt = clock.instant(), origin = DataOrigin.MANUAL,
                ))
                lastSavedLocation = location
                savedCount++
            }
            _state.update { it.copy(isSaving = false, importMessage = "$savedCount événement(s) créé(s)") }
            if (savedCount > 0) {
                savedEvents.send(requireNotNull(lastSavedLocation))
            }
        }
    }

    fun save() {
        val current = _state.value
        val start = current.startsAt.toInstantOrNull()
        val end = current.endsAt.toInstantOrNull()
        val priceCents = if (current.isFree) null else current.priceEuros.toCentsOrNull()
        val input = EventInput(
            current.title, current.shortDescription, start, end, current.venueName, current.address,
            current.latitude.toDoubleOrNull(), current.longitude.toDoubleOrNull(), current.sourceUrl, priceCents,
        )
        val errors = validator(input).let { base ->
            if (!current.isFree && priceCents == null) base.copy(price = "Prix invalide") else base
        }
        if (!errors.isEmpty) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = EventInputErrors()) }
            val id = UUID.randomUUID().toString()
            repository.save(
                Event(
                    id = id,
                    title = current.title.trim(),
                    shortDescription = current.shortDescription.trim(),
                    fullDescription = current.fullDescription.trim().ifBlank { null },
                    category = current.category,
                    startsAt = requireNotNull(start),
                    endsAt = requireNotNull(end),
                    venueName = current.venueName.trim(),
                    address = current.address.trim(),
                    latitude = requireNotNull(input.latitude),
                    longitude = requireNotNull(input.longitude),
                    sourceUrl = current.sourceUrl.trim(),
                    imageUrl = current.imageUrl.trim().ifBlank { null },
                    organizer = current.organizer.trim().ifBlank { null },
                    price = if (current.isFree) EventPrice.Free else EventPrice.Paid(priceCents),
                    updatedAt = clock.instant(),
                    origin = DataOrigin.MANUAL,
                ),
            )
            _state.update { it.copy(isSaving = false) }
            savedEvents.send(requireNotNull(input.latitude) to requireNotNull(input.longitude))
        }
    }
}

private fun String.toInstantOrNull() = runCatching {
    LocalDateTime.parse(trim()).atZone(ZoneId.systemDefault()).toInstant()
}.getOrNull()

private fun String.toCentsOrNull(): Int? = runCatching {
    BigDecimal(trim().replace(',', '.')).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).intValueExact()
}.getOrNull()
