package com.satyam.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Merge multipart SMS into one body, keep sender from first part
        val sender = messages[0].originatingAddress ?: "Unknown"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        Log.d("SmsReceiver", "SMS from $sender captured, forwarding...")
        TelegramForwarder.send(context, sender, fullBody)
    }
}
