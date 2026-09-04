# MeshCall

> **MeshCall enables communication without relying on traditional internet connectivity using device-to-device mesh communication.**

## Project Overview

MeshCall is an offline-first, peer-to-peer communication application designed to keep people connected when traditional internet or cellular networks fail. Whether you are in a remote area, at a crowded campus or event with poor reception, or dealing with an emergency situation where infrastructure is down, MeshCall bridges the gap. By establishing device-to-device connections, it forms a local mesh network that allows users to communicate directly with one another, completely offline.

## Key Features

- **Offline / Device-to-Device Mesh Communication**: Send and receive messages without internet access.
- **Nearby Device Discovery**: Automatically find and connect to other nearby MeshCall users.
- **Text Messaging**: Real-time chat functionality over the mesh network.
- **Voice Messaging**: Record and send voice notes, complete with visual waveforms and duration indicators.
- **Voice Calling**: Initiate real-time voice calls over the local network.
- **Photo Sharing & Viewer**: Share images and view them in a full-screen photo viewer, with the ability to save received photos directly to your Gallery.
- **Location Sharing**: Share your current coordinates with peers.
- **Offline Location / Map Card**: View shared locations offline, with seamless integration to open coordinates in Organic Maps.
- **Connection Status**: Real-time visual indicators of your peer connection states.
- **Message Delivery & Read States**: Know when your message has been successfully delivered and read.
- **Notifications**: Local notifications for incoming messages and calls.
- **Runtime Permission Handling**: Graceful, user-friendly requests for necessary hardware permissions.
- **Wi-Fi Enable Prompt**: Automatically prompts the user to enable Wi-Fi if it is disabled, which is required for peer-to-peer networking.

## How it Works

MeshCall's architecture is built entirely around localized peer-to-peer (P2P) networking:

`Device A → Nearby Device Connection → Mesh Transport → Device B`

Instead of routing data through a centralized cloud server, MeshCall establishes a direct socket connection using Wi-Fi Direct. All communication—including text, voice, and media—is packetized and transmitted securely over this existing mesh transport architecture. The application does not depend on conventional internet messaging APIs for its core communication flow.

## Offline Location Sharing

If you are lost or trying to coordinate a meeting spot off the grid, MeshCall can share your exact latitude and longitude through the mesh network. 

The received location data is presented as an interactive map card. If the user has [Organic Maps](https://organicmaps.app/) installed on their device, the location can be directly opened for fully offline turn-by-turn navigation. *(Note: MeshCall passes the coordinates to Organic Maps; it does not ship with a complete offline map of the world itself).*

## Photo & Voice Messaging

- **Photo Messaging**: Users can select and share images over the local mesh connection. Received images can be viewed in full screen and saved directly to the device Gallery.
- **Voice Messaging**: Includes a built-in voice recorder (`VoiceRecorder.kt`) that captures audio, generates a visual waveform for playback (`WaveformView.kt`), and sends the compressed audio payload over the mesh.

## Tech Stack

MeshCall is a native Android application built with modern standards:
- **Language**: Kotlin
- **UI Architecture**: XML Layouts & Material Design Components
- **Networking**: Wi-Fi Direct (P2P) and standard Java/Kotlin Sockets
- **Database**: Local SQLite (via `DatabaseHelper.kt`)
- **Offline Mapping**: Location coordinates can be shared through the mesh and opened in Organic Maps for offline navigation when Organic Maps is installed.
- **Media**: Native Android `AudioRecord`/`AudioTrack` for voice, `android-image-cropper` for photo handling

## Project Structure

```text
MeshCall/
├── app/src/main/java/com/example/meshcall/
│   ├── MainActivity.kt          # Main dashboard and navigation
│   ├── MeshService.kt           # Background service managing mesh lifecycle
│   ├── WifiDirectManager.kt     # Handles Wi-Fi Direct peer discovery and connections
│   ├── SocketManager.kt         # Low-level socket data transmission
│   ├── RouteManager.kt          # Manages peer routing
│   ├── CallManager.kt           # Handles real-time voice call logic
│   ├── DatabaseHelper.kt        # SQLite implementation for storing chats/history
│   └── ...                      # UI Activities, Adapters, and Helpers
├── app/src/main/res/            # XML Layouts, Drawables, Values, and Themes
└── build.gradle.kts             # Gradle build configuration
```

## Setup / Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/imadeelakhtar/MeshCall.git
   ```
2. **Open the project**:
   Launch Android Studio and select `File > Open`, then choose the `MeshCall` directory.
3. **Sync Gradle**:
   Allow Android Studio to download dependencies and sync the project.
4. **Connect a Device**:
   Connect a physical Android device (Wi-Fi Direct testing requires physical hardware; emulators have limited P2P support).
5. **Build and Run**:
   Click the **Run** button (Shift+F10) to install the app on your device.
6. **Grant Permissions**:
   Upon first launch, follow the prompts to grant the required permissions for the app to function.

## Permissions

To enable offline mesh networking and media sharing, MeshCall requires the following runtime permissions:
- **Nearby Devices / Wi-Fi**: Required to discover and connect to peers via Wi-Fi Direct.
- **Location**: Required by Android for Wi-Fi scanning (and to share your location with peers).
- **Microphone**: Required to record voice messages and make voice calls.
- **Notifications**: Required to alert you of incoming messages and calls while the app is in the background.

## Hackathon Value

MeshCall is a highly practical and relevant hackathon project because:
- **Resilience**: It provides communication without conventional internet dependency, solving real-world problems during outages or natural disasters.
- **Offline-First Approach**: The entire architecture is built around peer-to-peer connectivity rather than just being a front-end prototype.
- **Rich Features**: It implements functional, complex features (voice calling, media sharing, location passing) over custom socket connections, demonstrating strong technical execution in connectivity-limited environments.

## FUTURE SCOPE

*The following ideas are planned for future development and are not yet implemented:*
- Stronger multi-hop routing to pass messages through intermediary devices.
- Larger mesh network support (scaling beyond a few localized peers).
- Improved battery optimization for the background mesh service.
- Richer embedded offline maps directly within the application UI.
- Live location tracking over the mesh.
- Stronger delivery reliability mechanisms (retry-queues and store-and-forward architectures).

## License

License: Not specified yet.

## Acknowledgements

- **Organic Maps** / **OpenStreetMap**: Utilized for handling offline map intents and navigation.

## Screenshots

<!-- Add screenshots here -->
<br/>
<p align="center">
  <i>Screenshots coming soon.</i>
</p>
