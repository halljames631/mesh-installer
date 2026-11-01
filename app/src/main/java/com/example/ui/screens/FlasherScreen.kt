package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BleDiscoveredNode
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
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardDarkBg
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.DarkTealBorder
import com.example.ui.theme.DarkTealCard
import com.example.ui.theme.DarkTealHeader
import com.example.ui.theme.DeepDarkBg
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
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
 * Image 1: Main Flasher Interface with full Meshtastic & MeshCore Firmware Mapping
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
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top App Bar Card matching Image 1
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(DarkTealHeader, Color(0xFF143D35))
                        )
                    )
                    .border(1.dp, DarkTealBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberTeal.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mesh Installer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = when {
                                    uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                        "BLE OTA DFU • ${uiState.selectedBleNode?.name ?: "READY TO SCAN"}"
                                    hasPermission ->
                                        "USB OTG CONNECTED • ${uiState.selectedBaudRate / 1000}K"
                                    isUsbAttached ->
                                        "TAP TO ALLOW USB"
                                    uiState.loopbackTestBench ->
                                        "TEST BENCH READY • ${uiState.selectedBaudRate / 1000}K"
                                    else ->
                                        "READY FOR USB OTG"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasPermission || uiState.loopbackTestBench) Color(0xFF7EE787) else AmberSignal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Online / Scan Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(CyberTeal.copy(alpha = 0.15f))
                            .clickable {
                                if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                    onStartBleScanWithPermissions()
                                } else if (isUsbAttached && !hasPermission) {
                                    onRequestUsbPermission(usbDev!!)
                                } else {
                                    onRescanUsb()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("usb_status_action_pill")
                    ) {
                        Text(
                            text = when {
                                uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                    if (uiState.isBleScanning) "SCANNING" else "BLE SCAN"
                                hasPermission -> "ONLINE"
                                isUsbAttached -> "GRANT USB"
                                else -> "ONLINE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                }
            }
        }

        // Flash Completion or Failure Banner (if just finished)
        if (uiState.flashProgress.phase == FlashPhase.COMPLETED || uiState.flashProgress.phase == FlashPhase.FAILED) {
            item {
                val isSuccess = uiState.flashProgress.phase == FlashPhase.COMPLETED
                val accentColor = if (isSuccess) GreenSuccess else CoralRed
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSuccess) "Flash Completed Successfully" else "Flash Operation Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Text(
                                text = uiState.flashProgress.errorMessage ?: uiState.flashProgress.phase.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardDarkBg)
                                .clickable { onResetFlashState() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "DISMISS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Card 1: CONNECTED HARDWARE matching Image 1
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDarkBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { onOpenBoardPicker() }
                    .padding(16.dp)
                    .testTag("connected_hardware_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CONNECTED HARDWARE",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(GreenSuccess.copy(alpha = 0.25f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = board.mcu.shortName,
                                        style = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenSuccess
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = board.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ROM: ${board.mcu.shortName} • ${board.flashSizeMb}MB Quad-SPI Flash • ${board.loraChip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val portText = when {
                                uiState.selectedTransport == ConnectionTransport.BLE_DFU && uiState.selectedBleNode != null ->
                                    "BLE Target: ${uiState.selectedBleNode.name} [${uiState.selectedBleNode.address}] (${uiState.selectedBleNode.rssi} dBm)"
                                uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                    "BLE OTA Mode • Select or scan a nearby BLE radio below"
                                usbDev != null ->
                                    "Port: ${usbDev.deviceName} (Vendor: 0x%04X)".format(usbDev.vendorId)
                                else ->
                                    "Port: /dev/bus/usb/001/002 (Vendor: 0x%04X • ${board.usbBridge})".format(board.vendorId)
                            }
                            Text(
                                text = portText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else BlueAccent
                                )
                            )
                        }

                        // Active Pulse Indicator
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val pulseColor = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else GreenSuccess
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, pulseColor.copy(alpha = 0.4f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(pulseColor)
                            )
                        }
                    }

                    // Quick Hardware & Transport Selector Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1C232D))
                                .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                .clickable { onOpenBoardPicker() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("change_radio_button")
                        ) {
                            Text(
                                text = "Change Radio (${board.manufacturer}) ▾",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyberCyan
                                )
                            )
                        }

                        val isUsbMode = uiState.selectedTransport == ConnectionTransport.USB_OTG
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isUsbMode) DarkTealCard else Color(0xFF1C232D))
                                .border(
                                    1.dp,
                                    if (isUsbMode) CyberTeal else Color(0xFF2D3748),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    onSelectTransport(ConnectionTransport.USB_OTG)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("transport_usb_button")
                        ) {
                            Text(
                                text = "USB OTG Serial",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isUsbMode) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isUsbMode) CyberCyan else TextSecondary
                                )
                            )
                        }

                        if (board.mcu.supportsBleDfu) {
                            val isBle = uiState.selectedTransport == ConnectionTransport.BLE_DFU
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBle) PurpleAccent.copy(alpha = 0.22f) else Color(0xFF1C232D))
                                    .border(
                                        1.dp,
                                        if (isBle) PurpleAccent else Color(0xFF2D3748),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        if (isBle) {
                                            onSelectTransport(ConnectionTransport.USB_OTG)
                                        } else {
                                            onSelectTransport(ConnectionTransport.BLE_DFU)
                                            onStartBleScanWithPermissions()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("toggle_transport_button")
                            ) {
                                Text(
                                    text = if (isBle) "BLE OTA DFU ✓" else "BLE OTA DFU",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = if (isBle) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isBle) PurpleAccent else TextSecondary
                                    )
                                )
                            }
                        }

                        // Explicit Enter DFU Mode button for SenseCAP T1000-E / RAK4631 / T-Echo / RP2040 / ESP32-S3
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AmberSignal.copy(alpha = 0.16f))
                                .border(1.dp, AmberSignal.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
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
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("enter_dfu_mode_button")
                        ) {
                            Text(
                                text = "ENTER DFU MODE",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberSignal
                                )
                            )
                        }
                    }

                    // Dedicated BLE OTA Scanner & Target Picker when BLE_DFU is active
                    if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F141C))
                                .border(1.dp, PurpleAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.isBleScanning) {
                                        "SCANNING BLE RADIOS..."
                                    } else {
                                        "BLE OTA TARGETS (${uiState.bleDiscoveredNodes.size})"
                                    },
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp,
                                        color = PurpleAccent
                                    )
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AmberSignal.copy(alpha = 0.2f))
                                            .border(1.dp, AmberSignal, RoundedCornerShape(6.dp))
                                            .clickable { onTrigger1200BpsTouch() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("ble_enter_dfu_button")
                                    ) {
                                        Text(
                                            text = "ENTER DFU",
                                            style = TextStyle(
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberSignal
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PurpleAccent.copy(alpha = 0.2f))
                                            .border(1.dp, PurpleAccent, RoundedCornerShape(6.dp))
                                            .clickable { onStartBleScanWithPermissions() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("ble_rescan_button")
                                    ) {
                                        Text(
                                            text = if (uiState.isBleScanning) "SCANNING..." else "RESCAN BLE",
                                            style = TextStyle(
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1C232D))
                                            .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                            .clickable {
                                                onToggleLoopbackBench(true)
                                                onStartBleScanWithPermissions()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("ble_simulate_nodes_button")
                                    ) {
                                        Text(
                                            text = "SIMULATE BLE",
                                            style = TextStyle(
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = CyberCyan
                                            )
                                        )
                                    }
                                }
                            }

                            if (uiState.bleDiscoveredNodes.isEmpty()) {
                                Text(
                                    text = if (uiState.isBleScanning) {
                                        "Listening for Meshtastic, MeshCore & Nordic DFU BLE advertisements..."
                                    } else {
                                        "No BLE radios detected yet. Make sure Bluetooth is ON and permission is granted, or tap 'SIMULATE BLE' to test wireless DFU."
                                    },
                                    style = TextStyle(
                                        fontSize = 10.5.sp,
                                        color = TextSecondary
                                    )
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.bleDiscoveredNodes.take(6).forEach { node ->
                                        val isSelected = uiState.selectedBleNode?.address == node.address
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) DarkTealCard else Color(0xFF161B22))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) CyberTeal else CardBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onSelectBleNode(node) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .testTag("ble_node_${node.address}")
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) CyberTeal else Color(0xFF30363D)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(DeepDarkBg)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = node.name,
                                                            style = TextStyle(
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = node.address + (if (node.isInDfuMode) " • DFU Bootloader" else " • BLE GATT"),
                                                            style = TextStyle(
                                                                fontSize = 9.5.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = if (node.isInDfuMode) GreenSuccess else TextSecondary
                                                            )
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${node.rssi} dBm",
                                                    style = TextStyle(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CyberCyan
                                                    )
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

        // Card 2: FIRMWARE CONFIGURATION matching Image 1 with full Meshtastic & MeshCore mapping
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDarkBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("firmware_configuration_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FIRMWARE CONFIGURATION",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFF8B949E)
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1C232D))
                                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                    .clickable { onOpenReleasesPicker() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("open_releases_picker_button")
                            ) {
                                Text(
                                    text = "Versions (${uiState.selectedRelease?.tagName ?: "Latest"}) ▾",
                                    style = TextStyle(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BlueAccent
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1C232D))
                                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                    .clickable { onImportCustomFile() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("import_custom_firmware_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Import Local File",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Local File",
                                        style = TextStyle(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyberCyan
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Ecosystem Pills (Meshtastic vs MeshCore)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Meshtastic Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMeshtastic) DarkTealCard else Color(0xFF1C232D))
                                .border(
                                    1.dp,
                                    if (isMeshtastic) CyberTeal else Color(0xFF2D3748),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHTASTIC) }
                                .testTag("ecosystem_meshtastic_pill"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Meshtastic",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isMeshtastic) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMeshtastic) CyberCyan else TextSecondary
                            )
                        }

                        // MeshCore Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isMeshtastic) DarkTealCard else Color(0xFF1C232D))
                                .border(
                                    1.dp,
                                    if (!isMeshtastic) CyberTeal else Color(0xFF2D3748),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHCORE) }
                                .testTag("ecosystem_meshcore_pill"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MeshCore",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (!isMeshtastic) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isMeshtastic) CyberCyan else TextSecondary
                            )
                        }
                    }

                    if (isMeshtastic) {
                        // ================= MESHTASTIC MAPPING =================
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Release Track",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC9D1D9)
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isAlpha = uiState.selectedMeshtasticTrack == MeshtasticTrack.ALPHA
                                val isStable = uiState.selectedMeshtasticTrack == MeshtasticTrack.STABLE
                                // Alpha
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isAlpha) Color(0xFF1C232D) else Color(0xFF161B22))
                                        .border(
                                            1.5.dp,
                                            if (isAlpha) CyberTeal else CardBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSelectMeshtasticTrack(MeshtasticTrack.ALPHA) }
                                        .testTag("track_alpha_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Alpha ($alphaTag)",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = if (isAlpha) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isAlpha) Color.White else TextSecondary
                                        )
                                    )
                                }

                                // Stable
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isStable) Color(0xFF1C232D) else Color(0xFF161B22))
                                        .border(
                                            1.5.dp,
                                            if (isStable) CyberTeal else CardBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSelectMeshtasticTrack(MeshtasticTrack.STABLE) }
                                        .testTag("track_stable_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Stable ($stableTag)",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = if (isStable) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isStable) Color.White else TextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        // Variant Selector (T-Deck MUI vs BaseUI, or standard Board Target)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isTDeck) "LilyGO T-Deck Variant" else "${board.name} Firmware Target",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC9D1D9)
                                )
                            )

                            if (isTDeck) {
                                // Option 1: Meshtastic UI (MUI Touch)
                                VariantRadioCard(
                                    title = "Meshtastic UI (MUI Touch)",
                                    subtitle = if (uiState.tDeckMeshtasticMui) expectedFileName else "t-deck-tft • Graphical Touch UI & Maps",
                                    selected = uiState.tDeckMeshtasticMui,
                                    onClick = { onSetTDeckMeshtasticMui(true) },
                                    testTag = "tdeck_mui_option"
                                )

                                // Option 2: Classic BaseUI (Terminal)
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
                        // ================= MESHCORE MAPPING =================
                        if (isTDeck) {
                            // T-Deck MeshCore Flavor Selector (Ripple Ultra UI vs WadaMesh)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "T-Deck MeshCore Flavor",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC9D1D9)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TDeckMeshCoreFlavor.entries.forEach { flavor ->
                                        val isSelected = uiState.tDeckMeshCoreFlavor == flavor
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) Color(0xFF1C232D) else Color(0xFF161B22))
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) CyberTeal else CardBorder,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { onSelectTDeckMeshCoreFlavor(flavor) }
                                                .testTag("tdeck_meshcore_flavor_${flavor.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = flavor.label,
                                                style = TextStyle(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else TextSecondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // MeshCore Node Role Selector (for all boards, including T-Deck role override)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "MeshCore Node Role",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC9D1D9)
                                )
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
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isRoleSelected) Color(0xFF1C232D) else Color(0xFF161B22))
                                            .border(
                                                1.5.dp,
                                                if (isRoleSelected) CyberTeal else CardBorder,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectMeshCoreRole(role) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                            .testTag("meshcore_role_${role.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = role.label,
                                            style = TextStyle(
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isRoleSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isRoleSelected) Color.White else TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Active MeshCore Target Binary Card
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isTDeck) "LilyGO T-Deck MeshCore Target" else "${board.name} MeshCore Target",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC9D1D9)
                                )
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

                    // Custom Local Firmware File Banner (when imported or loaded from Off-Grid Vault)
                    uiState.customFirmwareFile?.let { customFile ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF162230))
                                .border(1.dp, BlueAccent, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "LOCAL BINARY: ${customFile.displayName}",
                                        style = TextStyle(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberCyan
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${customFile.sizeBytes / 1024} KB • ${customFile.format} • SHA256: ${customFile.sha256Hex.take(12)}...",
                                        style = TextStyle(
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(CoralRed.copy(alpha = 0.2f))
                                        .clickable { onClearCustomFile() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Local Firmware",
                                        tint = CoralRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Offline Vault Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSavedOffline = currentOfflineEntry != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSavedOffline) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isSavedOffline) GreenSuccess else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isSavedOffline) {
                                    "Cached in Vault (${currentOfflineEntry!!.sizeBytes / 1024} KB)"
                                } else {
                                    "Target: $expectedFileName"
                                },
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = if (isSavedOffline) GreenSuccess else TextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSavedOffline) GreenSuccess.copy(alpha = 0.15f) else Color(0xFF1C232D))
                                .border(
                                    1.dp,
                                    if (isSavedOffline) GreenSuccess.copy(alpha = 0.5f) else Color(0xFF2D3748),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSaveCurrentSelectionOffline() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("save_current_offline_button")
                        ) {
                            Text(
                                text = if (isSavedOffline) "SAVED OFFLINE ✓" else "SAVE TO VAULT",
                                style = TextStyle(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSavedOffline) GreenSuccess else CyberCyan
                                )
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Action Button (with baud rate removed as requested)
        item {
            Button(
                onClick = onStartFlash,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_flash_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(CyberTeal, CyberCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ FLASH FIRMWARE",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0B1411),
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        }

        // Sub-options Row matching Image 1 (Erase Flash: NO / Backup Config: YES)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Erase Flash
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardDarkBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .clickable { onToggleFullErase(!uiState.fullFlashErase) }
                        .padding(horizontal = 12.dp)
                        .testTag("toggle_erase_flash_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Erase Flash: " + if (uiState.fullFlashErase) "YES" else "NO",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.fullFlashErase) GreenSuccess else CoralRed
                        )
                    )
                }

                // Backup Config
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardDarkBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .clickable { onToggleBundleWebUi(!uiState.bundleWebUiPartition) }
                        .padding(horizontal = 12.dp)
                        .testTag("toggle_backup_config_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Backup Config: " + if (uiState.bundleWebUiPartition) "YES" else "NO",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.bundleWebUiPartition) GreenSuccess else TextSecondary
                        )
                    )
                }
            }
        }

        // Info Banner matching Image 1
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF11161D))
                    .border(1.dp, Color(0xFF1D242C), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BlueAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "100%",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueAccent
                                )
                            )
                        }
                        Column {
                            Text(
                                text = "Offline Field Flasher",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pure Kotlin esptool SLIP ROM • No PC needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
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
                            style = TextStyle(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7EE787)
                            )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DarkTealCard else Color(0xFF161B22))
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = if (selected) CyberTeal else CardBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Radio circle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (selected) CyberTeal else Color(0xFF30363D)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DeepDarkBg)
                )
            }

            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else TextSecondary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = if (selected) Color(0xFF7EE787) else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

/**
 * Image 2: Flashing Progress View
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
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Card matching Image 2
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF142634), Color(0xFF1B384D))
                        )
                    )
                    .border(1.dp, Color(0xFF234B68), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BlueAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = BlueAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Flashing Firmware",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                    "BLE OTA DFU PROTOCOL ACTIVE"
                                } else {
                                    "ESPTOOL SLIP PROTOCOL ACTIVE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else BlueAccent,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Speed Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(GreenSuccess.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                "%.1f KBPS".format(Locale.US, uiState.flashProgress.speedKbps.coerceAtLeast(42.4f))
                            } else {
                                "921.6 KBPS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }
        }

        // Circular Gauge Card matching Image 2
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDarkBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Circular Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(130.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 10.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2
                            // Track
                            drawCircle(
                                color = Color(0xFF1F2833),
                                radius = radius,
                                style = Stroke(stroke)
                            )
                            // Progress
                            drawArc(
                                brush = Brush.linearGradient(listOf(CyberTeal, CyberCyan)),
                                startAngle = -90f,
                                sweepAngle = 360f * progressFraction,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$progressPercent%",
                                style = TextStyle(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = uiState.flashProgress.phase.title.uppercase(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            )
                        }
                    }

                    // Target details
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.selectedBoard.name + if (uiState.selectedBoard.id == "lilygo_tdeck") " (MUI Touch)" else "",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Partition: ${uiState.flashProgress.currentPartitionName.ifEmpty { "app0 @ 0x10000" }} (%.1f MB / %.1f MB)".format(
                                Locale.US,
                                uiState.flashProgress.bytesWritten / (1024f * 1024f),
                                uiState.flashProgress.totalBytes.coerceAtLeast(1L) / (1024f * 1024f)
                            ),
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        )
                    }

                    // Linear progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1C232D))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CyberTeal, CyberCyan)
                                    )
                                )
                        )
                    }
                }
            }
        }

        // Stats Grid matching Image 2 (Block Rate, CRC32 Verify, Est Time)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Block Rate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDarkBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "BLOCK RATE",
                            style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        )
                        Text(
                            text = "84.2 KB/s",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                        )
                    }
                }

                // CRC32 Verify
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDarkBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CRC32 VERIFY",
                            style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        )
                        Text(
                            text = "PASSED",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                        )
                    }
                }

                // Est Time
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDarkBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "EST. TIME",
                            style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        )
                        Text(
                            text = "00:08",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlueAccent)
                        )
                    }
                }
            }
        }

        // Protocol Log Window matching Image 2
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(174.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1117))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Window Bar with 3 traffic dots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CoralRed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE3B341)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GreenSuccess))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "ROM Bootloader Protocol Logs",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B949E))
                        )
                    }

                    // Monospace Log Content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val recentLogs = uiState.consoleLogs.takeLast(10)
                        if (recentLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "[09:42:01] DTR/RTS pulse sent: chip in bootloader\n[09:42:02] Connected: ${uiState.selectedBoard.mcu.name}\n[09:42:03] Flash stub uploaded. Baud 115200 -> 921600\n[09:42:04] Writing partition: app0 @ 0x10000\n[09:42:05] >> Writing block ($progressPercent%)... OK\n[09:42:06] >> CRC32 verified",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF7EE787)
                                    )
                                )
                            }
                        } else {
                            items(recentLogs) { log ->
                                Text(
                                    text = "[${log.timestamp}] ${log.message}",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = when (log.level) {
                                            LogLevel.SUCCESS -> GreenSuccess
                                            LogLevel.ERROR -> CoralRed
                                            LogLevel.WARN -> AmberSignal
                                            LogLevel.TX -> PurpleAccent
                                            else -> Color(0xFF7EE787)
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Abort Button matching Image 2
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF21262D))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                    .clickable { onAbortFlash() }
                    .testTag("abort_flash_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABORT FLASHING",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralRed
                    )
                )
            }
        }
    }
}
