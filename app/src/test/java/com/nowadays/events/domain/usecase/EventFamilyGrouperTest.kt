package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.*
import com.nowadays.events.map.MapSelectionPolicy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFamilyGrouperTest {
    @Test fun `two official recurring bricolage events remain independently visible`() {
        val soiree = event(
            "api-d421201775b075210e22015e32fb1902", "Soirée bricolage",
            "https://www.montdemarsan-tourisme.com/preparer-mon-sejour/agenda/soiree-bricolage-mont-de-marsan-fr-6758790",
            "2026-09-09T22:00:00Z", "2026-10-29T22:59:59Z",
        ).copy(
            scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-10-08T16:00:00Z"),
            occurrenceStarts = listOf(Instant.parse("2026-10-08T16:00:00Z"), Instant.parse("2026-10-15T16:00:00Z")),
        )
        val jeudis = event(
            "api-bb45462dc037c01d8b9c61f50c3289a8", "Les jeudis du bricolage",
            "https://www.montdemarsan-tourisme.com/preparer-mon-sejour/agenda/les-jeudis-du-bricolage-mont-de-marsan-fr-6819511",
            "2026-10-07T22:00:00Z", "2026-11-29T22:59:59Z",
        ).copy(
            scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-10-08T16:45:00Z"),
            occurrenceStarts = listOf(Instant.parse("2026-10-08T16:45:00Z"), Instant.parse("2026-10-15T16:00:00Z")),
        )

        val families = EventFamilyGrouper.group(listOf(soiree, jeudis))
        assertEquals(setOf(soiree.id, jeudis.id), families.map { it.main.id }.toSet())
        assertEquals(0, families.sumOf { it.children.size })
    }

    @Test fun `generic Grand Dax agenda URL never creates an artificial family`() {
        val url = "https://www.dax-tourisme.com/agenda-evenements/tout-agenda"
        val events = listOf(
            event("dax-1", "Concert jazz", url, "2026-10-08T18:00:00Z", "2026-10-08T20:00:00Z"),
            event("dax-2", "Marché des producteurs", url, "2026-10-09T08:00:00Z", "2026-10-09T12:00:00Z").copy(
                venueName = "Halles de Dax", latitude = 43.71, longitude = -1.05, shortDescription = "Produits locaux",
            ),
            event("dax-3", "Exposition photographique", url, "2026-10-10T09:00:00Z", "2026-11-15T18:00:00Z").copy(
                venueName = "Musée", latitude = 43.70, longitude = -1.04, shortDescription = "Photographies",
                scheduleType = EventScheduleType.CONTINUOUS,
            ),
        )

        val families = EventFamilyGrouper.group(events)
        assertEquals(events.map { it.id }.toSet(), families.map { it.main.id }.toSet())
        assertEquals(0, families.sumOf { it.children.size })
    }
    @Test
    fun `does not merge unrelated events from the same agenda website`() {
        val visit = event(
            "visit",
            "Visite du Rucher de Claron",
            "https://www.montdemarsan-tourisme.com/preparer-mon-sejour/agenda/visite-du-rucher/",
            "2026-07-28T08:00:00Z",
            "2026-07-28T09:45:00Z",
        )
        val carriage = event(
            "carriage",
            "Balade en calèche",
            "https://www.montdemarsan-tourisme.com/preparer-mon-sejour/agenda/balade-en-caleche/",
            "2026-07-29T07:00:00Z",
            "2026-07-29T09:00:00Z",
        )

        assertEquals(2, EventFamilyGrouper.group(listOf(visit, carriage)).size)
    }

    @Test fun `merges two sources describing the same festival`() {
        val iciParent = event("ici-parent", "Fêtes de la Madeleine 2026", "https://ici.fr/fetes-madeleine-2026", "2026-07-22T00:00:00Z", "2026-07-26T23:00:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        val iciChild = event("ici-child", "Programme du mercredi", "https://ici.fr/fetes-madeleine-2026", "2026-07-22T18:00:00Z", "2026-07-22T23:00:00Z")
        val sudParent = event("sud-parent", "Mercredi 22 juillet : ouverture des fêtes", "https://sudouest.fr/madeleine-2026-programme-complet", "2026-07-22T09:00:00Z", "2026-07-22T23:00:00Z")
        val sudChild = event("sud-child", "Jeudi 23 juillet : journée des pitchouns", "https://sudouest.fr/madeleine-2026-programme-complet", "2026-07-23T09:00:00Z", "2026-07-23T23:00:00Z")

        val families = EventFamilyGrouper.group(listOf(iciParent, iciChild, sudParent, sudChild))

        assertEquals(1, families.size)
        assertEquals(3, families.single().children.size)
        assertEquals(setOf(iciParent.id, iciChild.id, sudParent.id, sudChild.id), families.single().events.map { it.id }.toSet())
        assertEquals("sud-child", MapSelectionPolicy.retainIfVisible("sud-child", families.single().events.map { it.id }.toSet()))
        assertEquals("ici-parent", MapSelectionPolicy.retainIfVisible("ici-parent", families.single().events.map { it.id }.toSet()))
    }

    @Test fun `same title on different dates stays as two visible entries`() {
        val first = event("one", "Atelier photographie", "https://example.org/agenda/atelier", "2026-10-08T18:00:00Z", "2026-10-08T20:00:00Z")
        val second = event("two", "Atelier photographie", "https://example.org/agenda/atelier", "2026-10-15T18:00:00Z", "2026-10-15T20:00:00Z")
        assertEquals(setOf("one", "two"), EventFamilyGrouper.group(listOf(first, second)).map { it.main.id }.toSet())
    }

    @Test fun `same venue does not imply one event family`() {
        val lecture = event("lecture", "Club lecture", "https://example.org/agenda/lecture", "2026-10-29T13:15:00Z", "2026-10-29T15:00:00Z")
        val concert = event("concert", "Concert acoustique", "https://example.org/agenda/concert", "2026-10-29T18:00:00Z", "2026-10-29T20:00:00Z")
        assertEquals(setOf("lecture", "concert"), EventFamilyGrouper.group(listOf(lecture, concert)).map { it.main.id }.toSet())
    }

    @Test fun `same venue and partially overlapping recurrences stay separate`() {
        val one = event("one", "Concert régulier", "https://example.org/agenda/concert", "2026-10-01T00:00:00Z", "2026-11-30T23:59:00Z")
            .copy(scheduleType = EventScheduleType.RECURRING, occurrenceStarts = listOf(Instant.parse("2026-10-08T18:00:00Z")))
        val two = event("two", "Concert invités", "https://example.org/agenda/invites", "2026-10-01T00:00:00Z", "2026-11-30T23:59:00Z")
            .copy(scheduleType = EventScheduleType.RECURRING, occurrenceStarts = listOf(Instant.parse("2026-10-08T19:00:00Z")))
        assertEquals(setOf("one", "two"), EventFamilyGrouper.group(listOf(one, two)).map { it.main.id }.toSet())
    }

    @Test fun `a specific festival program remains navigable without hiding a child`() {
        val parent = event("parent", "Festival principal", "https://example.org/festival-principal", "2026-10-01T00:00:00Z", "2026-10-04T23:59:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        val concert = event("concert", "Festival principal concert", "https://example.org/festival-principal-concert", "2026-10-02T18:00:00Z", "2026-10-02T20:00:00Z")
        val closing = event("closing", "Festival principal clôture", "https://example.org/festival-principal-cloture", "2026-10-04T18:00:00Z", "2026-10-04T20:00:00Z")
        val family = EventFamilyGrouper.group(listOf(parent, concert, closing)).single()
        assertEquals("parent", family.main.id)
        assertEquals(setOf("concert", "closing"), family.children.map { it.id }.toSet())
        assertEquals("concert", MapSelectionPolicy.retainIfVisible("concert", family.events.map { it.id }.toSet()))
        assertEquals("parent", MapSelectionPolicy.retainIfVisible("parent", family.events.map { it.id }.toSet()))
        assertEquals(3, EventFamilyGrouper.group(family.events).flatMap { it.events }.size)
        assertEquals(setOf("parent", "concert"), EventFamilyGrouper.group(listOf(parent, concert)).flatMap { it.events }.map { it.id }.toSet())
        assertEquals(null, MapSelectionPolicy.retainIfVisible("closing", setOf("parent", "concert")))
    }

    @Test fun `two sources describing the same continuous festival form one navigable family`() {
        val official = event("official", "Fêtes de la Madeleine", "https://office.example/fetes-madeleine", "2026-07-22T00:00:00Z", "2026-07-26T23:00:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        val partner = event("partner", "Fêtes de la Madeleine", "https://partner.example/madeleine", "2026-07-22T09:00:00Z", "2026-07-26T22:00:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        val family = EventFamilyGrouper.group(listOf(official, partner)).single()
        assertEquals(setOf("official", "partner"), family.events.map { it.id }.toSet())
        assertEquals(1, family.children.size)
    }

    @Test fun `same-site continuous events with the same title are not automatically one family`() {
        val oldSeries = event("old", "Repair Café", "https://official.example/agenda/repair-cafe-1", "2026-01-31T00:00:00Z", "2026-11-28T23:59:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        val newSeries = event("new", "Repair Café", "https://official.example/agenda/repair-cafe-2", "2026-09-26T00:00:00Z", "2027-07-28T23:59:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        assertEquals(setOf("old", "new"), EventFamilyGrouper.group(listOf(oldSeries, newSeries)).map { it.main.id }.toSet())
    }

    private fun event(id: String, title: String, source: String, start: String, end: String) = Event(
        id, title, "Description", null, EventCategory.COMMUNITY,
        Instant.parse(start), Instant.parse(end), "Mont-de-Marsan", "Mont-de-Marsan",
        43.8900, -0.5000, source, null, null, EventPrice.Free, Instant.EPOCH, DataOrigin.MANUAL,
    )
}
