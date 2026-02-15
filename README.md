# Daricheh

**Offline Mesh Messenger** — Peer-to-peer messaging without internet connection.

## About

Daricheh (دریچه) is a decentralized messaging app that works completely offline. It uses Bluetooth and WiFi Direct to create a mesh network between nearby devices, allowing you to send messages without internet, cellular data, or central servers.

## Features

- 🔗 Mesh networking — Messages hop through devices to reach destination
- 📡 Bluetooth + WiFi Direct — Dual protocol for maximum range
- 💬 Real-time chat — Instant messaging with delivery status
- 👥 Auto discovery — Finds nearby Daricheh users automatically
- 🔒 No servers — Fully decentralized, no data collection
- 🎨 Material Design 3 — Modern, beautiful interface

## Requirements

- Android 7.0+ (API 24)
- Bluetooth and WiFi enabled
- Physical device (emulator not supported)

## Tech Stack

- Kotlin
- MVVM Architecture
- Room Database
- Bluetooth LE & WiFi Direct

## Getting Started

1. Clone the repository
   ```bash
   git clone https://github.com/Amacorp/daricheh.git

2. Open in Android Studio and sync Gradle
3. Run on two Android devices with Bluetooth/WiFi enabled

## Permissions

    Bluetooth Connect/Scan/Advertise
    Location (for WiFi Direct)
    Read Contacts (optional)

Made with 💜 in Iran
