package com.nowadays.events.map

import kotlin.math.roundToInt

internal data class MapViewportPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal fun mapViewportPadding(displayDensity: Float): MapViewportPadding = MapViewportPadding(
    left = 0,
    top = (140f * displayDensity).roundToInt(),
    right = 0,
    bottom = (72f * displayDensity).roundToInt(),
)
