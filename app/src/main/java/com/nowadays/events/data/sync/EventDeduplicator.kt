package com.nowadays.events.data.sync

import com.nowadays.events.domain.model.Event
import java.text.Normalizer
import java.time.Duration
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class DuplicateMatch { SAME_ID, SAME_SOURCE_URL, SAME_FINGERPRINT, PROBABLE, NONE }

data class DuplicateResult(val event: Event?, val match: DuplicateMatch)

class EventDeduplicator @Inject constructor() {
    fun find(candidate: Event, existing: List<Event>): DuplicateResult {
        existing.firstOrNull { it.id == candidate.id }?.let { return DuplicateResult(it, DuplicateMatch.SAME_ID) }
        existing.firstOrNull { canonicalUrl(it.sourceUrl) == canonicalUrl(candidate.sourceUrl) }
            ?.let { return DuplicateResult(it, DuplicateMatch.SAME_SOURCE_URL) }
        existing.firstOrNull { fingerprint(it) == fingerprint(candidate) }
            ?.let { return DuplicateResult(it, DuplicateMatch.SAME_FINGERPRINT) }
        existing.firstOrNull { probableMatch(it, candidate) }
            ?.let { return DuplicateResult(it, DuplicateMatch.PROBABLE) }
        return DuplicateResult(null, DuplicateMatch.NONE)
    }

    private fun fingerprint(event: Event) = listOf(
        normalize(event.title), normalize(event.venueName), event.startsAt.toString().take(10),
    ).joinToString("|")

    private fun probableMatch(a: Event, b: Event): Boolean =
        normalize(a.title) == normalize(b.title) &&
            Duration.between(a.startsAt, b.startsAt).abs() <= Duration.ofHours(2) &&
            distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) <= 250.0

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun canonicalUrl(value: String) = value.trim().lowercase().removeSuffix("/")

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val x = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 6_371_000 * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}

