# ServerUI Android Client (Native ARM64)

A 100% native Android ARM64 application for [ServerUI](https://github.com/rakhechashubham/serverui), built with Kotlin, Jetpack Compose, and Material 3.

It connects directly to the ServerUI API backend to manage Linux servers, monitor live fleet metrics, inspect processes, control systemd services, browse and edit remote files via SFTP, and open interactive SSH terminals over WebSocket.

---

## Architecture & Design Principles

* **Native Android Controls**: Pure Kotlin and Jetpack Compose. No WebViews, no Tauri, no Capacitor, no PWAs, and no embedded browsers.
* **CPU-Portable & ARM64 Optimized**: Runs natively on 64-bit ARM (`arm64-v8a`) devices as well as x86_64 emulators via Android Runtime (ART) with AOT/JIT compilation. Native ARM64 vector graphics libraries are bundled in the APK.
* **Network & Security**: HTTPS is enforced by default for remote connections. Cleartext HTTP is restricted via `network_security_config.xml` to local development hosts (`localhost`, `10.0.2.2`, `127.0.0.1`). Authentication tokens are transmitted via the secure `X-ServerUI-Local-Token` header.
* **Mobile-First UX**: Responsive layouts designed for phones, foldables, and tablets (with adaptive NavigationBar on phones and NavigationRail on wide screens), touch targets meeting accessibility guidelines (48dp+), and mobile accessory keys for the terminal.

---

## Features

### 1. Fleet & System Monitor
* **Multi-Server Fleet Overview**: View all configured servers simultaneously with live status badges (Online, Offline, Connecting, Authentication Failed).
* **Live Telemetry & Telemetry Polling**:
  * **CPU**: Overall usage %, CPU model, thread count, and individual core usage gauges.
  * **Memory**: Active RAM usage, used/total bytes, available memory, and swap memory.
  * **Disk**: Root disk usage, read/write I/O transfer rates, and table of mounted filesystems (`ext4`, `btrfs`, `tmpfs`, etc.).
  * **Network**: Real-time Rx/Tx throughput rates, total bytes transferred, active established connections, and listening port list.
  * **System Telemetry**: OS distribution, kernel version, architecture, system uptime, and 1m/5m/15m load averages.

### 2. Process & Service Management
* **Processes**:
  * Search and filter by process name, PID, user, or command line.
  * Process detail inspection (PPID, threads, start time, CPU/memory usage).
  * Send `SIGTERM` to safely terminate processes with confirmation prompts.
* **Systemd Services**:
  * Search services and inspect unit state (`active`, `inactive`, `failed`).
  * Start, Stop, and Restart systemd units with confirmation guards.

### 3. SFTP File Explorer & Editor
* **Directory Navigation**: Breadcrumb path bar, "Up" directory navigation, and Home directory (`~`) shortcut.
* **File Operations**:
  * View directory contents with file size, permissions/mode (`-rw-r--r--`), and modification timestamps.
  * In-app file viewer and text editor for configuration and script files with Save action.
  * Create new files and directories.
  * Rename, delete, and compress files into `.zip` archives directly on the server.

### 4. Interactive SSH Terminal
* **WebSocket Terminal**: Streams directly to the ServerUI SSH PTY session (`/ws/terminal`).
* **Mobile Accessory Keyboard**: Quick touch shortcuts for `ESC`, `TAB`, `Ctrl+C`, `Ctrl+D`, `↑` (history previous), `↓` (history next), and `CLEAR`.
* **ANSI Output**: Cleaned, scrollable terminal buffer with monospaced console styling.

### 5. Server Management
* Add, edit, test, connect, disconnect, and delete servers.
* Password authentication or OpenSSH / PEM private key authentication.
* **Connection Draft Testing**: Test reachability, latency, and credentials before saving the server.

---

## Downloading the APK

Every push and pull request to the `main` branch automatically builds an installable APK via GitHub Actions.

1. Navigate to the repository's **Actions** tab on GitHub.
2. Select the latest run of **Android Build**.
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Download the `serverui-android-arm64` artifact (a ZIP archive containing `app-debug.apk`).

---

## Installing on an Android Device

### Option A: Via ADB (Android Debug Bridge)
Connect your ARM64 Android device with USB debugging enabled, then run:
```bash
adb install -r app-debug.apk
```

### Option B: Direct Installation
1. Transfer `app-debug.apk` to your Android device (via Google Drive, USB transfer, or download from browser).
2. Tap the file in your device's file manager.
3. If prompted, allow "Install unknown apps" for your browser or file manager.
4. Tap **Install**.

---

## Connecting to the ServerUI Backend

The native Android app connects to your ServerUI Go backend instance.

1. Ensure the ServerUI server is running:
   ```bash
   cd apps/server
   go run ./cmd/server
   ```
   Or via Docker:
   ```bash
   docker compose -f deploy/docker/docker-compose.yml up -d
   ```
2. Open the ServerUI Android app.
3. Tap the **Settings** gear icon in the top right corner.
4. Set the **ServerUI Instance URL**:
   * **Android Emulator**: `http://10.0.2.2:8080` (tap the "Emulator (10.0.2.2)" preset).
   * **Local Network / Wi-Fi**: `http://<YOUR_COMPUTER_LOCAL_IP>:8080` (e.g. `http://192.168.1.100:8080`).
   * **Production / Remote VPS**: `https://serverui.yourdomain.com`.
5. If running in desktop mode or with an auth token, enter the token in **Local Auth Token**.
6. Tap **Save & Connect**. The indicator dot will turn green when connected.

---

## Optional Release Signing Secrets

The GitHub Actions workflow (`.github/workflows/android.yml`) produces an installable debug APK automatically without requiring secrets.

To additionally produce signed release APKs in GitHub Actions, configure the following repository secrets under **Settings > Secrets and variables > Actions**:

* `KEYSTORE_BASE64`: Base64-encoded Java Keystore file (`base64 -w 0 your-keystore.jks`).
* `STORE_PASSWORD`: Keystore password.
* `KEY_ALIAS`: Key alias name.
* `KEY_PASSWORD`: Key password.

When these secrets are provided, the workflow will generate and upload both `serverui-android-arm64` and `serverui-android-arm64-release`. If secrets are absent, the release step is safely skipped and the debug APK is produced without failure.
