package com.bbirds.covidtracker

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

object SegmentContactService {
    private const val distDiff =  100 //in meters
    private const val R = 6371000 //in meters

    fun transformCoordinates(a : GeoPoint, lat : Double) : XyPoint {
        return XyPoint(
            Math.toRadians(a.long)*R*cos(Math.toRadians(lat)),
            Math.toRadians(a.lat)*R
        )
    }

    fun det(a : XyPoint, b : XyPoint, c : XyPoint) : Double {
        return a.x*b.y+b.x*c.y+c.x*a.y-a.x*c.y-b.x*a.y-c.x*b.y
    }

    fun dot(a : XyPoint, b : XyPoint, c : XyPoint) : Double {
        return (b.x-a.x)*(c.x-a.x) + (b.y-a.y)*(c.y-a.y)
    }

    fun euclidDist(a : XyPoint, b : XyPoint) : Double{
        val dx = a.x-b.x
        val dy = a.y-b.y
        return sqrt(dx*dx+dy*dy)
    }

    fun computePointToSegmentDist(a0 : XyPoint, a1 : XyPoint, b : XyPoint) : Double {
        if(dot(a0, a1, b) > 0 && dot(a1, a0, b) > 0) {
            return abs(det(a0, a1, b))/ euclidDist(a0, a1)
        }
        return min(euclidDist(a0, b), euclidDist(a1, b))
    }

    fun computeDist(a0 : GeoPoint, a1 : GeoPoint, b0 : GeoPoint, b1 : GeoPoint) : Double {
        val meanLat = (a0.lat+a1.lat+b0.lat+b1.lat)/4.0
        val a_0 = transformCoordinates(a0, meanLat)
        val a_1 = transformCoordinates(a1, meanLat)
        val b_0 = transformCoordinates(b0, meanLat)
        val b_1 = transformCoordinates(b1, meanLat)
        if(det(a_0, a_1, b_0)*det(a_0, a_1, b_1) < 0 &&
                det(b_0, b_1, a_0)*det(b_0, b_1, a_1) < 0) {
            return 0.0
        }
        return arrayOf<Double>(
            computePointToSegmentDist(a_0, a_1, b_0),
            computePointToSegmentDist(a_0, a_1, b_1),
            computePointToSegmentDist(b_0, b_1, a_0),
            computePointToSegmentDist(b_0, b_1, a_1)
        ).min()!!
    }

    fun contact(me: List<GeoPoint>, sick: List<GeoPoint>): List<GeoPoint> {
        var i=1
        var j=1
        val dangerousPoints = mutableSetOf<GeoPoint>()
        while(j < sick.size && sick[j].time < me[0].time) {
            j++
        }
        while(i < me.size && me[i].time < sick[0].time) {
            i++
        }
        while(i < me.size && j < sick.size) {
            val d = computeDist(me[i-1], me[i], sick[j-1], sick[j])
            if(d < distDiff) {
                dangerousPoints.add(me[i])
            }
//            print("" + d + " " + " " + me[i-1] + " " + me[i] + " " + sick[j-1] + " " + sick[j] + "\n")
            if(me[i].time < sick[j].time) {
                i++
            } else {
                j++
            }
        }
        return dangerousPoints.toList().sortedBy { x -> x.time }
    }
}