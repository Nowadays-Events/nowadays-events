package com.nowadays.events.map

internal sealed interface MapTapAction {
    data class SelectEvent(val eventId: String) : MapTapAction
    data class ExpandCluster(val eventIds: Set<String>) : MapTapAction
    data object Clear : MapTapAction
}

internal object MapInteractionPolicy {
    fun resolve(eventId: String?, clusterEventIds: Set<String>?): MapTapAction = when {
        !clusterEventIds.isNullOrEmpty() -> MapTapAction.ExpandCluster(clusterEventIds)
        eventId != null -> MapTapAction.SelectEvent(eventId)
        else -> MapTapAction.Clear
    }

    fun requestsZoom(action: MapTapAction): Boolean = action is MapTapAction.ExpandCluster
}

object MapSelectionPolicy {
    fun retainIfVisible(selectedId: String?, visibleIds: Set<String>): String? =
        selectedId?.takeIf(visibleIds::contains)

    fun expandedClusterIds(ids: Set<String>, visibleIds: Set<String>): Set<String> =
        ids.intersect(visibleIds)
}
