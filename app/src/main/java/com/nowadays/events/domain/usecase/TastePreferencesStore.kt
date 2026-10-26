package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.EventCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TasteLevel { UNSPECIFIED, LOW, MEDIUM, HIGH }

fun tasteStorageKey(category: EventCategory): String = "taste_${category.name}"

/** The keys use the stable EventCategory identifiers, never translated labels. */
interface TastePreferenceStorage {
    fun read(category: EventCategory): String?
    fun write(category: EventCategory, value: String?)
}

class TastePreferencesStore(private val storage: TastePreferenceStorage) {
    private val values = MutableStateFlow(EventCategory.entries.associateWith { category ->
        storage.read(category)?.let { saved -> TasteLevel.entries.firstOrNull { it.name == saved } }
            ?: TasteLevel.UNSPECIFIED
    })
    val preferences: StateFlow<Map<EventCategory, TasteLevel>> = values.asStateFlow()

    @Synchronized fun set(category: EventCategory, level: TasteLevel) {
        storage.write(category, level.takeUnless { it == TasteLevel.UNSPECIFIED }?.name)
        values.value = values.value + (category to level)
    }

    @Synchronized fun reset() {
        EventCategory.entries.forEach { storage.write(it, null) }
        values.value = EventCategory.entries.associateWith { TasteLevel.UNSPECIFIED }
    }
}
