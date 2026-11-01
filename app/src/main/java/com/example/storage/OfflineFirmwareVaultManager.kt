package com.example.storage

import android.content.Context
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.FirmwareEcosystem
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.MeshtasticTrack
import com.example.model.PreparedFirmwareImage
import com.example.model.TDeckMeshCoreFlavor
import com.example.model.VaultTargetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfflineFirmwareVaultManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val vaultRootDir = File(appContext.filesDir, "offline_vault").apply { mkdirs() }
    private val binariesDir = File(vaultRootDir, "binaries").apply { mkdirs() }
    private val indexFile = File(vaultRootDir, "vault_index.json")
    private val mutex = Mutex()

    companion object {
        private const val PREFS_NAME = "mesh_installer_offline_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_MY_DEVICES = "my_device_ids"
        private const val KEY_VAULT_SCHEMA_VERSION = "vault_schema_version"
        private const val CURRENT_VAULT_SCHEMA_VERSION = 3

        val DEFAULT_POPULAR_DEVICE_IDS = setOf(
            "heltec_v4",
            "heltec_v3",
            "lilygo_tdeck",
            "station_g2",
            "rak4631"
        )
    }

    init {
        migrateLegacySyntheticVaultIfNeeded()
    }

    private fun migrateLegacySyntheticVaultIfNeeded() {
        val savedSchema = prefs.getInt(KEY_VAULT_SCHEMA_VERSION, 1)
        if (savedSchema < CURRENT_VAULT_SCHEMA_VERSION) {
            try {
                // Purge any legacy 48KB (49152B) or 64KB (65536B) synthetic files from v1 vault
                val current = readEntriesFromDiskInternal()
                val valid = current.filter { entry ->
                    val len = resolveFile(entry.localFilePath).length()
                    val isLegacySynthetic = (len == 49152L || len == 65536L)
                    if (isLegacySynthetic) {
                        runCatching { resolveFile(entry.localFilePath).delete() }
                    }
                    !isLegacySynthetic
                }
                writeEntriesToDiskInternal(valid)
                // Ensure newly added Heltec V4 is included in default checked devices on upgrade
                val existingDevices = prefs.getStringSet(KEY_MY_DEVICES, null)
                if (existingDevices != null && "heltec_v3" in existingDevices && "heltec_v4" !in existingDevices) {
                    prefs.edit().putStringSet(KEY_MY_DEVICES, existingDevices + "heltec_v4").apply()
                }
            } catch (_: Exception) {
            }
            prefs.edit().putInt(KEY_VAULT_SCHEMA_VERSION, CURRENT_VAULT_SCHEMA_VERSION).apply()
        }
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getMyDeviceIds(): Set<String> {
        val stored = prefs.getStringSet(KEY_MY_DEVICES, null)
        return if (stored.isNullOrEmpty()) {
            DEFAULT_POPULAR_DEVICE_IDS
        } else {
            stored.toSet()
        }
    }

    fun saveMyDeviceIds(deviceIds: Set<String>) {
        prefs.edit().putStringSet(KEY_MY_DEVICES, deviceIds).apply()
    }

    /**
     * Computes a deterministic vault key for a specific board + ecosystem + role/UI configuration.
     */
    fun buildVaultKey(
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
        fullErase: Boolean = true,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE
    ): String {
        val variantKey = buildVariantKey(
            board = board,
            ecosystem = ecosystem,
            meshCoreRole = meshCoreRole,
            fullErase = fullErase,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
            meshtasticTrack = meshtasticTrack
        )
        return "${board.id}:${ecosystem.name}:$variantKey"
    }

    fun buildVariantKey(
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
        fullErase: Boolean = true,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE
    ): String {
        return when (ecosystem) {
            FirmwareEcosystem.MESHTASTIC -> {
                val trackPart = if (meshtasticTrack == MeshtasticTrack.ALPHA) "alpha" else "stable"
                if (board.id == "lilygo_tdeck") {
                    val uiPart = if (useMeshtasticMui) "mui" else "baseui"
                    val modePart = if (fullErase) "factory" else "update"
                    "mt_tdeck_${uiPart}_${trackPart}_$modePart"
                } else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) {
                    "mt_${trackPart}_uf2"
                } else {
                    if (fullErase) "mt_${trackPart}_factory" else "mt_${trackPart}_update"
                }
            }
            FirmwareEcosystem.MESHCORE -> {
                if (board.id == "lilygo_tdeck") {
                    when (tDeckMeshCoreFlavor) {
                        TDeckMeshCoreFlavor.RIPPLE -> "mc_tdeck_ripple"
                        TDeckMeshCoreFlavor.WADAMESH -> "mc_tdeck_wadamesh"
                    }
                } else {
                    when (meshCoreRole) {
                        MeshCoreRole.COMPANION_BLE -> "mc_companion_ble"
                        MeshCoreRole.COMPANION_USB -> "mc_companion_usb"
                        MeshCoreRole.REPEATER -> "mc_repeater"
                        MeshCoreRole.ROOM_SERVER -> "mc_room_server"
                        MeshCoreRole.CUSTOM -> "mc_companion_ble"
                    }
                }
            }
        }
    }

    fun buildVariantLabel(
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
        fullErase: Boolean = true,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE
    ): String {
        return when (ecosystem) {
            FirmwareEcosystem.MESHTASTIC -> {
                val trackName = if (meshtasticTrack == MeshtasticTrack.ALPHA) "Alpha Preview" else "Beta / Stable"
                if (board.id == "lilygo_tdeck") {
                    val uiName = if (useMeshtasticMui) "Meshtastic UI (MUI)" else "Meshtastic BaseUI"
                    if (fullErase) "$uiName • $trackName • Factory" else "$uiName • $trackName • Update"
                } else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) {
                    "Meshtastic $trackName (UF2)"
                } else {
                    if (fullErase) "Meshtastic $trackName (Full Factory)" else "Meshtastic $trackName (Update)"
                }
            }
            FirmwareEcosystem.MESHCORE -> {
                if (board.id == "lilygo_tdeck") {
                    "MeshCore • ${tDeckMeshCoreFlavor.label}"
                } else {
                    "MeshCore • ${meshCoreRole.label}"
                }
            }
        }
    }

    fun resolveFile(localPath: String): File {
        val direct = File(localPath)
        if (direct.exists() && direct.length() > 0L) return direct
        val fallback = File(binariesDir, direct.name)
        return if (fallback.exists() && fallback.length() > 0L) fallback else direct
    }

    /**
     * Generates all required Meshtastic + MeshCore download targets for the user's selected boards
     * so every role and UI option works 100% offline in the field.
     */
    fun buildDownloadSpecsForBoards(boardIds: Set<String>): List<VaultTargetSpec> {
        val boards = BoardCatalog.allBoards.filter { it.id in boardIds }
        val specs = mutableListOf<VaultTargetSpec>()
        for (board in boards) {
            if (board.id == "lilygo_tdeck") {
                // 1. T-Deck Meshtastic UI (MUI / t-deck-tft) • Full Factory
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHTASTIC,
                        useMeshtasticMui = true,
                        fullErase = true
                    )
                )
                // 2. T-Deck Meshtastic UI (MUI / t-deck-tft) • Update
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHTASTIC,
                        useMeshtasticMui = true,
                        fullErase = false
                    )
                )
                // 3. T-Deck Meshtastic BaseUI (t-deck) • Full Factory
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHTASTIC,
                        useMeshtasticMui = false,
                        fullErase = true
                    )
                )
                // 4. T-Deck MeshCore Ripple Ultra UI
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
                        fullErase = true
                    )
                )
                // 5. T-Deck MeshCore WadaMesh
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        tDeckMeshCoreFlavor = TDeckMeshCoreFlavor.WADAMESH,
                        fullErase = true
                    )
                )
            } else {
                // 1. Meshtastic Stable (Full Factory @ 0x0000 or UF2)
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHTASTIC,
                        fullErase = true
                    )
                )
                // For ESP32 architecture boards, also save an Update image @ 0x10000
                if (board.mcu != McuArchitecture.NRF52840 && board.mcu != McuArchitecture.RP2040) {
                    specs.add(
                        createSpec(
                            board = board,
                            ecosystem = FirmwareEcosystem.MESHTASTIC,
                            fullErase = false
                        )
                    )
                }
                // MeshCore Roles
                // Companion (BLE)
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        meshCoreRole = MeshCoreRole.COMPANION_BLE,
                        fullErase = true
                    )
                )
                // Companion (USB)
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        meshCoreRole = MeshCoreRole.COMPANION_USB,
                        fullErase = true
                    )
                )
                // Repeater
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        meshCoreRole = MeshCoreRole.REPEATER,
                        fullErase = true
                    )
                )
                // Room Server
                specs.add(
                    createSpec(
                        board = board,
                        ecosystem = FirmwareEcosystem.MESHCORE,
                        meshCoreRole = MeshCoreRole.ROOM_SERVER,
                        fullErase = true
                    )
                )
            }
        }
        return specs
    }

    private fun createSpec(
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
        meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        fullErase: Boolean = true
    ): VaultTargetSpec {
        val key = buildVaultKey(board, ecosystem, meshCoreRole, fullErase, useMeshtasticMui, tDeckMeshCoreFlavor, meshtasticTrack)
        val vKey = buildVariantKey(board, ecosystem, meshCoreRole, fullErase, useMeshtasticMui, tDeckMeshCoreFlavor, meshtasticTrack)
        val vLabel = buildVariantLabel(board, ecosystem, meshCoreRole, fullErase, useMeshtasticMui, tDeckMeshCoreFlavor, meshtasticTrack)
        return VaultTargetSpec(
            key = key,
            board = board,
            ecosystem = ecosystem,
            variantKey = vKey,
            variantLabel = vLabel,
            meshCoreRole = meshCoreRole,
            meshtasticTrack = meshtasticTrack,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
            fullErase = fullErase
        )
    }

    suspend fun loadAllEntries(): List<CachedFirmwareEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readEntriesFromDiskInternal()
        }
    }

    /**
     * Finds a matching cached entry for the current selection.
     * Guaranteed to match Meshtastic (Factory or Update, Alpha or Stable, MUI or BaseUI) and MeshCore offline.
     */
    fun findMatchingEntry(
        entries: List<CachedFirmwareEntry>,
        board: HardwareBoard,
        ecosystem: FirmwareEcosystem,
        meshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
        fullErase: Boolean = true,
        useMeshtasticMui: Boolean = true,
        tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
        meshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE,
        targetVersionTag: String? = null
    ): CachedFirmwareEntry? {
        val expectedVariantKey = buildVariantKey(
            board = board,
            ecosystem = ecosystem,
            meshCoreRole = meshCoreRole,
            fullErase = fullErase,
            useMeshtasticMui = useMeshtasticMui,
            tDeckMeshCoreFlavor = tDeckMeshCoreFlavor,
            meshtasticTrack = meshtasticTrack
        )
        val exactKey = "${board.id}:${ecosystem.name}:$expectedVariantKey"

        // 1. If targetVersionTag is specified, check for exact match with that versionTag AND variantKey
        if (!targetVersionTag.isNullOrBlank()) {
            entries.firstOrNull {
                it.key == exactKey &&
                    it.versionTag.equals(targetVersionTag, ignoreCase = true) &&
                    resolveFile(it.localFilePath).exists()
            }?.let { return it }

            entries.firstOrNull {
                it.boardId == board.id &&
                    it.ecosystem == ecosystem &&
                    it.variantKey == expectedVariantKey &&
                    it.versionTag.equals(targetVersionTag, ignoreCase = true) &&
                    resolveFile(it.localFilePath).exists()
            }?.let { return it }

            // For T-Deck on Meshtastic, ensure MUI vs BaseUI and track (Alpha vs Stable) are strictly partitioned
            if (board.id == "lilygo_tdeck" && ecosystem == FirmwareEcosystem.MESHTASTIC) {
                val trackPart = if (meshtasticTrack == MeshtasticTrack.ALPHA) "alpha" else "stable"
                val prefix = if (useMeshtasticMui) "mt_tdeck_mui_$trackPart" else "mt_tdeck_baseui_$trackPart"
                val legacyPrefix = if (useMeshtasticMui) "mt_tdeck_mui" else "mt_tdeck_baseui"
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == ecosystem &&
                        it.variantKey.startsWith(prefix) &&
                        it.versionTag.equals(targetVersionTag, ignoreCase = true) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == ecosystem &&
                        it.variantKey.startsWith(legacyPrefix) &&
                        it.versionTag.equals(targetVersionTag, ignoreCase = true) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
            }
        }

        // 2. Exact key match (matches board, ecosystem, role/mui, fullErase, track)
        entries.firstOrNull { it.key == exactKey && resolveFile(it.localFilePath).exists() }?.let { return it }

        // 3. Fallback matching respecting variant constraints
        if (ecosystem == FirmwareEcosystem.MESHTASTIC) {
            val isAlpha = meshtasticTrack == MeshtasticTrack.ALPHA
            fun isMatchingTrack(entry: CachedFirmwareEntry): Boolean {
                val hasAlpha = entry.variantKey.contains("alpha", ignoreCase = true) ||
                    entry.versionTag.contains("alpha", ignoreCase = true)
                return if (isAlpha) hasAlpha else !hasAlpha
            }

            if (board.id == "lilygo_tdeck") {
                val trackPart = if (meshtasticTrack == MeshtasticTrack.ALPHA) "alpha" else "stable"
                val trackPrefix = if (useMeshtasticMui) "mt_tdeck_mui_$trackPart" else "mt_tdeck_baseui_$trackPart"
                val basePrefix = if (useMeshtasticMui) "mt_tdeck_mui" else "mt_tdeck_baseui"
                val modeSuffix = if (fullErase) "factory" else "update"
                // Try matching exact mode (factory vs update) first with track prefix
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        it.variantKey.startsWith(trackPrefix) &&
                        it.variantKey.endsWith(modeSuffix) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
                // Otherwise match UI variant with track prefix
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        it.variantKey.startsWith(trackPrefix) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
                // Try matching exact mode with basePrefix + isMatchingTrack
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        it.variantKey.startsWith(basePrefix) &&
                        it.variantKey.endsWith(modeSuffix) &&
                        isMatchingTrack(it) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
                // Otherwise match UI variant and track
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        it.variantKey.startsWith(basePrefix) &&
                        isMatchingTrack(it) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
            } else {
                // For non-T-Deck Meshtastic boards, match saved Meshtastic image respecting track and mode
                val modeSuffix = if (fullErase) "factory" else "update"
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        it.variantKey.endsWith(modeSuffix) &&
                        isMatchingTrack(it) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHTASTIC &&
                        isMatchingTrack(it) &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
            }
        } else if (ecosystem == FirmwareEcosystem.MESHCORE) {
            if (board.id == "lilygo_tdeck") {
                val flavorKey = when (tDeckMeshCoreFlavor) {
                    TDeckMeshCoreFlavor.RIPPLE -> "mc_tdeck_ripple"
                    TDeckMeshCoreFlavor.WADAMESH -> "mc_tdeck_wadamesh"
                }
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHCORE &&
                        it.variantKey == flavorKey &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
            } else {
                val roleKey = when (meshCoreRole) {
                    MeshCoreRole.COMPANION_BLE -> "mc_companion_ble"
                    MeshCoreRole.COMPANION_USB -> "mc_companion_usb"
                    MeshCoreRole.REPEATER -> "mc_repeater"
                    MeshCoreRole.ROOM_SERVER -> "mc_room_server"
                    MeshCoreRole.CUSTOM -> "mc_companion_ble"
                }
                entries.firstOrNull {
                    it.boardId == board.id &&
                        it.ecosystem == FirmwareEcosystem.MESHCORE &&
                        it.variantKey == roleKey &&
                        resolveFile(it.localFilePath).exists()
                }?.let { return it }
            }
        }
        return null
    }

    suspend fun loadPreparedImageFromEntry(entry: CachedFirmwareEntry): PreparedFirmwareImage? =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(entry.localFilePath)
                if (!file.exists() || file.length() <= 0L) return@withContext null
                val bytes = file.readBytes()
                PreparedFirmwareImage(
                    bytes = bytes,
                    sha256Hex = entry.sha256Hex,
                    flashOffset = entry.flashOffset,
                    fileName = entry.fileName,
                    isFullFactoryImage = entry.isFullFactoryImage,
                    isRealBinary = entry.isRealBinary
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun savePreparedImageToVault(
        spec: VaultTargetSpec,
        versionTag: String,
        prepared: PreparedFirmwareImage
    ): CachedFirmwareEntry = withContext(Dispatchers.IO) {
        mutex.withLock {
            val safeFileName = "${spec.board.id}_${spec.variantKey}_${prepared.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val targetFile = File(binariesDir, safeFileName)
            targetFile.writeBytes(prepared.bytes)

            val newEntry = CachedFirmwareEntry(
                key = spec.key,
                boardId = spec.board.id,
                boardName = spec.board.name,
                ecosystem = spec.ecosystem,
                variantKey = spec.variantKey,
                variantLabel = spec.variantLabel,
                versionTag = versionTag,
                fileName = prepared.fileName,
                localFilePath = targetFile.absolutePath,
                sizeBytes = prepared.bytes.size.toLong(),
                sha256Hex = prepared.sha256Hex,
                flashOffset = prepared.flashOffset,
                isFullFactoryImage = prepared.isFullFactoryImage,
                isRealBinary = prepared.isRealBinary,
                downloadedAtEpochMs = System.currentTimeMillis()
            )

            val existing = readEntriesFromDiskInternal().filterNot { it.key == spec.key }.toMutableList()
            existing.add(0, newEntry)
            writeEntriesToDiskInternal(existing)
            newEntry
        }
    }

    suspend fun deleteEntry(key: String): List<CachedFirmwareEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readEntriesFromDiskInternal()
            val toDelete = current.filter { it.key == key }
            toDelete.forEach { entry ->
                runCatching { resolveFile(entry.localFilePath).delete() }
            }
            val remaining = current.filterNot { it.key == key }
            writeEntriesToDiskInternal(remaining)
            remaining
        }
    }

    suspend fun clearAllVaultEntries(): List<CachedFirmwareEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            binariesDir.listFiles()?.forEach { runCatching { it.delete() } }
            writeEntriesToDiskInternal(emptyList())
            emptyList()
        }
    }

    private fun readEntriesFromDiskInternal(): List<CachedFirmwareEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            val result = mutableListOf<CachedFirmwareEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val localPath = obj.optString("localFilePath", "")
                val file = resolveFile(localPath)
                if (!file.exists() || file.length() <= 0L) continue
                val ecoName = obj.optString("ecosystem", FirmwareEcosystem.MESHTASTIC.name)
                val eco = runCatching { FirmwareEcosystem.valueOf(ecoName) }.getOrDefault(FirmwareEcosystem.MESHTASTIC)
                val rawBoardId = obj.optString("boardId", "")
                val normalizedBoardName = if (rawBoardId == "station_g2") {
                    "Station G2 High-Power (3.5W)"
                } else {
                    obj.optString("boardName", "").replace("B&Q ", "")
                }
                result.add(
                    CachedFirmwareEntry(
                        key = obj.optString("key", ""),
                        boardId = rawBoardId,
                        boardName = normalizedBoardName,
                        ecosystem = eco,
                        variantKey = obj.optString("variantKey", ""),
                        variantLabel = obj.optString("variantLabel", ""),
                        versionTag = obj.optString("versionTag", ""),
                        fileName = obj.optString("fileName", ""),
                        localFilePath = file.absolutePath,
                        sizeBytes = obj.optLong("sizeBytes", file.length()),
                        sha256Hex = obj.optString("sha256Hex", ""),
                        flashOffset = obj.optInt("flashOffset", 0x10000),
                        isFullFactoryImage = obj.optBoolean("isFullFactoryImage", false),
                        isRealBinary = obj.optBoolean("isRealBinary", true),
                        downloadedAtEpochMs = obj.optLong("downloadedAtEpochMs", 0L)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeEntriesToDiskInternal(entries: List<CachedFirmwareEntry>) {
        val arr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("key", entry.key)
                put("boardId", entry.boardId)
                put("boardName", entry.boardName)
                put("ecosystem", entry.ecosystem.name)
                put("variantKey", entry.variantKey)
                put("variantLabel", entry.variantLabel)
                put("versionTag", entry.versionTag)
                put("fileName", entry.fileName)
                put("localFilePath", entry.localFilePath)
                put("sizeBytes", entry.sizeBytes)
                put("sha256Hex", entry.sha256Hex)
                put("flashOffset", entry.flashOffset)
                put("isFullFactoryImage", entry.isFullFactoryImage)
                put("isRealBinary", entry.isRealBinary)
                put("downloadedAtEpochMs", entry.downloadedAtEpochMs)
            }
            arr.put(obj)
        }
        indexFile.writeText(arr.toString(2))
    }
}
