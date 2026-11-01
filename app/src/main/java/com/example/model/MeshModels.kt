package com.example.model

import android.net.Uri

enum class FirmwareEcosystem(
    val displayName: String,
    val tagline: String,
    val repoOwner: String,
    val repoName: String,
    val defaultBleServiceUuid: String
) {
    MESHTASTIC(
        displayName = "Meshtastic",
        tagline = "Decentralized LoRa Flood & Managed Mesh Network",
        repoOwner = "meshtastic",
        repoName = "firmware",
        defaultBleServiceUuid = "6ba1b218-15a8-461f-9fa8-5dcae273eafd"
    ),
    MESHCORE(
        displayName = "MeshCore",
        tagline = "Multi-Hop Routing Mesh (Companion, Repeater & Room Server)",
        repoOwner = "meshcore-dev",
        repoName = "MeshCore",
        defaultBleServiceUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    )
}

enum class MeshtasticTrack(val label: String, val badge: String, val description: String) {
    STABLE("Beta / Stable", "STABLE", "Production-tested mesh firmware recommended for daily deployment"),
    ALPHA("Alpha Preview", "ALPHA", "Bleeding-edge features, new hardware targets & routing optimizations"),
    CUSTOM("Local Binary", "FILE", "Flash a local .bin, .uf2, or .zip DFU package from device storage")
}

enum class MeshCoreRole(
    val label: String,
    val badge: String,
    val binarySuffix: String,
    val description: String
) {
    COMPANION_BLE(
        label = "Companion Radio (BLE)",
        badge = "BLE NODE",
        binarySuffix = "companion_radio_ble",
        description = "Pairs with Android MeshCore client over Bluetooth LE for handheld messaging"
    ),
    COMPANION_USB(
        label = "Companion Radio (USB)",
        badge = "USB NODE",
        binarySuffix = "companion_radio_usb",
        description = "Direct USB-C serial packet modem for mobile/desktop host applications"
    ),
    REPEATER(
        label = "Standalone Repeater",
        badge = "REPEATER",
        binarySuffix = "repeater",
        description = "High-site solar/tower node dedicated to multi-hop packet forwarding & CLI admin"
    ),
    ROOM_SERVER(
        label = "Room Server (BBS)",
        badge = "ROOM BBS",
        binarySuffix = "room_server",
        description = "Store-and-forward bulletin board & group chat room server node"
    ),
    CUSTOM(
        label = "Local Binary",
        badge = "FILE",
        binarySuffix = "custom",
        description = "Flash a local .bin, .uf2, or .zip DFU package from device storage"
    )
}

enum class TDeckMeshCoreFlavor(
    val label: String,
    val badge: String,
    val repoOwner: String,
    val repoName: String,
    val binaryPrefix: String,
    val description: String
) {
    RIPPLE(
        label = "Ripple Ultra UI",
        badge = "RIPPLE UI",
        repoOwner = "ripplebiz",
        repoName = "MeshCore",
        binaryPrefix = "Ultra-TDeck",
        description = "Official Ripple Radios Ultra GUI standalone T-Deck firmware (touch keyboard, chat & maps — not Room Server)"
    ),
    WADAMESH(
        label = "WadaMesh",
        badge = "WADAMESH",
        repoOwner = "ALLFATHER-BV",
        repoName = "wadamesh",
        binaryPrefix = "wadamesh-tdeck-merged",
        description = "Standalone Touch-UI MeshCore firmware for T-Deck / T-Deck Plus with offline OSM SD maps & chat"
    )
}


enum class McuArchitecture(
    val shortName: String,
    val fullName: String,
    val flashProtocol: String,
    val defaultBaudRate: Int,
    val supportsUsbOtg: Boolean,
    val supportsBleDfu: Boolean
) {
    ESP32_S3(
        shortName = "ESP32-S3",
        fullName = "Espressif ESP32-S3 Dual-Core LX7 (Native USB + BLE 5.0)",
        flashProtocol = "esptool SLIP Bootloader + ESP-OTA",
        defaultBaudRate = 921600,
        supportsUsbOtg = true,
        supportsBleDfu = true
    ),
    ESP32(
        shortName = "ESP32",
        fullName = "Espressif ESP32 Dual-Core LX6 (UART Bridge + BLE 4.2)",
        flashProtocol = "esptool SLIP Stub Loader",
        defaultBaudRate = 460800,
        supportsUsbOtg = true,
        supportsBleDfu = true
    ),
    ESP32_C3(
        shortName = "ESP32-C3",
        fullName = "Espressif ESP32-C3 RISC-V Single-Core",
        flashProtocol = "esptool SLIP RISC-V Stub",
        defaultBaudRate = 460800,
        supportsUsbOtg = true,
        supportsBleDfu = true
    ),
    NRF52840(
        shortName = "nRF52840",
        fullName = "Nordic nRF52840 ARM Cortex-M4F (UF2 / Serial & BLE Secure DFU)",
        flashProtocol = "Adafruit nRF52 UF2 + Nordic Secure DFU (S140)",
        defaultBaudRate = 115200,
        supportsUsbOtg = true,
        supportsBleDfu = true
    ),
    RP2040(
        shortName = "RP2040",
        fullName = "Raspberry Pi RP2040 Dual Cortex-M0+ (PICOBOOT / UF2)",
        flashProtocol = "1200bps Touch + UF2 Block Stream",
        defaultBaudRate = 115200,
        supportsUsbOtg = true,
        supportsBleDfu = false
    )
}

enum class ConnectionTransport(val title: String, val subtitle: String) {
    USB_OTG("USB OTG Serial", "Direct esptool SLIP / nRF52 CDC DFU via USB-C cable"),
    BLE_DFU("Bluetooth LE DFU", "Wireless Nordic Secure DFU & ESP32 OTA over BLE")
}

data class FlashPartition(
    val name: String,
    val addressHex: String,
    val sizeKb: Int,
    val fileRole: String,
    val isRequired: Boolean = true
)

data class HardwareBoard(
    val id: String,
    val name: String,
    val manufacturer: String,
    val mcu: McuArchitecture,
    val loraChip: String,
    val frequencyBands: String,
    val usbBridge: String,
    val vendorId: Int,
    val productId: Int,
    val flashSizeMb: Int,
    val psramMb: Int,
    val hasOled: Boolean,
    val hasEInk: Boolean,
    val hasGps: Boolean,
    val meshtasticTarget: String,
    val meshCoreTarget: String,
    val partitions: List<FlashPartition>,
    val bootloaderGuide: String,
    val pinoutSummary: String
)

data class FirmwareAsset(
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val partitionOffsetHex: String = "0x10000"
)

data class FirmwareRelease(
    val id: String,
    val tagName: String,
    val title: String,
    val publishedDate: String,
    val ecosystem: FirmwareEcosystem,
    val isPrerelease: Boolean,
    val changelog: String,
    val assets: List<FirmwareAsset>
)

data class CustomFirmwareFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val format: String // BIN, UF2, ZIP
)

data class UsbAttachedBoard(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String,
    val productName: String,
    val serialNumber: String?,
    val hasPermission: Boolean,
    val matchedBoard: HardwareBoard?
)

data class BleDiscoveredNode(
    val address: String,
    val name: String,
    val rssi: Int,
    val ecosystemHint: FirmwareEcosystem?,
    val isInDfuMode: Boolean,
    val serviceUuids: List<String>,
    val matchedBoard: HardwareBoard?
)

enum class FlashPhase(val stepIndex: Int, val totalSteps: Int, val title: String) {
    IDLE(0, 6, "Ready to Flash"),
    PREPARING_BINARY(1, 6, "Preparing & Hashing Firmware Image"),
    ENTERING_BOOTLOADER(2, 6, "Entering Bootloader / DFU Sync"),
    ERASING_SECTORS(3, 6, "Erasing Flash Memory Sectors"),
    WRITING_PARTITIONS(4, 6, "Writing Firmware Blocks"),
    VERIFYING_CHECKSUM(5, 6, "Verifying SHA-256 & Stub CRC32"),
    COMPLETED(6, 6, "Flash Complete — Rebooting Node"),
    FAILED(0, 6, "Flash Aborted")
}

data class FlashProgressState(
    val phase: FlashPhase = FlashPhase.IDLE,
    val overallProgress: Float = 0f,
    val currentPartitionName: String = "",
    val currentAddressHex: String = "0x00000000",
    val bytesWritten: Long = 0L,
    val totalBytes: Long = 0L,
    val speedKbps: Float = 0f,
    val elapsedSeconds: Int = 0,
    val sha256Digest: String = "",
    val errorMessage: String? = null
)

enum class LogLevel { INFO, TX, RX, WARN, SUCCESS, ERROR }

data class ConsoleLogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object BoardCatalog {
    private val esp32S3Partitions8Mb = listOf(
        FlashPartition("Bootloader", "0x0000", 64, "bootloader.bin"),
        FlashPartition("Partition Table", "0x8000", 32, "partitions.bin"),
        FlashPartition("OTA Data (boot_app0)", "0xE000", 8, "boot_app0.bin"),
        FlashPartition("Application (App0)", "0x10000", 3072, "firmware.bin"),
        FlashPartition("LittleFS / WebUI", "0x300000", 2048, "littlefs.bin", isRequired = false)
    )

    private val esp32S3Partitions16Mb = listOf(
        FlashPartition("Bootloader", "0x0000", 64, "bootloader.bin"),
        FlashPartition("Partition Table", "0x8000", 32, "partitions.bin"),
        FlashPartition("NVS", "0x9000", 20, "nvs.bin"),
        FlashPartition("OTA Data (boot_app0)", "0xE000", 8, "boot_app0.bin"),
        FlashPartition("Application (App0)", "0x10000", 6400, "firmware.bin"),
        FlashPartition("Application (App1)", "0x650000", 6400, "app1.bin", isRequired = false),
        FlashPartition("SPIFFS / LittleFS", "0xC90000", 3456, "littlefs.bin", isRequired = false)
    )

    private val esp32Partitions4Mb = listOf(
        FlashPartition("Bootloader", "0x1000", 64, "bootloader.bin"),
        FlashPartition("Partition Table", "0x8000", 32, "partitions.bin"),
        FlashPartition("OTA Data (boot_app0)", "0xE000", 8, "boot_app0.bin"),
        FlashPartition("Application (App0)", "0x10000", 2048, "firmware.bin"),
        FlashPartition("SPIFFS / LittleFS", "0x290000", 1024, "littlefs.bin", isRequired = false)
    )

    private val nrf52Partitions = listOf(
        FlashPartition("SoftDevice S140 v7.3", "0x00000", 152, "s140_nrf52_7.3.0.hex", isRequired = false),
        FlashPartition("Application Image", "0x26000", 720, "firmware.uf2 / dfu.zip"),
        FlashPartition("Internal LittleFS (NVS)", "0xED000", 44, "adafruit_littlefs", isRequired = false),
        FlashPartition("UF2 Bootloader", "0xF4000", 48, "bootloader.hex", isRequired = false)
    )

    val allBoards: List<HardwareBoard> = listOf(
        HardwareBoard(
            id = "heltec_v3",
            name = "Heltec WiFi LoRa 32 V3 / V3.2",
            manufacturer = "Heltec Automation",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "CP2102 + ESP32-S3 Native USB",
            vendorId = 4292, // 0x10C4 Silabs
            productId = 60000, // 0xEA60
            flashSizeMb = 8,
            psramMb = 0,
            hasOled = true,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "heltec-v3",
            meshCoreTarget = "Heltec_v3",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Auto-resets via CP2102 DTR/RTS. For manual ROM bootloader: Hold PRG (USR) button while plugging in USB-C or tapping RST.",
            pinoutSummary = "SX1262 NSS:GPIO8, DIO1:GPIO14, RST:GPIO12, BUSY:GPIO13 | OLED SDA:17, SCL:18, RST:21"
        ),
        HardwareBoard(
            id = "rak4631",
            name = "RAKwireless WisBlock RAK4631",
            manufacturer = "RAKwireless",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 470 / 868 / 915 MHz",
            usbBridge = "nRF52840 Native USB CDC / UF2",
            vendorId = 9114, // 0x239A Adafruit UF2
            productId = 32809,
            flashSizeMb = 1,
            psramMb = 2, // External SPI Flash on WisBlock
            hasOled = true,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "rak4631",
            meshCoreTarget = "RAK_4631",
            partitions = nrf52Partitions,
            bootloaderGuide = "Double-click the side RESET button rapidly (within 500ms) or trigger 1200bps touch over USB to enter Adafruit nRF52 UF2 / Serial DFU mode.",
            pinoutSummary = "SX1262 NSS:P1.10, DIO1:P1.15, RST:P1.06, BUSY:P1.14 | I2C1 SDA:P0.13, SCL:P0.14"
        ),
        HardwareBoard(
            id = "lilygo_tbeam_s3",
            name = "LilyGO T-Beam Supreme (ESP32-S3)",
            manufacturer = "LilyGO",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "ESP32-S3 Native USB CDC/JTAG",
            vendorId = 12346, // 0x303A Espressif
            productId = 4097, // 0x1001
            flashSizeMb = 8,
            psramMb = 8,
            hasOled = true,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "tbeam-s3-core",
            meshCoreTarget = "T_Beam_S3_Supreme_SX1262",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Hold BOOT button, press and release PWR/RST button, then release BOOT to enter ESP32-S3 USB JTAG/Serial Download mode.",
            pinoutSummary = "SX1262 NSS:GPIO10, DIO1:GPIO1, RST:GPIO5, BUSY:GPIO4 | L76K GPS RX:44, TX:43 | PMU AXP2101"
        ),
        HardwareBoard(
            id = "lilygo_techo",
            name = "LilyGO T-Echo (nRF52840 E-Ink)",
            manufacturer = "LilyGO",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "nRF52840 Native USB CDC / UF2",
            vendorId = 9114,
            productId = 32810,
            flashSizeMb = 1,
            psramMb = 2,
            hasOled = false,
            hasEInk = true,
            hasGps = true,
            meshtasticTarget = "t-echo",
            meshCoreTarget = "LilyGo_T-Echo",
            partitions = nrf52Partitions,
            bootloaderGuide = "Double-tap the top reset button (near antenna) to enter UF2/DFU bootloader. Red & green LEDs will pulse slowly.",
            pinoutSummary = "SX1262 NSS:P0.24, DIO1:P0.20 | 1.54\" E-Paper SPI | L76K GPS | BME280 Env Sensor"
        ),
        HardwareBoard(
            id = "seeed_t1000e",
            name = "Seeed SenseCAP Card Tracker T1000-E",
            manufacturer = "Seeed Studio",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech LR1110 (LoRa + GNSS)",
            frequencyBands = "868 / 915 MHz",
            usbBridge = "nRF52840 Magnetic Pogo USB / BLE DFU",
            vendorId = 10374, // 0x2886 Seeed
            productId = 87,
            flashSizeMb = 1,
            psramMb = 0,
            hasOled = false,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "tracker-t1000-e",
            meshCoreTarget = "t1000e",
            partitions = nrf52Partitions,
            bootloaderGuide = "Press and hold the button while attaching the magnetic USB pogo cable, or trigger DFU mode wirelessly over Bluetooth LE.",
            pinoutSummary = "nRF52840 + Semtech LR1110 Transceiver | Mediatek AG3335 GPS | IP65 Card Enclosure"
        ),
        HardwareBoard(
            id = "lilygo_tbeam_v1_2",
            name = "LilyGO T-Beam v1.1 / v1.2 (AXP2101)",
            manufacturer = "LilyGO",
            mcu = McuArchitecture.ESP32,
            loraChip = "Semtech SX1262 / SX1276",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "CH9102F / CP2104 UART",
            vendorId = 6790, // 0x1A86 QinHeng CH9102
            productId = 21972, // 0x55D4
            flashSizeMb = 4,
            psramMb = 8,
            hasOled = true,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "tbeam",
            meshCoreTarget = "Tbeam_SX1262",
            partitions = esp32Partitions4Mb,
            bootloaderGuide = "CH9102F handles auto-reset via DTR/RTS. If sync stalls, hold USER/BOOT button while clicking RST.",
            pinoutSummary = "ESP32-D0WDQ6-V3 | SX1262 NSS:18, DIO1:33, RST:23 | NEO-6M/8M GPS RX:34, TX:12 | 18650 Holder"
        ),
        HardwareBoard(
            id = "heltec_t114",
            name = "Heltec Mesh Node T114 V2",
            manufacturer = "Heltec Automation",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "nRF52840 Native USB CDC / UF2",
            vendorId = 9114,
            productId = 32812,
            flashSizeMb = 1,
            psramMb = 2,
            hasOled = true,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "heltec-mesh-node-t114",
            meshCoreTarget = "Heltec_t114",
            partitions = nrf52Partitions,
            bootloaderGuide = "Double-press RST button quickly to enter UF2/Serial DFU bootloader, or use 1200bps USB touch.",
            pinoutSummary = "nRF52840 + SX1262 | 1.14\" TFT Color Display | L76K GNSS Module | Solar Input"
        ),
        HardwareBoard(
            id = "seeed_xiao_esp32s3",
            name = "Seeed XIAO ESP32-S3 + Wio-SX1262",
            manufacturer = "Seeed Studio",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "868 / 915 MHz",
            usbBridge = "ESP32-S3 Native USB CDC/JTAG",
            vendorId = 12346, // 0x303A Espressif
            productId = 4097,
            flashSizeMb = 8,
            psramMb = 8,
            hasOled = false,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "seeed-xiao-s3",
            meshCoreTarget = "Xiao_S3_WIO",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Hold the tiny B (BOOT) button on the XIAO S3 while plugging into USB-C or clicking the R (RESET) button.",
            pinoutSummary = "Ultra-compact thumb-sized node | ESP32-S3R8 8MB Flash/PSRAM | Wio-SX1262 B2B Shield"
        ),
        HardwareBoard(
            id = "seeed_xiao_nrf52840",
            name = "Seeed XIAO nRF52840 + Wio-SX1262",
            manufacturer = "Seeed Studio",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech SX1262",
            frequencyBands = "868 / 915 MHz",
            usbBridge = "nRF52840 Native UF2 / CDC",
            vendorId = 10374, // 0x2886 Seeed
            productId = 69,
            flashSizeMb = 1,
            psramMb = 2,
            hasOled = false,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "seeed_xiao_nrf52840_kit",
            meshCoreTarget = "Xiao_nrf52",
            partitions = nrf52Partitions,
            bootloaderGuide = "Double-tap the tiny Reset switch next to the USB-C port to enter UF2/DFU mode.",
            pinoutSummary = "Ultra-low power micro repeater/companion | nRF52840 + 2MB onboard QSPI Flash"
        ),
        HardwareBoard(
            id = "station_g2",
            name = "Station G2 High-Power (3.5W)",
            manufacturer = "Station G2 • Meshtastic & MeshCore",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "SX1262 + High-Gain PA/LNA",
            frequencyBands = "868 / 915 MHz (Up to 35.5 dBm)",
            usbBridge = "ESP32-S3 Native USB-C (15V PD)",
            vendorId = 12346,
            productId = 4097,
            flashSizeMb = 16,
            psramMb = 8,
            hasOled = true,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "station-g2",
            meshCoreTarget = "Station_G2",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Ensure antenna is connected before powering! Hold Action/Program button while plugging in USB-C.",
            pinoutSummary = "ESP32-S3 WROOM-1 (16MB/8MB) | High-Power PA + Ultra-Low Noise LNA | Grove GPS/I2C Port"
        ),
        HardwareBoard(
            id = "lilygo_tdeck",
            name = "LilyGO T-Deck / T-Deck Plus",
            manufacturer = "LilyGO",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "ESP32-S3 Native USB CDC/JTAG",
            vendorId = 12346,
            productId = 4097,
            flashSizeMb = 16,
            psramMb = 8,
            hasOled = false,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "t-deck-tft",
            meshCoreTarget = "LilyGo_TDeck",
            partitions = esp32S3Partitions16Mb,
            bootloaderGuide = "Turn power switch OFF, press and hold the trackball center button, turn power switch ON, then release trackball.",
            pinoutSummary = "Standalone QWERTY Handheld | 2.8\" IPS Touch LCD | ESP32-S3 + C3 Keyboard Controller"
        ),
        HardwareBoard(
            id = "heltec_wireless_paper",
            name = "Heltec Wireless Paper (E-Ink)",
            manufacturer = "Heltec Automation",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "CP2102 USB-UART",
            vendorId = 4292,
            productId = 60000,
            flashSizeMb = 8,
            psramMb = 0,
            hasOled = false,
            hasEInk = true,
            hasGps = false,
            meshtasticTarget = "heltec-wireless-paper",
            meshCoreTarget = "Heltec_Wireless_Paper",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Auto-resets via CP2102. Or hold PRG button while pressing RST.",
            pinoutSummary = "ESP32-S3FN8 + SX1262 | 2.13\" B/W E-Ink Display | Ultra-thin pocket enclosure"
        ),
        HardwareBoard(
            id = "heltec_wireless_tracker",
            name = "Heltec Wireless Tracker V1.1",
            manufacturer = "Heltec Automation",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "CP2102 + ESP32-S3 Native USB",
            vendorId = 4292,
            productId = 60000,
            flashSizeMb = 8,
            psramMb = 0,
            hasOled = true,
            hasEInk = false,
            hasGps = true,
            meshtasticTarget = "heltec-wireless-tracker",
            meshCoreTarget = "Heltec_Wireless_Tracker",
            partitions = esp32S3Partitions8Mb,
            bootloaderGuide = "Connect via USB-C; CP2102 auto-triggers bootloader or hold USER key while clicking RST.",
            pinoutSummary = "ESP32-S3 + SX1262 | UC6580 Dual-Freq GNSS (GPS/GLONASS/BDS) | 0.96\" Color TFT"
        ),
        HardwareBoard(
            id = "lilygo_tlora_t3s3",
            name = "LilyGO T-LoRa T3-S3 (SX1262 / LR1121)",
            manufacturer = "LilyGO",
            mcu = McuArchitecture.ESP32_S3,
            loraChip = "Semtech SX1262 / LR1121",
            frequencyBands = "433 / 868 / 915 MHz & 2.4 GHz",
            usbBridge = "ESP32-S3 Native USB",
            vendorId = 12346,
            productId = 4097,
            flashSizeMb = 4,
            psramMb = 2,
            hasOled = true,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "tlora-t3s3-v1",
            meshCoreTarget = "LilyGo_T3S3_sx1262",
            partitions = esp32Partitions4Mb,
            bootloaderGuide = "Hold BOOT button on board while pressing RST or connecting USB-C.",
            pinoutSummary = "ESP32-S3FH4R2 | 0.96\" SSD1306 OLED | MicroSD Slot | Solar & LiPo Charger"
        ),
        HardwareBoard(
            id = "elecrow_thinknode_m1",
            name = "Elecrow ThinkNode-M1 (nRF52840 E-Ink)",
            manufacturer = "Elecrow",
            mcu = McuArchitecture.NRF52840,
            loraChip = "Semtech SX1262",
            frequencyBands = "868 / 915 MHz",
            usbBridge = "nRF52840 Native USB CDC / UF2",
            vendorId = 9114,
            productId = 32815,
            flashSizeMb = 1,
            psramMb = 2,
            hasOled = false,
            hasEInk = true,
            hasGps = true,
            meshtasticTarget = "thinknode_m1",
            meshCoreTarget = "ThinkNode_M1",
            partitions = nrf52Partitions,
            bootloaderGuide = "Double-press Reset pin or use 1200bps touch over USB-C, or flash wirelessly via BLE DFU.",
            pinoutSummary = "nRF52840 + SX1262 | 1.54\" E-Ink Frontlight | Rotary Function Dial | GPS"
        ),
        HardwareBoard(
            id = "rpi_pico_waveshare",
            name = "Raspberry Pi Pico + Waveshare SX1262",
            manufacturer = "Raspberry Pi / Waveshare",
            mcu = McuArchitecture.RP2040,
            loraChip = "Semtech SX1262",
            frequencyBands = "433 / 868 / 915 MHz",
            usbBridge = "RP2040 USB PICOBOOT / CDC",
            vendorId = 11914, // 0x2E8A Raspberry Pi
            productId = 10,
            flashSizeMb = 2,
            psramMb = 0,
            hasOled = false,
            hasEInk = false,
            hasGps = false,
            meshtasticTarget = "rpi-pico",
            meshCoreTarget = "PicoW",
            partitions = listOf(
                FlashPartition("RP2040 UF2 Flash Image", "0x10000000", 2048, "firmware-rpi-pico.uf2")
            ),
            bootloaderGuide = "Hold BOOTSEL button on the Pico while plugging in USB, or use 1200bps serial touch reset.",
            pinoutSummary = "RP2040 Dual Cortex-M0+ @ 133MHz | Waveshare Pico-LoRa-SX1262 Stackable HAT"
        )
    )

    fun matchByUsbVidPid(
        vendorId: Int,
        productId: Int,
        productName: String?,
        currentlySelectedBoard: HardwareBoard? = null
    ): HardwareBoard? {
        val nameLower = (productName ?: "").lowercase()
        // 1. Check if USB product string explicitly names a specific board
        allBoards.firstOrNull {
            nameLower.contains(it.meshtasticTarget.lowercase()) ||
                nameLower.contains(it.id.replace("_", "-")) ||
                (nameLower.contains("station") && it.id == "station_g2") ||
                (nameLower.contains("rak") && it.id == "rak4631") ||
                (nameLower.contains("t-echo") && it.id == "lilygo_techo") ||
                (nameLower.contains("t1000") && it.id == "seeed_t1000e") ||
                (nameLower.contains("t-deck") && it.id == "lilygo_tdeck") ||
                (nameLower.contains("heltec") && it.id == "heltec_v3")
        }?.let { return it }

        // 2. If the user already selected a board that uses this USB Vendor ID / MCU family
        // (for example, Station G2 uses Espressif Native USB VID 12346 / 0x303A), keep their selection!
        if (currentlySelectedBoard != null) {
            val matchesSelectedFamily = when (vendorId) {
                12346 -> currentlySelectedBoard.mcu == McuArchitecture.ESP32_S3 ||
                    currentlySelectedBoard.mcu == McuArchitecture.ESP32_C3
                4292, 6790, 1027 -> currentlySelectedBoard.mcu == McuArchitecture.ESP32_S3 ||
                    currentlySelectedBoard.mcu == McuArchitecture.ESP32
                9114, 6421, 10374 -> currentlySelectedBoard.mcu == McuArchitecture.NRF52840
                11914 -> currentlySelectedBoard.mcu == McuArchitecture.RP2040
                else -> currentlySelectedBoard.vendorId == vendorId
            }
            if (matchesSelectedFamily) {
                return currentlySelectedBoard
            }
        }

        // 3. Fallback default board for the detected USB VID
        return when (vendorId) {
            10374 -> allBoards.first { it.id == "seeed_t1000e" }
            9114, 6421 -> allBoards.first { it.id == "rak4631" }
            12346 -> allBoards.first { it.id == "station_g2" }
            4292 -> allBoards.first { it.id == "heltec_v3" }
            6790 -> allBoards.first { it.id == "lilygo_tbeam_v1_2" }
            11914 -> allBoards.first { it.id == "rpi_pico_waveshare" }
            else -> null
        }
    }
}

data class PreparedFirmwareImage(
    val bytes: ByteArray,
    val sha256Hex: String,
    val flashOffset: Int,
    val fileName: String,
    val isFullFactoryImage: Boolean,
    val isRealBinary: Boolean
)

data class VaultTargetSpec(
    val key: String,
    val board: HardwareBoard,
    val ecosystem: FirmwareEcosystem,
    val variantKey: String,
    val variantLabel: String,
    val meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
    val meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE,
    val useMeshtasticMui: Boolean = true,
    val tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
    val fullErase: Boolean = true
)

data class CachedFirmwareEntry(
    val key: String,
    val boardId: String,
    val boardName: String,
    val ecosystem: FirmwareEcosystem,
    val variantKey: String,
    val variantLabel: String,
    val versionTag: String,
    val fileName: String,
    val localFilePath: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val flashOffset: Int,
    val isFullFactoryImage: Boolean,
    val isRealBinary: Boolean,
    val downloadedAtEpochMs: Long
)

data class VaultBatchProgress(
    val isDownloading: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentBoardName: String = "",
    val currentVariantLabel: String = "",
    val statusDetail: String = "",
    val overallProgress: Float = 0f,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)


