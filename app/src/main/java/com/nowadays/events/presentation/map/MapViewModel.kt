package com.nowadays.events.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nowadays.events.domain.model.TimeFilter
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.repository.EventRepository
import com.nowadays.events.domain.usecase.EventTimeFilters
import com.nowadays.events.domain.usecase.EventFamilyGrouper
import com.nowadays.events.map.MapSelectionPolicy
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(
        savedStateHandle.get<String>("selected_filter")
            ?.let { runCatching { TimeFilter.valueOf(it) }.getOrNull() }
            ?: TimeFilter.TODAY,
    )
    private val selectedEventId = MutableStateFlow<String?>(null)
    private val expandedMainEventId = MutableStateFlow<String?>(null)
    private val customDateRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val expandedClusterEventIds = MutableStateFlow<Set<String>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<EventCategory?>(null)
    private val priceFilter = MutableStateFlow(EventPriceFilter.ALL)
    private data class FilterSelection(
        val filter: TimeFilter,
        val range: Pair<LocalDate, LocalDate>?,
        val expandedClusterIds: Set<String>,
    )
    private val filterSelection = combine(selectedFilter, customDateRange, expandedClusterEventIds) { filter, range, clusterIds ->
        FilterSelection(filter, range, clusterIds)
    }
    private data class ContentFilters(
        val query: String,
        val category: EventCategory?,
        val price: EventPriceFilter,
    )
    private val contentFilters = combine(searchQuery, selectedCategory, priceFilter, ::ContentFilters)

    private val attendance = selectedEventId.flatMapLatest { eventId ->
        eventId?.let(repository::observeAttendance) ?: flowOf(AttendanceResponse.NONE)
    }

    private data class Selection(val eventId: String?, val attendance: AttendanceResponse, val expandedMainId: String?)
    private val selection = combine(selectedEventId, attendance, expandedMainEventId, ::Selection)

    val uiState = combine(repository.observeEvents(), filterSelection, contentFilters, selection) { events, filterSelection, content, selection ->
        val filter = filterSelection.filter
        val customRange = filterSelection.range
        val dateFiltered = if (filter == TimeFilter.CUSTOM && customRange != null) {
            filters.apply(events, customRange.first, customRange.second)
        } else filters.apply(events, filter)
        val query = content.query.trim().lowercase()
        val filtered = dateFiltered.filter { event ->
            (content.category == null || event.category == content.category) &&
                when (content.price) {
                    EventPriceFilter.ALL -> true
                    EventPriceFilter.FREE -> event.price is EventPrice.Free
                    EventPriceFilter.PAID -> event.price is EventPrice.Paid
                } &&
                (query.isEmpty() || listOfNotNull(
                    event.title, event.shortDescription, event.fullDescription,
                    event.venueName, event.address, event.organizer,
                ).any { it.lowercase().contains(query) })
        }
        val families = EventFamilyGrouper.group(filtered)
        val expandedFamily = families.firstOrNull { it.main.id == selection.expandedMainId }
        val visible = expandedFamily?.events ?: families.map { it.main }
        val visibleSelectionIds = filtered.map(Event::id).toSet()
        val validSelectedId = MapSelectionPolicy.retainIfVisible(selection.eventId, visibleSelectionIds)
        val selected = filtered.firstOrNull { it.id == validSelectedId }
        val selectedFamily = selected?.let { event -> families.firstOrNull { family -> family.events.any { it.id == event.id } } }
        val related = selectedFamily?.events.orEmpty()
        MapUiState(
            selectedFilter = filter,
            customStartDate = customRange?.first,
            customEndDate = customRange?.second,
            dataUpdatedAt = events.maxOfOrNull(Event::updatedAt),
            searchQuery = content.query,
            selectedCategory = content.category,
            priceFilter = content.price,
            events = visible,
            selectedEvent = selected,
            relatedEvents = related,
            selectedIsMainEvent = selected != null && selectedFamily?.main?.id == selected.id && selectedFamily.children.isNotEmpty(),
            mainEventIds = families.filter { it.children.isNotEmpty() }.map { it.main.id }.toSet(),
            isExploringGroup = expandedFamily != null,
            expandedMainEvent = expandedFamily?.main,
            childEventIds = expandedFamily?.children?.map(Event::id)?.toSet().orEmpty(),
            childCounts = families.associate { it.main.id to it.children.size },
            expandedClusterEventIds = MapSelectionPolicy.expandedClusterIds(
                filterSelection.expandedClusterIds, visible.map(Event::id).toSet(),
            ),
            selectedFamilyEventIds = selectedFamily?.events?.map(Event::id).orEmpty(),
            selectedSourceUrls = selectedFamily?.sourceUrls.orEmpty(),
            attendanceResponse = selection.attendance,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun selectFilter(filter: TimeFilter) {
        selectedFilter.value = filter
        savedStateHandle["selected_filter"] = filter.name
        expandedMainEventId.value = null
        selectedEventId.value = null
        expandedClusterEventIds.value = emptySet()
    }

    fun selectCustomRange(start: LocalDate, endInclusive: LocalDate) {
        customDateRange.value = start to endInclusive
        selectedFilter.value = TimeFilter.CUSTOM
        savedStateHandle["selected_filter"] = TimeFilter.CUSTOM.name
        expandedMainEventId.value = null
        selectedEventId.value = null
        expandedClusterEventIds.value = emptySet()
    }

    fun expandCluster(eventIds: Set<String>) { expandedClusterEventIds.value = eventIds }
    fun setSearchQuery(value: String) { searchQuery.value = value }
    fun selectCategory(category: EventCategory?) { selectedCategory.value = category }
    fun selectPriceFilter(filter: EventPriceFilter) { priceFilter.value = filter }
    fun clearContentFilters() {
        searchQuery.value = ""
        selectedCategory.value = null
        priceFilter.value = EventPriceFilter.ALL
    }
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
