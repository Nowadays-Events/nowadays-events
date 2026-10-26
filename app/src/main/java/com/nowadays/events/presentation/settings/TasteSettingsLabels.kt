package com.nowadays.events.presentation.settings

import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TasteLevel

internal fun tasteCategoryLabel(category: EventCategory): String = when (category) {
    EventCategory.CULTURE -> "Culture"
    EventCategory.MUSIC -> "Musique"
    EventCategory.SPORT -> "Sport"
    EventCategory.FOOD -> "Gastronomie"
    EventCategory.FAMILY -> "Famille"
    EventCategory.COMMUNITY -> "Vie locale et divers"
    EventCategory.TECHNOLOGY -> "Technologie"
}

internal fun tasteLevelLabel(level: TasteLevel): String = when (level) {
    TasteLevel.UNSPECIFIED -> "Sans avis"
    TasteLevel.LOW -> "Faible"
    TasteLevel.MEDIUM -> "Moyen"
    TasteLevel.HIGH -> "Fort"
}
