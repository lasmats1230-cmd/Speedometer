package com.lasse.speedometer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.LayoutSettings
import com.lasse.speedometer.data.repo.DaySummary
import com.lasse.speedometer.data.repo.Totals
import com.lasse.speedometer.tracking.TrackingState
import com.lasse.speedometer.tracking.TrackingStatus
import com.lasse.speedometer.ui.components.ActivityPicker
import com.lasse.speedometer.ui.components.BarChart
import com.lasse.speedometer.ui.components.BarSample
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.ProgressRing
import com.lasse.speedometer.ui.components.SectionCard
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.live.DimDisplay
import com.lasse.speedometer.ui.live.OnboardingDialog
import com.lasse.speedometer.ui.live.TodaySummary
import com.lasse.speedometer.ui.theme.SpeedometerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the shared parts and the screens that take plain data.
 *
 * Compiling proves the types line up; it does not prove a composable draws
 * without throwing, that a string has as many arguments as its format
 * expects, or that a tap reaches its callback. Robolectric runs the real
 * composition on the JVM, which is the only way to check any of that without
 * a device — and this container has no emulator to give one.
 *
 * The application class is the plain one on purpose: the app's own
 * initialises MapLibre, whose renderer is native code that has nothing to
 * load here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ComponentRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = AppSettings(layout = LayoutSettings())

    @Test
    fun `a stat tile shows its label and value, and reads as one thing aloud`() {
        compose.setContent {
            SpeedometerTheme { StatTile(label = "Max", value = "41.5 km/h") }
        }

        // The label is drawn uppercase but spoken as written.
        compose.onNodeWithContentDescription("Max: 41.5 km/h").assertIsDisplayed()
    }

    @Test
    fun `the bar chart totals its columns and names one when tapped`() {
        compose.setContent {
            SpeedometerTheme {
                BarChart(
                    bars = listOf(
                        BarSample("M", 1_000.0),
                        BarSample("T", 3_000.0, current = true),
                    ),
                    formatValue = { "${(it / 1000).toInt()} km" },
                    summaryLabel = "Total",
                )
            }
        }

        compose.onNodeWithText("Total").assertIsDisplayed()
        compose.onNodeWithText("4 km").assertIsDisplayed()

        compose.onNodeWithContentDescription("T: 3 km").performClick()

        compose.onNodeWithText("3 km").assertIsDisplayed()
    }

    @Test
    fun `the goal ring reports its progress to a screen reader`() {
        compose.setContent { SpeedometerTheme { ProgressRing(progress = 0.4f, label = "40%") } }

        compose.onNodeWithContentDescription("40%").assertIsDisplayed()
    }

    @Test
    fun `an overflow menu opens and calls back`() {
        var tapped = false
        compose.setContent {
            SpeedometerTheme {
                OverflowMenu(
                    actions = listOf(MenuAction("Rename") { tapped = true }),
                    contentDescription = "More",
                )
            }
        }

        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Rename").performClick()

        assertTrue("The menu action never reached its callback", tapped)
    }

    @Test
    fun `a section card draws its title and contents`() {
        compose.setContent {
            SpeedometerTheme {
                SectionCard(title = "Lifetime") { Text("21.34 km") }
            }
        }

        compose.onNodeWithText("Lifetime").assertIsDisplayed()
        compose.onNodeWithText("21.34 km").assertIsDisplayed()
    }

    @Test
    fun `the activity picker reports the choice it was tapped on`() {
        var chosen: ActivityType? = null
        compose.setContent {
            SpeedometerTheme {
                ActivityPicker(selected = ActivityType.RIDE, onSelect = { chosen = it })
            }
        }

        compose.onNodeWithText("Run").performClick()

        assertEquals(ActivityType.RUN, chosen)
    }

    @Test
    fun `the battery-saving readout shows speed, distance and time`() {
        compose.setContent {
            SpeedometerTheme {
                DimDisplay(
                    state = TrackingState(
                        status = TrackingStatus.RECORDING,
                        speedMps = 8.0,
                        distanceM = 12_340.0,
                        elapsedMs = 3_600_000,
                    ),
                    settings = settings,
                    onWake = {},
                )
            }
        }

        // 8 m/s is 28.8 km/h, rounded to a whole number for the big readout.
        compose.onNodeWithText("29").assertIsDisplayed()
        compose.onNodeWithText("12.34 km").assertIsDisplayed()
        compose.onNodeWithText("01:00:00").assertIsDisplayed()
    }

    @Test
    fun `the first-run dialog offers the permission and the way out`() {
        var granted = false
        var dismissed = false
        compose.setContent {
            SpeedometerTheme {
                OnboardingDialog(
                    onGrantLocation = { granted = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Not now").performClick()
        assertTrue(dismissed)

        compose.onNodeWithText("Allow location").performClick()
        assertTrue(granted)
    }

    @Test
    fun `the shared summary text names the activity and the figures`() {
        var summary = ""
        var card: com.lasse.speedometer.data.io.TripCardText? = null
        val trip = com.lasse.speedometer.data.db.TripEntity(
            id = 1,
            startedAt = 1_700_000_000_000,
            endedAt = 1_700_003_600_000,
            durationMs = 3_600_000,
            movingTimeMs = 3_000_000,
            distanceM = 21_340.0,
            avgSpeedMps = 7.1,
            maxSpeedMps = 11.5,
            ascentM = 312.0,
            descentM = 300.0,
            minAltitudeM = null,
            maxAltitudeM = null,
            activity = ActivityType.RUN.name,
            note = "Windy",
        )

        compose.setContent {
            SpeedometerTheme {
                summary = com.lasse.speedometer.ui.history.tripSummaryText(trip, settings)
                card = com.lasse.speedometer.ui.history.tripCardText(trip, settings)
            }
        }

        // Every placeholder resolved, nothing left as a raw format string.
        assertTrue(summary.contains("Run"))
        assertTrue(summary.contains("21.34 km"))
        assertTrue(summary.contains("Windy"))
        assertTrue("A run reads in pace, not km/h", summary.contains("/km"))
        // Nothing left as a raw format string or an unresolved template.
        assertTrue(!summary.contains("%1") && !summary.contains("{"))

        assertEquals("21.34 km", card?.headline)
        assertEquals(4, card?.stats?.size)
    }

    @Test
    fun `today's summary shows the day, the streak and the goal left`() {
        val summary = DaySummary(
            totals = Totals(trips = 2, distanceM = 13_000.0, movingTimeMs = 3_000_000),
            streakDays = 3,
            weekDistanceM = 28_000.0,
        )

        compose.setContent {
            SpeedometerTheme {
                TodaySummary(summary = summary, settings = settings.copy(weeklyGoalM = 50_000.0))
            }
        }

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("13.00 km · 50:00").assertIsDisplayed()
        compose.onNodeWithText("3 days in a row · 22.00 km to go this week").assertIsDisplayed()
    }

    @Test
    fun `a day with no history at all draws nothing`() {
        compose.setContent {
            SpeedometerTheme {
                Column {
                    Text("anchor")
                    TodaySummary(summary = DaySummary(Totals(), 0, 0.0), settings = settings)
                }
            }
        }

        compose.onNodeWithText("anchor").assertIsDisplayed()
        compose.onNodeWithText("Today").assertDoesNotExist()
    }
}
