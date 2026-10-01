package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventScheduleType
import java.text.Normalizer
import java.net.URI
import kotlin.math.*

data class EventFamily(val main: Event, val children: List<Event>, val sourceUrls: List<String>) {
    val events: List<Event> get() = listOf(main) + children
}

object EventFamilyGrouper {
    fun group(events: List<Event>): List<EventFamily> {
        val principals = events.filter(::isPrincipal)
        val principalGroups = mutableListOf<MutableList<Event>>()
        principals.forEach { principal ->
            val matching = principalGroups.filter { group -> group.any { samePrincipal(it, principal) } }
            if (matching.size == 1) matching.single() += principal
            else principalGroups += mutableListOf(principal)
        }
        val families = principalGroups.map { group ->
            val main = group.maxWith(compareBy<Event> { durationSeconds(it) }.thenBy { it.shortDescription.length })
            FamilyBuilder(main, group.filterNot { it.id == main.id }.toMutableList())
        }
        val principalIds = principals.map(Event::id).toSet()
        val standalone = mutableListOf<Event>()
        events.filterNot { it.id in principalIds }.forEach { candidate ->
            val matching = families.filter { strongChildRelation(it.main, candidate) }
            // Un lien ambigu ne retire jamais une fiche de la liste principale.
            if (matching.size == 1) matching.single().children += candidate else standalone += candidate
        }
        return families.map { family ->
            EventFamily(
                family.main,
                family.children.sortedBy(Event::startsAt),
                (listOf(family.main) + family.children).flatMap(Event::sourceUrls).distinct(),
            )
        } + standalone.map { EventFamily(it, emptyList(), it.sourceUrls) }
    }

    private fun isPrincipal(event: Event) =
        event.scheduleType == EventScheduleType.CONTINUOUS && durationSeconds(event) >= 36 * 60 * 60

    private fun samePrincipal(a: Event, b: Event): Boolean =
        sourceHost(a) != sourceHost(b) && normalize(a.title) == normalize(b.title) &&
            titleTokens(a).isNotEmpty() && a.startsAt < b.endsAt && b.startsAt < a.endsAt &&
            distanceKm(a, b) <= 2.0

    private fun strongChildRelation(parent: Event, child: Event): Boolean {
        if (child.scheduleType != EventScheduleType.SINGLE || child.id == parent.id) return false
        if (child.startsAt < parent.startsAt || child.endsAt > parent.endsAt) return false
        if (distanceKm(parent, child) > 5.0) return false
        if (!hasProgramCue(child)) return false
        val shared = titleTokens(parent).intersect(identityTokens(child))
        return shared.size >= 2 || shared.any { it.length >= 8 }
    }

    private fun hasProgramCue(event: Event): Boolean {
        val slug = runCatching { URI(event.sourceUrl).path.substringAfterLast('/') }.getOrDefault("")
        return normalize("${event.title} $slug").split(' ').any { it in PROGRAM_CUES }
    }

    private fun titleTokens(event: Event) = significantTokens(event.title)
    private fun identityTokens(event: Event) = titleTokens(event) + sourceSlug(event)
    private fun sourceSlug(event: Event): Set<String> = significantTokens(
        runCatching { URI(event.sourceUrl).path.substringAfterLast('/') }.getOrDefault(""),
    )
    private fun sourceHost(event: Event): String = runCatching { URI(event.sourceUrl).host.orEmpty() }.getOrDefault("")

    private fun significantTokens(value: String): Set<String> = normalize(value).split(' ')
        .filter { it.length >= 4 && it !in STOP_WORDS && it.toIntOrNull() == null }.toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun durationSeconds(event: Event) = event.endsAt.epochSecond - event.startsAt.epochSecond

    private fun distanceKm(a: Event, b: Event): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h))
    }

    private data class FamilyBuilder(val main: Event, val children: MutableList<Event>)

    private val STOP_WORDS = setOf(
        "avec", "dans", "pour", "sans", "sous", "entre", "programme", "complet", "fetes",
        "festival", "edition", "jours", "mont", "marsan", "juillet", "cette", "tout",
        "agenda", "evenements",
    )
    private val PROGRAM_CUES = setOf(
        "programme", "ouverture", "journee", "concert", "cloture", "spectacle", "animation",
    )
}
