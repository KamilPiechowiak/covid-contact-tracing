package com.bbirds.covidtracker

import org.junit.Test

import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt

class ContactServiceTest {
    val service = ContactService

    @Test
    fun computeDistSq() {
        val a = GeoPoint(52.0 + 0.2/0.6, 16.0 + 0.5/0.6, 0)
        val b = GeoPoint(52.0  + 0.25/0.6, 16.0 + 0.55/0.6, 0)
        assertTrue(abs(sqrt(service.computeDistSq(a, b))-10860) < 100)
    }

    @Test
    fun contact() {
        val a = arrayOf<GeoPoint>(
            GeoPoint(52.4022557,16.9408745, 0),
            GeoPoint(52.40253059,16.94299961, 120),
            GeoPoint(52.40078283,16.94826952, 400),
            GeoPoint(52.40270078,16.95000781, 520),
            GeoPoint(52.40383318,16.94983608, 550)
        )
        val b = arrayOf<GeoPoint>(
            GeoPoint(52.40383318,16.94983608, 400),
            GeoPoint(52.40301497,16.94994332, 430),
            GeoPoint(52.40257641,16.94987892, 500),
            GeoPoint(52.40122796,16.94852656, 600)
        )
        print(service.contact(a, b))
    }
}