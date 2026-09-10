package com.nowadays.events.presentation

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventTimePrecision
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

fun eventScheduleLabel(event: Event, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val instant = if (event.occurrenceCount > 1) event.nextOccurrenceAt ?: event.startsAt else event.startsAt
    val date = instant.atZone(zoneId)
    val day = date.format(dayFormatter).replaceFirstChar { it.uppercase() }
    return when (event.timePrecision) {
        EventTimePrecision.EXACT -> "$day · ${if (date.minute == 0) "%02d h".format(date.hour) else "%02d h %02d".format(date.hour, date.minute)}"
        EventTimePrecision.APPROXIMATE -> "$day · ${event.originalTimeText ?: "Horaire approximatif"}"
        EventTimePrecision.DATE_ONLY -> "$day · Horaire à confirmer"
        EventTimePrecision.UNKNOWN -> "$day · Horaire inconnu"
    }
}
