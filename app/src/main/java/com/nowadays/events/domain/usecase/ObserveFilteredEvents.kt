package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.TimeFilter
import com.nowadays.events.domain.repository.EventRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveFilteredEvents @Inject constructor(
    private val repository: EventRepository,
    private val filters: EventTimeFilters,
) {
    operator fun invoke(filter: TimeFilter, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<Event>> =
        repository.observeEvents().map { filters.apply(it, filter, zoneId) }
}

