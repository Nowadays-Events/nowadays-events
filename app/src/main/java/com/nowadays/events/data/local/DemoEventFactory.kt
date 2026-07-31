package com.nowadays.events.data.local

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class DemoEventFactory @Inject constructor(private val clock: Clock) {
    fun create(zoneId: ZoneId = ZoneId.systemDefault()): List<Event> {
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val locations = listOf(
            Triple("Hôtel de Ville", "Place de l'Hôtel de Ville, Paris", 48.8566 to 2.3522),
            Triple("Parc de la Villette", "211 avenue Jean Jaurès, Paris", 48.8938 to 2.3908),
            Triple("Bibliothèque François-Mitterrand", "Quai François Mauriac, Paris", 48.8339 to 2.3750),
            Triple("Jardin du Luxembourg", "Rue de Médicis, Paris", 48.8462 to 2.3372),
            Triple("La Recyclerie", "83 boulevard Ornano, Paris", 48.8976 to 2.3449),
            Triple("Bercy Village", "Cour Saint-Émilion, Paris", 48.8331 to 2.3868),
        )
        val categories = EventCategory.entries
        return (0 until 30).map { index ->
            val location = locations[index % locations.size]
            val date = when {
                index < 6 -> today
                index < 14 -> monday.plusDays((index % 7).toLong())
                index < 22 -> monday.plusDays(4 + (index % 3).toLong())
                else -> today.plusDays((index + 3).toLong())
            }
            val start = date.atTime(LocalTime.of(10 + index % 9, 0)).atZone(zoneId).toInstant()
            Event(
                id = "demo-event-${index + 1}", title = "Événement fictif ${index + 1}",
                shortDescription = "Démonstration — ne correspond pas à un événement réel.",
                fullDescription = "Événement fictif créé pour tester la carte, les filtres et les regroupements.",
                category = categories[index % categories.size], startsAt = start, endsAt = start.plusSeconds(7_200),
                venueName = location.first, address = location.second,
                latitude = location.third.first, longitude = location.third.second,
                sourceUrl = "https://example.invalid/events/${index + 1}", imageUrl = null,
                organizer = "Organisateur fictif", price = if (index % 3 == 0) EventPrice.Free else EventPrice.Paid(500 + index * 100),
                updatedAt = clock.instant(), origin = DataOrigin.DEMO,
                goingCount = (index * 7) % 43, maybeCount = (index * 5) % 29, isFictional = true,
            )
        }
    }
}

