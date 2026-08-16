package com.satyam.smsforwarder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class ServiceRestarterWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.d("ServiceRestarterWorker", "Heartbeat triggered: Ensuring Foreground Service is running")
        val intent = Intent(applicationContext, ForwarderForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("ServiceRestarterWorker", "Failed to start service: ${e.message}")
        }
        return Result.success()
    }
}
