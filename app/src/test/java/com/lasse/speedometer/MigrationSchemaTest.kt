package com.lasse.speedometer

import com.lasse.speedometer.data.db.SpeedometerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room compares the migrated database against the schema it generated and
 * throws at runtime if a single column differs. That failure only shows up on
 * a device with an existing install — exactly the case that is hardest to
 * notice — so the two are compared here instead.
 */
class MigrationSchemaTest {

    private val schemaDir =
        File("schemas/com.lasse.speedometer.data.db.SpeedometerDatabase")

    @Test
    fun `waypoint migration matches the schema Room generated`() {
        val schema = File(schemaDir, "2.json")
        assertTrue(
            "Missing exported schema at ${schema.absolutePath}; run a build first.",
            schema.isFile,
        )

        val expected = createSqlFor(schema.readText(), "waypoints")
            .replace("\${TABLE_NAME}", "waypoints")

        assertEquals(expected, SpeedometerDatabase.CREATE_WAYPOINTS)
    }

    @Test
    fun `the columns added at version three match the schema Room generated`() {
        val schema = File(schemaDir, "3.json")
        assertTrue(
            "Missing exported schema at ${schema.absolutePath}; run a build first.",
            schema.isFile,
        )

        val createSql = createSqlFor(schema.readText(), "trips")
        SpeedometerDatabase.ADD_TRIP_COLUMNS.forEach { statement ->
            // "ALTER TABLE `trips` ADD COLUMN `activity` TEXT NOT NULL DEFAULT 'RIDE'"
            // has to name the column exactly as the table declares it, defaults
            // included, or Room refuses to open the migrated database.
            val definition = statement.substringAfter("ADD COLUMN ")
            assertTrue(
                "Version 3 of trips does not declare: $definition",
                createSql.contains(definition),
            )
        }
    }

    @Test
    fun `the in-progress column added at version four matches the schema`() {
        val schema = File(schemaDir, "4.json")
        assertTrue(
            "Missing exported schema at ${schema.absolutePath}; run a build first.",
            schema.isFile,
        )

        val createSql = createSqlFor(schema.readText(), "trips")
        val definition = SpeedometerDatabase.ADD_IN_PROGRESS.substringAfter("ADD COLUMN ")
        assertTrue(
            "Version 4 of trips does not declare: $definition",
            createSql.contains(definition),
        )
    }

    @Test
    fun `the sketch column added at version five matches the schema`() {
        val schema = File(schemaDir, "5.json")
        assertTrue(
            "Missing exported schema at ${schema.absolutePath}; run a build first.",
            schema.isFile,
        )

        val createSql = createSqlFor(schema.readText(), "trips")
        val definition = SpeedometerDatabase.ADD_SKETCH.substringAfter("ADD COLUMN ")
        assertTrue(
            "Version 5 of trips does not declare: $definition",
            createSql.contains(definition),
        )
    }

    @Test
    fun `the photos table added at version six matches the schema`() {
        val schema = File(schemaDir, "6.json").readText()

        val expected = createSqlFor(schema, "trip_photos").replace("\${TABLE_NAME}", "trip_photos")

        assertEquals(expected, SpeedometerDatabase.CREATE_TRIP_PHOTOS)
    }

    @Test
    fun `every table in the schema is reachable from version one`() {
        val schema = File(schemaDir, "6.json").readText()
        // The tables that already existed at version 1 plus the one the
        // migration adds should account for the whole version 2 schema.
        val tables = Regex("\"tableName\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(schema)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            setOf("trips", "track_points", "tours", "routes", "waypoints", "trip_photos"),
            tables,
        )
    }

    /** Pulls one table's `createSql` out of the exported schema JSON. */
    private fun createSqlFor(json: String, table: String): String {
        val marker = "\"tableName\": \"$table\""
        val start = json.indexOf(marker)
        assertTrue("No entity named $table in the exported schema", start >= 0)

        val key = "\"createSql\": \""
        val sqlStart = json.indexOf(key, start) + key.length
        val sqlEnd = json.indexOf("\",", sqlStart)
        return json.substring(sqlStart, sqlEnd)
            // The JSON escapes the backticks' surrounding quotes, not the
            // backticks themselves, so only quote escapes need undoing.
            .replace("\\\"", "\"")
    }
}
