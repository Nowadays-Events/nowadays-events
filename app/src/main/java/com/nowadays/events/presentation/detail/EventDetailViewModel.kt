package com.nowadays.events.presentation.detail
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowadays.events.domain.model.*
import com.nowadays.events.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
data class EventDetailUiState(val event: Event? = null, val attendance: AttendanceResponse = AttendanceResponse.NONE, val loaded: Boolean = false)
@HiltViewModel class EventDetailViewModel @Inject constructor(private val repository: EventRepository, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val eventId: String = requireNotNull(savedStateHandle["eventId"])
    val uiState = combine(repository.observeEvent(eventId), repository.observeAttendance(eventId)) { event, attendance -> EventDetailUiState(event, attendance, true) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())
    fun setAttendance(value: AttendanceResponse) { viewModelScope.launch { repository.setAttendance(eventId, value) } }
    fun delete() { val event = uiState.value.event ?: return; if (event.origin == DataOrigin.MANUAL) viewModelScope.launch { repository.delete(event.id) } }
}
