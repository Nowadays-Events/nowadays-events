package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.TimeFilter
import java.time.LocalDate
import java.time.Instant

data class MapUiState(
    val selectedFilter: TimeFilter = TimeFilter.TODAY,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val dataUpdatedAt: Instant? = null,
    val searchQuery: String = "",
    val selectedCategory: com.nowadays.events.domain.model.EventCategory? = null,
    val priceFilter: EventPriceFilter = EventPriceFilter.ALL,
    val events: List<Event> = emptyList(),
    val selectedEvent: Event? = null,
    val relatedEvents: List<Event> = emptyList(),
    val selectedIsMainEvent: Boolean = false,
    val mainEventIds: Set<String> = emptySet(),
    val isExploringGroup: Boolean = false,
    val expandedMainEvent: Event? = null,
    val childEventIds: Set<String> = emptySet(),
    val childCounts: Map<String, Int> = emptyMap(),
    val expandedClusterEventIds: Set<String> = emptySet(),
    val selectedFamilyEventIds: List<String> = emptyList(),
    val selectedSourceUrls: List<String> = emptyList(),
    val attendanceResponse: AttendanceResponse = AttendanceResponse.NONE,
    val isLoading: Boolean = true,
)

enum class EventPriceFilter { ALL, FREE, PAID }
