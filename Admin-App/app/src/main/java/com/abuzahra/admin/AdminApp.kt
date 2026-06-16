package com.abuzahra.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AdminApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DEVICE_EVENTS,
                "أحداث الأجهزة",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات أحداث ومراقبة الأجهزة"
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_DEVICE_EVENTS = "device_events"

        @Volatile
        private lateinit var instance: AdminApp

        fun getInstance(): AdminApp = instance
    }
}