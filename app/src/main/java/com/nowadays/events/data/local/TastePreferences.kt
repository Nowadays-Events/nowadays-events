package com.nowadays.events.data.local

import android.content.Context
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TastePreferenceStorage
import com.nowadays.events.domain.usecase.TastePreferencesStore
import com.nowadays.events.domain.usecase.tasteStorageKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TastePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("for_me_preferences", Context.MODE_PRIVATE)
    val store = TastePreferencesStore(object : TastePreferenceStorage {
        override fun read(category: EventCategory): String? =
            preferences.getString(tasteStorageKey(category), null)

        override fun write(category: EventCategory, value: String?) {
            preferences.edit().apply {
                if (value == null) remove(tasteStorageKey(category)) else putString(tasteStorageKey(category), value)
            }.apply()
        }
    })

}
