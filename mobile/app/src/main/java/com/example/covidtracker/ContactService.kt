package com.example.covidtracker

// latitude longitude timestamp
data class GeoPoint(var lat: Double, var long: Double, var time: Long)

object ContactService {

    fun contact(me: Array<GeoPoint>, sick: Array<GeoPoint>): List<GeoPoint> {
        // @Kamil TODO
        return listOf();
    }

}