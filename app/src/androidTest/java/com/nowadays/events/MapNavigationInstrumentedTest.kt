package com.nowadays.events

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nowadays.events.data.local.EventDatabase
import com.nowadays.events.data.mapper.toEntity
import com.nowadays.events.domain.model.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Only adds isolated manual fixtures; never deletes or replaces user events. */
class MapNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun sameLocationEventsStaySeparateAndReturnRestoresPixelPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, EventDatabase::class.java, "events.db").build()
        val start = Instant.parse("2099-10-04T12:00:00Z")
        val fixtures = (0..50).map { index -> Event(
            id = "ui-validation-$index", title = "Validation position $index",
            shortDescription = "Fixture de navigation", fullDescription = null,
            category = EventCategory.COMMUNITY, startsAt = start, endsAt = start.plusSeconds(3600),
            venueName = "Lieu commun", address = "Mont-de-Marsan", latitude = 43.8904, longitude = -.5007,
            sourceUrl = "https://example.invalid/ui-validation/$index", imageUrl = null, organizer = null,
            price = EventPrice.Free, updatedAt = start, origin = DataOrigin.MANUAL,
        ) }
        try {
            runBlocking { database.eventDao().upsertAll(fixtures.map { it.toEntity() }) }
            compose.onNodeWithTag("show-list").performClick()
            compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-custom"))
            compose.onNodeWithTag("period-custom").performClick()
            compose.onNodeWithText("Toutes les dates").performClick()
            compose.onNodeWithTag("toggle-search").performClick()
            compose.onNodeWithTag("event-search").performTextInput("Validation position")
            val target = "event-row-ui-validation-30"
            compose.onNodeWithTag("event-list").performScrollToNode(hasTestTag(target))
            compose.onNodeWithTag("event-list").performTouchInput {
                swipe(center, center + Offset(0f, 47f), durationMillis = 400)
            }
            val before = compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag(target).performClick()
            compose.onNodeWithTag("event-map-preview").performClick()
            compose.onNodeWithTag("map-navigation-back").performClick()
            compose.onNodeWithTag("detail-back").performClick()
            assertEquals(before, compose.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot)
            // The neighboring event has identical dates and coordinates, but its own accessible row.
            compose.onNodeWithTag("event-list").performScrollToNode(hasTestTag("event-row-ui-validation-31"))
            compose.onNodeWithTag("event-row-ui-validation-31").performClick()
            compose.onNodeWithText("Validation position 31").assertIsDisplayed()
        } finally {
            runBlocking { database.eventDao().deleteEvents(fixtures.map { it.id }) }
            database.close()
        }
    }
}
