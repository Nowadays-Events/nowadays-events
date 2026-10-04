package com.nowadays.events.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nowadays.events.domain.model.Event
import com.nowadays.events.presentation.eventScheduleLabel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun HiddenEventsSheet(
    matchingEvents: List<Event>, allEvents: List<Event>, retainedKeyCount: Int,
    onReveal: (Event) -> Unit, onRevealAll: () -> Unit, onDismiss: () -> Unit,
    now: Instant = Instant.now(),
) {
    var currentFilterOnly by remember { mutableStateOf(true) }
    val events = if (currentFilterOnly) matchingEvents else allEvents
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("hidden-events-sheet")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 640.dp)) {
            Text("Événements masqués", style = MaterialTheme.typography.titleLarge)
            Text("Choix local à cet appareil. Aucun événement n’est supprimé.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(currentFilterOnly, { currentFilterOnly = true }, label = { Text("Filtre actif (${matchingEvents.size})") })
                FilterChip(!currentFilterOnly, { currentFilterOnly = false }, label = { Text("Tous (${allEvents.size})") }, modifier = Modifier.testTag("hidden-show-all"))
            }
            if (retainedKeyCount > allEvents.size) Text(
                "${retainedKeyCount - allEvents.size} choix conservé(s) pour des événements absents du catalogue. Leur masquage sera conservé s’ils reviennent.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRevealAll, modifier = Modifier.heightIn(min = 48.dp).testTag("reveal-all-events")) {
                Text("Tout réafficher ($retainedKeyCount)")
            }
            if (events.isEmpty()) Text("Aucun événement masqué pour ce filtre. Consultez « Tous » ou réaffichez tous les choix.", Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.weight(1f, fill = false).testTag("hidden-event-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(events, key = Event::id) { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text(eventScheduleLabel(event, now = now), style = MaterialTheme.typography.bodySmall)
                        Text(event.venueName, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onReveal(event) }, modifier = Modifier.heightIn(min = 48.dp).testTag("reveal-event-${event.id}")) {
                            Text("Réafficher")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
