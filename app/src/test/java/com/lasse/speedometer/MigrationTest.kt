package com.lasse.speedometer

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lasse.speedometer.data.db.SpeedometerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the migrations against a real SQLite file.
 *
 * `MigrationSchemaTest` compares the migration statements with the schema
 * Room exported, which catches a typo. It cannot catch a migration that runs
 * but loses data, or one whose result Room then refuses to open. This does
 * both: it writes rows into an old database, migrates it, and reads them back
 * out — the failure mode being guarded against is someone's history
 * disappearing on an app update, which is the worst thing this app could do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    private companion object {
        const val DATABASE = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SpeedometerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val migrations = arrayOf(
        SpeedometerDatabase.MIGRATION_1_2,
        SpeedometerDatabase.MIGRATION_2_3,
        SpeedometerDatabase.MIGRATION_3_4,
        SpeedometerDatabase.MIGRATION_4_5,
        SpeedometerDatabase.MIGRATION_5_6,
    )

    @Test
    fun `a version one database migrates all the way to the current one`() {
        helper.createDatabase(DATABASE, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO trips (
                    startedAt, endedAt, durationMs, movingTimeMs, distanceM,
                    avgSpeedMps, maxSpeedMps, ascentM, descentM,
                    minAltitudeM, maxAltitudeM, title, tourId
                ) VALUES (
                    1700000000000, 1700003600000, 3600000, 3000000, 21340.0,
                    7.1, 11.5, 312.0, 300.0, 88.0, 401.0, 'Ride to the lake', NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO track_points (
                    tripId, timestamp, latitude, longitude, altitudeM,
                    speedMps, accuracyM, cumulativeDistanceM
                ) VALUES (1, 1700000000000, 50.1, 8.2, 120.0, 5.5, 6.0, 0.0)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE, 6, true, *migrations)

        // The trip survived, and the columns added along the way defaulted the
        // way an existing recording needs them to.
        migrated.query("SELECT title, activity, note, inProgress, sketch FROM trips")
            .use { cursor ->
                assertTrue("The trip did not survive the migrations", cursor.moveToFirst())
                assertEquals("Ride to the lake", cursor.getString(0))
                assertEquals("RIDE", cursor.getString(1))
                assertTrue("A migrated trip has no note", cursor.isNull(2))
                assertEquals(0, cursor.getInt(3))
                // Filled in on first launch rather than in the migration.
                assertEquals("", cursor.getString(4))
            }

        migrated.query("SELECT COUNT(*) FROM track_points").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        // Version 2 added waypoints; they have to exist and be writable.
        migrated.execSQL(
            """
            INSERT INTO waypoints (label, latitude, longitude, colorArgb, createdAt, note)
            VALUES ('Water tap', 50.2, 8.3, -16711936, 1700000000000, NULL)
            """.trimIndent()
        )
        migrated.query("SELECT label FROM waypoints").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Water tap", cursor.getString(0))
        }

        // Version 6 added photos, which hang off the trip that survived.
        migrated.execSQL(
            "INSERT INTO trip_photos (tripId, uri, addedAt) VALUES (1, 'content://x/1', 1)"
        )
        migrated.query("SELECT uri FROM trip_photos WHERE tripId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("content://x/1", cursor.getString(0))
        }
    }

    @Test
    fun `deleting a trip takes its photos with it`() {
        helper.createDatabase(DATABASE, 1).close()
        val migrated = helper.runMigrationsAndValidate(DATABASE, 6, true, *migrations)

        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL(
            """
            INSERT INTO trips (
                startedAt, endedAt, durationMs, movingTimeMs, distanceM,
                avgSpeedMps, maxSpeedMps, ascentM, descentM,
                minAltitudeM, maxAltitudeM
            ) VALUES (1, 2, 1, 1, 0.0, 0.0, 0.0, 0.0, 0.0, NULL, NULL)
            """.trimIndent()
        )
        migrated.execSQL(
            "INSERT INTO trip_photos (tripId, uri, addedAt) VALUES (1, 'content://x/1', 1)"
        )

        migrated.execSQL("DELETE FROM trips WHERE id = 1")

        migrated.query("SELECT COUNT(*) FROM trip_photos").use { cursor ->
            cursor.moveToFirst()
            assertEquals("The cascade left photos behind", 0, cursor.getInt(0))
        }
    }

    @Test
    fun `each step validates on its own`() {
        helper.createDatabase(DATABASE, 1).close()

        helper.runMigrationsAndValidate(DATABASE, 2, true, SpeedometerDatabase.MIGRATION_1_2)
        helper.runMigrationsAndValidate(DATABASE, 3, true, SpeedometerDatabase.MIGRATION_2_3)
        helper.runMigrationsAndValidate(DATABASE, 4, true, SpeedometerDatabase.MIGRATION_3_4)
        helper.runMigrationsAndValidate(DATABASE, 5, true, SpeedometerDatabase.MIGRATION_4_5)
        val six = helper.runMigrationsAndValidate(
            DATABASE,
            6,
            true,
            SpeedometerDatabase.MIGRATION_5_6,
        )

        assertEquals(6, six.version)
    }

    @Test
    fun `an in-progress trip is hidden from history but still in the table`() {
        helper.createDatabase(DATABASE, 1).close()
        val migrated = helper.runMigrationsAndValidate(DATABASE, 6, true, *migrations)

        migrated.execSQL(
            """
            INSERT INTO trips (
                startedAt, endedAt, durationMs, movingTimeMs, distanceM,
                avgSpeedMps, maxSpeedMps, ascentM, descentM,
                minAltitudeM, maxAltitudeM, activity, inProgress
            ) VALUES (
                1700000000000, 1700000000000, 0, 0, 0.0,
                0.0, 0.0, 0.0, 0.0, NULL, NULL, 'RUN', 1
            )
            """.trimIndent()
        )

        migrated.query("SELECT COUNT(*) FROM trips WHERE inProgress = 0").use { cursor ->
            cursor.moveToFirst()
            assertEquals("A recording in progress is not history", 0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM trips").use { cursor ->
            cursor.moveToFirst()
            assertEquals("...but it is still stored", 1, cursor.getInt(0))
        }
        assertFalse(migrated.isReadOnly)
    }
}
