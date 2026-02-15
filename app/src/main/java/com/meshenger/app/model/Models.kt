package com.daricheh.app.model

import java.util.UUID

data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderPhone: String = "",
    val recipientId: String,
    val recipientPhone: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var hopCount: Int = 0,
    val maxHops: Int = 10,
    var status: MessageStatus = MessageStatus.PENDING,
    val routePath: MutableList<String> = mutableListOf()
) {
    fun incrementHop(nodeId: String): Boolean {
        hopCount++
        routePath.add(nodeId)
        return hopCount <= maxHops
    }
}

enum class MessageStatus {
    PENDING, SENT, DELIVERED, FAILED
}

data class Peer(
    val id: String,
    var name: String,
    var phoneNumber: String = "",
    var connectionType: ConnectionType,
    var lastSeen: Long = System.currentTimeMillis(),
    var hasInternet: Boolean = false,
    var signalStrength: Int = 0,
    var address: String = "",
    var deviceType: DeviceType = DeviceType.PHONE,
    var hasApp: Boolean = false
) {
    fun isAlive(): Boolean {
        return System.currentTimeMillis() - lastSeen < 120_000L
    }
}

enum class ConnectionType {
    BLUETOOTH, WIFI_DIRECT, LOCAL_NETWORK, INTERNET, UNKNOWN
}

enum class DeviceType {
    PHONE, TABLET, TV, COMPUTER, WATCH, UNKNOWN
}

data class Conversation(
    val peerId: String,
    val peerName: String,
    val peerPhone: String = "",
    val messages: MutableList<MeshMessage> = mutableListOf(),
    var lastMessageTime: Long = 0,
    var unreadCount: Int = 0,
    var isOnline: Boolean = false
) {
    fun addMessage(message: MeshMessage) {
        if (messages.none { it.id == message.id }) {
            messages.add(message)
            lastMessageTime = message.timestamp
        }
    }

    val lastMessage: MeshMessage?
        get() = messages.lastOrNull()
}

data class RoutingTableEntry(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val connectionType: ConnectionType,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class Contact(
    val name: String,
    val phoneNumber: String,
    var hasApp: Boolean = false,
    var peerId: String? = null,
    var isOnline: Boolean = false
)

data class UserProfile(
    val deviceId: String,
    val name: String,
    val phoneNumber: String,
    val appVersion: String = "1.0"
)