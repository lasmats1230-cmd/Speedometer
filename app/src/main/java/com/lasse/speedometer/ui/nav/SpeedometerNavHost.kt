package com.lasse.speedometer.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.ui.ImmersiveMode
import com.lasse.speedometer.ui.history.HistoryScreen
import com.lasse.speedometer.ui.history.TourDetailScreen
import com.lasse.speedometer.ui.history.TripDetailScreen
import com.lasse.speedometer.ui.live.LiveScreen
import com.lasse.speedometer.ui.settings.LayoutSettingsScreen
import com.lasse.speedometer.ui.settings.LicensesScreen
import com.lasse.speedometer.ui.settings.SettingsScreen
import com.lasse.speedometer.ui.stats.StatsScreen
import com.lasse.speedometer.ui.tools.ToolsScreen

@Composable
fun SpeedometerNavHost(settings: AppSettings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val immersive by ImmersiveMode.enabled.collectAsState()
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route }.toSet() }
    val showBottomBar = currentRoute in topLevelRoutes && !immersive

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                // Immersive screens draw edge to edge; everything else keeps
                // clear of the bars the scaffold reports.
                .padding(if (immersive) PaddingValues(0.dp) else innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.LIVE,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Routes.LIVE) {
                    LiveScreen(settings = settings, snackbarHostState = snackbarHostState)
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                        onOpenTrip = { navController.navigate(Routes.tripDetail(it)) },
                        onOpenTour = { navController.navigate(Routes.tourDetail(it)) },
                    )
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        settings = settings,
                        onOpenTrip = { navController.navigate(Routes.tripDetail(it)) },
                    )
                }
                composable(Routes.TOOLS) {
                    ToolsScreen(settings = settings, snackbarHostState = snackbarHostState)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                        onOpenLayout = { navController.navigate(Routes.LAYOUT_SETTINGS) },
                        onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                    )
                }
                composable(
                    route = Routes.LAYOUT_SETTINGS,
                    enterTransition = {
                        slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(260))
                    },
                ) {
                    LayoutSettingsScreen(
                        settings = settings,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.LICENSES,
                    enterTransition = {
                        slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(260))
                    },
                ) {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.TRIP_DETAIL,
                    arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
                    enterTransition = {
                        slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(260))
                    },
                ) { entry ->
                    TripDetailScreen(
                        tripId = entry.arguments?.getLong("tripId") ?: 0L,
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.TOUR_DETAIL,
                    arguments = listOf(navArgument("tourId") { type = NavType.LongType }),
                    enterTransition = {
                        slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(260))
                    },
                ) { entry ->
                    TourDetailScreen(
                        tourId = entry.arguments?.getLong("tourId") ?: 0L,
                        settings = settings,
                        onBack = { navController.popBackStack() },
                        onOpenTrip = { navController.navigate(Routes.tripDetail(it)) },
                    )
                }
            }
        }
    }
}
