package com.nowadays.events

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

/** Minimal UI integration. Map interaction decisions are covered by deterministic JVM tests. */
class MapScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainScreenOpensAndWeekendFilterCanBeSelected() {
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.onNodeWithText("✓ Week-end").assertIsDisplayed()
    }

    @Test fun selectedFilterSurvivesActivityRecreation() {
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("✓ Week-end").assertIsDisplayed()
    }

    @Test fun everyPresetTemporalFilterCanBeSelected() {
        listOf(
            "period-today" to "✓ Aujourd’hui",
            "period-tomorrow" to "✓ Demain",
            "period-next_7_days" to "✓ 7 jours",
            "period-this_weekend" to "✓ Week-end",
        ).forEach { (tag, selectedText) ->
            compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithText(selectedText).assertIsDisplayed()
        }
    }

    @Test fun compactHeaderOpensPlaceChooserInOneTap() {
        compose.onNodeWithTag("reference-place-button").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lieu de référence").assertIsDisplayed()
        compose.onNodeWithTag("choose-my-position").assertIsDisplayed()
        compose.onNodeWithTag("city-search").assertIsDisplayed()
        compose.onNodeWithTag("choose-map-point").assertIsDisplayed()
    }

    @Test fun secondaryFiltersContainRadiusAndSurviveRecreation() {
        compose.onNodeWithTag("open-filters").performClick()
        compose.onNodeWithTag("radius-15").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("15 km", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("compact-sync-state").assertIsDisplayed()
    }
}
