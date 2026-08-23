package com.lasse.speedometer.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.lasse.speedometer.R

object Routes {
    const val LIVE = "live"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val TRIP_DETAIL = "trip/{tripId}"
    const val TOUR_DETAIL = "tour/{tourId}"
    const val ROUTE_DETAIL = "route/{routeId}"
    const val LAYOUT_SETTINGS = "settings/layout"
    const val LICENSES = "settings/licenses"

    fun tripDetail(id: Long) = "trip/$id"
    fun tourDetail(id: Long) = "tour/$id"
    fun routeDetail(id: Long) = "route/$id"
}

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    LIVE(Routes.LIVE, R.string.nav_live, Icons.Filled.Speed, Icons.Outlined.Speed),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    STATS(Routes.STATS, R.string.nav_stats, Icons.Filled.Insights, Icons.Outlined.Insights),
    TOOLS(Routes.TOOLS, R.string.nav_tools, Icons.Filled.Explore, Icons.Outlined.Explore),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
