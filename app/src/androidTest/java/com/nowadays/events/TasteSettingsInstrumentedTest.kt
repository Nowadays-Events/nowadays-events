package com.nowadays.events

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nowadays.events.data.local.TastePreferences
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TasteLevel
import com.nowadays.events.presentation.settings.TasteSettingsContent
import com.nowadays.events.presentation.settings.tasteCategoryLabel
import com.nowadays.events.presentation.theme.NowadaysTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TasteSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sevenCategoriesCanBeSetRestoredAndResetWithoutTouchingOtherPreferences() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("validation_tastes_$name", mode)
        }
        val prefs = context.getSharedPreferences("for_me_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("unrelated", "keep").commit()
        try {
            val store = TastePreferences(context).store
            var backs = 0
            var showSettings by mutableStateOf(true)
            compose.setContent { NowadaysTheme {
                if (showSettings) {
                    val values by store.preferences.collectAsState()
                    TasteSettingsContent(values, store::set, store::reset, { backs++; showSettings = false })
                } else Button({ showSettings = true }, Modifier.testTag("reopen-settings")) { Text("Paramètres") }
            } }
            EventCategory.entries.forEach { category ->
                compose.onNodeWithTag("taste-settings").performScrollToNode(hasTestTag("taste-${category.name}"))
                compose.onNodeWithTag("taste-${category.name}").assertExists()
                compose.onNodeWithText(tasteCategoryLabel(category)).assertIsDisplayed()
                compose.onNodeWithTag("taste-level-${category.name}").assertTextEquals("Sans avis")
            }
            compose.onNodeWithTag("taste-settings").performScrollToNode(hasTestTag("taste-slider-COMMUNITY"))
            compose.onNodeWithTag("taste-slider-COMMUNITY")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithTag("taste-level-COMMUNITY").assertTextEquals("Moyen")
            assertEquals(TasteLevel.MEDIUM, TastePreferences(context).store.preferences.value[EventCategory.COMMUNITY])
            compose.onNodeWithTag("validate-tastes").assertIsDisplayed().performClick()
            compose.onNodeWithTag("reopen-settings").performClick()
            compose.onNodeWithTag("taste-settings").performScrollToNode(hasTestTag("taste-level-COMMUNITY"))
            compose.onNodeWithTag("taste-level-COMMUNITY").assertTextEquals("Moyen")
            compose.onNodeWithTag("taste-settings").performScrollToNode(hasTestTag("reset-tastes"))
            compose.onNodeWithTag("reset-tastes").performClick()
            compose.onNodeWithTag("confirm-reset-tastes").performClick()
            assertTrue(TastePreferences(context).store.preferences.value.values.all { it == TasteLevel.UNSPECIFIED })
            assertEquals("keep", prefs.getString("unrelated", null))
            assertEquals(1, backs)
        } finally {
            prefs.edit().clear().commit()
        }
    }
}

class TasteSettingsNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsAreOptionalAndMapIsRestoredOnReturn() {
        compose.onNodeWithTag("event-map").assertExists()
        val minTouchPx = 48f * compose.activity.resources.displayMetrics.density
        listOf("map-search-action", "open-settings").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag touch width", bounds.width + 1f >= minTouchPx)
            assertTrue("$tag touch height", bounds.height + 1f >= minTouchPx)
        }
        compose.onNodeWithTag("map-search-action").performClick()
        compose.onNodeWithText("✓ Toutes").assertIsDisplayed()
        compose.onNodeWithTag("map-search-action").performClick()
        compose.onNodeWithText("✓ Toutes").assertDoesNotExist()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("taste-settings").assertExists()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("event-map").assertExists()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("validate-tastes").performClick()
        compose.onNodeWithTag("event-map").assertExists()
    }

    @Test fun validatePreservesListFilterAcrossMapAndSettings() {
        compose.onNodeWithTag("show-list").performClick()
        compose.onNodeWithTag("period-filter-bar").performScrollToNode(hasTestTag("period-next_7_days"))
        compose.onNodeWithTag("period-next_7_days").performClick()
        compose.onNodeWithTag("show-map").performClick()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("validate-tastes").performClick()
        compose.onNodeWithTag("show-list").performClick()
        compose.onNodeWithTag("show-map").assertIsDisplayed()
        compose.onNodeWithText("✓ 7 jours").assertIsDisplayed()
    }
}
