package com.lasse.speedometer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TrackPointEntity::class, TourEntity::class, RouteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SpeedometerDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun tourDao(): TourDao
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var instance: SpeedometerDatabase? = null

        fun get(context: Context): SpeedometerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedometerDatabase::class.java,
                    "speedometer.db",
                ).build().also { instance = it }
            }
    }
}
