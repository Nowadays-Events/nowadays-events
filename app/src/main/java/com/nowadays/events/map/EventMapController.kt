package com.nowadays.events.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Path
import android.view.Gravity
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventStatus
import kotlin.math.floor
import kotlin.math.min
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.toString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class EventMapController(
    private val onEventSelected: (String) -> Unit,
    private val onClusterExpanded: (Set<String>) -> Unit = {},
    private val onBackgroundClick: () -> Unit = {},
    private val initialCenter: LatLng = DEFAULT_CENTER,
    private val initialZoom: Double = DEFAULT_ZOOM,
    private val onCameraChanged: (LatLng, Double) -> Unit = { _, _ -> },
) {
    private var map: MapLibreMap? = null
    private var pendingEvents: List<Event> = emptyList()
    private var pendingMainEventIds: Set<String> = emptySet()
    private var pendingChildEventIds: Set<String> = emptySet()
    private var pendingChildCounts: Map<String, Int> = emptyMap()
    private var pendingExpandedMainEvent: Event? = null
    private var clusterMembers: Map<String, List<Event>> = emptyMap()
    private var expandedClusterEventIds: Set<String> = emptySet()
    private var renderedEventPoints: Map<String, Point> = emptyMap()
    private var renderedClusterPoints: Map<String, Point> = emptyMap()

    fun attach(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        mapLibreMap.uiSettings.compassGravity = Gravity.TOP or Gravity.END
        mapLibreMap.uiSettings.setCompassMargins(0, 190, 20, 0)
        mapLibreMap.cameraPosition = CameraPosition.Builder().target(initialCenter).zoom(initialZoom).build()
        mapLibreMap.addOnCameraIdleListener {
            mapLibreMap.cameraPosition.target?.let { onCameraChanged(it, mapLibreMap.cameraPosition.zoom) }
            if (mapLibreMap.cameraPosition.zoom < CLUSTER_REFORM_ZOOM && expandedClusterEventIds.isNotEmpty()) {
                expandedClusterEventIds = emptySet()
                onClusterExpanded(emptySet())
            }
            refreshSource()
        }
        mapLibreMap.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
            style.addSource(GeoJsonSource(EVENT_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
            style.addSource(GeoJsonSource(HIERARCHY_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray())))
            style.addLayer(
                LineLayer(HIERARCHY_LAYER_ID, HIERARCHY_SOURCE_ID).withProperties(
                    lineColor(Color.rgb(103, 80, 164)),
                    lineWidth(3f),
                    lineOpacity(0.6f),
                ),
            )
            style.addLayer(
                SymbolLayer(CLUSTER_LAYER_ID, EVENT_SOURCE_ID)
                    .withFilter(eq(get(IS_CLUSTER_PROPERTY), literal(true)))
                    .withProperties(
                        iconImage(get(CLUSTER_ICON_PROPERTY)),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
            )
            style.addLayer(
                SymbolLayer(INDIVIDUAL_EVENT_LAYER_ID, EVENT_SOURCE_ID)
                    .withFilter(eq(get(IS_CLUSTER_PROPERTY), literal(false)))
                    .withProperties(
                        iconImage(get("event_icon")),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
            )
            style.addLayer(
                SymbolLayer(MAIN_BADGE_LAYER_ID, EVENT_SOURCE_ID)
                    .withFilter(eq(get("is_main_event"), literal(true)))
                    .withProperties(
                        iconImage(get("main_badge_icon")),
                        iconOffset(arrayOf(18f, -18f)),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
            )
            installClickHandling(mapLibreMap)
            refreshSource()
        }
    }

    fun setEvents(
        events: List<Event>,
        mainEventIds: Set<String> = emptySet(),
        childEventIds: Set<String> = emptySet(),
        childCounts: Map<String, Int> = emptyMap(),
        expandedMainEvent: Event? = null,
        expandedClusterIds: Set<String> = emptySet(),
    ) {
        pendingEvents = events
        pendingMainEventIds = mainEventIds
        pendingChildEventIds = childEventIds
        pendingChildCounts = childCounts
        pendingExpandedMainEvent = expandedMainEvent
        expandedClusterEventIds = expandedClusterIds.intersect(events.map(Event::id).toSet())
        refreshSource()
    }

    fun recenter(target: LatLng = DEFAULT_CENTER, zoom: Double = DEFAULT_ZOOM) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom), 500)
    }

    fun focus(event: Event) {
        val mapLibreMap = map ?: return
        val targetZoom = (mapLibreMap.cameraPosition.zoom + 0.7).coerceIn(13.0, 15.5)
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(event.latitude, event.longitude), targetZoom),
            420,
        )
    }

    fun frame(events: List<Event>) {
        if (events.isEmpty()) return
        if (events.size == 1 || events.map { it.latitude to it.longitude }.distinct().size == 1) {
            return recenter(LatLng(events.first().latitude, events.first().longitude), 15.5)
        }
        val bounds = LatLngBounds.Builder().includes(events.map { LatLng(it.latitude, it.longitude) }).build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 550)
    }

    private fun refreshSource() {
        val mapLibreMap = map ?: return
        mapLibreMap.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(EVENT_SOURCE_ID) ?: return@getStyle
            val regularFeatures = EventGeoJsonMapper.map(
                pendingEvents, pendingMainEventIds, pendingChildEventIds, pendingChildCounts,
            ).features().orEmpty()
                .associateBy { it.getStringProperty(EventGeoJsonMapper.EVENT_ID_PROPERTY) }
            pendingChildCounts.values.filter { it > 0 }.toSet().forEach { count ->
                style.addImage("main-child-count-$count", createBadgeBitmap(count))
            }
            pendingEvents.forEach { event ->
                style.addImage(
                    "event-marker-${event.id}",
                    createEventMarkerBitmap(
                        event = event,
                        isMain = event.id in pendingMainEventIds,
                        isChild = event.id in pendingChildEventIds,
                    ),
                )
            }
            updateHierarchyLines(style, regularFeatures)
            // While exploring a family, every child must remain individually visible.
            // Otherwise clustering is driven only by the actual on-screen distance:
            // a fixed zoom cut-off could remove a cluster before its markers were visible.
            if (pendingExpandedMainEvent != null || mapLibreMap.cameraPosition.zoom >= INDIVIDUAL_MARKERS_ZOOM) {
                clusterMembers = emptyMap()
                renderedClusterPoints = emptyMap()
                renderedEventPoints = regularFeatures.mapNotNull { (id, feature) ->
                    (feature.geometry() as? Point)?.let { id to it }
                }.toMap()
                source.setGeoJson(FeatureCollection.fromFeatures(regularFeatures.values.map(::markAsEvent)))
                return@getStyle
            }

            val forcedFeatures = expandedClusterEventIds.mapNotNull(regularFeatures::get).map(::markAsEvent)
            val groups = proximityGroups(mapLibreMap, pendingEvents.filterNot { it.id in expandedClusterEventIds })
            val members = mutableMapOf<String, List<Event>>()
            val clusterPoints = mutableMapOf<String, Point>()
            val clusterIcons = mutableSetOf<Pair<Int, Boolean>>()
            val features = groups.mapIndexedNotNull { index, events ->
                if (events.size == 1) {
                    regularFeatures[events.single().id]?.let(::markAsEvent)
                } else {
                    val key = "cluster-$index"
                    members[key] = events
                    // Anchor the bubble on a real event. When it separates during zoom,
                    // at least that event remains exactly under the former bubble.
                    val anchor = EventClusterGeometry.anchor(events)
                    clusterPoints[key] = Point.fromLngLat(anchor.longitude, anchor.latitude)
                    Feature.fromGeometry(Point.fromLngLat(anchor.longitude, anchor.latitude)).apply {
                        addBooleanProperty(IS_CLUSTER_PROPERTY, true)
                        addNumberProperty(POINT_COUNT_PROPERTY, events.size)
                        val hasCancelled = events.any { it.status == EventStatus.CANCELLED }
                        addStringProperty(CLUSTER_ICON_PROPERTY, clusterIconId(events.size, hasCancelled))
                        addStringProperty(CLUSTER_KEY_PROPERTY, key)
                    }
                        .also { clusterIcons += events.size to events.any { it.status == EventStatus.CANCELLED } }
                }
            }
            clusterIcons.forEach { (count, hasCancelled) ->
                style.addImage(clusterIconId(count, hasCancelled), createClusterBitmap(count, hasCancelled))
            }
            clusterMembers = members
            renderedClusterPoints = clusterPoints
            val visibleFeatures = features + forcedFeatures
            renderedEventPoints = visibleFeatures.mapNotNull { feature ->
                if (!feature.hasProperty(EventGeoJsonMapper.EVENT_ID_PROPERTY)) return@mapNotNull null
                val id = feature.getStringProperty(EventGeoJsonMapper.EVENT_ID_PROPERTY)
                (feature.geometry() as? Point)?.let { id to it }
            }.toMap()
            source.setGeoJson(FeatureCollection.fromFeatures(visibleFeatures))
        }
    }

    private fun proximityGroups(mapLibreMap: MapLibreMap, events: List<Event>): List<List<Event>> {
        val remaining = events.toMutableSet()
        val positions = events.associateWith {
            mapLibreMap.projection.toScreenLocation(LatLng(it.latitude, it.longitude))
        }
        val groups = mutableListOf<List<Event>>()
        while (remaining.isNotEmpty()) {
            val group = mutableListOf<Event>()
            val queue = ArrayDeque<Event>()
            queue += remaining.first()
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!remaining.remove(current)) continue
                group += current
                val point = positions.getValue(current)
                remaining.filter { candidate ->
                    val other = positions.getValue(candidate)
                    val dx = point.x - other.x
                    val dy = point.y - other.y
                    dx * dx + dy * dy <= CLUSTER_DISTANCE_PIXELS * CLUSTER_DISTANCE_PIXELS
                }.forEach(queue::addLast)
            }
            groups += group
        }
        return groups
    }

    private fun markAsEvent(feature: Feature) = feature.apply {
        addBooleanProperty(IS_CLUSTER_PROPERTY, false)
    }

    private fun clusterIconId(count: Int, hasCancelled: Boolean = false) =
        "event-cluster-icon-$count-${if (hasCancelled) "cancelled" else "active"}"

    private fun createClusterBitmap(count: Int, hasCancelled: Boolean = false): Bitmap {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (hasCancelled) Color.rgb(183, 28, 28) else Color.rgb(103, 80, 164)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawCircle(size / 2f, size / 2f, 31f, circlePaint)
        canvas.drawCircle(size / 2f, size / 2f, 31f, borderPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (count < 100) 27f else 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(count.toString(), size / 2f, baseline, textPaint)
        return bitmap
    }

    private fun createBadgeBitmap(count: Int): Bitmap {
        val size = 42
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 184, 0) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
        }
        canvas.drawCircle(21f, 21f, 18f, fill)
        canvas.drawCircle(21f, 21f, 18f, border)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textAlign = Paint.Align.CENTER; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(count.toString(), 21f, 21f - (text.ascent() + text.descent()) / 2f, text)
        return bitmap
    }

    private fun createEventMarkerBitmap(event: Event, isMain: Boolean, isChild: Boolean): Bitmap {
        val width = 112
        val height = 126
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val priority = MapMarkerPolicy.priority(event)
        val alpha = priority.alpha
        val radius = priority.radius
        val isLongRunning = MapMarkerPolicy.isLongRunning(event)
        val isRecurring = MapMarkerPolicy.isRecurring(event)
        val isCancelled = event.status == EventStatus.CANCELLED
        val isPostponed = event.status == EventStatus.POSTPONED
        val isUnverified = event.status == EventStatus.UNVERIFIED
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isCancelled -> Color.rgb(198, 40, 40)
                isPostponed -> Color.rgb(239, 108, 0)
                isUnverified -> Color.rgb(96, 104, 112)
                isRecurring -> Color.rgb(0, 121, 107)
                isLongRunning -> Color.rgb(0, 150, 136)
                isChild -> Color.rgb(90, 150, 210)
                isMain -> Color.rgb(103, 80, 164)
                else -> categoryColor(event)
            }
            this.alpha = when {
                isCancelled -> minOf(alpha, 130)
                isPostponed -> minOf(alpha, 205)
                isUnverified -> minOf(alpha, 115)
                else -> alpha
            }
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = if (isMain) 7f else 4f
        }
        if (isMain && !isCancelled && !isPostponed && !isUnverified) {
            canvas.drawCircle(width / 2f, 35f, radius + 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(103, 80, 164)
                style = Paint.Style.STROKE
                strokeWidth = 6f
            })
        }
        val pin = Path().apply {
            moveTo(width / 2f, 75f)
            lineTo(width / 2f - radius * 0.72f, 48f)
            lineTo(width / 2f + radius * 0.72f, 48f)
            close()
        }
        canvas.drawPath(pin, fill)
        canvas.drawCircle(width / 2f, 34f, radius, fill)
        canvas.drawCircle(width / 2f, 34f, radius, border)
        if (isCancelled) {
            drawCancelledGlyph(canvas, width / 2f, 34f)
        } else if (isPostponed) {
            drawPostponedGlyph(canvas, width / 2f, 34f)
        } else if (isUnverified) {
            drawUnverifiedGlyph(canvas, width / 2f, 34f)
        } else if (isMain) {
            val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(width / 2f, 34f, 6f, center)
        } else if (isRecurring) {
            drawRecurringGlyph(canvas, width / 2f, 34f)
        } else if (isLongRunning) {
            drawDurationGlyph(canvas, width / 2f, 34f)
        } else {
            drawCategoryGlyph(canvas, event, width / 2f, 34f)
        }
        val date = MapMarkerPolicy.displayDate(event).format(DateTimeFormatter.ofPattern("dd/MM"))
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 45)
            textAlign = Paint.Align.CENTER
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bounds = Rect()
        datePaint.getTextBounds(date, 0, date.length, bounds)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255) }
        canvas.drawRoundRect(
            width / 2f - bounds.width() / 2f - 7f,
            86f,
            width / 2f + bounds.width() / 2f + 7f,
            120f,
            10f,
            10f,
            label,
        )
        canvas.drawText(date, width / 2f, 112f, datePaint)
        return bitmap
    }

    private fun categoryColor(event: Event): Int = when (event.category.name) {
        "MUSIC" -> Color.rgb(156, 72, 192)
        "SPORT" -> Color.rgb(0, 137, 123)
        "FOOD" -> Color.rgb(239, 108, 0)
        "FAMILY" -> Color.rgb(216, 71, 122)
        "COMMUNITY" -> Color.rgb(49, 104, 183)
        "TECHNOLOGY" -> Color.rgb(0, 137, 180)
        else -> Color.rgb(225, 154, 0)
    }

    private fun drawCategoryGlyph(canvas: Canvas, event: Event, x: Float, y: Float) {
        val glyph = when (event.category.name) {
            "MUSIC" -> "♪"
            "SPORT" -> "●"
            "FOOD" -> "◆"
            "FAMILY" -> "♥"
            "COMMUNITY" -> "●"
            "TECHNOLOGY" -> "⌁"
            else -> "✦"
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(glyph, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun drawDurationGlyph(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(x - 11f, y - 10f, x + 11f, y + 10f, 3f, 3f, paint)
        canvas.drawLine(x - 11f, y - 3f, x + 11f, y - 3f, paint)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(x - 5f, y + 3f, 2f, dot)
        canvas.drawCircle(x + 5f, y + 3f, 2f, dot)
    }

    private fun drawRecurringGlyph(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(RectF(x - 12f, y - 11f, x + 12f, y + 11f), 35f, 270f, false, paint)
        val arrow = Path().apply {
            moveTo(x + 10f, y - 9f)
            lineTo(x + 13f, y - 2f)
            lineTo(x + 5f, y - 3f)
        }
        canvas.drawPath(arrow, Paint(paint).apply { style = Paint.Style.FILL })
    }

    private fun drawCancelledGlyph(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(x - 9f, y - 9f, x + 9f, y + 9f, paint)
        canvas.drawLine(x + 9f, y - 9f, x - 9f, y + 9f, paint)
    }

    private fun drawPostponedGlyph(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 27f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("!", x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun drawUnverifiedGlyph(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 25f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("?", x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun updateHierarchyLines(style: Style, regularFeatures: Map<String, Feature>) {
        val parent = pendingExpandedMainEvent
        val children = pendingEvents.filter { it.id in pendingChildEventIds }
        val parentPoint = parent?.let { regularFeatures[it.id]?.geometry() as? Point }
        val features = if (parentPoint == null) emptyList() else children.mapNotNull { child ->
            val childPoint = regularFeatures[child.id]?.geometry() as? Point ?: return@mapNotNull null
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                parentPoint,
                childPoint,
            )))
        }
        style.getSourceAs<GeoJsonSource>(HIERARCHY_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun installClickHandling(mapLibreMap: MapLibreMap) {
        mapLibreMap.addOnMapClickListener { coordinate ->
            val clusterKey = nearestPointId(mapLibreMap, coordinate, renderedClusterPoints, CLUSTER_HIT_RADIUS_PIXELS)
            if (clusterKey != null) {
                val members = clusterMembers[clusterKey].orEmpty()
                if (members.isNotEmpty()) {
                    expandedClusterEventIds = members.map(Event::id).toSet()
                    onClusterExpanded(expandedClusterEventIds)
                    refreshSource()
                    frame(members)
                }
                return@addOnMapClickListener true
            }
            val eventId = nearestEventId(mapLibreMap, coordinate)
            if (eventId != null) onEventSelected(eventId) else {
                if (expandedClusterEventIds.isNotEmpty()) {
                    expandedClusterEventIds = emptySet()
                    refreshSource()
                }
                onBackgroundClick()
            }
            eventId != null
        }
    }

    private fun nearestEventId(
        mapLibreMap: MapLibreMap,
        tap: LatLng,
    ): String? = nearestPointId(mapLibreMap, tap, renderedEventPoints, EVENT_HIT_RADIUS_PIXELS)

    private fun nearestPointId(
        mapLibreMap: MapLibreMap,
        tap: LatLng,
        points: Map<String, Point>,
        hitRadiusPixels: Float,
    ): String? {
        return MapHitResolver.nearest(
            tap = GeoPoint(tap.latitude, tap.longitude),
            zoom = mapLibreMap.cameraPosition.zoom,
            points = points.mapValues { (_, point) -> GeoPoint(point.latitude(), point.longitude()) },
            hitRadiusPixels = hitRadiusPixels,
        )
    }

    companion object {
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val EVENT_SOURCE_ID = "events"
        private const val CLUSTER_LAYER_ID = "event-clusters"
        private const val INDIVIDUAL_EVENT_LAYER_ID = "individual-event-symbols"
        private const val EVENT_LAYER_ID = "event-points"
        private const val CHILD_EVENT_LAYER_ID = "child-event-points"
        private const val MAIN_EVENT_LAYER_ID = "main-event-ring"
        private const val MAIN_BADGE_LAYER_ID = "main-event-badge"
        private const val HIERARCHY_SOURCE_ID = "event-hierarchy"
        private const val HIERARCHY_LAYER_ID = "event-hierarchy-lines"
        private const val IS_CLUSTER_PROPERTY = "is_cluster"
        private const val POINT_COUNT_PROPERTY = "point_count"
        private const val CLUSTER_KEY_PROPERTY = "cluster_key"
        private const val CLUSTER_ICON_PROPERTY = "cluster_icon"
        private const val CLUSTER_DISTANCE_PIXELS = 104f
        private const val INDIVIDUAL_MARKERS_ZOOM = 15.0
        private const val CLUSTER_REFORM_ZOOM = 14.4
        private const val EVENT_HIT_RADIUS_PIXELS = 54f
        private const val CLUSTER_HIT_RADIUS_PIXELS = 42f
        private const val DEFAULT_ZOOM = 11.5
        private val DEFAULT_CENTER = LatLng(48.8566, 2.3522)
    }
}
