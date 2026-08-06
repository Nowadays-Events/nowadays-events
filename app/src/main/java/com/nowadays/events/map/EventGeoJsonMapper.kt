package com.nowadays.events.map

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventStatus
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

object EventGeoJsonMapper {
    const val EVENT_ID_PROPERTY = "event_id"

    fun map(
        events: List<Event>,
        mainEventIds: Set<String> = emptySet(),
        childEventIds: Set<String> = emptySet(),
        childCounts: Map<String, Int> = emptyMap(),
    ): FeatureCollection {
        val positions = displayPositions(events)
        val today = LocalDate.now()
        return FeatureCollection.fromFeatures(
            events.map { event ->
            val (latitude, longitude) = positions.getValue(event)
            Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
                addStringProperty(EVENT_ID_PROPERTY, event.id)
                addStringProperty("title", event.title)
                addStringProperty("category", event.category.name)
                addStringProperty("status", event.status.name)
                addStringProperty("event_icon", "event-marker-${event.id}")
                addStringProperty(
                    "event_date_label",
                    event.startsAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM")),
                )
                addBooleanProperty("is_main_event", event.id in mainEventIds)
                addBooleanProperty("is_child_event", event.id in childEventIds)
                addNumberProperty("child_count", childCounts[event.id] ?: 0)
                childCounts[event.id]?.takeIf { it > 0 }?.let { addStringProperty("main_badge_icon", "main-child-count-$it") }
                val days = ChronoUnit.DAYS.between(today, event.startsAt.atZone(ZoneId.systemDefault()).toLocalDate()).coerceAtLeast(0)
                val opacity = when {
                    event.status == EventStatus.CANCELLED -> 0.5
                    event.status == EventStatus.POSTPONED -> 0.75
                    days <= 1 -> 1.0
                    days >= 7 -> 0.35
                    else -> 1.0 - (days - 1) * 0.108
                }
                addNumberProperty("event_opacity", opacity)
            }
            },
        )
    }

    fun displayPositions(events: List<Event>): Map<Event, Pair<Double, Double>> =
        events.groupBy { it.latitude to it.longitude }.flatMap { (_, colocated) ->
            colocated.mapIndexed { index, event -> event to displayPosition(event, index, colocated.size) }
        }.toMap()

    private fun displayPosition(event: Event, index: Int, count: Int): Pair<Double, Double> {
        if (count == 1) return event.latitude to event.longitude
        val ring = index / 8
        val positionOnRing = index % 8
        val itemsOnRing = minOf(8, count - ring * 8)
        val angle = 2 * PI * positionOnRing / itemsOnRing
        // About 70 m on the first ring: enough to expose separate touch targets
        // for programmes whose children share the exact venue coordinates.
        val radius = 0.00065 * (ring + 1)
        val latitude = event.latitude + radius * sin(angle)
        val longitude = event.longitude + radius * cos(angle) / cos(Math.toRadians(event.latitude))
        return latitude to longitude
    }
}
