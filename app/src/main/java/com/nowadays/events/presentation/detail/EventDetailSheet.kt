package com.nowadays.events.presentation.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nowadays.events.domain.model.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: Event,
    relatedEventCount: Int = 0,
    deleteEventCount: Int = 1,
    sourceUrls: List<String> = listOf(event.sourceUrl),
    attendance: AttendanceResponse,
    onAttendanceChanged: (AttendanceResponse) -> Unit,
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var expanded by androidx.compose.runtime.remember(event.id) { androidx.compose.runtime.mutableStateOf(false) }
    val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    val headerColor = when (event.status) {
        EventStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
        EventStatus.POSTPONED -> MaterialTheme.colorScheme.tertiaryContainer
        EventStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = headerColor,
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (expanded && relatedEventCount > 0) Text("Événement principal · $relatedEventCount rendez-vous liés", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (expanded) event.organizer?.let { Text("Par $it", style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (event.status) {
                            EventStatus.CANCELLED -> AssistChip(
                                onClick = {}, label = { Text("ANNULÉ") },
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error),
                            )
                            EventStatus.POSTPONED -> AssistChip(onClick = {}, label = { Text("REPORTÉ") })
                            EventStatus.ACTIVE -> Unit
                        }
                        AssistChip(onClick = {}, label = { Text(event.category.label()) })
                        if (event.isFictional) AssistChip(onClick = {}, label = { Text("DÉMO") }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(priceLabel(event.price), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (!expanded) return@Column
            when (event.status) {
                EventStatus.CANCELLED -> Text(
                    "Cet événement a été annulé par l’organisateur. Il reste affiché pour vous en informer.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                EventStatus.POSTPONED -> Text(
                    "Cet événement est reporté. Consultez la source avant de vous déplacer.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
                EventStatus.ACTIVE -> Unit
            }
            InfoRow(Icons.Default.CalendarMonth, "Quand", "${date.format(event.startsAt)}\n${date.format(event.endsAt)}")
            InfoRow(Icons.Default.LocationOn, event.venueName, event.address)
            HorizontalDivider()
            Text("À propos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(event.fullDescription ?: event.shortDescription, style = MaterialTheme.typography.bodyLarge)
            if (sourceUrls.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${sourceUrls.size} source${if (sourceUrls.size > 1) "s" else ""} associée${if (sourceUrls.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        sourceUrls.forEachIndexed { index, url ->
                            TextButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Link, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Ouvrir la source ${index + 1}", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            if (event.status != EventStatus.CANCELLED) ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Affluence indicative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(attendance == AttendanceResponse.GOING, { onAttendanceChanged(AttendanceResponse.GOING) }, label = { Text("J’y vais · ${event.goingCount}") })
                        FilterChip(attendance == AttendanceResponse.MAYBE, { onAttendanceChanged(AttendanceResponse.MAYBE) }, label = { Text("Peut-être · ${event.maybeCount}") })
                    }
                    Text("Compteurs anonymes, sans liste de participants.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = {
                val uri = "geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(${Uri.encode(event.venueName)})".toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Directions, null); Spacer(Modifier.width(8.dp)); Text("Itinéraire") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val text = "${event.title}\n${date.format(event.startsAt)}\n${event.sourceUrl}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager l’événement"))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Partager") }
            }
            if (event.origin == DataOrigin.MANUAL) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Supprimer cet événement", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer l’événement ?") },
        text = {
            Text(
                if (deleteEventCount > 1)
                    "Cette action supprimera l’événement principal et ses ${deleteEventCount - 1} sous-événements de cet appareil."
                else "Cette action supprimera uniquement cet événement de cet appareil.",
            )
        },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )
}

@Composable private fun InfoRow(icon: ImageVector, title: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, null, Modifier.padding(10.dp)) }
    Column { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun EventCategory.label() = name.lowercase().replaceFirstChar(Char::uppercase)
private fun priceLabel(price: EventPrice): String = when (price) {
    EventPrice.Free -> "Gratuit"
    is EventPrice.Paid -> price.amountCents?.let { "%.2f %s".format(it / 100.0, price.currency) } ?: "Payant"
}
