package com.nowadays.events.map

import kotlin.math.cos

internal data class GeoPoint(val latitude: Double, val longitude: Double)

internal object MapHitResolver {
    fun nearest(
        tap: GeoPoint,
        zoom: Double,
        points: Map<String, GeoPoint>,
        hitRadiusPixels: Float,
    ): String? {
        val latitudeRadians = Math.toRadians(tap.latitude)
        val metersPerPixel = 156543.03392 * cos(latitudeRadians) / Math.pow(2.0, zoom)
        val radiusMetersSquared = metersPerPixel * hitRadiusPixels *
            metersPerPixel * hitRadiusPixels
        return points.entries.map { (id, point) ->
            val dLatMeters = (point.latitude - tap.latitude) * 111_320.0
            val dLonMeters = (point.longitude - tap.longitude) * 111_320.0 * cos(latitudeRadians)
            id to (dLatMeters * dLatMeters + dLonMeters * dLonMeters)
        }.minByOrNull { it.second }
            ?.takeIf { it.second <= radiusMetersSquared }
            ?.first
    }
}
