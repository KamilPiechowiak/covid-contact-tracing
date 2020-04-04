package com.bbirds.covidtracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.system.Os.link
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bbirds.covidtracker.data.AppDatabase
import com.bbirds.covidtracker.data.GeoPoint
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask


class TrackingService : Service() {

    companion object {
        const val PREFS_FILENAME = "com.bbirds.covidtracker.prefs"
        const val TAG = "TrackingService"
        const val RETENTION_PERIOD = 1000L//60*60*1000L
        const val LOCATION_INTERVAL = 5_000L
        const val LOCATION_DISTANCE = 25L
        const val ID = 123123123
        val BREAK_RECORDING =
            GeoPoint(-1_000.0, -1_000.0, -1)
    }

    private val binder = LocationServiceBinder()
    private var mLocationListener: LocationListener? = null
    private var recentLocations: LinkedList<GeoPoint> = LinkedList()
    private var mLocationManager: LocationManager? = null
    private var queue: RequestQueue? = null
    private var retentionTimer: Timer = Timer()
    private var heartbeatTimer: Timer? = null
    var isTracking: Boolean = false
    var consumerOffset: Int = 0

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    private inner class LocationListener(provider: String?) :
        android.location.LocationListener {
        private val TAG = "LocationListener"

        override fun onLocationChanged(location: Location) {
            synchronized(recentLocations) {
                recentLocations.add(
                    GeoPoint(
                        location.latitude,
                        location.longitude,
                        location.time / 1000
                    )
                )
            }
            Log.d(TAG, "onLocationChanged: ${recentLocations.last}")
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
            synchronized(recentLocations) {
                recentLocations.add(BREAK_RECORDING)
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        queue = Volley.newRequestQueue(applicationContext)
        GlobalScope.launch {
            loadRecentLocations()
            loadOffset()

            Log.d(TAG, recentLocations.toString())

            startForeground(ID, notification)

            heartbeatTimer = Timer()
            heartbeatTimer!!.schedule(
                timerTask {
                    heartbeat()
                },
                RETENTION_PERIOD / 2,
                RETENTION_PERIOD
            )
        }
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
        heartbeatTimer!!.cancel()
        GlobalScope.launch {
            saveRecentLocations()
            saveOffset()
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
        synchronized(recentLocations) {
            while (recentLocations.size > 0 && recentLocations[0].time <= twoWeaksAgo.time) {
                recentLocations.removeFirst()
            }
        }
    }

    private fun heartbeat() {
        if (recentLocations.size > 0) {
            val url = "http://${getString(R.string.python_backend_host)}/heartbeat/$consumerOffset"
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                Response.Listener<String> { response -> heartbeatCallback(response) },
                Response.ErrorListener { er -> Log.e(TAG, "Response Error $er") })
            queue!!.add(stringRequest)
        }
    }

    private fun heartbeatCallback(response: String) {
        val mapper = ObjectMapper()
        var rootNode = mapper.readTree(response)
        var result: MutableList<GeoPoint> = ArrayList()
        if (rootNode.isArray) {
            for (arrayNode in rootNode) {
                synchronized(consumerOffset) {
                    consumerOffset += arrayNode.size()
                }
                var sickPointsList: ArrayList<GeoPoint> = ArrayList(arrayNode.size())
                if (rootNode.isArray) {
                    for (pointNode in arrayNode) {
                        sickPointsList.add(
                            GeoPoint(
                                latitude = pointNode.get(0).doubleValue(),
                                longitude = pointNode.get(1).doubleValue(),
                                time = pointNode.get(2).longValue()))
                    }
                }
                synchronized(recentLocations) {
                    result.addAll(if (recentLocations.size > 0 && sickPointsList.size > 0) {
                        SegmentContactService.contact(recentLocations, sickPointsList)
                    } else {
                        listOf()
                    })
                }
            }
        }

        if (result.isNotEmpty()) {
            handleDetection(result)
        }
    }

    fun handleDetection(result: List<GeoPoint>) {
        val mapper = ObjectMapper()
        val string = mapper.writeValueAsString(result)
        var encoded = Base64.encode(string)
        // TODO strzał na wizualizację Macieja
        NotificationService(applicationContext).notifyWithURLIntent("http://${getString(R.string.web_app_host)}/?mark=$encoded")
    }

    fun startTracking() {
        initializeLocationManager()
        mLocationListener =
            LocationListener(LocationManager.GPS_PROVIDER)
        try {
            mLocationManager!!.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL,
                LOCATION_DISTANCE.toFloat(),
                mLocationListener!!
            )
            isTracking = true
            retentionTimer.schedule(
                timerTask {
                    retention()
                },
                0,
                RETENTION_PERIOD
            )
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

    private fun saveOffset() {
        synchronized(consumerOffset) {
            val sharedPreferences: SharedPreferences =
                getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putInt("OFFSET", consumerOffset)
            editor.apply()
        }
    }

    private fun loadOffset() {
        synchronized(consumerOffset) {
            val sharedPreferences: SharedPreferences =
                getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            consumerOffset = sharedPreferences.getInt("OFFSET", 0)
        }
    }

    private fun saveRecentLocations() {
        synchronized(recentLocations) {
            AppDatabase(applicationContext).geoPointDAO().insertAll(recentLocations)
        }
    }

    private fun loadRecentLocations() {
        synchronized(recentLocations) {
            var persistedLocations = AppDatabase(applicationContext).geoPointDAO().getAll()
            recentLocations = LinkedList()
            recentLocations.addAll(persistedLocations)
        }
    }

}