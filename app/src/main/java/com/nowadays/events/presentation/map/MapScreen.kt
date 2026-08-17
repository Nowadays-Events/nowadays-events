package com.nowadays.events.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.R
import com.nowadays.events.domain.model.TimeFilter
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.map.EventMap
import com.nowadays.events.map.EventMapController
import com.nowadays.events.presentation.detail.EventDetailSheet
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onAddEvent: () -> Unit,
    focusLatitude: Double? = null,
    focusLongitude: Double? = null,
    onFocusHandled: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cameraPreferences = remember { context.getSharedPreferences("map_camera", Context.MODE_PRIVATE) }
    val controller = remember {
        val hasSavedCamera = cameraPreferences.contains("latitude")
        EventMapController(
            onEventSelected = viewModel::selectEvent,
            onClusterExpanded = viewModel::expandCluster,
            onBackgroundClick = viewModel::clearMapSelection,
            initialCenter = if (hasSavedCamera) LatLng(
                cameraPreferences.getFloat("latitude", 48.8566f).toDouble(),
                cameraPreferences.getFloat("longitude", 2.3522f).toDouble(),
            ) else LatLng(48.8566, 2.3522),
            initialZoom = cameraPreferences.getFloat("zoom", 11.5f).toDouble(),
            onCameraChanged = { target, zoom -> cameraPreferences.edit()
                .putFloat("latitude", target.latitude.toFloat()).putFloat("longitude", target.longitude.toFloat())
                .putFloat("zoom", zoom.toFloat()).apply() },
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) recenterOnLastKnownLocation(context, controller)
    }
    LaunchedEffect(focusLatitude, focusLongitude) {
        if (focusLatitude != null && focusLongitude != null) {
            viewModel.selectFilter(TimeFilter.ALL_FUTURE)
            controller.recenter(LatLng(focusLatitude, focusLongitude), 14.0)
            onFocusHandled()
        }
    }
    LaunchedEffect(state.selectedEvent?.id, state.selectedIsMainEvent) {
        if (state.selectedIsMainEvent) {
            viewModel.expandSelectedSource()
        }
    }
    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) recenterOnLastKnownLocation(context, controller)
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recentrer la carte")
                }
                FloatingActionButton(onClick = onAddEvent, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un événement")
                }
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            EventMap(
                events = state.events,
                mainEventIds = state.mainEventIds,
                childEventIds = state.childEventIds,
                childCounts = state.childCounts,
                expandedMainEvent = state.expandedMainEvent,
                expandedClusterEventIds = state.expandedClusterEventIds,
                onEventSelected = viewModel::selectEvent,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 6.dp),
            ) {
                val mapTextShadow = Shadow(color = MaterialTheme.colorScheme.surface, offset = Offset(1f, 1f), blurRadius = 5f)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall.copy(shadow = mapTextShadow),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.dataUpdatedAt?.let { updatedAt ->
                    Text(
                        "Actualisé le ${updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM à HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall.copy(shadow = mapTextShadow),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            FilterBar(
                selected = state.selectedFilter,
                customStartDate = state.customStartDate,
                customEndDate = state.customEndDate,
                onSelected = viewModel::selectFilter,
                onCalendarClick = { showCalendar = true },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp),
            )
            IconButton(
                onClick = { showSearch = !showSearch },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 8.dp),
            ) {
                Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = "Rechercher et filtrer")
            }
            if (showSearch) {
                SearchFilters(
                    query = state.searchQuery,
                    selectedCategory = state.selectedCategory,
                    priceFilter = state.priceFilter,
                    onQueryChanged = viewModel::setSearchQuery,
                    onCategorySelected = viewModel::selectCategory,
                    onPriceSelected = viewModel::selectPriceFilter,
                    onClear = viewModel::clearContentFilters,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 12.dp, end = 12.dp),
                )
            }
            if (state.isExploringGroup) {
                val transition = rememberInfiniteTransition(label = "group back pulse")
                val arrowAlpha by transition.animateFloat(
                    initialValue = 0.68f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "group back alpha",
                )
                SmallFloatingActionButton(
                    onClick = viewModel::collapseRelatedEvents,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 154.dp).alpha(arrowAlpha),
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour aux événements principaux") }
            }
            state.selectedEvent?.let { event -> EventDetailSheet(
                event = event,
                relatedEventCount = if (state.selectedIsMainEvent) state.relatedEvents.size - 1 else 0,
                deleteEventCount = if (state.selectedIsMainEvent) state.selectedFamilyEventIds.size else 1,
                sourceUrls = state.selectedSourceUrls,
                attendance = state.attendanceResponse,
                onAttendanceChanged = viewModel::setAttendance,
                onDelete = viewModel::deleteSelectedEvent,
                onDismiss = viewModel::clearSelection,
            ) }
            if (showCalendar) {
                DateFilterDialog(
                    initialStart = state.customStartDate,
                    initialEnd = state.customEndDate,
                    onDismiss = { showCalendar = false },
                    onConfirm = { start, end ->
                        viewModel.selectCustomRange(start, end)
                        showCalendar = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchFilters(
    query: String,
    selectedCategory: EventCategory?,
    priceFilter: EventPriceFilter,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (EventCategory?) -> Unit,
    onPriceSelected: (EventPriceFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 5.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Événement, lieu, ville…") },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Default.Close, contentDescription = "Effacer la recherche") }
                }} else null,
            )
            LazyRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                item { AssistChip(onClick = { onCategorySelected(null) }, label = { Text(if (selectedCategory == null) "✓ Toutes" else "Toutes") }) }
                items(EventCategory.entries) { category ->
                    AssistChip(
                        onClick = { onCategorySelected(category) },
                        label = { Text(if (selectedCategory == category) "✓ ${categoryLabel(category)}" else categoryLabel(category)) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            LazyRow(Modifier.fillMaxWidth()) {
                items(listOf(
                    EventPriceFilter.ALL to "Tous tarifs",
                    EventPriceFilter.FREE to "Gratuit",
                    EventPriceFilter.PAID to "Payant",
                )) { (filter, label) ->
                    AssistChip(
                        onClick = { onPriceSelected(filter) },
                        label = { Text(if (priceFilter == filter) "✓ $label" else label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                item { TextButton(onClick = onClear) { Text("Réinitialiser") } }
            }
        }
    }
}

private fun categoryLabel(category: EventCategory): String = when (category) {
    EventCategory.CULTURE -> "Culture"
    EventCategory.MUSIC -> "Musique"
    EventCategory.SPORT -> "Sport"
    EventCategory.FOOD -> "Gastronomie"
    EventCategory.FAMILY -> "Famille"
    EventCategory.COMMUNITY -> "Vie locale"
    EventCategory.TECHNOLOGY -> "Technologie"
}

private fun recenterOnLastKnownLocation(context: Context, controller: EventMapController) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = manager.getProviders(true).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
    if (location != null) controller.recenter(LatLng(location.latitude, location.longitude), 14.0)
    else controller.recenter()
}

@Composable
private fun FilterBar(
    selected: TimeFilter,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    onSelected: (TimeFilter) -> Unit,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        TimeFilter.TODAY to "Aujourd’hui",
        TimeFilter.TOMORROW to "Demain",
        TimeFilter.NEXT_7_DAYS to "7 prochains jours",
        TimeFilter.THIS_WEEKEND to "Ce week-end",
        TimeFilter.ALL_FUTURE to "Toutes les dates",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        LazyRow(Modifier.fillMaxWidth().wrapContentSize().padding(8.dp)) {
            items(labels) { (filter, label) ->
                val isSelected = selected == filter
                AssistChip(
                    onClick = { onSelected(filter) },
                    label = { Text(if (isSelected) "✓ $label" else label) },
                    colors = periodChipColors(isSelected),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                val formatter = DateTimeFormatter.ofPattern("dd/MM")
                val isSelected = selected == TimeFilter.CUSTOM
                val customLabel = if (isSelected && customStartDate != null && customEndDate != null) {
                    if (customStartDate == customEndDate) customStartDate.format(formatter)
                    else "${customStartDate.format(formatter)} – ${customEndDate.format(formatter)}"
                } else "Dates"
                AssistChip(
                    onClick = onCalendarClick,
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text(if (isSelected) "✓ $customLabel" else customLabel) },
                    colors = periodChipColors(isSelected),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun periodChipColors(selected: Boolean) = if (selected) {
    AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
} else {
    AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toPickerDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toPickerMillis(),
        initialSelectedEndDateMillis = initialEnd?.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis?.toPickerDate() ?: return@TextButton
                    val end = pickerState.selectedEndDateMillis?.toPickerDate() ?: start
                    onConfirm(start, end)
                },
            ) { Text("Afficher") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    ) {
        DateRangePicker(
            state = pickerState,
            title = { Text("Choisir un jour ou une période", Modifier.padding(16.dp)) },
            showModeToggle = false,
        )
    }
}
