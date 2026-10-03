package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.TimeFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHorizonLabelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-03T23:10:00Z"), ZoneOffset.UTC)

    @Test fun parisDateAndInclusiveSevenDayBoundaryAreExplicit() {
        assertEquals("Recherche jusqu’au 04/10/2026 · selon les sources disponibles", searchHorizonLabel(TimeFilter.TODAY, null, clock))
        assertEquals("Recherche jusqu’au 10/10/2026 · selon les sources disponibles", searchHorizonLabel(TimeFilter.NEXT_7_DAYS, null, clock))
    }

    @Test fun customBoundaryAndUnlimitedSearchDoNotPromiseFeedCoverage() {
        assertEquals("Recherche jusqu’au 31/12/2026 · selon les sources disponibles", searchHorizonLabel(TimeFilter.CUSTOM, LocalDate.of(2026, 12, 31), clock))
        assertEquals("Recherche sans limite de date · selon les sources disponibles", searchHorizonLabel(TimeFilter.ALL_FUTURE, null, clock))
    }
}
