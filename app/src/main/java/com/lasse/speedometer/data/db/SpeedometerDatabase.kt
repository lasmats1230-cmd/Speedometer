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
    version = 2,
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

        fun get(context: Context): SpeedometerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedometerDatabase::class.java,
                    "speedometer.db",
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
