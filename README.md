<!-- Improved compatibility of back to top link: See: https://github.com/othneildrew/Best-README-Template/pull/73 -->
<a id="readme-top"></a>

<!-- PROJECT SHIELDS -->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/sanjibxdas/ServerUI-Android">
    <img src="branding/serverui-icon-1024.png" alt="Logo" width="120" height="120">
  </a>

  <h1 align="center">ServerUI Android</h1>

  <p align="center">
    <strong>A 100% native, Android-exclusive control panel for your Linux fleet.</strong>
    <br />
    <br />
    <a href="https://github.com/sanjibxdas/ServerUI-Android/issues/new?labels=bug&template=bug-report---.md">Report Bug</a>
    ·
    <a href="https://github.com/sanjibxdas/ServerUI-Android/issues/new?labels=enhancement&template=feature-request---.md">Request Feature</a>
  </p>
</div>

<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#-about-the-project">About The Project</a></li>
    <li><a href="#-key-features">Key Features</a></li>
    <li><a href="#-screenshots">Screenshots</a></li>
    <li><a href="#-tech-stack">Tech Stack</a></li>
    <li><a href="#-getting-started">Getting Started</a></li>
    <li><a href="#-roadmap">Roadmap</a></li>
    <li><a href="#-contributing">Contributing</a></li>
    <li><a href="#-license">License</a></li>
  </ol>
</details>

## 📱 About The Project

**ServerUI Android** is a dedicated mobile client built from the ground up for Android. It is **not** a web wrapper and does not include a desktop or web version. 

Managing Linux servers from a phone usually means wrestling with unresponsive web panels or clunky SSH clients. ServerUI solves this by providing a **100% native Android application** built with Kotlin and Jetpack Compose. It gives you a Material 3 interface with 48dp+ touch targets, adaptive layouts for both phones and tablets, and secure WebSocket communication.

> **⚠️ Backend Required:** This app is the frontend client. You must have the [ServerUI Backend](https://github.com/rakhechashubham/serverui) deployed and reachable via HTTPS for the app to function.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## ✨ Key Features

* **📊 Fleet & System Monitoring**
  * Live telemetry polling for CPU (overall + per-core), memory (RAM/Swap), disk I/O, and network Rx/Tx.
  * Instant status badges for multiple servers (Online, Offline, Auth Failed).
* **⚙️ Process & Service Control**
  * **Systemd Management:** Start, stop, and restart services with safety confirmation prompts.
  * **Process Inspector:** Search by PID, user, or command line; view detailed stats and send `SIGTERM` safely.
* **📂 SFTP File Explorer & Editor**
  * Full file operations (create, rename, delete, zip) directly from your device.
  * Built-in plain-text editor to open, modify, and save configuration files remotely.
* **💻 Terminal & Cross-Server Tools**
  * **Interactive SSH Terminal:** WebSocket-based terminal with mobile-friendly accessory keys for easier typing.
  * **Cross-Server Transfers:** Drag and drop files between connected servers via the mobile sidebar.
* **🔐 Security & Mobile-First UX**
  * HTTPS enforced by default for remote connections; cleartext HTTP restricted to local dev hosts.
  * Adaptive `NavigationBar` for phones and `NavigationRail` for tablets.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 📸 Screenshots

<p align="center">
  <img src="docs/assets/screenshot-fleet.png" width="30%" alt="Fleet Dashboard" />
  <img src="docs/assets/screenshot-process.png" width="30%" alt="Process Inspector" />
  <img src="docs/assets/screenshot-terminal.png" width="30%" alt="SSH Terminal" />
</p>
*(Replace the image paths above with your actual screenshot assets in the repo)*

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 🛠 Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose, Material 3 |
| **Architecture** | MVVM (Model-View-ViewModel) |
| **Networking** | WebSockets, REST APIs (Retrofit/OkHttp) |
| **Target OS** | Android (ARM64 / arm64-v8a) |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 🚀 Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites
* An Android device with **ARM64 (arm64-v8a)** architecture.
* A deployed ServerUI backend accessible over HTTPS.

### Installation
1. Go to the **Releases** tab of this repository.
2. Download the latest `.apk` file (`serverui-android-arm64.apk`).
3. On your Android device, enable **"Install from unknown sources"** if prompted.
4. Install the APK and launch the app.
5. Enter your ServerUI backend URL and authentication token to connect.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 🗺 Roadmap

- [x] Initial v1.0 Native Android Release
- [x] Multi-server fleet dashboard
- [x] SFTP File Explorer & Editor
- [x] Interactive SSH Terminal
- [ ] Biometric authentication (Fingerprint/Face unlock)
- [ ] Push notifications for server alerts
- [ ] Multi-language support (i18n)

See the [open issues](https://github.com/sanjibxdas/ServerUI-Android/issues) for a full list of proposed features (and known issues).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 🤝 Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 📄 License

Distributed under the MIT License. See `LICENSE.txt` for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/sanjibxdas/ServerUI-Android.svg?style=for-the-badge
[contributors-url]: https://github.com/sanjibxdas/ServerUI-Android/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/sanjibxdas/ServerUI-Android.svg?style=for-the-badge
[forks-url]: https://github.com/sanjibxdas/ServerUI-Android/network/members
[stars-shield]: https://img.shields.io/github/stars/sanjibxdas/ServerUI-Android.svg?style=for-the-badge
[stars-url]: https://github.com/sanjibxdas/ServerUI-Android/stargazers
[issues-shield]: https://img.shields.io/github/issues/sanjibxdas/ServerUI-Android.svg?style=for-the-badge
[issues-url]: https://github.com/sanjibxdas/ServerUI-Android/issues
[license-shield]: https://img.shields.io/github/license/sanjibxdas/ServerUI-Android.svg?style=for-the-badge
[license-url]: https://github.com/sanjibxdas/ServerUI-Android/blob/master/LICENSE.txt
