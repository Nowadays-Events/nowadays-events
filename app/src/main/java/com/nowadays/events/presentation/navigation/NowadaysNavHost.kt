package com.nowadays.events.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri
import com.nowadays.events.presentation.map.MapScreen
import com.nowadays.events.presentation.map.EventListScreen
import com.nowadays.events.presentation.form.EventFormScreen
import com.nowadays.events.presentation.detail.EventDetailScreen

private const val MAP_ROUTE = "map"
private const val LIST_ROUTE = "list"
private const val FORM_ROUTE = "event-form"
private const val DETAIL_ROUTE = "event/{eventId}"

@Composable
fun NowadaysNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LIST_ROUTE) {
        composable(LIST_ROUTE) {
            EventListScreen(
                onShowMap = { navController.navigate(MAP_ROUTE) },
                onOpenEvent = { event -> navController.navigate(detailRoute(event.id)) },
            )
        }
        composable(
            route = "$MAP_ROUTE?lat={lat}&lon={lon}&eventId={eventId}&openDetail={openDetail}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lon") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("openDetail") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val latitude by entry.savedStateHandle.getStateFlow<Double?>("focus_lat", null).collectAsState()
            val longitude by entry.savedStateHandle.getStateFlow<Double?>("focus_lon", null).collectAsState()
            val routeLatitude = entry.arguments?.getString("lat")?.toDoubleOrNull()
            val routeLongitude = entry.arguments?.getString("lon")?.toDoubleOrNull()
            val eventId = entry.arguments?.getString("eventId")?.let(Uri::decode)
            MapScreen(
                onAddEvent = { navController.navigate(FORM_ROUTE) },
                onBackToList = { navController.popBackStack() },
                focusLatitude = latitude ?: routeLatitude,
                focusLongitude = longitude ?: routeLongitude,
                focusEventId = eventId,
                openFocusedEventDetail = entry.arguments?.getBoolean("openDetail") ?: false,
                onFocusHandled = { entry.savedStateHandle["focus_lat"] = null; entry.savedStateHandle["focus_lon"] = null },
            )
        }
        composable(
            route = DETAIL_ROUTE,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) {
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onShowMap = { event -> navController.navigate(mapRoute(MapFocusRequest(event.id, event.latitude, event.longitude))) },
            )
        }
        composable(FORM_ROUTE) {
            EventFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { latitude, longitude ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("focus_lat", latitude)
                    navController.previousBackStackEntry?.savedStateHandle?.set("focus_lon", longitude)
                    navController.popBackStack()
                },
            )
        }
    }
}
