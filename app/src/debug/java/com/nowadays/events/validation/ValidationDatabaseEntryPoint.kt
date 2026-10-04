package com.nowadays.events.validation

import com.nowadays.events.data.local.EventDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Shares the application's invalidation tracker with device tests. Debug builds only. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ValidationDatabaseEntryPoint {
    fun eventDao(): EventDao
}
