package com.bbirds.covidtracker

import com.bbirds.covidtracker.data.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

object SegmentContactService {
    private const val distDiff =  100 //in meters
    private const val R = 6371000 //in meters

    fun transformCoordinates(a : GeoPoint, lat : Double) : XyPoint {
        return XyPoint(
            Math.toRadians(a.longitude)*R*cos(Math.toRadians(lat)),
            Math.toRadians(a.latitude)*R
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
        val meanLat = (a0.latitude+a1.latitude+b0.latitude+b1.latitude)/4.0
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

    fun pointsToSegments(points : List<GeoPoint>) : List<GeoSegment> {
        val segments = mutableListOf<GeoSegment>()
        var i = 0
        while(i < points.size && points[i] == TrackingService.BREAK_RECORDING) {
            i++
        }
        var previousPoint = points[i]
        var previousIndex = i
        i++
        while(i < points.size) {
            if(points[i] == TrackingService.BREAK_RECORDING) {
                i++
                continue
            }
            segments.add(GeoSegment(previousPoint, points[i], (i-previousIndex)==1))
            previousPoint = points[i]
            previousIndex = i
            i++
        }
        return segments
    }

    fun contact(me: List<GeoPoint>, sick: ArrayList<GeoPoint>): List<GeoPoint> {
        val mySegments = pointsToSegments(me)
        val sickSegments = pointsToSegments(sick)
//        print("" + mySegments.size + " " + sickSegments.size)
        var i=0
        var j=0
        val dangerousPoints = mutableSetOf<GeoPoint>()
        if(mySegments.size == 0 || sickSegments.size == 0) {
            return listOf<GeoPoint>()
        }
        while(j < sickSegments.size && sickSegments[j].end.time < mySegments[0].begin.time) {
            j++
        }
        while(i < mySegments.size && mySegments[i].end.time < sickSegments[0].begin.time) {
            i++
        }
        while(i < mySegments.size && j < sickSegments.size) {
            if(mySegments[i].valid && sickSegments[j].valid) {
                val d = computeDist(mySegments[i].begin, mySegments[i].end, sickSegments[j].begin, sickSegments[j].end)
                if(d < distDiff) {
                    dangerousPoints.add(mySegments[i].end)
                }
            }
//            print("" + mySegments[i] + " " + sickSegments[j] + "\n")
            if(mySegments[i].end.time < sickSegments[j].end.time) {
                i++
            } else {
                j++
            }
        }
        return dangerousPoints.toList().sortedBy { x -> x.time }
    }
}