package com.bbirds.covidtracker.data

import android.content.Context
import androidx.room.*
import java.util.*


// latitude longitude timestamp
@Entity
data class GeoPoint(var lat: Double?, var long: Double?, var time: Long?, @PrimaryKey(autoGenerate = true) var id: Int? = null) {

    constructor(): this(null, null, null, null){}

}


@Dao
interface GeoPointDAO {

    @Query("SELECT * FROM GeoPoint")
    fun getAll(): List<GeoPoint>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(geoPoints: List<GeoPoint>)

}

@Database(entities = arrayOf(GeoPoint::class), version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun geoPointDAO(): GeoPointDAO
    companion object {
        @Volatile private var instance: AppDatabase? = null
        private val LOCK = Any()

        operator fun invoke(context: Context)= instance ?: synchronized(LOCK){
            instance ?: buildDatabase(context).also { instance = it}
        }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(context,
            AppDatabase::class.java, "todo-list.db")
            .build()
    }
}