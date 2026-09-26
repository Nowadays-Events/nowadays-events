package com.nowadays.events.presentation.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Aperçu volontairement léger : un seul moteur MapLibre reste actif sur la carte complète. */
@Composable
internal fun EventMapPreview(onOpenMap: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = modifier.fillMaxWidth().height(164.dp).clickable(onClick = onOpenMap).testTag("event-map-preview"),
        shape = MaterialTheme.shapes.large,
        color = surface,
    ) {
        Box(Modifier.fillMaxSize().testTag("event-map-preview-no-gestures"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val grid = Color.Gray.copy(alpha = .18f)
                repeat(5) { index ->
                    val x = size.width * index / 4f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 2f)
                }
                repeat(4) { index ->
                    val y = size.height * index / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 2f)
                }
                drawCircle(primary.copy(alpha = .16f), 42f, center)
            }
            Icon(Icons.Default.LocationOn, "Emplacement de l’événement — ouvrir la carte", tint = primary)
        }
    }
}
