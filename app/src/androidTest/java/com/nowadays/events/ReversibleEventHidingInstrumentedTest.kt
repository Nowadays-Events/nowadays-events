package com.nowadays.events

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.nowadays.events.data.local.EventDatabase
import com.nowadays.events.data.local.EventHidingPreferences
import com.nowadays.events.data.repository.OfflineFirstEventRepository
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.usecase.EventHidingPolicy
import com.nowadays.events.presentation.detail.EventDetailContent
import com.nowadays.events.presentation.map.*
import com.nowadays.events.presentation.theme.NowadaysTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** No MapLibre, network, production preferences or real-time dates involved. */
class ReversibleEventHidingInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-01T08:00:00Z")
    private val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("validation_hiding_$name", mode)
    }
    @Before fun prepare() { context.getSharedPreferences("event_hiding", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun cleanup() { context.getSharedPreferences("event_hiding", Context.MODE_PRIVATE).edit().clear().commit() }

    private fun event(id: String) = Event(id, "Événement $id", "Description", null, EventCategory.CULTURE,
        now.plusSeconds(7200), now.plusSeconds(10800), "Lieu", "Mont-de-Marsan", 43.89, -.5,
        "https://example.org/$id", null, null, EventPrice.Free, now, DataOrigin.AUTOMATIC)

    @Test fun preferenceReconstructionPreservesHideAndReveal() {
        val event = event("api-persistence")
        EventHidingPreferences(context).store.hide(event)
        val reopened = EventHidingPreferences(context).store
        assertTrue(EventHidingPolicy.partition(listOf(event), reopened.hiddenKeys.value).allResultsHidden)
        reopened.reveal(event)
        assertTrue(EventHidingPreferences(context).store.hiddenKeys.value.isEmpty())
    }

    @Test fun hideIsNotCollapseOrOpenEvenForCancelledRowAtLargeFont() {
        val event = event("api-cancelled").copy(status = EventStatus.CANCELLED)
        var collapsed by mutableStateOf(false)
        var hidden = false
        var opened = false
        var dark by mutableStateOf(false)
        compose.setContent { NowadaysTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                CompactEventRow(NearbyListItem(event, 2.0), now, collapsed,
                    { collapsed = !collapsed }, onHide = { hidden = true }, onOpenEvent = { opened = true })
            }
        } }
        compose.onNodeWithTag("event-collapse-${event.id}").performClick()
        compose.onNodeWithTag("event-actions-${event.id}").performClick()
        compose.onNodeWithTag("hide-event-${event.id}").assertIsDisplayed()
        compose.runOnIdle { dark = true }
        compose.onNodeWithTag("hide-event-${event.id}").performClick()
        compose.runOnIdle { assertTrue(hidden); assertTrue(collapsed); assertFalse(opened) }
    }

    @Test fun detailHasAnExplicitHideActionWithoutDeleting() {
        var hidden = false
        compose.setContent { NowadaysTheme { EventDetailContent(event("api-detail"),
            attendance = AttendanceResponse.NONE, onAttendanceChanged = {}, onHide = { hidden = true }) } }
        compose.onNodeWithTag("detail-hide-event").performScrollTo().performClick()
        assertTrue(hidden)
    }

    @Test fun rowStillOpensItsOwnDetailWhenHideActionsAreAvailable() {
        val event = event("api-row-open")
        var opened: String? = null
        var hidden = false
        compose.setContent { NowadaysTheme {
            CompactEventRow(NearbyListItem(event, 2.0), now,
                onHide = { hidden = true }, onOpenEvent = { opened = it.id })
        } }
        compose.onNodeWithTag("event-row-${event.id}").performClick()
        compose.runOnIdle { assertEquals(event.id, opened); assertFalse(hidden) }
    }

    @Test fun allHiddenEmptyStateExplainsAndOpensTheManagement() {
        var opened = false
        compose.setContent { NowadaysTheme {
            EventResultsEmptyState(3, { opened = true })
        } }
        compose.onNodeWithTag("event-list-empty").assertTextEquals("Tous les résultats de ce filtre sont masqués sur cet appareil.")
        compose.onNodeWithTag("empty-show-hidden").assertTextEquals("Voir les 3 événements masqués").performClick()
        assertTrue(opened)
    }

    @Test fun genuineEmptyStateDoesNotPretendResultsWereHidden() {
        compose.setContent { NowadaysTheme { EventResultsEmptyState(0, {}) } }
        compose.onNodeWithTag("event-list-empty").assertTextEquals("Aucun événement dans ce rayon pour cette période.")
        compose.onNodeWithTag("empty-show-hidden").assertDoesNotExist()
    }

    @Test fun sheetRevealsOneAndThenAllIncludingRetainedAbsentChoices() {
        val first = event("api-one")
        val second = event("api-two").copy(scheduleType = EventScheduleType.RECURRING, occurrenceCount = 2,
            nextOccurrenceAt = now.plusSeconds(7200))
        val preferences = EventHidingPreferences(context)
        preferences.store.hide(first); preferences.store.hide(second); preferences.store.hide(event("api-absent"))
        compose.setContent { NowadaysTheme {
            val keys by preferences.store.hiddenKeys.collectAsState()
            val hidden = EventHidingPolicy.partition(listOf(first, second), keys).hidden
            HiddenEventsSheet(hidden, hidden, keys.size, preferences.store::reveal, preferences.store::revealAll, {}, now)
        } }
        compose.onNodeWithTag("reveal-event-${first.id}").performClick()
        compose.onNodeWithTag("reveal-event-${first.id}").assertDoesNotExist()
        compose.onNodeWithTag("reveal-event-${second.id}").assertExists()
        compose.onNodeWithTag("reveal-all-events").performClick()
        compose.runOnIdle { assertTrue(preferences.store.hiddenKeys.value.isEmpty()) }
    }

    @Test fun roomSnapshotUpdatesAndTwoAbsencesDoNotEraseHiding() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        try {
            val target = event("api-room")
            val other = event("api-other")
            val preferences = EventHidingPreferences(context)
            val repository = OfflineFirstEventRepository(db.eventDao(), Clock.fixed(now, ZoneId.of("Europe/Paris")))
            val state = SyncState(status = SyncStatus.SUCCESS, lastSuccessAt = now)
            repository.reconcileRemoteSnapshot(listOf(target, other), state)
            preferences.store.hide(target)
            repository.reconcileRemoteSnapshot(listOf(other), state)
            repository.reconcileRemoteSnapshot(listOf(other), state)
            assertEquals(2, db.eventDao().count())
            assertFalse(repository.observeEvents().first().any { it.id == target.id })
            repository.reconcileRemoteSnapshot(listOf(target.copy(title = "Updated"), other), state)
            val keys = EventHidingPreferences(context).store.hiddenKeys.value
            val visible = EventHidingPolicy.partition(repository.observeEvents().first(), keys)
            assertEquals(listOf(other.id), visible.visible.map { it.id })
            assertEquals(listOf(target.id), visible.hidden.map { it.id })
            assertEquals(2, db.eventDao().count())
        } finally { db.close() }
    }
}
