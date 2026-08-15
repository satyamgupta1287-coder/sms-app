package com.satyam.smsforwarder

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Date

/**
 * Handles uploading incoming SMS to Firebase Realtime Database
 * and listening for outbound SMS commands.
 */
object FirebaseForwarder {

    private val database = FirebaseDatabase.getInstance()
    private val incomingRef = database.getReference("messages/incoming")
    private val outgoingRef = database.getReference("messages/outgoing")
    private val devicesRef = database.getReference("devices")

    @Volatile var isPolling = false
    private var listener: ValueEventListener? = null
    
    private var myDeviceId: String = ""
    private var myDeviceName: String = ""

    private fun initDevice(context: Context) {
        if (myDeviceId.isEmpty()) {
            myDeviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
            myDeviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
        }
    }

    private fun setupPresence(context: Context) {
        initDevice(context)
        val myDeviceRef = devicesRef.child(myDeviceId)
        
        // Firebase Presence system
        val connectedRef = database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // When device disconnects, set status to offline
                    myDeviceRef.child("status").onDisconnect().setValue("offline")
                    myDeviceRef.child("lastActive").onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                    
                    // Set status to online now
                    myDeviceRef.setValue(mapOf(
                        "name" to myDeviceName,
                        "status" to "online",
                        "lastActive" to System.currentTimeMillis()
                    ))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun send(context: Context, sender: String, body: String) {
        initDevice(context)
        val timestamp = DateFormat.format("dd MMM yyyy, hh:mm a", Date()).toString()
        val timestampMillis = System.currentTimeMillis()
        
        val messageData = mapOf(
            "sender" to sender,
            "body" to body,
            "timestamp" to timestamp,
            "timestampMillis" to timestampMillis,
            "deviceId" to myDeviceId,
            "deviceName" to myDeviceName
        )

        incomingRef.push().setValue(messageData)
            .addOnSuccessListener {
                Log.d("FirebaseForwarder", "SMS uploaded to Firebase successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseForwarder", "Failed to upload SMS to Firebase", e)
            }
    }

    fun startPolling(context: Context) {
        if (isPolling) return
        isPolling = true
        
        initDevice(context)
        setupPresence(context)
        
        Log.d("FirebaseForwarder", "Started listening for outgoing messages")

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val status = child.child("status").getValue(String::class.java)
                    val targetDevice = child.child("targetDeviceId").getValue(String::class.java)
                    
                    // Only process messages targeting this device (or messages without a target for backward compatibility)
                    val isForMe = targetDevice == myDeviceId || targetDevice == null
                    
                    if (status == "pending" && isForMe) {
                        val number = child.child("number").getValue(String::class.java)
                        val messageText = child.child("body").getValue(String::class.java)
                        
                        if (number != null && messageText != null) {
                            sendSms(context, number, messageText, child.key)
                        } else {
                            child.ref.child("status").setValue("failed")
                            child.ref.child("error").setValue("Missing number or body")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseForwarder", "Database listen error: ${error.message}")
            }
        }
        
        outgoingRef.addValueEventListener(listener!!)
    }

    fun stopPolling() {
        isPolling = false
        listener?.let { outgoingRef.removeEventListener(it) }
        listener = null
    }

    private fun sendSms(context: Context, number: String, messageText: String, key: String?) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            
            if (smsManager == null) {
                key?.let { 
                    outgoingRef.child(it).child("status").setValue("failed")
                    outgoingRef.child(it).child("error").setValue("SmsManager is null")
                }
                return
            }
            
            smsManager.sendTextMessage(number, null, messageText, null, null)
            
            // Mark as sent in database
            key?.let {
                outgoingRef.child(it).child("status").setValue("sent")
            }
            Log.d("FirebaseForwarder", "SMS sent successfully to $number")
        } catch (e: Exception) {
            Log.e("FirebaseForwarder", "Failed to send SMS: ${e.message}")
            key?.let {
                outgoingRef.child(it).child("status").setValue("failed")
                outgoingRef.child(it).child("error").setValue(e.message ?: "Unknown error")
            }
        }
    }
}
