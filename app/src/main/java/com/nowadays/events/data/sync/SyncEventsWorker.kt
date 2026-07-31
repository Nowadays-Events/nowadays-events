package com.nowadays.events.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncEventsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val synchronizer: EventSynchronizer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching { synchronizer.synchronize() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

