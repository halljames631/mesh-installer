package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ConnectionTransport
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme
import com.example.model.FirmwareEcosystem
import com.example.ui.components.HardwareChipBadge
import com.example.ui.screens.BoardsCatalogScreen
import com.example.ui.screens.FirmwareReleasesScreen
import com.example.ui.screens.FlasherWorkbenchScreen
import com.example.ui.screens.OffGridVaultDialog
import com.example.ui.screens.SerialConsoleScreen
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class InstallerTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    FLASHER("flasher", "Install", Icons.Default.Bolt),
    BOARDS("boards", "Radios", Icons.Default.DeveloperBoard),
    RELEASES("releases", "Versions", Icons.Default.CloudDownload),
    CONSOLE("console", "Logs & CLI", Icons.Default.Terminal)
}

@Composable
fun MeshInstallerApp(
    viewModel: MeshInstallerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(InstallerTab.FLASHER) }

    // BackHandler on secondary tabs returns to primary Flasher Workbench
    if (currentTab != InstallerTab.FLASHER) {
        BackHandler {
            currentTab = InstallerTab.FLASHER
        }
    }

    // Zero-permission Android Storage Access Framework launcher for local .bin / .uf2 / .zip firmware files
    val firmwareFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomFirmwareUri(uri)
        }
    }

    // Runtime Bluetooth permissions launcher for BLE OTA DFU scanning
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.startBleScan()
    }

    val triggerBleScanWithPermissions = {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        blePermissionLauncher.launch(perms)
    }

    val ecoColor = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        MeshtasticGreen
    } else {
        MeshCoreCyan
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianBg,
            topBar = {
                MeshInstallerTopBar(
                    uiState = uiState,
                    ecoColor = ecoColor,
                    onOpenOffGridVault = viewModel::openOffGridSetupDialog,
                    onStatusClick = {
                        val usbDev = uiState.usbAttachedBoards.firstOrNull()
                        if (uiState.selectedTransport == ConnectionTransport.USB_OTG) {
                            if (usbDev != null && !usbDev.hasPermission) {
                                viewModel.requestUsbPermission(usbDev)
                            } else {
                                viewModel.rescanUsbBus()
                            }
                        } else {
                            triggerBleScanWithPermissions()
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        tonalElevation = 8.dp
                    ) {
                        InstallerTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = ecoColor,
                                    indicatorColor = ecoColor,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        InstallerTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        InstallerTab.FLASHER -> FlasherWorkbenchScreen(
                            uiState = uiState,
                            expectedFileName = viewModel.expectedFirmwareFileName(),
                            currentOfflineEntry = viewModel.currentSelectionOfflineEntry(),
                            onSelectEcosystem = viewModel::selectEcosystem,
                            onSelectMeshtasticTrack = viewModel::selectMeshtasticTrack,
                            onSelectMeshCoreRole = viewModel::selectMeshCoreRole,
                            onSetTDeckMeshtasticMui = viewModel::setTDeckMeshtasticMui,
                            onSelectTDeckMeshCoreFlavor = viewModel::selectTDeckMeshCoreFlavor,
                            onSelectBoard = viewModel::selectBoard,
                            onOpenBoardPicker = { currentTab = InstallerTab.BOARDS },
                            onOpenReleasesPicker = { currentTab = InstallerTab.RELEASES },
                            onOpenConsole = { currentTab = InstallerTab.CONSOLE },
                            onOpenOffGridVault = viewModel::openOffGridSetupDialog,
                            onDownloadAllForSelected = viewModel::downloadAllFirmwareForMyDevices,
                            onUseSavedEntry = { entry ->
                                viewModel.useSavedVaultEntryAsLocalFile(entry)
                                currentTab = InstallerTab.FLASHER
                            },
                            onSaveCurrentSelectionOffline = viewModel::saveCurrentSelectionOffline,
                            onSelectTransport = viewModel::selectTransport,
                            onRescanUsb = viewModel::rescanUsbBus,
                            onRequestUsbPermission = viewModel::requestUsbPermission,
                            onStartBleScanWithPermissions = triggerBleScanWithPermissions,
                            onSelectBleNode = viewModel::selectBleNode,
                            onToggleFullErase = viewModel::toggleFullFlashErase,
                            onToggleBundleWebUi = viewModel::toggleBundleWebUi,
                            onSelectBaudRate = viewModel::setBaudRate,
                            onToggleLoopbackBench = viewModel::toggleLoopbackBench,
                            onImportCustomFile = {
                                firmwareFileLauncher.launch(arrayOf("*/*"))
                            },
                            onClearCustomFile = viewModel::clearCustomFirmware,
                            onTrigger1200BpsTouch = viewModel::trigger1200BpsDfuTouch,
                            onTriggerEspReset = viewModel::triggerEspReset,
                            onStartFlash = viewModel::startFirmwareFlash,
                            onResetFlashState = viewModel::resetFlashState
                        )

                        InstallerTab.BOARDS -> BoardsCatalogScreen(
                            uiState = uiState,
                            onSelectBoard = { board ->
                                viewModel.selectBoard(board)
                                currentTab = InstallerTab.FLASHER
                            },
                            onSetMcuFilter = viewModel::setMcuFilter,
                            onSetSearchQuery = viewModel::setBoardSearchQuery,
                            onToggleMyDevice = viewModel::toggleMyDevice,
                            onDownloadAllForSelected = viewModel::downloadAllFirmwareForMyDevices,
                            onDownloadSingleBoard = viewModel::downloadSingleBoardFirmware,
                            onUseSavedEntry = { entry ->
                                viewModel.useSavedVaultEntryAsLocalFile(entry)
                                currentTab = InstallerTab.FLASHER
                            }
                        )

                        InstallerTab.RELEASES -> FirmwareReleasesScreen(
                            uiState = uiState,
                            onSelectEcosystem = viewModel::selectEcosystem,
                            onSelectRelease = { rel ->
                                viewModel.selectRelease(rel)
                                currentTab = InstallerTab.FLASHER
                            },
                            onRefreshReleases = viewModel::refreshFirmwareCatalog,
                            onImportCustomFile = {
                                firmwareFileLauncher.launch(arrayOf("*/*"))
                            }
                        )

                        InstallerTab.CONSOLE -> SerialConsoleScreen(
                            uiState = uiState,
                            onSendCommand = viewModel::sendConsoleCommand,
                            onTrigger1200BpsTouch = viewModel::trigger1200BpsDfuTouch,
                            onTriggerEspReset = viewModel::triggerEspReset,
                            onClearLogs = viewModel::clearConsoleLogs
                        )
                    }
                }
            }
        }

        if (uiState.showOffGridSetupDialog) {
            OffGridVaultDialog(
                uiState = uiState,
                onToggleMyDevice = viewModel::toggleMyDevice,
                onSelectPopular = viewModel::selectPopularMyDevices,
                onSelectAll = viewModel::selectAllMyDevices,
                onClearSelection = viewModel::clearMyDevices,
                onDownloadAllForSelected = viewModel::downloadAllFirmwareForMyDevices,
                onDownloadSingleBoard = viewModel::downloadSingleBoardFirmware,
                onUseSavedEntry = { entry ->
                    viewModel.useSavedVaultEntryAsLocalFile(entry)
                    currentTab = InstallerTab.FLASHER
                },
                onCancelDownload = viewModel::cancelBatchDownload,
                onClearVault = viewModel::clearOfflineVault,
                onDismiss = viewModel::closeOffGridSetupDialog
            )
        }
    }
}

@Composable
private fun MeshInstallerTopBar(
    uiState: MeshInstallerUiState,
    ecoColor: androidx.compose.ui.graphics.Color,
    onOpenOffGridVault: () -> Unit,
    onStatusClick: () -> Unit
) {
    val activeFirmwareSummary = when {
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC ->
            if (uiState.tDeckMeshtasticMui) "Meshtastic UI (MUI)" else "Meshtastic BaseUI"
        uiState.selectedBoard.id == "lilygo_tdeck" && uiState.selectedEcosystem == FirmwareEcosystem.MESHCORE ->
            "MeshCore • ${uiState.tDeckMeshCoreFlavor.label}"
        else -> uiState.selectedEcosystem.displayName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurface)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateCard)
                        .border(1.5.dp, ecoColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Mesh Installer",
                        tint = ecoColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mesh Installer",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$activeFirmwareSummary • ${uiState.selectedBoard.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val savedCount = uiState.offlineVaultEntries.size
                HardwareChipBadge(
                    text = if (savedCount > 0) "OFF-GRID ($savedCount)" else "OFF-GRID",
                    color = if (savedCount > 0) MeshtasticGreen else MeshCoreCyan,
                    modifier = Modifier
                        .clickable { onOpenOffGridVault() }
                        .testTag("topbar_offgrid_vault_button")
                )

                val usbDev = uiState.usbAttachedBoards.firstOrNull()
                val (transportText, pillColor) = when {
                    uiState.selectedTransport == ConnectionTransport.USB_OTG && usbDev != null && usbDev.hasPermission ->
                        "USB READY" to MeshtasticGreen
                    uiState.selectedTransport == ConnectionTransport.USB_OTG && usbDev != null && !usbDev.hasPermission ->
                        "ALLOW USB" to AmberSignal
                    uiState.selectedTransport == ConnectionTransport.USB_OTG ->
                        "NO USB" to TextSecondary
                    uiState.selectedBleNode != null ->
                        "BLE READY" to MeshCoreCyan
                    else ->
                        "BLE MODE" to ecoColor
                }

                HardwareChipBadge(
                    text = transportText,
                    color = pillColor,
                    modifier = Modifier.clickable { onStatusClick() }
                )

                if (uiState.loopbackTestBench && uiState.usbAttachedBoards.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberSignal.copy(alpha = 0.14f))
                            .border(1.dp, AmberSignal.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AmberSignal)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberSignal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

@Preview(name = "Mesh Installer App TopBar", showBackground = true, backgroundColor = 0xFF0B0F19)
@Composable
fun MeshInstallerTopBarPreview() {
    MyApplicationTheme(darkTheme = true) {
        MeshInstallerTopBar(
            uiState = MeshInstallerUiState(),
            ecoColor = com.example.ui.theme.MeshtasticGreen,
            onOpenOffGridVault = {},
            onStatusClick = {}
        )
    }
}



