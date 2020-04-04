package com.bbirds.covidtracker

import com.bbirds.covidtracker.data.GeoPoint

data class GeoSegment(val begin : GeoPoint, val end : GeoPoint, val valid : Boolean)