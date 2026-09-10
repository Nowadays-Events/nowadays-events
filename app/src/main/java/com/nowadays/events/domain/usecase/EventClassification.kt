package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice

enum class EventKind { CONCERT, SPORT, MARKET, EXHIBITION, FAMILY, LOCAL_FESTIVAL, SHOW, WORKSHOP, OTHER }
enum class EventAudience { ALL, FAMILY, CHILDREN, ADULTS, ATHLETES, SENIORS, UNKNOWN }
enum class EventMood { FESTIVE, FRIENDLY, CULTURAL, SPORTY, CALM, UNKNOWN }
enum class EventScale { NEIGHBORHOOD, LOCAL, INTERCOMMUNAL, REGIONAL, NATIONAL, INTERNATIONAL, UNKNOWN }
enum class Confidence { LOW, MEDIUM, HIGH }

data class EventClassification(
    val kind: EventKind,
    val audience: EventAudience,
    val mood: EventMood,
    val scale: EventScale,
    val confidence: Confidence,
) {
    fun summary(): String = buildList {
        add(when (kind) {
            EventKind.CONCERT -> "Concert"
            EventKind.SPORT -> "Événement sportif"
            EventKind.MARKET -> "Marché"
            EventKind.EXHIBITION -> "Exposition"
            EventKind.FAMILY -> "Sortie familiale"
            EventKind.LOCAL_FESTIVAL -> "Fête locale"
            EventKind.SHOW -> "Spectacle"
            EventKind.WORKSHOP -> "Atelier"
            EventKind.OTHER -> "Événement"
        })
        when (audience) {
            EventAudience.FAMILY, EventAudience.CHILDREN -> add("adapté aux familles")
            EventAudience.ATHLETES -> add("pour les sportifs")
            else -> Unit
        }
        when (mood) {
            EventMood.FESTIVE -> add("ambiance festive")
            EventMood.FRIENDLY -> add("ambiance conviviale")
            EventMood.CULTURAL -> add("à caractère culturel")
            EventMood.SPORTY -> add("ambiance sportive")
            EventMood.CALM -> add("ambiance calme")
            EventMood.UNKNOWN -> Unit
        }
    }.joinToString(" · ")
}

object EventClassifier {
    fun classify(event: Event): EventClassification {
        val text = listOfNotNull(event.title, event.shortDescription, event.fullDescription, event.organizer, event.venueName)
            .joinToString(" ").lowercase()
        fun has(vararg words: String) = words.any(text::contains)
        val kind = when {
            has("concert", "dj set", "musique", "fanfare") -> EventKind.CONCERT
            has("course", "run", "sport", "tournoi", "match") || event.category == EventCategory.SPORT -> EventKind.SPORT
            has("marché", "brocante", "vide-grenier", "amap") -> EventKind.MARKET
            has("expo", "musée", "galerie") -> EventKind.EXHIBITION
            has("atelier", "stage", "initiation") -> EventKind.WORKSHOP
            has("spectacle", "théâtre", "comédie", "ciné") -> EventKind.SHOW
            has("fête", "festival", "fanfare") -> EventKind.LOCAL_FESTIVAL
            event.category == EventCategory.FAMILY -> EventKind.FAMILY
            else -> EventKind.OTHER
        }
        val audience = when {
            has("enfant", "jeunesse") -> EventAudience.CHILDREN
            has("famille", "familial") || event.category == EventCategory.FAMILY -> EventAudience.FAMILY
            has("senior") -> EventAudience.SENIORS
            kind == EventKind.SPORT -> EventAudience.ATHLETES
            has("adulte", "18 ans") -> EventAudience.ADULTS
            has("tout public") -> EventAudience.ALL
            else -> EventAudience.UNKNOWN
        }
        val mood = when {
            kind == EventKind.SPORT -> EventMood.SPORTY
            kind in setOf(EventKind.CONCERT, EventKind.LOCAL_FESTIVAL) -> EventMood.FESTIVE
            kind in setOf(EventKind.EXHIBITION, EventKind.SHOW) -> EventMood.CULTURAL
            has("convivial", "rencontre", "partage") -> EventMood.FRIENDLY
            has("lecture", "méditation", "nature") -> EventMood.CALM
            else -> EventMood.UNKNOWN
        }
        val scale = when {
            has("international") -> EventScale.INTERNATIONAL
            has("national") -> EventScale.NATIONAL
            has("régional", "regional") -> EventScale.REGIONAL
            has("intercommunal", "agglomération") -> EventScale.INTERCOMMUNAL
            has("quartier") -> EventScale.NEIGHBORHOOD
            else -> EventScale.LOCAL
        }
        val evidence = listOf(kind != EventKind.OTHER, audience != EventAudience.UNKNOWN, mood != EventMood.UNKNOWN,
            event.category != EventCategory.COMMUNITY, event.organizer != null, event.price !is EventPrice.Unknown,
            event.sourceUrls.distinct().size > 1).count { it }
        val confidence = when { evidence >= 5 -> Confidence.HIGH; evidence >= 2 -> Confidence.MEDIUM; else -> Confidence.LOW }
        return EventClassification(kind, audience, mood, scale, confidence)
    }
}
