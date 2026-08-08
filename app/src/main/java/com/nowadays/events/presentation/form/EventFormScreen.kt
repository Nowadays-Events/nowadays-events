package com.nowadays.events.presentation.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import android.app.TimePickerDialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.domain.model.EventCategory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormScreen(onBack: () -> Unit, onSaved: (Double, Double) -> Unit, viewModel: EventFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showOptional by remember { mutableStateOf(false) }
    var publishedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        viewModel.saved.collect { point ->
            publishedPoint = point
            delay(1_300)
            onSaved(point.first, point.second)
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajouter un événement local") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
        }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Text(
                "Cet ajout reste sur cet appareil. Les événements du flux public sont contrôlés par l’administrateur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gagner du temps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Collez le lien d’une page d’événement : Xymis Events tente d’en extraire le titre, la date et le lieu.")
                    FormField("Lien de l’événement", state.importUrl, null, KeyboardType.Uri) {
                        viewModel.update(EventFormField.IMPORT_URL, it)
                    }
                    Button(onClick = viewModel::importFromLink, enabled = !state.isImporting, modifier = Modifier.fillMaxWidth()) {
                        if (state.isImporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Analyser et préremplir")
                    }
                    state.importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    state.importCandidates.forEachIndexed { index, candidate ->
                        OutlinedButton(onClick = { viewModel.selectImportCandidate(index) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(candidate.title, fontWeight = FontWeight.SemiBold)
                                Text(listOf(candidate.startsAt, candidate.venue).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.importCandidates.size > 1) Button(
                        onClick = viewModel::saveAllImported,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Créer les ${state.importCandidates.size} événements") }
                }
            }
            Text("ou saisir manuellement", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormSection("L’essentiel") {
                FormField("Titre *", state.title, state.errors.title) { viewModel.update(EventFormField.TITLE, it) }
                FormField("Description courte *", state.shortDescription, state.errors.shortDescription, minLines = 2) {
                    viewModel.update(EventFormField.SHORT_DESCRIPTION, it)
                }
                Text("Catégorie", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventCategory.entries.forEach { category -> FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category.label()) },
                    ) }
                }
                FriendlyDateTimeField("Début *", state.startsAt, state.errors.dates) { viewModel.update(EventFormField.STARTS_AT, it) }
                FriendlyDateTimeField("Fin *", state.endsAt, state.errors.dates) { viewModel.update(EventFormField.ENDS_AT, it) }
                FormField("URL de la source *", state.sourceUrl, state.errors.sourceUrl, KeyboardType.Uri) { viewModel.update(EventFormField.SOURCE_URL, it) }
            }
            FormSection("Lieu") {
                FormField("Ville ou commune *", state.city, null, onValueChange = viewModel::searchCity)
                SuggestionList(state.citySuggestions.map { it.label }) { viewModel.selectCity(state.citySuggestions[it]) }
                FormField("Nom du lieu *", state.venueName, state.errors.venueName, onValueChange = viewModel::searchVenue)
                SuggestionList(state.venueSuggestions.map { it.label }) { viewModel.selectVenue(state.venueSuggestions[it]) }
                FormField("Adresse *", state.address, state.errors.address) { viewModel.update(EventFormField.ADDRESS, it) }
                var showMapPicker by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showMapPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Choisir précisément sur la carte")
                }
                if (state.latitude.isNotBlank()) Text("Position enregistrée : ${state.latitude.take(8)}, ${state.longitude.take(8)}", style = MaterialTheme.typography.bodySmall)
                if (showMapPicker) LocationPickerDialog(
                    initialLatitude = state.latitude.toDoubleOrNull() ?: 46.6,
                    initialLongitude = state.longitude.toDoubleOrNull() ?: 2.4,
                    onDismiss = { showMapPicker = false },
                    onConfirm = { lat, lon -> viewModel.setMapLocation(lat, lon); showMapPicker = false },
                )
            }
            OutlinedButton(onClick = { showOptional = !showOptional }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showOptional) "Masquer les détails facultatifs" else "Ajouter des détails facultatifs")
            }
            if (showOptional) FormSection("Détails facultatifs") {
                FormField("Description complète", state.fullDescription, null, minLines = 3) { viewModel.update(EventFormField.FULL_DESCRIPTION, it) }
                FormField("Organisateur", state.organizer, null) { viewModel.update(EventFormField.ORGANIZER, it) }
                FormField("URL de l’image", state.imageUrl, null, KeyboardType.Uri) { viewModel.update(EventFormField.IMAGE_URL, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text("Gratuit"); Switch(state.isFree, viewModel::setFree) }
                if (!state.isFree) FormField("Prix en euros *", state.priceEuros, state.errors.price, KeyboardType.Decimal) { viewModel.update(EventFormField.PRICE_EUROS, it) }
            }
            Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state.isSaving) "Enregistrement…" else "Enregistrer sur cet appareil")
            }
            Text("* Champs obligatoires", style = MaterialTheme.typography.bodySmall)
            }
            if (publishedPoint != null) {
                ElevatedCard(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "Événement enregistré sur cet appareil",
                        Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content()
    } }
}

@Composable private fun FormField(
    label: String, value: String, error: String?, keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier, minLines: Int = 1, onValueChange: (String) -> Unit,
) = OutlinedTextField(
    value, onValueChange, modifier.fillMaxWidth(), label = { Text(label) },
    supportingText = if (error != null) {{ Text(error) }} else null, isError = error != null,
    minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
)

private fun EventCategory.label() = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable private fun SuggestionList(labels: List<String>, onSelected: (Int) -> Unit) {
    labels.take(4).forEachIndexed { index, label ->
        TextButton(onClick = { onSelected(index) }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FriendlyDateTimeField(label: String, value: String, error: String?, onValueChange: (String) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    OutlinedTextField(
        value = value.replace('T', ' '), onValueChange = {}, readOnly = true, isError = error != null,
        label = { Text(label) }, supportingText = if (error != null) {{ Text(error) }} else null,
        trailingIcon = { IconButton(onClick = { showDate = true }) { Icon(Icons.Default.CalendarMonth, "Choisir la date") } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (showDate) {
        val initial = runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrDefault(LocalDate.now())
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton(onClick = {
                val date = Instant.ofEpochMilli(state.selectedDateMillis ?: return@TextButton).atZone(ZoneId.systemDefault()).toLocalDate()
                showDate = false
                TimePickerDialog(context, { _, hour, minute -> onValueChange(date.atTime(hour, minute).toString()) }, 18, 0, true).show()
            }) { Text("Choisir l’heure") }
        }, dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler") } }) { DatePicker(state) }
    }
}
