@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.nowadays.events.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.BuildConfig
import com.nowadays.events.data.location.LocationSearchService
import com.nowadays.events.data.location.LocationSearcher
import com.nowadays.events.data.location.LocationSuggestion
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.usecase.NearbyEvents
import com.nowadays.events.presentation.eventScheduleLabel
import com.nowadays.events.presentation.form.LocationPickerDialog
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

internal data class ReferencePlace(val label: String, val latitude: Double, val longitude: Double, val kind: String = "city")
private val montDeMarsan = ReferencePlace("Mont-de-Marsan", 43.8904, -0.5007)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventListScreen(
    onShowMap: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onReferenceChanged: (ReferencePlace) -> Unit = {},
    onRadiusChanged: (Int) -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("event_list", Context.MODE_PRIVATE) }
    var collapsedEventIds by remember {
        mutableStateOf(loadCollapsedEventIds(preferences))
    }
    var reference by remember { mutableStateOf(loadReference(preferences)) }
    var radiusKm by rememberSaveable { mutableIntStateOf(preferences.getInt("radius", 30)) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    var showMapPicker by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var citySuggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var citySearchFailed by remember { mutableStateOf(false) }
    val locationSearch: LocationSearcher = remember { LocationSearchService(context.applicationContext) }

    fun selectPlace(place: ReferencePlace) {
        reference = place
        saveReference(preferences, place)
        onReferenceChanged(place)
        showPlaces = false
        locationMessage = null
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            val location = lastListLocation(context)
            if (location != null) selectPlace(ReferencePlacePolicy.fromGps(location.first, location.second))
            else locationMessage = ReferencePlacePolicy.gpsFailureMessage(permissionGranted = true)
        } else locationMessage = ReferencePlacePolicy.gpsFailureMessage(permissionGranted = false)
    }
    val nearby = remember(state.events, reference, radiusKm) { NearbyEvents.find(state.events, reference.latitude, reference.longitude, radiusKm.toDouble()) }
    val hiddenNearby = remember(state.hiddenEvents, reference, radiusKm) { NearbyEvents.find(state.hiddenEvents, reference.latitude, reference.longitude, radiusKm.toDouble()) }
    val listItems = remember(nearby) { nearby.map { NearbyListItem(it.event, it.distanceKm) } }
    val sections = remember(listItems, state.selectedFilter) { CompactEventListPolicy.sections(listItems, state.selectedFilter, Instant.now(), ZoneId.systemDefault()) }

    Scaffold(modifier = modifier, topBar = {
        TopAppBar(
            title = {
                Column(Modifier.clickable { showPlaces = true }.testTag("reference-place-button")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ReferencePlacePolicy.title(reference), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, "Changer le lieu")
                    }
                    Text("${periodLabel(state.selectedFilter)} · $radiusKm km · ${nearby.size} résultat${if (nearby.size > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            },
            actions = {
                IconButton({ showSearch = !showSearch }, Modifier.testTag("toggle-search")) { Icon(Icons.Default.Search, "Rechercher") }
                IconButton({ showFilters = true }, Modifier.testTag("open-filters")) { Icon(Icons.Default.Tune, "Filtres") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSearch) OutlinedTextField(state.searchQuery, viewModel::setSearchQuery, Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("event-search"), placeholder = { Text("Rechercher un événement") }, singleLine = true)
            CompactPeriodBar(state.selectedFilter, viewModel::selectFilter) { showCalendar = true }
            Text(searchHorizonLabel(state.selectedFilter, state.customEndDate, Clock.systemUTC()), Modifier.padding(horizontal = 16.dp, vertical = 3.dp).testTag("result-introduction"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${compactSyncStatusLabel(state.syncState, state.events.isNotEmpty(), state.syncDataPotentiallyStale)} · v${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp, vertical = 2.dp).testTag("compact-sync-state"), style = MaterialTheme.typography.labelSmall, color = if (state.syncState.status in setOf(SyncStatus.FAILED_EMPTY, SyncStatus.FAILED_WITH_CACHE)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.hiddenKeyCount > 0) TextButton(onClick = { showHidden = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("show-hidden-events")) {
                Text("Masqués (${hiddenNearby.size}) · Gérer")
            }
            when {
                state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                nearby.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hiddenNearby.isNotEmpty()) "Tous les résultats de ce filtre sont masqués sur cet appareil." else "Aucun événement dans ce rayon pour cette période.", Modifier.testTag("event-list-empty"))
                    if (hiddenNearby.isNotEmpty()) TextButton({ showHidden = true }, Modifier.heightIn(min = 48.dp).testTag("empty-show-hidden")) { Text("Voir les ${hiddenNearby.size} événements masqués") }
                }
                else -> LazyColumn(Modifier.fillMaxSize().testTag("event-list"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    sections.forEach { section ->
                        stickyHeader { Text(section.title, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        items(section.events, key = { it.event.id }) { item ->
                            CompactEventRow(
                                item = item,
                                isCollapsed = item.event.id in collapsedEventIds,
                                onToggleCollapsed = {
                                    collapsedEventIds = if (item.event.id in collapsedEventIds) {
                                        collapsedEventIds - item.event.id
                                    } else {
                                        collapsedEventIds + item.event.id
                                    }
                                    saveCollapsedEventIds(preferences, collapsedEventIds)
                                },
                                onHide = viewModel::hideEvent,
                                onOpenEvent = onOpenEvent,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }) {
        FilterSheet(radiusKm, state.selectedCategory, state.priceFilter, { radiusKm = it; preferences.edit().putInt("radius", it).apply(); onRadiusChanged(it) }, viewModel::selectCategory, viewModel::selectPriceFilter, viewModel::retrySync)
    }
    if (showPlaces) ModalBottomSheet(onDismissRequest = { showPlaces = false }) {
        PlaceSheet(reference, loadRecentPlaces(preferences), cityQuery, citySuggestions, citySearchFailed,
            onQuery = { value -> cityQuery = value; citySearchFailed = false; scope.launch { citySuggestions = locationSearch.search(value); citySearchFailed = value.length >= 3 && citySuggestions.isEmpty() } },
            onSelect = { selectPlace(ReferencePlacePolicy.fromSuggestion(it)) }, onRecent = ::selectPlace,
            onGps = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val location = lastListLocation(context)
                    if (location != null) selectPlace(ReferencePlacePolicy.fromGps(location.first, location.second)) else locationMessage = ReferencePlacePolicy.gpsFailureMessage(permissionGranted = true)
                } else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }, onMap = { showPlaces = false; showMapPicker = true })
    }
    locationMessage?.let { message -> AlertDialog({ locationMessage = null }, confirmButton = { TextButton({ locationMessage = null }) { Text("Compris") } }, text = { Text(message) }) }
    if (showMapPicker) LocationPickerDialog(reference.latitude, reference.longitude, { showMapPicker = false }) { lat, lon -> showMapPicker = false; selectPlace(ReferencePlace("Point choisi", lat, lon, "map")) }
    if (showCalendar) DateFilterDialog(state.customStartDate, state.customEndDate, { showCalendar = false }, { start, end -> viewModel.selectCustomRange(start, end); showCalendar = false }, { viewModel.selectFilter(TimeFilter.ALL_FUTURE); showCalendar = false })
    if (showHidden) HiddenEventsSheet(hiddenNearby.map { it.event }, state.allHiddenEvents, state.hiddenKeyCount,
        viewModel::revealEvent, viewModel::revealAllEvents, { showHidden = false })
}

@Composable
internal fun CompactEventRow(
    item: NearbyListItem,
    now: Instant = Instant.now(),
    isCollapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    onHide: ((Event) -> Unit)? = null,
    onOpenEvent: (Event) -> Unit,
) {
    val event = item.event
    var showActions by remember { mutableStateOf(false) }
    val status = when (event.status) { EventStatus.CANCELLED -> "ANNULÉ · "; EventStatus.POSTPONED -> "REPORTÉ · "; else -> "" }
    Surface(Modifier.fillMaxWidth().heightIn(min = if (isCollapsed) 52.dp else 72.dp).clickable { onOpenEvent(event) }.testTag("event-row-${event.id}"), color = if (event.status == EventStatus.CANCELLED) MaterialTheme.colorScheme.errorContainer.copy(alpha = .22f) else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isCollapsed) 0.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isCollapsed) Icon(
                if (event.status == EventStatus.CANCELLED) Icons.Default.Close else categoryPictogram(event.category),
                contentDescription = if (event.status == EventStatus.CANCELLED) "Annulé" else categoryLabel(event.category),
                modifier = Modifier.padding(end = 12.dp).size(20.dp),
                tint = if (event.status == EventStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = if (isCollapsed) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!isCollapsed) Text("$status${eventScheduleLabel(event, now = now)} · ${shortPlace(event)}", style = MaterialTheme.typography.bodySmall, color = if (event.status == EventStatus.CANCELLED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(distanceLabel(item.distanceKm), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.testTag("event-distance-${event.id}"))
            IconButton(onClick = onToggleCollapsed, modifier = Modifier.testTag("event-collapse-${event.id}")) {
                Icon(if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, if (isCollapsed) "Développer ${event.title}" else "Réduire ${event.title}")
            }
            if (onHide != null) Box {
                IconButton({ showActions = true }, Modifier.testTag("event-actions-${event.id}")) {
                    Icon(Icons.Default.MoreVert, "Actions pour ${event.title}")
                }
                DropdownMenu(showActions, { showActions = false }) {
                    DropdownMenuItem(text = { Text("Masquer cet événement") },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                        onClick = { showActions = false; onHide(event) }, modifier = Modifier.testTag("hide-event-${event.id}"))
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 52.dp))
}

// COMMUNITY also receives unknown source categories in legacy data: use a neutral symbol.
private fun categoryPictogram(category: EventCategory) = when (category) {
    EventCategory.CULTURE -> Icons.Default.TheaterComedy
    EventCategory.MUSIC -> Icons.Default.MusicNote
    EventCategory.SPORT -> Icons.Default.Sports
    EventCategory.FOOD -> Icons.Default.Restaurant
    EventCategory.FAMILY -> Icons.Default.FamilyRestroom
    EventCategory.TECHNOLOGY -> Icons.Default.Computer
    EventCategory.COMMUNITY -> Icons.Default.Event
}

@Composable private fun CompactPeriodBar(selected: TimeFilter, onSelect: (TimeFilter) -> Unit, onDates: () -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp).testTag("period-filter-bar")) {
        items(listOf(TimeFilter.TODAY to "Aujourd’hui", TimeFilter.TOMORROW to "Demain", TimeFilter.NEXT_7_DAYS to "7 jours", TimeFilter.THIS_WEEKEND to "Week-end")) { (filter, label) -> FilterChip(selected == filter, { onSelect(filter) }, { Text(if (selected == filter) "✓ $label" else label) }, Modifier.padding(horizontal = 3.dp).testTag("period-${filter.name.lowercase()}")) }
        item { FilterChip(selected == TimeFilter.CUSTOM, onDates, { Text(if (selected == TimeFilter.CUSTOM) "✓ Dates" else "Dates") }, Modifier.padding(horizontal = 3.dp).testTag("period-custom")) }
    }
}

@Composable private fun FilterSheet(radius: Int, category: EventCategory?, price: EventPriceFilter, onRadius: (Int) -> Unit, onCategory: (EventCategory?) -> Unit, onPrice: (EventPriceFilter) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Filtres", style = MaterialTheme.typography.titleLarge); Text("Rayon", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 15, 30, 50).forEach { value -> FilterChip(radius == value, { onRadius(value) }, { Text("$value km") }, Modifier.heightIn(min = 48.dp).testTag("radius-$value")) } }
        Text("Catégorie", fontWeight = FontWeight.SemiBold)
        LazyRow { item { FilterChip(category == null, { onCategory(null) }, { Text("Toutes") }) }; items(EventCategory.entries) { value -> FilterChip(category == value, { onCategory(value) }, { Text(categoryLabel(value)) }, Modifier.padding(start = 6.dp)) } }
        Text("Prix", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EventPriceFilter.entries.forEach { value -> FilterChip(price == value, { onPrice(value) }, { Text(when(value) { EventPriceFilter.ALL -> "Tous"; EventPriceFilter.FREE -> "Gratuit"; EventPriceFilter.PAID -> "Payant" }) }) } }
        TextButton(onRetry, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Refresh, null); Text(" Actualiser") }
    }
}

@Composable private fun PlaceSheet(reference: ReferencePlace, recent: List<ReferencePlace>, query: String, suggestions: List<LocationSuggestion>, failed: Boolean, onQuery: (String) -> Unit, onSelect: (LocationSuggestion) -> Unit, onRecent: (ReferencePlace) -> Unit, onGps: () -> Unit, onMap: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lieu de référence", style = MaterialTheme.typography.titleLarge)
        ListItem({ Text("Ma position actuelle") }, leadingContent = { Icon(Icons.Default.MyLocation, null) }, modifier = Modifier.clickable(onClick = onGps).testTag("choose-my-position"))
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("city-search"), placeholder = { Text("Rechercher une ville") }, singleLine = true)
        suggestions.take(5).forEach { suggestion -> ListItem({ Text(suggestion.label, maxLines = 2) }, leadingContent = { Icon(Icons.Default.LocationCity, null) }, modifier = Modifier.clickable { onSelect(suggestion) }) }
        if (failed) Text("Ville introuvable. Vérifiez le nom ou choisissez un point sur la carte.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (recent.isNotEmpty()) Text("Lieux récents", fontWeight = FontWeight.SemiBold)
        recent.forEach { place -> ListItem({ Text(place.label) }, trailingContent = { if (place == reference) Text("✓") }, modifier = Modifier.clickable { onRecent(place) }) }
        ListItem({ Text("Choisir un point sur la carte") }, leadingContent = { Icon(Icons.Default.AddLocationAlt, null) }, modifier = Modifier.clickable(onClick = onMap).testTag("choose-map-point"))
    }
}

internal fun compactSyncStatusLabel(state: SyncState, hasCache: Boolean, stale: Boolean): String { val date = state.lastSuccessAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd/MM à HH:mm")); return when (state.status) { SyncStatus.RUNNING -> "Actualisation…"; SyncStatus.SUCCESS -> if (stale) "Données potentiellement anciennes${date?.let { " · $it" }.orEmpty()}" else date?.let { "Actualisé le $it" } ?: "À jour"; SyncStatus.OFFLINE_WITH_CACHE -> "Hors connexion${date?.let { " · données du $it" }.orEmpty()}"; SyncStatus.FAILED_WITH_CACHE -> "Actualisation impossible${date?.let { " · données du $it" }.orEmpty()}"; SyncStatus.FAILED_EMPTY -> "Actualisation impossible · aucune donnée"; SyncStatus.NEVER -> if (hasCache) "Données locales" else "Première actualisation en attente" } }
private fun periodLabel(filter: TimeFilter) = when (filter) { TimeFilter.TODAY -> "Aujourd’hui"; TimeFilter.TOMORROW -> "Demain"; TimeFilter.THIS_WEEKEND -> "Week-end"; TimeFilter.NEXT_7_DAYS -> "7 jours"; TimeFilter.CUSTOM -> "Dates choisies"; TimeFilter.ALL_FUTURE -> "À venir" }
private fun distanceLabel(value: Double) = if (value < 10) "%.1f km".format(value) else "%.0f km".format(value)
private fun categoryLabel(value: EventCategory) = when (value) { EventCategory.CULTURE -> "Culture"; EventCategory.MUSIC -> "Musique"; EventCategory.SPORT -> "Sport"; EventCategory.FOOD -> "Gastronomie"; EventCategory.FAMILY -> "Famille"; EventCategory.COMMUNITY -> "Vie locale"; EventCategory.TECHNOLOGY -> "Technologie" }
private fun shortPlace(event: Event) = event.venueName.ifBlank { event.address }.substringBefore(',').ifBlank { "Lieu à confirmer" }
internal fun loadCollapsedEventIds(prefs: android.content.SharedPreferences): Set<String> = prefs.getStringSet("collapsed_event_ids", emptySet()).orEmpty().toSet()
internal fun saveCollapsedEventIds(prefs: android.content.SharedPreferences, ids: Set<String>) { prefs.edit().putStringSet("collapsed_event_ids", ids).apply() }
internal fun loadReference(prefs: android.content.SharedPreferences) = ReferencePlace(prefs.getString("reference_label", montDeMarsan.label) ?: montDeMarsan.label, java.lang.Double.longBitsToDouble(prefs.getLong("reference_lat", java.lang.Double.doubleToLongBits(montDeMarsan.latitude))), java.lang.Double.longBitsToDouble(prefs.getLong("reference_lon", java.lang.Double.doubleToLongBits(montDeMarsan.longitude))), prefs.getString("reference_kind", "city") ?: "city")
private fun saveReference(prefs: android.content.SharedPreferences, place: ReferencePlace) { prefs.edit().putString("reference_label", place.label).putLong("reference_lat", java.lang.Double.doubleToRawLongBits(place.latitude)).putLong("reference_lon", java.lang.Double.doubleToRawLongBits(place.longitude)).putString("reference_kind", place.kind).apply(); if (place.kind != "gps") { val entries = (listOf(place) + loadRecentPlaces(prefs)).distinctBy { "${it.latitude},${it.longitude}" }.take(3); prefs.edit().putString("recent_places", ReferencePlaceCodec.encode(entries)).apply() } }
private fun loadRecentPlaces(prefs: android.content.SharedPreferences): List<ReferencePlace> = ReferencePlaceCodec.decode(prefs.getString("recent_places", "").orEmpty())
private fun lastListLocation(context: Context): Pair<Double, Double>? { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null; val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager; return manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude } }
