# Hinode: Decentralized Discord Alternative - Implementation Plan

## Context

**Problem:** Discord and similar platforms centralize user data and control, raising privacy concerns. Users cannot choose where their data is hosted or which servers handle their messages, calls, and screen sharing.

**Solution:** Transform Hinode into a decentralized, open-source Discord alternative using Matrix Protocol for federated chat, WebRTC for video/audio calls, and platform-specific screen sharing APIs. Users (or groups) can self-host on VPS/dedicated servers with configurable federation (isolated by default, optional cross-server communication).

**Why now:** The project already has a solid Kotlin Multiplatform Compose foundation with sidebar navigation UI, supporting Android, iOS, Desktop (JVM), and Web. This provides the perfect starting point to build a privacy-focused communication platform.

---

## Architecture Overview

### Technology Stack

**Chat/Messaging:**
- **Matrix Protocol** for federated messaging
- **Ktor Client** (v2.3.6) for HTTP/WebSocket - fully multiplatform
- **SQLDelight** (v2.0.1) for local message persistence across all platforms

**Video/Audio Calling:**
- **WebRTC** with platform-specific libraries:
  - Android: `org.webrtc:org.webrtc.android:1.0.32663`
  - iOS: WebRTC via CocoaPods (`pod 'GoogleWebRTC'`)
  - JVM: `org.webrtc:webrtc-jvm` or build from source
  - Web: Native browser WebRTC APIs

**Screen Sharing:**
- Android: `MediaProjection` API (API 21+)
- iOS: `ScreenCaptureKit` (iOS 17+) or `ReplayKit` (iOS 14-16)
- JVM: `java.awt.Robot` or platform-specific capture
- Web: `navigator.mediaDevices.getDisplayMedia()`

**Self-Hosting Options:**
- **Conduit** (Rust) - Lightest: ~10MB binary, 50-100MB RAM
- **Dendrite** (Go) - Modern: ~100MB binary, 256MB RAM
- **Synapse** (Python) - Most mature: 1-2GB RAM minimum

### Architectural Patterns

1. **Expect/Actual** - Platform-specific implementations in dedicated source sets
2. **Repository Pattern** - Separate data layer from UI logic
3. **StateFlow + ViewModel** - Reactive state management for Compose UI
4. **Offline-First** - Cache all messages locally, sync when connected
5. **Hybrid Communication** - P2P when possible, server relay as fallback

---

## Implementation Phases

### Phase 1: Matrix Client Integration + Basic Chat (8-10 weeks)

**Objective:** Enable real-time text messaging with Matrix Protocol support

**New Files to Create:**

1. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/matrix/MatrixClient.kt`**
   - Core Matrix API client wrapping Ktor
   - Functions: `login()`, `sync()`, `sendMessage()`, `createRoom()`, `joinRoom()`, `leaveRoom()`
   - Maintains sync token for incremental updates
   - Handles exponential backoff for sync failures

2. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/matrix/Models.kt`**
   - Data classes: `Room`, `Message`, `User`, `Event`, `SyncResponse`
   - Kotlinx.serialization annotations for JSON parsing
   - Sealed classes for event types (Message, RoomCreate, UserJoin, etc.)

3. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/data/MatrixRepository.kt`**
   - Mediates between MatrixClient and local database
   - StateFlow exposures: `rooms`, `messages`, `currentUser`
   - Background sync with coroutines
   - Expect/actual for platform-specific credential storage

4. **`composeApp/src/commonMain/sqldelight/com/lostinspacebar/hinode/db/schema.sql`**
   ```sql
   CREATE TABLE messages (
     id TEXT PRIMARY KEY,
     roomId TEXT NOT NULL,
     senderId TEXT NOT NULL,
     content TEXT NOT NULL,
     timestamp INTEGER NOT NULL,
     deliveryStatus TEXT DEFAULT 'SENT'
   );

   CREATE TABLE rooms (
     id TEXT PRIMARY KEY,
     name TEXT NOT NULL,
     topic TEXT,
     avatarUrl TEXT,
     lastMessage TEXT,
     unreadCount INTEGER DEFAULT 0
   );

   CREATE TABLE serverConfig (
     id TEXT PRIMARY KEY,
     serverUrl TEXT NOT NULL,
     federationEnabled INTEGER DEFAULT 0,
     createdAt INTEGER NOT NULL
   );
   ```

5. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/chat/ChatScreen.kt`**
   - Main chat interface composable
   - Room list (integrated with existing sidebar)
   - Message list with lazy loading (`LazyColumn`)
   - Message input box with send button
   - User avatars and timestamps

6. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/chat/ChatViewModel.kt`**
   - StateFlow for rooms, messages, connection status
   - Functions: `loadRooms()`, `loadMessages(roomId)`, `sendMessage()`, `createRoom()`, `joinRoom()`
   - Coroutine management with `viewModelScope`

7. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/auth/LoginScreen.kt`**
   - Server URL input
   - Username/password fields
   - Login/Register toggle
   - Error message display

**Platform-Specific Implementations:**

- **`composeApp/src/androidMain/kotlin/.../data/AndroidMatrixRepository.kt`**
  - Secure credential storage using `EncryptedSharedPreferences`
  - Background sync with WorkManager

- **`composeApp/src/iosMain/kotlin/.../data/IOSMatrixRepository.kt`**
  - Keychain integration for credentials
  - Background fetch capabilities

- **`composeApp/src/jvmMain/kotlin/.../data/JVMMatrixRepository.kt`**
  - File-based encrypted credential storage

- **`composeApp/src/webMain/kotlin/.../data/WebMatrixRepository.kt`**
  - LocalStorage with encryption

**Dependencies to Add (in `gradle/libs.versions.toml`):**
```toml
[versions]
ktor = "2.3.6"
sqldelight = "2.0.1"
kotlinx-serialization = "1.6.2"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }

sqldelight-runtime = { module = "app.cash.sqldelight:sqldelight-runtime", version.ref = "sqldelight" }
sqldelight-driver-android = { module = "app.cash.sqldelight:sqldelight-driver-android", version.ref = "sqldelight" }
sqldelight-driver-native = { module = "app.cash.sqldelight:sqldelight-driver-native", version.ref = "sqldelight" }
sqldelight-driver-sqlite = { module = "app.cash.sqldelight:sqldelight-driver-sqlite", version.ref = "sqldelight" }
sqldelight-driver-js = { module = "app.cash.sqldelight:sqldelight-driver-js", version.ref = "sqldelight" }

kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
```

**Files to Modify:**

- **`composeApp/build.gradle.kts`** - Add dependencies in appropriate source sets
- **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/App.kt`** - Integrate ChatScreen into sidebar navigation

---

### Phase 2: WebRTC Video Calling (8-12 weeks)

**Objective:** Enable 1-to-1 video/audio calls using WebRTC

**New Files to Create:**

1. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/webrtc/PeerConnection.kt`**
   ```kotlin
   expect interface WebRTCPeerConnection {
     suspend fun createOffer(): SessionDescription
     suspend fun createAnswer(): SessionDescription
     suspend fun setRemoteDescription(sdp: SessionDescription)
     suspend fun addIceCandidate(candidate: IceCandidate)
     fun addLocalStream(stream: MediaStream)
     fun close()
   }
   ```

2. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/webrtc/SignalingManager.kt`**
   - Handles SDP offer/answer exchange via Matrix events
   - ICE candidate trickling through Matrix
   - Call state machine: Idle → Dialing → Ringing → Connected → Ended

3. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/webrtc/CallState.kt`**
   - Sealed class hierarchy for call states
   - StateFlow for UI reactivity

4. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/call/CallScreen.kt`**
   - Full-screen remote video
   - Picture-in-picture local video (draggable)
   - Call controls: mute, camera toggle, speaker, end call
   - Duration timer

5. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/call/CallViewModel.kt`**
   - StateFlow for call state, mute/camera toggles
   - Functions: `initiateCall()`, `answerCall()`, `endCall()`, `toggleMute()`, `toggleCamera()`

**Platform-Specific Implementations:**

- **`composeApp/src/androidMain/kotlin/.../webrtc/AndroidPeerConnection.kt`**
  - Wraps `org.webrtc.PeerConnection`
  - `AndroidVideoRenderer.kt` with TextureView/SurfaceView
  - `AndroidAudioManager.kt` for audio routing
  - Permission handling (CAMERA, RECORD_AUDIO)

- **`composeApp/src/iosMain/kotlin/.../webrtc/IOSPeerConnection.kt`**
  - Kotlin/Native bindings to WebRTC framework
  - AVAudioSession configuration
  - Metal-based video rendering

- **`composeApp/src/jvmMain/kotlin/.../webrtc/JVMPeerConnection.kt`**
  - JavaFX Canvas for video rendering
  - OpenGL acceleration

- **`composeApp/src/webMain/kotlin/.../webrtc/WebPeerConnection.kt`**
  - JavaScript interop with browser WebRTC API
  - HTMLVideoElement for rendering

**Dependencies to Add:**
```toml
[libraries]
webrtc-android = { module = "org.webrtc:org.webrtc.android", version = "1.0.32663" }
```

For iOS, add to `iosApp/Podfile`:
```ruby
pod 'GoogleWebRTC'
```

**Files to Modify:**

- **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/App.kt`** - Add call screen navigation

---

### Phase 3: Screen Sharing (6-8 weeks)

**Objective:** Enable screen sharing during calls on all platforms

**New Files to Create:**

1. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/screenshare/ScreenShareManager.kt`**
   ```kotlin
   expect class ScreenShareManager {
     suspend fun startScreenShare(onFrame: (ByteArray) -> Unit): Boolean
     suspend fun stopScreenShare()
     val isScreenSharing: StateFlow<Boolean>
   }
   ```

**Platform-Specific Implementations:**

- **`composeApp/src/androidMain/kotlin/.../screenshare/AndroidScreenShareManager.kt`**
  - MediaProjection API integration
  - VirtualDisplay for screen capture
  - H.264 encoding for transmission

- **`composeApp/src/iosMain/kotlin/.../screenshare/IOSScreenShareManager.kt`**
  - ScreenCaptureKit (iOS 17+) or ReplayKit fallback
  - CMSampleBuffer encoding

- **`composeApp/src/jvmMain/kotlin/.../screenshare/JVMScreenShareManager.kt`**
  - java.awt.Robot for cross-platform capture
  - Platform detection for OS-specific optimizations

- **`composeApp/src/webMain/kotlin/.../screenshare/WebScreenShareManager.kt`**
  - navigator.mediaDevices.getDisplayMedia()
  - MediaStream integration

**Files to Modify:**

- **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/call/CallScreen.kt`** - Add screen share button and indicator

---

### Phase 4: Federation Configuration UI (4-6 weeks)

**Objective:** Allow admins to configure federation settings

**New Files to Create:**

1. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/settings/ServerSettingsScreen.kt`**
   - Server URL configuration
   - Federation toggle (off by default)
   - Domain allowlist/blocklist editor
   - SSL certificate management

2. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/settings/FederationManager.kt`**
   - Federation health checks
   - Connection testing
   - Domain validation

**Files to Modify:**

- **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/App.kt`** - Add settings screen to sidebar
- SQLDelight schema - Already includes `serverConfig` table

---

### Phase 5: Self-Hosting Setup & Documentation (6-8 weeks)

**Objective:** Make it easy for users to deploy their own Matrix server

**New Files to Create:**

1. **`server/docker-compose.yml`**
   ```yaml
   version: '3.8'
   services:
     conduit:
       image: matrixconduit/matrix-conduit:latest
       ports:
         - "8448:8448"
       volumes:
         - conduit_data:/var/lib/matrix-conduit
       environment:
         CONDUIT_SERVER_NAME: "your-domain.com"
         CONDUIT_DATABASE_PATH: "/var/lib/matrix-conduit"
   ```

2. **`server/.env.example`** - Configuration template
3. **`server/nginx.conf`** - Reverse proxy configuration
4. **`server/conduit.toml`** - Conduit server config
5. **`docs/SELF_HOSTING.md`** - Complete self-hosting guide
6. **`docs/FEDERATION.md`** - Federation setup guide
7. **`docs/ADMIN_GUIDE.md`** - Server administration guide

**Files to Create (In-App):**

- **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/setup/ServerSetupWizard.kt`**
  - First-launch wizard for configuring connection to self-hosted or public server
  - Steps: Server URL → Admin credentials → Federation settings → Backup location

---

## Critical Files Summary

### Files to Create (Core Implementation):

1. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/matrix/MatrixClient.kt`** - Matrix protocol implementation
2. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/webrtc/SignalingManager.kt`** - WebRTC signaling
3. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/ui/chat/ChatScreen.kt`** - Main chat UI
4. **`composeApp/src/commonMain/sqldelight/com/lostinspacebar/hinode/db/schema.sql`** - Database schema

### Files to Modify:

1. **`composeApp/build.gradle.kts`** - Add all new dependencies
2. **`gradle/libs.versions.toml`** - Update version catalog
3. **`composeApp/src/commonMain/kotlin/com/lostinspacebar/hinode/App.kt`** - Integrate new screens

### Platform-Specific Files (Expect/Actual Pattern):

- **Android**: `AndroidMatrixRepository.kt`, `AndroidPeerConnection.kt`, `AndroidScreenShareManager.kt`
- **iOS**: `IOSMatrixRepository.kt`, `IOSPeerConnection.kt`, `IOSScreenShareManager.kt`
- **JVM**: `JVMMatrixRepository.kt`, `JVMPeerConnection.kt`, `JVMScreenShareManager.kt`
- **Web**: `WebMatrixRepository.kt`, `WebPeerConnection.kt`, `WebScreenShareManager.kt`

---

## Verification Strategy

### Phase 1 Testing:
1. Start app and navigate to login screen
2. Enter Matrix server URL (matrix.org or self-hosted)
3. Create account or login
4. Send messages between two devices
5. Verify messages persist after app restart
6. Test offline mode - send messages while offline, verify sync when reconnected

### Phase 2 Testing:
1. Initiate video call from Device A to Device B
2. Accept call on Device B
3. Verify audio/video streams on both devices
4. Toggle mute/camera and verify changes
5. End call and verify cleanup
6. Test call rejection flow
7. Test call timeout (no answer)

### Phase 3 Testing:
1. Start call between two devices
2. Click "Share Screen" on Device A
3. Verify screen content visible on Device B
4. Toggle between camera and screen share
5. Stop screen sharing and verify return to camera
6. Test on each platform (Android, iOS, Desktop, Web)

### Phase 4 Testing:
1. Navigate to Server Settings
2. Toggle federation off (default)
3. Attempt to communicate with external server - should fail
4. Toggle federation on
5. Add domain to allowlist
6. Verify communication with allowed server works

### Phase 5 Testing:
1. Deploy server using `docker-compose.yml`
2. Configure DNS/SSL
3. Connect client app to self-hosted server
4. Test full chat + call + screen share flow
5. Verify federation with another server (if enabled)

---

## Timeline Estimate

- **Phase 1**: 8-10 weeks
- **Phase 2**: 8-12 weeks
- **Phase 3**: 6-8 weeks
- **Phase 4**: 4-6 weeks
- **Phase 5**: 6-8 weeks

**Total**: ~32-44 weeks (8-11 months) for complete implementation

---

## Key Architectural Decisions Rationale

1. **Matrix Protocol**: Open standard, proven federation model, extensive ecosystem
2. **Ktor Client**: True multiplatform support (not just wrappers), excellent coroutine integration
3. **SQLDelight**: Type-safe, multiplatform-native, better KMP support than Room
4. **Hybrid P2P/Server**: Reliability of server relay with efficiency of P2P when possible
5. **Offline-First**: Better UX, reduces server dependency, handles poor connectivity gracefully

---

## Security Considerations

- **Credentials**: Platform-specific secure storage (Keychain, KeyStore, encrypted files)
- **Network**: HTTPS only, certificate pinning for self-hosted servers
- **E2E Encryption**: Future phase using Olm/Megolm (standard Matrix encryption)
- **Permissions**: Fine-grained, runtime-requested on Android/iOS
- **Data Storage**: Encrypted local database, secure deletion on logout

---

## Potential Challenges

1. **WebRTC codec compatibility** - Test all platform combinations, provide fallback codecs (VP8, H.264)
2. **Screen capture APIs vary significantly** - Thorough platform-specific testing required
3. **Battery optimization** - Implement adaptive bitrate, monitor power consumption
4. **iOS background limitations** - Use CallKit for proper call handling
5. **NAT traversal** - Provide STUN/TURN server configuration options
