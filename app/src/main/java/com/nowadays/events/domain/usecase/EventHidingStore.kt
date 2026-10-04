package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface HiddenEventStorage {
    fun read(): Set<String>
    fun write(keys: Set<String>)
}

data class EventVisibility(val visible: List<Event>, val hidden: List<Event>) {
    val allResultsHidden: Boolean get() = visible.isEmpty() && hidden.isNotEmpty()
}

/** Id-only identity: never merge preferences by title, place, URL or occurrence date. */
object EventHidingPolicy {
    fun key(event: Event): String {
        require(event.id.isNotBlank()) { "An event needs a persistent id" }
        return "id:${event.id}"
    }

    fun partition(events: List<Event>, hiddenKeys: Set<String>): EventVisibility =
        EventVisibility(events.filterNot { key(it) in hiddenKeys }, events.filter { key(it) in hiddenKeys })
}

/** Preferences are deliberately retained when a remote event temporarily disappears. */
class EventHidingStore(private val storage: HiddenEventStorage) {
    private val keys = MutableStateFlow(storage.read().toSet())
    val hiddenKeys: StateFlow<Set<String>> = keys.asStateFlow()

    @Synchronized fun hide(event: Event) = update(keys.value + EventHidingPolicy.key(event))
    @Synchronized fun reveal(event: Event) = update(keys.value - EventHidingPolicy.key(event))
    @Synchronized fun revealAll() = update(emptySet())

    private fun update(value: Set<String>) {
        storage.write(value.toSet())
        keys.value = value.toSet()
    }
}
