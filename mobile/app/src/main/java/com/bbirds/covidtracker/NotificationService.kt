package com.bbirds.covidtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationService private constructor(private val applicationContext: Context) {

    private val CHANNEL_ID = "notification_001";
    private val notificationManager: NotificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;

    companion object {
        @Volatile private var instance: NotificationService? = null
        private val LOCK = Any()

        operator fun invoke(context: Context) = instance ?: synchronized(LOCK){
            instance ?: NotificationService(context)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var channel = NotificationChannel(
                CHANNEL_ID,
                "COVID-19 contact detection channel",
                NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    fun notifyWithURLIntent(url: String) {
        val notificationIntent = Intent(Intent.ACTION_VIEW)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        notificationIntent.data = Uri.parse(url)

        var pending = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0);

        var builder = NotificationCompat.Builder(applicationContext, "notification_001")
            .setAutoCancel(true)
            .setContentTitle("COVID-19 Contact Detection!")
            .setContentIntent(pending)
            .setContentText("")
            .setSmallIcon(R.drawable.small_icon)
        builder.setChannelId(CHANNEL_ID);

        notificationManager.notify(0, builder.build())
    }

}