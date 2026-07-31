package com.nowadays.events.presentation.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@Composable
fun LocationPickerDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by remember { mutableStateOf(LatLng(initialLatitude, initialLongitude)) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            view.getMapAsync { map ->
                map.setStyle("https://tiles.openfreemap.org/styles/liberty")
                map.cameraPosition = CameraPosition.Builder().target(selected).zoom(if (initialLatitude == 46.6) 5.0 else 14.0).build()
                map.addOnMapClickListener { point -> selected = point; true }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Touchez l’emplacement") },
        text = { Column { AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(420.dp)); Text("Point : ${selected.latitude.format()}, ${selected.longitude.format()}") } },
        confirmButton = { TextButton(onClick = { onConfirm(selected.latitude, selected.longitude) }) { Text("Utiliser ce point") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> mapView.onStart(); Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause(); Lifecycle.Event.ON_STOP -> mapView.onStop(); else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onDestroy() }
    }
}

private fun Double.format() = "%.5f".format(this)
