package com.bbirds.covidtracker.data

import androidx.room.*


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

