package com.nowadays.events.map

import kotlin.math.roundToInt

internal object ClusterTapCameraPolicy {
    const val CLICKABLE_EVENTS_ZOOM = 15.0

    fun targetZoom(currentZoom: Double, fittedMembersZoom: Double): Double =
        maxOf(currentZoom, minOf(fittedMembersZoom, CLICKABLE_EVENTS_ZOOM))

    fun durationMs(currentZoom: Double, targetZoom: Double): Int {
        val zoomDistance = (targetZoom - currentZoom).coerceAtLeast(0.0)
        return (600 + zoomDistance * 100).roundToInt().coerceAtMost(1_200)
    }
}
