package com.nowadays.events

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import android.view.View
import android.view.ViewGroup
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

/** Minimal UI integration. Map interaction decisions are covered by deterministic JVM tests. */
class MapScreenInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun twoFingerZoomChangesCameraWithoutOpeningTheList() {
        fun findMap(view: View): MapView? {
            if (view is MapView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                findMap(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        var map: MapLibreMap? = null
        compose.runOnIdle { requireNotNull(findMap(compose.activity.window.decorView)).getMapAsync { map = it } }
        compose.waitUntil(30_000) { map != null }
        var before = 0.0
        var observed = 0.0
        compose.runOnIdle {
            before = map!!.cameraPosition.zoom
            observed = before
            map!!.addOnCameraIdleListener { observed = map!!.cameraPosition.zoom }
        }
        compose.onNodeWithTag("event-map").performTouchInput {
            val focus = Offset(center.x, center.y * .8f)
            pinch(start0 = focus - Offset(35f, 0f), start1 = focus + Offset(35f, 0f),
                end0 = focus - Offset(135f, 0f), end1 = focus + Offset(135f, 0f), durationMillis = 450)
        }
        compose.waitUntil(10_000) { observed > before + .2 }
        assertTrue(observed > before)
        compose.onNodeWithTag("show-list").assertIsDisplayed()
        compose.onNodeWithTag("preview-open-list").assertDoesNotExist()
    }

    private fun openList() {
        compose.onNodeWithTag("show-list").performClick()
        compose.onNodeWithTag("show-map").assertIsDisplayed()
    }

    @Test fun applicationOpensOnMapWithAnExplicitListControl() {
        compose.onNodeWithTag("event-map").assertIsDisplayed()
        compose.onNodeWithTag("map-list-handle").assertIsDisplayed()
        compose.onNodeWithTag("show-list").assertIsDisplayed()
    }

    @Test fun handleMovesFromClosedToPreviewAndExpandedWithoutTouchingTheMap() {
        compose.onNodeWithTag("map-list-handle").performTouchInput { swipeUp() }
        compose.onNodeWithTag("preview-open-list").assertIsDisplayed()
        compose.onNodeWithTag("map-list-handle").performTouchInput { swipeUp() }
        compose.onNodeWithTag("show-map").assertIsDisplayed()
        compose.onNodeWithTag("event-map").assertExists()
    }

    @Test fun mainScreenOpensAndWeekendFilterCanBeSelected() {
        openList()
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.onNodeWithText("✓ Week-end").assertIsDisplayed()
    }

    @Test fun selectedFilterSurvivesActivityRecreation() {
        openList()
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-this_weekend"))
        compose.onNodeWithTag("period-this_weekend").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("show-map").assertIsDisplayed()
        compose.onNodeWithText("✓ Week-end").assertIsDisplayed()
    }

    @Test fun everyPresetTemporalFilterCanBeSelected() {
        openList()
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
        openList()
        compose.onNodeWithTag("reference-place-button").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lieu de référence").assertIsDisplayed()
        compose.onNodeWithTag("choose-my-position").assertIsDisplayed()
        compose.onNodeWithTag("city-search").assertIsDisplayed()
        compose.onNodeWithTag("choose-map-point").assertIsDisplayed()
    }

    @Test fun secondaryFiltersContainRadiusAndSurviveRecreation() {
        openList()
        compose.onNodeWithTag("open-filters").performClick()
        compose.onNodeWithTag("radius-15").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reference-place-button").assertTextContains("15 km", substring = true)
        compose.onNodeWithTag("compact-sync-state").assertIsDisplayed()
    }
}
