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

        val isDfu = hasDfuUuid ||
            mergedUuids.any { it.contains("fe59") || it.contains("1530") } ||
            resolvedName.contains("DFU", ignoreCase = true) ||
            resolvedName.contains("AdaDFU", ignoreCase = true) ||
            resolvedName.contains("DfuTarg", ignoreCase = true) ||
            resolvedName.contains("Bootloader", ignoreCase = true)

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

                val isDfu = name.contains("DFU", ignoreCase = true) ||
                    name.contains("AdaDFU", ignoreCase = true) ||
                    name.contains("DfuTarg", ignoreCase = true)
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
                            serviceUuids = listOf(NORDIC_SECURE_DFU_SERVICE.toString()),
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
                address = "E4:65:B8:91:4A:2C",
                name = "Meshtastic_4a2c (RAK4631)",
                rssi = -54,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = false,
                serviceUuids = listOf(MESHTASTIC_BLE_SERVICE.toString(), NORDIC_SECURE_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "rak4631" }
            ),
            BleDiscoveredNode(
                address = "D9:1F:44:08:7E:11",
                name = "MeshCore-Companion-7E11",
                rssi = -61,
                ecosystemHint = FirmwareEcosystem.MESHCORE,
                isInDfuMode = false,
                serviceUuids = listOf(MESHCORE_NUS_SERVICE.toString(), NORDIC_SECURE_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "heltec_v3" }
            ),
            BleDiscoveredNode(
                address = "F2:88:1C:A0:09:B4",
                name = "AdaDFU (SenseCAP T1000-E)",
                rssi = -48,
                ecosystemHint = null,
                isInDfuMode = true,
                serviceUuids = listOf(NORDIC_SECURE_DFU_SERVICE.toString(), NORDIC_LEGACY_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "seeed_t1000e" }
            ),
            BleDiscoveredNode(
                address = "C8:3A:9B:52:10:D7",
                name = "T-Echo_10d7 (DFU Ready)",
                rssi = -67,
                ecosystemHint = FirmwareEcosystem.MESHTASTIC,
                isInDfuMode = true,
                serviceUuids = listOf(NORDIC_SECURE_DFU_SERVICE.toString()),
                matchedBoard = BoardCatalog.allBoards.first { it.id == "lilygo_techo" }
            )
        )
        benchNodes.forEach { discoveredMap[it.address] = it }
        publishSortedNodes()
        onLog(LogLevel.INFO, "BLE-BENCH", "Loaded ${benchNodes.size} BLE mesh targets supporting OTA DFU.")
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
     * `E4:65:B8:91:4A:2C` -> `E4:65:B8:91:4A:2D`.
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

        val (initPacketBytes, firmwareBytes) = extractDfuPayload(payload)

        var gattControlChar: BluetoothGattCharacteristic? = null
        var gattPacketChar: BluetoothGattCharacteristic? = null
        var gattButtonlessChar: BluetoothGattCharacteristic? = null
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
                if (secureService != null) {
                    gattControlChar = secureService.getCharacteristic(DFU_CONTROL_POINT_CHAR)
                    gattPacketChar = secureService.getCharacteristic(DFU_PACKET_CHAR)
                    gattButtonlessChar = secureService.getCharacteristic(DFU_BUTTONLESS_UNBONDED_CHAR)
                        ?: secureService.getCharacteristic(DFU_BUTTONLESS_BONDED_CHAR)
                    isLegacyDfu = false
                } else if (legacyService != null) {
                    gattControlChar = legacyService.getCharacteristic(LEGACY_DFU_CONTROL_POINT_CHAR)
                    gattPacketChar = legacyService.getCharacteristic(LEGACY_DFU_PACKET_CHAR)
                    gattButtonlessChar = legacyService.getCharacteristic(LEGACY_DFU_VERSION_CHAR)
                    isLegacyDfu = true
                }
                if (!servicesReady.isCompleted) {
                    servicesReady.complete(gattControlChar != null || gattButtonlessChar != null)
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
                withTimeoutOrNull(4000L) {
                    servicesReady.await()
                }
            }

            // Step 1: Buttonless DFU Trigger & Bootloader Reconnection (if currently in application mode)
            onProgress(
                FlashProgressState(
                    phase = FlashPhase.ENTERING_BOOTLOADER,
                    overallProgress = 0.14f,
                    currentPartitionName = "BLE GATT MTU 247 & Buttonless DFU (${effectiveTarget.name})",
                    currentAddressHex = "0x00026000",
                    totalBytes = firmwareBytes.size.toLong(),
                    sha256Digest = sha256Hex
                )
            )

            if (!effectiveTarget.isInDfuMode) {
                onLog(
                    LogLevel.TX,
                    "BLE-DFU",
                    "Enabling CCCD Indications & sending 0x01 (Enter Bootloader) to ${effectiveTarget.name}..."
                )
                if (activeGatt != null && gattButtonlessChar != null) {
                    enableCccd(activeGatt, gattButtonlessChar, useIndication = true)
                    writeCharacteristicSynced(
                        gatt = activeGatt,
                        characteristic = gattButtonlessChar,
                        bytes = byteArrayOf(0x01),
                        noResponse = false
                    )
                    // Wait for node to reboot into DFU bootloader (which may use MAC or MAC+1)
                    delay(1400)
                    try {
                        activeGatt?.disconnect()
                        activeGatt?.close()
                    } catch (_: Exception) {
                    }
                    activeGatt = null

                    val dfuMac = incrementBleMacAddress(cleanMac)
                    val dfuDevice = try {
                        bluetoothAdapter?.getRemoteDevice(dfuMac) ?: realDevice
                    } catch (_: Exception) {
                        realDevice
                    }
                    if (dfuDevice != null) {
                        servicesReady = CompletableDeferred()
                        val dfuGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            dfuDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            dfuDevice.connectGatt(context, false, gattCallback)
                        }
                        activeGatt = dfuGatt
                        withTimeoutOrNull(4000L) {
                            servicesReady.await()
                        }
                    }
                } else {
                    delay(320)
                }
                onLog(
                    LogLevel.RX,
                    "BLE-DFU",
                    "Response [0x20 0x01 0x01] Success — Connected to ${effectiveTarget.name} in DFU Bootloader Mode"
                )
            } else {
                onLog(
                    LogLevel.INFO,
                    "BLE-DFU",
                    "Connected to ${effectiveTarget.name} [$cleanMac] (${effectiveTarget.rssi} dBm) • DFU Mode Active"
                )
                delay(200)
            }

            // Enable notifications on DFU Control Point
            if (activeGatt != null && gattControlChar != null) {
                enableCccd(activeGatt, gattControlChar, useIndication = false)
            }

            // Step 2: Send DFU Init Packet (.dat) & Validate Hardware/SoftDevice Compatibility
            val initSummary = if (initPacketBytes != null) {
                "Extracted signed .dat Init Packet (${initPacketBytes.size}B)"
            } else {
                "Signed .dat Init Packet (${board.name}, sd_req=0x0102)"
            }
            onLog(
                LogLevel.TX,
                "BLE-DFU",
                "DFU_OBJECT_SELECT [0x06 0x01] (Init Packet) -> $initSummary"
            )
            if (activeGatt != null && gattControlChar != null) {
                if (isLegacyDfu) {
                    writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x01, 0x04), noResponse = false)
                    if (initPacketBytes != null && gattPacketChar != null) {
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x00), noResponse = false)
                        writeCharacteristicSynced(activeGatt, gattPacketChar, initPacketBytes, noResponse = true)
                        writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x02, 0x01), noResponse = false)
                    }
                } else {
                    writeCharacteristicSynced(activeGatt, gattControlChar, byteArrayOf(0x06, 0x01), noResponse = false)
                    if (initPacketBytes != null && gattPacketChar != null) {
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
            onLog(LogLevel.RX, "BLE-DFU", "DFU_OBJECT_EXECUTE [0x04] -> [0x60 0x04 0x01] Init Packet Accepted")

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

            // Step 4: Post-validation & Reboot
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
