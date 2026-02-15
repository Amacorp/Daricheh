package com.daricheh.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MeshApplication : Application() {

    private val _logs = ConcurrentLinkedQueue<String>()
    val logs: List<String> get() = _logs.toList()

    lateinit var prefs: android.content.SharedPreferences
        private set

    val deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString().substring(0, 8)
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var phoneNumber: String
        get() = prefs.getString("phone_number", "") ?: ""
        set(value) = prefs.edit().putString("phone_number", value).apply()

    val isSetupComplete: Boolean
        get() = username.isNotBlank() && phoneNumber.isNotBlank()

    var bluetoothEnabled: Boolean
        get() = prefs.getBoolean("bluetooth_enabled", true)
        set(value) = prefs.edit().putBoolean("bluetooth_enabled", value).apply()

    var wifiDirectEnabled: Boolean
        get() = prefs.getBoolean("wifi_direct_enabled", false)
        set(value) = prefs.edit().putBoolean("wifi_direct_enabled", value).apply()

    var autoDiscoverEnabled: Boolean
        get() = prefs.getBoolean("auto_discover_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_discover_enabled", value).apply()

    override fun onCreate() {
        super.onCreate()

        try {
            instance = this
            prefs = getSharedPreferences("mesh_prefs", MODE_PRIVATE)
            createNotificationChannels()
            log("App started. Device ID: $deviceId")
        } catch (e: Exception) {
            android.util.Log.e("MeshApp", "Application init error: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val serviceChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Mesh Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                val messageChannel = NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
                manager?.createNotificationChannel(messageChannel)
            } catch (e: Exception) {
                log("Notification channel error: ${e.message}")
            }
        }
    }

    fun log(message: String) {
        try {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val entry = "[$time] $message"
            _logs.add(entry)
            while (_logs.size > 500) {
                _logs.poll()
            }
            android.util.Log.d("MeshApp", message)
        } catch (e: Exception) {
            android.util.Log.e("MeshApp", "Log error: ${e.message}")
        }
    }

    fun clearLogs() {
        _logs.clear()
    }

    fun getLogsText(): String {
        return try {
            _logs.joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "mesh_service_channel"
        const val MESSAGE_CHANNEL_ID = "mesh_message_channel"

        lateinit var instance: MeshApplication
            private set
    }
}