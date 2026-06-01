# NetSentry AI

<div align="center">

![NetSentry Logo](https://img.shields.io/badge/NetSentry-AI%20Network%20Security-blue?style=for-the-badge&logo=shield)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-61DAFB?style=for-the-badge&logo=jetpackcompose)

**Advanced Android Network Security Firewall with AI-Powered Traffic Analysis**

[Features](#features) • [Architecture](#architecture) • [Installation](#installation) • [Configuration](#configuration) • [Building](#building)

</div>

---

## Overview

NetSentry AI is a professional-grade Android network security application that combines local VPN-based packet inspection with Google Gemini AI analysis to provide comprehensive network traffic monitoring, threat detection, and bandwidth management capabilities.

The application intercepts and analyzes all network traffic on the device, applying configurable firewall rules, monitoring bandwidth consumption per application, and leveraging AI to identify potential security risks, trackers, and anomalous behavior patterns.

## Features

### 🔒 Core Security Engine

- **VPN-Based Packet Inspection**: Intercepts all network traffic using Android's VpnService API
- **Real-Time Traffic Analysis**: Monitors incoming and outgoing packets with protocol detection (TCP, UDP, DNS, ICMP)
- **DNS Query Sniffing**: Captures and logs DNS resolution requests to identify domain lookups
- **TLS SNI Parsing**: Extracts Server Name Indication from HTTPS handshakes (placeholder implementation)
- **Application Attribution**: Maps network connections to their source applications via UID tracking

### 🛡️ Firewall Management

- **Per-App Rules**: Configure ALLOWED, WHITELISTED, or BLOCKED states for each application
- **Bandwidth Quotas**: Set daily data limits per application with automatic enforcement
- **Quota Alerts**: Real-time notifications when applications exceed configured limits
- **Quick Actions**: Extend limits, sever connections, or activate no-data drop mode
- **In-Memory Rule Caching**: High-performance rule evaluation for minimal VPN latency

### 🤖 AI-Powered Analysis

- **Gemini Integration**: Cloud-based AI analysis using Google Gemini 3.5 Flash model
- **Local AI Core Detection**: Automatic detection of on-device Gemini Nano capability (Pixel 8+, Samsung S24+)
- **BYOK Support**: Bring Your Own Key configuration for custom API access
- **Security Audit Reports**: AI-generated analysis identifying trackers, telemetry, and security risks
- **Anomaly Detection**: Pattern recognition for unusual traffic volume spikes

### 📊 Dashboard & Monitoring

- **Live Traffic Stats**: Real-time visualization of scanned, blocked, and total bytes
- **Traffic Filtering**: Search and filter logs by app name, domain, IP, protocol, or status
- **Protocol Filtering**: Filter by TCP, UDP, DNS, or HTTPS traffic types
- **Quota Tracking**: Visual progress indicators for daily bandwidth consumption
- **Log Management**: Persistent traffic logging with Room database storage

### 🎨 Modern UI/UX

- **Material 3 Design**: Contemporary Android design language with dynamic theming
- **Jetpack Compose UI**: Declarative UI with smooth animations and transitions
- **Edge-to-Edge Support**: Modern display cutout and gesture navigation compatibility
- **Dark/Light Themes**: Automatic theme adaptation based on system settings
- **Responsive Layout**: Adaptive navigation with Dashboard, Rules, and AI Core tabs

## Architecture

```
NetSentry AI
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt           # Main entry point & UI orchestration
│   │   ├── ai/
│   │   │   └── AiAnalysisEngine.kt   # Gemini AI integration & analysis
│   │   ├── db/
│   │   │   ├── Models.kt             # Room entities & DAO definitions
│   │   │   └── NetSentryDatabase.kt  # Database singleton & configuration
│   │   ├── ui/
│   │   │   ├── TrafficViewModel.kt   # MVVM ViewModel for state management
│   │   │   └── theme/                # Material 3 theming & colors
│   │   └── vpn/
│   │       ├── LocalVpnService.kt    # Android VPN service implementation
│   │       └── TrafficRuleManager.kt # Rule engine & quota management
│   └── src/main/res/                 # Resources (layouts, strings, drawables)
├── build.gradle.kts                  # Project-level build configuration
└── settings.gradle.kts               # Project settings & dependency resolution
```

### Component Details

#### LocalVpnService
The core VPN service that:
- Establishes a local VPN interface for packet interception
- Parses IPv4/IPv6 headers to extract protocol and addressing information
- Sniffs DNS queries to identify domain resolutions
- Forwards packets to TrafficRuleManager for filtering decisions
- Runs a simulation traffic generator for demo purposes

#### TrafficRuleManager
The rule evaluation engine that:
- Maintains in-memory rule cache for fast packet processing
- Evaluates firewall rules (ALLOWED, BLOCKED, WHITELISTED)
- Tracks bandwidth consumption per application
- Enforces quota limits with throttling support
- Emits quota exceeded events for UI notifications

#### AiAnalysisEngine
The AI integration layer that:
- Constructs prompts for Gemini security analysis
- Manages API key configuration (BuildConfig + BYOK)
- Detects local AI Core hardware support
- Parses JSON responses from Gemini API
- Provides fallback error handling

#### NetSentryDatabase
Room database with:
- **AppRule**: Firewall rules with quota configuration
- **TrafficLog**: Historical traffic records (400 entry limit)
- **Flow-based queries**: Reactive UI updates via Kotlin Flow

## Installation

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34 (API 34) or higher
- Gradle 8.4+
- Kotlin 1.9.x
- Java 17 (JDK)

### Dependencies

The project uses the following key dependencies:

```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Testing
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
```

## Configuration

### API Key Setup

NetSentry supports two methods for Gemini API access:

#### Method 1: BuildConfig (Recommended for production)

Add your API key to `local.properties`:

```properties
GEMINI_API_KEY=your_api_key_here
```

Or configure via Gradle secrets plugin (see `build.gradle.kts`).

#### Method 2: BYOK (Bring Your Own Key)

Users can configure their own API key through the UI:

1. Tap the Settings icon in the top-right corner
2. Enter your Gemini API key
3. Click "Apply Credentials"

### Supported Devices for Local AI

Local AI Core (on-device Gemini Nano) is supported on:
- Google Pixel 8, 8 Pro, 8a
- Google Pixel 9, 9 Pro, 9 Pro XL
- Samsung Galaxy S24, S24+, S24 Ultra
- Samsung Galaxy S25, S25+, S25 Ultra

Other devices will use cloud-based Gemini API.

## Building

### Debug Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Signing

To create a signed release build:

1. Place your keystore file in the project root
2. Configure signing in `build.gradle.kts` or `gradle.properties`
3. Build with:

```bash
./gradlew signRelease
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

### Linting

```bash
./gradlew lint
```

## Usage

### Starting the Firewall

1. Grant VPN permission when prompted
2. Tap "Start Protection" on the dashboard
3. The VPN service runs in the foreground with a persistent notification

### Managing Firewall Rules

1. Navigate to the **RULES** tab
2. Tap an application to view its current rule
3. Select ALLOWED, WHITELISTED, or BLOCKED
4. Configure bandwidth quotas by tapping the quota icon

### AI Security Analysis

1. Navigate to the **AI CORE** tab
2. Enter a security question (e.g., "What trackers are active?")
3. Tap "Run Security Audit"
4. Review the AI-generated analysis report

### Quota Management

When an application exceeds its quota:
- **Extend Limit**: Add 10MB or 50MB to the quota
- **End Connection**: Block all traffic for the application
- **No Data Mode**: Drop packets silently without notification

## Permissions

NetSentry requires the following permissions:

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network traffic analysis and AI API calls |
| `QUERY_ALL_PACKAGES` | Application metadata for traffic attribution |
| `FOREGROUND_SERVICE` | Continuous VPN monitoring |
| `FOREGROUND_SERVICE_SPECIAL_USE` | VPN service priority |
| `POST_NOTIFICATIONS` | Status and alert notifications |

## Security Considerations

- **VPN Trust**: NetSentry creates a local VPN that does not route traffic through external servers
- **Data Privacy**: Traffic logs are stored locally on the device
- **API Security**: API keys are stored in encrypted shared preferences
- **No Root Required**: The application works on standard Android installations

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Google Android](https://developer.android.com) for the VPN Service API
- [Google Gemini](https://gemini.google.com) for AI capabilities
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for modern UI
- [Room](https://developer.android.com/training/data-storage/room) for local persistence

---

<div align="center">

**NetSentry AI** - *Secure Your Network, Empower Your Privacy*

</div>