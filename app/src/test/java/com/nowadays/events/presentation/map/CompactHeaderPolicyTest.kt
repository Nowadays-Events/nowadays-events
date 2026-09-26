package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.SyncState
import com.nowadays.events.domain.model.SyncStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactHeaderPolicyTest {
    @Test fun `offline status stays explicit while cached events remain available`() {
        val label = compactSyncStatusLabel(
            SyncState(status = SyncStatus.OFFLINE_WITH_CACHE, lastSuccessAt = Instant.parse("2026-09-26T08:00:00Z")),
            hasCache = true,
            stale = true,
        )

        assertTrue(label.startsWith("Hors connexion"))
        assertTrue(label.contains("données du"))
    }

    @Test fun `first load and local cache have distinct labels`() {
        assertEquals("Première actualisation en attente", compactSyncStatusLabel(SyncState(), hasCache = false, stale = false))
        assertEquals("Données locales", compactSyncStatusLabel(SyncState(), hasCache = true, stale = false))
    }
}
