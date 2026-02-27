# Zikir Master Pro

A modern Islamic digital counter application for Android with Islamic-themed backgrounds and intuitive design.

## Technical Overview

### Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle 8.4.0
- **Target SDK**: 35
- **Minimum SDK**: 24

### Key Features
- **Digital Counter**: Real-time counter with monospace display
- **Dynamic Backgrounds**: Random Islamic-themed images from Unsplash on each app launch
- **Smart Text Color**: Automatic text color adjustment based on background luminance for optimal readability
- **Persistent Storage**: Save counter values with timestamps in Turkish locale format (dd/MM/yyyy HH:mm:ss)
- **Confirmation Dialogs**: User-friendly reset confirmation in Turkish
- **Adaptive UI**: Translucent panels with responsive design

### Technical Implementation

#### UI Components
- **Jetpack Compose**: Fully declarative UI with Material3 components
- **State Management**: `rememberSaveable` for counter persistence, `remember` for UI state
- **Image Loading**: Coil library for async image loading with caching
- **Color Analysis**: Palette API for dynamic text color based on background

#### Data Handling
- **Counter Save**: Data class with value and timestamp
- **Date Formatting**: SimpleDateFormat with Turkish locale
- **List Reversal**: Latest entries displayed first

#### Networking
- **Image Service**: Unsplash API for high-quality Islamic architecture photos
- **Cache Policy**: Disk and memory caching enabled for optimal performance

### Dependencies
```gradle
androidx.compose.bom:2024.04.00
androidx.activity:activity-compose:1.9.0
androidx.palette:palette-ktx:1.0.0
io.coil-kt:coil-compose:2.5.0
```

### Build Configuration
- **Version Code**: 4
- **Version Name**: 1.0.3
- **JVM Target**: 17
- **Signing**: Release builds signed with keystore

### Permissions
- `INTERNET`: Required for loading background images

## Setup & Running

1. Open project in Android Studio
2. Select an emulator or physical device
3. Run the application

## Privacy
This application does not collect, process, or share any personal data. See [Privacy Policy](index.md) for details.

## Contact
yasergirit@gmail.com

---
© 2026 Zikir Master Pro. All rights reserved.
