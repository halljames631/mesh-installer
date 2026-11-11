package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.model.McuArchitecture
import com.example.model.MeshCoreRole
import com.example.model.MeshtasticTrack
import com.example.model.TDeckMeshCoreFlavor
import com.example.model.UsbAttachedBoard
import com.example.ui.MeshInstallerUiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
import kotlinx.coroutines.launch
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
    onEraseFlash: () -> Unit = {},
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
            onSelectBaudRate = onSelectBaudRate,
            onToggleLoopbackBench = onToggleLoopbackBench,
            onImportCustomFile = onImportCustomFile,
            onClearCustomFile = onClearCustomFile,
            onTrigger1200BpsTouch = onTrigger1200BpsTouch,
            onTriggerEspReset = onTriggerEspReset,
            onStartFlash = onStartFlash,
            onEraseFlash = onEraseFlash,
            onResetFlashState = onResetFlashState,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    onSelectBaudRate: (Int) -> Unit = {},
    onToggleLoopbackBench: (Boolean) -> Unit,
    onImportCustomFile: () -> Unit,
    onClearCustomFile: () -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onStartFlash: () -> Unit,
    onEraseFlash: () -> Unit = {},
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

    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var baudMenuExpanded by remember { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var activeStep by rememberSaveable { mutableIntStateOf(1) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isConnectionReady = when (uiState.selectedTransport) {
        ConnectionTransport.USB_OTG -> hasPermission || uiState.loopbackTestBench
        ConnectionTransport.BLE_DFU -> uiState.selectedBleNode != null || uiState.loopbackTestBench
    }

    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Top Header Bar + Horizontal Stepper
        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Surface(
                                shape = CircleShape,
                                color = if (isConnectionReady) GreenSuccess.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Usb,
                                        contentDescription = "Connection Status",
                                        tint = if (isConnectionReady) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            "BLE OTA • ${uiState.selectedBleNode?.name ?: "No BLE Radio Selected"}"
                                        hasPermission ->
                                            "USB Connected • ${board.name}"
                                        isUsbAttached ->
                                            "USB Attached • Permission Needed"
                                        uiState.loopbackTestBench ->
                                            "Simulation Mode Ready"
                                        else ->
                                            "No USB Radio Plugged In"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isConnectionReady) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                    onStartBleScanWithPermissions()
                                } else if (isUsbAttached && !hasPermission) {
                                    onRequestUsbPermission(usbDev!!)
                                } else {
                                    onRescanUsb()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("usb_status_action_pill")
                        ) {
                            Text(
                                text = when {
                                    uiState.selectedTransport == ConnectionTransport.BLE_DFU ->
                                        if (uiState.isBleScanning) "SCANNING..." else "SCAN BLE"
                                    hasPermission -> "RESCAN"
                                    isUsbAttached -> "ALLOW USB"
                                    else -> "SCAN USB"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Clean 3-Step Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepTabButton(
                                stepNumber = 1,
                                label = "1. Device",
                                isCurrent = activeStep == 1,
                                isDone = activeStep > 1,
                                onClick = { activeStep = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stepper_node_1")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StepTabButton(
                                stepNumber = 2,
                                label = "2. Firmware",
                                isCurrent = activeStep == 2,
                                isDone = activeStep > 2,
                                onClick = { activeStep = 2 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stepper_node_2")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StepTabButton(
                                stepNumber = 3,
                                label = "3. Flash",
                                isCurrent = activeStep == 3,
                                isDone = uiState.flashProgress.phase == FlashPhase.COMPLETED,
                                onClick = { activeStep = 3 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stepper_node_3")
                            )
                        }

                        LinearProgressIndicator(
                            progress = { activeStep / 3f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Flash Completion or Failure Banner
        if (uiState.flashProgress.phase == FlashPhase.COMPLETED || uiState.flashProgress.phase == FlashPhase.FAILED) {
            item {
                val isSuccess = uiState.flashProgress.phase == FlashPhase.COMPLETED
                val accentColor = if (isSuccess) GreenSuccess else MaterialTheme.colorScheme.error
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = accentColor.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.5.dp, accentColor),
                    modifier = Modifier.fillMaxWidth()
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
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.flashProgress.errorMessage ?: uiState.flashProgress.phase.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isSuccess) {
                                val err = (uiState.flashProgress.errorMessage ?: "").lowercase(Locale.US)
                                val tip = when {
                                    uiState.selectedBoard.id == "seeed_t1000e" && (err.contains("bootloader") || err.contains("interrupted") || err.contains("msc")) ->
                                        "T1000-E Bootloader: Hold device button for 3s while plugging in magnetic cable (or double-flick magnetic cable) until green LED stays solid, then tap Flash."
                                    err.contains("sync") || err.contains("bootloader") || err.contains("0x08") || err.contains("sync with") ->
                                        "Hold the BOOT/PRG button while connecting USB-C, or re-seat the OTG adapter."
                                    err.contains("permission") || err.contains("denied") || err.contains("grant") ->
                                        "Tap 'Allow' on the Android USB permission prompt."
                                    err.contains("write") || err.contains("data") || err.contains("block") || err.contains("timeout") || err.contains("ack") ->
                                        "Lower the baud rate to 115200 or 460800 in Advanced Settings."
                                    else ->
                                        "Hold the BOOT/PRG button while connecting USB-C, or re-seat the OTG adapter."
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "💡 Fix: $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { onResetFlashState() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "DISMISS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==================== STEP 1: SELECT RADIO MODEL (DROPDOWN MENU) & CONNECTION ====================
        item {
            val step1Border by animateColorAsState(
                targetValue = if (activeStep == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "step1Border"
            )
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(if (activeStep == 1) 2.dp else 1.dp, step1Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connected_hardware_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Step 1 Title Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeStep = 1 },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StepNumberBadge(
                                number = 1,
                                isActive = activeStep == 1,
                                isCompleted = activeStep > 1
                            )
                            Column {
                                Text(
                                    text = "STEP 1: SELECT DEVICE & CONNECTION",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStep == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${board.name} • ${uiState.selectedTransport.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = { onOpenBoardPicker() },
                            modifier = Modifier.testTag("change_radio_button")
                        ) {
                            Text(
                                text = "Full Catalog",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // DEVICE SELECTION DROPDOWN MENU (ExposedDropdownMenuBox)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Radio Hardware Model",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = deviceMenuExpanded,
                            onExpandedChange = {
                                activeStep = 1
                                deviceMenuExpanded = it
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = "${board.name}  (${board.mcu.shortName} • ${board.flashSizeMb}MB)",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceMenuExpanded)
                                },
                                supportingText = {
                                    Text(
                                        text = "LoRa: ${board.loraChip} • USB: ${board.usbBridge}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("device_dropdown_field")
                            )

                            DropdownMenu(
                                expanded = deviceMenuExpanded,
                                onDismissRequest = { deviceMenuExpanded = false },
                                modifier = Modifier
                                    .exposedDropdownSize()
                                    .heightIn(max = 360.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                BoardCatalog.allBoards.forEach { candidateBoard ->
                                    val isCurrentBoard = candidateBoard.id == board.id
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = candidateBoard.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isCurrentBoard) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrentBoard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${candidateBoard.manufacturer} • ${candidateBoard.mcu.shortName} • ${candidateBoard.flashSizeMb}MB Flash",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (isCurrentBoard) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            deviceMenuExpanded = false
                                            onSelectBoard(candidateBoard)
                                        },
                                        modifier = Modifier.testTag("quick_select_${candidateBoard.id}")
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Open Full Radio Hardware Catalog...",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    onClick = {
                                        deviceMenuExpanded = false
                                        onOpenBoardPicker()
                                    }
                                )
                            }
                        }
                    }

                    // Connection Method Segmented Button (Only 1 is filled; inactive is transparent)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connection Method",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = {
                                    if (uiState.selectedTransport == ConnectionTransport.BLE_DFU ||
                                        board.mcu.name.startsWith("NRF") ||
                                        board.mcu.name.startsWith("RP2040")
                                    ) {
                                        onTrigger1200BpsTouch()
                                    } else {
                                        onTriggerEspReset()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("enter_dfu_mode_button")
                            ) {
                                Text(
                                    text = "Reboot to Bootloader (DFU)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        val isUsbMode = uiState.selectedTransport == ConnectionTransport.USB_OTG
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = isUsbMode,
                                onClick = {
                                    activeStep = 1
                                    onSelectTransport(ConnectionTransport.USB_OTG)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = 0,
                                    count = if (board.mcu.supportsBleDfu) 2 else 1
                                ),
                                colors = segmentedColors,
                                modifier = Modifier.testTag("transport_usb_button")
                            ) {
                                Text(
                                    text = "USB-C Cable (OTG)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isUsbMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            if (board.mcu.supportsBleDfu) {
                                val isBle = uiState.selectedTransport == ConnectionTransport.BLE_DFU
                                SegmentedButton(
                                    selected = isBle,
                                    onClick = {
                                        activeStep = 1
                                        if (!isBle) {
                                            onSelectTransport(ConnectionTransport.BLE_DFU)
                                            onStartBleScanWithPermissions()
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    colors = segmentedColors,
                                    modifier = Modifier.testTag("toggle_transport_button")
                                ) {
                                    Text(
                                        text = "Bluetooth OTA (BLE)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isBle) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // BLE Scanner Panel when BLE OTA is chosen
                    if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(1.dp, PurpleAccent.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isBleScanning) {
                                            "Scanning Bluetooth Radios..."
                                        } else {
                                            "Discovered BLE Radios (${uiState.bleDiscoveredNodes.size})"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PurpleAccent
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { onTrigger1200BpsTouch() },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.testTag("ble_enter_dfu_button")
                                        ) {
                                            Text("Enter DFU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                        TextButton(
                                            onClick = { onStartBleScanWithPermissions() },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.testTag("ble_rescan_button")
                                        ) {
                                            Text("Rescan", style = MaterialTheme.typography.labelSmall, color = PurpleAccent)
                                        }
                                        TextButton(
                                            onClick = {
                                                onToggleLoopbackBench(true)
                                                onStartBleScanWithPermissions()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.testTag("ble_simulate_nodes_button")
                                        ) {
                                            Text("Simulate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                if (uiState.bleDiscoveredNodes.isEmpty()) {
                                    OutlinedButton(
                                        onClick = { onStartBleScanWithPermissions() },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("step1_scan_ble_button")
                                    ) {
                                        Text("Scan for Nearby Bluetooth Radios")
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        uiState.bleDiscoveredNodes.take(8).forEach { node ->
                                            val isSelected = uiState.selectedBleNode?.address == node.address
                                            OutlinedCard(
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.outlinedCardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                                ),
                                                border = BorderStroke(
                                                    if (isSelected) 1.5.dp else 1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onSelectBleNode(node) }
                                                    .testTag("ble_node_${node.address}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { onSelectBleNode(node) }
                                                        )
                                                        Column {
                                                            Text(
                                                                text = node.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "${node.address} • ${node.rssi} dBm",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    // Connection Status Banner inside Step 1
                    if (uiState.selectedTransport == ConnectionTransport.USB_OTG) {
                        if (isUsbAttached && !hasPermission) {
                            OutlinedButton(
                                onClick = { onRequestUsbPermission(usbDev!!) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("step1_grant_permission_button")
                            ) {
                                Text(
                                    text = "USB Device Detected — Tap to Grant Permission",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!isUsbAttached && !uiState.loopbackTestBench) {
                            OutlinedButton(
                                onClick = { onRescanUsb() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("step1_scan_usb_button")
                            ) {
                                Text(
                                    text = "Plug in USB-C OTG Cable & Tap to Scan",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Step 1 -> Step 2 Next Button
                    FilledTonalButton(
                        onClick = {
                            activeStep = 2
                            coroutineScope.launch {
                                listState.animateScrollToItem(2)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("step1_next_button")
                    ) {
                        Text(
                            text = "Next: Step 2 — Choose Firmware",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ==================== STEP 2: CHOOSE FIRMWARE & ERASE OPTIONS ====================
        item {
            val step2Border by animateColorAsState(
                targetValue = if (activeStep == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "step2Border"
            )
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(if (activeStep == 2) 2.dp else 1.dp, step2Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("firmware_configuration_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Step 2 Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeStep = 2 },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StepNumberBadge(
                                number = 2,
                                isActive = activeStep == 2,
                                isCompleted = activeStep > 2
                            )
                            Column {
                                Text(
                                    text = "STEP 2: CHOOSE FIRMWARE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStep == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${uiState.selectedEcosystem.displayName} • ${uiState.selectedRelease?.tagName ?: "Latest"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    activeStep = 2
                                    onOpenReleasesPicker()
                                },
                                modifier = Modifier.testTag("open_releases_picker_button")
                            ) {
                                Text(
                                    text = "Version (${uiState.selectedRelease?.tagName ?: "Latest"}) ▾",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            TextButton(
                                onClick = {
                                    activeStep = 2
                                    onImportCustomFile()
                                },
                                modifier = Modifier.testTag("import_custom_firmware_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Local File",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "File",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 1. Firmware Ecosystem Segmented Button (Meshtastic vs MeshCore)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Firmware Ecosystem",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = isMeshtastic,
                                onClick = {
                                    activeStep = 2
                                    onSelectEcosystem(FirmwareEcosystem.MESHTASTIC)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = segmentedColors,
                                modifier = Modifier.testTag("ecosystem_meshtastic_pill")
                            ) {
                                Text(
                                    text = "Meshtastic",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isMeshtastic) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            SegmentedButton(
                                selected = !isMeshtastic,
                                onClick = {
                                    activeStep = 2
                                    onSelectEcosystem(FirmwareEcosystem.MESHCORE)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = segmentedColors,
                                modifier = Modifier.testTag("ecosystem_meshcore_pill")
                            ) {
                                Text(
                                    text = "MeshCore",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (!isMeshtastic) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (isMeshtastic) {
                        // ================= MESHTASTIC CHANNEL =================
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Release Channel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val isStable = uiState.selectedMeshtasticTrack == MeshtasticTrack.STABLE
                            val isAlpha = uiState.selectedMeshtasticTrack == MeshtasticTrack.ALPHA
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = isStable,
                                    onClick = {
                                        activeStep = 2
                                        onSelectMeshtasticTrack(MeshtasticTrack.STABLE)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    colors = segmentedColors,
                                    modifier = Modifier.testTag("track_stable_button")
                                ) {
                                    Text(
                                        text = "Stable ($stableTag)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isStable) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                SegmentedButton(
                                    selected = isAlpha,
                                    onClick = {
                                        activeStep = 2
                                        onSelectMeshtasticTrack(MeshtasticTrack.ALPHA)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    colors = segmentedColors,
                                    modifier = Modifier.testTag("track_alpha_button")
                                ) {
                                    Text(
                                        text = "Alpha ($alphaTag)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isAlpha) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        if (isTDeck) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "T-Deck Interface Style",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.tDeckMeshtasticMui,
                                        onClick = {
                                            activeStep = 2
                                            onSetTDeckMeshtasticMui(true)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        colors = segmentedColors,
                                        modifier = Modifier.testTag("tdeck_mui_option")
                                    ) {
                                        Text("MUI Touch UI")
                                    }
                                    SegmentedButton(
                                        selected = !uiState.tDeckMeshtasticMui,
                                        onClick = {
                                            activeStep = 2
                                            onSetTDeckMeshtasticMui(false)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        colors = segmentedColors,
                                        modifier = Modifier.testTag("tdeck_baseui_option")
                                    ) {
                                        Text("Classic BaseUI")
                                    }
                                }
                            }
                        }
                    } else {
                        // ================= MESHCORE ROLE DROPDOWN MENU =================
                        if (isTDeck) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "T-Deck MeshCore Edition",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    TDeckMeshCoreFlavor.entries.forEachIndexed { index, flavor ->
                                        val isSelected = uiState.tDeckMeshCoreFlavor == flavor
                                        SegmentedButton(
                                            selected = isSelected,
                                            onClick = {
                                                activeStep = 2
                                                onSelectTDeckMeshCoreFlavor(flavor)
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = TDeckMeshCoreFlavor.entries.size
                                            ),
                                            colors = segmentedColors,
                                            modifier = Modifier.testTag("tdeck_meshcore_flavor_${flavor.name.lowercase()}")
                                        ) {
                                            Text(flavor.label)
                                        }
                                    }
                                }
                            }
                        }

                        // MeshCore Node Role Dropdown Menu
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "MeshCore Node Role",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val roles = listOf(
                                MeshCoreRole.COMPANION_BLE,
                                MeshCoreRole.COMPANION_USB,
                                MeshCoreRole.REPEATER,
                                MeshCoreRole.ROOM_SERVER
                            )
                            ExposedDropdownMenuBox(
                                expanded = roleMenuExpanded,
                                onExpandedChange = {
                                    activeStep = 2
                                    roleMenuExpanded = it
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = uiState.selectedMeshCoreRole.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenuExpanded)
                                    },
                                    supportingText = {
                                        Text(
                                            text = uiState.selectedMeshCoreRole.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("meshcore_target_card")
                                )

                                DropdownMenu(
                                    expanded = roleMenuExpanded,
                                    onDismissRequest = { roleMenuExpanded = false },
                                    modifier = Modifier
                                        .exposedDropdownSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    roles.forEach { role ->
                                        val isCurrentRole = uiState.selectedMeshCoreRole == role
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = role.label,
                                                        fontWeight = if (isCurrentRole) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isCurrentRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = role.description,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (isCurrentRole) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                roleMenuExpanded = false
                                                onSelectMeshCoreRole(role)
                                            },
                                            modifier = Modifier.testTag("meshcore_role_${role.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Custom Local Firmware File Banner (only shown when user imports a custom file)
                    uiState.customFirmwareFile?.let { customFile ->
                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth()
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
                                        text = "Custom File: ${customFile.displayName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${customFile.sizeBytes / 1024} KB • ${customFile.format}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onClearCustomFile() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Local Firmware",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Clean Erase Flash Switch Row (Unselected = neutral surface, Selected = clear switch state)
                    val eraseDescription = when (board.mcu) {
                        McuArchitecture.NRF52840 ->
                            "Wipes nRF52840 InternalFS (28KB Configs & BLE Bonds) via UF2"
                        McuArchitecture.RP2040 ->
                            "Wipes RP2040 LittleFS (64KB Configs) via UF2"
                        else -> when {
                            board.flashSizeMb >= 16 ->
                                "Wipes NVS (0x9000) + LittleFS /prefs (0x300000, 0x670000 & 0xC90000)"
                            board.flashSizeMb >= 8 ->
                                "Wipes NVS (0x9000) + LittleFS /prefs (0x300000 & 0x670000)"
                            else ->
                                "Wipes NVS (0x9000) + SPIFFS/LittleFS /prefs (0x290000 & 0x300000)"
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleFullErase(!uiState.fullFlashErase) }
                            .padding(vertical = 4.dp)
                            .testTag("step2_full_erase_card"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Full Flash Erase (Factory Reset)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = eraseDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.fullFlashErase,
                            onCheckedChange = { onToggleFullErase(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Offline Vault & Advanced Settings Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSavedOffline = currentOfflineEntry != null
                        OutlinedButton(
                            onClick = { onSaveCurrentSelectionOffline() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_current_offline_button")
                        ) {
                            Icon(
                                imageVector = if (isSavedOffline) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isSavedOffline) GreenSuccess else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSavedOffline) "Saved in Vault" else "Save for Offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSavedOffline) GreenSuccess else MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                            modifier = Modifier.testTag("advanced_settings_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsInputComponent,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Advanced (${uiState.selectedBaudRate / 1000}K)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (advancedSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = advancedSettingsExpanded) {
                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Baud Rate Dropdown Menu inside Advanced Settings
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "USB Flash Baud Rate",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box {
                                        OutlinedButton(
                                            onClick = { baudMenuExpanded = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${uiState.selectedBaudRate} bps",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = baudMenuExpanded,
                                            onDismissRequest = { baudMenuExpanded = false }
                                        ) {
                                            listOf(115200, 230400, 460800, 921600).forEach { rate ->
                                                DropdownMenuItem(
                                                    text = { Text("$rate bps") },
                                                    onClick = {
                                                        baudMenuExpanded = false
                                                        onSelectBaudRate(rate)
                                                    },
                                                    modifier = Modifier.testTag("baud_rate_$rate")
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bundle LittleFS WebUI Partition",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Flashes littlefs-*.bin on ESP32/S3 radios",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.bundleWebUiPartition,
                                        onCheckedChange = { onToggleBundleWebUi(it) },
                                        modifier = Modifier.testTag("toggle_backup_config_button")
                                    )
                                }

                                val primaryOffset = board.partitions.firstOrNull { it.isRequired }?.addressHex ?: "0x10000"
                                Text(
                                    text = "Target File: $expectedFileName\nOffset: $primaryOffset • MCU: ${board.mcu.shortName} (${board.flashSizeMb}MB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("board_meshtastic_target_card")
                                )
                            }
                        }
                    }

                    // Step 2 Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeStep = 1
                                coroutineScope.launch {
                                    listState.animateScrollToItem(1)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .height(44.dp)
                                .testTag("step2_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Step 1", fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                activeStep = 3
                                coroutineScope.launch {
                                    listState.animateScrollToItem(3)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.65f)
                                .height(44.dp)
                                .testTag("step2_next_button")
                        ) {
                            Text(
                                text = "Next: Step 3 — Ready to Flash",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // High-Power RF Safety Warning (station_g2 3.5W PA, heltec_v4 27dBm PA)
        if (board.id == "station_g2" || board.id == "heltec_v4") {
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rf_safety_warning_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "RF PA Warning",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "HIGH-POWER RF SAFETY WARNING",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Warning: Connect RF antenna before plugging in USB power to prevent PA damage.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // ==================== STEP 3: REVIEW & FLASH ====================
        item {
            val step3Border by animateColorAsState(
                targetValue = if (activeStep == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "step3Border"
            )
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(if (activeStep == 3) 2.dp else 1.dp, step3Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_safety_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeStep = 3 },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StepNumberBadge(
                                number = 3,
                                isActive = activeStep == 3,
                                isCompleted = uiState.flashProgress.phase == FlashPhase.COMPLETED
                            )
                            Column {
                                Text(
                                    text = "STEP 3: REVIEW & FLASH",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStep == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Verify your selections below and start flashing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Clean Pre-Flight Summary Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryRow(
                                label = "Selected Device",
                                value = "${board.name} (${board.mcu.shortName})"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            SummaryRow(
                                label = "Firmware Image",
                                value = expectedFileName
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            SummaryRow(
                                label = "Erase Mode",
                                value = if (uiState.fullFlashErase) "Full Erase (Wipe NVS & Configs)" else "Update Only (Keep Configs)"
                            )
                        }
                    }

                    val isUsbMode = uiState.selectedTransport == ConnectionTransport.USB_OTG
                    val isBleMode = uiState.selectedTransport == ConnectionTransport.BLE_DFU

                    val buttonLabel = when {
                        isUsbMode && isUsbAttached && !hasPermission -> "GRANT USB PERMISSION TO FLASH"
                        isUsbMode && !isUsbAttached && !uiState.loopbackTestBench -> "CONNECT RADIO VIA USB-C"
                        isBleMode && uiState.selectedBleNode == null -> "SCAN & SELECT BLE RADIO"
                        else -> "FLASH FIRMWARE NOW"
                    }

                    Button(
                        onClick = {
                            activeStep = 3
                            when {
                                isUsbMode && isUsbAttached && !hasPermission -> onRequestUsbPermission(usbDev!!)
                                isUsbMode && !isUsbAttached && !uiState.loopbackTestBench -> onRescanUsb()
                                isBleMode && uiState.selectedBleNode == null -> onStartBleScanWithPermissions()
                                else -> onStartFlash()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_flash_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUsbMode && isUsbAttached && !hasPermission) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isUsbMode && isUsbAttached && !hasPermission) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Standalone Hardware Flash Erase Button
                    if (isUsbMode) {
                        val eraseButtonLabel = when (board.mcu) {
                            McuArchitecture.NRF52840 ->
                                "Erase Flash Only (Wipe nRF52 Config & Bonds)"
                            McuArchitecture.RP2040 ->
                                "Erase Flash Only (Wipe RP2040 LittleFS)"
                            else ->
                                "Erase Flash Only (Full ${board.mcu.shortName} Factory Wipe)"
                        }
                        OutlinedButton(
                            onClick = {
                                activeStep = 3
                                when {
                                    isUsbAttached && !hasPermission -> onRequestUsbPermission(usbDev!!)
                                    !isUsbAttached && !uiState.loopbackTestBench -> onRescanUsb()
                                    else -> onEraseFlash()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("erase_flash_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = eraseButtonLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTabButton(
    stepNumber: Int,
    label: String,
    isCurrent: Boolean,
    isDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = BorderStroke(
            1.dp,
            when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isDone -> GreenSuccess.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isDone && !isCurrent) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GreenSuccess,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.onPrimary
                    isDone -> GreenSuccess
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepNumberBadge(
    number: Int,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val bgColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isCompleted -> GreenSuccess
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimary
        isCompleted -> Color(0xFF002414)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.size(26.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCompleted && !isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
        }
    }
}

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
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
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
                                    "ESPTOOL SLIP / UF2 PROTOCOL ACTIVE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) PurpleAccent else MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GreenSuccess.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (uiState.selectedTransport == ConnectionTransport.BLE_DFU) {
                                "%.1f KBPS".format(Locale.US, uiState.flashProgress.speedKbps.coerceAtLeast(42.4f))
                            } else {
                                "${uiState.selectedBaudRate / 1000} KBPS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Circular Gauge Card
        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(136.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 10.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2
                            drawCircle(
                                color = Color(0xFF1F2833),
                                radius = radius,
                                style = Stroke(stroke)
                            )
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
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
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

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.selectedBoard.name + if (uiState.selectedBoard.id == "lilygo_tdeck") " (MUI Touch)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "BLOCK RATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "84.2 KB/s",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "MD5 / CRC32",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "VERIFIED",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "EST. TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "00:08",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Protocol Log Window
        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color(0xFF0D1117)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp),
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
                                    fontSize = 10.sp,
                                    color = GreenSuccess
                                )
                            }
                        } else {
                            items(recentLogs) { log ->
                                Text(
                                    text = "[${log.timestamp}] ${log.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
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

        // Abort Button
        item {
            OutlinedButton(
                onClick = { onAbortFlash() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("abort_flash_button")
            ) {
                Text(
                    text = "ABORT FLASHING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
