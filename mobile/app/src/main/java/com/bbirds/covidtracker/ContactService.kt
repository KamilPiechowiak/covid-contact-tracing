package com.bbirds.covidtracker

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

object ContactService {
    private const val timeDiff = 30*60 //in seconds
    private const val distDiff =  100 //in meters
    private const val R = 6371000 //in meters

    fun computeDistSq(a : GeoPoint, b : GeoPoint) : Double {
        val dx = Math.toRadians(abs(a.long - b.long))*R*cos(Math.toRadians((a.lat+b.lat)/2))
        val dy = Math.toRadians(abs(a.lat-b.lat))*R
        return dx*dx+dy*dy
    }

    fun contact(me: List<GeoPoint>, sick: ArrayList<GeoPoint>): List<GeoPoint> {
        val distDiffSq = distDiff * distDiff
        var j = 0
        val dangerousPoints = mutableListOf<GeoPoint>()
        for(point in me) {
            if(j < 0) {
                j++
            }
            while(j < sick.size && sick[j].time < point.time) {
                j++
            }
            j--
            if(j < 0 || sick[j].time + timeDiff < point.time) { //point.time is not on the sick path
                continue
            }
            val d = computeDistSq(point, sick[j])
            if(d < distDiffSq) {
                dangerousPoints.add(point)
            }
//            print("" + point + " " + sick[j] + " " + sqrt(d) + "\n")
        }
        return dangerousPoints
    }

}