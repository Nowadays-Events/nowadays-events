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
        compose.onNodeWithText("Xymis Events").assertIsDisplayed()
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.onNodeWithText("✓ Ce week-end").assertIsDisplayed()
    }

    @Test fun selectedFilterSurvivesActivityRecreation() {
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("✓ Ce week-end").assertIsDisplayed()
    }
}
