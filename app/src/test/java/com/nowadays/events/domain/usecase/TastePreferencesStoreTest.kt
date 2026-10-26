package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TastePreferencesStoreTest {
    private class MemoryStorage : TastePreferenceStorage {
        val saved = mutableMapOf<EventCategory, String>()
        val unrelated = mutableMapOf("reference_label" to "Mont-de-Marsan", "hidden_keys" to "id:api-a")
        override fun read(category: EventCategory): String? = saved[category]
        override fun write(category: EventCategory, value: String?) {
            if (value == null) saved.remove(category) else saved[category] = value
        }
    }

    @Test fun allSevenBusinessCategoriesStartUnspecifiedWithoutWritingDefaults() {
        val storage = MemoryStorage()
        val values = TastePreferencesStore(storage).preferences.value
        assertEquals(setOf(EventCategory.CULTURE, EventCategory.MUSIC, EventCategory.SPORT,
            EventCategory.FOOD, EventCategory.FAMILY, EventCategory.COMMUNITY,
            EventCategory.TECHNOLOGY), values.keys)
        assertTrue(values.values.all { it == TasteLevel.UNSPECIFIED })
        assertTrue(storage.saved.isEmpty())
    }

    @Test fun fourDistinctValuesRoundTripAcrossStoreReconstruction() {
        val storage = MemoryStorage()
        val store = TastePreferencesStore(storage)
        TasteLevel.entries.forEach { level ->
            store.set(EventCategory.MUSIC, level)
            assertEquals(level, TastePreferencesStore(storage).preferences.value[EventCategory.MUSIC])
        }
        assertEquals(4, TasteLevel.entries.size)
        assertFalse(TasteLevel.UNSPECIFIED == TasteLevel.MEDIUM)
    }

    @Test fun changingOneCategoryAndResettingNeverTouchesOtherPreferences() {
        val storage = MemoryStorage()
        val store = TastePreferencesStore(storage)
        store.set(EventCategory.MUSIC, TasteLevel.HIGH)
        store.set(EventCategory.COMMUNITY, TasteLevel.LOW)
        store.set(EventCategory.MUSIC, TasteLevel.MEDIUM)
        assertEquals(TasteLevel.LOW, store.preferences.value[EventCategory.COMMUNITY])
        assertEquals(TasteLevel.MEDIUM, store.preferences.value[EventCategory.MUSIC])
        store.reset()
        assertTrue(TastePreferencesStore(storage).preferences.value.values.all { it == TasteLevel.UNSPECIFIED })
        assertTrue(storage.saved.isEmpty())
        assertEquals("Mont-de-Marsan", storage.unrelated["reference_label"])
        assertEquals("id:api-a", storage.unrelated["hidden_keys"])
    }

    @Test fun unknownStoredValueFallsBackToUnspecified() {
        val storage = MemoryStorage()
        storage.saved[EventCategory.CULTURE] = "future-version-value"
        assertEquals(TasteLevel.UNSPECIFIED, TastePreferencesStore(storage).preferences.value[EventCategory.CULTURE])
    }
}
