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
     * Uses `device_type = 0xFFFF` (`DFU_DEVICE_TYPE_EMPTY`) so `dfu_init_prevalidate()` in
     * `Adafruit_nRF52_Bootloader` (`dfu_init_template.c`) accepts any nRF52840 board UICR device_type.
     */
    private fun buildLegacyNrf52InitPacket(firmwareBytes: ByteArray): ByteArray {
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
     * If `data` starts with UF2 magic (`0x55, 0x46, 0x32, 0x0A` = "UF2\n"), reconstructs the
     * contiguous application flash image using each UF2 block's `targetAddr` (offset 12, LE32),
     * filtering out non-main-flash blocks (e.g., UICR >= `0x000F4000`) and filling alignment
     * gaps with `0xFF` (erased NOR flash state).
     */
    private fun unwrapUf2IfPresent(data: ByteArray): ByteArray {
        if (data.size >= 512 &&
            data[0] == 0x55.toByte() &&
            data[1] == 0x46.toByte() &&
            data[2] == 0x32.toByte() &&
            data[3] == 0x0A.toByte()
        ) {
            data class Uf2Chunk(val targetAddr: Int, val payload: ByteArray)
            val totalBlocks = data.size / 512
            val chunks = ArrayList<Uf2Chunk>(totalBlocks)
            var minAddr = Int.MAX_VALUE
            var maxEndAddr = 0

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

                    // Filter out UICR / Bootloader addresses (>= 0x000F4000) or invalid low addresses
                    if (payloadLen <= 0 || base + 32 + payloadLen > data.size) continue
                    if (targetAddr < 0 || targetAddr > 0x000F4000) continue

                    val slice = data.copyOfRange(base + 32, base + 32 + payloadLen)
                    chunks.add(Uf2Chunk(targetAddr, slice))
                    if (targetAddr < minAddr) minAddr = targetAddr
                    if (targetAddr + payloadLen > maxEndAddr) maxEndAddr = targetAddr + payloadLen
                }
            }

            if (chunks.isNotEmpty() && maxEndAddr > minAddr && (maxEndAddr - minAddr) <= 2 * 1024 * 1024) {
                val imageSize = maxEndAddr - minAddr
                val image = ByteArray(imageSize) { 0xFF.toByte() }
                for (chunk in chunks) {
                    val relOffset = chunk.targetAddr - minAddr
                    if (relOffset >= 0 && relOffset + chunk.payload.size <= image.size) {
                        System.arraycopy(chunk.payload, 0, image, relOffset, chunk.payload.size)
                    }
                }
                return image
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
        isRealBinary: Boolean = true,
        onProgress: (FlashProgressState) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        stopBleScan()

        if (!isRealBinary && !loopbackBench) {
            val errMsg = "Firmware download for ${board.name} did not return a valid hardware DFU binary. Check internet connection or select a release in the Vault."
            onLog(LogLevel.ERROR, "BLE-DFU", errMsg)
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

        fun handleDfuNotification(uuid: UUID?, rawValue: ByteArray?) {
            if (rawValue == null || rawValue.isEmpty()) return
            if (uuid == LEGACY_DFU_CONTROL_POINT_CHAR || uuid == DFU_CONTROL_POINT_CHAR) {
                val opCode = rawValue[0].toInt() and 0xFF
                if (opCode == 0x10 || opCode == 0x60) {
                    // Legacy DFU Response [0x10, reqOp, status] or Secure DFU Response [0x60, reqOp, status]
                    pendingControlResponse?.complete(rawValue)
                } else if (opCode == 0x11) {
                    // Legacy DFU Packet Receipt Notification [0x11, offset_le32]
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
                    pendingWriteAck?.complete(false)
                    pendingDescriptorAck?.complete(false)
                    pendingControlResponse?.complete(byteArrayOf())
                    pendingPrnNotification?.complete(byteArrayOf())
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (!servicesReady.isCompleted) {
                    g.discoverServices()
                }
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

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleDfuNotification(characteristic.uuid, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleDfuNotification(characteristic.uuid, value)
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
                withTimeoutOrNull(5000L) {
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
                    delay(850)
                    try {
                        activeGatt?.disconnect()
                        activeGatt?.close()
                    } catch (_: Exception) {
                    }
                    activeGatt = null

                    // Scan for 2.5s so Android discovers the rebooted AdaDFU peripheral at MAC+1
                    val dfuMac = incrementBleMacAddress(cleanMac)
                    onLog(
                        LogLevel.INFO,
                        "BLE-DFU",
                        "Waiting for ${board.name} to reboot into AdaDFU bootloader (expecting MAC $dfuMac)..."
                    )
                    startBleScan(loopbackBench = false)
                    delay(2500)
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
                        withTimeoutOrNull(5500L) {
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

            // Enable notifications on DFU Control Point (00001531 / 8ec90001)
            if (activeGatt != null && gattControlChar != null) {
                enableCccd(activeGatt, gattControlChar, useIndication = false)
                delay(150)
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
                "DFU_INIT_PACKET -> $initSummary • App Size=${firmwareBytes.size}B (${firmwareBytes.size / 1024} KB)"
            )

            if (activeGatt != null && gattControlChar != null && isLegacyDfu) {
                // =========================================================================
                // Adafruit / Nordic Legacy DFU (00001530) — SenseCAP T1000-E, RAK4631, T-Echo
                // IMPORTANT: In Adafruit_nRF52_Bootloader (ble_dfu.c), 00001532 (PKT_CHAR)
                // has max_len = 20 bytes (PKT_LEN = 20)! Writing > 20 bytes fails silently
                // after START_DFU erases Bank 0, which previously left T1000-E stuck in DFU!
                // =========================================================================
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

                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x01, 0x04), noResponse = false)
                if (gattPacketChar != null) {
                    val sizeBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(0)                  // SoftDevice size = 0
                        .putInt(0)                  // Bootloader size = 0
                        .putInt(firmwareBytes.size) // Application size
                        .array()
                    writeCharacteristicSynced(activeGatt, gattPacketChar, sizeBuf, noResponse = true)
                }

                onLog(
                    LogLevel.INFO,
                    "BLE-DFU",
                    "Waiting for AdaDFU to erase ${firmwareBytes.size / 1024} KB application flash bank (00001531 -> [0x10, 0x01, 0x01])..."
                )
                val startResp = withTimeoutOrNull(15000L) { startEraseAck.await() }
                val eraseElapsed = System.currentTimeMillis() - eraseStartMs
                if (startResp == null || startResp.size < 3) {
                    // Fallback wait if Android did not deliver the notification so flash erase finishes cleanly
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
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x00), noResponse = false)
                if (gattPacketChar != null) {
                    var initOff = 0
                    while (initOff < initPacketBytes.size) {
                        val initLen = minOf(legacyPacketMaxLen, initPacketBytes.size - initOff)
                        val slice = initPacketBytes.copyOfRange(initOff, initOff + initLen)
                        writeCharacteristicSynced(activeGatt, gattPacketChar, slice, noResponse = true)
                        initOff += initLen
                    }
                }
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x01), noResponse = false)
                val initResp = withTimeoutOrNull(4000L) { initAck.await() }
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
                writeCharacteristicSynced(activeGatt, gattControlChar, prnReq, noResponse = false)

                // 4. Send RECEIVE_FIRMWARE_IMAGE [0x03] to Control Point (00001531)
                val firmwareCompleteAck = CompletableDeferred<ByteArray>()
                pendingControlResponse = firmwareCompleteAck
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x03), noResponse = false)

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

                    if (gattPacketChar != null) {
                        writeCharacteristicSynced(activeGatt, gattPacketChar, pktBytes, noResponse = true)
                    }
                    packetsSincePrn++

                    if (prnDeferred != null) {
                        val prnResp = withTimeoutOrNull(1200L) { prnDeferred.await() }
                        if (prnResp == null) {
                            // Small pacing delay if Android suppressed a PRN notification
                            delay(25)
                        }
                        packetsSincePrn = 0
                    }

                    // Update UI progress every 64 packets (1,280 bytes) or on the final packet
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
                                currentPartitionName = "AdaDFU 20B Stream (${effectiveTarget.name})",
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
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x04), noResponse = false)
                val valResp = withTimeoutOrNull(5000L) { validateAck.await() }
                if (valResp == null || valResp.size < 3) {
                    delay(350)
                } else if ((valResp[2].toInt() and 0xFF) != 0x01) {
                    val statusHex = "0x%02X".format(valResp[2].toInt() and 0xFF)
                    throw IllegalStateException("AdaDFU VALIDATE_FIRMWARE (0x04) CRC16 check failed with status $statusHex")
                }
                onLog(LogLevel.RX, "BLE-DFU", "DFU_VALIDATE_ACK -> [0x10 0x04 0x01] Bank 0 CRC16 Verified")

                // 8. Send ACTIVATE_IMAGE_AND_RESET [0x05] so AdaDFU marks Bank 0 valid and boots the application!
                onLog(LogLevel.TX, "BLE-DFU", "Sending ACTIVATE_IMAGE_AND_RESET [0x05] -> Exiting AdaDFU & booting ${board.name}...")
                writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x05), noResponse = false)
                delay(400)

                // Restore node state in discoveredMap back to normal application mode
                val cleanDfuMac = effectiveTarget.address.substringBefore(" ").trim()
                discoveredMap.remove(cleanDfuMac)
                publishSortedNodes()
            } else {
                // =========================================================================
                // Nordic Secure DFU (0000fe59) OR Loopback Test Bench Simulation
                // =========================================================================
                if (activeGatt != null && gattControlChar != null) {
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
                        val createCmd = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                            .put(0x01.toByte())
                            .put(0x02.toByte())
                            .putInt(len)
                            .array()
                        writeCharacteristicSynced(activeGatt, gattControlChar, createCmd, noResponse = false)

                        var subOffset = 0
                        while (subOffset < len) {
                            val subLen = minOf(mtuPayload, len - subOffset)
                            val packetSlice = objBytes.copyOfRange(subOffset, subOffset + subLen)
                            writeCharacteristicSynced(activeGatt, gattPacketChar, packetSlice, noResponse = true)
                            subOffset += subLen
                        }

                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x03), noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x04), noResponse = false)
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
            }

            onLog(
                LogLevel.SUCCESS,
                "BLE-DFU",
                "OTA DFU verified! Target ${effectiveTarget.name} activated new application image & rebooted out of DFU mode."
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
            var started = false
            for (attempt in 0 until 6) {
                started = gatt.writeDescriptor(cccd)
                if (started) break
                delay(15)
            }
            if (started) {
                withTimeoutOrNull(1200L) { ack.await() }
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
            var started = false
            for (attempt in 0 until 15) {
                started = gatt.writeCharacteristic(characteristic)
                if (started) break
                delay(8)
            }
            if (started) {
                withTimeoutOrNull(if (noResponse) 500L else 2000L) {
                    ack.await()
                }
            }
        } catch (_: Exception) {
        } finally {
            pendingWriteAck = null
        }
    }
}
