package com.lasse.speedometer.ui.tools

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.io.RouteParser
import com.lasse.speedometer.data.io.RoutePoint
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.ui.components.ChartSample
import com.lasse.speedometer.ui.components.DetailScaffold
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.ProfileChart
import com.lasse.speedometer.ui.components.SectionCard
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.ui.history.ConfirmDialog
import com.lasse.speedometer.ui.history.TextFieldDialog
import com.lasse.speedometer.ui.live.ActiveRoute
import com.lasse.speedometer.util.Formatters
import com.lasse.speedometer.util.GeoMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RouteDetailViewModel(
    application: Application,
    private val routeId: Long,
) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository

    val route: StateFlow<RouteEntity?> = repository.observeRoute(routeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The route decoded once here rather than on every recomposition: a long
     * GPX is tens of thousands of points and the screen redraws on every
     * scroll.
     */
    val points: StateFlow<List<RoutePoint>> = route
        .map { current -> current?.let { RouteParser.decode(it.encodedPoints) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) = viewModelScope.launch { repository.renameRoute(routeId, name) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        // A route being followed cannot be left selected once it is gone.
        if (ActiveRoute.routeId.value == routeId) ActiveRoute.clear()
        repository.deleteRoute(routeId)
        onDone()
    }

    class Factory(
        private val application: Application,
        private val routeId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RouteDetailViewModel(application, routeId) as T
    }
}

/**
 * One imported route, before committing to riding it.
 *
 * The list can only say how long a route is and how much it climbs, which
 * does not answer the question actually being asked — where does it go, and
 * is the climbing one wall or spread over the whole thing. The map and the
 * profile answer both, and the button underneath starts following it.
 */
@Composable
fun RouteDetailScreen(
    routeId: Long,
    settings: AppSettings,
    onBack: () -> Unit,
    onFollow: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: RouteDetailViewModel = viewModel(
        factory = RouteDetailViewModel.Factory(context.app, routeId),
        key = "route-$routeId",
    )
    val route by viewModel.route.collectAsState()
    val points by viewModel.points.collectAsState()
    val activeRouteId by ActiveRoute.routeId.collectAsState()
    val following = activeRouteId == routeId

    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DetailScaffold(
        title = route?.name.orEmpty(),
        onBack = onBack,
        actions = {
            OverflowMenu(
                actions = listOf(
                    MenuAction(stringResource(R.string.rename_route)) { renaming = true },
                    MenuAction(
                        label = stringResource(R.string.delete),
                        destructive = true,
                        onClick = { confirmDelete = true },
                    ),
                ),
                contentDescription = stringResource(R.string.more),
            )
        },
    ) { padding ->
        val current = route ?: return@DetailScaffold Box(Modifier.fillMaxSize())

        val line = remember(points) {
            points.map { LatLng(it.latitude, it.longitude) }
        }
        val profile = remember(points) { elevationProfile(points) }
        val highest = remember(points) { points.mapNotNull { it.altitudeM }.maxOrNull() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.Screen,
                end = Dimens.Screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.BottomGap,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.Item),
        ) {
            if (line.size >= 2) {
                item("map") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        TrackMap(
                            modifier = Modifier.fillMaxSize(),
                            route = line,
                            fitTrack = true,
                            followPosition = false,
                        )
                    }
                }
            }

            item("follow") {
                Button(
                    onClick = {
                        ActiveRoute.toggle(routeId)
                        if (!following) onFollow()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (following) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Icon(
                        imageVector = if (following) {
                            Icons.Filled.Close
                        } else {
                            Icons.Filled.Navigation
                        },
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            if (following) R.string.stop_following else R.string.follow_route
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            item("totals") {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
                ) {
                    StatTile(
                        label = stringResource(R.string.stat_distance),
                        value = Formatters.distance(current.distanceM, settings.units),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    StatTile(
                        label = stringResource(R.string.ascent),
                        value = Formatters.elevation(current.ascentM, settings.units),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    StatTile(
                        label = stringResource(R.string.altitude),
                        value = highest
                            ?.let { Formatters.elevation(it, settings.units) }
                            ?: PLACEHOLDER,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            if (profile.size >= 2) {
                item("profile") {
                    SectionCard(
                        title = stringResource(R.string.elevation_profile),
                        subtitle = stringResource(R.string.axis_distance_altitude),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        val lowest = stringResource(R.string.lowest, "%s")
                        val peak = stringResource(R.string.highest, "%s")
                        ProfileChart(
                            samples = profile,
                            formatX = { Formatters.distance(it.toDouble(), settings.units) },
                            formatY = { Formatters.elevation(it.toDouble(), settings.units) },
                            describeLow = {
                                lowest.format(Formatters.elevation(it.toDouble(), settings.units))
                            },
                            describeHigh = {
                                peak.format(Formatters.elevation(it.toDouble(), settings.units))
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        TextFieldDialog(
            title = stringResource(R.string.rename_route),
            label = stringResource(R.string.route_name),
            initial = route?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_route_title),
            message = stringResource(R.string.delete_route_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Altitude against distance travelled along the route.
 *
 * A planned route has no timestamps, so distance is the only axis it can be
 * drawn against — and it is the one that matters anyway: what is asked of an
 * elevation profile is where the climb sits, not when.
 */
internal fun elevationProfile(points: List<RoutePoint>): List<ChartSample> {
    if (points.size < 2) return emptyList()
    val samples = ArrayList<ChartSample>(points.size)
    var travelled = 0.0
    points.forEachIndexed { index, point ->
        if (index > 0) {
            val previous = points[index - 1]
            travelled += GeoMath.distanceMeters(
                previous.latitude,
                previous.longitude,
                point.latitude,
                point.longitude,
            )
        }
        // Points without an altitude are a gap in the file, not a sea-level
        // reading; dropping them beats drawing a cliff down to zero.
        point.altitudeM?.let { samples += ChartSample(travelled.toFloat(), it.toFloat()) }
    }
    return samples
}

private const val PLACEHOLDER = "—"
