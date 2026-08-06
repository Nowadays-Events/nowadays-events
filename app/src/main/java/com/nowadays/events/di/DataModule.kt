package com.nowadays.events.di

import android.content.Context
import androidx.room.Room
import com.nowadays.events.data.local.EventDao
import com.nowadays.events.data.local.EventDatabase
import com.nowadays.events.data.local.MIGRATION_1_2
import com.nowadays.events.data.local.MIGRATION_2_3
import com.nowadays.events.data.local.MIGRATION_3_4
import com.nowadays.events.data.local.MIGRATION_4_5
import com.nowadays.events.data.repository.OfflineFirstEventRepository
import com.nowadays.events.data.remote.EventSource
import com.nowadays.events.data.remote.ApiEventSource
import com.nowadays.events.domain.repository.EventRepository
import dagger.Binds
import dagger.multibindings.Multibinds
import dagger.multibindings.IntoSet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindEventRepository(implementation: OfflineFirstEventRepository): EventRepository
    @Binds @IntoSet abstract fun bindApiEventSource(implementation: ApiEventSource): EventSource
    @Multibinds abstract fun eventSources(): Set<EventSource>
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun provideClock(): Clock = Clock.systemDefaultZone()
    @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): EventDatabase =
        Room.databaseBuilder(context, EventDatabase::class.java, "events.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    @Provides fun provideEventDao(database: EventDatabase): EventDao = database.eventDao()
}
