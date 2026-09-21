package com.nowadays.events.presentation.detail
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nowadays.events.domain.model.Event
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EventDetailScreen(onBack: () -> Unit, onShowMap: (Event) -> Unit, viewModel: EventDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Détail de l’événement") }, navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("detail-back")) { Icon(Icons.Default.ArrowBack, "Retour à la liste") } }) }) { padding ->
        when { !state.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.event == null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Cet événement n’est plus disponible.", modifier = Modifier.testTag("event-not-found")); TextButton(onClick = onBack) { Text("Retour à la liste") } }
            else -> EventDetailContent(state.event!!, attendance = state.attendance, onAttendanceChanged = viewModel::setAttendance, onDelete = { viewModel.delete(); onBack() }, onShowMap = { onShowMap(state.event!!) }, modifier = Modifier.padding(padding).padding(horizontal = 18.dp)) }
    }
}
