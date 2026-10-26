package com.nowadays.events.presentation.settings

import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TasteLevel
import com.nowadays.events.domain.usecase.tasteStorageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TasteSettingsLabelsTest {
    @Test fun fallbackCategoryKeepsItsIdentifierAndGetsItsUserFacingLabel() {
        assertEquals("Vie locale et divers", tasteCategoryLabel(EventCategory.COMMUNITY))
        assertEquals("taste_COMMUNITY", tasteStorageKey(EventCategory.COMMUNITY))
        assertNotEquals(tasteCategoryLabel(EventCategory.COMMUNITY), tasteStorageKey(EventCategory.COMMUNITY))
        assertEquals(7, EventCategory.entries.map(::tasteCategoryLabel).distinct().size)
        assertEquals("Sans avis", tasteLevelLabel(TasteLevel.UNSPECIFIED))
        assertEquals("Moyen", tasteLevelLabel(TasteLevel.MEDIUM))
    }
}
