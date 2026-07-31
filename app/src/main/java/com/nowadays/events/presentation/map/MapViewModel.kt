package com.nowadays.events.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowadays.events.domain.model.TimeFilter
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.repository.EventRepository
import com.nowadays.events.domain.usecase.EventTimeFilters
import com.nowadays.events.domain.usecase.EventFamilyGrouper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel @Inject constructor(
    private val repository: EventRepository,
    private val filters: EventTimeFilters,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(TimeFilter.TODAY)
    private val selectedEventId = MutableStateFlow<String?>(null)
    private val expandedMainEventId = MutableStateFlow<String?>(null)
    private val customDateRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val expandedClusterEventIds = MutableStateFlow<Set<String>>(emptySet())
    private data class FilterSelection(
        val filter: TimeFilter,
        val range: Pair<LocalDate, LocalDate>?,
        val expandedClusterIds: Set<String>,
    )
    private val filterSelection = combine(selectedFilter, customDateRange, expandedClusterEventIds) { filter, range, clusterIds ->
        FilterSelection(filter, range, clusterIds)
    }

    private val attendance = selectedEventId.flatMapLatest { eventId ->
        eventId?.let(repository::observeAttendance) ?: flowOf(AttendanceResponse.NONE)
    }

    val uiState = combine(repository.observeEvents(), filterSelection, selectedEventId, attendance, expandedMainEventId) { events, filterSelection, selectedId, response, expandedMainId ->
        val filter = filterSelection.filter
        val customRange = filterSelection.range
        val filtered = if (filter == TimeFilter.CUSTOM && customRange != null) {
            filters.apply(events, customRange.first, customRange.second)
        } else filters.apply(events, filter)
        val families = EventFamilyGrouper.group(filtered)
        val expandedFamily = families.firstOrNull { it.main.id == expandedMainId }
        val visible = expandedFamily?.events ?: families.map { it.main }
        val selected = events.firstOrNull { it.id == selectedId }
        val selectedFamily = selected?.let { event -> families.firstOrNull { family -> family.events.any { it.id == event.id } } }
        val related = selectedFamily?.events.orEmpty()
        MapUiState(
            selectedFilter = filter,
            customStartDate = customRange?.first,
            customEndDate = customRange?.second,
            events = visible,
            selectedEvent = selected,
            relatedEvents = related,
            selectedIsMainEvent = selected != null && selectedFamily?.main?.id == selected.id && selectedFamily.children.isNotEmpty(),
            mainEventIds = families.filter { it.children.isNotEmpty() }.map { it.main.id }.toSet(),
            isExploringGroup = expandedFamily != null,
            expandedMainEvent = expandedFamily?.main,
            childEventIds = expandedFamily?.children?.map(Event::id)?.toSet().orEmpty(),
            childCounts = families.associate { it.main.id to it.children.size },
            expandedClusterEventIds = filterSelection.expandedClusterIds.intersect(visible.map(Event::id).toSet()),
            selectedFamilyEventIds = selectedFamily?.events?.map(Event::id).orEmpty(),
            selectedSourceUrls = selectedFamily?.sourceUrls.orEmpty(),
            attendanceResponse = response,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun selectFilter(filter: TimeFilter) {
        selectedFilter.value = filter
        selectedEventId.value = null
        expandedClusterEventIds.value = emptySet()
    }

    fun selectCustomRange(start: LocalDate, endInclusive: LocalDate) {
        customDateRange.value = start to endInclusive
        selectedFilter.value = TimeFilter.CUSTOM
        expandedMainEventId.value = null
        selectedEventId.value = null
        expandedClusterEventIds.value = emptySet()
    }

    fun expandCluster(eventIds: Set<String>) { expandedClusterEventIds.value = eventIds }
    fun clearMapSelection() {
        selectedEventId.value = null
        expandedClusterEventIds.value = emptySet()
    }

    fun selectEvent(id: String) {
        selectedEventId.value = id
    }
    fun expandSelectedSource() { uiState.value.selectedEvent?.id?.let { expandedMainEventId.value = it } }
    fun clearSelection() { selectedEventId.value = null }
    fun clearDetailOnly() { selectedEventId.value = null }
    fun collapseRelatedEvents() { expandedMainEventId.value = null; selectedEventId.value = null }
    fun deleteSelectedEvent() {
        val event = uiState.value.selectedEvent ?: return
        if (event.origin != com.nowadays.events.domain.model.DataOrigin.MANUAL) return
        viewModelScope.launch {
            val state = uiState.value
            if (state.selectedIsMainEvent) repository.deleteAll(state.selectedFamilyEventIds)
            else repository.delete(event.id)
            expandedMainEventId.value = null
            selectedEventId.value = null
        }
    }
    fun setAttendance(response: AttendanceResponse) {
        val eventId = selectedEventId.value ?: return
        viewModelScope.launch { repository.setAttendance(eventId, response) }
    }
}
