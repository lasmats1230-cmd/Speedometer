package com.lasse.speedometer

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.tracking.TripSummary
import com.lasse.speedometer.ui.history.HistoryScreen
import com.lasse.speedometer.ui.live.ActiveRoute
import com.lasse.speedometer.ui.settings.SettingsScreen
import com.lasse.speedometer.ui.stats.StatsScreen
import com.lasse.speedometer.ui.tools.RouteDetailScreen
import com.lasse.speedometer.ui.theme.SpeedometerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the rewritten screens with their real view models and database.
 *
 * These are the screens this work changed most, and until now none of them
 * had been run: a wrong resource id, a `remember` in a lazy scope, a flow that
 * never emits — all compile perfectly and fail the moment a person opens the
 * tab. The app's own Application class is used here, which is what makes the
 * view models resolvable; it survives a MapLibre that cannot load, and the map
 * falls back to a drawn sketch of the track.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: SpeedometerApp = ApplicationProvider.getApplicationContext()
    private val settings = AppSettings()

    private val startedAt = System.currentTimeMillis() - 3_600_000

    @Before
    fun seedHistory() {
        runBlocking {
        val repository = app.tripRepository
        repository.deleteAllTrips()
        repository.saveTrip(
            summary = TripSummary(
                // Today, so it falls inside every period the screen offers.
                startedAt = startedAt,
                endedAt = startedAt + 3_600_000,
                durationMs = 3_600_000,
                movingTimeMs = 3_000_000,
                distanceM = 21_340.0,
                avgSpeedMps = 7.1,
                maxSpeedMps = 11.5,
                ascentM = 312.0,
                descentM = 300.0,
                minAltitudeM = 88.0,
                maxAltitudeM = 401.0,
            ),
            points = emptyList(),
            activity = ActivityType.RIDE,
            title = "Morning ride",
        )
        }
    }

    @Test
    fun `history lists what was recorded and can search it away again`() {
        compose.setContent {
            SpeedometerTheme {
                HistoryScreen(
                    settings = settings,
                    snackbarHostState = SnackbarHostState(),
                    onOpenTrip = {},
                    onOpenTour = {},
                    onStartRecording = {},
                )
            }
        }

        compose.awaitText("Morning ride")
        compose.onNodeWithText("Morning ride").assertIsDisplayed()
        compose.onNodeWithText("21.34 km").assertIsDisplayed()

        compose.onNodeWithText("Search trips").performTextInput("something else")

        compose.onNodeWithText("Nothing matches.").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        compose.onNodeWithText("Morning ride").assertIsDisplayed()
    }

    @Test
    fun `statistics adds today's ride up and lists the records`() {
        compose.setContent {
            SpeedometerTheme {
                StatsScreen(settings = settings, onOpenTrip = {}, onStartRecording = {})
            }
        }

        // The ride was recorded today, so this week's total is the ride.
        compose.awaitText("21.34 km")

        // The rest of the screen has to be scrolled to: a lazy list does not
        // compose what is below the fold, so this is also the only honest way
        // to assert it — the same way a person would reach it.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Longest trip"))
        compose.onNodeWithText("Longest trip").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lifetime"))
        // The tiles merge their label and value into one spoken description,
        // so that is what identifies them — the same string a screen reader
        // would read out.
        compose.onNodeWithContentDescription("Day streak", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `statistics can be narrowed to one activity`() {
        runBlocking {
            app.tripRepository.saveTrip(
                summary = TripSummary(
                    startedAt = startedAt + 1000,
                    endedAt = startedAt + 1_801_000,
                    durationMs = 1_800_000,
                    movingTimeMs = 1_800_000,
                    distanceM = 5_000.0,
                    avgSpeedMps = 2.8,
                    maxSpeedMps = 3.5,
                    ascentM = 10.0,
                    descentM = 10.0,
                    minAltitudeM = null,
                    maxAltitudeM = null,
                ),
                points = emptyList(),
                activity = ActivityType.RUN,
                title = "Evening run",
            )
        }

        compose.setContent {
            SpeedometerTheme {
                StatsScreen(settings = settings, onOpenTrip = {}, onStartRecording = {})
            }
        }

        // Both trips together: 21.34 km ridden plus 5 km run.
        compose.awaitText("26.34 km")

        compose.onNodeWithText("Run").performClick()

        // The run on its own.
        compose.awaitText("5.00 km")
    }

    @Test
    fun `settings draws every section and changes a unit`() {
        compose.setContent {
            SpeedometerTheme {
                SettingsScreen(
                    settings = settings,
                    snackbarHostState = SnackbarHostState(),
                    onOpenLayout = {},
                    onOpenLicenses = {},
                )
            }
        }

        compose.onNodeWithText("Units").assertIsDisplayed()
        compose.onNodeWithText("mph").performClick()

        // Sections further down the list exist once scrolled to.
        compose.onNodeWithText("Units").assertIsDisplayed()
    }

    @Test
    fun `history offers to start a recording when there is nothing yet`() {
        runBlocking { app.tripRepository.deleteAllTrips() }
        var started = false

        compose.setContent {
            SpeedometerTheme {
                HistoryScreen(
                    settings = settings,
                    snackbarHostState = SnackbarHostState(),
                    onOpenTrip = {},
                    onOpenTour = {},
                    onStartRecording = { started = true },
                )
            }
        }

        compose.awaitText("No trips yet.")
        compose.onNodeWithText("Start a recording").performClick()

        assertTrue("The empty state's button did nothing", started)
    }

    @Test
    fun `the screens still say what they say at the largest font scale`() {
        // Android's accessibility settings go to 2x, and the tiles, chips and
        // headers here are the sort of layout that quietly stops fitting.
        compose.setContent {
            HugeText {
                SpeedometerTheme {
                    StatsScreen(settings = settings, onOpenTrip = {}, onStartRecording = {})
                }
            }
        }

        compose.awaitText("21.34 km")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Longest trip"))
        compose.onNodeWithText("Longest trip").assertIsDisplayed()
    }

    @Test
    fun `history is still readable and searchable at the largest font scale`() {
        compose.setContent {
            HugeText {
                SpeedometerTheme {
                    HistoryScreen(
                        settings = settings,
                        snackbarHostState = SnackbarHostState(),
                        onOpenTrip = {},
                        onOpenTour = {},
                        onStartRecording = {},
                    )
                }
            }
        }

        compose.awaitText("Morning ride")
        compose.onNodeWithText("Search trips").performTextInput("something else")
        compose.onNodeWithText("Nothing matches.").assertIsDisplayed()
    }

    @Test
    fun `an imported route opens on its figures and a way to follow it`() {
        // Eleven points running north, climbing 100 m over about a kilometre.
        val encoded = (0..10).joinToString(";") { index ->
            "${50.0 + index * 0.0008993},8.0,${100 + index * 10}"
        }
        val routeId = runBlocking {
            app.tripRepository.insertRoute(
                RouteEntity(
                    name = "Sunday loop",
                    importedAt = System.currentTimeMillis(),
                    distanceM = 1_000.0,
                    ascentM = 100.0,
                    encodedPoints = encoded,
                )
            )
        }
        var followed = false

        compose.setContent {
            SpeedometerTheme {
                RouteDetailScreen(
                    routeId = routeId,
                    settings = settings,
                    onBack = {},
                    onFollow = { followed = true },
                )
            }
        }

        compose.awaitText("Sunday loop")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Follow on live view"))
        compose.onNodeWithText("Follow on live view").performClick()

        assertTrue("Following a route did not open the live view", followed)
        assertEquals(routeId, ActiveRoute.routeId.value)

        // The high point is read from the decoded points, not from the row,
        // which only stores distance and ascent.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("200 m"))
        compose.onNodeWithContentDescription("200 m", substring = true).assertIsDisplayed()

        ActiveRoute.clear()
        runBlocking { app.tripRepository.deleteRoute(routeId) }
    }
}

/**
 * Renders its content at the largest font scale Android's settings offer.
 *
 * A screen that only ever runs at 1x looks finished and is not: the person
 * most likely to have text at 2x is the one least able to work around a
 * layout that breaks.
 */
@Composable
private fun HugeText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 2f),
        content = content,
    )
}

/**
 * Waits for text to appear.
 *
 * The screens read their data from flows backed by the database, so the first
 * frame is empty by design: asserting immediately would only ever test the
 * loading state.
 *
 * The budget is a deadlock guard, not a performance assertion — nothing here
 * measures how fast a screen renders, and a passing test takes the same time
 * whatever the number is, because the wait ends as soon as the text is there.
 * Five seconds was too tight: on a runner with a cold Gradle cache, the
 * statistics screen alone timed out three times over while the same commit
 * passed everywhere else. Sized to survive a slow shared runner instead.
 */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.awaitText(text: String) {
    waitUntil(30_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
