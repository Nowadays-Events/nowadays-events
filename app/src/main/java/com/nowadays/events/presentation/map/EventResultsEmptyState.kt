package com.nowadays.events.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun EventResultsEmptyState(hiddenCount: Int, onShowHidden: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (hiddenCount > 0) "Tous les résultats de ce filtre sont masqués sur cet appareil."
             else "Aucun événement dans ce rayon pour cette période.", Modifier.testTag("event-list-empty"))
        if (hiddenCount > 0) TextButton(onShowHidden, Modifier.heightIn(min = 48.dp).testTag("empty-show-hidden")) {
            Text("Voir les $hiddenCount événements masqués")
        }
    }
}
