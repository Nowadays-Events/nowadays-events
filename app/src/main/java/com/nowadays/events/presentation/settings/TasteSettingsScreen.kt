package com.nowadays.events.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.usecase.TasteLevel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteSettingsScreen(onBack: () -> Unit, viewModel: TasteSettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsState()
    TasteSettingsContent(preferences, viewModel::set, viewModel::reset, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasteSettingsContent(
    preferences: Map<EventCategory, TasteLevel>,
    onChange: (EventCategory, TasteLevel) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Paramètres") },
            navigationIcon = { IconButton(onBack, Modifier.testTag("settings-back")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            } },
        ) },
        bottomBar = {
            Surface(Modifier.navigationBarsPadding(), shadowElevation = 4.dp) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp).testTag("validate-tastes"),
                ) { Text("Valider") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("taste-settings"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mes goûts", style = MaterialTheme.typography.titleLarge)
                    Text("Choisissez les types de sorties qui vous intéressent. Ces préférences serviront prochainement à personnaliser vos suggestions.")
                }
            }
            items(EventCategory.entries, key = EventCategory::name) { category ->
                val level = preferences[category] ?: TasteLevel.UNSPECIFIED
                val title = tasteCategoryLabel(category)
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("taste-${category.name}")) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(tasteLevelLabel(level), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("taste-level-${category.name}"))
                    Slider(
                        value = level.ordinal.toFloat(),
                        onValueChange = { onChange(category, TasteLevel.entries[it.roundToInt().coerceIn(0, 3)]) },
                        valueRange = 0f..3f,
                        steps = 2,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { contentDescription = title; stateDescription = tasteLevelLabel(level) }
                            .testTag("taste-slider-${category.name}"),
                    )
                }
            }
            item { TextButton({ confirmReset = true }, Modifier.padding(12.dp).heightIn(min = 48.dp).testTag("reset-tastes")) {
                Text("Réinitialiser mes goûts")
            } }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Réinitialiser mes goûts ?") },
        text = { Text("Les sept préférences reviendront à « Sans avis ».") },
        confirmButton = { TextButton({ onReset(); confirmReset = false }, Modifier.testTag("confirm-reset-tastes")) { Text("Réinitialiser") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Annuler") } },
    )
}
