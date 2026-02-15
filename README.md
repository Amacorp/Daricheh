# 4iran - Secure Messaging App

A secure, resilient messaging application for Android built with Kotlin. 4iran is designed to ensure reliable message delivery even in challenging network conditions by supporting multiple communication protocols with automatic fallback mechanisms.

## Features

### Multi-Protocol Support
The app supports multiple messaging protocols to ensure reliable message delivery:

- **WebSocket** - Persistent duplex communication
- **HTTP/HTTPS** - REST-based fallback transport
- **HTTP/2** - Multiplexed streaming protocol
- **HTTP/3 (QUIC)** - Modern UDP-based transport
- **gRPC** - High-performance RPC framework
- **TCP Direct Socket** - Low-level reliable transport
- **UDP Datagram** - Fast connectionless transport
- **TLS-encrypted raw socket** - Encrypted direct communication
- **MQTT** - Lightweight pub/sub messaging
- **XMPP** - Extensible messaging protocol

### Protocol Switching & Filtering Detection
- Automatic protocol switching when one is blocked
- Continuous monitoring of network conditions
- Detection of national internet filtering
- Dynamic protocol priority adjustment
- Fallback to alternative protocols without user intervention

### P2P Communication & Mesh Network
- Peer-to-Peer (P2P) architecture for direct device-to-device communication
- WiFi Direct support for local network messaging
- Mesh network capability - devices can relay messages to reach destination
- No central server required for P2P mode
- Enhanced privacy through decentralized communication

### Bluetooth Integration
- Bluetooth Low Energy (BLE) as last-resort communication method
- Automatic Bluetooth activation when network protocols fail
- Background Bluetooth scanning for nearby devices
- Message relay through Bluetooth mesh
- Works within ~10 meter range

### End-to-End Encryption
- AES-256-GCM for message encryption
- RSA-2048 for key exchange
- Tink cryptographic library for secure key management
- Android Keystore for key storage
- Self-destructing messages support
- Private keys never leave the device

### User Interface
- Clean, minimalistic WhatsApp-like design
- White background with soft blue accent colors
- English language interface
- Material Design 3 components
- Dark mode support
- Responsive Compose UI

## Architecture

### Tech Stack
- **Language**: Kotlin
- **Framework**: Android SDK 34 (minSdk 24)
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Hilt
- **Database**: Room with SQLite
- **Networking**: 
  - OkHttp (HTTP/HTTPS/HTTP2/HTTP3)
  - Socket.IO (WebSocket)
  - Netty (TCP/UDP)
  - gRPC
  - Paho MQTT
  - Smack XMPP
- **Encryption**: Tink + BouncyCastle
- **Bluetooth**: Blessed Android library
- **P2P**: WiFi Direct + WebRTC

### Project Structure
```
app/src/main/java/com/fouriran/securemessaging/
├── data/
│   ├── dao/              # Room Data Access Objects
│   ├── database/         # Room Database
│   ├── model/            # Data models (Message, Contact, Conversation)
│   └── repository/       # Repository classes
├── di/                   # Dependency Injection (Hilt modules)
├── encryption/           # Encryption/Decryption manager
├── network/
│   ├── manager/          # ProtocolManager for switching
│   └── protocol/         # Protocol implementations
│       ├── websocket/
│       ├── http/
│       ├── tcp/
│       ├── udp/
│       ├── mqtt/
│       ├── bluetooth/
│       └── p2p/
├── service/              # Background services
├── receiver/             # Broadcast receivers
└── ui/
    ├── theme/            # App theme and colors
    ├── main/             # Main chat list screen
    ├── chat/             # Chat conversation screen
    ├── contacts/         # Contacts list screen
    └── settings/         # Settings screen
```

## Setup & Installation

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK with API 34
- Kotlin 1.9.20 or higher

### Build Instructions

1. Clone the repository:
```bash
git clone https://github.com/yourusername/4iran.git
cd 4iran
```

2. Open in Android Studio:
   - File → Open → Select the project directory
   - Wait for Gradle sync to complete

3. Build the project:
   - Build → Make Project (Ctrl+F9)
   - Or use: `./gradlew assembleDebug`

4. Run on device/emulator:
   - Run → Run 'app' (Shift+F10)
   - Or use: `./gradlew installDebug`

### Gradle Configuration

The project uses multiple Maven repositories for dependency resolution:
- Maven Central (primary)
- Google Maven
- JCenter (legacy)
- Iranian Maven mirrors
- Chinese Maven mirrors (Aliyun, Huawei, Tencent)

This ensures dependencies can be resolved even with network restrictions.

## Configuration

### Server Endpoints
Configure server endpoints in each protocol implementation:
- `WebSocketProtocol.kt` - WebSocket servers
- `HttpProtocol.kt` - HTTP REST API servers
- `TcpProtocol.kt` - TCP socket servers
- etc.

### Encryption Keys
The app automatically generates encryption keys on first launch:
- RSA key pair stored in Android Keystore
- AES keys managed by Tink
- No manual key configuration required

### Permissions
The app requires the following permissions:
- `INTERNET` - Network communication
- `BLUETOOTH` / `BLUETOOTH_ADMIN` - Bluetooth messaging
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - WiFi Direct
- `FOREGROUND_SERVICE` - Background messaging service
- `POST_NOTIFICATIONS` - Message notifications
- `CAMERA` - Profile pictures
- `RECORD_AUDIO` - Voice messages

## Usage

### Starting a Chat
1. Tap the floating action button (+) on the main screen
2. Select a contact from the list
3. Start messaging

### Protocol Status
- View protocol status in Settings → Protocol Status
- Green = Healthy and available
- Yellow = Degraded but working
- Red = Blocked or unavailable

### Security Features
- All messages are encrypted by default
- Verify contacts by tapping the verified badge
- Self-destructing messages can be set per message

## Security Considerations

### End-to-End Encryption
- Messages are encrypted on the sender's device
- Only the recipient can decrypt messages
- Private keys are stored in hardware-backed keystore when available
- Forward secrecy through ephemeral keys

### Protocol Security
- TLS 1.3 for all encrypted connections
- Certificate pinning for production servers
- Automatic downgrade protection

### Local Security
- Encrypted SharedPreferences for sensitive data
- SQLCipher for database encryption (can be added)
- Root detection (can be added)
- Screenshot protection (can be added)

## Development

### Adding a New Protocol
1. Create a new class implementing `MessageProtocol` interface
2. Add protocol type to `Message.ProtocolType` enum
3. Register in `ProtocolManager.initializeProtocols()`
4. Add default priority in `ProtocolStatus.getDefaultPriority()`

### Customizing UI
- Colors: `ui/theme/Theme.kt`
- Typography: `ui/theme/Type.kt`
- Components: Individual screen files in `ui/` package

### Testing
Run unit tests:
```bash
./gradlew test
```

Run instrumentation tests:
```bash
./gradlew connectedAndroidTest
```

## Troubleshooting

### Build Issues
- Ensure JDK 17 is set in Android Studio
- Clear Gradle cache: `./gradlew clean`
- Invalidate caches: File → Invalidate Caches

### Connection Issues
- Check protocol status in Settings
- Verify internet connectivity
- Try switching protocols manually
- Check if ports are blocked by firewall

### Bluetooth Issues
- Ensure Bluetooth is enabled
- Grant location permissions (required for BLE scanning)
- Keep devices within 10 meters

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see LICENSE file for details.

## Disclaimer

This application is designed for secure communication research and educational purposes. Users are responsible for complying with local laws and regulations regarding encrypted communications.

## Acknowledgments

- [Socket.IO](https://socket.io/) for WebSocket client
- [OkHttp](https://square.github.io/okhttp/) for HTTP client
- [Netty](https://netty.io/) for TCP/UDP
- [Tink](https://github.com/google/tink) for cryptography
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for UI
- [Hilt](https://dagger.dev/hilt/) for dependency injection
