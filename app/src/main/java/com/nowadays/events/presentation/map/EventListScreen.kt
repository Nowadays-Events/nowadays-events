@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nowadays.events.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.BuildConfig
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.usecase.EventClassifier
import com.nowadays.events.domain.usecase.NearbyEvent
import com.nowadays.events.domain.usecase.NearbyEvents
import com.nowadays.events.presentation.eventScheduleLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class ReferencePlace(val label: String, val latitude: Double, val longitude: Double)
private val montDeMarsan = ReferencePlace("Mont-de-Marsan", 43.8904, -0.5007)
private val saintPierreDuMont = ReferencePlace("Saint-Pierre-du-Mont", 43.8849, -0.5217)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onShowMap: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("event_list", Context.MODE_PRIVATE) }
    var reference by remember {
        mutableStateOf(if (preferences.getString("reference", "mont-de-marsan") == "saint-pierre") saintPierreDuMont else montDeMarsan)
    }
    var customLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var customLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    val savedLatitude = customLatitude
    val savedLongitude = customLongitude
    val customLocation: Pair<Double, Double>? = if (savedLatitude != null && savedLongitude != null) savedLatitude to savedLongitude else null
    var usingMyPosition by rememberSaveable { mutableStateOf(false) }
    var locationUnavailable by remember { mutableStateOf(false) }
    var radiusKm by rememberSaveable { mutableIntStateOf(preferences.getInt("radius", 30)) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            lastListLocation(context).also { customLatitude = it?.first; customLongitude = it?.second }
            usingMyPosition = customLatitude != null
            locationUnavailable = customLatitude == null
        } else locationUnavailable = true
    }
    val coordinates = customLocation ?: (reference.latitude to reference.longitude)
    val results = remember(state.events, coordinates, radiusKm) {
        NearbyEvents.find(state.events, coordinates.first, coordinates.second, radiusKm.toDouble())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Autour de ${if (usingMyPosition) "moi" else reference.label}", style = MaterialTheme.typography.titleLarge)
                        val updated = state.dataUpdatedAt?.atZone(ZoneId.systemDefault())
                            ?.format(DateTimeFormatter.ofPattern("dd/MM à HH:mm"))
                        Text(
                            if (updated == null) "Xymis · v${BuildConfig.VERSION_NAME}" else "Actualisé le $updated · v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShowMap, modifier = Modifier.testTag("open-map")) {
                        Icon(Icons.Default.Map, contentDescription = "Afficher la carte")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterBar(state.selectedFilter, state.customStartDate, state.customEndDate, viewModel::selectFilter, { showCalendar = true })
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                item {
                    AssistChip(
                        onClick = {
                            usingMyPosition = false; customLatitude = null; customLongitude = null; reference = montDeMarsan
                            preferences.edit().putString("reference", "mont-de-marsan").apply()
                        },
                        label = { Text(if (!usingMyPosition && reference == montDeMarsan) "✓ Mont-de-Marsan" else "Mont-de-Marsan") },
                        modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp),
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            usingMyPosition = false; customLatitude = null; customLongitude = null; reference = saintPierreDuMont
                            preferences.edit().putString("reference", "saint-pierre").apply()
                        },
                        label = { Text(if (!usingMyPosition && reference == saintPierreDuMont) "✓ Saint-Pierre" else "Saint-Pierre") },
                        modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp),
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            locationUnavailable = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                lastListLocation(context).also { customLatitude = it?.first; customLongitude = it?.second }
                                usingMyPosition = customLatitude != null
                                locationUnavailable = customLatitude == null
                            } else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        },
                        leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                        label = { Text(if (usingMyPosition) "✓ Ma position" else "Ma position") },
                        modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp).testTag("use-my-position"),
                    )
                }
            }
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                items(listOf(5, 15, 30, 50)) { radius ->
                    FilterChip(
                        selected = radiusKm == radius,
                        onClick = { radiusKm = radius; preferences.edit().putInt("radius", radius).apply() },
                        label = { Text("$radius km") },
                        leadingIcon = if (radiusKm == radius) {{ Text("✓") }} else null,
                        modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp).testTag("radius-$radius"),
                    )
                }
            }
            when {
                state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("event-list-loading"))
                locationUnavailable -> Text(
                    "Position indisponible. Choisissez Ville ou Plage, ou activez la localisation puis réessayez.",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun événement dans ce rayon pour cette période.", Modifier.padding(24.dp).testTag("event-list-empty"))
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("event-list"),
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(results, key = { it.event.id }) { result -> EventListCard(result) { onOpenEvent(result.event) } }
                }
            }
        }
    }
    if (showCalendar) DateFilterDialog(
        state.customStartDate, state.customEndDate, { showCalendar = false },
        { start, end -> viewModel.selectCustomRange(start, end); showCalendar = false },
        { viewModel.selectFilter(com.nowadays.events.domain.model.TimeFilter.ALL_FUTURE); showCalendar = false },
    )
}

@Composable
private fun EventListCard(result: NearbyEvent, onClick: () -> Unit) {
    val event = result.event
    val classification = remember(event) { EventClassifier.classify(event) }
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("event-card-${event.id}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(eventScheduleLabel(event), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(distanceLabel(result.distanceKm), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, modifier = Modifier.testTag("event-distance-${event.id}"))
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(event.venueName.ifBlank { event.address }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(classification.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SuggestionChip(onClick = {}, label = { Text(categoryLabel(event.category)) }, modifier = Modifier.heightIn(min = 48.dp))
                if (event.price !is EventPrice.Unknown) SuggestionChip(onClick = {}, label = { Text(priceLabel(event.price)) }, modifier = Modifier.heightIn(min = 48.dp))
                if (event.price is EventPrice.Unknown) Text("Tarif non renseigné", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                if (event.scheduleType == EventScheduleType.RECURRING) AssistChip({}, { Text("Récurrent") }, modifier = Modifier.heightIn(min = 48.dp))
                if (event.scheduleType == EventScheduleType.CONTINUOUS) AssistChip({}, { Text("En continu") }, modifier = Modifier.heightIn(min = 48.dp))
                if (event.status == EventStatus.CANCELLED) AssistChip({}, { Text("ANNULÉ") }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error), modifier = Modifier.heightIn(min = 48.dp).testTag("event-cancelled-${event.id}"))
            }
        }
    }
}

private fun distanceLabel(distanceKm: Double) = if (distanceKm < 10) "%.1f km".format(distanceKm) else "%.0f km".format(distanceKm)
private fun categoryLabel(category: EventCategory) = when (category) {
    EventCategory.CULTURE -> "Culture"; EventCategory.MUSIC -> "Musique"; EventCategory.SPORT -> "Sport"
    EventCategory.FOOD -> "Gastronomie"; EventCategory.FAMILY -> "Famille"; EventCategory.COMMUNITY -> "Vie locale"; EventCategory.TECHNOLOGY -> "Technologie"
}
private fun priceLabel(price: EventPrice) = when (price) {
    EventPrice.Free -> "Gratuit"
    EventPrice.Unknown -> "Tarif inconnu"
    is EventPrice.Paid -> price.amountCents?.let { "%.2f €".format(it / 100.0) } ?: "Payant"
}

private fun lastListLocation(context: Context): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }?.let { it.latitude to it.longitude }
}
