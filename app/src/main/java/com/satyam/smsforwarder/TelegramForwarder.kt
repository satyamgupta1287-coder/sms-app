package com.satyam.smsforwarder

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Date

/**
 * Sends captured SMS content to a Telegram bot via the Bot API's sendMessage endpoint.
 * Token and chat id come from BuildConfig, which is populated from local.properties
 * at build time (see README in the zip for setup instructions).
 */
object TelegramForwarder {

    private val client = OkHttpClient()

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
}
