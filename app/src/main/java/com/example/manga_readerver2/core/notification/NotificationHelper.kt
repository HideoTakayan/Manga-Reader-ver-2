package com.example.manga_readerver2.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class NotificationHelper(private val context: Context) {

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel = NotificationChannel(
                "default_channel",
                "Default Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val downloadChannel = NotificationChannel(
                "download_channel",
                "Download Notifications",
                NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(defaultChannel)
            notificationManager.createNotificationChannel(downloadChannel)
        }
    }

    fun showNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = androidx.core.app.NotificationCompat.Builder(context, "default_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
