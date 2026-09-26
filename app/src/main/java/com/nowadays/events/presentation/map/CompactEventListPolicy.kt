package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventScheduleType
import com.nowadays.events.domain.model.TimeFilter
import java.time.Instant
import java.time.ZoneId

internal data class EventListSection(val title: String, val events: List<NearbyListItem>)
internal data class NearbyListItem(val event: Event, val distanceKm: Double)

internal object CompactEventListPolicy {
    fun introduction(count: Int, radiusKm: Int, filter: TimeFilter): String = when {
        count == 0 && filter == TimeFilter.TODAY -> "Rien aujourd’hui dans un rayon de $radiusKm km"
        filter == TimeFilter.TODAY -> "$count événement${if (count > 1) "s" else ""} aujourd’hui autour de vous"
        else -> "$count idée${if (count > 1) "s" else ""} à moins de $radiusKm km"
    }

    fun sections(
        items: List<NearbyListItem>,
        filter: TimeFilter,
        now: Instant,
        zoneId: ZoneId,
    ): List<EventListSection> {
        if (items.isEmpty()) return emptyList()
        val today = now.atZone(zoneId).toLocalDate()
        val grouped = linkedMapOf<String, MutableList<NearbyListItem>>()
        items.forEach { item ->
            val event = item.event
            val usefulStart = if (event.scheduleType == EventScheduleType.RECURRING) event.nextOccurrenceAt ?: event.startsAt else event.startsAt
            val day = usefulStart.atZone(zoneId).toLocalDate()
            val isInProgress = event.scheduleType != EventScheduleType.RECURRING && event.startsAt <= now && event.endsAt >= now
            val title = when (filter) {
                TimeFilter.TODAY -> if (isInProgress) "En cours" else "Plus tard aujourd’hui"
                TimeFilter.TOMORROW -> "Demain"
                TimeFilter.THIS_WEEKEND -> "Ce week-end"
                else -> when {
                    isInProgress -> "En cours"
                    day == today -> "Plus tard aujourd’hui"
                    day == today.plusDays(1) -> "Demain"
                    else -> "Prochainement"
                }
            }
            grouped.getOrPut(title) { mutableListOf() } += item
        }
        return grouped.map { EventListSection(it.key, it.value) }
    }

    fun icon(category: EventCategory): String = when (category) {
        EventCategory.CULTURE -> "🎭"
        EventCategory.MUSIC -> "♫"
        EventCategory.SPORT -> "●"
        EventCategory.FOOD -> "◆"
        EventCategory.FAMILY -> "★"
        EventCategory.COMMUNITY -> "●"
        EventCategory.TECHNOLOGY -> "⌘"
    }
}
