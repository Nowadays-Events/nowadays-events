package com.nowadays.events.presentation.settings

import androidx.lifecycle.ViewModel
import com.nowadays.events.data.local.TastePreferences
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TasteLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TasteSettingsViewModel @Inject constructor(private val tastePreferences: TastePreferences) : ViewModel() {
    val preferences = tastePreferences.store.preferences
    fun set(category: EventCategory, level: TasteLevel) = tastePreferences.store.set(category, level)
    fun reset() = tastePreferences.store.reset()
}
