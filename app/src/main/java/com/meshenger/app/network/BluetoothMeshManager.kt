package com.daricheh.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.daricheh.app.MeshApplication
import com.daricheh.app.model.ConnectionType
import com.daricheh.app.model.DeviceType
import com.daricheh.app.model.Peer
import com.daricheh.app.model.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BluetoothMeshManager(
    private val context: Context,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onMessageReceived: (String, String) -> Unit,
    private val onPeerDisconnected: (String) -> Unit
) {
    companion object {
        private const val SERVICE_NAME = "MeshMessenger"
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        private const val BUFFER_SIZE = 4096
    }

    private val app = MeshApplication.instance
    private val gson = Gson()
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val peerConnections = ConcurrentHashMap<String, ConnectedThread>()
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private var serverThread: AcceptThread? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Track handshake state per peer
    private val handshakeDone = ConcurrentHashMap<String, Boolean>()

    val isAvailable: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    private fun isPhoneOrTablet(device: BluetoothDevice): Boolean {
        val dc = device.bluetoothClass ?: return true
        return when (dc.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> true
            BluetoothClass.Device.Major.COMPUTER -> true
            BluetoothClass.Device.Major.UNCATEGORIZED -> true
            else -> false
        }
    }

    private fun getDeviceType(device: BluetoothDevice): DeviceType {
        val dc = device.bluetoothClass ?: return DeviceType.UNKNOWN
        return when (dc.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> DeviceType.PHONE
            BluetoothClass.Device.Major.COMPUTER -> DeviceType.TABLET
            else -> DeviceType.UNKNOWN
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    if (!isPhoneOrTablet(device)) return

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val name = device.name ?: return // Skip unnamed devices
                    val address = device.address

                    // Store for later connection
                    discoveredDevices[address] = device

                    val peer = Peer(
                        id = address,
                        name = name,
                        connectionType = ConnectionType.BLUETOOTH,
                        signalStrength = rssi,
                        address = address,
                        deviceType = getDeviceType(device)
                    )

                    app.log("BT: Found device: $name ($address) rssi=$rssi")
                    onPeerDiscovered(peer)

                    // Auto-connect if not already connected
                    if (!peerConnections.containsKey(address)) {
                        app.log("BT: Auto-connecting to $name ($address)...")
                        connectToPeer(peer)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    app.log("BT: Discovery finished. Found ${discoveredDevices.size} devices")
                    // Restart discovery after a delay to keep finding devices
                    if (isRunning) {
                        scope.launch {
                            delay(15_000)
                            if (isRunning && peerConnections.isEmpty()) {
                                app.log("BT: No connections, restarting discovery...")
                                startDiscovery()
                            }
                        }
                    }
                }
            }
        }
    }

    fun start() {
        if (!isAvailable || isRunning) {
            app.log("BT: Cannot start. Available=$isAvailable Running=$isRunning")
            return
        }
        isRunning = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)

        // Start server to accept incoming connections
        serverThread = AcceptThread()
        serverThread?.start()

        app.log("BT: Started. Adapter=${bluetoothAdapter?.name} Address=${bluetoothAdapter?.address}")
    }

    fun stop() {
        isRunning = false
        try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: Exception) {}
        serverThread?.cancel()
        peerConnections.values.forEach { it.cancel() }
        peerConnections.clear()
        discoveredDevices.clear()
        handshakeDone.clear()
        scope.cancel()
        app.log("BT: Stopped")
    }

    fun startDiscovery() {
        if (!isAvailable) {
            app.log("BT: Cannot discover - not available")
            return
        }
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            val started = bluetoothAdapter?.startDiscovery() ?: false
            app.log("BT: Discovery started: $started")
        } catch (e: Exception) {
            app.log("BT: Discovery error: ${e.message}")
        }
    }

    fun connectToPeer(peer: Peer) {
        if (!isAvailable) return
        if (peerConnections.containsKey(peer.id)) {
            app.log("BT: Already connected to ${peer.id}")
            return
        }

        scope.launch {
            try {
                // Cancel discovery before connecting
                bluetoothAdapter?.cancelDiscovery()

                val device = discoveredDevices[peer.address]
                    ?: bluetoothAdapter?.getRemoteDevice(peer.address)
                    ?: run {
                        app.log("BT: Device not found: ${peer.address}")
                        return@launch
                    }

                app.log("BT: Connecting to ${peer.name} (${peer.address})...")

                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)

                try {
                    socket.connect()
                    app.log("BT: Connected to ${peer.name}!")

                    val thread = ConnectedThread(socket, peer.address)
                    peerConnections[peer.address] = thread
                    thread.start()

                    // Send handshake
                    sendHandshake(peer.address)

                } catch (e: IOException) {
                    app.log("BT: Connect failed to ${peer.name}: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}

                    // Try fallback method
                    tryFallbackConnect(device, peer)
                }
            } catch (e: Exception) {
                app.log("BT: Connect error: ${e.message}")
            }
        }
    }

    private fun tryFallbackConnect(device: BluetoothDevice, peer: Peer) {
        try {
            app.log("BT: Trying fallback connect to ${peer.name}...")
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val socket = method.invoke(device, 1) as BluetoothSocket
            socket.connect()
            app.log("BT: Fallback connected to ${peer.name}!")

            val thread = ConnectedThread(socket, peer.address)
            peerConnections[peer.address] = thread
            thread.start()

            sendHandshake(peer.address)
        } catch (e: Exception) {
            app.log("BT: Fallback also failed: ${e.message}")
        }
    }

    private fun sendHandshake(peerId: String) {
        if (handshakeDone[peerId] == true) return

        val profile = UserProfile(app.deviceId, app.username, app.phoneNumber)
        val msg = "HANDSHAKE:" + gson.toJson(profile)
        val sent = sendRaw(peerId, msg)
        app.log("BT: Sent handshake to $peerId: $sent")
    }

    private fun processReceivedData(peerId: String, data: String): String {
        if (data.startsWith("HANDSHAKE:")) {
            try {
                val profile = gson.fromJson(data.removePrefix("HANDSHAKE:"), UserProfile::class.java)
                val realId = profile.deviceId

                app.log("BT: Handshake from ${profile.name} ($realId)")

                // Remap if needed
                if (realId != peerId) {
                    val conn = peerConnections.remove(peerId)
                    if (conn != null) peerConnections[realId] = conn
                    app.log("BT: Remapped $peerId -> $realId")
                }

                val peer = Peer(
                    id = realId,
                    name = profile.name,
                    phoneNumber = profile.phoneNumber,
                    connectionType = ConnectionType.BLUETOOTH,
                    deviceType = DeviceType.PHONE,
                    hasApp = true,
                    address = peerId // Keep BT address
                )
                onPeerDiscovered(peer)

                // Reply handshake if we haven't
                if (handshakeDone[realId] != true) {
                    handshakeDone[realId] = true
                    val myProfile = UserProfile(app.deviceId, app.username, app.phoneNumber)
                    val reply = "HANDSHAKE:" + gson.toJson(myProfile)
                    sendRaw(realId, reply)
                    app.log("BT: Sent handshake reply to $realId")
                } else {
                    handshakeDone[realId] = true
                }

                app.log("BT: ✅ Handshake DONE with ${profile.name} ($realId)")
                return realId
            } catch (e: Exception) {
                app.log("BT: Handshake error: ${e.message}")
            }
        } else {
            app.log("BT: MSG from $peerId (${data.take(80)})")
            onMessageReceived(peerId, data)
        }
        return peerId
    }

    private fun sendRaw(peerId: String, message: String): Boolean {
        val thread = peerConnections[peerId] ?: return false
        return thread.write((message + "\n").toByteArray())
    }

    fun sendMessage(peerId: String, message: String): Boolean {
        val result = sendRaw(peerId, message)
        if (!result) {
            app.log("BT: Send failed to $peerId. Connected: ${peerConnections.keys}")
        }
        return result
    }

    fun broadcastMessage(message: String, excludePeerId: String? = null) {
        val targets = peerConnections.keys.filter { it != excludePeerId }
        targets.forEach { id ->
            try { sendRaw(id, message) } catch (_: Exception) {}
        }
    }

    fun getConnectedPeerIds(): Set<String> = peerConnections.keys.toSet()

    // Accept incoming BT connections
    private inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null

        init {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                app.log("BT: Server socket created")
            } catch (e: IOException) {
                app.log("BT: Server socket error: ${e.message}")
            }
        }

        override fun run() {
            while (isRunning) {
                try {
                    val socket = serverSocket?.accept(30000) ?: continue
                    val device = socket.remoteDevice
                    val address = device.address

                    if (!peerConnections.containsKey(address)) {
                        app.log("BT: Incoming from ${device.name} ($address)")
                        val thread = ConnectedThread(socket, address)
                        peerConnections[address] = thread
                        thread.start()

                        // Don't send handshake yet - wait for theirs
                    } else {
                        socket.close()
                    }
                } catch (e: IOException) {
                    if (isRunning && e.message?.contains("closed") != true) {
                        // Timeout is normal, just retry
                    }
                }
            }
        }

        fun cancel() {
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }

    // Handle connected BT socket
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private var peerId: String
    ) : Thread() {

        private val input = try { socket.inputStream } catch (_: Exception) { null }
        private val output = try { socket.outputStream } catch (_: Exception) { null }

        override fun run() {
            val buffer = ByteArray(BUFFER_SIZE)
            val sb = StringBuilder()

            app.log("BT: Reader started for $peerId")

            while (isRunning && socket.isConnected) {
                try {
                    val bytes = input?.read(buffer) ?: break
                    if (bytes <= 0) continue

                    sb.append(String(buffer, 0, bytes))

                    while (sb.contains("\n")) {
                        val idx = sb.indexOf("\n")
                        val line = sb.substring(0, idx)
                        sb.delete(0, idx + 1)

                        if (line.isNotBlank()) {
                            val newId = processReceivedData(peerId, line)
                            if (newId != peerId) {
                                peerId = newId
                            }
                        }
                    }
                } catch (e: IOException) {
                    app.log("BT: Read error from $peerId: ${e.message}")
                    break
                }
            }

            peerConnections.remove(peerId)
            handshakeDone.remove(peerId)
            onPeerDisconnected(peerId)
            app.log("BT: Disconnected from $peerId")
        }

        fun write(bytes: ByteArray): Boolean {
            return try {
                output?.write(bytes)
                output?.flush()
                true
            } catch (e: IOException) {
                app.log("BT: Write error to $peerId: ${e.message}")
                false
            }
        }

        fun cancel() {
            try { socket.close() } catch (_: Exception) {}
            peerConnections.remove(peerId)
        }
    }
}