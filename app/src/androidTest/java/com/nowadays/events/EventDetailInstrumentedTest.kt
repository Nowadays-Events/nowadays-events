package com.nowadays.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.*
import com.nowadays.events.presentation.detail.EventDetailSheet
import com.nowadays.events.presentation.detail.EventDetailContent
import com.nowadays.events.presentation.theme.NowadaysTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class EventDetailInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deterministicEventImmediatelyShowsEssentialDetails() {
        val instant = Instant.parse("2026-09-01T10:00:00Z")
        val event = Event(
            "simple", "Événement simple", "Description", null, EventCategory.CULTURE,
            instant, instant.plusSeconds(7200), "Lieu", "Mont-de-Marsan", 43.89, -0.50,
            "https://example.invalid/simple", null, null, EventPrice.Free, instant, DataOrigin.DEMO,
        )
        compose.setContent {
            NowadaysTheme {
                EventDetailSheet(
                    event = event,
                    attendance = AttendanceResponse.NONE,
                    onAttendanceChanged = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Événement simple").assertIsDisplayed()
        compose.onNodeWithText("Mont-de-Marsan").assertIsDisplayed()
        compose.onNodeWithText("Gratuit").assertIsDisplayed()
    }

    @Test fun standaloneDetailOffersExplicitMapAction() {
        val instant = Instant.parse("2026-09-01T10:00:00Z")
        val event = Event("map", "Événement à localiser", "Description", null, EventCategory.CULTURE,
            instant, instant.plusSeconds(3600), "Lieu", "Adresse", 43.89, -0.50,
            "https://example.invalid/map", null, null, EventPrice.Unknown, instant, DataOrigin.DEMO)
        compose.setContent { NowadaysTheme { EventDetailContent(event, attendance = AttendanceResponse.NONE, onAttendanceChanged = {}, onShowMap = {}) } }
        compose.onNodeWithTag("show-event-on-map").assertIsDisplayed()
        compose.onNodeWithText("Tarif non renseigné").assertIsDisplayed()
    }

    @Test fun cancelledRecurringEventKeepsAllStatusInformation() {
        val instant = Instant.parse("2026-09-01T10:00:00Z")
        val event = Event("cancelled", "Atelier hebdomadaire", "Description", null, EventCategory.COMMUNITY,
            instant, instant.plusSeconds(3600), "Lieu", "Adresse", 43.89, -0.50,
            "https://example.invalid/cancelled", null, null, EventPrice.Free, instant, DataOrigin.DEMO,
            status = EventStatus.CANCELLED, occurrenceCount = 4, nextOccurrenceAt = instant,
            scheduleType = EventScheduleType.RECURRING, occurrenceStarts = listOf(instant))
        compose.setContent { NowadaysTheme { EventDetailContent(event, attendance = AttendanceResponse.NONE, onAttendanceChanged = {}) } }
        compose.onNodeWithText("ANNULÉ").assertIsDisplayed()
        compose.onNodeWithText("Récurrent").assertIsDisplayed()
        compose.onNodeWithText("Vie locale").assertIsDisplayed()
    }
}
