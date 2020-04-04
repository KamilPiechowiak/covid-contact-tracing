package com.bbirds.covidtracker


import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bbirds.covidtracker.TrackingService.LocationServiceBinder
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var gpsService: TrackingService? = null;
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val name = className.className
            if (name.endsWith(TrackingService.TAG)) {
                gpsService = (service as LocationServiceBinder).service
                progressBar.isVisible = false
                trackButton.isEnabled = true
                if (gpsService!!.isTracking) {
                    buttonStateOn()
                    Toast.makeText(this@MainActivity,  "Tracking device!", Toast.LENGTH_LONG).show()
                } else {
                    buttonStateOff()
                    Toast.makeText(this@MainActivity,  "Device is not tracked", Toast.LENGTH_LONG).show()
                }
                textView.text = getString(R.string.gps_ready)
                Log.i("APP", "TrackingService connected")
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            if (className.className == TrackingService.TAG) {
                gpsService = null
                textView.text = getString(R.string.gps_not_ready)
                Log.i("APP", "TrackingService disconnected")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this.application, TrackingService::class.java)
        this.application.startService(intent)
        this.application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun buttonStateOn() {
        trackButton.text = getString(R.string.tracking_off_description)
        trackButton.setOnClickListener { trackingOff() }
    }

    fun buttonStateOff() {
        trackButton.text = getString(R.string.tracking_on_description)
        trackButton.setOnClickListener { trackingOn() }
    }

    fun trackingOn() {
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    gpsService!!.startTracking()
                    Log.i("APP", "Started tracking device");
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    if (response.isPermanentlyDenied()) {
                        openSettings()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
        buttonStateOn()
    }

    fun trackingOff() {
        gpsService!!.stopTracking()
        Log.i("APP", "Stopped tracking device");
        buttonStateOff()
    }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri: Uri =
            Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

}
