package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import java.text.Normalizer
import java.net.URI
import kotlin.math.*

data class EventFamily(val main: Event, val children: List<Event>, val sourceUrls: List<String>) {
    val events: List<Event> get() = listOf(main) + children
}

object EventFamilyGrouper {
    fun group(events: List<Event>): List<EventFamily> {
        val sourceBundles = events.groupBy(Event::sourceUrl).values.map { sourceEvents ->
            val parent = sourceEvents.maxWithOrNull(
                compareBy<Event> { it.endsAt.epochSecond - it.startsAt.epochSecond }
                    .thenBy { it.title.length },
            ) ?: return@map null
            Bundle(
                parent,
                sourceEvents,
                parent.endsAt.epochSecond - parent.startsAt.epochSecond >= 36 * 60 * 60,
            )
        }.filterNotNull().toMutableList()

        val components = mutableListOf<MutableList<Bundle>>()
        sourceBundles.forEach { bundle ->
            val matching = components.filter { component -> component.any { sameFestival(it.parent, bundle.parent) } }
            if (matching.isEmpty()) components += mutableListOf(bundle)
            else {
                val target = matching.first()
                target += bundle
                matching.drop(1).forEach { other -> target += other; components.remove(other) }
            }
        }
        return components.map { bundles ->
            val mainCandidates = bundles.filter(Bundle::hasPrincipal).map(Bundle::parent)
                .ifEmpty { bundles.map(Bundle::parent) }
            val main = mainCandidates.maxWith(
                compareBy<Event> { it.endsAt.epochSecond - it.startsAt.epochSecond }
                    .thenBy { it.fullDescription?.length ?: it.shortDescription.length },
            )
            val secondaryParents = bundles.filter(Bundle::hasPrincipal).map(Bundle::parent)
                .filterNot { it.id == main.id }.map(Event::id).toSet()
            val children = deduplicateChildren(bundles.flatMap(Bundle::events)
                .filterNot { it.id == main.id || it.id in secondaryParents }
                .sortedBy(Event::startsAt))
            EventFamily(main, children, bundles.flatMap(Bundle::events).map(Event::sourceUrl).distinct())
        }
    }

    private fun deduplicateChildren(events: List<Event>): List<Event> {
        val result = mutableListOf<Event>()
        events.forEach { event ->
            val duplicateIndex = result.indexOfFirst { existing ->
                abs(existing.startsAt.epochSecond - event.startsAt.epochSecond) <= 16 * 60 * 60 &&
                    distanceKm(existing, event) <= 2.0 &&
                    significantTokens(existing.title).intersect(significantTokens(event.title)).isNotEmpty()
            }
            if (duplicateIndex < 0) result += event
            else {
                val existing = result[duplicateIndex]
                val existingScore = existing.shortDescription.length + (existing.fullDescription?.length ?: 0)
                val newScore = event.shortDescription.length + (event.fullDescription?.length ?: 0)
                if (newScore > existingScore) result[duplicateIndex] = event
            }
        }
        return result
    }

    private fun sameFestival(a: Event, b: Event): Boolean {
        if (a.sourceUrl == b.sourceUrl) return true
        val overlaps = a.startsAt <= b.endsAt && b.startsAt <= a.endsAt
        if (!overlaps || distanceKm(a, b) > 20.0) return false
        // Keep only the final URL slug as a weak identity signal. Domain names and
        // common agenda paths would merge every item published by one tourism office.
        val common = identityTokens(a).intersect(identityTokens(b))
        return common.size >= 2 || common.any { it.length >= 8 }
    }

    private fun identityTokens(event: Event): Set<String> {
        val slug = runCatching { URI(event.sourceUrl).path.substringAfterLast('/') }.getOrDefault("")
        return significantTokens("${event.title} $slug")
    }

    private fun significantTokens(value: String): Set<String> = normalize(value).split(' ')
        .filter { it.length >= 4 && it !in STOP_WORDS && it.toIntOrNull() == null }.toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun distanceKm(a: Event, b: Event): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h))
    }

    private data class Bundle(val parent: Event, val events: List<Event>, val hasPrincipal: Boolean)

    private val STOP_WORDS = setOf(
        "avec", "dans", "pour", "sans", "sous", "entre", "programme", "complet", "fetes",
        "festival", "edition", "jours", "mont", "marsan", "juillet", "cette", "tout",
    )
}
