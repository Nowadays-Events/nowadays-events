package com.nowadays.events.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nowadays.events.domain.model.Event

/** Localisation explicite et fiable : aucun faux aperçu de carte n'est affiché. */
@Composable
internal fun EventMapPreview(event: Event, onOpenMap: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenMap).testTag("event-map-preview"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(Modifier.fillMaxWidth().padding(18.dp).testTag("event-map-preview-no-gestures"), contentAlignment = Alignment.CenterStart) {
            Icon(Icons.Default.LocationOn, "Emplacement de l’événement — ouvrir la carte", tint = primary)
            Column(Modifier.padding(start = 46.dp)) {
                Text(event.venueName.ifBlank { "Lieu de l’événement" }, fontWeight = FontWeight.SemiBold)
                Text(normalizedAddress(event.address), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Toucher pour voir le marqueur", style = MaterialTheme.typography.labelMedium, color = primary)
            }
        }
    }
}
