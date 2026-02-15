package com.daricheh.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.gson.Gson
import com.daricheh.app.MeshApplication
import com.daricheh.app.model.ConnectionType
import com.daricheh.app.model.DeviceType
import com.daricheh.app.model.Peer
import com.daricheh.app.model.UserProfile
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class LocalNetworkManager(
    private val context: Context,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onMessageReceived: (String, String) -> Unit,
    private val onPeerDisconnected: (String) -> Unit
) {
    companion object {
        private const val SERVICE_TYPE = "_daricheh._tcp."
        private const val SERVICE_NAME_PREFIX = "MeshMsg_"
    }

    private val app = MeshApplication.instance
    private val gson = Gson()
    private var nsdManager: NsdManager? = null
    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 0
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var isDiscovering = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

        scope.launch {
            startServer()
            if (localPort > 0) {
                delay(500)
                withContext(Dispatchers.Main) { registerService() }
            }
        }
    }

    fun stop() {
        isRunning = false
        try { if (isDiscovering) discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { registrationListener?.let { nsdManager?.unregisterService(it) } } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        try { scope.cancel() } catch (_: Exception) {}
        isDiscovering = false
    }

    private fun startServer() {
        for (port in 39000..39100) {
            try {
                serverSocket = ServerSocket(port)
                localPort = port
                break
            } catch (_: Exception) { }
        }

        if (localPort == 0) return

        scope.launch {
            while (isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    val addr = client.inetAddress?.hostAddress ?: continue
                    val tempId = "incoming_$addr"
                    handleConnection(client, tempId)
                } catch (_: Exception) { if (isRunning) delay(1000) }
            }
        }
    }

    private fun registerService() {
        if (!isRunning || localPort == 0) return
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME_PREFIX + app.deviceId
            serviceType = SERVICE_TYPE
            port = localPort
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo) {
                scope.launch { delay(1500); withContext(Dispatchers.Main) { startDiscovery() } }
            }
            override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
        }
        try { nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) } catch (_: Exception) {}
    }

    fun startDiscovery() {
        if (!isRunning || nsdManager == null) return
        if (isDiscovering) {
            try { discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) } } catch (_: Exception) {}
            isDiscovering = false
            scope.launch { delay(2000); withContext(Dispatchers.Main) { beginDiscovery() } }
        } else {
            beginDiscovery()
        }
    }

    private fun beginDiscovery() {
        if (!isRunning) return
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String, code: Int) { isDiscovering = false }
            override fun onStopDiscoveryFailed(t: String, code: Int) { isDiscovering = false }
            override fun onDiscoveryStarted(t: String) { isDiscovering = true }
            override fun onDiscoveryStopped(t: String) { isDiscovering = false }
            override fun onServiceFound(si: NsdServiceInfo) {
                val name = si.serviceName ?: return
                if (name.startsWith(SERVICE_NAME_PREFIX)) {
                    val devId = name.removePrefix(SERVICE_NAME_PREFIX)
                    if (devId != app.deviceId && !peerConnections.containsKey(devId)) {
                        try { nsdManager?.resolveService(si, resolveListener) } catch (_: Exception) {}
                    }
                }
            }
            override fun onServiceLost(si: NsdServiceInfo) {
                val devId = (si.serviceName ?: "").removePrefix(SERVICE_NAME_PREFIX)
                peerConnections.remove(devId)?.close()
                onPeerDisconnected(devId)
            }
        }
        try { nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) } catch (_: Exception) {}
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(s: NsdServiceInfo, c: Int) {}
        override fun onServiceResolved(s: NsdServiceInfo) {
            val devId = (s.serviceName ?: "").removePrefix(SERVICE_NAME_PREFIX)
            val host = s.host?.hostAddress ?: return
            val port = s.port
            if (!peerConnections.containsKey(devId)) {
                scope.launch { connectToHost(host, port, devId) }
            }
        }
    }

    private suspend fun connectToHost(host: String, port: Int, deviceId: String) {
        if (peerConnections.containsKey(deviceId)) return
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            handleConnection(socket, deviceId)

            // Send handshake
            delay(100)
            val profile = UserProfile(app.deviceId, app.username, app.phoneNumber)
            sendMessage(deviceId, "HANDSHAKE:" + gson.toJson(profile))
        } catch (_: Exception) {}
    }

    private fun handleConnection(socket: Socket, initialId: String) {
        val conn = PeerConnection(socket)
        peerConnections[initialId] = conn

        scope.launch {
            val buffer = ByteArray(8192)
            val sb = StringBuilder()
            var currentId = initialId
            val handshakeDone = AtomicBoolean(false)

            try {
                val input = socket.getInputStream()
                while (isRunning && !socket.isClosed) {
                    val bytes = input.read(buffer)
                    if (bytes == -1) break
                    sb.append(String(buffer, 0, bytes))

                    while (sb.contains("\n")) {
                        val idx = sb.indexOf("\n")
                        val line = sb.substring(0, idx)
                        sb.delete(0, idx + 1)
                        if (line.isBlank()) continue

                        if (line.startsWith("HANDSHAKE:")) {
                            try {
                                val json = line.removePrefix("HANDSHAKE:")
                                val profile = gson.fromJson(json, UserProfile::class.java)
                                val realId = profile.deviceId

                                // FIX: Use named arguments to avoid Type Mismatch error
                                val peer = Peer(
                                    id = realId,
                                    name = profile.name,
                                    phoneNumber = profile.phoneNumber,
                                    connectionType = ConnectionType.LOCAL_NETWORK,
                                    deviceType = DeviceType.PHONE,
                                    hasApp = true
                                )
                                onPeerDiscovered(peer)

                                if (!handshakeDone.getAndSet(true)) {
                                    val myProfile = UserProfile(app.deviceId, app.username, app.phoneNumber)
                                    val reply = "HANDSHAKE:" + gson.toJson(myProfile)
                                    conn.send(reply)
                                }

                                if (realId != currentId) {
                                    peerConnections.remove(currentId)
                                    peerConnections[realId] = conn
                                    currentId = realId
                                }
                            } catch (_: Exception) {}
                        } else {
                            onMessageReceived(currentId, line)
                        }
                    }
                }
            } catch (_: Exception) {}

            peerConnections.remove(currentId)
            conn.close()
            onPeerDisconnected(currentId)
        }
    }

    fun sendMessage(peerId: String, message: String): Boolean {
        return peerConnections[peerId]?.send(message) ?: false
    }

    fun broadcastMessage(message: String, excludePeerId: String? = null) {
        peerConnections.forEach { (id, conn) ->
            if (id != excludePeerId && !id.startsWith("incoming_")) {
                conn.send(message)
            }
        }
    }

    fun getConnectedPeerIds() = peerConnections.keys.filter { !it.startsWith("incoming_") }.toSet()

    private class PeerConnection(private val socket: Socket) {
        private val output = try { socket.getOutputStream() } catch (_: Exception) { null }
        private val lock = Any()

        fun send(msg: String): Boolean {
            return try {
                synchronized(lock) {
                    output?.write((msg + "\n").toByteArray())
                    output?.flush()
                }
                true
            } catch (_: Exception) { false }
        }
        fun close() { try { socket.close() } catch (_: Exception) {} }
    }
}