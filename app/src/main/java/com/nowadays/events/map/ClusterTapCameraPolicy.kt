package com.nowadays.events.map

internal object ClusterTapCameraPolicy {
    private const val ZOOM_STEP = 1.0
    private const val MAX_AUTOMATIC_ZOOM = 15.0

    fun targetZoom(currentZoom: Double): Double =
        minOf(currentZoom + ZOOM_STEP, MAX_AUTOMATIC_ZOOM).coerceAtLeast(currentZoom)
}
