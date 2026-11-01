package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BleDiscoveredNode
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.ConnectionTransport
import com.example.model.FirmwareEcosystem
import com.example.model.FlashPhase
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.MeshtasticTrack
import com.example.model.TDeckMeshCoreFlavor
import com.example.model.UsbAttachedBoard
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.HardwareChipBadge
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralError
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FlasherWorkbenchScreen(
    uiState: MeshInstallerUiState,
    expectedFileName: String,
    currentOfflineEntry: CachedFirmwareEntry? = null,
    onSelectEcosystem: (FirmwareEcosystem) -> Unit,
    onSelectMeshtasticTrack: (MeshtasticTrack) -> Unit,
    onSelectMeshCoreRole: (MeshCoreRole) -> Unit,
    onSetTDeckMeshtasticMui: (Boolean) -> Unit,
    onSelectTDeckMeshCoreFlavor: (TDeckMeshCoreFlavor) -> Unit,
    onSelectBoard: (HardwareBoard) -> Unit,
    onOpenBoardPicker: () -> Unit,
    onOpenReleasesPicker: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenOffGridVault: () -> Unit = {},
    onDownloadAllForSelected: () -> Unit = {},
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit = {},
    onSaveCurrentSelectionOffline: () -> Unit = {},
    onSelectTransport: (ConnectionTransport) -> Unit,
    onRescanUsb: () -> Unit,
    onRequestUsbPermission: (UsbAttachedBoard) -> Unit,
    onStartBleScanWithPermissions: () -> Unit,
    onSelectBleNode: (BleDiscoveredNode) -> Unit,
    onToggleFullErase: (Boolean) -> Unit,
    onToggleBundleWebUi: (Boolean) -> Unit,
    onSelectBaudRate: (Int) -> Unit,
    onToggleLoopbackBench: (Boolean) -> Unit,
    onImportCustomFile: () -> Unit,
    onClearCustomFile: () -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onStartFlash: () -> Unit,
    onResetFlashState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ecoColor = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        MeshtasticGreen
    } else {
        MeshCoreCyan
    }

    val roleOrTrackBadge = when {
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC ->
            if (uiState.tDeckMeshtasticMui) "MUI TOUCH" else "BASE UI"
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHCORE ->
            uiState.tDeckMeshCoreFlavor.badge
        uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC ->
            uiState.selectedMeshtasticTrack.badge
        else ->
            uiState.selectedMeshCoreRole.badge
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("flasher_workbench_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Status & Quick Summary Banner
        item {
            QuickReadinessBanner(
                uiState = uiState,
                ecoColor = ecoColor,
                onRequestUsbPermission = onRequestUsbPermission,
                onRescanUsb = onRescanUsb,
                onStartBleScanWithPermissions = onStartBleScanWithPermissions
            )
        }

        // Off-Grid Firmware Pack & My Devices Quick Card
        item {
            OffGridVaultSummaryBanner(
                uiState = uiState,
                currentOfflineEntry = currentOfflineEntry,
                onOpenOffGridVault = onOpenOffGridVault,
                onDownloadAllForSelected = onDownloadAllForSelected,
                onUseSavedEntry = onUseSavedEntry,
                onSaveCurrentSelectionOffline = onSaveCurrentSelectionOffline
            )
        }

        // STEP 1: Choose Your Radio Board
        item {
            Step1BoardSelectorCard(
                uiState = uiState,
                ecoColor = ecoColor,
                onSelectBoard = onSelectBoard,
                onOpenBoardPicker = onOpenBoardPicker,
                onOpenOffGridVault = onOpenOffGridVault,
                onTriggerEspReset = onTriggerEspReset,
                onTrigger1200BpsTouch = onTrigger1200BpsTouch
            )
        }

        // STEP 2: Choose Firmware (Meshtastic vs MeshCore & T-Deck UI / Flavor options)
        item {
            Step2FirmwareSelectorCard(
                uiState = uiState,
                ecoColor = ecoColor,
                expectedFileName = expectedFileName,
                currentOfflineEntry = currentOfflineEntry,
                onSelectEcosystem = onSelectEcosystem,
                onSelectMeshtasticTrack = onSelectMeshtasticTrack,
                onSelectMeshCoreRole = onSelectMeshCoreRole,
                onSetTDeckMeshtasticMui = onSetTDeckMeshtasticMui,
                onSelectTDeckMeshCoreFlavor = onSelectTDeckMeshCoreFlavor,
                onOpenReleasesPicker = onOpenReleasesPicker,
                onSaveCurrentSelectionOffline = onSaveCurrentSelectionOffline,
                onImportCustomFile = onImportCustomFile,
                onClearCustomFile = onClearCustomFile
            )
        }

        // STEP 3: Connection & Install Mode (Update vs Fresh Erase)
        item {
            Step3ConnectionAndModeCard(
                uiState = uiState,
                ecoColor = ecoColor,
                onSelectTransport = onSelectTransport,
                onRescanUsb = onRescanUsb,
                onRequestUsbPermission = onRequestUsbPermission,
                onStartBleScanWithPermissions = onStartBleScanWithPermissions,
                onSelectBleNode = onSelectBleNode,
                onToggleFullErase = onToggleFullErase
            )
        }

        // STEP 4: Primary Install Action & 4-Step Progress Tracker
        item {
            Step4FlashActionCard(
                uiState = uiState,
                ecoColor = ecoColor,
                expectedFileName = expectedFileName,
                onStartFlash = onStartFlash,
                onResetFlashState = onResetFlashState,
                onOpenConsole = onOpenConsole
            )
        }

        // Collapsible Advanced Options & PCB Hardware Map
        item {
            FlasherAdvancedToolsSection(
                uiState = uiState,
                ecoColor = ecoColor,
                roleOrTrackBadge = roleOrTrackBadge,
                onSelectBaudRate = onSelectBaudRate,
                onToggleBundleWebUi = onToggleBundleWebUi,
                onToggleLoopbackBench = onToggleLoopbackBench,
                onTrigger1200BpsTouch = onTrigger1200BpsTouch,
                onTriggerEspReset = onTriggerEspReset
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
private fun QuickReadinessBanner(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    onRequestUsbPermission: (UsbAttachedBoard) -> Unit,
    onRescanUsb: () -> Unit,
    onStartBleScanWithPermissions: () -> Unit
) {
    val usbDev = uiState.usbAttachedBoards.firstOrNull()
    val isUsbMode = uiState.selectedTransport == ConnectionTransport.USB_OTG

    val (statusTitle, statusSubtitle, statusColor) = when {
        isUsbMode && usbDev != null && usbDev.hasPermission -> Triple(
            "USB Radio Connected & Ready",
            "Detected ${usbDev.productName} • Ready to install ${uiState.selectedEcosystem.displayName}",
            MeshtasticGreen
        )
        isUsbMode && usbDev != null && !usbDev.hasPermission -> Triple(
            "USB Permission Needed",
            "Tap 'Allow USB' so Mesh Installer can talk to ${usbDev.productName}",
            AmberSignal
        )
        isUsbMode && uiState.loopbackTestBench -> Triple(
            "Demo Mode Active (No Radio Needed)",
            "Will download real firmware & simulate flashing ${uiState.selectedBoard.name}",
            AmberSignal
        )
        isUsbMode -> Triple(
            "Waiting for USB Radio Connection",
            "Plug your ${uiState.selectedBoard.name} into this phone using a USB-C OTG cable",
            TextSecondary
        )
        uiState.selectedBleNode != null -> Triple(
            "Bluetooth Radio Selected (${uiState.selectedBleNode.name})",
            "Ready for wireless update over Bluetooth LE",
            MeshCoreCyan
        )
        else -> Triple(
            "Bluetooth Wireless Mode",
            "Tap 'Scan Bluetooth' to find your nearby radio",
            MeshCoreCyan
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(statusColor.copy(alpha = 0.10f))
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isUsbMode && usbDev != null && !usbDev.hasPermission) {
            Button(
                onClick = { onRequestUsbPermission(usbDev) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberSignal,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Allow USB", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        } else if (isUsbMode && usbDev == null) {
            OutlinedButton(
                onClick = onRescanUsb,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Check USB",
                    modifier = Modifier.size(15.dp),
                    tint = ecoColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Check USB", style = MaterialTheme.typography.labelSmall)
            }
        } else if (!isUsbMode && uiState.selectedBleNode == null) {
            OutlinedButton(
                onClick = onStartBleScanWithPermissions,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Scan BLE", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun OffGridVaultSummaryBanner(
    uiState: MeshInstallerUiState,
    currentOfflineEntry: CachedFirmwareEntry?,
    onOpenOffGridVault: () -> Unit,
    onDownloadAllForSelected: () -> Unit,
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit,
    onSaveCurrentSelectionOffline: () -> Unit
) {
    val savedCount = uiState.offlineVaultEntries.size
    val totalSavedMb = uiState.offlineVaultEntries.sumOf { it.sizeBytes } / (1024f * 1024f)
    val isDownloading = uiState.vaultBatchProgress.isDownloading
    val isCurrentSaved = currentOfflineEntry != null
    val selectedBoards = BoardCatalog.allBoards.filter { it.id in uiState.myDeviceIds }
    val totalPlannedImages = selectedBoards.sumOf { board ->
        if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6
    }
    val boardSavedEntries = uiState.offlineVaultEntries
        .filter { it.boardId == uiState.selectedBoard.id }
        .sortedWith(compareBy<CachedFirmwareEntry> { it.ecosystem != uiState.selectedEcosystem }.thenBy { it.variantLabel })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCard)
            .border(
                width = 1.dp,
                color = if (isCurrentSaved || savedCount > 0) MeshtasticGreen.copy(alpha = 0.55f) else MeshCoreCyan.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (savedCount > 0) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                    contentDescription = "Off-Grid Firmware Vault",
                    tint = if (savedCount > 0) MeshtasticGreen else MeshCoreCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Off-Grid Local Firmware Vault",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        HardwareChipBadge(
                            text = if (isCurrentSaved) "SAVED OFFLINE" else "$savedCount FILES SAVED",
                            color = if (isCurrentSaved) MeshtasticGreen else MeshCoreCyan
                        )
                    }
                    Text(
                        text = if (isDownloading) {
                            uiState.vaultBatchProgress.statusDetail
                        } else if (savedCount > 0) {
                            "$savedCount local firmware file(s) saved on phone (%.1f MB) • Ready for local flashing".format(Locale.US, totalSavedMb)
                        } else {
                            "Check the radios you use & download all Meshtastic + MeshCore firmware for local offline use"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { uiState.vaultBatchProgress.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MeshtasticGreen,
                trackColor = ObsidianBg
            )
        }

        // Direct Action Row: "Check Radios" + "Download & Save Checked Radios"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onOpenOffGridVault,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_offgrid_vault_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperBoard,
                    contentDescription = "Check Radios",
                    modifier = Modifier.size(15.dp),
                    tint = MeshCoreCyan
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Check Radios (${uiState.myDeviceIds.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshCoreCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = if (uiState.myDeviceIds.isNotEmpty()) onDownloadAllForSelected else onOpenOffGridVault,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshtasticGreen,
                    contentColor = Color(0xFF002612)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1.45f)
                    .testTag("banner_download_all_offline_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download & Save Locally",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isDownloading) {
                        "Saving ${uiState.vaultBatchProgress.currentIndex}/${uiState.vaultBatchProgress.totalCount}..."
                    } else if (uiState.myDeviceIds.isNotEmpty()) {
                        "Download & Save ($totalPlannedImages Files)"
                    } else {
                        "Select & Download"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quick-Load Saved Local Firmware Files for Current Board
        if (boardSavedEntries.isNotEmpty()) {
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f))
            Text(
                text = "Saved Local Firmware for ${uiState.selectedBoard.name} (Tap to Use Local File):",
                style = MaterialTheme.typography.labelSmall,
                color = MeshtasticGreen,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                boardSavedEntries.forEach { entry ->
                    val accent = if (entry.ecosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
                    val isCurrentlyLoaded = uiState.customFirmwareFile?.displayName?.startsWith(entry.fileName) == true
                    FilterChip(
                        selected = isCurrentlyLoaded,
                        onClick = { onUseSavedEntry(entry) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isCurrentlyLoaded) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Use Local: ${entry.variantLabel}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.22f),
                            selectedLabelColor = accent
                        ),
                        modifier = Modifier.testTag("banner_use_saved_${entry.key}")
                    )
                }
            }
        }
    }
}

@Composable
private fun Step1BoardSelectorCard(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    onSelectBoard: (HardwareBoard) -> Unit,
    onOpenBoardPicker: () -> Unit,
    onOpenOffGridVault: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onTrigger1200BpsTouch: () -> Unit
) {
    val board = uiState.selectedBoard
    var showBootHelp by rememberSaveable { mutableStateOf(false) }

    val defaultQuickBoardIds = listOf(
        "station_g2",
        "lilygo_tdeck",
        "heltec_v3",
        "rak4631",
        "lilygo_techo",
        "lilygo_tbeam_s3",
        "seeed_t1000e"
    )
    // Put the user's selected "My Devices" first so their field kit is always immediately accessible
    val orderedQuickIds = (uiState.myDeviceIds.toList() + defaultQuickBoardIds).distinct()
    val quickBoards = orderedQuickIds.mapNotNull { id ->
        BoardCatalog.allBoards.firstOrNull { it.id == id }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        StepHeaderRow(
            stepNumber = "1",
            title = "Pick Your Radio Hardware",
            subtitle = "Tap your device below or browse all 16 supported boards",
            accentColor = ecoColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Active Board Highlight Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianBg)
                .border(1.dp, ecoColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .clickable { onOpenBoardPicker() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = board.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HardwareChipBadge(text = board.mcu.shortName, color = ecoColor)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${board.manufacturer} • ${board.flashSizeMb}MB Flash • ${board.loraChip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            OutlinedButton(
                onClick = onOpenBoardPicker,
                modifier = Modifier.testTag("change_board_button"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperBoard,
                    contentDescription = "All Boards",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("All 16 Boards", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick-Select My Radios + Popular Radios
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.myDeviceIds.isNotEmpty()) "My Radios & Popular Boards:" else "Quick Select Popular Radios:",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "Edit My Radios",
                style = MaterialTheme.typography.labelSmall,
                color = ecoColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenOffGridVault() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickBoards.forEach { item ->
                val selected = item.id == board.id
                val isSavedOffline = uiState.offlineVaultEntries.any { it.boardId == item.id }
                val shortLabel = when (item.id) {
                    "station_g2" -> "Station G2"
                    "lilygo_tdeck" -> "T-Deck / Plus"
                    "heltec_v3" -> "Heltec V3"
                    "rak4631" -> "RAK4631"
                    "lilygo_techo" -> "T-Echo"
                    "lilygo_tbeam_s3" -> "T-Beam S3"
                    "seeed_t1000e" -> "Seeed T1000-E"
                    else -> item.name
                }
                FilterChip(
                    selected = selected,
                    onClick = { onSelectBoard(item) },
                    leadingIcon = if (isSavedOffline) {
                        {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Saved Offline",
                                tint = MeshtasticGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    label = { Text(text = shortLabel, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                        selectedLabelColor = ecoColor
                    ),
                    modifier = Modifier.testTag("quick_board_${item.id}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Plain-English Bootloader Helper Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showBootHelp = !showBootHelp }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = AmberSignal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (showBootHelp) {
                    "Hide Bootloader Instructions"
                } else {
                    "How do I put ${board.name} into Flash / Bootloader Mode?"
                },
                style = MaterialTheme.typography.labelMedium,
                color = AmberSignal
            )
        }

        AnimatedVisibility(visible = showBootHelp) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, AmberSignal.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = board.bootloaderGuide,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (board.mcu.shortName.startsWith("ESP32")) {
                        OutlinedButton(
                            onClick = onTriggerEspReset,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = ecoColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Enter Bootloader", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTrigger1200BpsTouch,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AmberSignal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Enter DFU Mode (1200bps)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step2FirmwareSelectorCard(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    expectedFileName: String,
    currentOfflineEntry: CachedFirmwareEntry?,
    onSelectEcosystem: (FirmwareEcosystem) -> Unit,
    onSelectMeshtasticTrack: (MeshtasticTrack) -> Unit,
    onSelectMeshCoreRole: (MeshCoreRole) -> Unit,
    onSetTDeckMeshtasticMui: (Boolean) -> Unit,
    onSelectTDeckMeshCoreFlavor: (TDeckMeshCoreFlavor) -> Unit,
    onOpenReleasesPicker: () -> Unit,
    onSaveCurrentSelectionOffline: () -> Unit,
    onImportCustomFile: () -> Unit,
    onClearCustomFile: () -> Unit
) {
    val isTDeck = uiState.selectedBoard.id == "lilygo_tdeck"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        StepHeaderRow(
            stepNumber = "2",
            title = "Choose Firmware & Options",
            subtitle = "Pick Meshtastic or MeshCore and configure your build",
            accentColor = ecoColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Ecosystem Cards (Meshtastic vs MeshCore)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FirmwareEcosystem.entries.forEach { eco ->
                val isSelected = uiState.selectedEcosystem == eco
                val accent = if (eco == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
                val plainSummary = if (eco == FirmwareEcosystem.MESHTASTIC) {
                    "Off-grid text messaging, GPS maps & shared channels"
                } else if (isTDeck) {
                    "T-Deck standalone Touch UI: Ripple Firmware or WadaMesh"
                } else {
                    "Modular Companion Radios, Repeaters & Room Servers"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.16f) else ObsidianBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent else SlateBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectEcosystem(eco) }
                        .padding(12.dp)
                        .testTag("ecosystem_card_${eco.name.lowercase()}")
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (eco == FirmwareEcosystem.MESHTASTIC) {
                                        Icons.Default.Router
                                    } else {
                                        Icons.Default.SettingsInputComponent
                                    },
                                    contentDescription = eco.displayName,
                                    tint = if (isSelected) accent else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = eco.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = plainSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
            // Release Channel Row
            Text(
                text = "Release Channel:",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeshtasticTrack.entries.forEach { track ->
                    val selected = uiState.selectedMeshtasticTrack == track
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (track == MeshtasticTrack.CUSTOM) onImportCustomFile()
                            else onSelectMeshtasticTrack(track)
                        },
                        label = { Text(track.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                            selectedLabelColor = ecoColor
                        ),
                        modifier = Modifier.testTag("track_chip_${track.name.lowercase()}")
                    )
                }
            }
            Text(
                text = uiState.selectedMeshtasticTrack.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            // T-DECK ONLY: Meshtastic UI (MUI Touch GUI) vs Standard BaseUI Option
            if (isTDeck) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "T-Deck Screen Interface (Meshtastic UI):",
                    style = MaterialTheme.typography.labelMedium,
                    color = ecoColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Meshtastic UI (MUI / t-deck-tft)
                    val isMui = uiState.tDeckMeshtasticMui
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isMui) ecoColor.copy(alpha = 0.16f) else ObsidianBg)
                            .border(
                                width = if (isMui) 2.dp else 1.dp,
                                color = if (isMui) ecoColor else SlateBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSetTDeckMeshtasticMui(true) }
                            .padding(12.dp)
                            .testTag("tdeck_meshtastic_ui_enabled")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Meshtastic UI (MUI)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isMui) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isMui) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ecoColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Full graphical touch UI (t-deck-tft) with chat, maps & node list",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Option 2: Standard BaseUI (t-deck)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isMui) ecoColor.copy(alpha = 0.16f) else ObsidianBg)
                            .border(
                                width = if (!isMui) 2.dp else 1.dp,
                                color = if (!isMui) ecoColor else SlateBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSetTDeckMeshtasticMui(false) }
                            .padding(12.dp)
                            .testTag("tdeck_meshtastic_ui_disabled")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Classic BaseUI",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (!isMui) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!isMui) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ecoColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Standard lightweight screen interface (firmware-t-deck)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            // MESHCORE: If T-Deck is selected, show Ripple Firmware vs WadaMesh selector (T-Deck only!)
            if (isTDeck) {
                Text(
                    text = "Select T-Deck MeshCore Firmware:",
                    style = MaterialTheme.typography.labelMedium,
                    color = ecoColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TDeckMeshCoreFlavor.entries.forEach { flavor ->
                        val selected = uiState.tDeckMeshCoreFlavor == flavor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) ecoColor.copy(alpha = 0.16f) else ObsidianBg)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) ecoColor else SlateBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectTDeckMeshCoreFlavor(flavor) }
                                .padding(12.dp)
                                .testTag("tdeck_meshcore_${flavor.name.lowercase()}")
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = flavor.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ecoColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = flavor.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                // Non-T-Deck boards: standard MeshCore Role selector
                Text(
                    text = "What will this MeshCore radio do?",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MeshCoreRole.entries.forEach { role ->
                        val selected = uiState.selectedMeshCoreRole == role
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (role == MeshCoreRole.CUSTOM) onImportCustomFile()
                                else onSelectMeshCoreRole(role)
                            },
                            label = { Text(role.label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                                selectedLabelColor = ecoColor
                            ),
                            modifier = Modifier.testTag("role_chip_${role.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = uiState.selectedMeshCoreRole.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Version or Custom Local File Box
        val customFile = uiState.customFirmwareFile
        if (customFile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBg)
                    .border(1.dp, AmberSignal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Using Local File: ${customFile.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberSignal,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${customFile.sizeBytes / 1024} KB • Tap X to switch back to official GitHub releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onClearCustomFile,
                    modifier = Modifier.testTag("clear_custom_firmware_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove custom file",
                        tint = TextSecondary
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val activeTag = uiState.selectedRelease?.tagName ?: currentOfflineEntry?.versionTag ?: "Latest"
                val isOfflineForActiveVersion = currentOfflineEntry != null &&
                    (uiState.selectedRelease == null || currentOfflineEntry.versionTag == uiState.selectedRelease.tagName)

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Version: $activeTag",
                            style = MaterialTheme.typography.titleSmall,
                            color = ecoColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isOfflineForActiveVersion && currentOfflineEntry != null) {
                            HardwareChipBadge(
                                text = "SAVED OFFLINE (${currentOfflineEntry.sizeBytes / 1024} KB)",
                                color = MeshtasticGreen
                            )
                        } else {
                            Text(
                                text = "Change Version",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateElevated)
                                    .clickable { onOpenReleasesPicker() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("change_release_version_button")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Target: $expectedFileName",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOfflineForActiveVersion) MeshtasticGreen else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isOfflineForActiveVersion) {
                        OutlinedButton(
                            onClick = onSaveCurrentSelectionOffline,
                            enabled = !uiState.vaultBatchProgress.isDownloading,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_current_offline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Save Offline",
                                tint = MeshtasticGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Offline", style = MaterialTheme.typography.labelSmall, color = MeshtasticGreen)
                        }
                    }

                    OutlinedButton(
                        onClick = onImportCustomFile,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("import_custom_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Load local file",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Local File", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3ConnectionAndModeCard(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    onSelectTransport: (ConnectionTransport) -> Unit,
    onRescanUsb: () -> Unit,
    onRequestUsbPermission: (UsbAttachedBoard) -> Unit,
    onStartBleScanWithPermissions: () -> Unit,
    onSelectBleNode: (BleDiscoveredNode) -> Unit,
    onToggleFullErase: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        StepHeaderRow(
            stepNumber = "3",
            title = "Connection & Install Type",
            subtitle = "Choose USB or Bluetooth, and whether to keep your settings",
            accentColor = ecoColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // USB vs Bluetooth Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConnectionTransport.entries.forEach { transport ->
                val isSelected = uiState.selectedTransport == transport
                val enabled = transport == ConnectionTransport.USB_OTG || uiState.selectedBoard.mcu.supportsBleDfu
                val label = if (transport == ConnectionTransport.USB_OTG) "USB Cable" else "Bluetooth (BLE)"
                val sub = if (transport == ConnectionTransport.USB_OTG) {
                    "Fastest • Works on all boards"
                } else if (enabled) {
                    "Wireless OTA update"
                } else {
                    "USB only on RP2040"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ecoColor.copy(alpha = 0.16f) else ObsidianBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ecoColor else SlateBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = enabled) { onSelectTransport(transport) }
                        .padding(12.dp)
                        .testTag("transport_tab_${transport.name.lowercase()}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (transport == ConnectionTransport.USB_OTG) {
                                Icons.Default.Usb
                            } else {
                                Icons.Default.Bluetooth
                            },
                            contentDescription = label,
                            tint = if (isSelected) ecoColor else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active USB or Bluetooth Device Box
        if (uiState.selectedTransport == ConnectionTransport.USB_OTG) {
            if (uiState.usbAttachedBoards.isNotEmpty()) {
                uiState.usbAttachedBoards.forEach { usbDev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBg)
                            .border(1.dp, MeshtasticGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected: ${usbDev.productName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MeshtasticGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${usbDev.manufacturerName} (USB-C Serial Ready)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (!usbDev.hasPermission) {
                            Button(
                                onClick = { onRequestUsbPermission(usbDev) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberSignal),
                                modifier = Modifier.testTag("grant_usb_permission_button")
                            ) {
                                Text("Allow USB", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            HardwareChipBadge(text = "READY", color = MeshtasticGreen)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.loopbackTestBench) {
                                "Demo Mode Enabled (No USB Cable Needed)"
                            } else {
                                "No USB Radio Detected Yet"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (uiState.loopbackTestBench) AmberSignal else TextPrimary
                        )
                        Text(
                            text = "Plug your radio into your phone with a USB-C data cable, then tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRescanUsb,
                        modifier = Modifier.testTag("rescan_usb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh USB",
                            tint = ecoColor
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Bluetooth Radios (${uiState.bleDiscoveredNodes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                OutlinedButton(
                    onClick = onStartBleScanWithPermissions,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("scan_ble_nodes_button")
                ) {
                    if (uiState.isBleScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ecoColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = "Scan BLE",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isBleScanning) "Scanning…" else "Scan Bluetooth",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (uiState.bleDiscoveredNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Tap 'Scan Bluetooth' to find nearby radios ready for wireless update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.bleDiscoveredNodes.forEach { node ->
                        val isSelected = uiState.selectedBleNode?.address == node.address
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SlateElevated else ObsidianBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ecoColor else SlateBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectBleNode(node) }
                                .padding(10.dp)
                                .testTag("ble_node_${node.address}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${node.address} • ${node.matchedBoard?.name ?: "BLE Radio"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            HardwareChipBadge(
                                text = "${node.rssi} dBm",
                                color = if (node.rssi > -65) MeshtasticGreen else AmberSignal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = SlateBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // Plain-English Install Mode Selector (Update vs Fresh Erase)
        Text(
            text = "How should we install the firmware?",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Option A: Update (Keep Settings)
            val isUpdate = !uiState.fullFlashErase
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isUpdate) ecoColor.copy(alpha = 0.16f) else ObsidianBg)
                    .border(
                        width = if (isUpdate) 2.dp else 1.dp,
                        color = if (isUpdate) ecoColor else SlateBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleFullErase(false) }
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update Only",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isUpdate) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        if (isUpdate) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ecoColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keeps your channels, keys & node name",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Option B: Fresh Factory Install (Full Erase)
            val isFullErase = uiState.fullFlashErase
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFullErase) AmberSignal.copy(alpha = 0.16f) else ObsidianBg)
                    .border(
                        width = if (isFullErase) 2.dp else 1.dp,
                        color = if (isFullErase) AmberSignal else SlateBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleFullErase(true) }
                    .padding(12.dp)
                    .testTag("full_erase_switch")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fresh Install",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isFullErase) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        if (isFullErase) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AmberSignal,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wipes all settings (Best when switching OS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun Step4FlashActionCard(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    expectedFileName: String,
    onStartFlash: () -> Unit,
    onResetFlashState: () -> Unit,
    onOpenConsole: () -> Unit
) {
    val fp = uiState.flashProgress
    val isBusy = fp.phase != FlashPhase.IDLE &&
        fp.phase != FlashPhase.COMPLETED &&
        fp.phase != FlashPhase.FAILED

    val currentStageIndex = when (fp.phase) {
        FlashPhase.IDLE -> 0
        FlashPhase.PREPARING_BINARY -> 1
        FlashPhase.ENTERING_BOOTLOADER, FlashPhase.ERASING_SECTORS -> 2
        FlashPhase.WRITING_PARTITIONS, FlashPhase.VERIFYING_CHECKSUM -> 3
        FlashPhase.COMPLETED -> 4
        FlashPhase.FAILED -> -1
    }

    val installTargetTitle = when {
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC ->
            if (uiState.tDeckMeshtasticMui) "Meshtastic UI (MUI)" else "Meshtastic BaseUI"
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHCORE ->
            uiState.tDeckMeshCoreFlavor.label
        else -> uiState.selectedEcosystem.displayName
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SlateElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.8.dp,
            color = when (fp.phase) {
                FlashPhase.COMPLETED -> MeshtasticGreen
                FlashPhase.FAILED -> CoralError
                else -> ecoColor.copy(alpha = 0.8f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = when (fp.phase) {
                            FlashPhase.COMPLETED -> Icons.Default.CheckCircle
                            FlashPhase.FAILED -> Icons.Default.ErrorOutline
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = fp.phase.title,
                        tint = when (fp.phase) {
                            FlashPhase.COMPLETED -> MeshtasticGreen
                            FlashPhase.FAILED -> CoralError
                            else -> ecoColor
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (fp.phase == FlashPhase.IDLE) {
                                "Ready to Install $installTargetTitle"
                            } else {
                                fp.phase.title
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.selectedBoard.name} • ${
                                if (uiState.fullFlashErase) "Fresh Factory Install" else "Update (Keep Settings)"
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Text(
                    text = "View Logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = ecoColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianBg)
                        .clickable { onOpenConsole() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Stage Visual Checklist Bar (1. Download -> 2. Connect -> 3. Write -> 4. Done)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val stages = listOf(
                    1 to "1. Download",
                    2 to "2. Connect",
                    3 to "3. Write",
                    4 to "4. Done"
                )
                stages.forEach { (idx, label) ->
                    val isDone = currentStageIndex > idx || fp.phase == FlashPhase.COMPLETED
                    val isCurrent = currentStageIndex == idx
                    val pillColor = when {
                        isDone -> MeshtasticGreen
                        isCurrent -> ecoColor
                        else -> SlateBorder
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDone || isCurrent) pillColor.copy(alpha = 0.18f) else ObsidianBg
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDone || isCurrent) pillColor else SlateBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDone || isCurrent) TextPrimary else TextMuted,
                            fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Progress Bar & Live Status when active/completed/failed
            AnimatedVisibility(visible = fp.phase != FlashPhase.IDLE) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { fp.overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (fp.phase == FlashPhase.FAILED) CoralError else ecoColor,
                        trackColor = ObsidianBg
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = fp.currentPartitionName.ifEmpty { expectedFileName },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(fp.overallProgress * 100).toInt()}% • ${"%.1f".format(fp.speedKbps)} KB/s",
                            style = MaterialTheme.typography.labelMedium,
                            color = ecoColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (fp.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CoralError.copy(alpha = 0.12f))
                                .border(1.dp, CoralError.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = fp.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralError,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onOpenConsole,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Details", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartFlash,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ecoColor,
                        contentColor = Color(0xFF002211)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("start_flash_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF002211)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Installing ${(fp.overallProgress * 100).toInt()}%…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Install Firmware"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Install $installTargetTitle Now",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (fp.phase == FlashPhase.COMPLETED || fp.phase == FlashPhase.FAILED) {
                    OutlinedButton(
                        onClick = onResetFlashState,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("reset_flash_state_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeaderRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(1.5.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
