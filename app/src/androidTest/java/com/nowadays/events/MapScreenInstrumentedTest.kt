package com.nowadays.events

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
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
        lateinit var mapView: MapView
        compose.runOnIdle {
            mapView = requireNotNull(findMap(compose.activity.window.decorView))
            mapView.getMapAsync { map = it }
        }
        compose.waitUntil(30_000) { map != null }
        var styleReady = false
        compose.runOnIdle { map!!.getStyle { styleReady = true } }
        compose.waitUntil(30_000) { styleReady }
        compose.waitUntil(10_000) {
            var focused = false
            compose.runOnIdle { focused = mapView.hasWindowFocus() }
            focused
        }
        fun currentZoom(): Double {
            var zoom = 0.0
            compose.runOnIdle { zoom = map!!.cameraPosition.zoom }
            return zoom
        }
        val before = currentZoom()
        var receivedMoves = 0
        compose.runOnIdle {
            android.util.Log.i("MapGestureValidation", "Gestures: zoom=${map!!.uiSettings.isZoomGesturesEnabled}; location=${mapView.x},${mapView.y}")
            mapView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 2) receivedMoves++
                android.util.Log.i("MapGestureValidation", "Touch action=${event.actionMasked}; pointers=${event.pointerCount}; x=${event.x}; y=${event.y}")
                false
            }
        }
        injectNativePinch(mapView, 35f, 135f)
        var observed = currentZoom()
        android.util.Log.i("MapGestureValidation", "Zoom in: $before -> $observed; view=${mapView.width}x${mapView.height}; receivedMoves=$receivedMoves")
        compose.waitUntil(10_000) { observed = currentZoom(); observed > before + .2 }
        assertTrue("Zoom must increase: $before -> $observed", observed > before + .2)
        val zoomedIn = observed
        injectNativePinch(mapView, 135f, 35f)
        compose.waitUntil(10_000) { observed = currentZoom(); observed < zoomedIn - .2 }
        assertTrue("Zoom must decrease: $zoomedIn -> $observed", observed < zoomedIn - .2)
        compose.onNodeWithTag("show-list").assertIsDisplayed()
        compose.onNodeWithTag("preview-open-list").assertDoesNotExist()
    }

    /** Pace real touchscreen events so the native AndroidView receives frame-time gestures. */
    private fun injectNativePinch(view: MapView, startRadius: Float, endRadius: Float) {
        val location = IntArray(2)
        var focusX = 0f
        var focusY = 0f
        compose.runOnIdle {
            view.getLocationOnScreen(location)
            focusX = location[0] + view.width / 2f
            focusY = location[1] + view.height * .4f
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply {
            id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        fun send(action: Int, count: Int, radius: Float) {
            val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
                x = focusX + if (index == 0) -radius else radius
                y = focusY; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 1, startRadius)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, startRadius)
        for (step in 1..30) {
            SystemClock.sleep(15)
            send(MotionEvent.ACTION_MOVE, 2, startRadius + (endRadius - startRadius) * step / 30f)
        }
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, endRadius)
        send(MotionEvent.ACTION_UP, 1, endRadius)
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
