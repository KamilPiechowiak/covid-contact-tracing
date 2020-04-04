package com.bbirds.covidtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.bbirds.covidtracker.data.AppDatabase
import com.bbirds.covidtracker.data.GeoPoint
import java.util.*
import kotlin.concurrent.timerTask
import kotlinx.coroutines.*

class TrackingService : Service() {

    companion object {
        const val TAG = "TrackingService"
        const val RETENTION_PERIOD = 60*60*1000L
        const val LOCATION_INTERVAL = 5_000L
        const val LOCATION_DISTANCE = 25L
        val BREAK_RECORDING =
            GeoPoint(-1_000.0, -1_000.0, -1)
    }

    private val binder = LocationServiceBinder()
    private var mLocationListener: LocationListener? = null
    private var recentLocations: LinkedList<GeoPoint> = LinkedList()
    private var mLocationManager: LocationManager? = null
    var isTracking: Boolean = false;

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    private inner class LocationListener(provider: String?) :
        android.location.LocationListener {
        private val TAG = "LocationListener"

        override fun onLocationChanged(location: Location) {
            recentLocations.add(
                GeoPoint(
                    location.longitude,
                    location.latitude,
                    location.time
                )
            )
        }

        override fun onProviderDisabled(provider: String) {
            Log.e(TAG, "onProviderDisabled: $provider")
        }

        override fun onProviderEnabled(provider: String) {
            Log.e(TAG, "onProviderEnabled: $provider")
        }

        override fun onStatusChanged(
            provider: String,
            status: Int,
            extras: Bundle
        ) {
            Log.e(TAG, "onStatusChanged: $status")
        }

        init {
            recentLocations.add(BREAK_RECORDING)
            var location = Location(provider)
            recentLocations.add(
                GeoPoint(
                    location.longitude,
                    location.latitude,
                    location.time
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        var persistedLocations = AppDatabase(applicationContext).geoPointDAO().getAll()
        recentLocations = LinkedList()
        for (loc in persistedLocations) {
            recentLocations.add(loc)
        }

        startForeground(123123123, notification)

        Timer().schedule(
            timerTask {
                heartbeat()
            },
            RETENTION_PERIOD / 2,
            RETENTION_PERIOD
        )

        Timer().schedule(
            timerTask {
                retention()
            },
            0,
            RETENTION_PERIOD
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        if (mLocationManager != null) {
            try {
                mLocationManager!!.removeUpdates(mLocationListener!!)
            } catch (ex: Exception) {
                Log.i(TAG, "fail to remove location listeners, ignore", ex)
            }
        }
        isTracking = false;
        GlobalScope.launch {
            AppDatabase(applicationContext).geoPointDAO().insertAll(recentLocations)
        }
    }

    private fun initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager =
                applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
    }

    private fun retention() {
        val currentDate = Date()
        val calendar = Calendar.getInstance()
        calendar.time = currentDate
        calendar.add(Calendar.DATE, -14)
        val twoWeaksAgo = calendar.time
        while (recentLocations.size > 0 && recentLocations[0].time!! <= twoWeaksAgo.time ) {
            recentLocations.removeFirst()
        }
    }

    private fun heartbeat() {

    }

    fun startTracking() {
        initializeLocationManager()
        mLocationListener =
            LocationListener(LocationManager.GPS_PROVIDER)
        try {
            mLocationManager!!.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL.toLong(),
                LOCATION_DISTANCE.toFloat(),
                mLocationListener!!
            )
            isTracking = true;
            Log.i(TAG, "Started tracking")
        } catch (ex: SecurityException) {
            Log.i(TAG, "fail to request location update, ignore $ex")
        } catch (ex: IllegalArgumentException) {
            Log.d(TAG, "gps provider does not exist $ex")
        }
    }

    fun stopTracking() {
        onDestroy()
    }

    private val notification: Notification
        @RequiresApi(Build.VERSION_CODES.O)
        get() {
            val channel =
                NotificationChannel(
                    "channel_01",
                    "COVID-19_Contact_Detector",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager!!.createNotificationChannel(channel)
            val builder =
                NotificationCompat.Builder(applicationContext, "channel_01")
                    .setAutoCancel(true)
                    .setContentTitle("COVID-19 Contact Detector")
                    .setContentText("")
            return builder.build()
        }

    inner class LocationServiceBinder : Binder() {
        val service: TrackingService
            get() = this@TrackingService
    }
}