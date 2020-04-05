package com.bbirds.covidtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import java.nio.file.Files
import java.nio.file.Paths
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
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
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
        Log.i(TAG, "retention")
        val currentDate = Date()
        val calendar = Calendar.getInstance()
        calendar.time = currentDate
        calendar.add(Calendar.DATE, -14)
        val twoWeaksAgo = calendar.time
        synchronized(recentLocations) {
            while (recentLocations.size > 0 && recentLocations[0].time * 1000 <= twoWeaksAgo.time) {
                recentLocations.removeFirst()
            }
        }
    }

    private fun heartbeat() {
        synchronized(recentLocations) {
            if (recentLocations.size > 0) {
                val url =
                    "http://${getString(R.string.python_backend_host)}/heartbeat/$consumerOffset"
                val stringRequest = StringRequest(
                    Request.Method.GET, url,
                    Response.Listener<String> { response -> heartbeatCallback(response) },
                    Response.ErrorListener { er -> Log.e(TAG, "Response Error $er") })
                queue!!.add(stringRequest)
            }
        }
    }

    private fun heartbeatCallback(response: String) {
        Log.i(TAG, "heartbeatCallback")
        val mapper = ObjectMapper()
        var rootNode = mapper.readTree(response)
        var result: MutableList<GeoPoint> = ArrayList()
        synchronized(consumerOffset) {
            consumerOffset += rootNode.size()
        }
        if (rootNode.isArray) {
            for (arrayNode in rootNode) {
                var sickPointsList: ArrayList<GeoPoint> = ArrayList(arrayNode.size())
                if (arrayNode.isArray) {
                    for (pointNode in arrayNode) {
                        sickPointsList.add(
                            GeoPoint(
                                latitude = pointNode.get(0)!!.asDouble(),
                                longitude = pointNode.get(1)!!.asDouble(),
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
        Log.d(TAG, "encoded: $encoded")
        NotificationService(applicationContext).notifyWithURLIntent("https://kamilpiechowiak.github.io/covid-contact-tracing/?mark=$encoded")
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
            queue = Volley.newRequestQueue(applicationContext)
            GlobalScope.launch {
                loadRecentLocations()
                loadOffset()

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
//            consumerOffset = sharedPreferences.getInt("OFFSET", 0)
        }
    }

    private fun saveRecentLocations() {
        synchronized(recentLocations) {
            AppDatabase(applicationContext).geoPointDAO().insertAll(recentLocations)
        }
    }

    private fun loadRecentLocations() {
        synchronized(recentLocations) {
//            var persistedLocations = AppDatabase(applicationContext).geoPointDAO().getAll()
//            recentLocations = LinkedList()
//            recentLocations.addAll(persistedLocations)

//            DEMO ONLY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var string = "[[52.391519, 16.953873, 1586014426], [52.392192, 16.951709, 1586014545], [52.392387, 16.95187, 1586014563], [52.392416, 16.951777, 1586014568], [52.392597, 16.951724, 1586014582], [52.392594, 16.951703, 1586014583], [52.393243, 16.951499, 1586014636], [52.393245, 16.951516, 1586014637], [52.393465, 16.95142, 1586014655], [52.393785, 16.951592, 1586007452], [52.394096, 16.951497, 1586012522], [52.394213, 16.95138, 1586014719], [52.394464, 16.951375, 1586010816], [52.39449, 16.951417, 1586012081], [52.394566, 16.951397, 1586014748], [52.394591, 16.951614, 1586014759], [52.394638, 16.951584, 1586003264], [52.395156, 16.951429, 1586014805], [52.395202, 16.951212, 1586014816], [52.395225, 16.951221, 1586014818], [52.395226, 16.951207, 1586014819], [52.395306, 16.951244, 1586014825], [52.395307, 16.951226, 1586014826], [52.395449, 16.951304, 1586014838], [52.395459, 16.951293, 1586014839], [52.395615, 16.951427, 1586014853], [52.395634, 16.951414, 1586005369], [52.395692, 16.951453, 1586014860], [52.396028, 16.951228, 1586010629], [52.39616, 16.951162, 1586013811], [52.396185, 16.951096, 1586014904], [52.396217, 16.951093, 1586014907], [52.396239, 16.950846, 1586006601], [52.396346, 16.950394, 1586014943], [52.396433, 16.950494, 1586003409], [52.396622, 16.950606, 1586005427], [52.396777, 16.95067, 1586007032], [52.397087, 16.950883, 1586010407], [52.397467, 16.951063, 1586014379], [52.397513, 16.951141, 1586015044], [52.397814, 16.95084, 1586005506], [52.398732, 16.949813, 1586015162], [52.39876, 16.949874, 1586015165], [52.399606, 16.94895, 1586009833], [52.399658, 16.948938, 1586010208], [52.399694, 16.9489, 1586010514], [52.399798, 16.948757, 1586011485], [52.399887, 16.948565, 1586012537], [52.400153, 16.948103, 1586015308], [52.400656, 16.948225, 1586015349], [52.400683, 16.948157, 1586015353], [52.400777, 16.948192, 1586004305], [52.400907, 16.948294, 1586006625], [52.40137, 16.948768, 1586015416], [52.401406, 16.948675, 1586015421], [52.401605, 16.948825, 1586004704], [52.402681, 16.949899, 1586015540], [52.403334, 16.948143, 1586015299], [52.403365, 16.948117, 1586015643], [52.403365, 16.948117, 1586015643], [52.403384, 16.948101, 1586015645], [52.403425, 16.948118, 1586003371], [52.404801, 16.949475, 1586015777], [52.404753, 16.94955, 1586015782], [52.404753, 16.94955, 1586015782], [52.404757, 16.949542, 1586015783], [52.404803, 16.949606, 1586003933], [52.404859, 16.949571, 1586004678], [52.404915, 16.949595, 1586005397], [52.405636, 16.950308, 1586015865], [52.405568, 16.950453, 1586004978], [52.405534, 16.950596, 1586006415], [52.405215, 16.951467, 1586015931], [52.405351, 16.951595, 1586009245], [52.405526, 16.951542, 1586015958], [52.405526, 16.951542, 1586015958], [52.405351, 16.951595, 1586010070], [52.405215, 16.951467, 1586015985], [52.405179, 16.951518, 1586003884], [52.40511, 16.95151, 1586004610], [52.404171, 16.950593, 1586016082], [52.404159, 16.950616, 1586016083], [52.403963, 16.950419, 1586016102], [52.403882, 16.950552, 1586005960], [52.403741, 16.950633, 1586009163], [52.40362, 16.950635, 1586011758], [52.403422, 16.950549, 1586016150], [52.4033, 16.9509, 1586016170], [52.40313, 16.950715, 1586016186], [52.403112, 16.950761, 1586016189], [52.402961, 16.950606, 1586005656], [52.402866, 16.950544, 1586006843], [52.402409, 16.950086, 1586013059], [52.402134, 16.950029, 1586016276], [52.402118, 16.950068, 1586016279], [52.401955, 16.949907, 1586014432], [52.401922, 16.949907, 1586016297], [52.401597, 16.95075, 1586016345], [52.401546, 16.950702, 1586016351], [52.40132, 16.950704, 1586016370], [52.401307, 16.950621, 1586016374], [52.401271, 16.950645, 1586016377], [52.401271, 16.950645, 1586016377], [52.401307, 16.950621, 1586016380], [52.401345, 16.950873, 1586005143], [52.401318, 16.951066, 1586006183], [52.401347, 16.951137, 1586006632], [52.401752, 16.95165, 1586011039], [52.401913, 16.951891, 1586012917], [52.402265, 16.952254, 1586016498], [52.402346, 16.952341, 1586016506], [52.402362, 16.952357, 1586016509], [52.402382, 16.952378, 1586016511], [52.402997, 16.953008, 1586016572], [52.40304, 16.952954, 1586005503], [52.4031, 16.952944, 1586007209], [52.403198, 16.953001, 1586010150], [52.403426, 16.953022, 1586016608], [52.403397, 16.954083, 1586016660], [52.403414, 16.954077, 1586016661], [52.403522, 16.954038, 1586016670], [52.403522, 16.954038, 1586016670], [52.403476, 16.954055, 1586016674], [52.403473, 16.954203, 1586016681], [52.403399, 16.954921, 1586016717], [52.403299, 16.95494, 1586016725], [52.403325, 16.955056, 1586004731], [52.403267, 16.955593, 1586007408], [52.403288, 16.955688, 1586007904], [52.403173, 16.957168, 1586015227], [52.403146, 16.957283, 1586015832], [52.403071, 16.957352, 1586016524], [52.403062, 16.957417, 1586016852], [52.401463, 16.957456, 1586016980], [52.401463, 16.957456, 1586016980], [52.401198, 16.957447, 1586009654], [52.400831, 16.957361, 1586017031], [52.400836, 16.957281, 1586005175], [52.400818, 16.957247, 1586005592], [52.400815, 16.957189, 1586006131], [52.400921, 16.956235, 1586015111], [52.400926, 16.956086, 1586016493], [52.400957, 16.956045, 1586017098], [52.400858, 16.955828, 1586017111], [52.40088, 16.955801, 1586004682], [52.399726, 16.953284, 1586016686], [52.399711, 16.953132, 1586017274], [52.399731, 16.953051, 1586017279], [52.399721, 16.952995, 1586005859], [52.399746, 16.952842, 1586009063], [52.399836, 16.952462, 1586017309], [52.399816, 16.952452, 1586017311], [52.399812, 16.952425, 1586004784], [52.399588, 16.952257, 1586005845], [52.399562, 16.952184, 1586006067], [52.399569, 16.952105, 1586006277], [52.400507, 16.949494, 1586014244], [52.400656, 16.949154, 1586015345], [52.400872, 16.948555, 1586017174], [52.400905, 16.948494, 1586017388], [52.400942, 16.948485, 1586017550], [52.401005, 16.948285, 1586005450], [52.401047, 16.947968, 1586006170], [52.401476, 16.946775, 1586009247], [52.401932, 16.945406, 1586012712], [52.401968, 16.945346, 1586012899], [52.402619, 16.943398, 1586017834], [52.402725, 16.943452, 1586017843], [52.402871, 16.943391, 1586017855], [52.40343, 16.943836, 1586017062], [52.403411, 16.943902, 1586017908], [52.403509, 16.943974, 1586005769], [52.403595, 16.944083, 1586006236], [52.404371, 16.944592, 1586009828], [52.404655, 16.944715, 1586011090], [52.404938, 16.944766, 1586012314], [52.405322, 16.944727, 1586013967], [52.405709, 16.944577, 1586015676], [52.40605, 16.944345, 1586017263], [52.406225, 16.944168, 1586018147]]"
                val mapper = ObjectMapper()
                var rootNode = mapper.readTree(string)

                if (rootNode.isArray) {
                    for (arrayNode in rootNode) {
                        recentLocations.add(
                            GeoPoint(
                                latitude = arrayNode.get(0)!!.asDouble(),
                                longitude = arrayNode.get(1)!!.asDouble(),
                                time = arrayNode.get(2).longValue()))
                    }
                }
            } else {
                TODO("VERSION.SDK_INT < O")
            }
            recentLocations.add(BREAK_RECORDING)
        }
    }

}