package com.satyam.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * A lightweight foreground service whose only job is to keep the process alive
 * so the SMS BroadcastReceiver keeps firing reliably even when the app is
 * swiped away, on Android 8+ background restrictions.
 */
class ForwarderForegroundService : Service() {

    private val channelId = "sms_forwarder_channel"
    private var pollingThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingThread == null || !pollingThread!!.isAlive) {
            pollingThread = Thread {
                TelegramForwarder.startPolling(this)
            }
            pollingThread?.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        TelegramForwarder.stopPolling()
        pollingThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Forwarder",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Forwarder running")
            .setContentText("Forwarding incoming SMS to Telegram")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}
