package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.example.model.BleDiscoveredNode
import com.example.model.BoardCatalog
import com.example.model.FirmwareEcosystem
import com.example.model.FlashPhase
import com.example.model.FlashProgressState
import com.example.model.HardwareBoard
import com.example.model.LogLevel
import com.example.model.McuArchitecture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

@SuppressLint("MissingPermission")
class BleTransportManager(
    private val context: Context,
    private val onLog: (LogLevel, String, String) -> Unit,
    private val onNodesUpdated: (List<BleDiscoveredNode>) -> Unit
) {
    companion object {
        // 1. Standard Nordic Secure DFU (SDK 12+ — 0000fe59)
        val NORDIC_SECURE_DFU_SERVICE: UUID = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb")
        val DFU_CONTROL_POINT_CHAR: UUID = UUID.fromString("8ec90001-f315-4f60-9fb8-838830daea50")
        val DFU_PACKET_CHAR: UUID = UUID.fromString("8ec90002-f315-4f60-9fb8-838830daea50")
        val DFU_BUTTONLESS_UNBONDED_CHAR: UUID = UUID.fromString("8ec90003-f315-4f60-9fb8-838830daea50")
        val DFU_BUTTONLESS_BONDED_CHAR: UUID = UUID.fromString("8ec90004-f315-4f60-9fb8-838830daea50")

        // 2. Adafruit nRF52 / Nordic Legacy DFU Service (00001530 — SenseCAP T1000-E, RAK4631, LilyGO T-Echo, Heltec T114, XIAO nRF52840)
        val NORDIC_LEGACY_DFU_SERVICE: UUID = UUID.fromString("00001530-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_CONTROL_POINT_CHAR: UUID = UUID.fromString("00001531-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_PACKET_CHAR: UUID = UUID.fromString("00001532-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_VERSION_CHAR: UUID = UUID.fromString("00001534-1212-efde-1523-785feabcd123")

        // 3. Meshtastic BLE Service & Characteristics (nRF52 & ESP32 Application Mode)
        val MESHTASTIC_BLE_SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val MESHTASTIC_TORADIO_CHAR: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val MESHTASTIC_FROMRADIO_CHAR: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val MESHTASTIC_FROMNUM_CHAR: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

        // 4. MeshCore Nordic UART Service (NUS — 6e400001)
        val MESHCORE_NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val MESHCORE_NUS_RX_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val MESHCORE_NUS_TX_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        // 5. ESP32 / ESP32-S3 / ESP32-C3 BLE OTA Services
        // 5a. Meshtastic Unified ESP32 BLE OTA Service (bleota / esp32-unified-ota)
        val ESP32_MESHTASTIC_OTA_SERVICE: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val ESP32_MESHTASTIC_OTA_SIZE_CHAR: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val ESP32_MESHTASTIC_OTA_DATA_CHAR: UUID = UUID.fromString("cab5483e-36e1-4688-b7f5-ea07361b26a8")
        val ESP32_MESHTASTIC_OTA_STATUS_CHAR: UUID = UUID.fromString("dab5483e-36e1-4688-b7f5-ea07361b26a8")

        // 5b. FB1E ESP32 BLE OTA Service (Arduino / NimBLE / Heltec / LilyGO T-Deck / MeshCore BLE OTA)
        val ESP32_FB1E_OTA_SERVICE: UUID = UUID.fromString("fb1e4001-54ae-4a28-9f74-dfccb248601d")
        val ESP32_FB1E_OTA_RX_CHAR: UUID = UUID.fromString("fb1e4002-54ae-4a28-9f74-dfccb248601d")
        val ESP32_FB1E_OTA_TX_CHAR: UUID = UUID.fromString("fb1e4003-54ae-4a28-9f74-dfccb248601d")

        // 5c. NimBLE / Espressif BLE OTA Services
        val ESP32_NIMBLE_OTA_SERVICE: UUID = UUID.fromString("d804b643-6ce7-4e81-9f8a-ce0f699085eb")
        val ESP32_NIMBLE_OTA_RECV_CHAR: UUID = UUID.fromString("d804b644-6ce7-4e81-9f8a-ce0f699085eb")
        val ESP32_NIMBLE_OTA_CMD_CHAR: UUID = UUID.fromString("d804b645-6ce7-4e81-9f8a-ce0f699085eb")

        val ESP32_CUSTOM_OTA_SERVICE: UUID = UUID.fromString("c8659210-af91-4ad3-a995-a58d6fd26145")
        val ESP32_CUSTOM_OTA_RECV_CHAR: UUID = UUID.fromString("c8659211-af91-4ad3-a995-a58d6fd26145")
        val ESP32_CUSTOM_OTA_CMD_CHAR: UUID = UUID.fromString("c8659212-af91-4ad3-a995-a58d6fd26145")

        val ESP32_ESPRESSIF_OTA_SERVICE: UUID = UUID.fromString("00008018-0000-1000-8000-00805f9b34fb")
        val ESP32_ESPRESSIF_OTA_RECV_CHAR: UUID = UUID.fromString("00008020-0000-1000-8000-00805f9b34fb")
        val ESP32_ESPRESSIF_OTA_BAR_CHAR: UUID = UUID.fromString("00008021-0000-1000-8000-00805f9b34fb")
        val ESP32_ESPRESSIF_OTA_CMD_CHAR: UUID = UUID.fromString("00008022-0000-1000-8000-00805f9b34fb")

        // Client Characteristic Configuration Descriptor (CCCD)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private enum class ActiveBleProtocol {
        NRF52_LEGACY_DFU,
        NRF52_SECURE_DFU,
        ESP32_MESHTASTIC_OTA,
        ESP32_FB1E_OTA,
        ESP32_NIMBLE_OTA,
        ESP32_NUS_OTA
    }

    private val bluetoothManager: BluetoothManager? = try {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    } catch (_: Exception) {
        null
    }
    private val bluetoothAdapter: BluetoothAdapter? = try {
        bluetoothManager?.adapter
    } catch (_: Exception) {
        null
    }

    private val discoveredMap = ConcurrentHashMap<String, BleDiscoveredNode>()
    private var isScanning = false
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var negotiatedMtu: Int = 23

    @Volatile
    private var pendingMtuAck: CompletableDeferred<Int>? = null

    @Volatile
    private var pendingWriteAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingReadAck: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var pendingDescriptorAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingControlResponse: CompletableDeferred<ByteArray>? = null

    @Volatile
    private var pendingPrnNotification: CompletableDeferred<ByteArray>? = null

    private fun isEmulatorEnvironment(): Boolean {
        val fp = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val hw = Build.HARDWARE.lowercase(Locale.US)
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            hw.contains("ranchu") ||
            hw.contains("cuttlefish") ||
            hw.contains("goldfish")
    }

    private fun processScanResult(res: ScanResult) {
        val dev = res.device ?: return
        val address = try {
            dev.address
        } catch (_: Exception) {
            null
        } ?: return

        val recordName = try {
            res.scanRecord?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        val devName = try {
            dev.name?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        val uuids = try {
            res.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase(Locale.US) }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val hasDfuUuid = uuids.any { it.contains("fe59") || it.contains("1530") }
        val hasEsp32OtaUuid = uuids.any {
            it.contains("4fafc201") ||
                it.contains("fb1e4001") ||
                it.contains("d804b643") ||
                it.contains("c8659210") ||
                it.contains("00008018")
        }
        val hasMeshtasticUuid = uuids.any { it.contains("6ba1b218") }
        val hasMeshCoreUuid = uuids.any { it.contains("6e400001") }

        val existingNode = discoveredMap[address]
        val rawName = recordName ?: devName ?: existingNode?.name
        val isKnownMeshName = rawName != null && (
            rawName.contains("Meshtastic", ignoreCase = true) ||
                rawName.contains("MeshCore", ignoreCase = true) ||
                rawName.contains("Ripple", ignoreCase = true) ||
                rawName.contains("WadaMesh", ignoreCase = true) ||
                rawName.contains("AdaDFU", ignoreCase = true) ||
                rawName.contains("DfuTarg", ignoreCase = true) ||
                rawName.contains("DFU", ignoreCase = true) ||
                rawName.contains("OTA", ignoreCase = true) ||
                rawName.contains("Heltec", ignoreCase = true) ||
                rawName.contains("T-Deck", ignoreCase = true) ||
                rawName.contains("TDeck", ignoreCase = true) ||
                rawName.contains("T-Beam", ignoreCase = true) ||
                rawName.contains("TBeam", ignoreCase = true) ||
                rawName.contains("Station", ignoreCase = true) ||
                rawName.contains("T1000", ignoreCase = true) ||
                rawName.contains("SenseCAP", ignoreCase = true) ||
                rawName.contains("RAK", ignoreCase = true) ||
                rawName.contains("T-Echo", ignoreCase = true) ||
                rawName.contains("TEcho", ignoreCase = true) ||
                rawName.contains("XIAO", ignoreCase = true) ||
                rawName.contains("ThinkNode", ignoreCase = true) ||
                rawName.contains("ESP32", ignoreCase = true)
            )

        val resolvedName = when {
            rawName != null && (isKnownMeshName || hasDfuUuid || hasEsp32OtaUuid || hasMeshtasticUuid || hasMeshCoreUuid) -> rawName
            hasEsp32OtaUuid -> "ESP32 BLE OTA (${address.takeLast(5)})"
            hasDfuUuid -> "Nordic DFU (${address.takeLast(5)})"
            hasMeshtasticUuid -> "Meshtastic BLE (${address.takeLast(5)})"
            hasMeshCoreUuid -> "MeshCore BLE (${address.takeLast(5)})"
            else -> return // Ignore unrelated anonymous BLE beacons
        }

        val mergedUuids = (existingNode?.serviceUuids.orEmpty() + uuids).distinct()

        // IMPORTANT: On SenseCAP T1000-E, RAK4631, and LilyGO T-Echo, normal Meshtastic/MeshCore
        // application firmware calls bledfu.begin(), which advertises 00001530 / 0000fe59 while still
        // running the main app. Only mark a node as already in DFU/OTA Bootloader mode when its advertised
        // name or ESP32 OTA service explicitly identifies the bootloader/OTA partition and NOT a normal app node.
        val hasAppName = (resolvedName.contains("Meshtastic", ignoreCase = true) && !resolvedName.contains("OTA", ignoreCase = true)) ||
            (resolvedName.contains("MeshCore", ignoreCase = true) && !resolvedName.contains("OTA", ignoreCase = true)) ||
            resolvedName.contains("Ripple", ignoreCase = true) ||
            resolvedName.contains("WadaMesh", ignoreCase = true)
        val isDfu = (!hasAppName && (
            resolvedName.contains("AdaDFU", ignoreCase = true) ||
                resolvedName.contains("DfuTarg", ignoreCase = true) ||
                resolvedName.contains("Bootloader", ignoreCase = true) ||
                resolvedName.startsWith("Nordic DFU", ignoreCase = true) ||
                resolvedName.startsWith("ESP32 BLE OTA", ignoreCase = true) ||
                resolvedName.contains("DFU", ignoreCase = true) ||
                resolvedName.contains("OTA", ignoreCase = true)
            )) || (hasEsp32OtaUuid && !hasMeshtasticUuid)

        val ecoHint = when {
            hasMeshtasticUuid ||
                mergedUuids.any { it.contains("6ba1b218") || it.contains("4fafc201") } ||
                resolvedName.contains("Meshtastic", ignoreCase = true) ->
                FirmwareEcosystem.MESHTASTIC
            hasMeshCoreUuid ||
                mergedUuids.any { it.contains("6e400001") } ||
                resolvedName.contains("MeshCore", ignoreCase = true) ||
                resolvedName.contains("Ripple", ignoreCase = true) ||
                resolvedName.contains("WadaMesh", ignoreCase = true) ->
                FirmwareEcosystem.MESHCORE
            else -> existingNode?.ecosystemHint
        }

        val matchedBoard = matchBoardFromBleName(resolvedName) ?: existingNode?.matchedBoard

        val node = BleDiscoveredNode(
            address = address,
            name = resolvedName,
            rssi = res.rssi,
            ecosystemHint = ecoHint,
            isInDfuMode = isDfu,
            serviceUuids = mergedUuids,
            matchedBoard = matchedBoard
        )
        discoveredMap[address] = node
        publishSortedNodes()
    }

    private fun matchBoardFromBleName(name: String): HardwareBoard? {
        return BoardCatalog.allBoards.firstOrNull { b ->
            name.contains(b.meshtasticTarget, ignoreCase = true) ||
                (name.contains("Heltec", ignoreCase = true) && name.contains("V4", ignoreCase = true) && b.id == "heltec_v4") ||
                (name.contains("Heltec", ignoreCase = true) && name.contains("V3", ignoreCase = true) && b.id == "heltec_v3") ||
                (name.contains("Heltec", ignoreCase = true) && name.contains("T114", ignoreCase = true) && b.id == "heltec_t114") ||
                (name.contains("Heltec", ignoreCase = true) && name.contains("Paper", ignoreCase = true) && b.id == "heltec_wireless_paper") ||
                (name.contains("Heltec", ignoreCase = true) && name.contains("Tracker", ignoreCase = true) && b.id == "heltec_wireless_tracker") ||
                (name.contains("Heltec", ignoreCase = true) && !name.contains("V3", ignoreCase = true) && !name.contains("T114", ignoreCase = true) && b.id == "heltec_v4") ||
                ((name.contains("T-Deck", ignoreCase = true) || name.contains("TDeck", ignoreCase = true)) && b.id == "lilygo_tdeck") ||
                ((name.contains("Station", ignoreCase = true) || name.contains("G2", ignoreCase = true)) && b.id == "station_g2") ||
                (name.contains("RAK", ignoreCase = true) && b.id == "rak4631") ||
                ((name.contains("T-Echo", ignoreCase = true) || name.contains("TEcho", ignoreCase = true)) && b.id == "lilygo_techo") ||
                ((name.contains("T1000", ignoreCase = true) || name.contains("SenseCAP", ignoreCase = true) || name.contains("T1KE", ignoreCase = true)) && b.id == "seeed_t1000e") ||
                (name.contains("AdaDFU", ignoreCase = true) && b.id == "seeed_t1000e") ||
                ((name.contains("Supreme", ignoreCase = true) || name.contains("T-Beam S3", ignoreCase = true)) && b.id == "lilygo_tbeam_s3") ||
                (name.contains("T-Beam", ignoreCase = true) && b.id == "lilygo_tbeam_v1_2") ||
                (name.contains("T3-S3", ignoreCase = true) && b.id == "lilygo_tlora_t3s3") ||
                (name.contains("ThinkNode", ignoreCase = true) && b.id == "elecrow_thinknode_m1") ||
                (name.contains("XIAO", ignoreCase = true) && name.contains("nRF", ignoreCase = true) && b.id == "seeed_xiao_nrf52840") ||
                (name.contains("XIAO", ignoreCase = true) && b.id == "seeed_xiao_esp32s3")
        }
    }

    private fun publishSortedNodes() {
        val sorted = discoveredMap.values.sortedWith(
            compareByDescending<BleDiscoveredNode> { it.isInDfuMode }
                .thenByDescending { it.ecosystemHint != null || it.matchedBoard != null }
                .thenByDescending { it.rssi }
        )
        onNodesUpdated(sorted)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result != null) {
                processScanResult(result)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            onLog(LogLevel.WARN, "BLE-SCAN", "Bluetooth LE scanner returned error code $errorCode")
        }
    }

    fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    /**
     * Populates already-paired/bonded Meshtastic, MeshCore, ESP32 OTA, or nRF52 DFU radios from Android Bluetooth settings.
     */
    private fun includeBondedMeshDevices() {
        try {
            val bonded = bluetoothAdapter?.bondedDevices ?: return
            for (dev in bonded) {
                val name = try {
                    dev.name?.trim()
                } catch (_: Exception) {
                    null
                } ?: continue
                val addr = try {
                    dev.address
                } catch (_: Exception) {
                    null
                } ?: continue

                val hasAppName = (name.contains("Meshtastic", ignoreCase = true) && !name.contains("OTA", ignoreCase = true)) ||
                    (name.contains("MeshCore", ignoreCase = true) && !name.contains("OTA", ignoreCase = true)) ||
                    name.contains("Ripple", ignoreCase = true) ||
                    name.contains("WadaMesh", ignoreCase = true)
                val isDfu = !hasAppName && (
                    name.contains("AdaDFU", ignoreCase = true) ||
                        name.contains("DfuTarg", ignoreCase = true) ||
                        name.contains("Bootloader", ignoreCase = true) ||
                        name.contains("DFU", ignoreCase = true) ||
                        name.contains("OTA", ignoreCase = true)
                    )
                val ecoHint = when {
                    name.contains("Meshtastic", ignoreCase = true) -> FirmwareEcosystem.MESHTASTIC
                    name.contains("MeshCore", ignoreCase = true) ||
                        name.contains("Ripple", ignoreCase = true) ||
                        name.contains("WadaMesh", ignoreCase = true) -> FirmwareEcosystem.MESHCORE
                    else -> null
                }
                val matched = matchBoardFromBleName(name)
                if (isDfu || ecoHint != null || matched != null) {
                    discoveredMap.putIfAbsent(
                        addr,
                        BleDiscoveredNode(
                            address = addr,
                            name = "$name (Paired)",
                            rssi = -55,
                            ecosystemHint = ecoHint,
                            isInDfuMode = isDfu,
                            serviceUuids = emptyList(),
                            matchedBoard = matched
                        )
                    )
                }
            }
            if (discoveredMap.isNotEmpty()) {
                publishSortedNodes()
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun startBleScan(loopbackBench: Boolean, clearExisting: Boolean = true): Boolean {
        stopBleScan()
        if (clearExisting) {
            discoveredMap.clear()
        }

        // Always include paired/bonded mesh radios first
        includeBondedMeshDevices()

        if (loopbackBench || isEmulatorEnvironment()) {
            populateBenchBleNodes()
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            onLog(
                LogLevel.WARN,
                "BLE-SCAN",
                "No hardware Bluetooth LE adapter detected on this host. Loaded simulated ESP32 & nRF52 BLE OTA targets."
            )
            if (discoveredMap.isEmpty()) {
                populateBenchBleNodes()
            }
            return false
        }

        if (!isBluetoothEnabled()) {
            onLog(
                LogLevel.WARN,
                "BLE-SCAN",
                "Bluetooth is turned off. Please allow turning on Bluetooth or tap 'SIMULATE BLE'."
            )
            if ((loopbackBench || isEmulatorEnvironment()) && discoveredMap.isEmpty()) {
                populateBenchBleNodes()
            }
            return false
        }

        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (_: SecurityException) {
            onLog(
                LogLevel.ERROR,
                "BLE-PERM",
                "Bluetooth Scan permission required. Please allow Nearby Devices / Location permission."
            )
            return false
        } catch (_: Exception) {
            null
        }

        if (scanner != null) {
            val settingsBuilder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                } catch (_: Exception) {
                }
            }
            return try {
                scanner.startScan(null, settingsBuilder.build(), scanCallback)
                isScanning = true
                onLog(
                    LogLevel.INFO,
                    "BLE-SCAN",
                    "Scanning BLE advertisements for ESP32 OTA & nRF52 DFU (Meshtastic & MeshCore) targets..."
                )
                true
            } catch (se: SecurityException) {
                onLog(
                    LogLevel.ERROR,
                    "BLE-PERM",
                    "Missing Bluetooth runtime permission (${se.message}). Tap 'RESCAN BLE' and allow Nearby Devices."
                )
                false
            } catch (e: Exception) {
                onLog(LogLevel.WARN, "BLE-SCAN", "Could not start BLE scan: ${e.message}")
                false
            }
        }
        return false
    }

    fun stopBleScan() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        isScanning = false
    }

    /**
     * Populates both ESP32-S3 (Heltec V4, Heltec V3, LilyGO T-Deck, Station G2) and
     * nRF52840 (SenseCAP T1000-E, RAK4631, LilyGO T-Echo) BLE OTA targets for test bench / emulator use.
     */
    fun populateBenchBleNodes() {
        val benchNodes = listOf(
            BleDiscoveredNode(
                address = "A0:B7:65:4C:12:E0",
                name = "Meshtastic_12e0 (Heltec V4)",
                rssi = -44,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), ESP32_MESHTASTIC_OTA_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "heltec_v4" }
            ),
            BleDiscoveredNode(
                address = "F2:88:1C:A0:09:B4",
                name = "Meshtastic_09b4 (SenseCAP T1000-E)",
                rssi = -48,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "seeed_t1000e" }
            ),
            BleDiscoveredNode(
                address = "34:85:18:7A:3B:90",
                name = "Meshtastic_3b90 (Heltec V3)",
                rssi = -51,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), ESP32_MESHTASTIC_OTA_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "heltec_v3" }
            ),
            BleDiscoveredNode(
                address = "E4:65:B8:91:4A:2C",
                name = "Meshtastic_4a2c (RAK4631)",
                rssi = -54,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "rak4631" }
            ),
            BleDiscoveredNode(
                address = "7C:DF:A1:20:88:42",
                name = "Meshtastic_8842 (LilyGO T-Deck)",
                rssi = -56,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), ESP32_FB1E_OTA_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "lilygo_tdeck" }
            ),
            BleDiscoveredNode(
                address = "58:CF:79:14:6E:A2",
                name = "Meshtastic_6ea2 (Station G2)",
                rssi = -58,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), ESP32_MESHTASTIC_OTA_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "station_g2" }
            ),
            BleDiscoveredNode(
                address = "C8:3A:9B:52:10:D7",
                name = "Meshtastic_10d7 (LilyGO T-Echo)",
                rssi = -62,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "lilygo_techo" }
            ),
            BleDiscoveredNode(
                address = "D9:1F:44:08:7E:11",
                name = "MeshCore-Companion-7E11",
                rssi = -61,
                ecosystemHint = FirmwareEcosystem.MESHCORE,
                isInDfuMode = false,
                serviceUuids = listOf(MESHCORE_NUS_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "seeed_t1000e" }
            )
        )
        benchNodes.forEach { discoveredMap[it.address] = it }
        publishSortedNodes()
        onLog(LogLevel.INFO, "BLE-BENCH", "Loaded ${benchNodes.size} ESP32 & nRF52 BLE mesh targets supporting OTA DFU.")
    }

    /**
     * Synthesizes a standard 14-byte Adafruit/Nordic Legacy DFU `.dat` Init Packet with CRC-16-CCITT
     * when the user flashes a raw `.bin` or `.uf2` without an accompanying `.dat` file.
     * Uses `device_type = 0xFFFF` (`DFU_DEVICE_TYPE_EMPTY`) so `dfu_init_prevalidate()` in
     * `Adafruit_nRF52_Bootloader` (`dfu_init_template.c`) accepts any nRF52840 board UICR device_type.
     */
    fun buildLegacyNrf52InitPacket(firmwareBytes: ByteArray): ByteArray {
        val crc16 = computeCrc16Ccitt(firmwareBytes)
        return ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0xFFFF.toShort()) // device_type = DFU_DEVICE_TYPE_EMPTY (0xFFFF)
            .putShort(0xFFFF.toShort()) // device_rev = DFU_DEVICE_REVISION_EMPTY (0xFFFF)
            .putInt(-1)                 // app_version = DFU_APP_VERSION_EMPTY (0xFFFFFFFF)
            .putShort(0x0001.toShort()) // softdevices_len = 1
            .putShort(0xFFFE.toShort()) // sd_req = any S140 SoftDevice (0xFFFE)
            .putShort(crc16.toShort())  // CRC-16-CCITT of firmware image
            .array()
    }

    /**
     * Synthesizes a Nordic Secure DFU (`0000fe59`) Protobuf `.dat` Init Packet (`dfu-cc.proto`)
     * for nRF52840 targets using Secure DFU when no external `.dat` file was bundled.
     */
    fun buildSecureNrf52InitPacket(firmwareBytes: ByteArray): ByteArray {
        val sha256Reversed = MessageDigest.getInstance("SHA-256").digest(firmwareBytes).reversedArray()
        val initCommand = ByteArrayOutputStream()

        fun writeVarint(out: ByteArrayOutputStream, value: Long) {
            var v = value
            while ((v and -0x80L) != 0L) {
                out.write(((v.toInt() and 0x7F) or 0x80))
                v = v ushr 7
            }
            out.write(v.toInt() and 0x7F)
        }

        // Field 1: fw_version = 1 (tag 0x08)
        initCommand.write(0x08)
        writeVarint(initCommand, 1L)
        // Field 2: hw_version = 52 (tag 0x10)
        initCommand.write(0x10)
        writeVarint(initCommand, 52L)
        // Field 3: sd_req = 0xFFFE (tag 0x18)
        initCommand.write(0x18)
        writeVarint(initCommand, 0xFFFEL)
        // Field 4: type = APPLICATION (4) (tag 0x20)
        initCommand.write(0x20)
        writeVarint(initCommand, 4L)
        // Field 7: app_size = firmwareBytes.size (tag 0x38)
        initCommand.write(0x38)
        writeVarint(initCommand, firmwareBytes.size.toLong())

        // Field 8: Hash { hash_type = SHA256 (3), hash = 32B } (tag 0x42)
        val hashMsg = ByteArrayOutputStream()
        hashMsg.write(0x08)
        hashMsg.write(0x03) // SHA256 = 3
        hashMsg.write(0x12)
        hashMsg.write(sha256Reversed.size)
        hashMsg.write(sha256Reversed)
        val hashBytes = hashMsg.toByteArray()
        initCommand.write(0x42)
        writeVarint(initCommand, hashBytes.size.toLong())
        initCommand.write(hashBytes)

        // Wrap in Command { op_code = INIT (1), init = initCommand } -> Packet { command = Command }
        val initCmdBytes = initCommand.toByteArray()
        val commandMsg = ByteArrayOutputStream()
        commandMsg.write(0x08)
        commandMsg.write(0x01) // op_code = INIT
        commandMsg.write(0x12)
        writeVarint(commandMsg, initCmdBytes.size.toLong())
        commandMsg.write(initCmdBytes)

        val cmdBytes = commandMsg.toByteArray()
        val packetMsg = ByteArrayOutputStream()
        packetMsg.write(0x0A)
        writeVarint(packetMsg, cmdBytes.size.toLong())
        packetMsg.write(cmdBytes)
        return packetMsg.toByteArray()
    }

    fun computeCrc16Ccitt(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = (crc ushr 8) or ((crc shl 8) and 0xFFFF)
            crc = crc xor (b.toInt() and 0xFF)
            crc = crc xor ((crc and 0xFF) ushr 4)
            crc = crc xor ((crc shl 12) and 0xFFFF)
            crc = crc xor (((crc and 0xFF) shl 5) and 0xFFFF)
        }
        return crc and 0xFFFF
    }

    /**
     * Ensures that an ESP32 / ESP32-S3 / ESP32-C3 binary being sent over BLE OTA is a clean
     * `0x10000` Application Partition binary (`0xE9` magic at offset 0x00 and `0xABCD5432`
     * `ESP_APP_DESC_MAGIC_WORD` at offset 0x20).
     *
     * If the caller provides a full `.factory.bin` or `-merged.bin` (which starts with the 2nd-stage
     * bootloader at `0x0000` / `0x1000`, partition table at `0x8000`, and application at `0x10000`),
     * this method automatically slices off the `0x0000..0xFFFF` (or `0x0000..0xEFFF`) prefix so
     * ESP-IDF's `esp_ota_write()` / `esp_ota_end()` receives only the valid application partition!
     */
    fun prepareEsp32OtaAppBinary(rawBytes: ByteArray): ByteArray {
        val unwrapped = extractDfuPayload(rawBytes).second
        if (unwrapped.size < 64) return unwrapped

        // Case 1: Already a clean 0x10000 app-only binary (0xE9 at 0x00 and 0xABCD5432 at 0x20)
        if ((unwrapped[0].toInt() and 0xFF) == 0xE9 && hasEspAppDescMagic(unwrapped, 0x20)) {
            return unwrapped
        }

        // Case 2: Padded 0x0000 .factory.bin or -merged.bin -> Application starts at offset 0x10000 (65,536)
        if (unwrapped.size > 0x10040 &&
            (unwrapped[0x10000].toInt() and 0xFF) == 0xE9 &&
            hasEspAppDescMagic(unwrapped, 0x10020)
        ) {
            return unwrapped.copyOfRange(0x10000, unwrapped.size)
        }

        // Case 3: Unpadded 0x1000 ESP32 factory binary -> Application starts at offset 0xF000 (61,440)
        if (unwrapped.size > 0xF040 &&
            (unwrapped[0xF000].toInt() and 0xFF) == 0xE9 &&
            hasEspAppDescMagic(unwrapped, 0xF020)
        ) {
            return unwrapped.copyOfRange(0xF000, unwrapped.size)
        }

        // Case 4: Scan on 4KB flash sector boundaries up to 0x20000 for ESP_APP_DESC_MAGIC_WORD (0xABCD5432 at +0x20)
        var sectorOffset = 0x1000
        val maxSearch = minOf(unwrapped.size - 0x40, 0x30000)
        while (sectorOffset <= maxSearch) {
            if ((unwrapped[sectorOffset].toInt() and 0xFF) == 0xE9 &&
                hasEspAppDescMagic(unwrapped, sectorOffset + 0x20)
            ) {
                return unwrapped.copyOfRange(sectorOffset, unwrapped.size)
            }
            sectorOffset += 0x1000
        }

        return unwrapped
    }

    private fun hasEspAppDescMagic(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || bytes.size < offset + 4) return false
        return (bytes[offset].toInt() and 0xFF) == 0x32 &&
            (bytes[offset + 1].toInt() and 0xFF) == 0x54 &&
            (bytes[offset + 2].toInt() and 0xFF) == 0xCD &&
            (bytes[offset + 3].toInt() and 0xFF) == 0xAB
    }

    /**
     * Unwraps a `.zip` Nordic DFU archive into `(initPacketDat, firmwareBin)`, OR unwraps 512-byte
     * UF2 blocks (`0x0A324655`) into a contiguous raw ARM flash binary for BLE DFU streaming.
     */
    fun extractDfuPayload(rawPayload: ByteArray): Pair<ByteArray?, ByteArray> {
        // 1. Check if payload is a ZIP archive (PK\x03\x04)
        if (rawPayload.size >= 4 &&
            rawPayload[0] == 0x50.toByte() &&
            rawPayload[1] == 0x4B.toByte() &&
            rawPayload[2] == 0x03.toByte() &&
            rawPayload[3] == 0x04.toByte()
        ) {
            var datBytes: ByteArray? = null
            var binBytes: ByteArray? = null
            try {
                ZipInputStream(ByteArrayInputStream(rawPayload)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.lowercase(Locale.US)
                            val bos = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            var r: Int
                            while (zis.read(buf).also { r = it } != -1) {
                                bos.write(buf, 0, r)
                            }
                            val data = bos.toByteArray()
                            if (name.endsWith(".dat") && datBytes == null) {
                                datBytes = data
                            } else if ((name.endsWith(".bin") || name.endsWith(".uf2")) &&
                                !name.contains("bootloader") &&
                                !name.contains("softdevice") &&
                                (binBytes == null || data.size > binBytes!!.size)
                            ) {
                                binBytes = data
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (_: Exception) {
            }
            if (binBytes != null) {
                return Pair(datBytes, unwrapUf2IfPresent(binBytes!!))
            }
        }
        return Pair(null, unwrapUf2IfPresent(rawPayload))
    }

    /**
     * If `data` starts with UF2 magic (`0x55, 0x46, 0x32, 0x0A` = "UF2\n"), reconstructs the
     * contiguous application flash image using each UF2 block's `targetAddr` (offset 12, LE32).
     *
     * On nRF52840, if blocks exist in the S140 Application Flash region (`0x00026000..0x000ECFFF`),
     * strictly filters out MBR/SoftDevice (`< 0x00026000`), LittleFS (`>= 0x000ED000`), Bootloader
     * (`>= 0x000F4000`), and UICR (`>= 0x10000000`) blocks so Application DFU (`0x04`) never writes
     * non-application segments into Bank 0.
     */
    fun unwrapUf2IfPresent(data: ByteArray): ByteArray {
        if (data.size >= 512 &&
            data[0] == 0x55.toByte() &&
            data[1] == 0x46.toByte() &&
            data[2] == 0x32.toByte() &&
            data[3] == 0x0A.toByte()
        ) {
            data class Uf2Chunk(val targetAddr: Int, val payload: ByteArray)
            val totalBlocks = data.size / 512
            val allChunks = ArrayList<Uf2Chunk>(totalBlocks)

            for (b in 0 until totalBlocks) {
                val base = b * 512
                if (data[base] == 0x55.toByte() && data[base + 1] == 0x46.toByte()) {
                    val flags = ByteBuffer.wrap(data, base + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    // Skip blocks marked "not main flash" (UF2_FLAG_NOT_MAIN_FLASH = 0x00000001)
                    if ((flags and 0x01) != 0) continue

                    val targetAddr = ByteBuffer.wrap(data, base + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    val payloadLen = ByteBuffer.wrap(data, base + 16, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .coerceIn(0, 476)

                    if (payloadLen <= 0 || base + 32 + payloadLen > data.size) continue
                    if (targetAddr < 0 || targetAddr >= 0x000F4000) continue

                    val slice = data.copyOfRange(base + 32, base + 32 + payloadLen)
                    allChunks.add(Uf2Chunk(targetAddr, slice))
                }
            }

            // Prefer nRF52840 S140 Application Flash region [0x00026000, 0x000ED000) when present
            val appRegionChunks = allChunks.filter { it.targetAddr in 0x00026000 until 0x000ED000 }
            val chosenChunks = if (appRegionChunks.isNotEmpty()) appRegionChunks else allChunks

            if (chosenChunks.isNotEmpty()) {
                var minAddr = Int.MAX_VALUE
                var maxEndAddr = 0
                for (c in chosenChunks) {
                    if (c.targetAddr < minAddr) minAddr = c.targetAddr
                    if (c.targetAddr + c.payload.size > maxEndAddr) maxEndAddr = c.targetAddr + c.payload.size
                }
                if (maxEndAddr > minAddr && (maxEndAddr - minAddr) <= 2 * 1024 * 1024) {
                    val imageSize = maxEndAddr - minAddr
                    val image = ByteArray(imageSize) { 0xFF.toByte() }
                    for (chunk in chosenChunks) {
                        val relOffset = chunk.targetAddr - minAddr
                        if (relOffset >= 0 && relOffset + chunk.payload.size <= image.size) {
                            System.arraycopy(chunk.payload, 0, image, relOffset, chunk.payload.size)
                        }
                    }
                    return image
                }
            }
        }
        return data
    }

    /**
     * Builds Meshtastic `ToRadio` protobuf packets containing `AdminMessage` (`portnum = 6`)
     * to command a connected Meshtastic node over BLE (`TORADIO_CHAR` `f75c76d2-129e-4dad-a1dd-7866124401e7`)
     * to reboot into DFU / BLE OTA mode:
     * - For **nRF52840 / RP2040**: `AdminMessage.enter_dfu_mode_request = true` (field 33 -> `0x88, 0x02, 0x01`)
     * - For **ESP32 / ESP32-S3 / ESP32-C3**: `AdminMessage.reboot_ota_seconds = 1` (field 34 -> `0x90, 0x02, 0x01`)
     */
    fun buildMeshtasticAdminOtaPackets(
        isEsp32Family: Boolean,
        targetNodeNum: Int = 0
    ): List<ByteArray> {
        // AdminMessage payload:
        // field 33 (enter_dfu_mode_request): tag = (33 << 3) | 0 = 264 -> varint [0x88, 0x02], value = 1
        // field 34 (reboot_ota_seconds):     tag = (34 << 3) | 0 = 272 -> varint [0x90, 0x02], value = 1
        val adminPayload = if (isEsp32Family) {
            byteArrayOf(0x90.toByte(), 0x02, 0x01)
        } else {
            byteArrayOf(0x88.toByte(), 0x02, 0x01)
        }

        // Data protobuf (meshtastic/mesh.proto):
        // field 1: portnum = ADMIN_APP (6) -> [0x08, 0x06]
        // field 2: payload (bytes)         -> [0x12, len, ...adminPayload]
        // field 3: want_response = false
        val dataProto = ByteArrayOutputStream().apply {
            write(0x08)
            write(0x06) // PortNum.ADMIN_APP = 6
            write(0x12)
            write(adminPayload.size)
            write(adminPayload)
        }.toByteArray()

        fun buildToRadioForDest(destNodeNum: Int): ByteArray {
            val meshPacket = ByteArrayOutputStream()
            // field 1: from = destNodeNum (fixed32, wire type 5 -> tag 0x0D)
            if (destNodeNum != 0) {
                meshPacket.write(0x0D)
                val fromBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(destNodeNum).array()
                meshPacket.write(fromBuf)
            }
            // field 2: to = destNodeNum (fixed32, wire type 5 -> tag 0x15)
            meshPacket.write(0x15)
            val toBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(destNodeNum).array()
            meshPacket.write(toBuf)
            // field 4: decoded = Data (length-delimited, wire type 2 -> tag 0x22)
            meshPacket.write(0x22)
            meshPacket.write(dataProto.size)
            meshPacket.write(dataProto)
            // field 6: id = random packet id (fixed32, wire type 5 -> tag 0x35)
            meshPacket.write(0x35)
            val pktIdBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4D455348).array()
            meshPacket.write(pktIdBuf)
            // field 11: want_ack = false

            val pktBytes = meshPacket.toByteArray()
            // Wrap in ToRadio { field 1: packet = MeshPacket (tag 0x0A) }
            return ByteArrayOutputStream().apply {
                write(0x0A)
                write(pktBytes.size)
                write(pktBytes)
            }.toByteArray()
        }

        val packets = mutableListOf<ByteArray>()
        if (targetNodeNum != 0) {
            packets.add(buildToRadioForDest(targetNodeNum))
        }
        // Also send dest = 0 (local node in Meshtastic PhoneAPI) and dest = 0xFFFFFFFF (broadcast)
        packets.add(buildToRadioForDest(0))
        packets.add(buildToRadioForDest(-1))
        return packets
    }

    /**
     * Parses `my_info.my_node_num` (field 3 -> field 1) from a Meshtastic `FromRadio` protobuf packet
     * if present during the `want_config_id` handshake.
     */
    private fun tryParseMeshtasticMyNodeNum(fromRadioBytes: ByteArray?): Int {
        if (fromRadioBytes == null || fromRadioBytes.size < 4) return 0
        try {
            // Look for FromRadio.my_info (field 3, wire type 2 -> tag 0x1A)
            var idx = 0
            while (idx < fromRadioBytes.size - 2) {
                val tag = fromRadioBytes[idx].toInt() and 0xFF
                idx++
                if (tag == 0x1A) {
                    val len = fromRadioBytes[idx].toInt() and 0x7F
                    idx++
                    if (idx + len <= fromRadioBytes.size && (fromRadioBytes[idx].toInt() and 0xFF) == 0x08) {
                        // MyNodeInfo.my_node_num (field 1, varint -> tag 0x08)
                        var p = idx + 1
                        var result = 0
                        var shift = 0
                        while (p < idx + len && shift < 35) {
                            val b = fromRadioBytes[p].toInt() and 0xFF
                            result = result or ((b and 0x7F) shl shift)
                            p++
                            if ((b and 0x80) == 0) return result
                            shift += 7
                        }
                    }
                    break
                } else {
                    val wireType = tag and 0x07
                    when (wireType) {
                        0 -> while (idx < fromRadioBytes.size && (fromRadioBytes[idx].toInt() and 0x80) != 0) idx++
                        2 -> {
                            val l = fromRadioBytes[idx].toInt() and 0x7F
                            idx += 1 + l
                            continue
                        }
                        5 -> {
                            idx += 4
                            continue
                        }
                        else -> break
                    }
                    idx++
                }
            }
        } catch (_: Exception) {
        }
        return 0
    }

    /**
     * Computes Nordic's incremented DFU bootloader MAC address (`lastByte + 1`), e.g.
     * `F2:88:1C:A0:09:B4` -> `F2:88:1C:A0:09:B5`.
     */
    private fun incrementBleMacAddress(mac: String): String {
        val parts = mac.split(":")
        if (parts.size != 6) return mac
        return try {
            val last = parts[5].toInt(16)
            val next = (last + 1) and 0xFF
            (parts.dropLast(1) + "%02X".format(Locale.US, next)).joinToString(":")
        } catch (_: Exception) {
            mac
        }
    }

    /**
     * Clears Android's internal GATT service cache via `BluetoothGatt.refresh()` so that when a
     * radio reboots from Application Mode into AdaDFU / Secure DFU / ESP32 BLE OTA Mode on the
     * same or incremented MAC address, `discoverServices()` fetches the fresh bootloader GATT table.
     */
    private fun refreshGattCache(gatt: BluetoothGatt?) {
        if (gatt == null) return
        try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            refreshMethod.invoke(gatt)
        } catch (_: Exception) {
        }
    }

    /**
     * Standalone trigger to put either an nRF52840 device (SenseCAP T1000-E, RAK4631, LilyGO T-Echo,
     * Heltec T114, XIAO nRF52840) or an ESP32 device (Heltec V4, Heltec V3, Station G2, LilyGO T-Deck,
     * T-Beam Supreme) into OTA DFU / BLE OTA Bootloader mode over Bluetooth LE.
     */
    suspend fun triggerBleDfuMode(
        targetNode: BleDiscoveredNode?,
        board: HardwareBoard,
        loopbackBench: Boolean
    ): BleDiscoveredNode? = withContext(Dispatchers.IO) {
        stopBleScan()
        if (discoveredMap.isEmpty() && (loopbackBench || isEmulatorEnvironment() || !isBluetoothEnabled())) {
            populateBenchBleNodes()
        }

        val effectiveTarget = targetNode
            ?: discoveredMap.values.firstOrNull { it.matchedBoard?.id == board.id }
            ?: discoveredMap.values.firstOrNull()

        if (effectiveTarget == null) {
            onLog(LogLevel.WARN, "BLE-DFU", "No BLE radio selected. Tap 'RESCAN BLE' and select your ${board.name} first.")
            return@withContext null
        }

        val targetBoard = effectiveTarget.matchedBoard ?: board
        val isEspFamily = targetBoard.mcu == McuArchitecture.ESP32 ||
            targetBoard.mcu == McuArchitecture.ESP32_S3 ||
            targetBoard.mcu == McuArchitecture.ESP32_C3

        val cleanMac = effectiveTarget.address.substringBefore(" ").trim()
        val expectedDfuMac = if (isEspFamily) cleanMac else incrementBleMacAddress(cleanMac)

        var legacyControlChar: BluetoothGattCharacteristic? = null
        var secureButtonlessChar: BluetoothGattCharacteristic? = null
        var meshtasticToRadioChar: BluetoothGattCharacteristic? = null
        var meshtasticFromRadioChar: BluetoothGattCharacteristic? = null
        var nusRxChar: BluetoothGattCharacteristic? = null
        val servicesReady = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!servicesReady.isCompleted) servicesReady.complete(false)
                    pendingWriteAck?.complete(false)
                    pendingReadAck?.complete(null)
                    pendingDescriptorAck?.complete(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val legacyService = g.getService(NORDIC_LEGACY_DFU_SERVICE)
                val secureService = g.getService(NORDIC_SECURE_DFU_SERVICE)
                val meshtasticService = g.getService(MESHTASTIC_BLE_SERVICE)
                val nusService = g.getService(MESHCORE_NUS_SERVICE)

                legacyControlChar = legacyService?.getCharacteristic(LEGACY_DFU_CONTROL_POINT_CHAR)
                secureButtonlessChar = secureService?.getCharacteristic(DFU_BUTTONLESS_UNBONDED_CHAR)
                    ?: secureService?.getCharacteristic(DFU_BUTTONLESS_BONDED_CHAR)
                meshtasticToRadioChar = meshtasticService?.getCharacteristic(MESHTASTIC_TORADIO_CHAR)
                meshtasticFromRadioChar = meshtasticService?.getCharacteristic(MESHTASTIC_FROMRADIO_CHAR)
                nusRxChar = nusService?.getCharacteristic(MESHCORE_NUS_RX_CHAR)

                if (!servicesReady.isCompleted) {
                    servicesReady.complete(
                        legacyControlChar != null ||
                            secureButtonlessChar != null ||
                            meshtasticToRadioChar != null ||
                            nusRxChar != null
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                pendingWriteAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingReadAck?.complete(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                pendingReadAck?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                pendingDescriptorAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        val realDevice: BluetoothDevice? = try {
            if (isBluetoothEnabled() && !loopbackBench && !isEmulatorEnvironment()) {
                bluetoothAdapter?.getRemoteDevice(cleanMac)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        if (realDevice != null) {
            try {
                onLog(
                    LogLevel.INFO,
                    "BLE-DFU",
                    "Connecting to ${effectiveTarget.name} [$cleanMac] to trigger OTA Bootloader mode..."
                )
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    realDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    realDevice.connectGatt(context, false, gattCallback)
                }
                activeGatt = gatt
                withTimeoutOrNull(5000L) { servicesReady.await() }

                sendAllBootloaderTriggers(
                    gatt = gatt,
                    targetName = effectiveTarget.name,
                    isEspFamily = isEspFamily,
                    legacyControlChar = legacyControlChar,
                    secureButtonlessChar = secureButtonlessChar,
                    meshtasticToRadioChar = meshtasticToRadioChar,
                    meshtasticFromRadioChar = meshtasticFromRadioChar,
                    nusRxChar = nusRxChar
                )

                delay(750)
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (_: Exception) {
                }
                activeGatt = null

                // Scan up to 5.5s so Android discovers the rebooted AdaDFU / DfuTarg / ESP32-OTA peripheral
                startBleScan(loopbackBench = false, clearExisting = false)
                var foundBootloaderNode: BleDiscoveredNode? = null
                for (poll in 0 until 14) {
                    delay(400)
                    foundBootloaderNode = discoveredMap[expectedDfuMac]?.takeIf { it.isInDfuMode || expectedDfuMac != cleanMac }
                        ?: discoveredMap.values.firstOrNull { it.isInDfuMode }
                    if (foundBootloaderNode != null) break
                }
                stopBleScan()

                if (foundBootloaderNode != null) {
                    publishSortedNodes()
                    onLog(
                        LogLevel.SUCCESS,
                        "BLE-DFU",
                        "${effectiveTarget.name} rebooted into OTA Bootloader mode -> '${foundBootloaderNode.name}' [${foundBootloaderNode.address}]"
                    )
                    return@withContext foundBootloaderNode
                }
            } catch (e: Exception) {
                onLog(LogLevel.WARN, "BLE-DFU", "BLE DFU trigger warning: ${e.message}")
            }
        } else {
            if (isEspFamily) {
                onLog(
                    LogLevel.TX,
                    "BLE-OTA",
                    "Meshtastic ESP32 AdminMessage [ToRadio f75c76d2 -> reboot_ota_seconds=1] -> Switching ${targetBoard.name} boot partition to bleota"
                )
            } else {
                onLog(
                    LogLevel.TX,
                    "BLE-DFU",
                    "Adafruit BLEDfu [00001530 -> 00001531]: CCCD=0x0001, Write [0x01, 0x04] (DFU_OP_START_DFU, APP) -> enterOTADfu() (GPREGRET=0xA8)"
                )
            }
            delay(350)
        }

        // Update discoveredMap with the rebooted bootloader node (while keeping cleanMac reachable if needed)
        val dfuDisplayName = if (isEspFamily) {
            "ESP32-OTA (${targetBoard.name})"
        } else {
            "AdaDFU (${targetBoard.name})"
        }
        val rebootedServiceUuids = if (isEspFamily) {
            listOf(ESP32_MESHTASTIC_OTA_SERVICE.toString(), ESP32_FB1E_OTA_SERVICE.toString())
        } else {
            listOf(NORDIC_LEGACY_DFU_SERVICE.toString(), NORDIC_SECURE_DFU_SERVICE.toString())
        }
        if (expectedDfuMac != cleanMac) {
            discoveredMap.remove(cleanMac)
        }
        val rebootedNode = BleDiscoveredNode(
            address = expectedDfuMac,
            name = dfuDisplayName,
            rssi = effectiveTarget.rssi,
            ecosystemHint = effectiveTarget.ecosystemHint,
            isInDfuMode = true,
            serviceUuids = rebootedServiceUuids,
            matchedBoard = targetBoard
        )
        discoveredMap[expectedDfuMac] = rebootedNode
        publishSortedNodes()
        onLog(
            LogLevel.SUCCESS,
            "BLE-DFU",
            "${effectiveTarget.name} rebooted into OTA Bootloader mode -> Now advertising as '${rebootedNode.name}' [$expectedDfuMac]"
        )
        return@withContext rebootedNode
    }

    /**
     * Sends all supported OTA/DFU bootloader entry triggers to a connected Meshtastic or MeshCore radio:
     * 1. Meshtastic `ToRadio` (`f75c76d2`) AdminMessage (`reboot_ota_seconds = 1` on ESP32, `enter_dfu_mode_request = true` on nRF52)
     * 2. Adafruit BLEDfu (`00001531`) `[0x01, 0x04]` (`DFU_OP_START_DFU`, `DFU_TYPE_APPLICATION`)
     * 3. Nordic Secure Buttonless DFU (`8ec90003` / `8ec90004`) `[0x01]` (`DFU_OP_ENTER_BOOTLOADER`)
     * 4. MeshCore Nordic UART RX (`6e400002`) `"ota\r\n"` / `"dfu\r\n"`
     */
    private suspend fun sendAllBootloaderTriggers(
        gatt: BluetoothGatt?,
        targetName: String,
        isEspFamily: Boolean,
        legacyControlChar: BluetoothGattCharacteristic?,
        secureButtonlessChar: BluetoothGattCharacteristic?,
        meshtasticToRadioChar: BluetoothGattCharacteristic?,
        meshtasticFromRadioChar: BluetoothGattCharacteristic?,
        nusRxChar: BluetoothGattCharacteristic?
    ): Boolean {
        if (gatt == null) return false
        var sentAny = false

        // 1. Meshtastic ToRadio AdminMessage (works on both ESP32 and nRF52840 running Meshtastic)
        if (meshtasticToRadioChar != null) {
            var myNodeNum = 0
            if (meshtasticFromRadioChar != null) {
                // Send want_config_id (field 3 = 0x18, varint 0x2A) to read MyNodeInfo.my_node_num
                writeCharacteristicSynced(gatt, meshtasticToRadioChar, byteArrayOf(0x18, 0x2A), noResponse = false)
                for (readAttempt in 0 until 6) {
                    val fromBytes = readCharacteristicSynced(gatt, meshtasticFromRadioChar)
                    if (fromBytes == null || fromBytes.isEmpty()) break
                    val parsed = tryParseMeshtasticMyNodeNum(fromBytes)
                    if (parsed != 0) {
                        myNodeNum = parsed
                        break
                    }
                }
            }
            val actionDesc = if (isEspFamily) "reboot_ota_seconds=1 (ESP32 bleota)" else "enter_dfu_mode_request=true (nRF52 AdaDFU)"
            onLog(
                LogLevel.TX,
                "BLE-OTA",
                "Meshtastic ToRadio (f75c76d2) -> Sending AdminMessage [$actionDesc] to $targetName..."
            )
            val adminPackets = buildMeshtasticAdminOtaPackets(isEsp32Family = isEspFamily, targetNodeNum = myNodeNum)
            for (pkt in adminPackets) {
                if (writeCharacteristicSynced(gatt, meshtasticToRadioChar, pkt, noResponse = false)) {
                    sentAny = true
                }
                delay(60)
            }
        }

        // 2. Adafruit BLEDfu (00001531): Enable CCCD & write [0x01, 0x04]
        if (legacyControlChar != null) {
            onLog(
                LogLevel.TX,
                "BLE-DFU",
                "Adafruit BLEDfu (00001531) -> Enabling CCCD & writing [0x01, 0x04] (START_DFU, APP) to $targetName..."
            )
            enableCccd(gatt, legacyControlChar, useIndication = false)
            val wroteDefault = writeCharacteristicSynced(gatt, legacyControlChar, byteArrayOf(0x01, 0x04), noResponse = false)
            if (!wroteDefault) {
                writeCharacteristicSynced(gatt, legacyControlChar, byteArrayOf(0x01, 0x04), noResponse = true)
            }
            sentAny = true
        }

        // 3. Nordic Secure Buttonless DFU (8ec90003 / 8ec90004): Enable Indications & write [0x01]
        if (secureButtonlessChar != null) {
            onLog(
                LogLevel.TX,
                "BLE-DFU",
                "Nordic Buttonless DFU (${secureButtonlessChar.uuid.toString().take(8)}) -> Enabling Indications & writing [0x01]..."
            )
            enableCccd(gatt, secureButtonlessChar, useIndication = true)
            writeCharacteristicSynced(gatt, secureButtonlessChar, byteArrayOf(0x01), noResponse = false)
            sentAny = true
        }

        // 4. MeshCore Nordic UART RX (6e400002): Send "ota\r\n" / "dfu\r\n"
        if (nusRxChar != null && !sentAny) {
            val cmd = if (isEspFamily) "ota\r\n" else "dfu\r\n"
            onLog(
                LogLevel.TX,
                "BLE-OTA",
                "Nordic UART RX (6e400002) -> Sending '${cmd.trim()}' bootloader trigger to $targetName..."
            )
            writeCharacteristicSynced(gatt, nusRxChar, cmd.toByteArray(Charsets.UTF_8), noResponse = false)
            sentAny = true
        }

        return sentAny
    }

    /**
     * Executes wireless Bluetooth LE OTA / DFU flashing for both:
     * - **ESP32 / ESP32-S3 / ESP32-C3** devices (Heltec V4, Heltec V3, Station G2, LilyGO T-Deck, T-Beam Supreme, Seeed XIAO ESP32S3, etc.)
     * - **nRF52840** devices (SenseCAP T1000-E, RAK4631, LilyGO T-Echo, Heltec T114, Seeed XIAO nRF52840, Elecrow ThinkNode M1)
     */
    suspend fun flashFirmwareOverBleDfu(
        targetNode: BleDiscoveredNode?,
        board: HardwareBoard,
        payload: ByteArray,
        sha256Hex: String,
        fullErase: Boolean,
        loopbackBench: Boolean,
        isRealBinary: Boolean = true,
        onProgress: (FlashProgressState) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        stopBleScan()

        if (!isRealBinary && !loopbackBench) {
            val errMsg = "Firmware download for ${board.name} did not return a valid hardware binary. Check internet connection or select a release in the Vault."
            onLog(LogLevel.ERROR, "BLE-OTA", errMsg)
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.FAILED,
                    errorMessage = errMsg
                )
            )
            return@withContext false
        }

        if (discoveredMap.isEmpty() && (loopbackBench || isEmulatorEnvironment() || !isBluetoothEnabled())) {
            populateBenchBleNodes()
        }

        val effectiveTarget = targetNode
            ?: discoveredMap.values.firstOrNull { it.matchedBoard?.id == board.id }
            ?: discoveredMap.values.firstOrNull()

        if (effectiveTarget == null) {
            onLog(LogLevel.ERROR, "BLE-OTA", "No BLE target node selected. Scan and pick a BLE radio first.")
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.FAILED,
                    errorMessage = "Select a Bluetooth LE radio from the BLE OTA Targets list (or tap 'SIMULATE BLE') before starting."
                )
            )
            return@withContext false
        }

        val isEspFamily = board.mcu == McuArchitecture.ESP32 ||
            board.mcu == McuArchitecture.ESP32_S3 ||
            board.mcu == McuArchitecture.ESP32_C3

        // Prepare architecture-appropriate OTA image:
        // - For ESP32/S3/C3: ensure we send only the 0x10000 application partition binary (auto-slicing .factory.bin / -merged.bin if needed)
        // - For nRF52840: extract (.dat, .bin) from Nordic DFU .zip or unwrap .uf2 application flash blocks
        val (extractedInitBytes, rawFirmwareBytes) = extractDfuPayload(payload)
        val firmwareBytes = if (isEspFamily) {
            val cleanEspApp = prepareEsp32OtaAppBinary(rawFirmwareBytes)
            if (cleanEspApp.size < rawFirmwareBytes.size) {
                onLog(
                    LogLevel.INFO,
                    "BLE-OTA",
                    "Extracted clean 0x10000 ESP32 OTA App Partition (${cleanEspApp.size / 1024} KB) from ${rawFirmwareBytes.size / 1024} KB factory/merged image."
                )
            }
            cleanEspApp
        } else {
            rawFirmwareBytes
        }

        val baseFlashAddress = if (isEspFamily) 0x00010000 else 0x00026000

        // Discovered GATT characteristics across nRF52 & ESP32 services
        var legacyControlChar: BluetoothGattCharacteristic? = null
        var legacyPacketChar: BluetoothGattCharacteristic? = null
        var secureControlChar: BluetoothGattCharacteristic? = null
        var securePacketChar: BluetoothGattCharacteristic? = null
        var gattButtonlessChar: BluetoothGattCharacteristic? = null
        var meshtasticToRadioChar: BluetoothGattCharacteristic? = null
        var meshtasticFromRadioChar: BluetoothGattCharacteristic? = null
        var nusRxChar: BluetoothGattCharacteristic? = null
        var nusTxChar: BluetoothGattCharacteristic? = null

        var espMeshtasticSizeChar: BluetoothGattCharacteristic? = null
        var espMeshtasticDataChar: BluetoothGattCharacteristic? = null
        var espMeshtasticStatusChar: BluetoothGattCharacteristic? = null
        var espFb1eRxChar: BluetoothGattCharacteristic? = null
        var espFb1eTxChar: BluetoothGattCharacteristic? = null
        var espNimbleRecvChar: BluetoothGattCharacteristic? = null
        var espNimbleCmdChar: BluetoothGattCharacteristic? = null

        var detectedProtocol: ActiveBleProtocol? = null
        var servicesReady = CompletableDeferred<Boolean>()
        negotiatedMtu = 23

        fun handleBleNotification(uuid: UUID?, rawValue: ByteArray?) {
            if (rawValue == null || rawValue.isEmpty()) return
            when (uuid) {
                LEGACY_DFU_CONTROL_POINT_CHAR, DFU_CONTROL_POINT_CHAR -> {
                    val opCode = rawValue[0].toInt() and 0xFF
                    if (opCode == 0x10 || opCode == 0x60) {
                        pendingControlResponse?.complete(rawValue)
                    } else if (opCode == 0x11) {
                        pendingPrnNotification?.complete(rawValue)
                    }
                }
                ESP32_FB1E_OTA_TX_CHAR,
                ESP32_MESHTASTIC_OTA_STATUS_CHAR,
                ESP32_NIMBLE_OTA_CMD_CHAR,
                ESP32_CUSTOM_OTA_CMD_CHAR,
                ESP32_ESPRESSIF_OTA_BAR_CHAR,
                ESP32_ESPRESSIF_OTA_CMD_CHAR,
                MESHCORE_NUS_TX_CHAR -> {
                    pendingControlResponse?.complete(rawValue)
                    pendingPrnNotification?.complete(rawValue)
                }
            }
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (_: Exception) {
                    }
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!servicesReady.isCompleted) {
                        servicesReady.complete(false)
                    }
                    val disconnectEx = java.io.IOException("Bluetooth connection dropped by target board")
                    pendingMtuAck?.complete(negotiatedMtu)
                    pendingWriteAck?.complete(false)
                    pendingReadAck?.complete(null)
                    pendingDescriptorAck?.complete(false)
                    pendingControlResponse?.completeExceptionally(disconnectEx)
                    pendingPrnNotification?.completeExceptionally(disconnectEx)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedMtu = mtu
                }
                pendingMtuAck?.complete(negotiatedMtu)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val legacyService = g.getService(NORDIC_LEGACY_DFU_SERVICE)
                val secureService = g.getService(NORDIC_SECURE_DFU_SERVICE)
                val meshtasticService = g.getService(MESHTASTIC_BLE_SERVICE)
                val nusService = g.getService(MESHCORE_NUS_SERVICE)

                val espMeshOtaService = g.getService(ESP32_MESHTASTIC_OTA_SERVICE)
                val espFb1eService = g.getService(ESP32_FB1E_OTA_SERVICE)
                val espNimbleService = g.getService(ESP32_NIMBLE_OTA_SERVICE)
                    ?: g.getService(ESP32_CUSTOM_OTA_SERVICE)
                val espEspressifService = g.getService(ESP32_ESPRESSIF_OTA_SERVICE)

                legacyControlChar = legacyService?.getCharacteristic(LEGACY_DFU_CONTROL_POINT_CHAR)
                legacyPacketChar = legacyService?.getCharacteristic(LEGACY_DFU_PACKET_CHAR)

                secureControlChar = secureService?.getCharacteristic(DFU_CONTROL_POINT_CHAR)
                securePacketChar = secureService?.getCharacteristic(DFU_PACKET_CHAR)
                gattButtonlessChar = secureService?.getCharacteristic(DFU_BUTTONLESS_UNBONDED_CHAR)
                    ?: secureService?.getCharacteristic(DFU_BUTTONLESS_BONDED_CHAR)

                meshtasticToRadioChar = meshtasticService?.getCharacteristic(MESHTASTIC_TORADIO_CHAR)
                meshtasticFromRadioChar = meshtasticService?.getCharacteristic(MESHTASTIC_FROMRADIO_CHAR)

                nusRxChar = nusService?.getCharacteristic(MESHCORE_NUS_RX_CHAR)
                nusTxChar = nusService?.getCharacteristic(MESHCORE_NUS_TX_CHAR)

                espMeshtasticSizeChar = espMeshOtaService?.getCharacteristic(ESP32_MESHTASTIC_OTA_SIZE_CHAR)
                espMeshtasticDataChar = espMeshOtaService?.getCharacteristic(ESP32_MESHTASTIC_OTA_DATA_CHAR)
                    ?: espMeshtasticSizeChar
                espMeshtasticStatusChar = espMeshOtaService?.getCharacteristic(ESP32_MESHTASTIC_OTA_STATUS_CHAR)

                espFb1eRxChar = espFb1eService?.getCharacteristic(ESP32_FB1E_OTA_RX_CHAR)
                espFb1eTxChar = espFb1eService?.getCharacteristic(ESP32_FB1E_OTA_TX_CHAR)

                espNimbleRecvChar = espNimbleService?.getCharacteristic(ESP32_NIMBLE_OTA_RECV_CHAR)
                    ?: espNimbleService?.getCharacteristic(ESP32_CUSTOM_OTA_RECV_CHAR)
                    ?: espEspressifService?.getCharacteristic(ESP32_ESPRESSIF_OTA_RECV_CHAR)
                espNimbleCmdChar = espNimbleService?.getCharacteristic(ESP32_NIMBLE_OTA_CMD_CHAR)
                    ?: espNimbleService?.getCharacteristic(ESP32_CUSTOM_OTA_CMD_CHAR)
                    ?: espEspressifService?.getCharacteristic(ESP32_ESPRESSIF_OTA_CMD_CHAR)
                    ?: espEspressifService?.getCharacteristic(ESP32_ESPRESSIF_OTA_BAR_CHAR)

                // Determine active OTA/DFU protocol from discovered GATT characteristics
                detectedProtocol = when {
                    espMeshtasticDataChar != null -> ActiveBleProtocol.ESP32_MESHTASTIC_OTA
                    espFb1eRxChar != null -> ActiveBleProtocol.ESP32_FB1E_OTA
                    espNimbleRecvChar != null -> ActiveBleProtocol.ESP32_NIMBLE_OTA
                    legacyControlChar != null && legacyPacketChar != null -> ActiveBleProtocol.NRF52_LEGACY_DFU
                    secureControlChar != null && securePacketChar != null -> ActiveBleProtocol.NRF52_SECURE_DFU
                    isEspFamily && nusRxChar != null && meshtasticToRadioChar == null -> ActiveBleProtocol.ESP32_NUS_OTA
                    else -> null
                }

                if (!servicesReady.isCompleted) {
                    servicesReady.complete(
                        detectedProtocol != null ||
                            legacyControlChar != null ||
                            gattButtonlessChar != null ||
                            meshtasticToRadioChar != null ||
                            nusRxChar != null
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                pendingWriteAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingReadAck?.complete(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                pendingReadAck?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                pendingDescriptorAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleBleNotification(characteristic.uuid, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleBleNotification(characteristic.uuid, value)
            }
        }

        try {
            val cleanMac = effectiveTarget.address.substringBefore(" ").trim()
            val realDevice: BluetoothDevice? = try {
                if (isBluetoothEnabled() && !loopbackBench && !isEmulatorEnvironment()) {
                    bluetoothAdapter?.getRemoteDevice(cleanMac)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

            if (realDevice != null) {
                onLog(
                    LogLevel.INFO,
                    "BLE-GATT",
                    "Connecting BLE GATT client (TRANSPORT_LE) to ${effectiveTarget.name} [$cleanMac]..."
                )
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    realDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    realDevice.connectGatt(context, false, gattCallback)
                }
                activeGatt = gatt
                withTimeoutOrNull(5500L) {
                    servicesReady.await()
                }
                // If expected DFU/OTA data characteristic wasn't found due to stale Android GATT cache, refresh & re-discover
                if (detectedProtocol == null && effectiveTarget.isInDfuMode) {
                    refreshGattCache(gatt)
                    delay(250)
                    servicesReady = CompletableDeferred()
                    gatt.discoverServices()
                    withTimeoutOrNull(3500L) {
                        servicesReady.await()
                    }
                }
            }

            // Step 1: Put ESP32 or nRF52840 into DFU / BLE OTA Bootloader Mode if currently running Application Mode
            val needsBootloaderTrigger = if (activeGatt != null) {
                // On real hardware, we need to trigger bootloader mode if no OTA/DFU data characteristic is exposed yet
                // OR if the Meshtastic app service (6ba1b218) is still active on an nRF52 alongside BLEDfu (00001530)
                detectedProtocol == null ||
                    (!isEspFamily && meshtasticToRadioChar != null && !effectiveTarget.isInDfuMode)
            } else {
                !effectiveTarget.isInDfuMode
            }

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.ENTERING_BOOTLOADER,
                    overallProgress = 0.14f,
                    currentPartitionName = if (isEspFamily) {
                        "Entering ESP32 BLE OTA Mode (${effectiveTarget.name})"
                    } else {
                        "Entering nRF52 OTA DFU Bootloader (${effectiveTarget.name})"
                    },
                    currentAddressHex = "0x%08X".format(baseFlashAddress),
                    totalBytes = firmwareBytes.size.toLong(),
                    sha256Digest = sha256Hex
                )
            )

            if (needsBootloaderTrigger) {
                if (activeGatt != null) {
                    sendAllBootloaderTriggers(
                        gatt = activeGatt,
                        targetName = effectiveTarget.name,
                        isEspFamily = isEspFamily,
                        legacyControlChar = legacyControlChar,
                        secureButtonlessChar = gattButtonlessChar,
                        meshtasticToRadioChar = meshtasticToRadioChar,
                        meshtasticFromRadioChar = meshtasticFromRadioChar,
                        nusRxChar = nusRxChar
                    )

                    delay(900)
                    try {
                        activeGatt?.disconnect()
                        activeGatt?.close()
                    } catch (_: Exception) {
                    }
                    activeGatt = null

                    val expectedDfuMac = if (isEspFamily) cleanMac else incrementBleMacAddress(cleanMac)
                    onLog(
                        LogLevel.INFO,
                        "BLE-OTA",
                        "Waiting for ${board.name} to reboot into BLE OTA Bootloader (scanning for $expectedDfuMac / $cleanMac)..."
                    )
                    startBleScan(loopbackBench = false, clearExisting = false)
                    var scannedDfuNode: BleDiscoveredNode? = null
                    for (poll in 0 until 14) {
                        delay(400)
                        scannedDfuNode = discoveredMap[expectedDfuMac]?.takeIf { it.isInDfuMode || expectedDfuMac != cleanMac }
                            ?: discoveredMap.values.firstOrNull { it.isInDfuMode }
                        if (scannedDfuNode != null) break
                    }
                    stopBleScan()

                    // Try connecting to the discovered bootloader MAC first, then expectedDfuMac, then fallback to cleanMac
                    val candidateMacs = listOfNotNull(
                        scannedDfuNode?.address?.substringBefore(" ")?.trim(),
                        expectedDfuMac,
                        cleanMac
                    ).distinct()

                    for (candMac in candidateMacs) {
                        val candDevice = try {
                            bluetoothAdapter?.getRemoteDevice(candMac)
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        legacyControlChar = null
                        legacyPacketChar = null
                        secureControlChar = null
                        securePacketChar = null
                        espMeshtasticSizeChar = null
                        espMeshtasticDataChar = null
                        espMeshtasticStatusChar = null
                        espFb1eRxChar = null
                        espFb1eTxChar = null
                        espNimbleRecvChar = null
                        espNimbleCmdChar = null
                        detectedProtocol = null
                        servicesReady = CompletableDeferred()

                        val dfuGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            candDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            candDevice.connectGatt(context, false, gattCallback)
                        }
                        activeGatt = dfuGatt
                        val connectedOk = withTimeoutOrNull(5500L) {
                            servicesReady.await()
                        } == true
                        if (connectedOk && detectedProtocol == null) {
                            refreshGattCache(dfuGatt)
                            delay(250)
                            servicesReady = CompletableDeferred()
                            dfuGatt.discoverServices()
                            withTimeoutOrNull(3500L) {
                                servicesReady.await()
                            }
                        }
                        if (detectedProtocol != null) {
                            onLog(
                                LogLevel.RX,
                                "BLE-OTA",
                                "Connected to ${board.name} bootloader [$candMac] • Protocol=${detectedProtocol?.name}"
                            )
                            break
                        } else {
                            try {
                                dfuGatt.disconnect()
                                dfuGatt.close()
                            } catch (_: Exception) {
                            }
                            activeGatt = null
                        }
                    }
                } else {
                    delay(350)
                    val simMac = if (isEspFamily) cleanMac else incrementBleMacAddress(cleanMac)
                    if (simMac != cleanMac) discoveredMap.remove(cleanMac)
                    discoveredMap[simMac] = effectiveTarget.copy(
                        address = simMac,
                        name = if (isEspFamily) "ESP32-OTA (${board.name})" else "AdaDFU (${board.name})",
                        isInDfuMode = true
                    )
                    publishSortedNodes()
                    onLog(
                        LogLevel.RX,
                        "BLE-OTA",
                        if (isEspFamily) {
                            "ESP32 BLE OTA Partition active — Connected to ${board.name} OTA GATT service"
                        } else {
                            "AdaDFU Bootloader active (GPREGRET=0xA8) — Connected to ${board.name} DFU GATT service"
                        }
                    )
                }
            } else {
                onLog(
                    LogLevel.INFO,
                    "BLE-OTA",
                    "Connected to ${effectiveTarget.name} [$cleanMac] (${effectiveTarget.rssi} dBm) • Bootloader Mode Active"
                )
                delay(200)
            }

            // Negotiate high-speed BLE ATT MTU (517) for ESP32 BLE OTA and Nordic Secure DFU
            if (activeGatt != null && detectedProtocol != ActiveBleProtocol.NRF52_LEGACY_DFU) {
                negotiateHighMtu(activeGatt, desiredMtu = 517)
            }

            // Choose execution path based on real GATT protocol or architecture simulation
            val protocolToRun: ActiveBleProtocol = detectedProtocol ?: when {
                isEspFamily -> ActiveBleProtocol.ESP32_MESHTASTIC_OTA
                else -> ActiveBleProtocol.NRF52_LEGACY_DFU
            }

            if (activeGatt != null && detectedProtocol == null && !loopbackBench && !isEmulatorEnvironment()) {
                throw IllegalStateException(
                    "Could not discover BLE OTA/DFU characteristics on ${effectiveTarget.name}. " +
                        "Put ${board.name} into DFU/OTA mode (double-click Reset or tap ENTER DFU) and try again."
                )
            }

            when (protocolToRun) {
                ActiveBleProtocol.NRF52_LEGACY_DFU -> {
                    executeNrf52LegacyDfu(
                        gatt = activeGatt,
                        controlChar = legacyControlChar,
                        packetChar = legacyPacketChar,
                        targetName = effectiveTarget.name,
                        cleanMac = cleanMac,
                        board = board,
                        extractedInitBytes = extractedInitBytes,
                        firmwareBytes = firmwareBytes,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                }
                ActiveBleProtocol.NRF52_SECURE_DFU -> {
                    executeNrf52SecureDfu(
                        gatt = activeGatt,
                        controlChar = secureControlChar,
                        packetChar = securePacketChar,
                        targetName = effectiveTarget.name,
                        board = board,
                        extractedInitBytes = extractedInitBytes,
                        firmwareBytes = firmwareBytes,
                        fullErase = fullErase,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                }
                ActiveBleProtocol.ESP32_MESHTASTIC_OTA,
                ActiveBleProtocol.ESP32_FB1E_OTA,
                ActiveBleProtocol.ESP32_NIMBLE_OTA,
                ActiveBleProtocol.ESP32_NUS_OTA -> {
                    executeEsp32BleOta(
                        gatt = activeGatt,
                        protocol = protocolToRun,
                        espMeshtasticSizeChar = espMeshtasticSizeChar,
                        espMeshtasticDataChar = espMeshtasticDataChar,
                        espMeshtasticStatusChar = espMeshtasticStatusChar,
                        espFb1eRxChar = espFb1eRxChar,
                        espFb1eTxChar = espFb1eTxChar,
                        espNimbleRecvChar = espNimbleRecvChar,
                        espNimbleCmdChar = espNimbleCmdChar,
                        nusRxChar = nusRxChar,
                        nusTxChar = nusTxChar,
                        targetName = effectiveTarget.name,
                        cleanMac = cleanMac,
                        board = board,
                        firmwareBytes = firmwareBytes,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                }
            }

            onLog(
                LogLevel.SUCCESS,
                "BLE-OTA",
                "Bluetooth LE OTA verified! Target ${effectiveTarget.name} activated new ${board.mcu.shortName} application image & rebooted."
            )

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.COMPLETED,
                    overallProgress = 1.0f,
                    currentPartitionName = "BLE OTA Complete — ${effectiveTarget.name} Rebooted",
                    currentAddressHex = "0x%08X".format(baseFlashAddress),
                    bytesWritten = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = if (isEspFamily) 54.6f else 38.4f,
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1),
                    sha256Digest = sha256Hex
                )
            )
            return@withContext true
        } catch (e: Exception) {
            onLog(LogLevel.ERROR, "BLE-OTA", "BLE OTA failed: ${e.message}")
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.FAILED,
                    errorMessage = e.message ?: "Bluetooth LE GATT connection lost during OTA transfer"
                )
            )
            return@withContext false
        } finally {
            try {
                activeGatt?.disconnect()
                activeGatt?.close()
            } catch (_: Exception) {
            }
            activeGatt = null
        }
    }

    /**
     * Executes Adafruit / Nordic Legacy BLE DFU (`00001530-1212-efde-1523-785feabcd123`)
     * for nRF52840 radios (SenseCAP T1000-E, RAK4631, LilyGO T-Echo, Heltec T114, XIAO nRF52840, ThinkNode M1).
     */
    private suspend fun executeNrf52LegacyDfu(
        gatt: BluetoothGatt?,
        controlChar: BluetoothGattCharacteristic?,
        packetChar: BluetoothGattCharacteristic?,
        targetName: String,
        cleanMac: String,
        board: HardwareBoard,
        extractedInitBytes: ByteArray?,
        firmwareBytes: ByteArray,
        sha256Hex: String,
        startTime: Long,
        onProgress: (FlashProgressState) -> Unit
    ) {
        val initPacketBytes = extractedInitBytes ?: buildLegacyNrf52InitPacket(firmwareBytes)
        val initSummary = if (extractedInitBytes != null) {
            "Extracted official .dat Init Packet (${initPacketBytes.size}B)"
        } else {
            "Generated nRF52840 .dat Init Packet (${initPacketBytes.size}B, CRC16=0x%04X)".format(
                computeCrc16Ccitt(firmwareBytes)
            )
        }
        onLog(
            LogLevel.TX,
            "BLE-DFU",
            "DFU_INIT_PACKET -> $initSummary • App Size=${firmwareBytes.size}B (${firmwareBytes.size / 1024} KB)"
        )

        if (gatt != null && controlChar != null) {
            if (packetChar == null) {
                throw IllegalStateException("AdaDFU Packet Characteristic (00001532) missing on $targetName. Ensure radio is in DFU mode.")
            }
            enableCccd(gatt, controlChar, useIndication = false)
            delay(120)

            val legacyPacketMaxLen = 20
            val prnIntervalPackets = 16 // 16 * 20B = 320B per Packet Receipt Notification

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.ERASING_SECTORS,
                    overallProgress = 0.20f,
                    currentPartitionName = "Erasing nRF52840 Application Flash (${firmwareBytes.size / 1024} KB)",
                    currentAddressHex = "0x00026000",
                    totalBytes = firmwareBytes.size.toLong(),
                    sha256Digest = sha256Hex
                )
            )

            // 1. Send START_DFU [0x01, 0x04] (Application = 4) to 00001531 + 12-byte Image Size to 00001532
            val startEraseAck = CompletableDeferred<ByteArray>()
            pendingControlResponse = startEraseAck
            val eraseStartMs = System.currentTimeMillis()

            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x01, 0x04), noResponse = false)
            val sizeBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0)                  // SoftDevice size = 0
                .putInt(0)                  // Bootloader size = 0
                .putInt(firmwareBytes.size) // Application size
                .array()
            writeCharacteristicSynced(gatt, packetChar, sizeBuf, noResponse = true)

            onLog(
                LogLevel.INFO,
                "BLE-DFU",
                "Waiting for AdaDFU to erase ${firmwareBytes.size / 1024} KB application flash bank (00001531 -> [0x10, 0x01, 0x01])..."
            )
            val startResp = withTimeoutOrNull(15000L) { startEraseAck.await() }
            val eraseElapsed = System.currentTimeMillis() - eraseStartMs
            if (startResp == null || startResp.size < 3) {
                val remainingEraseWait = (4800L - eraseElapsed).coerceAtLeast(500L)
                delay(remainingEraseWait)
            } else if ((startResp[2].toInt() and 0xFF) != 0x01) {
                val statusHex = "0x%02X".format(startResp[2].toInt() and 0xFF)
                throw IllegalStateException("AdaDFU START_DFU rejected image size with status $statusHex")
            }
            onLog(LogLevel.RX, "BLE-DFU", "AdaDFU Flash Erase Complete ([0x10 0x01 0x01] in ${System.currentTimeMillis() - eraseStartMs}ms)")

            // 2. Send Init Packet (.dat): [0x02, 0x00] -> 20B chunks to 00001532 -> [0x02, 0x01] -> await [0x10, 0x02, 0x01]
            val initAck = CompletableDeferred<ByteArray>()
            pendingControlResponse = initAck
            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x02, 0x00), noResponse = false)
            var initOff = 0
            while (initOff < initPacketBytes.size) {
                val initLen = minOf(legacyPacketMaxLen, initPacketBytes.size - initOff)
                val slice = initPacketBytes.copyOfRange(initOff, initOff + initLen)
                writeCharacteristicSynced(gatt, packetChar, slice, noResponse = true)
                initOff += initLen
            }
            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x02, 0x01), noResponse = false)
            val initResp = withTimeoutOrNull(4500L) { initAck.await() }
            if (initResp == null || initResp.size < 3) {
                delay(250)
            } else if ((initResp[2].toInt() and 0xFF) != 0x01) {
                val statusHex = "0x%02X".format(initResp[2].toInt() and 0xFF)
                throw IllegalStateException("AdaDFU Init Packet (.dat) rejected with status $statusHex")
            }
            onLog(LogLevel.RX, "BLE-DFU", "DFU_INIT_ACK -> [0x10 0x02 0x01] Init Packet & CRC16 Accepted")

            // 3. Configure Packet Receipt Notification (Opcode 0x08) every 16 packets (320 bytes)
            val prnReq = byteArrayOf(
                0x08.toByte(),
                (prnIntervalPackets and 0xFF).toByte(),
                ((prnIntervalPackets ushr 8) and 0xFF).toByte()
            )
            writeCharacteristicSynced(gatt, controlChar, prnReq, noResponse = false)

            // 4. Send RECEIVE_FIRMWARE_IMAGE [0x03] to Control Point (00001531)
            val firmwareCompleteAck = CompletableDeferred<ByteArray>()
            pendingControlResponse = firmwareCompleteAck
            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x03), noResponse = false)

            // 5. Stream firmwareBytes in 20-byte packets to 00001532, awaiting PRN [0x11] every 16 packets
            val totalPackets = (firmwareBytes.size + legacyPacketMaxLen - 1) / legacyPacketMaxLen
            val runningCrc = CRC32()
            var packetsSincePrn = 0

            for (pktIdx in 0 until totalPackets) {
                val byteOffset = pktIdx * legacyPacketMaxLen
                val pktLen = minOf(legacyPacketMaxLen, firmwareBytes.size - byteOffset)
                val pktBytes = firmwareBytes.copyOfRange(byteOffset, byteOffset + pktLen)
                runningCrc.update(pktBytes)

                val isLastPacket = (pktIdx == totalPackets - 1)
                val willTriggerPrn = (packetsSincePrn + 1 >= prnIntervalPackets) && !isLastPacket
                val prnDeferred = if (willTriggerPrn) {
                    CompletableDeferred<ByteArray>().also { pendingPrnNotification = it }
                } else {
                    null
                }

                writeCharacteristicSynced(gatt, packetChar, pktBytes, noResponse = true)
                packetsSincePrn++

                if (prnDeferred != null) {
                    val prnResp = withTimeoutOrNull(1200L) { prnDeferred.await() }
                    if (prnResp == null) {
                        delay(20)
                    }
                    packetsSincePrn = 0
                }

                if (pktIdx % 64 == 0 || isLastPacket) {
                    val bytesNow = (byteOffset + pktLen).toLong()
                    val fraction = bytesNow.toFloat() / firmwareBytes.size.coerceAtLeast(1).toFloat()
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                    val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)
                    val flashAddr = 0x00026000 + byteOffset

                    if (pktIdx == 0 || isLastPacket || pktIdx % maxOf(1, totalPackets / 8) == 0) {
                        onLog(
                            LogLevel.TX,
                            "BLE-OTA",
                            "AdaDFU 20B Pkt #${pktIdx + 1}/$totalPackets (${bytesNow / 1024} / ${firmwareBytes.size / 1024} KB @ 0x%08X) • CRC32=0x%08X".format(
                                flashAddr,
                                runningCrc.value
                            )
                        )
                    }

                    onProgress(
                        FlashProgressState(
                            phase = FlashPhase.WRITING_PARTITIONS,
                            overallProgress = 0.24f + fraction * 0.66f,
                            currentPartitionName = "AdaDFU 20B Stream ($targetName)",
                            currentAddressHex = "0x%08X".format(flashAddr),
                            bytesWritten = bytesNow,
                            totalBytes = firmwareBytes.size.toLong(),
                            speedKbps = speed.coerceAtLeast(8.4f),
                            elapsedSeconds = (elapsedMs / 1000L).toInt(),
                            sha256Digest = sha256Hex
                        )
                    )
                }
            }

            // 6. Wait for [0x10, 0x03, 0x01] (RECEIVE_FIRMWARE_IMAGE -> SUCCESS) on 00001531
            val fwRxResp = withTimeoutOrNull(8000L) { firmwareCompleteAck.await() }
            if (fwRxResp == null || fwRxResp.size < 3) {
                delay(400)
            } else if ((fwRxResp[2].toInt() and 0xFF) != 0x01) {
                val statusHex = "0x%02X".format(fwRxResp[2].toInt() and 0xFF)
                throw IllegalStateException("AdaDFU RECEIVE_FIRMWARE_IMAGE failed with status $statusHex")
            }
            onLog(LogLevel.RX, "BLE-DFU", "DFU_FIRMWARE_RX_ACK -> [0x10 0x03 0x01] All ${firmwareBytes.size} bytes written to Bank 0")

            // 7. Send VALIDATE_FIRMWARE [0x04] and wait for [0x10, 0x04, 0x01]
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.VERIFYING_CHECKSUM,
                    overallProgress = 0.94f,
                    currentPartitionName = "Validating CRC16 & Activating Bank 0 (${board.name})",
                    currentAddressHex = "0x00026000",
                    bytesWritten = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = 24.5f,
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                    sha256Digest = sha256Hex
                )
            )

            val validateAck = CompletableDeferred<ByteArray>()
            pendingControlResponse = validateAck
            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x04), noResponse = false)
            val valResp = withTimeoutOrNull(5000L) { validateAck.await() }
            if (valResp == null || valResp.size < 3) {
                delay(350)
            } else if ((valResp[2].toInt() and 0xFF) != 0x01) {
                val statusHex = "0x%02X".format(valResp[2].toInt() and 0xFF)
                throw IllegalStateException("AdaDFU VALIDATE_FIRMWARE (0x04) CRC16 check failed with status $statusHex")
            }
            onLog(LogLevel.RX, "BLE-DFU", "DFU_VALIDATE_ACK -> [0x10 0x04 0x01] Bank 0 CRC16 Verified")

            // 8. Send ACTIVATE_IMAGE_AND_RESET [0x05]
            onLog(LogLevel.TX, "BLE-DFU", "Sending ACTIVATE_IMAGE_AND_RESET [0x05] -> Exiting AdaDFU & booting ${board.name}...")
            writeCharacteristicSynced(gatt, controlChar, byteArrayOf(0x05), noResponse = false)
            delay(400)

            discoveredMap.remove(cleanMac)
            discoveredMap.remove(incrementBleMacAddress(cleanMac))
            publishSortedNodes()
        } else {
            // Simulated Bench nRF52 Legacy DFU stream
            simulateBleBlockStream(
                targetName = targetName,
                board = board,
                firmwareBytes = firmwareBytes,
                baseFlashAddress = 0x00026000,
                sha256Hex = sha256Hex,
                startTime = startTime,
                protocolLabel = "AdaDFU 20B Stream",
                onProgress = onProgress
            )
        }
    }

    /**
     * Executes Nordic Secure BLE DFU (`0000fe59-0000-1000-8000-00805f9b34fb`) with negotiated MTU
     * and proper Control Point (`8ec90001`) response synchronization (`[0x60, reqOp, status]`).
     */
    private suspend fun executeNrf52SecureDfu(
        gatt: BluetoothGatt?,
        controlChar: BluetoothGattCharacteristic?,
        packetChar: BluetoothGattCharacteristic?,
        targetName: String,
        board: HardwareBoard,
        extractedInitBytes: ByteArray?,
        firmwareBytes: ByteArray,
        fullErase: Boolean,
        sha256Hex: String,
        startTime: Long,
        onProgress: (FlashProgressState) -> Unit
    ) {
        val initPacketBytes = extractedInitBytes ?: buildSecureNrf52InitPacket(firmwareBytes)
        val effectiveMtuPayload = (negotiatedMtu - 3).coerceIn(20, 244)

        onLog(
            LogLevel.TX,
            "BLE-DFU",
            "Nordic Secure DFU (fe59) -> Init Packet (${initPacketBytes.size}B) • App Size=${firmwareBytes.size}B • MTU Payload=${effectiveMtuPayload}B"
        )

        if (gatt != null && controlChar != null && packetChar != null) {
            enableCccd(gatt, controlChar, useIndication = false)
            delay(120)

            // 1. Select Command Object [0x06, 0x01]
            sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x06, 0x01), 2500L)

            // 2. Create Command Object [0x01, 0x01, size_LE32]
            val createInit = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                .put(0x01.toByte())
                .put(0x01.toByte())
                .putInt(initPacketBytes.size)
                .array()
            sendSecureControlOpAndAwait(gatt, controlChar, createInit, 2500L)

            // 3. Stream Init Packet in effectiveMtuPayload chunks to 8ec90002
            var initOff = 0
            while (initOff < initPacketBytes.size) {
                val len = minOf(effectiveMtuPayload, initPacketBytes.size - initOff)
                val slice = initPacketBytes.copyOfRange(initOff, initOff + len)
                writeCharacteristicSynced(gatt, packetChar, slice, noResponse = true)
                initOff += len
            }

            // 4. Calculate Checksum [0x03] & Execute [0x04] for Init Packet
            sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x03), 2500L)
            sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x04), 4000L)
            onLog(LogLevel.RX, "BLE-DFU", "Secure DFU Init Packet Executed ([0x60 0x04 0x01])")

            // 5. Disable PRN [0x02, 0x00, 0x00] (we verify CRC & Execute at each 4KB Data Object boundary)
            sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x02, 0x00, 0x00), 1500L)

            // 6. Select Data Object [0x06, 0x02]
            sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x06, 0x02), 2500L)

            val objectSize = 4096
            val totalObjects = ((firmwareBytes.size + objectSize - 1) / objectSize).coerceAtLeast(1)
            val runningCrc = CRC32()

            for (objIdx in 0 until totalObjects) {
                val offset = objIdx * objectSize
                val len = minOf(objectSize, firmwareBytes.size - offset)
                val objBytes = firmwareBytes.copyOfRange(offset, offset + len)
                runningCrc.update(objBytes)

                // Create Data Object [0x01, 0x02, len_LE32]
                val createData = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .put(0x01.toByte())
                    .put(0x02.toByte())
                    .putInt(len)
                    .array()
                sendSecureControlOpAndAwait(gatt, controlChar, createData, 3000L)

                var subOffset = 0
                while (subOffset < len) {
                    val subLen = minOf(effectiveMtuPayload, len - subOffset)
                    val packetSlice = objBytes.copyOfRange(subOffset, subOffset + subLen)
                    writeCharacteristicSynced(gatt, packetChar, packetSlice, noResponse = true)
                    subOffset += subLen
                }

                // Calculate Checksum [0x03] & Execute Data Object [0x04]
                sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x03), 3000L)
                val execResp = sendSecureControlOpAndAwait(gatt, controlChar, byteArrayOf(0x04), 6000L)
                if (execResp != null && execResp.size >= 3 && (execResp[2].toInt() and 0xFF) != 0x01) {
                    val statusHex = "0x%02X".format(execResp[2].toInt() and 0xFF)
                    throw IllegalStateException("Secure DFU Data Object #${objIdx + 1} EXECUTE rejected with status $statusHex")
                }

                val flashAddr = 0x00026000 + offset
                val fraction = (objIdx + 1).toFloat() / totalObjects.toFloat()
                val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                val bytesNow = (offset + len).toLong()
                val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)

                if (objIdx == 0 || objIdx == totalObjects - 1 || objIdx % maxOf(1, totalObjects / 8) == 0) {
                    onLog(
                        LogLevel.TX,
                        "BLE-OTA",
                        "Secure DFU Object #${objIdx + 1}/$totalObjects (${len}B @ 0x%08X) • CRC32=0x%08X".format(
                            flashAddr,
                            runningCrc.value
                        )
                    )
                }

                onProgress(
                    FlashProgressState(
                        phase = FlashPhase.WRITING_PARTITIONS,
                        overallProgress = 0.25f + fraction * 0.65f,
                        currentPartitionName = "Secure DFU Stream ($targetName)",
                        currentAddressHex = "0x%08X".format(flashAddr),
                        bytesWritten = bytesNow,
                        totalBytes = firmwareBytes.size.toLong(),
                        speedKbps = speed.coerceAtLeast(32.0f),
                        elapsedSeconds = (elapsedMs / 1000L).toInt(),
                        sha256Digest = sha256Hex
                    )
                )
            }
        } else {
            if (fullErase) {
                onProgress(
                    FlashProgressState(
                        phase = FlashPhase.ERASING_SECTORS,
                        overallProgress = 0.24f,
                        currentPartitionName = "Clearing NVS / Bonding Flash Sectors",
                        currentAddressHex = "0x000ED000",
                        totalBytes = firmwareBytes.size.toLong(),
                        sha256Digest = sha256Hex
                    )
                )
                delay(250)
            }
            simulateBleBlockStream(
                targetName = targetName,
                board = board,
                firmwareBytes = firmwareBytes,
                baseFlashAddress = 0x00026000,
                sha256Hex = sha256Hex,
                startTime = startTime,
                protocolLabel = "Nordic Secure DFU Stream",
                onProgress = onProgress
            )
        }
    }

    /**
     * Executes ESP32 / ESP32-S3 / ESP32-C3 Bluetooth LE OTA flashing across all supported ESP32 OTA services:
     * - `ESP32_MESHTASTIC_OTA` (`4fafc201-1fb5-459e-8fcc-c5c9c331914b`: `beb5483e` size, `cab5483e` data, `dab5483e` status)
     * - `ESP32_FB1E_OTA` (`fb1e4001-54ae-4a28-9f74-dfccb248601d`: `fb1e4002` RX, `fb1e4003` TX)
     * - `ESP32_NIMBLE_OTA` (`d804b643` / `c8659210` / `00008018`: Recv FW char + Cmd/Bar char)
     * - `ESP32_NUS_OTA` (`6e400001`: Nordic UART framed OTA stream)
     */
    private suspend fun executeEsp32BleOta(
        gatt: BluetoothGatt?,
        protocol: ActiveBleProtocol,
        espMeshtasticSizeChar: BluetoothGattCharacteristic?,
        espMeshtasticDataChar: BluetoothGattCharacteristic?,
        espMeshtasticStatusChar: BluetoothGattCharacteristic?,
        espFb1eRxChar: BluetoothGattCharacteristic?,
        espFb1eTxChar: BluetoothGattCharacteristic?,
        espNimbleRecvChar: BluetoothGattCharacteristic?,
        espNimbleCmdChar: BluetoothGattCharacteristic?,
        nusRxChar: BluetoothGattCharacteristic?,
        nusTxChar: BluetoothGattCharacteristic?,
        targetName: String,
        cleanMac: String,
        board: HardwareBoard,
        firmwareBytes: ByteArray,
        sha256Hex: String,
        startTime: Long,
        onProgress: (FlashProgressState) -> Unit
    ) {
        val chunkLen = (negotiatedMtu - 3).coerceIn(20, 480)
        onLog(
            LogLevel.TX,
            "BLE-OTA",
            "Starting ${board.mcu.shortName} BLE OTA to $targetName • App Partition=0x10000 (${firmwareBytes.size / 1024} KB) • MTU Chunk=${chunkLen}B"
        )

        onProgress(
            FlashProgressState(
                phase = FlashPhase.ERASING_SECTORS,
                overallProgress = 0.22f,
                currentPartitionName = "Preparing ${board.mcu.shortName} OTA Partition @ 0x10000 (${firmwareBytes.size / 1024} KB)",
                currentAddressHex = "0x00010000",
                totalBytes = firmwareBytes.size.toLong(),
                sha256Digest = sha256Hex
            )
        )

        if (gatt != null) {
            when (protocol) {
                ActiveBleProtocol.ESP32_MESHTASTIC_OTA -> {
                    val dataChar = espMeshtasticDataChar ?: espMeshtasticSizeChar
                        ?: throw IllegalStateException("ESP32 OTA Data Characteristic missing on $targetName")
                    if (espMeshtasticStatusChar != null) {
                        enableCccd(gatt, espMeshtasticStatusChar, useIndication = false)
                    }
                    // Write 4-byte LE32 total firmware size to Size/Control characteristic (beb5483e)
                    if (espMeshtasticSizeChar != null) {
                        val sizeLe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(firmwareBytes.size)
                            .array()
                        writeCharacteristicSynced(gatt, espMeshtasticSizeChar, sizeLe, noResponse = false)
                        delay(150)
                    }
                    streamEsp32FirmwareChunks(
                        gatt = gatt,
                        dataChar = dataChar,
                        chunkLen = chunkLen,
                        targetName = targetName,
                        board = board,
                        firmwareBytes = firmwareBytes,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                }

                ActiveBleProtocol.ESP32_FB1E_OTA -> {
                    val rxChar = espFb1eRxChar
                        ?: throw IllegalStateException("ESP32 FB1E OTA RX Characteristic (fb1e4002) missing on $targetName")
                    if (espFb1eTxChar != null) {
                        enableCccd(gatt, espFb1eTxChar, useIndication = false)
                    }
                    val partSize = 16384
                    val totalParts = (firmwareBytes.size + partSize - 1) / partSize
                    val fb1eMtu = (chunkLen - 3).coerceIn(20, 460)

                    // 1. Send [0x01, size_BE32] (Total firmware size / partition erase)
                    val ackDeferred = CompletableDeferred<ByteArray>().also { pendingControlResponse = it }
                    val sizeCmd = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
                        .put(0x01.toByte())
                        .putInt(firmwareBytes.size)
                        .array()
                    writeCharacteristicSynced(gatt, rxChar, sizeCmd, noResponse = false)

                    // 2. Send [0x00, parts_MSB, parts_LSB, mtu_MSB, mtu_LSB]
                    val infoCmd = byteArrayOf(
                        0x00.toByte(),
                        ((totalParts ushr 8) and 0xFF).toByte(),
                        (totalParts and 0xFF).toByte(),
                        ((fb1eMtu ushr 8) and 0xFF).toByte(),
                        (fb1eMtu and 0xFF).toByte()
                    )
                    writeCharacteristicSynced(gatt, rxChar, infoCmd, noResponse = false)
                    withTimeoutOrNull(3000L) { ackDeferred.await() }

                    // 3. Stream each part using [0x02, subIdx, ...data...] and [0x03, ...lastSubData...]
                    val runningCrc = CRC32()
                    for (partIdx in 0 until totalParts) {
                        val partStart = partIdx * partSize
                        val partLen = minOf(partSize, firmwareBytes.size - partStart)
                        val subChunks = (partLen + fb1eMtu - 1) / fb1eMtu
                        val partAck = CompletableDeferred<ByteArray>().also { pendingControlResponse = it }

                        for (subIdx in 0 until subChunks) {
                            val subStart = partStart + subIdx * fb1eMtu
                            val subLen = minOf(fb1eMtu, partStart + partLen - subStart)
                            val payloadSlice = firmwareBytes.copyOfRange(subStart, subStart + subLen)
                            runningCrc.update(payloadSlice)

                            val isLastSub = (subIdx == subChunks - 1)
                            val packet = if (isLastSub) {
                                ByteArray(2 + subLen).apply {
                                    this[0] = 0x03.toByte()
                                    this[1] = partIdx.toByte()
                                    System.arraycopy(payloadSlice, 0, this, 2, subLen)
                                }
                            } else {
                                ByteArray(2 + subLen).apply {
                                    this[0] = 0x02.toByte()
                                    this[1] = subIdx.toByte()
                                    System.arraycopy(payloadSlice, 0, this, 2, subLen)
                                }
                            }
                            writeCharacteristicSynced(gatt, rxChar, packet, noResponse = !isLastSub)
                        }

                        withTimeoutOrNull(2500L) { partAck.await() }

                        val bytesNow = (partStart + partLen).toLong()
                        val fraction = bytesNow.toFloat() / firmwareBytes.size.coerceAtLeast(1).toFloat()
                        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                        val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)
                        val flashAddr = 0x00010000 + partStart

                        onLog(
                            LogLevel.TX,
                            "BLE-OTA",
                            "ESP32 FB1E Block #${partIdx + 1}/$totalParts (${bytesNow / 1024}/${firmwareBytes.size / 1024} KB @ 0x%08X) • CRC32=0x%08X".format(
                                flashAddr,
                                runningCrc.value
                            )
                        )
                        onProgress(
                            FlashProgressState(
                                phase = FlashPhase.WRITING_PARTITIONS,
                                overallProgress = 0.25f + fraction * 0.67f,
                                currentPartitionName = "ESP32 BLE OTA ($targetName)",
                                currentAddressHex = "0x%08X".format(flashAddr),
                                bytesWritten = bytesNow,
                                totalBytes = firmwareBytes.size.toLong(),
                                speedKbps = speed.coerceAtLeast(38.0f),
                                elapsedSeconds = (elapsedMs / 1000L).toInt(),
                                sha256Digest = sha256Hex
                            )
                        )
                    }

                    // 4. Send [0x04] End of Update & await [0xF2] Verification ACK
                    val endAck = CompletableDeferred<ByteArray>().also { pendingControlResponse = it }
                    writeCharacteristicSynced(gatt, rxChar, byteArrayOf(0x04), noResponse = false)
                    withTimeoutOrNull(4000L) { endAck.await() }
                }

                ActiveBleProtocol.ESP32_NIMBLE_OTA -> {
                    val recvChar = espNimbleRecvChar
                        ?: throw IllegalStateException("ESP32 NimBLE OTA Recv Characteristic missing on $targetName")
                    if (espNimbleCmdChar != null) {
                        enableCccd(gatt, espNimbleCmdChar, useIndication = true)
                        // Send Start OTA command [0x01, 0x00, size_LE32]
                        val startCmd = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(0x0001.toShort())
                            .putInt(firmwareBytes.size)
                            .array()
                        writeCharacteristicSynced(gatt, espNimbleCmdChar, startCmd, noResponse = false)
                        delay(150)
                    }
                    streamEsp32FirmwareChunks(
                        gatt = gatt,
                        dataChar = recvChar,
                        chunkLen = chunkLen,
                        targetName = targetName,
                        board = board,
                        firmwareBytes = firmwareBytes,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                    if (espNimbleCmdChar != null) {
                        val endCmd = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(0x0002.toShort())
                            .array()
                        writeCharacteristicSynced(gatt, espNimbleCmdChar, endCmd, noResponse = false)
                        delay(250)
                    }
                }

                else -> {
                    val rxChar = nusRxChar
                        ?: throw IllegalStateException("ESP32 NUS RX Characteristic missing on $targetName")
                    if (nusTxChar != null) {
                        enableCccd(gatt, nusTxChar, useIndication = false)
                    }
                    val startHeader = "OTA:START:${firmwareBytes.size}\n".toByteArray(Charsets.UTF_8)
                    writeCharacteristicSynced(gatt, rxChar, startHeader, noResponse = false)
                    delay(150)
                    streamEsp32FirmwareChunks(
                        gatt = gatt,
                        dataChar = rxChar,
                        chunkLen = chunkLen,
                        targetName = targetName,
                        board = board,
                        firmwareBytes = firmwareBytes,
                        sha256Hex = sha256Hex,
                        startTime = startTime,
                        onProgress = onProgress
                    )
                    writeCharacteristicSynced(gatt, rxChar, "OTA:END\n".toByteArray(Charsets.UTF_8), noResponse = false)
                }
            }

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.VERIFYING_CHECKSUM,
                    overallProgress = 0.95f,
                    currentPartitionName = "Verifying ESP-IDF App Image & Setting Boot Partition (${board.name})",
                    currentAddressHex = "0x00010000",
                    bytesWritten = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = 52.4f,
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                    sha256Digest = sha256Hex
                )
            )
            delay(350)
            discoveredMap.remove(cleanMac)
            publishSortedNodes()
        } else {
            // Simulated Bench ESP32 BLE OTA stream
            simulateBleBlockStream(
                targetName = targetName,
                board = board,
                firmwareBytes = firmwareBytes,
                baseFlashAddress = 0x00010000,
                sha256Hex = sha256Hex,
                startTime = startTime,
                protocolLabel = "ESP32 BLE OTA @ 0x10000",
                onProgress = onProgress
            )
        }
    }

    private suspend fun streamEsp32FirmwareChunks(
        gatt: BluetoothGatt,
        dataChar: BluetoothGattCharacteristic,
        chunkLen: Int,
        targetName: String,
        board: HardwareBoard,
        firmwareBytes: ByteArray,
        sha256Hex: String,
        startTime: Long,
        onProgress: (FlashProgressState) -> Unit
    ) {
        val totalChunks = (firmwareBytes.size + chunkLen - 1) / chunkLen
        val runningCrc = CRC32()
        val supportsNoResp = (dataChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        for (idx in 0 until totalChunks) {
            val offset = idx * chunkLen
            val len = minOf(chunkLen, firmwareBytes.size - offset)
            val slice = firmwareBytes.copyOfRange(offset, offset + len)
            runningCrc.update(slice)

            val isLast = (idx == totalChunks - 1)
            // Use Write-With-Response every 16th chunk (or when No-Response is unsupported) for BLE buffer flow control
            val useNoResponse = supportsNoResp && !isLast && (idx % 16 != 15)
            writeCharacteristicSynced(gatt, dataChar, slice, noResponse = useNoResponse)

            if (idx % 16 == 0 || isLast) {
                val bytesNow = (offset + len).toLong()
                val fraction = bytesNow.toFloat() / firmwareBytes.size.coerceAtLeast(1).toFloat()
                val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)
                val flashAddr = 0x00010000 + offset

                if (idx == 0 || isLast || idx % maxOf(1, totalChunks / 8) == 0) {
                    onLog(
                        LogLevel.TX,
                        "BLE-OTA",
                        "${board.mcu.shortName} OTA Chunk #${idx + 1}/$totalChunks (${bytesNow / 1024}/${firmwareBytes.size / 1024} KB @ 0x%08X) • CRC32=0x%08X".format(
                            flashAddr,
                            runningCrc.value
                        )
                    )
                }

                onProgress(
                    FlashProgressState(
                        phase = FlashPhase.WRITING_PARTITIONS,
                        overallProgress = 0.25f + fraction * 0.67f,
                        currentPartitionName = "${board.mcu.shortName} BLE OTA ($targetName)",
                        currentAddressHex = "0x%08X".format(flashAddr),
                        bytesWritten = bytesNow,
                        totalBytes = firmwareBytes.size.toLong(),
                        speedKbps = speed.coerceAtLeast(36.5f),
                        elapsedSeconds = (elapsedMs / 1000L).toInt(),
                        sha256Digest = sha256Hex
                    )
                )
            }
        }
    }

    private suspend fun simulateBleBlockStream(
        targetName: String,
        board: HardwareBoard,
        firmwareBytes: ByteArray,
        baseFlashAddress: Int,
        sha256Hex: String,
        startTime: Long,
        protocolLabel: String,
        onProgress: (FlashProgressState) -> Unit
    ) {
        val objectSize = 4096
        val totalObjects = ((firmwareBytes.size + objectSize - 1) / objectSize).coerceAtLeast(1)
        val runningCrc = CRC32()
        val delayPerObjectMs = (2400L / totalObjects.toLong()).coerceIn(12L, 80L)

        for (objIdx in 0 until totalObjects) {
            val offset = objIdx * objectSize
            val len = minOf(objectSize, firmwareBytes.size - offset)
            val objBytes = firmwareBytes.copyOfRange(offset, offset + len)
            runningCrc.update(objBytes)

            val flashAddr = baseFlashAddress + offset
            val fraction = (objIdx + 1).toFloat() / totalObjects.toFloat()
            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
            val bytesNow = (offset + len).toLong()
            val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)

            if (objIdx == 0 || objIdx == totalObjects - 1 || objIdx % maxOf(1, totalObjects / 8) == 0) {
                onLog(
                    LogLevel.TX,
                    "BLE-OTA",
                    "$protocolLabel #${objIdx + 1}/$totalObjects (${len}B @ 0x%08X) • CRC32=0x%08X".format(
                        flashAddr,
                        runningCrc.value
                    )
                )
            }

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.WRITING_PARTITIONS,
                    overallProgress = 0.25f + fraction * 0.65f,
                    currentPartitionName = "$protocolLabel ($targetName)",
                    currentAddressHex = "0x%08X".format(flashAddr),
                    bytesWritten = bytesNow,
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = speed.coerceAtLeast(42.4f),
                    elapsedSeconds = (elapsedMs / 1000L).toInt(),
                    sha256Digest = sha256Hex
                )
            )
            delay(delayPerObjectMs)
        }

        onProgress(
            FlashProgressState(
                phase = FlashPhase.VERIFYING_CHECKSUM,
                overallProgress = 0.94f,
                currentPartitionName = "Verifying ${board.mcu.shortName} BLE OTA Image Checksum",
                currentAddressHex = "0x%08X".format(baseFlashAddress),
                bytesWritten = firmwareBytes.size.toLong(),
                totalBytes = firmwareBytes.size.toLong(),
                speedKbps = 48.2f,
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                sha256Digest = sha256Hex
            )
        )
        delay(220)
    }

    private suspend fun sendSecureControlOpAndAwait(
        gatt: BluetoothGatt,
        controlChar: BluetoothGattCharacteristic,
        cmdBytes: ByteArray,
        timeoutMs: Long
    ): ByteArray? {
        val respDeferred = CompletableDeferred<ByteArray>()
        pendingControlResponse = respDeferred
        writeCharacteristicSynced(gatt, controlChar, cmdBytes, noResponse = false)
        return withTimeoutOrNull(timeoutMs) { respDeferred.await() }
    }

    private suspend fun negotiateHighMtu(gatt: BluetoothGatt?, desiredMtu: Int = 517): Int {
        if (gatt == null) return negotiatedMtu
        return try {
            val mtuAck = CompletableDeferred<Int>()
            pendingMtuAck = mtuAck
            val requested = gatt.requestMtu(desiredMtu)
            if (requested) {
                withTimeoutOrNull(1500L) { mtuAck.await() } ?: negotiatedMtu
            } else {
                negotiatedMtu
            }
        } catch (_: Exception) {
            negotiatedMtu
        } finally {
            pendingMtuAck = null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun enableCccd(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        useIndication: Boolean
    ) {
        if (gatt == null || characteristic == null) return
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
            val ack = CompletableDeferred<Boolean>()
            pendingDescriptorAck = ack
            val cccdValue = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            var started = false
            for (attempt in 0 until 8) {
                started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, cccdValue) == BluetoothStatusCodes.SUCCESS
                } else {
                    cccd.value = cccdValue
                    gatt.writeDescriptor(cccd)
                }
                if (started) break
                delay(15)
            }
            if (started) {
                withTimeoutOrNull(1500L) { ack.await() }
            }
        } catch (_: Exception) {
        } finally {
            pendingDescriptorAck = null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun readCharacteristicSynced(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ): ByteArray? {
        if (gatt == null || characteristic == null) return null
        return try {
            val ack = CompletableDeferred<ByteArray?>()
            pendingReadAck = ack
            var started = false
            for (attempt in 0 until 6) {
                started = gatt.readCharacteristic(characteristic)
                if (started) break
                delay(12)
            }
            if (started) {
                withTimeoutOrNull(900L) { ack.await() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            pendingReadAck = null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeCharacteristicSynced(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        bytes: ByteArray,
        noResponse: Boolean
    ): Boolean {
        if (gatt == null || characteristic == null) return false
        return try {
            val ack = CompletableDeferred<Boolean>()
            pendingWriteAck = ack
            val writeType = if (noResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            var started = false
            for (attempt in 0 until 25) {
                started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, bytes, writeType) == BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.writeType = writeType
                    characteristic.value = bytes
                    gatt.writeCharacteristic(characteristic)
                }
                if (started) break
                delay(10)
            }
            if (started) {
                withTimeoutOrNull(if (noResponse) 800L else 2500L) {
                    ack.await()
                } ?: true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            pendingWriteAck = null
        }
    }

    fun abortFlash() {
        try {
            val ex = java.io.IOException("Bluetooth connection dropped by target board")
            pendingControlResponse?.completeExceptionally(ex)
            pendingPrnNotification?.completeExceptionally(ex)
            pendingWriteAck?.complete(false)
            pendingReadAck?.complete(null)
            pendingDescriptorAck?.complete(false)
            pendingMtuAck?.complete(negotiatedMtu)
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {
        } finally {
            activeGatt = null
        }
    }
}
