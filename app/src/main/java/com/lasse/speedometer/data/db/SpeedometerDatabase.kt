package com.lasse.speedometer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        TourEntity::class,
        RouteEntity::class,
        WaypointEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class SpeedometerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun tourDao(): TourDao
    abstract fun routeDao(): RouteDao
    abstract fun waypointDao(): WaypointDao

    companion object {
        @Volatile
        private var instance: SpeedometerDatabase? = null

        /**
         * Must match the `createSql` Room exports for [WaypointEntity] in
         * `schemas/…/2.json` exactly, or Room rejects the migrated database at
         * runtime. `MigrationSchemaTest` compares the two.
         */
        const val CREATE_WAYPOINTS = "CREATE TABLE IF NOT EXISTS `waypoints` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`label` TEXT NOT NULL, " +
            "`latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, " +
            "`colorArgb` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`note` TEXT)"

        /**
         * Adds the waypoints table. Written out by hand rather than falling
         * back to a destructive migration, which would take every recorded
         * trip with it.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_WAYPOINTS)
            }
        }

        /**
         * Adds the activity a trip was recorded as, and a free-text note.
         *
         * Existing trips become rides: this app was written for a bike, and a
         * wrong guess is one tap to correct where a null would need handling
         * in every screen that reads the column.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ADD_TRIP_COLUMNS.forEach(db::execSQL)
            }
        }

        /**
         * The column definitions have to read exactly as Room writes them in
         * `schemas/…/3.json`, down to the default value, or Room rejects the
         * migrated database. `MigrationSchemaTest` compares the two.
         */
        val ADD_TRIP_COLUMNS = listOf(
            "ALTER TABLE `trips` ADD COLUMN `activity` TEXT NOT NULL DEFAULT 'RIDE'",
            "ALTER TABLE `trips` ADD COLUMN `note` TEXT",
        )

        /**
         * Marks trips that are still being recorded, so a recording written as
         * it happens is not mistaken for a finished one.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ADD_IN_PROGRESS)
            }
        }

        const val ADD_IN_PROGRESS =
            "ALTER TABLE `trips` ADD COLUMN `inProgress` INTEGER NOT NULL DEFAULT 0"

        /**
         * Adds the thumbnail sketch. Existing trips get an empty one and are
         * filled in on first read, since deriving thousands of them inside a
         * migration would stall the first launch after an update.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ADD_SKETCH)
            }
        }

        const val ADD_SKETCH =
            "ALTER TABLE `trips` ADD COLUMN `sketch` TEXT NOT NULL DEFAULT ''"

        fun get(context: Context): SpeedometerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedometerDatabase::class.java,
                    "speedometer.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
