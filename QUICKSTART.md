# 4iran - Quick Start Guide

## Project Overview

4iran is a secure messaging Android app with multi-protocol support, P2P communication, Bluetooth fallback, and end-to-end encryption.

## Project Structure

```
4iran/
├── app/
│   ├── src/main/java/com/fouriran/securemessaging/
│   │   ├── data/              # Database, DAOs, Models, Repositories
│   │   ├── di/                # Dependency Injection (Hilt)
│   │   ├── encryption/        # Encryption/Decryption
│   │   ├── network/           # Protocol implementations
│   │   │   ├── manager/       # ProtocolManager
│   │   │   └── protocol/      # WebSocket, HTTP, TCP, UDP, MQTT, Bluetooth, P2P
│   │   ├── service/           # Background services
│   │   ├── ui/                # Screens (Compose)
│   │   │   ├── main/          # Chat list
│   │   │   ├── chat/          # Conversation
│   │   │   ├── contacts/      # Contact list
│   │   │   ├── settings/      # Settings
│   │   │   └── theme/         # App theme
│   │   ├── SecureMessagingApp.kt
│   │   └── MainActivity.kt
│   └── src/main/res/          # Resources (layouts, strings, etc.)
├── build.gradle.kts           # Root build script
├── settings.gradle.kts        # Project settings
├── gradle.properties          # Gradle configuration
└── README.md                  # Full documentation
```

## Key Features Implemented

### 1. Multi-Protocol Support
- **WebSocket** (`WebSocketProtocol.kt`) - Real-time bidirectional communication
- **HTTP/HTTPS** (`HttpProtocol.kt`) - REST-based messaging
- **TCP** (`TcpProtocol.kt`) - Direct socket connection using Netty
- **UDP** (`UdpProtocol.kt`) - Fast datagram transport
- **MQTT** (`MqttProtocol.kt`) - Lightweight pub/sub messaging
- **Bluetooth** (`BluetoothProtocol.kt`) - BLE fallback
- **P2P** (`P2PProtocol.kt`) - WiFi Direct mesh networking

### 2. Protocol Manager (`ProtocolManager.kt`)
- Automatic protocol switching
- Priority-based protocol selection
- Health monitoring and fallback
- Filtering detection

### 3. Encryption (`EncryptionManager.kt`)
- AES-256-GCM for message encryption
- RSA-2048 for key exchange
- Tink library for secure key management
- Android Keystore integration

### 4. Database (Room)
- **Message** - Stores encrypted messages
- **Conversation** - Chat threads
- **Contact** - User contacts
- **ProtocolStatus** - Protocol health tracking

### 5. UI (Jetpack Compose)
- **MainScreen** - Chat list with connection indicator
- **ChatScreen** - Messaging interface
- **ContactsScreen** - Contact management
- **SettingsScreen** - App settings and protocol status

## Building the App

### Prerequisites
1. Android Studio Hedgehog (2023.1.1) or newer
2. JDK 17+
3. Android SDK API 34

### Steps

1. **Open in Android Studio**
   ```
   File → Open → Select /mnt/okcomputer/output/4iran
   ```

2. **Sync Gradle**
   - Wait for automatic sync
   - Or click "Sync Now" in the notification

3. **Configure SDK**
   - Copy `local.properties.template` to `local.properties`
   - Update `sdk.dir` to your Android SDK path

4. **Build**
   ```
   Build → Make Project (Ctrl+F9)
   ```
   Or use Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Run**
   - Connect Android device or start emulator
   - Click "Run" (Shift+F10)

## Configuration

### Server Endpoints
Edit protocol files to configure your servers:

```kotlin
// In WebSocketProtocol.kt
private val serverUrls = listOf(
    "wss://your-server.com",
    "wss://backup-server.com"
)

// In HttpProtocol.kt
private val serverUrls = listOf(
    "https://your-api.com",
    "https://backup-api.com"
)
```

### Protocol Priority
Adjust protocol priority in `ProtocolStatus.kt`:
```kotlin
fun getDefaultPriority(protocolType: Message.ProtocolType): Int {
    return when (protocolType) {
        Message.ProtocolType.WEBSOCKET -> 10  // Highest
        Message.ProtocolType.HTTP3 -> 9
        // ... etc
    }
}
```

## Architecture

### MVVM Pattern
```
UI (Compose) → ViewModel → Repository → DAO → Database
                    ↓
            ProtocolManager → Protocols → Network
```

### Dependency Injection
Hilt modules in `di/AppModule.kt` provide:
- Database singleton
- DAOs
- Repositories

### Protocol Interface
All protocols implement `MessageProtocol`:
```kotlin
interface MessageProtocol {
    val protocolType: Message.ProtocolType
    val isConnected: Boolean
    suspend fun connect(): Boolean
    suspend fun sendMessage(message: Message): Boolean
    fun incomingMessages(): Flow<IncomingMessage>
}
```

## Adding a New Protocol

1. Create class implementing `MessageProtocol`
2. Add type to `Message.ProtocolType` enum
3. Register in `ProtocolManager.initializeProtocols()`
4. Add default priority

## Security Notes

- All messages encrypted with AES-256-GCM
- Keys stored in Android Keystore
- Private keys never transmitted
- Certificate pinning supported
- Self-destructing messages available

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Gradle sync fails | Check internet, try different Maven mirrors |
| Build errors | Ensure JDK 17, clean and rebuild |
| Protocol fails | Check server endpoints, view status in Settings |
| Bluetooth fails | Grant location permission, enable Bluetooth |

## Next Steps

1. Implement actual server endpoints
2. Add user authentication
3. Implement message attachments
4. Add voice/video calls
5. Implement group chats
6. Add message reactions
7. Implement backup/restore

## License

MIT License - See README.md for details
