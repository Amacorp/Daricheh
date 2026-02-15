package com.daricheh.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.daricheh.app.MeshApplication
import com.daricheh.app.data.ContactsManager
import com.daricheh.app.model.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class MessageRouter(private val context: Context) {

    private val gson = Gson()
    private val app = MeshApplication.instance
    private val contactsManager = ContactsManager(context)
    private val peers = ConcurrentHashMap<String, Peer>()
    private val routingTable = ConcurrentHashMap<String, RoutingTableEntry>()
    private val pendingMessages = ConcurrentLinkedQueue<MeshMessage>()
    private val processedIds = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var localNetworkManager: LocalNetworkManager? = null
    private var bluetoothManager: BluetoothMeshManager? = null

    var onMessageForMe: ((MeshMessage) -> Unit)? = null
    var onPeerListChanged: ((List<Peer>) -> Unit)? = null
    var onMessageStatusChanged: ((String, MessageStatus) -> Unit)? = null

    fun start() {
        app.log("Router: Starting...")

        localNetworkManager = LocalNetworkManager(
            context, ::handlePeerDiscovered, ::handleRawMessage, ::handlePeerDisconnected
        )
        localNetworkManager?.start()

        // Always start Bluetooth as fallback
        try {
            bluetoothManager = BluetoothMeshManager(
                context, ::handlePeerDiscovered, ::handleRawMessage, ::handlePeerDisconnected
            )
            bluetoothManager?.start()
        } catch (e: Exception) {
            app.log("Router: BT error: ${e.message}")
        }

        scope.launch {
            while (true) {
                delay(10_000)
                retryPending()
                cleanOldIds()
            }
        }
    }

    fun stop() {
        localNetworkManager?.stop()
        bluetoothManager?.stop()
        scope.cancel()
    }

    fun discoverPeers() {
        localNetworkManager?.startDiscovery()
        bluetoothManager?.startDiscovery()
    }

    fun sendMessage(recipientId: String, content: String): MeshMessage {
        val msg = MeshMessage(
            senderId = app.deviceId,
            senderName = app.username,
            senderPhone = app.phoneNumber,
            recipientId = recipientId,
            content = content
        )
        msg.routePath.add(app.deviceId)
        routeMessage(msg)
        return msg
    }

    fun connectToPeer(peer: Peer) {
        when (peer.connectionType) {
            ConnectionType.BLUETOOTH -> bluetoothManager?.connectToPeer(peer)
            else -> {} // LocalNetwork auto-connects
        }
    }

    fun getDiscoveredPeers(): List<Peer> = peers.values.filter { it.isAlive() }

    private fun handlePeerDiscovered(peer: Peer) {
        if (peer.deviceType == DeviceType.TV) return

        peers[peer.id] = peer
        routingTable[peer.id] = RoutingTableEntry(peer.id, peer.id, 1, peer.connectionType)

        if (peer.phoneNumber.isNotBlank()) {
            contactsManager.registerPhone(peer.phoneNumber, peer.id)
        }
        onPeerListChanged?.invoke(getDiscoveredPeers())
        retryPending()
    }

    private fun handlePeerDisconnected(peerId: String) {
        peers.remove(peerId)
        routingTable.remove(peerId)
        onPeerListChanged?.invoke(getDiscoveredPeers())
    }

    private fun handleRawMessage(senderId: String, raw: String) {
        try {
            val wrapper = gson.fromJson(raw, MessageWrapper::class.java)
            when (wrapper.type) {
                MessageType.CHAT -> handleChat(gson.fromJson(wrapper.payload, MeshMessage::class.java), senderId)
                MessageType.ACK -> handleAck(gson.fromJson(wrapper.payload, MessageAck::class.java))
                MessageType.PEER_ANNOUNCE -> handleAnnounce(gson.fromJson(wrapper.payload, PeerAnnounce::class.java), senderId)
                else -> {}
            }
        } catch (_: Exception) {}
    }

    private fun handleChat(msg: MeshMessage, fromId: String) {
        if (processedIds.containsKey(msg.id)) return
        if (!msg.incrementHop(app.deviceId)) return
        processedIds[msg.id] = System.currentTimeMillis()

        if (msg.recipientId == app.deviceId) {
            msg.status = MessageStatus.DELIVERED
            onMessageForMe?.invoke(msg)
            sendAck(msg, fromId)
        } else {
            routeMessage(msg, fromId)
        }
    }

    private fun routeMessage(msg: MeshMessage, excludeId: String? = null) {
        val wrapped = gson.toJson(MessageWrapper(MessageType.CHAT, gson.toJson(msg)), String::class.java) ?: return
        val json = gson.toJson(MessageWrapper(MessageType.CHAT, gson.toJson(msg)))

        // Try direct first
        if (localNetworkManager?.sendMessage(msg.recipientId, json) == true) {
            updateMsgStatus(msg, MessageStatus.SENT)
            return
        }
        if (bluetoothManager?.sendMessage(msg.recipientId, json) == true) {
            updateMsgStatus(msg, MessageStatus.SENT)
            return
        }

        // Broadcast
        localNetworkManager?.broadcastMessage(json, excludeId)
        bluetoothManager?.broadcastMessage(json, excludeId)

        // Queue if nobody received
        val anyConnected = (localNetworkManager?.getConnectedPeerIds()?.isNotEmpty() == true) ||
                (bluetoothManager?.getConnectedPeerIds()?.isNotEmpty() == true)

        if (anyConnected) {
            updateMsgStatus(msg, MessageStatus.SENT)
        } else {
            updateMsgStatus(msg, MessageStatus.PENDING)
            if (pendingMessages.none { it.id == msg.id }) pendingMessages.add(msg)
        }
    }

    private fun updateMsgStatus(msg: MeshMessage, status: MessageStatus) {
        msg.status = status
        onMessageStatusChanged?.invoke(msg.id, status)
    }

    private fun sendAck(msg: MeshMessage, toId: String) {
        val ack = MessageAck(msg.id, msg.senderId)
        val json = gson.toJson(MessageWrapper(MessageType.ACK, gson.toJson(ack)))

        if (localNetworkManager?.sendMessage(toId, json) == true) return
        bluetoothManager?.sendMessage(toId, json)
    }

    private fun handleAck(ack: MessageAck) {
        pendingMessages.removeAll { it.id == ack.messageId }
        onMessageStatusChanged?.invoke(ack.messageId, MessageStatus.DELIVERED)

        if (ack.recipientId != app.deviceId) {
            val json = gson.toJson(MessageWrapper(MessageType.ACK, gson.toJson(ack)))
            localNetworkManager?.broadcastMessage(json)
            bluetoothManager?.broadcastMessage(json)
        }
    }

    private fun handleAnnounce(ann: PeerAnnounce, fromId: String) {
        if (ann.phoneNumber.isNotBlank()) contactsManager.registerPhone(ann.phoneNumber, ann.peerId)
        ann.knownPeers.forEach { id ->
            if (id != app.deviceId) routingTable[id] = RoutingTableEntry(id, fromId, ann.hopCount + 1, ConnectionType.UNKNOWN)
        }
    }

    fun broadcastPeerAnnounce() {
        val ann = PeerAnnounce(app.deviceId, app.username, app.phoneNumber, routingTable.keys.toList(), hasInternetAccess(), 0)
        val json = gson.toJson(MessageWrapper(MessageType.PEER_ANNOUNCE, gson.toJson(ann)))
        localNetworkManager?.broadcastMessage(json)
        bluetoothManager?.broadcastMessage(json)
    }

    private fun retryPending() {
        val msgs = pendingMessages.toList()
        pendingMessages.clear()
        msgs.forEach { routeMessage(it) }
    }

    private fun cleanOldIds() {
        val cutoff = System.currentTimeMillis() - 300_000
        processedIds.entries.removeIf { it.value < cutoff }
    }

    fun hasInternetAccess(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ✅ این مدل‌ها اینجا اضافه شدند
enum class MessageType { CHAT, PEER_ANNOUNCE, ACK, ROUTING_UPDATE }
data class MessageWrapper(val type: MessageType, val payload: String)
data class PeerAnnounce(val peerId: String, val peerName: String, val phoneNumber: String = "", val knownPeers: List<String>, val hasInternet: Boolean, val hopCount: Int)
data class MessageAck(val messageId: String, val recipientId: String)
data class RoutingUpdate(val entries: List<RoutingTableEntry>)