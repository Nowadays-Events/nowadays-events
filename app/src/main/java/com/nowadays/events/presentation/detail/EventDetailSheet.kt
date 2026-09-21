@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nowadays.events.presentation.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nowadays.events.domain.model.*
import com.nowadays.events.presentation.eventScheduleLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(event: Event, relatedEventCount: Int = 0, deleteEventCount: Int = 1,
    sourceUrls: List<String> = event.sourceUrls, attendance: AttendanceResponse,
    onAttendanceChanged: (AttendanceResponse) -> Unit, onDelete: () -> Unit = {}, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("event-detail-sheet")) {
        EventDetailContent(event, relatedEventCount, deleteEventCount, sourceUrls, attendance,
            onAttendanceChanged, onDelete, modifier = Modifier.heightIn(max = 590.dp).padding(horizontal = 18.dp))
    }
}

@Composable
fun EventDetailContent(event: Event, relatedEventCount: Int = 0, deleteEventCount: Int = 1,
    sourceUrls: List<String> = event.sourceUrls, attendance: AttendanceResponse,
    onAttendanceChanged: (AttendanceResponse) -> Unit, onDelete: () -> Unit = {},
    onShowMap: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    val headerColor = when (event.status) { EventStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer; EventStatus.POSTPONED -> MaterialTheme.colorScheme.tertiaryContainer; EventStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer; EventStatus.UNVERIFIED -> MaterialTheme.colorScheme.surfaceVariant }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("event-detail-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = headerColor.copy(alpha = .68f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusBadges(event)
                Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                event.organizer?.takeIf(String::isNotBlank)?.let { Text("Par $it") }
                if (relatedEventCount > 0) Text("Événement principal · $relatedEventCount rendez-vous liés", color = MaterialTheme.colorScheme.primary)
                InfoRow(Icons.Default.CalendarMonth, "Quand", eventDateLabel(event, formatter))
                InfoRow(Icons.Default.LocationOn, event.venueName.ifBlank { "Lieu" }, normalizedAddress(event.address))
            }
        }
        statusMessage(event.status)?.let { Text(it, color = if (event.status == EventStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
        Text("À propos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(event.fullDescription?.takeIf(String::isNotBlank) ?: event.shortDescription, style = MaterialTheme.typography.bodyLarge)
        if (onShowMap != null) OutlinedButton(onClick = onShowMap, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("show-event-on-map")) { Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Voir sur la carte") }
        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(${Uri.encode(event.venueName)})".toUri())) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Directions, null); Spacer(Modifier.width(8.dp)); Text("Itinéraire") }
        if (sourceUrls.any(String::isNotBlank)) ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            sourceUrls.filter(String::isNotBlank).distinct().forEach { url -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir ${sourceLabel(url)}", Modifier.fillMaxWidth()) } }
        } }
        if (event.status != EventStatus.CANCELLED) ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Affluence indicative", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(attendance == AttendanceResponse.GOING, { onAttendanceChanged(AttendanceResponse.GOING) }, label = { Text("J’y vais · ${event.goingCount}") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(attendance == AttendanceResponse.MAYBE, { onAttendanceChanged(AttendanceResponse.MAYBE) }, label = { Text("Peut-être · ${event.maybeCount}") }, modifier = Modifier.heightIn(min = 48.dp))
            }
        } }
        OutlinedButton(onClick = { val text = "${event.title}\n${eventScheduleLabel(event)}\n${event.sourceUrl}"; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager l’événement")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Partager") }
        TextButton(onClick = {
            val uri = Uri.parse("mailto:vincent.delporte84@outlook.fr").buildUpon()
                .appendQueryParameter("subject", "[Xymis Events] Signalement : ${event.title}")
                .appendQueryParameter("body", "Événement : ${event.title}\nIdentifiant : ${event.id}\n\nPrécisions : ").build()
            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Flag, null); Spacer(Modifier.width(8.dp)); Text("Signaler une information incorrecte") }
        if (event.origin == DataOrigin.MANUAL) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Supprimer cet événement", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Supprimer l’événement ?") }, text = { Text(if (deleteEventCount > 1) "Cette action supprimera cet événement et ses rendez-vous liés de cet appareil." else "Cette action supprimera uniquement cet événement de cet appareil.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } })
}

@Composable private fun StatusBadges(event: Event) = FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    when (event.status) { EventStatus.CANCELLED -> AssistChip({}, { Text("ANNULÉ") }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)); EventStatus.POSTPONED -> AssistChip({}, { Text("REPORTÉ") }); EventStatus.UNVERIFIED -> AssistChip({}, { Text("À VÉRIFIER") }); EventStatus.ACTIVE -> Unit }
    AssistChip({}, { Text(event.category.label()) })
    if (event.scheduleType == EventScheduleType.RECURRING) AssistChip({}, { Text("Récurrent") })
    if (event.scheduleType == EventScheduleType.CONTINUOUS) AssistChip({}, { Text("En continu") })
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) { Text(priceLabel(event.price), Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }
}
@Composable private fun InfoRow(icon: ImageVector, title: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, null, Modifier.padding(10.dp)) }; Column { Text(title, fontWeight = FontWeight.SemiBold); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun statusMessage(status: EventStatus) = when (status) { EventStatus.CANCELLED -> "Cet événement a été annulé par l’organisateur."; EventStatus.POSTPONED -> "Cet événement est reporté. Vérifiez la source avant de vous déplacer."; EventStatus.UNVERIFIED -> "Cette information n’a pas été confirmée récemment."; EventStatus.ACTIVE -> null }
private fun EventCategory.label() = when (this) { EventCategory.CULTURE -> "Culture"; EventCategory.MUSIC -> "Musique"; EventCategory.SPORT -> "Sport"; EventCategory.FOOD -> "Gastronomie"; EventCategory.FAMILY -> "Famille"; EventCategory.COMMUNITY -> "Vie locale"; EventCategory.TECHNOLOGY -> "Technologie" }
internal fun normalizedAddress(value: String): String = value.trim().replace(Regex("(?<=\\d)(?=[A-ZÀ-ÖØ-Þ])"), " ").replace(Regex("(?<=[a-zà-öø-ÿ])(?=France\\b)", RegexOption.IGNORE_CASE), ", ").replace(Regex("\\s*,\\s*"), ", ").replace(Regex("\\s{2,}"), " ")
internal fun eventDateLabel(event: Event, formatter: DateTimeFormatter): String = if (event.scheduleType != EventScheduleType.SINGLE || event.timePrecision != EventTimePrecision.EXACT) eventScheduleLabel(event) else "${formatter.format(event.startsAt)}\n${formatter.format(event.endsAt)}"
private fun sourceLabel(url: String) = runCatching { Uri.parse(url).host?.removePrefix("www.")?.takeIf(String::isNotBlank) }.getOrNull() ?: "la source"
private fun priceLabel(price: EventPrice) = when (price) { EventPrice.Unknown -> "Tarif non renseigné"; EventPrice.Free -> "Gratuit"; is EventPrice.Paid -> price.amountCents?.let { "%.2f %s".format(it / 100.0, price.currency) } ?: "Payant" }
