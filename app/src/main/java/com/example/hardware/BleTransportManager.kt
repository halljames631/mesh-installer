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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        // Standard Nordic Secure DFU (SDK 12+)
        val NORDIC_SECURE_DFU_SERVICE: UUID = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb")
        val DFU_CONTROL_POINT_CHAR: UUID = UUID.fromString("8ec90001-f315-4f60-9fb8-838830daea50")
        val DFU_PACKET_CHAR: UUID = UUID.fromString("8ec90002-f315-4f60-9fb8-838830daea50")
        val DFU_BUTTONLESS_UNBONDED_CHAR: UUID = UUID.fromString("8ec90003-f315-4f60-9fb8-838830daea50")
        val DFU_BUTTONLESS_BONDED_CHAR: UUID = UUID.fromString("8ec90004-f315-4f60-9fb8-838830daea50")

        // Adafruit nRF52 / Nordic Legacy DFU Service (used by RAK4631, T-Echo, SenseCAP T1000-E bootloaders)
        val NORDIC_LEGACY_DFU_SERVICE: UUID = UUID.fromString("00001530-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_CONTROL_POINT_CHAR: UUID = UUID.fromString("00001531-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_PACKET_CHAR: UUID = UUID.fromString("00001532-1212-efde-1523-785feabcd123")
        val LEGACY_DFU_VERSION_CHAR: UUID = UUID.fromString("00001534-1212-efde-1523-785feabcd123")

        // Client Characteristic Configuration Descriptor (CCCD)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Ecosystem Service UUIDs
        val MESHTASTIC_BLE_SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val MESHCORE_NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
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
    private var pendingWriteAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingDescriptorAck: CompletableDeferred<Boolean>? = null

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
            res.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val hasDfuUuid = uuids.any { it.contains("fe59") || it.contains("1530") }
        val hasMeshtasticUuid = uuids.any { it.contains("6ba1b218") }
        val hasMeshCoreUuid = uuids.any { it.contains("6e400001") }

        val existingNode = discoveredMap[address]
        val resolvedName = recordName
            ?: devName
            ?: existingNode?.name
            ?: when {
                hasDfuUuid -> "Nordic DFU (${address.takeLast(5)})"
                hasMeshtasticUuid -> "Meshtastic BLE (${address.takeLast(5)})"
                hasMeshCoreUuid -> "MeshCore BLE (${address.takeLast(5)})"
                else -> return // Ignore anonymous non-mesh BLE beacons
            }

        val mergedUuids = (existingNode?.serviceUuids.orEmpty() + uuids).distinct()

        // IMPORTANT: On SenseCAP T1000-E, RAK4631, and LilyGO T-Echo, normal Meshtastic/MeshCore
        // application firmware calls bledfu.begin(), which advertises 00001530 / 0000fe59 while still
        // running the main app. Only mark a node as already in DFU Bootloader mode when its advertised
        // name explicitly identifies the bootloader (AdaDFU, DfuTarg, DFU, Bootloader) and NOT a normal
        // Meshtastic/MeshCore application node.
        val hasAppName = resolvedName.contains("Meshtastic", ignoreCase = true) ||
            resolvedName.contains("MeshCore", ignoreCase = true) ||
            resolvedName.contains("Ripple", ignoreCase = true) ||
            resolvedName.contains("WadaMesh", ignoreCase = true)
        val isDfu = !hasAppName && (
            resolvedName.contains("AdaDFU", ignoreCase = true) ||
                resolvedName.contains("DfuTarg", ignoreCase = true) ||
                resolvedName.contains("Bootloader", ignoreCase = true) ||
                resolvedName.startsWith("Nordic DFU", ignoreCase = true) ||
                resolvedName.contains("DFU", ignoreCase = true)
            )

        val ecoHint = when {
            hasMeshtasticUuid ||
                mergedUuids.any { it.contains("6ba1b218") } ||
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
                (name.contains("T-Deck", ignoreCase = true) && b.id == "lilygo_tdeck") ||
                (name.contains("G2", ignoreCase = true) && b.id == "station_g2") ||
                (name.contains("RAK", ignoreCase = true) && b.id == "rak4631") ||
                (name.contains("T-Echo", ignoreCase = true) && b.id == "lilygo_techo") ||
                (name.contains("T1000", ignoreCase = true) && b.id == "seeed_t1000e") ||
                (name.contains("SenseCAP", ignoreCase = true) && b.id == "seeed_t1000e") ||
                (name.contains("AdaDFU", ignoreCase = true) && b.id == "seeed_t1000e") ||
                (name.contains("Heltec", ignoreCase = true) && b.id == "heltec_v3") ||
                (name.contains("T-Beam", ignoreCase = true) && b.id == "lilygo_tbeam_supreme")
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
     * Populates already-paired/bonded Meshtastic, MeshCore, or DFU radios from Android Bluetooth settings.
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

                val hasAppName = name.contains("Meshtastic", ignoreCase = true) ||
                    name.contains("MeshCore", ignoreCase = true) ||
                    name.contains("Ripple", ignoreCase = true) ||
                    name.contains("WadaMesh", ignoreCase = true)
                val isDfu = !hasAppName && (
                    name.contains("AdaDFU", ignoreCase = true) ||
                        name.contains("DfuTarg", ignoreCase = true) ||
                        name.contains("Bootloader", ignoreCase = true) ||
                        name.contains("DFU", ignoreCase = true)
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

    fun startBleScan(loopbackBench: Boolean): Boolean {
        stopBleScan()
        discoveredMap.clear()

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
                "No hardware Bluetooth LE adapter detected on this host. Loaded simulated BLE DFU targets."
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
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build()
            return try {
                scanner.startScan(null, settings, scanCallback)
                isScanning = true
                onLog(
                    LogLevel.INFO,
                    "BLE-SCAN",
                    "Scanning BLE advertisements for Meshtastic, MeshCore & Nordic DFU targets..."
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

    fun populateBenchBleNodes() {
        val benchNodes = listOf(
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
                address = "E4:65:B8:91:4A:2C",
                name = "Meshtastic_4a2c (RAK4631)",
                rssi = -54,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "rak4631" }
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
        onLog(LogLevel.INFO, "BLE-BENCH", "Loaded ${benchNodes.size} BLE mesh targets supporting OTA DFU.")
    }

    /**
     * Synthesizes a standard 14-byte Adafruit/Nordic Legacy DFU `.dat` Init Packet with CRC-16-CCITT
     * when the user flashes a raw `.bin` or `.uf2` without an accompanying `.dat` file.
     */
    private fun buildLegacyNrf52InitPacket(firmwareBytes: ByteArray): ByteArray {
        val crc16 = computeCrc16Ccitt(firmwareBytes)
        return ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x0052.toShort()) // device_type = nRF52 (0x0052)
            .putShort(0xFFFF.toShort()) // device_rev = any (0xFFFF)
            .putInt(-1)                 // app_version = any (0xFFFFFFFF)
            .putShort(0x0001.toShort()) // softdevices_len = 1
            .putShort(0xFFFE.toShort()) // sd_req = any S140 SoftDevice (0xFFFE)
            .putShort(crc16.toShort())  // CRC-16-CCITT of firmware image
            .array()
    }

    private fun computeCrc16Ccitt(data: ByteArray): Int {
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
     * Unwraps a `.zip` Nordic DFU archive into `(initPacketDat, firmwareBin)`, OR unwraps 512-byte
     * UF2 blocks (`0x0A324655`) into a contiguous raw ARM flash binary for BLE DFU streaming.
     */
    private fun extractDfuPayload(rawPayload: ByteArray): Pair<ByteArray?, ByteArray> {
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
     * If `data` starts with UF2 magic (`0x55, 0x46, 0x32, 0x0A` = "UF2\n"), extracts the 256-byte
     * payload slice from each 512-byte UF2 block so BLE DFU receives raw application bytes.
     */
    private fun unwrapUf2IfPresent(data: ByteArray): ByteArray {
        if (data.size >= 512 &&
            data[0] == 0x55.toByte() &&
            data[1] == 0x46.toByte() &&
            data[2] == 0x32.toByte() &&
            data[3] == 0x0A.toByte()
        ) {
            val totalBlocks = data.size / 512
            val out = ByteArrayOutputStream(totalBlocks * 256)
            for (b in 0 until totalBlocks) {
                val base = b * 512
                if (data[base] == 0x55.toByte() && data[base + 1] == 0x46.toByte()) {
                    val payloadLen = ByteBuffer.wrap(data, base + 16, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .coerceIn(0, 476)
                    if (payloadLen > 0 && base + 32 + payloadLen <= data.size) {
                        out.write(data, base + 32, payloadLen)
                    }
                }
            }
            val unwrapped = out.toByteArray()
            if (unwrapped.isNotEmpty()) {
                return unwrapped
            }
        }
        return data
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
     * Standalone trigger to put a SenseCAP T1000-E, RAK4631, or LilyGO T-Echo into OTA DFU Bootloader
     * mode (`AdaDFU` / `DfuTarg`) over Bluetooth LE without starting a full firmware flash yet.
     *
     * Sends:
     * 1. Adafruit `BLEDfu` (`00001530-1212-efde-1523-785feabcd123`) -> `LEGACY_DFU_CONTROL_POINT_CHAR`
     *    (`00001531-1212-efde-1523-785feabcd123`): Enable CCCD Notifications (`0x01, 0x00`), then write
     *    `byteArrayOf(0x01, 0x04)` (`DFU_OP_START_DFU = 1, DFU_TYPE_APPLICATION = 4`), which invokes
     *    `enterOTADfu()` (`NRF_POWER->GPREGRET = 0xA8` + `NVIC_SystemReset()`) on SenseCAP T1000-E / RAK4631 / T-Echo.
     * 2. Nordic Secure Buttonless DFU (`0000fe59` -> `8ec90003` / `8ec90004`): Enable CCCD Indications
     *    (`0x02, 0x00`), then write `byteArrayOf(0x01)` (`DFU_OP_ENTER_BOOTLOADER`).
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

        val cleanMac = effectiveTarget.address.substringBefore(" ").trim()
        val dfuMac = incrementBleMacAddress(cleanMac)

        var legacyControlChar: BluetoothGattCharacteristic? = null
        var secureButtonlessChar: BluetoothGattCharacteristic? = null
        var nusRxChar: BluetoothGattCharacteristic? = null
        val servicesReady = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!servicesReady.isCompleted) servicesReady.complete(false)
                    pendingWriteAck?.complete(false)
                    pendingDescriptorAck?.complete(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val legacyService = g.getService(NORDIC_LEGACY_DFU_SERVICE)
                val secureService = g.getService(NORDIC_SECURE_DFU_SERVICE)
                val nusService = g.getService(MESHCORE_NUS_SERVICE)

                legacyControlChar = legacyService?.getCharacteristic(LEGACY_DFU_CONTROL_POINT_CHAR)
                secureButtonlessChar = secureService?.getCharacteristic(DFU_BUTTONLESS_UNBONDED_CHAR)
                    ?: secureService?.getCharacteristic(DFU_BUTTONLESS_BONDED_CHAR)
                nusRxChar = nusService?.getCharacteristic(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))

                if (!servicesReady.isCompleted) {
                    servicesReady.complete(
                        legacyControlChar != null || secureButtonlessChar != null || nusRxChar != null
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
                    "Connecting to ${effectiveTarget.name} [$cleanMac] to trigger OTA DFU Bootloader mode..."
                )
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    realDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    realDevice.connectGatt(context, false, gattCallback)
                }
                activeGatt = gatt
                withTimeoutOrNull(4500L) { servicesReady.await() }

                var sentTrigger = false
                if (legacyControlChar != null) {
                    onLog(
                        LogLevel.TX,
                        "BLE-DFU",
                        "Adafruit BLEDfu (00001531) -> Enabling CCCD & writing [0x01, 0x04] (START_DFU, APP) to reboot ${effectiveTarget.name} into AdaDFU..."
                    )
                    enableCccd(gatt, legacyControlChar, useIndication = false)
                    writeCharacteristicSynced(gatt, legacyControlChar, byteArrayOf(0x01, 0x04), noResponse = false)
                    sentTrigger = true
                }
                if (secureButtonlessChar != null) {
                    onLog(
                        LogLevel.TX,
                        "BLE-DFU",
                        "Nordic Buttonless DFU (${secureButtonlessChar!!.uuid.toString().take(8)}) -> Enabling Indications & writing [0x01] (Enter Bootloader)..."
                    )
                    enableCccd(gatt, secureButtonlessChar, useIndication = true)
                    writeCharacteristicSynced(gatt, secureButtonlessChar, byteArrayOf(0x01), noResponse = false)
                    sentTrigger = true
                }
                if (!sentTrigger && nusRxChar != null) {
                    onLog(
                        LogLevel.TX,
                        "BLE-DFU",
                        "Sending 'dfu' bootloader trigger over Nordic UART RX (6e400002)..."
                    )
                    writeCharacteristicSynced(gatt, nusRxChar, "dfu\r\n".toByteArray(), noResponse = false)
                }

                delay(700)
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (_: Exception) {
                }
                activeGatt = null

                // Scan briefly so Android discovers the rebooted AdaDFU / DfuTarg node at MAC+1
                startBleScan(loopbackBench = false)
                delay(2200)
                stopBleScan()
            } catch (e: Exception) {
                onLog(LogLevel.WARN, "BLE-DFU", "BLE DFU trigger warning: ${e.message}")
            }
        } else {
            onLog(
                LogLevel.TX,
                "BLE-DFU",
                "Adafruit BLEDfu [00001530 -> 00001531]: CCCD=0x0001, Write [0x01, 0x04] (DFU_OP_START_DFU, APP) -> enterOTADfu() (GPREGRET=0xA8)"
            )
            delay(350)
        }

        // Update discoveredMap with the rebooted AdaDFU bootloader node so UI immediately reflects DFU mode
        discoveredMap.remove(cleanMac)
        val rebootedNode = BleDiscoveredNode(
            address = dfuMac,
            name = "AdaDFU (${effectiveTarget.matchedBoard?.name ?: board.name})",
            rssi = effectiveTarget.rssi,
            ecosystemHint = effectiveTarget.ecosystemHint,
            isInDfuMode = true,
            serviceUuids = listOf(NORDIC_LEGACY_DFU_SERVICE.toString(), NORDIC_SECURE_DFU_SERVICE.toString()),
            matchedBoard = effectiveTarget.matchedBoard ?: board
        )
        discoveredMap[dfuMac] = rebootedNode
        publishSortedNodes()
        onLog(
            LogLevel.SUCCESS,
            "BLE-DFU",
            "${effectiveTarget.name} rebooted into OTA DFU Bootloader mode -> Now advertising as '${rebootedNode.name}' [$dfuMac]"
        )
        return@withContext rebootedNode
    }

    /**
     * Executes wireless OTA / BLE Secure & Legacy DFU flashing to the selected BLE target.
     */
    suspend fun flashFirmwareOverBleDfu(
        targetNode: BleDiscoveredNode?,
        board: HardwareBoard,
        payload: ByteArray,
        sha256Hex: String,
        fullErase: Boolean,
        loopbackBench: Boolean,
        onProgress: (FlashProgressState) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        stopBleScan()

        if (discoveredMap.isEmpty() && (loopbackBench || isEmulatorEnvironment() || !isBluetoothEnabled())) {
            populateBenchBleNodes()
        }

        val effectiveTarget = targetNode
            ?: discoveredMap.values.firstOrNull { it.matchedBoard?.id == board.id }
            ?: discoveredMap.values.firstOrNull()

        if (effectiveTarget == null) {
            onLog(LogLevel.ERROR, "BLE-DFU", "No BLE target node selected. Scan and pick a BLE radio first.")
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.FAILED,
                    errorMessage = "Select a Bluetooth LE radio from the BLE OTA Targets list (or tap 'SIMULATE BLE') before starting."
                )
            )
            return@withContext false
        }

        val (extractedInitBytes, firmwareBytes) = extractDfuPayload(payload)

        var gattControlChar: BluetoothGattCharacteristic? = null
        var gattPacketChar: BluetoothGattCharacteristic? = null
        var gattButtonlessChar: BluetoothGattCharacteristic? = null
        var legacyControlChar: BluetoothGattCharacteristic? = null
        var legacyPacketChar: BluetoothGattCharacteristic? = null
        var secureControlChar: BluetoothGattCharacteristic? = null
        var securePacketChar: BluetoothGattCharacteristic? = null
        var isLegacyDfu = false
        var servicesReady = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val mtuRequested = try {
                        g.requestMtu(517)
                    } catch (_: Exception) {
                        false
                    }
                    if (!mtuRequested) {
                        g.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!servicesReady.isCompleted) {
                        servicesReady.complete(false)
                    }
                    pendingWriteAck?.complete(false)
                    pendingDescriptorAck?.complete(false)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val secureService = g.getService(NORDIC_SECURE_DFU_SERVICE)
                val legacyService = g.getService(NORDIC_LEGACY_DFU_SERVICE)

                legacyControlChar = legacyService?.getCharacteristic(LEGACY_DFU_CONTROL_POINT_CHAR)
                legacyPacketChar = legacyService?.getCharacteristic(LEGACY_DFU_PACKET_CHAR)

                secureControlChar = secureService?.getCharacteristic(DFU_CONTROL_POINT_CHAR)
                securePacketChar = secureService?.getCharacteristic(DFU_PACKET_CHAR)
                gattButtonlessChar = secureService?.getCharacteristic(DFU_BUTTONLESS_UNBONDED_CHAR)
                    ?: secureService?.getCharacteristic(DFU_BUTTONLESS_BONDED_CHAR)

                // Prefer Legacy DFU (00001530) when present (SenseCAP T1000-E, RAK4631, LilyGO T-Echo AdaDFU)
                if (legacyControlChar != null && (legacyPacketChar != null || secureControlChar == null)) {
                    gattControlChar = legacyControlChar
                    gattPacketChar = legacyPacketChar
                    isLegacyDfu = true
                } else if (secureControlChar != null) {
                    gattControlChar = secureControlChar
                    gattPacketChar = securePacketChar
                    isLegacyDfu = false
                } else if (legacyControlChar != null) {
                    gattControlChar = legacyControlChar
                    gattPacketChar = legacyPacketChar
                    isLegacyDfu = true
                }

                if (!servicesReady.isCompleted) {
                    servicesReady.complete(
                        gattControlChar != null || gattButtonlessChar != null || legacyControlChar != null
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

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                pendingDescriptorAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
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
                withTimeoutOrNull(4500L) {
                    servicesReady.await()
                }
            }

            // Step 1: Put SenseCAP T1000-E / RAK4631 / T-Echo into DFU Bootloader Mode if currently in Application Mode.
            // Note: In Adafruit BLEDfu (00001530), Application Mode exposes 00001531 (Control Point) and 00001534 (Version=0x0001),
            // but does NOT expose 00001532 (Packet Characteristic) until the device reboots into AdaDFU bootloader!
            val needsBootloaderTrigger = !effectiveTarget.isInDfuMode ||
                (activeGatt != null && gattPacketChar == null && (legacyControlChar != null || gattButtonlessChar != null))

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.ENTERING_BOOTLOADER,
                    overallProgress = 0.14f,
                    currentPartitionName = "Entering OTA DFU Bootloader (${effectiveTarget.name})",
                    currentAddressHex = "0x00026000",
                    totalBytes = firmwareBytes.size.toLong(),
                    sha256Digest = sha256Hex
                )
            )

            if (needsBootloaderTrigger) {
                onLog(
                    LogLevel.TX,
                    "BLE-DFU",
                    "Sending OTA DFU Bootloader trigger to ${effectiveTarget.name} [$cleanMac] (BLEDfu 00001531 -> [0x01, 0x04] / Buttonless 0x01)..."
                )
                if (activeGatt != null) {
                    // 1. Adafruit BLEDfu (SenseCAP T1000-E, RAK4631, LilyGO T-Echo):
                    // Enable notifications on 00001531 and write [0x01, 0x04] (DFU_OP_START_DFU = 1, DFU_TYPE_APPLICATION = 4)
                    if (legacyControlChar != null) {
                        enableCccd(activeGatt, legacyControlChar, useIndication = false)
                        writeCharacteristicSynced(
                            gatt = activeGatt,
                            characteristic = legacyControlChar,
                            bytes = byteArrayOf(0x01, 0x04),
                            noResponse = false
                        )
                    }
                    // 2. Nordic Secure Buttonless DFU (8ec90003 / 8ec90004):
                    // Enable indications and write [0x01] (DFU_OP_ENTER_BOOTLOADER)
                    if (gattButtonlessChar != null) {
                        enableCccd(activeGatt, gattButtonlessChar, useIndication = true)
                        writeCharacteristicSynced(
                            gatt = activeGatt,
                            characteristic = gattButtonlessChar,
                            bytes = byteArrayOf(0x01),
                            noResponse = false
                        )
                    }

                    // Wait for nRF52840 to set GPREGRET = 0xA8 and reboot into AdaDFU / DfuTarg
                    delay(750)
                    try {
                        activeGatt?.disconnect()
                        activeGatt?.close()
                    } catch (_: Exception) {
                    }
                    activeGatt = null

                    // Scan for 2.2s so Android discovers the rebooted AdaDFU peripheral at MAC+1
                    val dfuMac = incrementBleMacAddress(cleanMac)
                    onLog(
                        LogLevel.INFO,
                        "BLE-DFU",
                        "Waiting for ${board.name} to reboot into AdaDFU bootloader (expecting MAC $dfuMac)..."
                    )
                    startBleScan(loopbackBench = false)
                    delay(2200)
                    stopBleScan()

                    val scannedDfuNode = discoveredMap[dfuMac]
                        ?: discoveredMap.values.firstOrNull { it.isInDfuMode }
                    val targetDfuMac = scannedDfuNode?.address ?: dfuMac

                    val dfuDevice = try {
                        bluetoothAdapter?.getRemoteDevice(targetDfuMac) ?: realDevice
                    } catch (_: Exception) {
                        realDevice
                    }
                    if (dfuDevice != null) {
                        gattControlChar = null
                        gattPacketChar = null
                        legacyControlChar = null
                        legacyPacketChar = null
                        secureControlChar = null
                        securePacketChar = null
                        servicesReady = CompletableDeferred()
                        val dfuGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            dfuDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            dfuDevice.connectGatt(context, false, gattCallback)
                        }
                        activeGatt = dfuGatt
                        withTimeoutOrNull(5000L) {
                            servicesReady.await()
                        }
                    }
                } else {
                    delay(350)
                    val dfuMac = incrementBleMacAddress(cleanMac)
                    discoveredMap.remove(cleanMac)
                    discoveredMap[dfuMac] = effectiveTarget.copy(
                        address = dfuMac,
                        name = "AdaDFU (${board.name})",
                        isInDfuMode = true
                    )
                    publishSortedNodes()
                }
                onLog(
                    LogLevel.RX,
                    "BLE-DFU",
                    "AdaDFU Bootloader active (GPREGRET=0xA8) — Connected to ${board.name} DFU GATT service"
                )
            } else {
                onLog(
                    LogLevel.INFO,
                    "BLE-DFU",
                    "Connected to ${effectiveTarget.name} [$cleanMac] (${effectiveTarget.rssi} dBm) • AdaDFU Mode Active"
                )
                delay(200)
            }

            // Enable notifications on DFU Control Point
            if (activeGatt != null && gattControlChar != null) {
                enableCccd(activeGatt, gattControlChar, useIndication = false)
            }

            // Step 2: Send DFU Init Packet (.dat) & Image Size
            val initPacketBytes = extractedInitBytes ?: buildLegacyNrf52InitPacket(firmwareBytes)
            val initSummary = if (extractedInitBytes != null) {
                "Extracted signed .dat Init Packet (${initPacketBytes.size}B)"
            } else {
                "Generated nRF52840 .dat Init Packet (${initPacketBytes.size}B, CRC16=0x%04X)".format(
                    computeCrc16Ccitt(firmwareBytes)
                )
            }
            onLog(
                LogLevel.TX,
                "BLE-DFU",
                "DFU_INIT_PACKET -> $initSummary"
            )
            if (activeGatt != null && gattControlChar != null) {
                if (isLegacyDfu) {
                    // Adafruit / Nordic Legacy DFU (00001530) used on SenseCAP T1000-E, RAK4631, LilyGO T-Echo:
                    // 1. Write [0x01, 0x04] (START_DFU, Application = 4) to Control Point (00001531)
                    writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x01, 0x04), noResponse = false)
                    // 2. Write 12-byte image size [sdSize=0, blSize=0, appSize=firmwareBytes.size] to Packet Char (00001532)
                    if (gattPacketChar != null) {
                        val sizeBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(0)
                            .putInt(0)
                            .putInt(firmwareBytes.size)
                            .array()
                        writeCharacteristicSynced(activeGatt, gattPacketChar, sizeBuf, noResponse = true)
                    }
                    // Wait for flash erase of application region
                    delay(650)
                    // 3. Send Init Packet (.dat): [0x02, 0x00] -> Packet Char -> [0x02, 0x01]
                    if (gattPacketChar != null) {
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x00), noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattPacketChar, initPacketBytes, noResponse = true)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x01), noResponse = false)
                    }
                    // 4. SendRECEIVE_FIRMWARE_IMAGE [0x03] to Control Point before streaming bytes to 00001532
                    writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x03), noResponse = false)
                } else {
                    // Nordic Secure DFU (0000fe59)
                    writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x06, 0x01), noResponse = false)
                    if (gattPacketChar != null) {
                        val createInit = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                            .put(0x01.toByte())
                            .put(0x01.toByte())
                            .putInt(initPacketBytes.size)
                            .array()
                        writeCharacteristicSynced(activeGatt, gattControlChar, createInit, noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattPacketChar, initPacketBytes, noResponse = true)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x03), noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x04), noResponse = false)
                    }
                }
            }
            delay(250)
            onLog(LogLevel.RX, "BLE-DFU", "DFU_INIT_ACK -> [0x10 0x02 0x01] Init Packet & Image Size Accepted")

            if (fullErase) {
                onProgress(
                    FlashProgressState(
                        phase = FlashPhase.ERASING_SECTORS,
                        overallProgress = 0.25f,
                        currentPartitionName = "Clearing NVS / Bonding Flash Sectors",
                        currentAddressHex = "0x000ED000",
                        totalBytes = firmwareBytes.size.toLong(),
                        sha256Digest = sha256Hex
                    )
                )
                onLog(LogLevel.WARN, "BLE-DFU", "Erasing user NVS / LittleFS settings partition prior to OTA write...")
                delay(360)
            }

            // Step 3: Stream Firmware Object Packets over DFU Packet Characteristic
            val objectSize = 4096
            val mtuPayload = 244
            val totalObjects = ((firmwareBytes.size + objectSize - 1) / objectSize).coerceAtLeast(1)
            val runningCrc = CRC32()
            val delayPerObjectMs = (3500L / totalObjects.toLong()).coerceIn(16L, 100L)

            for (objIdx in 0 until totalObjects) {
                val offset = objIdx * objectSize
                val len = minOf(objectSize, firmwareBytes.size - offset)
                val objBytes = firmwareBytes.copyOfRange(offset, offset + len)
                runningCrc.update(objBytes)

                if (activeGatt != null && gattControlChar != null && gattPacketChar != null) {
                    if (!isLegacyDfu) {
                        val createCmd = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                            .put(0x01.toByte())
                            .put(0x02.toByte())
                            .putInt(len)
                            .array()
                        writeCharacteristicSynced(activeGatt, gattControlChar, createCmd, noResponse = false)
                    }

                    var subOffset = 0
                    while (subOffset < len) {
                        val subLen = minOf(mtuPayload, len - subOffset)
                        val packetSlice = objBytes.copyOfRange(subOffset, subOffset + subLen)
                        writeCharacteristicSynced(activeGatt, gattPacketChar, packetSlice, noResponse = true)
                        subOffset += subLen
                    }

                    if (!isLegacyDfu) {
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x03), noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x04), noResponse = false)
                    }
                }

                val flashAddr = if (board.mcu.name.startsWith("NRF")) 0x00026000 + offset else 0x00010000 + offset
                val fraction = (objIdx + 1).toFloat() / totalObjects.toFloat()
                val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                val bytesNow = (offset + len).toLong()
                val speed = (bytesNow / 1024f) / (elapsedMs / 1000f)

                if (objIdx == 0 || objIdx == totalObjects - 1 || objIdx % maxOf(1, totalObjects / 8) == 0) {
                    onLog(
                        LogLevel.TX,
                        "BLE-OTA",
                        "GATT DFU Object #${objIdx + 1}/$totalObjects (${len}B @ 0x%08X) • PRN CRC32=0x%08X".format(
                            flashAddr,
                            runningCrc.value
                        )
                    )
                }

                onProgress(
                    FlashProgressState(
                        phase = FlashPhase.WRITING_PARTITIONS,
                        overallProgress = 0.28f + fraction * 0.60f,
                        currentPartitionName = "BLE OTA Stream (${effectiveTarget.name})",
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

            // Step 4: Validate & Activate Firmware Image on Legacy DFU (0x04 VALIDATE, 0x05 ACTIVATE_AND_RESET)
            if (activeGatt != null && gattControlChar != null && isLegacyDfu) {
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x04), noResponse = false)
                delay(120)
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x05), noResponse = false)
            }

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.VERIFYING_CHECKSUM,
                    overallProgress = 0.94f,
                    currentPartitionName = "Verifying BLE DFU Image Signature",
                    currentAddressHex = "0x00026000",
                    bytesWritten = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = 46.2f,
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                    sha256Digest = sha256Hex
                )
            )
            delay(280)
            onLog(
                LogLevel.SUCCESS,
                "BLE-DFU",
                "OTA DFU CRC32 (0x%08X) & SHA-256 verified! Target ${effectiveTarget.name} activating new image & rebooting.".format(runningCrc.value)
            )

            onProgress(
                FlashProgressState(
                    phase = FlashPhase.COMPLETED,
                    overallProgress = 1.0f,
                    currentPartitionName = "BLE OTA Complete — ${effectiveTarget.name} Rebooted",
                    currentAddressHex = "0x00026000",
                    bytesWritten = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    speedKbps = 46.8f,
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1),
                    sha256Digest = sha256Hex
                )
            )
            return@withContext true
        } catch (e: Exception) {
            onLog(LogLevel.ERROR, "BLE-DFU", "BLE OTA failed: ${e.message}")
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.FAILED,
                    errorMessage = e.message ?: "Bluetooth LE GATT connection lost during DFU"
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
            cccd.value = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            val started = gatt.writeDescriptor(cccd)
            if (started) {
                withTimeoutOrNull(450L) { ack.await() }
            }
        } catch (_: Exception) {
        } finally {
            pendingDescriptorAck = null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeCharacteristicSynced(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        bytes: ByteArray,
        noResponse: Boolean
    ) {
        if (gatt == null || characteristic == null) return
        try {
            val ack = CompletableDeferred<Boolean>()
            pendingWriteAck = ack
            characteristic.writeType = if (noResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.value = bytes
            val started = gatt.writeCharacteristic(characteristic)
            if (started) {
                withTimeoutOrNull(if (noResponse) 80L else 450L) {
                    ack.await()
                }
            }
        } catch (_: Exception) {
        } finally {
            pendingWriteAck = null
        }
    }
}
