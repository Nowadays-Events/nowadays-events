package com.nowadays.events.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nowadays.events.domain.model.Event
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

@Composable
fun EventMap(
    events: List<Event>,
    mainEventIds: Set<String> = emptySet(),
    childEventIds: Set<String> = emptySet(),
    childCounts: Map<String, Int> = emptyMap(),
    expandedMainEvent: Event? = null,
    expandedClusterEventIds: Set<String> = emptySet(),
    onEventSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: EventMapController = remember(onEventSelected) { EventMapController(onEventSelected) },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            view.getMapAsync { map ->
                controller.attach(map, context.resources.displayMetrics.density)
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = {
        controller.setEvents(events, mainEventIds, childEventIds, childCounts, expandedMainEvent, expandedClusterEventIds)
    })

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
}
