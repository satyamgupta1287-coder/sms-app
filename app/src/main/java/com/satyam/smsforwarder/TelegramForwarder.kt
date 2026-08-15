package com.satyam.smsforwarder

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Sends captured SMS content to a Telegram bot, and polls for /send commands to send out SMS.
 */
object TelegramForwarder {

    private val client = OkHttpClient.Builder()
        .readTimeout(65, TimeUnit.SECONDS) // long polling needs longer timeout
        .build()

    private var lastUpdateId: Long = 0
    @Volatile var isPolling = false

    fun send(context: Context, sender: String, body: String) {
        val token = BuildConfig.BOT_TOKEN
        val chatId = BuildConfig.CHAT_ID

        if (token.isBlank() || token == "PUT_YOUR_BOT_TOKEN_HERE") {
            Log.e("TelegramForwarder", "Bot token not configured, skipping send")
            return
        }

        val timestamp = DateFormat.format("dd MMM yyyy, hh:mm a", Date())
        val text = "📩 New SMS\nFrom: $sender\nAt: $timestamp\n\n$body"

        val url = "https://api.telegram.org/bot$token/sendMessage"
        val formBody = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramForwarder", "Send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramForwarder", "Telegram API error: ${response.code} ${response.body?.string()}")
                }
                response.close()
            }
        })
    }

    fun sendSystemMessage(context: Context, text: String) {
        val token = BuildConfig.BOT_TOKEN
        val chatId = BuildConfig.CHAT_ID
        if (token.isBlank() || token == "PUT_YOUR_BOT_TOKEN_HERE") return
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val formBody = FormBody.Builder().add("chat_id", chatId).add("text", text).build()
        val request = Request.Builder().url(url).post(formBody).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: okhttp3.Response) { response.close() }
        })
    }

    fun startPolling(context: Context) {
        isPolling = true
        val token = BuildConfig.BOT_TOKEN
        if (token.isBlank() || token == "PUT_YOUR_BOT_TOKEN_HERE") {
            Log.e("TelegramForwarder", "Bot token not configured, skipping poll")
            return
        }

        while (isPolling) {
            try {
                // long polling
                val url = "https://api.telegram.org/bot$token/getUpdates?offset=$lastUpdateId&timeout=60"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        parseAndHandleUpdates(context, responseBody)
                    }
                }
                response.close()
            } catch (e: Exception) {
                Log.e("TelegramForwarder", "Polling error: ${e.message}")
                if (isPolling) Thread.sleep(5000)
            }
        }
    }

    fun stopPolling() {
        isPolling = false
    }

    private fun parseAndHandleUpdates(context: Context, jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.getBoolean("ok")) return

            val result = jsonObject.getJSONArray("result")
            for (i in 0 until result.length()) {
                val update = result.getJSONObject(i)
                val updateId = update.getLong("update_id")
                lastUpdateId = updateId + 1

                if (update.has("message")) {
                    val message = update.getJSONObject("message")
                    if (message.has("text")) {
                        val text = message.getString("text")
                        handleCommand(context, text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramForwarder", "JSON Parse error: ${e.message}")
        }
    }

    private fun handleCommand(context: Context, text: String) {
        if (text.trim().equals("/ping", ignoreCase = true)) {
            sendSystemMessage(context, "✅ Bot is active and polling successfully!\nSend SMS permission: " + 
                (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED))
            return
        }
        
        if (text.startsWith("/send ", ignoreCase = true)) {
            val parts = text.split(" ", limit = 3)
            if (parts.size >= 3) {
                val number = parts[1]
                val messageText = parts[2]
                sendSms(context, number, messageText)
            } else {
                sendSystemMessage(context, "❌ Invalid command format.\nUse: /send +919876543210 Hello")
            }
        }
    }

    private fun sendSms(context: Context, number: String, messageText: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            
            if (smsManager == null) {
                sendSystemMessage(context, "❌ Failed: SmsManager is null on this device.")
                return
            }
            
            smsManager.sendTextMessage(number, null, messageText, null, null)
            sendSystemMessage(context, "✅ SMS sent successfully to $number\nMessage: $messageText")
        } catch (e: Exception) {
            Log.e("TelegramForwarder", "Failed to send SMS: ${e.message}")
            sendSystemMessage(context, "❌ Failed to send SMS to $number\nError: ${e.message}\nMake sure SEND_SMS permission is granted.")
        }
    }
}
