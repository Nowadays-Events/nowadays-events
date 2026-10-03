package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.TimeFilter
import com.nowadays.events.domain.usecase.EventTimeFilters
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Filter boundary, not a promise that sources supply events through that date. */
internal fun searchHorizonLabel(
    filter: TimeFilter,
    customEnd: LocalDate?,
    clock: Clock,
    zone: ZoneId = ZoneId.of("Europe/Paris"),
): String {
    val end = if (filter == TimeFilter.CUSTOM) customEnd else
        EventTimeFilters(clock).window(filter, zone).endExclusive?.minusNanos(1)?.atZone(zone)?.toLocalDate()
    val boundary = end?.let { "Recherche jusqu’au ${it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}" }
        ?: "Recherche sans limite de date"
    return "$boundary · selon les sources disponibles"
}
