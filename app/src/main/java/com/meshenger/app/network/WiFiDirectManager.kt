package com.daricheh.app.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log
import com.daricheh.app.model.ConnectionType
import com.daricheh.app.model.Peer
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class WiFiDirectManager(
    private val context: Context,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onMessageReceived: (String, String) -> Unit,
    private val onPeerDisconnected: (String) -> Unit
) {
    companion object {
        private const val TAG = "WiFiDirectMesh"
        private const val PORT = 8765
        private const val BUFFER_SIZE = 4096
    }

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private val connectedPeers = ConcurrentHashMap<String, PeerConnection>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null
    private var isRunning = false
    private var isGroupOwner = false
    private var groupOwnerAddress: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d(TAG, "WiFi P2P state: ${if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "ON" else "OFF"}")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeerList()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    }
                }
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)

        startServer()
        Log.d(TAG, "WiFi Direct manager started")
    }

    fun stop() {
        isRunning = false
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) { }
        serverJob?.cancel()
        connectedPeers.values.forEach { it.close() }
        connectedPeers.clear()
        wifiP2pManager?.removeGroup(channel, null)
        scope.cancel()
        Log.d(TAG, "WiFi Direct manager stopped")
    }

    fun startDiscovery() {
        val ch = channel ?: return
        wifiP2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WiFi Direct discovery started")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "WiFi Direct discovery failed: $reason")
            }
        })
    }

    private fun requestPeerList() {
        val ch = channel ?: return
        wifiP2pManager?.requestPeers(ch) { peerList ->
            peerList.deviceList.forEach { device ->
                val peer = Peer(
                    id = device.deviceAddress,
                    name = device.deviceName,
                    connectionType = ConnectionType.WIFI_DIRECT,
                    address = device.deviceAddress
                )
                onPeerDiscovered(peer)
            }
        }
    }

    fun connectToPeer(peer: Peer) {
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
        }
        wifiP2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to: ${peer.name}")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed to: ${peer.name}, reason: $reason")
            }
        })
    }

    private fun requestConnectionInfo() {
        val ch = channel ?: return
        wifiP2pManager?.requestConnectionInfo(ch) { info ->
            if (info != null) {
                isGroupOwner = info.groupFormed && info.isGroupOwner
                groupOwnerAddress = info.groupOwnerAddress?.hostAddress

                if (!isGroupOwner && groupOwnerAddress != null) {
                    // Client - connect to group owner
                    scope.launch {
                        connectToGroupOwner(groupOwnerAddress!!)
                    }
                }
            }
        }
    }

    private fun startServer() {
        serverJob = scope.launch {
            try {
                val serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server listening on port $PORT")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket.accept()
                        val address = clientSocket.inetAddress.hostAddress ?: continue
                        Log.d(TAG, "Client connected from: $address")
                        handleConnection(clientSocket, address)
                    } catch (e: IOException) {
                        if (isRunning) Log.e(TAG, "Accept error", e)
                    }
                }
                serverSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Server socket error", e)
            }
        }
    }

    private suspend fun connectToGroupOwner(address: String) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(address, PORT), 5000)
            Log.d(TAG, "Connected to group owner: $address")
            handleConnection(socket, address)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect to group owner", e)
        }
    }

    private fun handleConnection(socket: Socket, address: String) {
        val connection = PeerConnection(socket, address)
        connectedPeers[address] = connection

        scope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            val messageBuilder = StringBuilder()
            try {
                val input = socket.getInputStream()
                while (isRunning && !socket.isClosed) {
                    val bytes = input.read(buffer)
                    if (bytes == -1) break
                    val received = String(buffer, 0, bytes)
                    messageBuilder.append(received)

                    while (messageBuilder.contains("\n")) {
                        val idx = messageBuilder.indexOf("\n")
                        val msg = messageBuilder.substring(0, idx)
                        messageBuilder.delete(0, idx + 1)
                        if (msg.isNotBlank()) {
                            onMessageReceived(address, msg)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Connection lost with: $address")
            } finally {
                connectedPeers.remove(address)
                connection.close()
                onPeerDisconnected(address)
            }
        }
    }

    fun sendMessage(peerId: String, message: String): Boolean {
        val connection = connectedPeers[peerId] ?: return false
        return connection.send(message)
    }

    fun broadcastMessage(message: String, excludePeerId: String? = null) {
        connectedPeers.forEach { (id, conn) ->
            if (id != excludePeerId) {
                conn.send(message)
            }
        }
    }

    fun getConnectedPeerIds(): Set<String> = connectedPeers.keys.toSet()

    private class PeerConnection(
        private val socket: Socket,
        val address: String
    ) {
        private val output = try { socket.getOutputStream() } catch (e: IOException) { null }

        fun send(message: String): Boolean {
            return try {
                output?.write((message + "\n").toByteArray())
                output?.flush()
                true
            } catch (e: IOException) {
                false
            }
        }

        fun close() {
            try { socket.close() } catch (e: IOException) { }
        }
    }
}