package com.nowadays.events.domain.model

import java.time.Duration
import java.time.Instant

enum class SyncStatus { NEVER, RUNNING, SUCCESS, OFFLINE_WITH_CACHE, FAILED_WITH_CACHE, FAILED_EMPTY }

data class SyncState(
    val status: SyncStatus = SyncStatus.NEVER,
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val receivedCount: Int = 0,
    val lastSuccessfulCount: Int = 0,
    val technicalReason: String? = null,
) {
    fun isPotentiallyStale(now: Instant, threshold: Duration = Duration.ofHours(6)): Boolean =
        lastSuccessAt?.let { Duration.between(it, now) > threshold } ?: false
}
