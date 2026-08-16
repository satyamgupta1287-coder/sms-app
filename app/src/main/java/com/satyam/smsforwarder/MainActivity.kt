package com.satyam.smsforwarder
import com.satyam.smsforwarder.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
            Toast.makeText(
                this,
                "SMS permission is required for forwarding to work",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.grantButton).setOnClickListener {
            requestPermissionsIfNeeded()
        }

        findViewById<android.widget.Button>(R.id.settingsButton).setOnClickListener {
            openAppInfoSettings() 
        }

        findViewById<android.widget.Button>(R.id.batteryButton).setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
        
        findViewById<android.widget.Button>(R.id.autoStartButton).setOnClickListener {
            openAutoStartSettings()
        }

        findViewById<android.widget.Button>(R.id.testButton).setOnClickListener {
            FirebaseForwarder.send(
                applicationContext,
                "AI Studio Test",
                "Test Message: Ye app bilkul sahi kaam kar raha hai! 🎉"
            )
            Toast.makeText(this, "Sending test message to Firebase...", Toast.LENGTH_SHORT).show()
        }

        if (hasAllPermissions()) {
            onPermissionsGranted()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            onPermissionsGranted()
        } else {
            findViewById<TextView>(R.id.statusText).text =
                "❌ Permissions Missing\n\nYou MUST grant both SMS (Read/Receive) and SEND_SMS permissions in the app settings to use Two-Way forwarding."
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissionsIfNeeded() {
        if (hasAllPermissions()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun onPermissionsGranted() {
        val serviceIntent = Intent(this, ForwarderForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        setupAutoRunner()
        
        findViewById<TextView>(R.id.statusText).text =
            "✅ Running in Background.\n\n" +
            "Ultimate Background Runner is Active. If killed, it will auto-restart."
            
        requestIgnoreBatteryOptimizations()
    }

    private fun setupAutoRunner() {
        // Setup WorkManager Heartbeat to run every 15 minutes
        val workRequest = PeriodicWorkRequestBuilder<ServiceRestarterWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ServiceRestarter",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Couldn't open battery settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openAppInfoSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAutoStartSettings() {
        try {
            val intent = Intent()
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    intent.component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                    intent.component = android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                }
                manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                    intent.component = android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                }
                manufacturer.contains("asus") -> {
                    intent.component = android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    intent.component = android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                }
                else -> {
                    // Fallback to standard app info or battery optimization
                    Toast.makeText(this, "Auto-start handled natively or not found for this device.", Toast.LENGTH_LONG).show()
                    requestIgnoreBatteryOptimizations()
                    return
                }
            }
            
            val list = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (list.size > 0) {
                startActivity(intent)
                Toast.makeText(this, "Please enable AutoStart / Background Activity for SMS Forwarder", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Auto-start settings not found on this device.", Toast.LENGTH_LONG).show()
                openAppInfoSettings()
            }
        } catch (e: Exception) {
            openAppInfoSettings()
        }
    }
}
