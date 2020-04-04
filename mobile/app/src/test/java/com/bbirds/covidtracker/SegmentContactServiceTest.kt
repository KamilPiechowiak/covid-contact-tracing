package com.bbirds.covidtracker

import org.junit.Test

import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt

class SegmentContactServiceTest {
    val service = SegmentContactService

    @Test
    fun transformCoordinates() {
        val a = GeoPoint(52.0 + 0.2/0.6, 16.0 + 0.5/0.6, 0)
        val b = GeoPoint(52.0  + 0.25/0.6, 16.0 + 0.55/0.6, 0)
        val meanLat = (a.lat+b.lat)/2.0
        val axy = service.transformCoordinates(a, meanLat)
        val bxy = service.transformCoordinates(b, meanLat)
        print(service.euclidDist(axy, bxy))
        assertTrue(abs(service.euclidDist(axy, bxy) -10860) < 100)
    }

    @Test
    fun computePointToSegmentDistMisc() {
        assertEquals(sqrt(10.0), service.computePointToSegmentDist(XyPoint(0.0, 0.0), XyPoint(2.0, 3.0), XyPoint(1.0, 6.0)), 1e-3)
        assertEquals(sqrt(10.0), service.computePointToSegmentDist(XyPoint(0.0, 0.0), XyPoint(2.0, 3.0), XyPoint(5.0, 4.0)), 1e-3)
        assertEquals(4.0/sqrt(13.0), service.computePointToSegmentDist(XyPoint(0.0, 0.0), XyPoint(2.0, 3.0), XyPoint(0.0, 2.0)), 1e-3)
        assertEquals(4.0/sqrt(13.0), service.computePointToSegmentDist(XyPoint(10.0, 20.0), XyPoint(12.0, 23.0), XyPoint(10.0, 22.0)), 1e-3)
    }

    @Test
    fun computePointToSegmentDistLine() {
        assertEquals(sqrt(10.0), service.computePointToSegmentDist(XyPoint(-13.0, -3.0), XyPoint(-15.0, -1.0), XyPoint(-14.0, 2.0)), 1e-3)
        assertEquals(sqrt(8.0), service.computePointToSegmentDist(XyPoint(-13.0, -3.0), XyPoint(-15.0, -1.0), XyPoint(-13.0, 1.0)), 1e-3)
        assertEquals(sqrt(8.0), service.computePointToSegmentDist(XyPoint(-13.0, -3.0), XyPoint(-15.0, -1.0), XyPoint(-12.0, 0.0)), 1e-3)
        assertEquals(sqrt(8.0), service.computePointToSegmentDist(XyPoint(-13.0, -3.0), XyPoint(-15.0, -1.0), XyPoint(-11.0, -1.0)), 1e-3)
        assertEquals(sqrt(10.0), service.computePointToSegmentDist(XyPoint(-13.0, -3.0), XyPoint(-15.0, -1.0), XyPoint(-10.0, -2.0)), 1e-3)
    }

    @Test
    fun contact() {
        val a = listOf<GeoPoint>(
            GeoPoint(52.4022557,16.9408745, 0),
            GeoPoint(52.40253059,16.94299961, 120),
            GeoPoint(52.40078283,16.94826952, 400),
            GeoPoint(52.40270078,16.95000781, 520),
            GeoPoint(52.40383318,16.94983608, 550)
        )
        val b = arrayListOf<GeoPoint>(
            GeoPoint(52.40383318,16.94983608, 400),
            GeoPoint(52.40301497,16.94994332, 430),
            GeoPoint(52.40257641,16.94987892, 500),
            GeoPoint(52.40122796,16.94852656, 600)
        )
        print(service.contact(a, b))
    }
}