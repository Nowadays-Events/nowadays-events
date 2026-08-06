package com.nowadays.events.map

internal object ClusterZoomPolicy {
    private const val COLLAPSE_DELTA = 0.45

    fun shouldCollapse(expansionZoom: Double?, currentZoom: Double): Boolean =
        expansionZoom != null && currentZoom <= expansionZoom - COLLAPSE_DELTA
}
