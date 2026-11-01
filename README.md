# Mesh Installer

[![Android Build](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-006A60.svg)](https://m3.material.io)
[![Meshtastic](https://img.shields.io/badge/Ecosystem-Meshtastic-2B8A3E.svg)](https://meshtastic.org)
[![MeshCore](https://img.shields.io/badge/Ecosystem-MeshCore-0284C7.svg)](https://meshcore.io)

**Mesh Installer** is an all-in-one Android flashing utility, firmware manager, and off-grid field vault for **Meshtastic®** and **MeshCore** off-grid communicator radios.

Flash, update, or configure radio nodes directly from your Android phone or tablet via **USB-C OTG** or **Bluetooth LE (BLE DFU)** — completely eliminating the need for a laptop or desktop computer in the field.

---

## ⚡ Key Features

- **Direct USB OTG Flashing (Zero PC Required)**:
  - Pure Kotlin implementation of the **esptool SLIP ROM protocol** for Espressif chips (**ESP32**, **ESP32-S3**, **ESP32-C3**).
  - Handles automatic bootloader reset, DTR/RTS pulse sequencing, and 1200 bps CDC touch re-enumeration.
  - High-speed flashing (up to 921,600 baud) with real-time block-by-block progress and CRC32 verification.
  - Full support for **Nordic Semiconductor Secure DFU** and **Adafruit UF2 Bootloader** for **nRF52840** and **RP2040** devices.

- **Dual Firmware Ecosystems**:
  - **Meshtastic**: Choose between **Stable** and **Alpha** release tracks. Supports full factory flash (with partition tables & bootloader) or update images.
  - **MeshCore**: Supports all radio roles — **Companion BLE**, **Companion USB**, **Repeater**, and **Room Server** with unified version syncing.

- **LilyGO T-Deck / T-Deck Plus Specialization**:
  - **Meshtastic UI (MUI)**: Graphical touch UI with on-device touchscreen, keyboard, and map rendering (`t-deck-tft`).
  - **Classic BaseUI**: Streamlined terminal-style display firmware (`t-deck`).
  - **MeshCore Flavors**: Direct switching between **Ripple Ultra UI** and **WadaMesh** firmware variants.

- **100% Off-Grid Firmware Vault ("My Devices")**:
  - Select your fleet of radios and pre-download complete firmware binaries to persistent local storage.
  - Flash devices in remote wilderness or emergency scenarios with zero cellular or Wi-Fi connectivity.

- **Integrated Serial Monitor & CLI**:
  - Live 8N1 serial console with baud rate selector (115200 to 921600).
  - Color-coded logs (TX, RX, INFO, WARNING, ERROR) with quick pause and export tools.

- **Built-in Loopback Test Bench**:
  - Safely test and preview the entire flashing sequence, state machine, and log parsing without physical hardware connected.

---

## 📻 Supported Radios & Hardware

| Device | MCU Architecture | Transport | Supported Ecosystems |
|---|---|---|---|
| **LilyGO T-Deck / T-Deck Plus** | ESP32-S3 (16MB Flash) | USB OTG / CDC | Meshtastic (MUI & BaseUI), MeshCore (Ripple & WadaMesh) |
| **B&Q Station G2 (3.5W High-Power)** | ESP32-S3 (16MB Flash) | USB OTG / JTAG | Meshtastic, MeshCore |
| **Heltec WiFi LoRa 32 (V3 / V2)** | ESP32-S3 / ESP32 | USB OTG (CP2102 / CH9102) | Meshtastic, MeshCore |
| **RAK Wireless RAK4631 (WisBlock)** | Nordic nRF52840 | USB CDC & BLE DFU | Meshtastic, MeshCore |
| **Seeed Studio SenseCAP T1000-E** | Nordic nRF52840 | USB CDC & BLE DFU | Meshtastic |
| **LilyGO T-Beam / T-Beam Supreme** | ESP32 / ESP32-S3 | USB OTG | Meshtastic, MeshCore |
| **LilyGO T-Echo** | Nordic nRF52840 | USB CDC & BLE DFU | Meshtastic |
| **Raspberry Pi RP2040 Pico LoRa** | RP2040 | USB UF2 / CDC | Meshtastic |
| **Custom / Generic Boards** | ESP32 / S3 / C3 / nRF52 / RP2040 | USB OTG | Custom `.bin` / `.uf2` |

---

## 📱 Installation & Releases

### Download Pre-built APK
Pre-compiled APKs are available in the [Releases](https://github.com) section of this repository:
1. Download the latest `MeshInstaller-vX.X.X.apk` (or `app-debug.apk`).
2. Open the file on your Android device and allow **Install from Unknown Sources** if prompted.
3. Plug in your radio using a USB-C OTG cable or USB-C to USB-C cable and grant the USB connection prompt.

---

## 🛠️ Building from Source

### Prerequisites
- **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer)
- **JDK 17** (Temurin or OpenJDK recommended)
- **Android SDK** (API 26 minimum, API 36 target)

### Command Line Build
Clone the repository and build the debug APK using the Gradle wrapper:

```bash
git clone https://github.com/<your-username>/<your-repo-name>.git
cd <your-repo-name>

# Make wrapper executable (Linux / macOS)
chmod +x ./gradlew

# Build the Debug APK
./gradlew :app:assembleDebug

# Run Robolectric JVM unit tests
./gradlew :app:testDebugUnitTest
```

The generated APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Automated GitHub Releases Workflow

This repository includes a pre-configured GitHub Actions workflow (`.github/workflows/release.yml`).

To publish a new release automatically:
1. **Push a git tag**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. GitHub Actions will automatically checkout the code, compile the APK, and publish it under **Releases** with downloadable binaries.

---

## 🔒 Permissions & Security

Mesh Installer follows strict least-privilege standards:
- `android.permission.INTERNET` & `ACCESS_NETWORK_STATE`: Only used to discover releases and fetch official firmware from GitHub / Meshtastic APIs when online.
- `android.hardware.usb.host`: Communicates with USB-to-UART bridges (CP210x, CH340, FTDI) and native USB CDC controllers.
- `android.permission.BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`: Only used for BLE discovery and OTA Nordic Secure DFU flashing (`neverForLocation` flag declared).

---

## 📄 License

This project is licensed under the Apache License 2.0.
Meshtastic® is a registered trademark of Meshtastic LLC. MeshCore is an independent open-source mesh networking project.
