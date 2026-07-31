package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFamilyGrouperTest {
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
        val iciParent = event("ici-parent", "Fêtes de la Madeleine 2026", "https://ici.fr/a", "2026-07-22T00:00:00Z", "2026-07-26T23:00:00Z")
        val iciChild = event("ici-child", "Programme du mercredi", "https://ici.fr/a", "2026-07-22T18:00:00Z", "2026-07-22T23:00:00Z")
        val sudParent = event("sud-parent", "Mercredi 22 juillet : ouverture des fêtes", "https://sudouest.fr/madeleine-2026-programme-complet", "2026-07-22T09:00:00Z", "2026-07-22T23:00:00Z")
        val sudChild = event("sud-child", "Jeudi 23 juillet : journée des pitchouns", "https://sudouest.fr/madeleine-2026-programme-complet", "2026-07-23T09:00:00Z", "2026-07-23T23:00:00Z")

        val families = EventFamilyGrouper.group(listOf(iciParent, iciChild, sudParent, sudChild))

        assertEquals(1, families.size)
        assertEquals(2, families.single().children.size)
    }

    private fun event(id: String, title: String, source: String, start: String, end: String) = Event(
        id, title, "Description", null, EventCategory.COMMUNITY,
        Instant.parse(start), Instant.parse(end), "Mont-de-Marsan", "Mont-de-Marsan",
        43.8900, -0.5000, source, null, null, EventPrice.Free, Instant.EPOCH, DataOrigin.MANUAL,
    )
}
