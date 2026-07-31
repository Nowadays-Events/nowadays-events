package com.nowadays.events.domain.usecase

import java.net.URI
import java.time.Instant
import javax.inject.Inject

data class EventInput(
    val title: String,
    val shortDescription: String,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val sourceUrl: String,
    val priceCents: Int?,
)

data class EventInputErrors(
    val title: String? = null,
    val shortDescription: String? = null,
    val dates: String? = null,
    val venueName: String? = null,
    val address: String? = null,
    val coordinates: String? = null,
    val sourceUrl: String? = null,
    val price: String? = null,
) {
    val isEmpty get() = listOf(title, shortDescription, dates, venueName, address, coordinates, sourceUrl, price).all { it == null }
}

class ValidateEventInput @Inject constructor() {
    operator fun invoke(input: EventInput): EventInputErrors = EventInputErrors(
        title = if (input.title.trim().length < 3) "Le titre doit contenir au moins 3 caractères" else null,
        shortDescription = if (input.shortDescription.trim().length < 10) "La description doit contenir au moins 10 caractères" else null,
        dates = when {
            input.startsAt == null || input.endsAt == null -> "Dates ou heures invalides"
            input.endsAt < input.startsAt -> "La fin doit être postérieure au début"
            else -> null
        },
        venueName = if (input.venueName.isBlank()) "Le nom du lieu est requis" else null,
        address = if (input.address.isBlank()) "L’adresse est requise" else null,
        coordinates = when {
            input.latitude == null || input.longitude == null -> "Coordonnées invalides"
            input.latitude !in -90.0..90.0 || input.longitude !in -180.0..180.0 -> "Coordonnées hors limites"
            else -> null
        },
        sourceUrl = if (!input.sourceUrl.isHttpUrl()) "Une URL source HTTP(S) valide est requise" else null,
        price = if (input.priceCents != null && input.priceCents < 0) "Le prix ne peut pas être négatif" else null,
    )
}

private fun String.isHttpUrl(): Boolean = runCatching {
    val uri = URI(trim())
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

