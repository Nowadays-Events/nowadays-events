package com.nowadays.events

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nowadays.events.domain.model.*
import com.nowadays.events.presentation.detail.EventDetailContent
import com.nowadays.events.presentation.map.CompactEventRow
import com.nowadays.events.presentation.map.NearbyListItem
import com.nowadays.events.presentation.theme.NowadaysTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompactEventListInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixedNow = Instant.parse("2026-09-26T10:00:00Z")

    @Test fun compactRowKeepsLongTitleDistanceAndUnknownTimeReadable() {
        val event = event("long", "Un titre volontairement très long qui doit rester lisible sur deux lignes", EventStatus.ACTIVE, EventTimePrecision.UNKNOWN)
        compose.setContent { NowadaysTheme { CompactEventRow(NearbyListItem(event, 12.4), fixedNow) {} } }
        compose.onNodeWithText(event.title).assertIsDisplayed()
        compose.onNodeWithTag("event-distance-long", useUnmergedTree = true).assertTextContains("12 km").assertIsDisplayed()
        compose.onNodeWithText("Horaire inconnu", substring = true).assertIsDisplayed()
    }

    @Test fun cancelledRowIsExplicitAndClickOpensEvent() {
        var clicked = false
        val event = event("cancelled", "Concert Jazz Swing 40", EventStatus.CANCELLED)
        compose.setContent { NowadaysTheme { CompactEventRow(NearbyListItem(event, 2.1), fixedNow) { clicked = true } } }
        compose.onNodeWithText("ANNULÉ", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("event-row-cancelled").performClick()
        assertTrue(clicked)
    }

    @Test fun mapPreviewIsVisibleClickableAndHasNoGesturesSurface() {
        val event = event("map", "Événement cartographié")
        var opened = false
        compose.setContent { NowadaysTheme { EventDetailContent(event, attendance = AttendanceResponse.NONE, onAttendanceChanged = {}, onShowMap = { opened = true }) } }
        compose.onNodeWithTag("event-map-preview").assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-map-preview-no-gestures", useUnmergedTree = true).assertExists()
        assertTrue(opened)
    }

    @Test fun compactRowSupportsLargeTypography() {
        val event = event("large-font", "Grand titre accessible")
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { MaterialTheme { CompactEventRow(NearbyListItem(event, 3.2), fixedNow) {} } } }
        compose.onNodeWithText("Grand titre accessible").assertIsDisplayed()
    }

    @Test fun compactRowRendersInLightAndDarkThemes() {
        val event = event("themes", "Événement dans les deux thèmes")
        compose.setContent {
            Column {
                NowadaysTheme(darkTheme = false, dynamicColor = false) { CompactEventRow(NearbyListItem(event, 1.2), fixedNow) {} }
                NowadaysTheme(darkTheme = true, dynamicColor = false) { CompactEventRow(NearbyListItem(event.copy(id = "themes-dark"), 1.2), fixedNow) {} }
            }
        }
        compose.onNodeWithTag("event-row-themes").assertIsDisplayed()
        compose.onNodeWithTag("event-row-themes-dark").assertIsDisplayed()
    }

    @Test fun continuousAndRecurringSchedulesStayExplicitInCompactRows() {
        val continuous = event("continuous", "Exposition continue").copy(
            startsAt = Instant.parse("2027-09-26T18:00:00Z"),
            endsAt = Instant.parse("2027-09-30T18:00:00Z"),
            scheduleType = EventScheduleType.CONTINUOUS,
        )
        val recurring = event("recurring", "Marché récurrent").copy(
            scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-09-27T08:00:00Z"),
            occurrenceCount = 4,
        )
        compose.setContent {
            NowadaysTheme(dynamicColor = false) {
                Column {
                    CompactEventRow(NearbyListItem(continuous, 2.0), fixedNow) {}
                    CompactEventRow(NearbyListItem(recurring, 3.0), fixedNow) {}
                }
            }
        }
        compose.onNodeWithTag("event-row-continuous").assertIsDisplayed()
        compose.onNodeWithTag("event-row-recurring").assertIsDisplayed()
        compose.onNodeWithText("Du", substring = true).assertIsDisplayed()
    }

    private fun event(id: String, title: String, status: EventStatus = EventStatus.ACTIVE, precision: EventTimePrecision = EventTimePrecision.EXACT): Event {
        val start = Instant.parse("2026-09-26T18:00:00Z")
        return Event(id, title, "Description", null, EventCategory.MUSIC, start, start.plusSeconds(7200), "Cinéma Le Renoir", "Adresse longue, Biscarrosse", 44.39, -1.16, "https://example.invalid/$id", null, null, EventPrice.Unknown, start, DataOrigin.DEMO, status = status, timePrecision = precision)
    }
}
