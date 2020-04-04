package com.bbirds.covidtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = arrayOf(GeoPoint::class), version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun geoPointDAO(): GeoPointDAO
    companion object {
        @Volatile private var instance: AppDatabase? = null
        private val LOCK = Any()

        operator fun invoke(context: Context)= instance ?: synchronized(LOCK){
            instance ?: buildDatabase(context).also { instance = it}
        }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(context,
            AppDatabase::class.java, "geopoint-list.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}