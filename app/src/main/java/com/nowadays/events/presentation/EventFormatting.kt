package com.nowadays.events.presentation

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventTimePrecision
import com.nowadays.events.domain.model.EventScheduleType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

fun eventScheduleLabel(
    event: Event,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): String {
    if (event.scheduleType == EventScheduleType.RECURRING && event.nextOccurrenceAt == null) {
        return "Aucune prochaine occurrence"
    }
    if (event.scheduleType == EventScheduleType.CONTINUOUS) {
        val startDay = event.startsAt.atZone(zoneId).format(dayFormatter).replaceFirstChar { it.uppercase() }
        val endDay = event.endsAt.atZone(zoneId).format(dayFormatter)
        return if (now in event.startsAt..event.endsAt) "En cours · jusqu’au $endDay" else "Du $startDay au $endDay"
    }
    val instant = if (event.scheduleType == EventScheduleType.RECURRING) event.nextOccurrenceAt!! else event.startsAt
    val date = instant.atZone(zoneId)
    val day = date.format(dayFormatter).replaceFirstChar { it.uppercase() }
    val prefix = if (event.scheduleType == EventScheduleType.RECURRING) "Prochaine date : " else ""
    return when (event.timePrecision) {
        EventTimePrecision.EXACT -> "$prefix$day · ${if (date.minute == 0) "%02d h".format(date.hour) else "%02d h %02d".format(date.hour, date.minute)}"
        EventTimePrecision.APPROXIMATE -> "$prefix$day · ${event.originalTimeText ?: "Horaire approximatif"}"
        EventTimePrecision.DATE_ONLY -> "$prefix$day · Horaire à confirmer"
        EventTimePrecision.UNKNOWN -> "$prefix$day · Horaire inconnu"
    }
}
