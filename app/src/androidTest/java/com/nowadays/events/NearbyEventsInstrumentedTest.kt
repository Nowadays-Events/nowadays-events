package com.nowadays.events

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.usecase.NearbyEvent
import com.nowadays.events.presentation.map.NearbyEventsSheet
import com.nowadays.events.presentation.theme.NowadaysTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NearbyEventsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun touchingNearbyResultOpensExistingEventPath() {
        val now = Instant.parse("2026-09-10T10:00:00Z")
        val event = Event(
            "nearby", "Concert proche", "Description", null, EventCategory.MUSIC,
            now, now.plusSeconds(3600), "Salle", "Mont-de-Marsan", 43.89, -0.50,
            "https://example.invalid", null, null, EventPrice.Free, now, DataOrigin.DEMO,
        )
        var selected: String? = null
        compose.setContent { NowadaysTheme {
            NearbyEventsSheet(
                listOf(NearbyEvent(event, 1.2)), 15, true, {}, { selected = it.event.id }, {}, {},
            )
        } }
        compose.onNodeWithTag("nearby-result-nearby").performClick()
        compose.runOnIdle { assertEquals("nearby", selected) }
    }
}
