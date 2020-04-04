package com.bbirds.covidtracker.data

import android.content.Context
import androidx.room.*
import java.util.*


// latitude longitude timestamp
@Entity
data class GeoPoint(var latitude: Double, var longitude: Double, var time: Long, @PrimaryKey(autoGenerate = true) var id: Int = 0) {

    constructor() : this(-3_000.0, -3_000.0, -3, 0) {

    }

}


@Dao
interface GeoPointDAO {

    @Query("SELECT * FROM GeoPoint")
    fun getAll(): List<GeoPoint>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(geoPoints: List<GeoPoint>)

}

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