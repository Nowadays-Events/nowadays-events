package com.nowadays.events.map

import com.nowadays.events.domain.model.Event
import java.security.MessageDigest

internal data class ScreenPoint(val x: Float, val y: Float)
internal data class EventCluster(val id: String, val events: List<Event>)

internal object ProximityClusterer {
    fun group(
        events: List<Event>,
        positions: Map<String, ScreenPoint>,
        distancePixels: Float,
    ): List<EventCluster> {
        val byId = events.associateBy(Event::id)
        val remaining = events.map(Event::id).filter(positions::containsKey).toMutableSet()
        val groups = mutableListOf<EventCluster>()
        while (remaining.isNotEmpty()) {
            val members = mutableListOf<String>()
            val queue = ArrayDeque<String>().apply { add(remaining.minOrNull()!!) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!remaining.remove(current)) continue
                members += current
                val point = positions.getValue(current)
                remaining.sorted().filter { candidate ->
                    val other = positions.getValue(candidate)
                    val dx = point.x - other.x
                    val dy = point.y - other.y
                    dx * dx + dy * dy <= distancePixels * distancePixels
                }.forEach(queue::addLast)
            }
            val orderedIds = members.sorted()
            groups += EventCluster(stableId(orderedIds), orderedIds.map(byId::getValue))
        }
        return groups.sortedBy(EventCluster::id)
    }

    private fun stableId(ids: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ids.joinToString("\u0000").toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return "cluster-$digest"
    }
}
