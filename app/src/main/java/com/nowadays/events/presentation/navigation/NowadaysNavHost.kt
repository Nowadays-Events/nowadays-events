package com.nowadays.events.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nowadays.events.presentation.map.MapScreen
import com.nowadays.events.presentation.form.EventFormScreen

private const val MAP_ROUTE = "map"
private const val FORM_ROUTE = "event-form"

@Composable
fun NowadaysNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MAP_ROUTE) {
        composable(MAP_ROUTE) { entry ->
            val latitude by entry.savedStateHandle.getStateFlow<Double?>("focus_lat", null).collectAsState()
            val longitude by entry.savedStateHandle.getStateFlow<Double?>("focus_lon", null).collectAsState()
            MapScreen(
                onAddEvent = { navController.navigate(FORM_ROUTE) },
                focusLatitude = latitude,
                focusLongitude = longitude,
                onFocusHandled = { entry.savedStateHandle["focus_lat"] = null; entry.savedStateHandle["focus_lon"] = null },
            )
        }
        composable(FORM_ROUTE) {
            EventFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { latitude, longitude ->
                    navController.getBackStackEntry(MAP_ROUTE).savedStateHandle["focus_lat"] = latitude
                    navController.getBackStackEntry(MAP_ROUTE).savedStateHandle["focus_lon"] = longitude
                    navController.popBackStack()
                },
            )
        }
    }
}
