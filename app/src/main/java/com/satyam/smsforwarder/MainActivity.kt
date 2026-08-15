package com.satyam.smsforwarder
import com.satyam.smsforwarder.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
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

        findViewById<TextView>(R.id.statusText).text =
            "Configure BOT_TOKEN and CHAT_ID in app/build.gradle before building.\n\n" +
            "Tap the button below to grant permissions and start forwarding."

        findViewById<android.widget.Button>(R.id.grantButton).setOnClickListener {
            requestPermissionsIfNeeded()
        }

        findViewById<android.widget.Button>(R.id.batteryButton).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        findViewById<android.widget.Button>(R.id.testButton).setOnClickListener {
            TelegramForwarder.send(
                applicationContext,
                "AI Studio Test",
                "Test Message: Ye app bilkul sahi kaam kar raha hai! 🎉"
            )
            Toast.makeText(this, "Sending test message to Telegram...", Toast.LENGTH_SHORT).show()
        }

        if (hasAllPermissions()) {
            onPermissionsGranted()
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
        findViewById<TextView>(R.id.statusText).text =
            "✅ Running. Incoming SMS will be forwarded to your Telegram bot.\n\n" +
            "Tip: also disable battery optimization for this app so Android doesn't kill it."
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open settings", Toast.LENGTH_SHORT).show()
        }
    }
}
