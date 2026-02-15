package com.daricheh.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.daricheh.app.MeshApplication
import com.daricheh.app.R
import com.daricheh.app.model.MeshMessage
import com.daricheh.app.model.MessageStatus
import com.daricheh.app.model.Peer
import com.daricheh.app.network.MessageRouter
import com.daricheh.app.ui.ChatActivity
import com.daricheh.app.ui.MainActivity
import kotlinx.coroutines.*

class MeshService : Service() {

    private val binder = MeshBinder()
    private var router: MessageRouter? = null
    private var scope: CoroutineScope? = null
    private val app by lazy { MeshApplication.instance }

    var onMessageReceived: ((MeshMessage) -> Unit)? = null
    var onPeerListChanged: ((List<Peer>) -> Unit)? = null
    var onMessageStatusChanged: ((String, MessageStatus) -> Unit)? = null

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        app.log("Service: Created")

        try {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            router = MessageRouter(this)

            router?.onMessageForMe = { msg ->
                try {
                    onMessageReceived?.invoke(msg)
                    showMessageNotification(msg)
                } catch (e: Exception) {
                    app.log("Service: msg callback error: ${e.message}")
                }
            }
            router?.onPeerListChanged = { peers ->
                try { onPeerListChanged?.invoke(peers) } catch (e: Exception) { app.log("Service: peers error: ${e.message}") }
            }
            router?.onMessageStatusChanged = { id, status ->
                try { onMessageStatusChanged?.invoke(id, status) } catch (e: Exception) { app.log("Service: status error: ${e.message}") }
            }

            router?.start()

            scope?.launch {
                while (true) {
                    delay(30_000)
                    try { router?.broadcastPeerAnnounce() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            app.log("Service: onCreate error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(1001, createServiceNotification()) } catch (e: Exception) { app.log("Service: foreground error: ${e.message}") }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { router?.stop(); scope?.cancel() } catch (_: Exception) {}
        app.log("Service: Destroyed")
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, MeshApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.mesh_service_running))
            .setSmallIcon(R.drawable.ic_mesh_network)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /**
     * Show notification for new message - clicking opens the chat
     */
    private fun showMessageNotification(msg: MeshMessage) {
        try {
            // Create intent that opens the specific chat
            val chatIntent = Intent(this, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("peer_id", msg.senderId)
                putExtra("peer_name", msg.senderName)
            }

            val pi = PendingIntent.getActivity(
                this,
                msg.senderId.hashCode(), // Unique request code per sender
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, MeshApplication.MESSAGE_CHANNEL_ID)
                .setContentTitle(msg.senderName)
                .setContentText(msg.content)
                .setSmallIcon(R.drawable.ic_mesh_network)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(msg.senderId.hashCode(), notification)
            app.log("Service: Notification shown for ${msg.senderName}: ${msg.content}")
        } catch (e: Exception) {
            app.log("Service: Notification error: ${e.message}")
        }
    }

    fun sendMessage(recipientId: String, content: String): MeshMessage? {
        return try { router?.sendMessage(recipientId, content) } catch (e: Exception) { app.log("Service: send error: ${e.message}"); null }
    }

    fun discoverPeers() { try { router?.discoverPeers() } catch (_: Exception) {} }
    fun connectToPeer(peer: Peer) { try { router?.connectToPeer(peer) } catch (_: Exception) {} }
    fun getDiscoveredPeers(): List<Peer> = try { router?.getDiscoveredPeers() ?: emptyList() } catch (_: Exception) { emptyList() }
    fun hasInternet(): Boolean = try { router?.hasInternetAccess() ?: false } catch (_: Exception) { false }
}