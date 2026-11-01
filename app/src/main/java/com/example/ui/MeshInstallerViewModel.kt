package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hardware.BleTransportManager
import com.example.hardware.UsbTransportManager
import com.example.model.BleDiscoveredNode
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.ConnectionTransport
import com.example.model.ConsoleLogEntry
import com.example.model.CustomFirmwareFile
import com.example.model.FirmwareEcosystem
import com.example.model.FirmwareRelease
import com.example.model.FlashPhase
import com.example.model.FlashProgressState
import com.example.model.HardwareBoard
import com.example.model.LogLevel
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.MeshtasticTrack
import com.example.model.TDeckMeshCoreFlavor
import com.example.model.UsbAttachedBoard
import com.example.model.VaultBatchProgress
import com.example.model.VaultTargetSpec
import com.example.network.GitHubFirmwareRepository
import com.example.storage.OfflineFirmwareVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class MeshInstallerUiState(
    val selectedEcosystem: FirmwareEcosystem = FirmwareEcosystem.MESHTASTIC,
    val selectedMeshtasticTrack: MeshtasticTrack = MeshtasticTrack.STABLE,
    val selectedMeshCoreRole: MeshCoreRole = MeshCoreRole.COMPANION_BLE,
    val tDeckMeshtasticMui: Boolean = true,
    val tDeckMeshCoreFlavor: TDeckMeshCoreFlavor = TDeckMeshCoreFlavor.RIPPLE,
    val selectedBoard: HardwareBoard = BoardCatalog.allBoards.first(),
    val selectedTransport: ConnectionTransport = ConnectionTransport.USB_OTG,
    val mcuFilter: McuArchitecture? = null,
    val boardSearchQuery: String = "",
    val meshtasticReleases: List<FirmwareRelease> = emptyList(),
    val meshCoreReleases: List<FirmwareRelease> = emptyList(),
    val selectedRelease: FirmwareRelease? = null,
    val isFetchingReleases: Boolean = false,
    val customFirmwareFile: CustomFirmwareFile? = null,
    val customFirmwareBytes: ByteArray? = null,
    val fullFlashErase: Boolean = true,
    val bundleWebUiPartition: Boolean = true,
    val verifySha256AfterFlash: Boolean = true,
    val selectedBaudRate: Int = 921600,
    val loopbackTestBench: Boolean = false,
    val usbAttachedBoards: List<UsbAttachedBoard> = emptyList(),
    val selectedUsbDevice: UsbAttachedBoard? = null,
    val bleDiscoveredNodes: List<BleDiscoveredNode> = emptyList(),
    val selectedBleNode: BleDiscoveredNode? = null,
    val isBleScanning: Boolean = false,
    val flashProgress: FlashProgressState = FlashProgressState(),
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    // Off-Grid Firmware Vault & My Devices state
    val showOffGridSetupDialog: Boolean = false,
    val myDeviceIds: Set<String> = OfflineFirmwareVaultManager.DEFAULT_POPULAR_DEVICE_IDS,
    val offlineVaultEntries: List<CachedFirmwareEntry> = emptyList(),
    val vaultBatchProgress: VaultBatchProgress = VaultBatchProgress()
)

class MeshInstallerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GitHubFirmwareRepository()
    private val vaultManager = OfflineFirmwareVaultManager(application.applicationContext)
    private val logCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var batchDownloadJob: Job? = null
    private var bleScanJob: Job? = null

    private val initialMyDevices = vaultManager.getMyDeviceIds()
    private val initialBoard = BoardCatalog.allBoards.firstOrNull { it.id in initialMyDevices }
        ?: BoardCatalog.allBoards.first()

    private val _uiState = MutableStateFlow(
        MeshInstallerUiState(
            selectedBoard = initialBoard,
            selectedBaudRate = initialBoard.mcu.defaultBaudRate,
            meshtasticReleases = repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC),
            meshCoreReleases = repository.curatedFallbackReleases(FirmwareEcosystem.MESHCORE),
            selectedRelease = repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first(),
            showOffGridSetupDialog = !vaultManager.hasCompletedOnboarding(),
            myDeviceIds = initialMyDevices
        )
    )
    val uiState: StateFlow<MeshInstallerUiState> = _uiState.asStateFlow()

    val usbManager = UsbTransportManager(
        context = application.applicationContext,
        onLog = { level, tag, msg -> appendLog(level, tag, msg) },
        onDevicesChanged = { attached ->
            _uiState.update { current ->
                val chosenUsb = attached.firstOrNull()
                // Preserve the user's selected board (e.g. Station G2) if the attached USB device shares its MCU/VID
                val autoBoard = chosenUsb?.matchedBoard ?: current.selectedBoard
                current.copy(
                    usbAttachedBoards = attached,
                    selectedUsbDevice = chosenUsb,
                    selectedBoard = autoBoard,
                    selectedBaudRate = autoBoard.mcu.defaultBaudRate
                )
            }
        }
    )

    val bleManager = BleTransportManager(
        context = application.applicationContext,
        onLog = { level, tag, msg -> appendLog(level, tag, msg) },
        onNodesUpdated = { nodes ->
            _uiState.update { current ->
                current.copy(
                    bleDiscoveredNodes = nodes,
                    selectedBleNode = current.selectedBleNode
                        ?: nodes.firstOrNull { it.isInDfuMode }
                        ?: nodes.firstOrNull()
                )
            }
        }
    )

    init {
        appendLog(
            LogLevel.INFO,
            "BOOT",
            "Mesh Installer initialized • Supports esptool SLIP (ESP32/S3/C3 incl. Station G2), Adafruit UF2 & Nordic Secure DFU (nRF52840)."
        )
        try {
            usbManager.updateSelectedBoardHint(_uiState.value.selectedBoard)
            usbManager.registerReceiver()
        } catch (e: Exception) {
            appendLog(LogLevel.WARN, "USB-BOOT", "USB service initialization deferred: ${e.message}")
        }
        try {
            loadVaultEntriesFromDisk()
        } catch (e: Exception) {
            appendLog(LogLevel.WARN, "VAULT-BOOT", "Could not load cached vault: ${e.message}")
        }
        try {
            refreshFirmwareCatalog()
        } catch (e: Exception) {
            appendLog(LogLevel.WARN, "NET-BOOT", "Firmware catalog refresh deferred: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        usbManager.unregisterReceiver()
        bleManager.stopBleScan()
    }

    private fun loadVaultEntriesFromDisk() {
        viewModelScope.launch {
            val entries = vaultManager.loadAllEntries()
            _uiState.update { it.copy(offlineVaultEntries = entries) }
            if (entries.isNotEmpty()) {
                val totalMb = entries.sumOf { it.sizeBytes } / (1024f * 1024f)
                appendLog(
                    LogLevel.SUCCESS,
                    "OFFGRID-VAULT",
                    "Loaded ${entries.size} offline firmware image(s) from device storage (%.1f MB ready for off-grid flashing).".format(Locale.US, totalMb)
                )
            }
        }
    }

    fun openOffGridSetupDialog() {
        _uiState.update { it.copy(showOffGridSetupDialog = true) }
    }

    fun closeOffGridSetupDialog() {
        vaultManager.setOnboardingCompleted(true)
        _uiState.update { it.copy(showOffGridSetupDialog = false) }
    }

    fun toggleMyDevice(boardId: String) {
        _uiState.update { state ->
            val next = if (boardId in state.myDeviceIds) {
                state.myDeviceIds - boardId
            } else {
                state.myDeviceIds + boardId
            }
            vaultManager.saveMyDeviceIds(next)
            val updatedSelectedBoard = if (state.selectedBoard.id !in next && next.isNotEmpty()) {
                BoardCatalog.allBoards.firstOrNull { it.id in next } ?: state.selectedBoard
            } else {
                state.selectedBoard
            }
            state.copy(
                myDeviceIds = next,
                selectedBoard = updatedSelectedBoard
            )
        }
    }

    fun selectPopularMyDevices() {
        val popular = OfflineFirmwareVaultManager.DEFAULT_POPULAR_DEVICE_IDS
        vaultManager.saveMyDeviceIds(popular)
        _uiState.update { it.copy(myDeviceIds = popular) }
    }

    fun selectAllMyDevices() {
        val allIds = BoardCatalog.allBoards.map { it.id }.toSet()
        vaultManager.saveMyDeviceIds(allIds)
        _uiState.update { it.copy(myDeviceIds = allIds) }
    }

    fun clearMyDevices() {
        vaultManager.saveMyDeviceIds(emptySet())
        _uiState.update { it.copy(myDeviceIds = emptySet()) }
    }

    fun cancelBatchDownload() {
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        _uiState.update {
            it.copy(
                vaultBatchProgress = it.vaultBatchProgress.copy(
                    isDownloading = false,
                    statusDetail = "Batch download paused — saved binaries remain ready offline."
                )
            )
        }
        appendLog(LogLevel.WARN, "OFFGRID-VAULT", "Cancelled batch firmware download.")
    }

    /**
     * Downloads ALL Meshtastic + MeshCore firmware variants for every board in `myDeviceIds`
     * directly to persistent on-device storage (`offline_vault/binaries`) for 100% off-grid operation.
     */
    fun downloadAllFirmwareForMyDevices() {
        val selectedIds = _uiState.value.myDeviceIds
        if (selectedIds.isEmpty()) return
        downloadFirmwareForBoardSet(selectedIds)
    }

    /**
     * Downloads and saves all Meshtastic + MeshCore firmware variants for a single radio board.
     */
    fun downloadSingleBoardFirmware(boardId: String) {
        val updatedIds = _uiState.value.myDeviceIds + boardId
        vaultManager.saveMyDeviceIds(updatedIds)
        _uiState.update { it.copy(myDeviceIds = updatedIds) }
        downloadFirmwareForBoardSet(setOf(boardId))
    }

    private fun downloadFirmwareForBoardSet(targetBoardIds: Set<String>) {
        if (targetBoardIds.isEmpty()) return
        if (_uiState.value.vaultBatchProgress.isDownloading) return

        vaultManager.saveMyDeviceIds(_uiState.value.myDeviceIds + targetBoardIds)
        vaultManager.setOnboardingCompleted(true)

        val specs = vaultManager.buildDownloadSpecsForBoards(targetBoardIds)
        if (specs.isEmpty()) return

        batchDownloadJob = viewModelScope.launch {
            appendLog(
                LogLevel.INFO,
                "OFFGRID-VAULT",
                "Starting off-grid firmware download & local save: ${specs.size} firmware files across ${targetBoardIds.size} radio(s)."
            )

            val snapshot = _uiState.value
            val mtReleases = snapshot.meshtasticReleases.ifEmpty {
                repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC)
            }
            val mcReleases = snapshot.meshCoreReleases.ifEmpty {
                repository.curatedFallbackReleases(FirmwareEcosystem.MESHCORE)
            }
            val mtStable = mtReleases.firstOrNull { !it.isPrerelease } ?: mtReleases.first()
            val mcLatest = mcReleases.first()
            val rippleDefault = repository.curatedTDeckFlavorFallback(TDeckMeshCoreFlavor.RIPPLE).first()
            val wadaDefault = repository.curatedTDeckFlavorFallback(TDeckMeshCoreFlavor.WADAMESH).first()

            var completed = 0
            var failed = 0

            for ((idx, spec) in specs.withIndex()) {
                val stepNum = idx + 1
                val baseProgress = idx.toFloat() / specs.size.toFloat()
                val stepSpan = 1f / specs.size.toFloat()

                _uiState.update {
                    it.copy(
                        vaultBatchProgress = VaultBatchProgress(
                            isDownloading = true,
                            currentIndex = stepNum,
                            totalCount = specs.size,
                            completedCount = completed,
                            failedCount = failed,
                            currentBoardName = spec.board.name,
                            currentVariantLabel = spec.variantLabel,
                            statusDetail = "[$stepNum/${specs.size}] Downloading & saving ${spec.board.name} • ${spec.variantLabel}...",
                            overallProgress = (baseProgress + 0.05f * stepSpan).coerceIn(0.02f, 0.99f)
                        )
                    )
                }

                val targetRelease = when {
                    spec.ecosystem == FirmwareEcosystem.MESHTASTIC -> mtStable
                    spec.board.id == "lilygo_tdeck" && spec.tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.RIPPLE -> rippleDefault
                    spec.board.id == "lilygo_tdeck" && spec.tDeckMeshCoreFlavor == TDeckMeshCoreFlavor.WADAMESH -> wadaDefault
                    else -> mcLatest
                }

                try {
                    val prepared = repository.prepareFirmwarePayloadForVault(
                        board = spec.board,
                        release = targetRelease,
                        meshCoreRole = spec.meshCoreRole,
                        fullErase = spec.fullErase,
                        useMeshtasticMui = spec.useMeshtasticMui,
                        tDeckMeshCoreFlavor = spec.tDeckMeshCoreFlavor,
                        onProgress = { subProg, msg ->
                            _uiState.update { s ->
                                s.copy(
                                    vaultBatchProgress = s.vaultBatchProgress.copy(
                                        overallProgress = (baseProgress + subProg * stepSpan).coerceIn(0.02f, 0.99f),
                                        statusDetail = "[$stepNum/${specs.size}] $msg"
                                    )
                                )
                            }
                        }
                    )

                    vaultManager.savePreparedImageToVault(
                        spec = spec,
                        versionTag = targetRelease.tagName,
                        prepared = prepared
                    )
                    completed++
                    val updatedEntries = vaultManager.loadAllEntries()
                    _uiState.update {
                        it.copy(
                            offlineVaultEntries = updatedEntries,
                            vaultBatchProgress = it.vaultBatchProgress.copy(
                                completedCount = completed,
                                failedCount = failed
                            )
                        )
                    }
                    appendLog(
                        LogLevel.SUCCESS,
                        "OFFGRID-SAVED",
                        "Saved local file [$stepNum/${specs.size}]: ${spec.board.name} • ${spec.variantLabel} -> ${prepared.fileName} (${prepared.bytes.size / 1024} KB)"
                    )
                } catch (e: Exception) {
                    failed++
                    _uiState.update {
                        it.copy(
                            vaultBatchProgress = it.vaultBatchProgress.copy(
                                completedCount = completed,
                                failedCount = failed
                            )
                        )
                    }
                    appendLog(
                        LogLevel.ERROR,
                        "OFFGRID-ERR",
                        "Failed saving ${spec.board.name} (${spec.variantLabel}): ${e.message}"
                    )
                }
            }

            val finalEntries = vaultManager.loadAllEntries()
            val totalMb = finalEntries.sumOf { it.sizeBytes } / (1024f * 1024f)
            _uiState.update {
                it.copy(
                    offlineVaultEntries = finalEntries,
                    vaultBatchProgress = it.vaultBatchProgress.copy(
                        isDownloading = false,
                        overallProgress = 1f,
                        completedCount = completed,
                        failedCount = failed,
                        statusDetail = "Saved $completed firmware file(s) locally (${finalEntries.size} total • %.1f MB on phone)".format(Locale.US, totalMb)
                    )
                )
            }
            appendLog(
                LogLevel.SUCCESS,
                "OFFGRID-VAULT",
                "Off-Grid Firmware Pack saved! $completed file(s) written to phone storage (${finalEntries.size} total • %.1f MB) — ready for local flashing without internet.".format(Locale.US, totalMb)
            )
        }
    }

    /**
     * Loads a saved firmware entry from the Off-Grid Vault directly as the active Local Firmware File
     * and configures the matching radio board, ecosystem, and role/UI variant.
     */
    fun useSavedVaultEntryAsLocalFile(entry: CachedFirmwareEntry) {
        viewModelScope.launch {
            val prepared = vaultManager.loadPreparedImageFromEntry(entry)
            if (prepared == null) {
                appendLog(LogLevel.ERROR, "OFFGRID-LOAD", "Saved file ${entry.fileName} not found on disk. Please re-download.")
                return@launch
            }
            val matchedBoard = BoardCatalog.allBoards.firstOrNull { it.id == entry.boardId } ?: _uiState.value.selectedBoard
            val matchedRole = when {
                entry.variantKey.contains("companion_usb") -> MeshCoreRole.COMPANION_USB
                entry.variantKey.contains("repeater") -> MeshCoreRole.REPEATER
                entry.variantKey.contains("room_server") -> MeshCoreRole.ROOM_SERVER
                else -> MeshCoreRole.COMPANION_BLE
            }
            val matchedMui = !entry.variantKey.contains("baseui")
            val matchedFlavor = if (entry.variantKey.contains("wadamesh")) {
                TDeckMeshCoreFlavor.WADAMESH
            } else {
                TDeckMeshCoreFlavor.RIPPLE
            }
            val localFileObj = CustomFirmwareFile(
                uri = Uri.fromFile(java.io.File(entry.localFilePath)),
                displayName = entry.fileName,
                sizeBytes = entry.sizeBytes,
                sha256Hex = entry.sha256Hex,
                format = if (entry.fileName.endsWith(".uf2", ignoreCase = true)) "UF2" else "BIN"
            )
            _uiState.update {
                it.copy(
                    selectedBoard = matchedBoard,
                    selectedBaudRate = matchedBoard.mcu.defaultBaudRate,
                    selectedEcosystem = entry.ecosystem,
                    selectedMeshCoreRole = matchedRole,
                    tDeckMeshtasticMui = matchedMui,
                    tDeckMeshCoreFlavor = matchedFlavor,
                    fullFlashErase = entry.isFullFactoryImage,
                    customFirmwareFile = localFileObj,
                    customFirmwareBytes = prepared.bytes,
                    showOffGridSetupDialog = false
                )
            }
            appendLog(
                LogLevel.SUCCESS,
                "LOCAL-FIRMWARE",
                "Loaded saved local firmware for ${matchedBoard.name}: ${entry.fileName} (${entry.sizeBytes / 1024} KB @ 0x%05X)".format(entry.flashOffset)
            )
        }
    }

    /**
     * Downloads and saves the single currently selected Board + Ecosystem + Role/UI variant to the Off-Grid Vault.
     */
    fun saveCurrentSelectionOffline() {
        val snapshot = _uiState.value
        val release = if (snapshot.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
            snapshot.selectedRelease?.takeIf { it.ecosystem == FirmwareEcosystem.MESHTASTIC }
                ?: if (snapshot.selectedMeshtasticTrack == MeshtasticTrack.ALPHA) {
                    snapshot.meshtasticReleases.firstOrNull { it.isPrerelease }
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
                } else {
                    snapshot.meshtasticReleases.firstOrNull { !it.isPrerelease }
                        ?: snapshot.meshtasticReleases.firstOrNull()
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
                }
        } else {
            snapshot.selectedRelease?.takeIf { it.ecosystem == FirmwareEcosystem.MESHCORE }
                ?: snapshot.meshCoreReleases.firstOrNull()
                ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHCORE).first()
        }

        val key = vaultManager.buildVaultKey(
            board = snapshot.selectedBoard,
            ecosystem = snapshot.selectedEcosystem,
            meshCoreRole = snapshot.selectedMeshCoreRole,
            fullErase = snapshot.fullFlashErase,
            useMeshtasticMui = snapshot.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
            meshtasticTrack = snapshot.selectedMeshtasticTrack
        )
        val vKey = vaultManager.buildVariantKey(
            board = snapshot.selectedBoard,
            ecosystem = snapshot.selectedEcosystem,
            meshCoreRole = snapshot.selectedMeshCoreRole,
            fullErase = snapshot.fullFlashErase,
            useMeshtasticMui = snapshot.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
            meshtasticTrack = snapshot.selectedMeshtasticTrack
        )
        val vLabel = vaultManager.buildVariantLabel(
            board = snapshot.selectedBoard,
            ecosystem = snapshot.selectedEcosystem,
            meshCoreRole = snapshot.selectedMeshCoreRole,
            fullErase = snapshot.fullFlashErase,
            useMeshtasticMui = snapshot.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
            meshtasticTrack = snapshot.selectedMeshtasticTrack
        )
        val spec = VaultTargetSpec(
            key = key,
            board = snapshot.selectedBoard,
            ecosystem = snapshot.selectedEcosystem,
            variantKey = vKey,
            variantLabel = vLabel,
            meshCoreRole = snapshot.selectedMeshCoreRole,
            meshtasticTrack = snapshot.selectedMeshtasticTrack,
            useMeshtasticMui = snapshot.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
            fullErase = snapshot.fullFlashErase
        )

        batchDownloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    vaultBatchProgress = VaultBatchProgress(
                        isDownloading = true,
                        currentIndex = 1,
                        totalCount = 1,
                        currentBoardName = spec.board.name,
                        currentVariantLabel = spec.variantLabel,
                        statusDetail = "Saving ${spec.board.name} • ${spec.variantLabel} to local storage...",
                        overallProgress = 0.1f
                    )
                )
            }
            try {
                val prepared = repository.prepareFirmwarePayloadForVault(
                    board = spec.board,
                    release = release,
                    meshCoreRole = spec.meshCoreRole,
                    fullErase = spec.fullErase,
                    useMeshtasticMui = spec.useMeshtasticMui,
                    tDeckMeshCoreFlavor = spec.tDeckMeshCoreFlavor,
                    onProgress = { p, msg ->
                        _uiState.update { s ->
                            s.copy(
                                vaultBatchProgress = s.vaultBatchProgress.copy(
                                    overallProgress = p.coerceIn(0.05f, 0.99f),
                                    statusDetail = msg
                                )
                            )
                        }
                    }
                )
                val savedEntry = vaultManager.savePreparedImageToVault(spec, release.tagName, prepared)
                val updatedEntries = vaultManager.loadAllEntries()
                val localFileObj = CustomFirmwareFile(
                    uri = Uri.fromFile(java.io.File(savedEntry.localFilePath)),
                    displayName = savedEntry.fileName,
                    sizeBytes = savedEntry.sizeBytes,
                    sha256Hex = savedEntry.sha256Hex,
                    format = if (savedEntry.fileName.endsWith(".uf2", ignoreCase = true)) "UF2" else "BIN"
                )
                _uiState.update {
                    it.copy(
                        offlineVaultEntries = updatedEntries,
                        customFirmwareFile = localFileObj,
                        customFirmwareBytes = prepared.bytes,
                        vaultBatchProgress = VaultBatchProgress(
                            isDownloading = false,
                            currentIndex = 1,
                            totalCount = 1,
                            completedCount = 1,
                            overallProgress = 1f,
                            statusDetail = "Saved & loaded ${prepared.fileName} (${prepared.bytes.size / 1024} KB) for local use"
                        )
                    )
                }
                appendLog(
                    LogLevel.SUCCESS,
                    "OFFGRID-SAVED",
                    "Saved & loaded ${prepared.fileName} (${prepared.bytes.size / 1024} KB) to Off-Grid Vault for ${spec.board.name}."
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        vaultBatchProgress = VaultBatchProgress(
                            isDownloading = false,
                            failedCount = 1,
                            statusDetail = "Failed to save offline: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun deleteOfflineEntry(key: String) {
        viewModelScope.launch {
            val remaining = vaultManager.deleteEntry(key)
            _uiState.update { it.copy(offlineVaultEntries = remaining) }
            appendLog(LogLevel.INFO, "OFFGRID-VAULT", "Removed cached firmware entry ($key) from local storage.")
        }
    }

    fun clearOfflineVault() {
        viewModelScope.launch {
            val empty = vaultManager.clearAllVaultEntries()
            _uiState.update {
                it.copy(
                    offlineVaultEntries = empty,
                    vaultBatchProgress = VaultBatchProgress()
                )
            }
            appendLog(LogLevel.INFO, "OFFGRID-VAULT", "Cleared all cached firmware binaries from Off-Grid Vault.")
        }
    }

    fun currentSelectionOfflineEntry(): CachedFirmwareEntry? {
        val state = _uiState.value
        return vaultManager.findMatchingEntry(
            entries = state.offlineVaultEntries,
            board = state.selectedBoard,
            ecosystem = state.selectedEcosystem,
            meshCoreRole = state.selectedMeshCoreRole,
            fullErase = state.fullFlashErase,
            useMeshtasticMui = state.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = state.tDeckMeshCoreFlavor,
            meshtasticTrack = state.selectedMeshtasticTrack,
            targetVersionTag = state.selectedRelease?.tagName
        )
    }

    fun selectEcosystem(ecosystem: FirmwareEcosystem) {
        _uiState.update { state ->
            val releases = if (ecosystem == FirmwareEcosystem.MESHTASTIC) {
                state.meshtasticReleases
            } else {
                state.meshCoreReleases
            }
            val activeRelease = if (ecosystem == FirmwareEcosystem.MESHTASTIC) {
                when (state.selectedMeshtasticTrack) {
                    MeshtasticTrack.STABLE -> releases.firstOrNull { !it.isPrerelease }
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
                    MeshtasticTrack.ALPHA -> releases.firstOrNull { it.isPrerelease }
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
                    MeshtasticTrack.CUSTOM -> releases.firstOrNull()
                }
            } else {
                releases.firstOrNull()
            }
            state.copy(
                selectedEcosystem = ecosystem,
                selectedRelease = activeRelease,
                customFirmwareFile = null,
                customFirmwareBytes = null
            )
        }
        if (ecosystem == FirmwareEcosystem.MESHCORE && _uiState.value.selectedBoard.id == "lilygo_tdeck") {
            selectTDeckMeshCoreFlavor(_uiState.value.tDeckMeshCoreFlavor)
        }
        appendLog(
            LogLevel.INFO,
            "ECOSYSTEM",
            "Switched firmware target to ${ecosystem.displayName} (${ecosystem.tagline})"
        )
    }

    fun selectMeshtasticTrack(track: MeshtasticTrack) {
        _uiState.update { state ->
            val matchingRelease = when (track) {
                MeshtasticTrack.STABLE -> state.meshtasticReleases.firstOrNull { !it.isPrerelease }
                    ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
                MeshtasticTrack.ALPHA -> state.meshtasticReleases.firstOrNull { it.isPrerelease }
                    ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
                MeshtasticTrack.CUSTOM -> state.selectedRelease
            }
            state.copy(
                selectedMeshtasticTrack = track,
                selectedRelease = matchingRelease,
                customFirmwareFile = if (track == MeshtasticTrack.CUSTOM) state.customFirmwareFile else null,
                customFirmwareBytes = if (track == MeshtasticTrack.CUSTOM) state.customFirmwareBytes else null
            )
        }
        val current = _uiState.value
        val expectedFile = expectedFirmwareFileName()
        appendLog(
            LogLevel.INFO,
            "TRACK",
            "Switched Meshtastic track to ${track.label} (Version: ${current.selectedRelease?.tagName ?: "None"}) -> Target: $expectedFile"
        )
    }

    fun selectMeshCoreRole(role: MeshCoreRole) {
        _uiState.update { state ->
            val currentRel = state.selectedRelease
            val bestRel = if (state.selectedEcosystem == FirmwareEcosystem.MESHCORE && state.selectedBoard.id != "lilygo_tdeck") {
                val currentHasRole = currentRel != null && repository.selectCandidateAssets(
                    release = currentRel,
                    board = state.selectedBoard,
                    meshCoreRole = role,
                    useMeshtasticMui = state.tDeckMeshtasticMui,
                    tDeckMeshCoreFlavor = state.tDeckMeshCoreFlavor
                ).isNotEmpty()
                if (currentHasRole) {
                    currentRel
                } else {
                    state.meshCoreReleases.firstOrNull { rel ->
                        repository.selectCandidateAssets(
                            release = rel,
                            board = state.selectedBoard,
                            meshCoreRole = role,
                            useMeshtasticMui = state.tDeckMeshtasticMui,
                            tDeckMeshCoreFlavor = state.tDeckMeshCoreFlavor
                        ).isNotEmpty()
                    } ?: currentRel
                }
            } else {
                currentRel
            }
            state.copy(
                selectedMeshCoreRole = role,
                selectedRelease = bestRel,
                customFirmwareFile = if (role == MeshCoreRole.CUSTOM) state.customFirmwareFile else null,
                customFirmwareBytes = if (role == MeshCoreRole.CUSTOM) state.customFirmwareBytes else null
            )
        }
        val stateNow = _uiState.value
        val expectedFile = stateNow.selectedRelease?.let { rel ->
            repository.resolveExpectedFileName(
                board = stateNow.selectedBoard,
                release = rel,
                meshCoreRole = role,
                fullErase = stateNow.fullFlashErase,
                useMeshtasticMui = stateNow.tDeckMeshtasticMui,
                tDeckMeshCoreFlavor = stateNow.tDeckMeshCoreFlavor
            )
        } ?: role.binarySuffix
        appendLog(
            LogLevel.INFO,
            "MESHCORE",
            "Selected MeshCore role: ${role.label} -> Target binary: $expectedFile"
        )
    }

    fun setTDeckMeshtasticMui(enabled: Boolean) {
        _uiState.update {
            it.copy(
                tDeckMeshtasticMui = enabled,
                customFirmwareFile = null,
                customFirmwareBytes = null
            )
        }
        val targetFile = expectedFirmwareFileName()
        appendLog(
            LogLevel.INFO,
            "T-DECK",
            if (enabled) "Enabled Meshtastic UI (MUI / t-deck-tft) for LilyGO T-Deck -> Target: $targetFile"
            else "Selected classic Meshtastic BaseUI (t-deck) for LilyGO T-Deck -> Target: $targetFile"
        )
    }

    fun selectTDeckMeshCoreFlavor(flavor: TDeckMeshCoreFlavor) {
        val placeholderList = repository.curatedTDeckFlavorFallback(flavor)
        _uiState.update { state ->
            state.copy(
                tDeckMeshCoreFlavor = flavor,
                meshCoreReleases = placeholderList,
                selectedRelease = if (state.selectedEcosystem == FirmwareEcosystem.MESHCORE) {
                    placeholderList.firstOrNull()
                } else {
                    state.selectedRelease
                },
                customFirmwareFile = null,
                customFirmwareBytes = null
            )
        }
        appendLog(
            LogLevel.INFO,
            "T-DECK",
            "Selected T-Deck MeshCore firmware variant: ${flavor.label} (${flavor.repoOwner}/${flavor.repoName})"
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingReleases = true) }
            val liveFlavorReleases = repository.fetchTDeckMeshCoreReleases(flavor)
            _uiState.update { state ->
                state.copy(
                    meshCoreReleases = liveFlavorReleases,
                    selectedRelease = if (state.selectedEcosystem == FirmwareEcosystem.MESHCORE) {
                        liveFlavorReleases.firstOrNull() ?: state.selectedRelease
                    } else {
                        state.selectedRelease
                    },
                    isFetchingReleases = false
                )
            }
        }
    }

    fun selectBoard(board: HardwareBoard) {
        val prevBoardId = _uiState.value.selectedBoard.id
        usbManager.updateSelectedBoardHint(board)
        _uiState.update {
            it.copy(
                selectedBoard = board,
                selectedBaudRate = board.mcu.defaultBaudRate,
                selectedTransport = if (!board.mcu.supportsBleDfu && it.selectedTransport == ConnectionTransport.BLE_DFU) {
                    ConnectionTransport.USB_OTG
                } else {
                    it.selectedTransport
                },
                customFirmwareFile = if (prevBoardId != board.id) null else it.customFirmwareFile,
                customFirmwareBytes = if (prevBoardId != board.id) null else it.customFirmwareBytes
            )
        }
        if (board.id == "lilygo_tdeck" && _uiState.value.selectedEcosystem == FirmwareEcosystem.MESHCORE) {
            selectTDeckMeshCoreFlavor(_uiState.value.tDeckMeshCoreFlavor)
        } else if (prevBoardId == "lilygo_tdeck" && board.id != "lilygo_tdeck" && _uiState.value.selectedEcosystem == FirmwareEcosystem.MESHCORE) {
            refreshFirmwareCatalog()
        }
        appendLog(
            LogLevel.INFO,
            "BOARD",
            "Selected target hardware: ${board.name} (${board.mcu.shortName} • ${board.flashSizeMb}MB Flash • ${board.loraChip})"
        )
    }

    fun selectTransport(transport: ConnectionTransport) {
        _uiState.update { it.copy(selectedTransport = transport) }
        if (transport == ConnectionTransport.BLE_DFU && _uiState.value.bleDiscoveredNodes.isEmpty()) {
            startBleScan()
        } else if (transport == ConnectionTransport.USB_OTG) {
            rescanUsbBus()
        }
    }

    fun setMcuFilter(mcu: McuArchitecture?) {
        _uiState.update { it.copy(mcuFilter = mcu) }
    }

    fun setBoardSearchQuery(query: String) {
        _uiState.update { it.copy(boardSearchQuery = query) }
    }

    fun selectRelease(release: FirmwareRelease) {
        val newTrack = if (release.ecosystem == FirmwareEcosystem.MESHTASTIC) {
            if (release.isPrerelease) MeshtasticTrack.ALPHA else MeshtasticTrack.STABLE
        } else {
            _uiState.value.selectedMeshtasticTrack
        }
        _uiState.update {
            it.copy(
                selectedEcosystem = release.ecosystem,
                selectedMeshtasticTrack = newTrack,
                selectedRelease = release,
                customFirmwareFile = null,
                customFirmwareBytes = null
            )
        }
        appendLog(
            LogLevel.INFO,
            "RELEASE",
            "Selected ${release.ecosystem.displayName} release ${release.tagName} (${if (release.isPrerelease) "Alpha" else "Beta / Stable"})"
        )
    }

    fun toggleFullFlashErase(enabled: Boolean) {
        _uiState.update {
            it.copy(
                fullFlashErase = enabled,
                customFirmwareFile = null,
                customFirmwareBytes = null
            )
        }
    }

    fun toggleBundleWebUi(enabled: Boolean) {
        _uiState.update { it.copy(bundleWebUiPartition = enabled) }
    }

    fun setBaudRate(baud: Int) {
        _uiState.update { it.copy(selectedBaudRate = baud) }
    }

    fun toggleLoopbackBench(enabled: Boolean) {
        _uiState.update { it.copy(loopbackTestBench = enabled) }
        appendLog(
            LogLevel.INFO,
            "BENCH",
            if (enabled) "Hardware Loopback / Protocol Test Bench ENABLED for dry-run verification."
            else "Hardware Loopback Test Bench DISABLED (strict physical USB/BLE hardware mode)."
        )
    }

    fun rescanUsbBus() {
        val found = usbManager.scanUsbDevices(_uiState.value.selectedBoard)
        if (found.isEmpty()) {
            appendLog(
                LogLevel.INFO,
                "USB-HOST",
                "Scanned Android USB Host bus: 0 physical OTG devices attached (${if (_uiState.value.loopbackTestBench) "Loopback Test Bench ready" else "Connect USB-C OTG adapter"})."
            )
        } else {
            appendLog(
                LogLevel.SUCCESS,
                "USB-HOST",
                "Detected ${found.size} USB OTG device(s): ${found.joinToString { "${it.productName} (VID:0x%04X PID:0x%04X)".format(it.vendorId, it.productId) }}"
            )
        }
    }

    fun requestUsbPermission(device: UsbAttachedBoard) {
        usbManager.requestPermission(device.deviceName)
    }

    fun startBleScan() {
        bleScanJob?.cancel()
        _uiState.update { it.copy(isBleScanning = true) }
        val startedHardwareScan = bleManager.startBleScan(loopbackBench = _uiState.value.loopbackTestBench)
        bleScanJob = viewModelScope.launch {
            if (startedHardwareScan) {
                kotlinx.coroutines.delay(8000)
            } else {
                kotlinx.coroutines.delay(800)
            }
            bleManager.stopBleScan()
            _uiState.update { it.copy(isBleScanning = false) }
        }
    }

    fun selectBleNode(node: BleDiscoveredNode) {
        _uiState.update { state ->
            val matchedBoard = node.matchedBoard ?: state.selectedBoard
            state.copy(
                selectedBleNode = node,
                selectedBoard = matchedBoard
            )
        }
        appendLog(LogLevel.INFO, "BLE-NODE", "Targeted BLE node ${node.name} [${node.address}] (${node.rssi} dBm)")
    }

    fun refreshFirmwareCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingReleases = true) }
            val mtResult = repository.fetchReleases(FirmwareEcosystem.MESHTASTIC)
            val mcResult = repository.fetchReleases(FirmwareEcosystem.MESHCORE)

            _uiState.update { state ->
                val mtList = mtResult.getOrDefault(state.meshtasticReleases)
                val mcList = mcResult.getOrDefault(state.meshCoreReleases)
                val newSelectedRelease = if (state.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
                    when (state.selectedMeshtasticTrack) {
                        MeshtasticTrack.STABLE -> mtList.firstOrNull { !it.isPrerelease }
                            ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
                        MeshtasticTrack.ALPHA -> mtList.firstOrNull { it.isPrerelease }
                            ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
                        MeshtasticTrack.CUSTOM -> state.selectedRelease
                    }
                } else {
                    mcList.firstOrNull() ?: state.selectedRelease
                }
                state.copy(
                    meshtasticReleases = mtList,
                    meshCoreReleases = mcList,
                    selectedRelease = newSelectedRelease ?: state.selectedRelease,
                    isFetchingReleases = false
                )
            }
            appendLog(
                LogLevel.SUCCESS,
                "GITHUB-API",
                "Synchronized Meshtastic (Selected: ${_uiState.value.selectedRelease?.tagName}) & MeshCore release catalogs."
            )
        }
    }

    fun importCustomFirmwareUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                var fileName = "custom_firmware.bin"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
                    }
                }
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val currentState = _uiState.value
                val inspected = repository.inspectUserImportedBytes(
                    rawBytes = rawBytes,
                    fileName = fileName,
                    board = currentState.selectedBoard,
                    ecosystem = currentState.selectedEcosystem,
                    meshCoreRole = currentState.selectedMeshCoreRole,
                    preferFactoryImage = currentState.fullFlashErase
                )
                val ext = inspected.fileName.substringAfterLast('.', "bin").uppercase(Locale.US)
                val customFile = CustomFirmwareFile(
                    uri = uri,
                    displayName = inspected.fileName,
                    sizeBytes = inspected.bytes.size.toLong(),
                    sha256Hex = inspected.sha256Hex,
                    format = ext
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            customFirmwareFile = customFile,
                            customFirmwareBytes = inspected.bytes,
                            selectedMeshtasticTrack = if (state.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticTrack.CUSTOM else state.selectedMeshtasticTrack,
                            selectedMeshCoreRole = if (state.selectedEcosystem == FirmwareEcosystem.MESHCORE) MeshCoreRole.CUSTOM else state.selectedMeshCoreRole
                        )
                    }
                    appendLog(
                        LogLevel.SUCCESS,
                        "FILE-IMPORT",
                        "Prepared ${inspected.fileName} (${inspected.bytes.size / 1024} KB @ 0x%05X) • SHA-256: ${inspected.sha256Hex.take(16)}…".format(inspected.flashOffset)
                    )
                }
            } catch (e: Exception) {
                appendLog(LogLevel.ERROR, "FILE-IMPORT", "Failed to read firmware file: ${e.message}")
            }
        }
    }

    fun clearCustomFirmware() {
        _uiState.update {
            it.copy(
                customFirmwareFile = null,
                customFirmwareBytes = null,
                selectedMeshtasticTrack = if (it.selectedMeshtasticTrack == MeshtasticTrack.CUSTOM) MeshtasticTrack.STABLE else it.selectedMeshtasticTrack,
                selectedMeshCoreRole = if (it.selectedMeshCoreRole == MeshCoreRole.CUSTOM) MeshCoreRole.COMPANION_BLE else it.selectedMeshCoreRole
            )
        }
    }

    fun trigger1200BpsDfuTouch() {
        val state = _uiState.value
        viewModelScope.launch {
            if (state.selectedTransport == ConnectionTransport.BLE_DFU ||
                (state.selectedUsbDevice == null && state.selectedBleNode != null)
            ) {
                val rebooted = bleManager.triggerBleDfuMode(
                    targetNode = state.selectedBleNode,
                    board = state.selectedBoard,
                    loopbackBench = state.loopbackTestBench
                )
                if (rebooted != null) {
                    _uiState.update {
                        it.copy(
                            selectedBleNode = rebooted,
                            selectedBoard = rebooted.matchedBoard ?: it.selectedBoard
                        )
                    }
                }
            } else {
                usbManager.trigger1200BpsBootloaderTouch(
                    attached = state.selectedUsbDevice,
                    board = state.selectedBoard,
                    loopbackBench = state.loopbackTestBench
                )
            }
        }
    }

    fun triggerEspReset() {
        val state = _uiState.value
        viewModelScope.launch {
            usbManager.triggerEsp32BootloaderReset(
                attached = state.selectedUsbDevice,
                board = state.selectedBoard,
                loopbackBench = state.loopbackTestBench
            )
        }
    }

    fun startFirmwareFlash() {
        val snapshot = _uiState.value
        if (snapshot.flashProgress.phase != FlashPhase.IDLE &&
            snapshot.flashProgress.phase != FlashPhase.COMPLETED &&
            snapshot.flashProgress.phase != FlashPhase.FAILED
        ) {
            return
        }

        val release = if (snapshot.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
            snapshot.selectedRelease?.takeIf { it.ecosystem == FirmwareEcosystem.MESHTASTIC }
                ?: if (snapshot.selectedMeshtasticTrack == MeshtasticTrack.ALPHA) {
                    snapshot.meshtasticReleases.firstOrNull { it.isPrerelease }
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).last()
                } else {
                    snapshot.meshtasticReleases.firstOrNull { !it.isPrerelease }
                        ?: snapshot.meshtasticReleases.firstOrNull()
                        ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHTASTIC).first()
                }
        } else {
            snapshot.selectedRelease?.takeIf { it.ecosystem == FirmwareEcosystem.MESHCORE }
                ?: snapshot.meshCoreReleases.firstOrNull()
                ?: repository.curatedFallbackReleases(FirmwareEcosystem.MESHCORE).first()
        }

        viewModelScope.launch {
            appendLog(
                LogLevel.INFO,
                "FLASH-JOB",
                "Starting ${snapshot.selectedEcosystem.displayName} flash for ${snapshot.selectedBoard.name} via ${snapshot.selectedTransport.title}"
            )

            _uiState.update {
                it.copy(
                    flashProgress = FlashProgressState(
                        phase = FlashPhase.PREPARING_BINARY,
                        overallProgress = 0.05f,
                        currentPartitionName = "Checking Off-Grid Vault / Preparing ${snapshot.selectedBoard.name} Image..."
                    )
                )
            }

            val customBytes = snapshot.customFirmwareBytes
            val customFile = snapshot.customFirmwareFile

            // Check if this exact board + ecosystem + role/UI variant is already saved in the Off-Grid Vault!
            val cachedEntry = vaultManager.findMatchingEntry(
                entries = snapshot.offlineVaultEntries,
                board = snapshot.selectedBoard,
                ecosystem = snapshot.selectedEcosystem,
                meshCoreRole = snapshot.selectedMeshCoreRole,
                fullErase = snapshot.fullFlashErase,
                useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
                meshtasticTrack = snapshot.selectedMeshtasticTrack,
                targetVersionTag = release.tagName
            )
            val isMatchingVariant = if (cachedEntry != null && snapshot.selectedBoard.id == "lilygo_tdeck" && snapshot.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
                val trackPart = if (snapshot.selectedMeshtasticTrack == MeshtasticTrack.ALPHA) "alpha" else "stable"
                val prefix = if (snapshot.tDeckMeshtasticMui) "mt_tdeck_mui_$trackPart" else "mt_tdeck_baseui_$trackPart"
                val basePrefix = if (snapshot.tDeckMeshtasticMui) "mt_tdeck_mui" else "mt_tdeck_baseui"
                cachedEntry.variantKey.startsWith(prefix) || (
                    cachedEntry.variantKey.startsWith(basePrefix) && cachedEntry.versionTag == release.tagName
                )
            } else {
                true
            }
            val canUseCachedEntry = cachedEntry != null &&
                isMatchingVariant &&
                (snapshot.selectedRelease == null || snapshot.selectedRelease?.tagName == cachedEntry.versionTag) &&
                ((cachedEntry.isRealBinary && cachedEntry.sizeBytes > 65536L) || snapshot.loopbackTestBench)
            val cachedImage = if (customBytes == null && canUseCachedEntry && cachedEntry != null) {
                vaultManager.loadPreparedImageFromEntry(cachedEntry)
            } else {
                null
            }

            val preparedImage = if (customBytes != null && customFile != null) {
                val inspected = repository.inspectAndWrapBinary(
                    bytes = customBytes,
                    fileName = customFile.displayName,
                    board = snapshot.selectedBoard,
                    isRealBinary = true
                )
                appendLog(
                    LogLevel.INFO,
                    "PAYLOAD",
                    "Using imported binary: ${inspected.fileName} (${inspected.bytes.size / 1024} KB @ 0x%05X)".format(inspected.flashOffset)
                )
                inspected
            } else if (cachedImage != null && cachedEntry != null) {
                appendLog(
                    LogLevel.SUCCESS,
                    "OFFGRID-VAULT",
                    "Loaded saved offline firmware: ${cachedImage.fileName} (${cachedImage.bytes.size / 1024} KB @ 0x%05X) • Zero internet required!".format(cachedImage.flashOffset)
                )
                _uiState.update { s ->
                    s.copy(
                        flashProgress = s.flashProgress.copy(
                            phase = FlashPhase.PREPARING_BINARY,
                            overallProgress = 0.14f,
                            currentPartitionName = "Loaded ${cachedImage.fileName} from Off-Grid Vault"
                        )
                    )
                }
                cachedImage
            } else {
                val downloaded = repository.prepareFirmwarePayload(
                    board = snapshot.selectedBoard,
                    release = release,
                    meshCoreRole = snapshot.selectedMeshCoreRole,
                    fullErase = snapshot.fullFlashErase,
                    useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                    tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
                    onProgress = { p, msg ->
                        appendLog(LogLevel.INFO, "BINARY", msg)
                        _uiState.update { s ->
                            s.copy(
                                flashProgress = s.flashProgress.copy(
                                    phase = FlashPhase.PREPARING_BINARY,
                                    overallProgress = (p * 0.14f).coerceAtMost(0.14f),
                                    currentPartitionName = msg
                                )
                            )
                        }
                    }
                )
                // Automatically cache real downloaded binaries into the Off-Grid Vault for future field use
                if (downloaded.isRealBinary) {
                    val key = vaultManager.buildVaultKey(
                        board = snapshot.selectedBoard,
                        ecosystem = snapshot.selectedEcosystem,
                        meshCoreRole = snapshot.selectedMeshCoreRole,
                        fullErase = snapshot.fullFlashErase,
                        useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                        tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor
                    )
                    val vKey = vaultManager.buildVariantKey(
                        board = snapshot.selectedBoard,
                        ecosystem = snapshot.selectedEcosystem,
                        meshCoreRole = snapshot.selectedMeshCoreRole,
                        fullErase = snapshot.fullFlashErase,
                        useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                        tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor
                    )
                    val vLabel = vaultManager.buildVariantLabel(
                        board = snapshot.selectedBoard,
                        ecosystem = snapshot.selectedEcosystem,
                        meshCoreRole = snapshot.selectedMeshCoreRole,
                        fullErase = snapshot.fullFlashErase,
                        useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                        tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor
                    )
                    val spec = VaultTargetSpec(
                        key = key,
                        board = snapshot.selectedBoard,
                        ecosystem = snapshot.selectedEcosystem,
                        variantKey = vKey,
                        variantLabel = vLabel,
                        meshCoreRole = snapshot.selectedMeshCoreRole,
                        useMeshtasticMui = snapshot.tDeckMeshtasticMui,
                        tDeckMeshCoreFlavor = snapshot.tDeckMeshCoreFlavor,
                        fullErase = snapshot.fullFlashErase
                    )
                    vaultManager.savePreparedImageToVault(spec, release.tagName, downloaded)
                    val updatedEntries = vaultManager.loadAllEntries()
                    _uiState.update { it.copy(offlineVaultEntries = updatedEntries) }
                }
                downloaded
            }

            when (snapshot.selectedTransport) {
                ConnectionTransport.USB_OTG -> {
                    usbManager.flashFirmwareOverUsb(
                        attached = snapshot.selectedUsbDevice,
                        board = snapshot.selectedBoard,
                        preparedImage = preparedImage,
                        fullErase = snapshot.fullFlashErase,
                        bundleWebUi = snapshot.bundleWebUiPartition,
                        baudRate = snapshot.selectedBaudRate,
                        loopbackBench = snapshot.loopbackTestBench,
                        onProgress = { progress ->
                            _uiState.update { it.copy(flashProgress = progress) }
                        }
                    )
                }
                ConnectionTransport.BLE_DFU -> {
                    bleManager.flashFirmwareOverBleDfu(
                        targetNode = snapshot.selectedBleNode,
                        board = snapshot.selectedBoard,
                        payload = preparedImage.bytes,
                        sha256Hex = preparedImage.sha256Hex,
                        fullErase = snapshot.fullFlashErase,
                        loopbackBench = snapshot.loopbackTestBench,
                        isRealBinary = preparedImage.isRealBinary,
                        onProgress = { progress ->
                            _uiState.update { it.copy(flashProgress = progress) }
                        }
                    )
                }
            }
        }
    }

    fun resetFlashState() {
        _uiState.update { it.copy(flashProgress = FlashProgressState(phase = FlashPhase.IDLE)) }
    }

    fun sendConsoleCommand(command: String) {
        val state = _uiState.value
        usbManager.sendSerialCommand(
            command = command,
            board = state.selectedBoard,
            ecosystemName = state.selectedEcosystem.displayName,
            loopbackBench = state.loopbackTestBench
        )
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    fun expectedFirmwareFileName(): String {
        val state = _uiState.value
        state.customFirmwareFile?.let { return it.displayName }
        val offline = currentSelectionOfflineEntry()
        if (offline != null && (state.selectedRelease == null || state.selectedRelease?.tagName == offline.versionTag)) {
            val isMatchingVariant = if (state.selectedBoard.id == "lilygo_tdeck" && state.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
                val trackPart = if (state.selectedMeshtasticTrack == MeshtasticTrack.ALPHA) "alpha" else "stable"
                val prefix = if (state.tDeckMeshtasticMui) "mt_tdeck_mui_$trackPart" else "mt_tdeck_baseui_$trackPart"
                val basePrefix = if (state.tDeckMeshtasticMui) "mt_tdeck_mui" else "mt_tdeck_baseui"
                offline.variantKey.startsWith(prefix) || (
                    offline.variantKey.startsWith(basePrefix) && offline.versionTag == state.selectedRelease?.tagName
                )
            } else {
                true
            }
            if (isMatchingVariant) {
                return offline.fileName
            }
        }
        val rel = state.selectedRelease ?: return "firmware.bin"
        return repository.resolveExpectedFileName(
            board = state.selectedBoard,
            release = rel,
            meshCoreRole = state.selectedMeshCoreRole,
            fullErase = state.fullFlashErase,
            useMeshtasticMui = state.tDeckMeshtasticMui,
            tDeckMeshCoreFlavor = state.tDeckMeshCoreFlavor
        )
    }

    private fun appendLog(level: LogLevel, tag: String, message: String) {
        val entry = ConsoleLogEntry(
            id = logCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _uiState.update { current ->
            val updated = (current.consoleLogs + entry).takeLast(180)
            current.copy(consoleLogs = updated)
        }
    }
}

