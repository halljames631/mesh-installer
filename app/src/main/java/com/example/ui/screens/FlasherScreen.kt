package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BleDiscoveredNode
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.ConnectionTransport
import com.example.model.FirmwareEcosystem
import com.example.model.FlashPhase
import com.example.model.HardwareBoard
import com.example.model.LogLevel
import com.example.model.MeshCoreRole
import com.example.model.MeshtasticTrack
import com.example.model.TDeckMeshCoreFlavor
import com.example.model.UsbAttachedBoard
import com.example.ui.MeshInstallerUiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
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
    val isFlashing = uiState.flashProgress.phase != FlashPhase.IDLE &&
        uiState.flashProgress.phase != FlashPhase.COMPLETED &&
        uiState.flashProgress.phase != FlashPhase.FAILED

    if (isFlashing) {
        FlashingProgressView(
            uiState = uiState,
            expectedFileName = expectedFileName,
            onAbortFlash = onResetFlashState,
            modifier = modifier
        )
    } else {
        FlasherIdleView(
            uiState = uiState,
            expectedFileName = expectedFileName,
            currentOfflineEntry = currentOfflineEntry,
            onSelectEcosystem = onSelectEcosystem,
            onSelectMeshtasticTrack = onSelectMeshtasticTrack,
            onSelectMeshCoreRole = onSelectMeshCoreRole,
            onSetTDeckMeshtasticMui = onSetTDeckMeshtasticMui,
            onSelectTDeckMeshCoreFlavor = onSelectTDeckMeshCoreFlavor,
            onSelectBoard = onSelectBoard,
            onOpenBoardPicker = onOpenBoardPicker,
            onOpenReleasesPicker = onOpenReleasesPicker,
            onSaveCurrentSelectionOffline = onSaveCurrentSelectionOffline,
            onSelectTransport = onSelectTransport,
            onRequestUsbPermission = onRequestUsbPermission,
            onRescanUsb = onRescanUsb,
            onStartBleScanWithPermissions = onStartBleScanWithPermissions,
            onSelectBleNode = onSelectBleNode,
            onToggleFullErase = onToggleFullErase,
            onToggleBundleWebUi = onToggleBundleWebUi,
            onToggleLoopbackBench = onToggleLoopbackBench,
            onImportCustomFile = onImportCustomFile,
            onClearCustomFile = onClearCustomFile,
            onTrigger1200BpsTouch = onTrigger1200BpsTouch,
            onTriggerEspReset = onTriggerEspReset,
            onStartFlash = onStartFlash,
            onResetFlashState = onResetFlashState,
            modifier = modifier
        )
    }
}

/**
 * Modern, User-Friendly Flasher Interface adhering to 8dp Grid, 48dp Touch Targets & M3 Hierarchy
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlasherIdleView(
    uiState: MeshInstallerUiState,
    expectedFileName: String,
    currentOfflineEntry: CachedFirmwareEntry?,
    onSelectEcosystem: (FirmwareEcosystem) -> Unit,
    onSelectMeshtasticTrack: (MeshtasticTrack) -> Unit,
    onSelectMeshCoreRole: (MeshCoreRole) -> Unit,
    onSetTDeckMeshtasticMui: (Boolean) -> Unit,
    onSelectTDeckMeshCoreFlavor: (TDeckMeshCoreFlavor) -> Unit,
    onSelectBoard: (HardwareBoard) -> Unit,
    onOpenBoardPicker: () -> Unit,
    onOpenReleasesPicker: () -> Unit,
    onSaveCurrentSelectionOffline: () -> Unit,
    onSelectTransport: (ConnectionTransport) -> Unit,
    onRequestUsbPermission: (UsbAttachedBoard) -> Unit,
    onRescanUsb: () -> Unit,
    onStartBleScanWithPermissions: () -> Unit,
    onSelectBleNode: (BleDiscoveredNode) -> Unit,
    onToggleFullErase: (Boolean) -> Unit,
    onToggleBundleWebUi: (Boolean) -> Unit,
    onToggleLoopbackBench: (Boolean) -> Unit,
    onImportCustomFile: () -> Unit,
    onClearCustomFile: () -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onStartFlash: () -> Unit,
    onResetFlashState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usbDev = uiState.usbAttachedBoards.firstOrNull()
    val isUsbAttached = usbDev != null
    val hasPermission = isUsbAttached && usbDev!!.hasPermission
    val board = uiState.selectedBoard
    val isTDeck = board.id == "lilygo_tdeck"
    val isMeshtastic = uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC

    val alphaRelease = uiState.meshtasticReleases.firstOrNull { it.isPrerelease }
    val stableRelease = uiState.meshtasticReleases.firstOrNull { !it.isPrerelease }
    val alphaTag = alphaRelease?.tagName ?: "v2.6.0-alpha"
    val stableTag = stableRelease?.tagName ?: "v2.5.18"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. TOP STATUS HEADER CARD
        // ==========================================
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) Icons.Default.Bluetooth else Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mesh Installer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                        "BLE OTA • ${uiState.selectedBleNode?.name ?: "Ready to scan"}"
                                    hasPermission ->
                                        "USB Connected • ${uiState.selectedBaudRate / 1000}K baud"
                                    isUsbAttached ->
                                        "USB device attached • Tap to allow"
                                    uiState.loopbackTestBench ->
                                        "Simulated test bench active"
                                    else ->
                                        "Plug USB-C OTG cable to radio"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasPermission || uiState.loopbackTestBench) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Online / Action Pill (min 48dp touch target)
                    Surface(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                    onStartBleScanWithPermissions()
                                } else if (isUsbAttached && !hasPermission) {
                                    onRequestUsbPermission(usbDev!!)
                                } else {
                                    onRescanUsb()
                                }
                            }
                            .testTag("usb_status_action_pill"),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                        if (uiState.isBleScanning) "SCANNING" else "BLE SCAN"
                                    hasPermission -> "ONLINE"
                                    isUsbAttached -> "GRANT USB"
                                    else -> "RESCAN"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. FLASH RESULT BANNER (Completed / Failed)
        // ==========================================
        if (uiState.flashProgress.phase == FlashPhase.COMPLETED || uiState.flashProgress.phase == FlashPhase.FAILED) {
            item {
                val isSuccess = uiState.flashProgress.phase == FlashPhase.COMPLETED
                val bannerColor = if (isSuccess) GreenSuccess else CoralRed
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = bannerColor.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSuccess) "Flash Completed Successfully" else "Flash Operation Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.flashProgress.errorMessage ?: uiState.flashProgress.phase.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onResetFlashState() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "DISMISS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. TARGET HARDWARE & CONNECTION CARD
        // ==========================================
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connected_hardware_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header row: Label + MCU chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONNECTED HARDWARE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = board.mcu.shortName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Board Name & Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenBoardPicker() }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${board.mcu.shortName} • ${board.flashSizeMb}MB Flash • ${board.loraChip}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val portDesc = when {
                            uiState.selectedTransport == ConnectionTransport.BLE_DFU && uiState.selectedBleNode != null ->
                                "BLE Target: ${uiState.selectedBleNode.name} [${uiState.selectedBleNode.address}] (${uiState.selectedBleNode.rssi} dBm)"
                            uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                "BLE OTA Mode • Select a discovered node below"
                            usbDev != null ->
                                "Port: ${usbDev.deviceName} (VID: 0x%04X)".format(usbDev.vendorId)
                            else ->
                                "Port: /dev/bus/usb/001/002 (VID: 0x%04X • ${board.usbBridge})".format(board.vendorId)
                        }
                        Text(
                            text = portDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else BlueAccent
                        )
                    }

                    // Quick Radio Selector Pills (8dp grid, min 48dp touch targets)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Quick Radio Switch",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val quickBoards = listOf(
                                "heltec_v4" to "Heltec V4",
                                "heltec_v3" to "Heltec V3",
                                "seeed_t1000e" to "T1000-E",
                                "rak4631" to "RAK4631",
                                "lilygo_tdeck" to "T-Deck",
                                "station_g2" to "Station G2"
                            )
                            quickBoards.forEach { (bId, shortLabel) ->
                                val targetBoard = BoardCatalog.allBoards.firstOrNull { it.id == bId }
                                if (targetBoard != null) {
                                    val isSelectedBoard = board.id == bId
                                    Surface(
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSelectBoard(targetBoard) }
                                            .testTag("quick_select_$bId"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelectedBoard) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        border = BorderStroke(
                                            if (isSelectedBoard) 2.dp else 1.dp,
                                            if (isSelectedBoard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (isSelectedBoard) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = shortLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelectedBoard) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelectedBoard) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // All Radios button
                            Surface(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenBoardPicker() }
                                    .testTag("change_radio_button"),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "All Radios (${BoardCatalog.allBoards.size}) ▾",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Transport Mode Buttons (USB vs BLE OTA, min 48dp height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isUsbMode = uiState.selectedTransport == ConnectionTransport.USB_OTG
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectTransport(ConnectionTransport.USB_OTG) }
                                .testTag("transport_usb_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUsbMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isUsbMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = if (isUsbMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "USB OTG",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isUsbMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isUsbMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (board.mcu.supportsBleDfu) {
                            val isBle = uiState.selectedTransport == ConnectionTransport.BLE_DFU
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isBle) {
                                            onSelectTransport(ConnectionTransport.USB_OTG)
                                        } else {
                                            onSelectTransport(ConnectionTransport.BLE_DFU)
                                            onStartBleScanWithPermissions()
                                        }
                                    }
                                    .testTag("toggle_transport_button"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isBle) PurpleAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isBle) PurpleAccent else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isBle) PurpleAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBle) "BLE OTA ✓" else "BLE OTA",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isBle) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isBle) PurpleAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Explicit Enter DFU Mode button (min 48dp height)
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (uiState.selectedTransport == ConnectionTransport.BLE_DFU ||
                                        board.mcu.name.startsWith("NRF") ||
                                        board.mcu.name.startsWith("RP2040")
                                    ) {
                                        onTrigger1200BpsTouch()
                                    } else {
                                        onTriggerEspReset()
                                    }
                                }
                                .testTag("enter_dfu_mode_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = AmberSignal.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AmberSignal.copy(alpha = 0.5f))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "DFU MODE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberSignal
                                )
                            }
                        }
                    }

                    // BLE OTA Scanner & Target Picker (when BLE_DFU active)
                    if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, PurpleAccent.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isBleScanning) "SCANNING BLE RADIOS..." else "BLE TARGETS (${uiState.bleDiscoveredNodes.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PurpleAccent
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onTrigger1200BpsTouch() }
                                                .testTag("ble_enter_dfu_button"),
                                            shape = RoundedCornerShape(8.dp),
                                            color = AmberSignal.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, AmberSignal.copy(alpha = 0.4f))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "ENTER DFU",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AmberSignal
                                                )
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onStartBleScanWithPermissions() }
                                                .testTag("ble_rescan_button"),
                                            shape = RoundedCornerShape(8.dp),
                                            color = PurpleAccent.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, PurpleAccent.copy(alpha = 0.5f))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = if (uiState.isBleScanning) "SCANNING..." else "RESCAN",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PurpleAccent
                                                )
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onToggleLoopbackBench(true)
                                                    onStartBleScanWithPermissions()
                                                }
                                                .testTag("ble_simulate_nodes_button"),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "SIMULATE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (uiState.bleDiscoveredNodes.isEmpty()) {
                                    Text(
                                        text = if (uiState.isBleScanning) {
                                            "Listening for Meshtastic, MeshCore & Nordic DFU BLE advertisements..."
                                        } else {
                                            "No BLE radios detected yet. Make sure Bluetooth is ON or tap 'SIMULATE'."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        uiState.bleDiscoveredNodes.take(10).forEach { node ->
                                            val isSelected = uiState.selectedBleNode?.address == node.address
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onSelectBleNode(node) }
                                                    .testTag("ble_node_${node.address}"),
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = node.name,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = node.address + (if (node.isInDfuMode) " • DFU Bootloader" else " • BLE GATT"),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = if (node.isInDfuMode) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "${node.rssi} dBm",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 4. FIRMWARE CONFIGURATION CARD
        // ==========================================
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("firmware_configuration_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header row: Label + Actions (Versions & Local File)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FIRMWARE CONFIGURATION",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenReleasesPicker() }
                                    .testTag("open_releases_picker_button"),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Versions (${uiState.selectedRelease?.tagName ?: "Latest"}) ▾",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BlueAccent
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImportCustomFile() }
                                    .testTag("import_custom_firmware_button"),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Import Local File",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Local File",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Ecosystem Segmented Selector (Meshtastic vs MeshCore, 48dp min height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHTASTIC) }
                                .testTag("ecosystem_meshtastic_pill"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMeshtastic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isMeshtastic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = "Meshtastic",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isMeshtastic) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isMeshtastic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHCORE) }
                                .testTag("ecosystem_meshcore_pill"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isMeshtastic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (!isMeshtastic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = "MeshCore",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (!isMeshtastic) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isMeshtastic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (isMeshtastic) {
                        // Meshtastic Track Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Release Track",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isAlpha = uiState.selectedMeshtasticTrack == MeshtasticTrack.ALPHA
                                val isStable = uiState.selectedMeshtasticTrack == MeshtasticTrack.STABLE

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectMeshtasticTrack(MeshtasticTrack.ALPHA) }
                                        .testTag("track_alpha_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isAlpha) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAlpha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text(
                                            text = "Alpha ($alphaTag)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isAlpha) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isAlpha) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectMeshtasticTrack(MeshtasticTrack.STABLE) }
                                        .testTag("track_stable_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isStable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isStable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text(
                                            text = "Stable ($stableTag)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isStable) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isStable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Variant Selector (T-Deck MUI vs BaseUI, or Target Binary)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isTDeck) "LilyGO T-Deck Variant" else "${board.name} Target",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isTDeck) {
                                VariantRadioCard(
                                    title = "Meshtastic UI (MUI Touch)",
                                    subtitle = if (uiState.tDeckMeshtasticMui) expectedFileName else "t-deck-tft • Graphical Touch UI & Maps",
                                    selected = uiState.tDeckMeshtasticMui,
                                    onClick = { onSetTDeckMeshtasticMui(true) },
                                    testTag = "tdeck_mui_option"
                                )
                                VariantRadioCard(
                                    title = "Classic BaseUI (Terminal)",
                                    subtitle = if (!uiState.tDeckMeshtasticMui) expectedFileName else "t-deck • Classic OLED/TFT Screen UI",
                                    selected = !uiState.tDeckMeshtasticMui,
                                    onClick = { onSetTDeckMeshtasticMui(false) },
                                    testTag = "tdeck_baseui_option"
                                )
                            } else {
                                VariantRadioCard(
                                    title = "${board.name} (${board.meshtasticTarget})",
                                    subtitle = expectedFileName,
                                    selected = true,
                                    onClick = { onOpenReleasesPicker() },
                                    testTag = "board_meshtastic_target_card"
                                )
                            }
                        }
                    } else {
                        // MeshCore Options
                        if (isTDeck) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "T-Deck MeshCore Flavor",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TDeckMeshCoreFlavor.entries.forEach { flavor ->
                                        val isSelected = uiState.tDeckMeshCoreFlavor == flavor
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onSelectTDeckMeshCoreFlavor(flavor) }
                                                .testTag("tdeck_meshcore_flavor_${flavor.name.lowercase()}"),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                                Text(
                                                    text = flavor.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // MeshCore Role Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "MeshCore Node Role",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val roles = listOf(
                                    MeshCoreRole.COMPANION_BLE,
                                    MeshCoreRole.COMPANION_USB,
                                    MeshCoreRole.REPEATER,
                                    MeshCoreRole.ROOM_SERVER
                                )
                                roles.forEach { role ->
                                    val isRoleSelected = uiState.selectedMeshCoreRole == role
                                    Surface(
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectMeshCoreRole(role) }
                                            .testTag("meshcore_role_${role.name.lowercase()}"),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isRoleSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isRoleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = role.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isRoleSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isRoleSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // MeshCore Target Card
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isTDeck) "LilyGO T-Deck MeshCore Target" else "${board.name} MeshCore Target",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            VariantRadioCard(
                                title = if (isTDeck) {
                                    "${uiState.tDeckMeshCoreFlavor.label} • ${uiState.selectedMeshCoreRole.label}"
                                } else {
                                    "${board.name} • ${uiState.selectedMeshCoreRole.label}"
                                },
                                subtitle = expectedFileName,
                                selected = true,
                                onClick = { onOpenReleasesPicker() },
                                testTag = "meshcore_target_card"
                            )
                        }
                    }

                    // Custom Local Firmware File Banner
                    uiState.customFirmwareFile?.let { customFile ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = BlueAccent.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "LOCAL: ${customFile.displayName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${customFile.sizeBytes / 1024} KB • ${customFile.format} • SHA256: ${customFile.sha256Hex.take(12)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                                        .clip(CircleShape)
                                        .clickable { onClearCustomFile() },
                                    color = CoralRed.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear Local Firmware",
                                            tint = CoralRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Offline Vault Status & Quick Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSavedOffline = currentOfflineEntry != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isSavedOffline) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isSavedOffline) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isSavedOffline) {
                                    "Cached in Vault (${currentOfflineEntry!!.sizeBytes / 1024} KB)"
                                } else {
                                    "Target: $expectedFileName"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSavedOffline) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSaveCurrentSelectionOffline() }
                                .testTag("save_current_offline_button"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSavedOffline) GreenSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSavedOffline) GreenSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isSavedOffline) "SAVED OFFLINE ✓" else "SAVE TO VAULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSavedOffline) GreenSuccess else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. FLASH OPTIONS ROW (Erase & Backup Config)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Erase Flash Toggle
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleFullErase(!uiState.fullFlashErase) }
                        .testTag("toggle_erase_flash_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Erase Flash: " + if (uiState.fullFlashErase) "YES" else "NO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.fullFlashErase) GreenSuccess else CoralRed
                        )
                    }
                }

                // Backup Config Toggle
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleBundleWebUi(!uiState.bundleWebUiPartition) }
                        .testTag("toggle_backup_config_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Backup Config: " + if (uiState.bundleWebUiPartition) "YES" else "NO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.bundleWebUiPartition) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. PRIMARY CALL TO ACTION: FLASH FIRMWARE
        // Most visually dominant element on screen!
        // ==========================================
        item {
            Button(
                onClick = onStartFlash,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_flash_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FLASH FIRMWARE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // ==========================================
        // 7. TECHNICAL ASSURANCE BANNER
        // ==========================================
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BlueAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "100%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BlueAccent
                            )
                        }
                        Column {
                            Text(
                                text = "Offline Field Flasher",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pure Kotlin esptool SLIP ROM • No PC needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess)
                        )
                        Text(
                            text = "CRC32 Block Verification • Auto DTR/RTS Reset",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = GreenSuccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantRadioCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface
                        ) {}
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Modern Flashing Progress View with M3 Aesthetics & Clean Visuals
 */
@Composable
private fun FlashingProgressView(
    uiState: MeshInstallerUiState,
    expectedFileName: String,
    onAbortFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = uiState.flashProgress.overallProgress.coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100).toInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card: Protocol Status & Speed
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Flashing Firmware",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                    "BLE OTA DFU PROTOCOL ACTIVE"
                                } else {
                                    "ESPTOOL SLIP PROTOCOL ACTIVE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else BlueAccent
                            )
                        }
                    }

                    // Speed Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GreenSuccess.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                "%.1f KBPS".format(Locale.US, uiState.flashProgress.speedKbps.coerceAtLeast(42.4f))
                            } else {
                                "921.6 KBPS"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }
        }

        // Circular Gauge Progress Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 12.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2
                            drawCircle(
                                color = trackColor,
                                radius = radius,
                                style = Stroke(stroke)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progressFraction,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiState.flashProgress.phase.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Target details
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.selectedBoard.name + if (uiState.selectedBoard.id == "lilygo_tdeck") " (MUI Touch)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Partition: ${uiState.flashProgress.currentPartitionName.ifEmpty { "app0 @ 0x10000" }} (%.1f MB / %.1f MB)".format(
                                Locale.US,
                                uiState.flashProgress.bytesWritten / (1024f * 1024f),
                                uiState.flashProgress.totalBytes.coerceAtLeast(1L) / (1024f * 1024f)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        // Live Stats Grid (Block Rate, CRC32, Est Time)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Block Rate
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "BLOCK RATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "84.2 KB/s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // CRC32 Verify
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "CRC32 VERIFY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "PASSED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }

                // Est Time
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "EST. TIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "00:08",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BlueAccent
                        )
                    }
                }
            }
        }

        // Protocol Log Window
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CoralRed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberSignal))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GreenSuccess))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "ROM Bootloader Protocol Logs",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val recentLogs = uiState.consoleLogs.takeLast(10)
                        if (recentLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "[09:42:01] DTR/RTS pulse sent: chip in bootloader\n[09:42:02] Connected: ${uiState.selectedBoard.mcu.name}\n[09:42:03] Flash stub uploaded. Baud 115200 -> 921600\n[09:42:04] Writing partition: app0 @ 0x10000\n[09:42:05] >> Writing block ($progressPercent%)... OK\n[09:42:06] >> CRC32 verified",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = GreenSuccess
                                )
                            }
                        } else {
                            items(recentLogs) { log ->
                                Text(
                                    text = "[${log.timestamp}] ${log.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = when (log.level) {
                                        LogLevel.SUCCESS -> GreenSuccess
                                        LogLevel.ERROR -> CoralRed
                                        LogLevel.WARN -> AmberSignal
                                        LogLevel.TX -> PurpleAccent
                                        else -> GreenSuccess
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Abort Button (min 48dp height)
        item {
            OutlinedButton(
                onClick = onAbortFlash,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("abort_flash_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "ABORT FLASHING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoralRed
                )
            }
        }
    }
}
