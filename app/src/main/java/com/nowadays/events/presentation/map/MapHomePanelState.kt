package com.nowadays.events.presentation.map

enum class MapHomePanelState { CLOSED, PREVIEW, EXPANDED }

/** Pure transition rules keep the three panel states predictable and testable. */
object MapHomePanelPolicy {
    private const val DRAG_THRESHOLD_PX = 48f

    fun onHandleTap(state: MapHomePanelState) = when (state) {
        MapHomePanelState.CLOSED -> MapHomePanelState.PREVIEW
        MapHomePanelState.PREVIEW -> MapHomePanelState.EXPANDED
        MapHomePanelState.EXPANDED -> MapHomePanelState.PREVIEW
    }

    fun onListButton() = MapHomePanelState.EXPANDED

    fun onMapButton() = MapHomePanelState.CLOSED

    fun onVerticalDrag(state: MapHomePanelState, distancePx: Float): MapHomePanelState {
        if (distancePx <= -DRAG_THRESHOLD_PX) return when (state) {
            MapHomePanelState.CLOSED -> MapHomePanelState.PREVIEW
            MapHomePanelState.PREVIEW -> MapHomePanelState.EXPANDED
            MapHomePanelState.EXPANDED -> state
        }
        if (distancePx >= DRAG_THRESHOLD_PX) return when (state) {
            MapHomePanelState.CLOSED -> state
            MapHomePanelState.PREVIEW -> MapHomePanelState.CLOSED
            MapHomePanelState.EXPANDED -> MapHomePanelState.PREVIEW
        }
        return state
    }
}
