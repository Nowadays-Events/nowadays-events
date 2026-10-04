package com.nowadays.events.data.local

import android.content.Context
import com.nowadays.events.domain.usecase.EventHidingStore
import com.nowadays.events.domain.usecase.HiddenEventStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventHidingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("event_hiding", Context.MODE_PRIVATE)
    val store = EventHidingStore(object : HiddenEventStorage {
        override fun read(): Set<String> = preferences.getStringSet("hidden_keys", emptySet()).orEmpty().toSet()
        override fun write(keys: Set<String>) {
            preferences.edit().putStringSet("hidden_keys", keys.toSet()).apply()
        }
    })
}
