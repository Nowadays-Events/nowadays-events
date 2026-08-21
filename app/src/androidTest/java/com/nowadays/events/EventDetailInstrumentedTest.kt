package com.nowadays.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.*
import com.nowadays.events.presentation.detail.EventDetailSheet
import com.nowadays.events.presentation.theme.NowadaysTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class EventDetailInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deterministicEventOpensAndExpandsItsDetails() {
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
        compose.onNodeWithTag("event-detail-header").performClick()
        compose.onNodeWithText("Mont-de-Marsan").assertIsDisplayed()
    }
}
