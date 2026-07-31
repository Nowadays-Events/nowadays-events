package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.AttendanceResponse
import com.nowadays.events.domain.model.TimeFilter
import java.time.LocalDate

data class MapUiState(
    val selectedFilter: TimeFilter = TimeFilter.TODAY,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
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
