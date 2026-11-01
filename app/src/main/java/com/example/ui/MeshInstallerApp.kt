package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.BoardsCatalogScreen
import com.example.ui.screens.FirmwareReleasesScreen
import com.example.ui.screens.FlasherWorkbenchScreen
import com.example.ui.screens.OffGridVaultDialog
import com.example.ui.screens.OffGridVaultScreen
import com.example.ui.screens.SerialConsoleScreen
import com.example.ui.screens.TestBenchScreen
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.DeepDarkBg
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TextSecondary

enum class InstallerTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    FLASHER("flasher", "Flasher", Icons.Default.NearMe),
    TERMINAL("terminal", "Terminal", Icons.Default.Terminal),
    VAULT("vault", "Vault", Icons.Default.Lock),
    TEST_BENCH("test_bench", "Test Bench", Icons.Default.RadioButtonUnchecked),
    BOARDS("boards", "Radios", Icons.Default.DeveloperBoard),
    RELEASES("releases", "Versions", Icons.Default.CloudDownload)
}

@Composable
fun MeshInstallerApp(
    viewModel: MeshInstallerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(InstallerTab.FLASHER) }

    // BackHandler on secondary screens returns to primary Flasher
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = DeepDarkBg,
            bottomBar = {
                if (!isWideScreen) {
                    val mainTabs = listOf(
                        InstallerTab.FLASHER,
                        InstallerTab.TERMINAL,
                        InstallerTab.VAULT,
                        InstallerTab.TEST_BENCH
                    )
                    NavigationBar(
                        containerColor = Color(0xFF0D1117),
                        tonalElevation = 0.dp,
                        modifier = Modifier.border(width = 1.dp, color = Color(0xFF21262D))
                    ) {
                        mainTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val tabColor = when (tab) {
                                InstallerTab.FLASHER -> CyberCyan
                                InstallerTab.TERMINAL -> PurpleAccent
                                InstallerTab.VAULT -> BlueAccent
                                InstallerTab.TEST_BENCH -> AmberSignal
                                else -> CyberCyan
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) tabColor.copy(alpha = 0.15f) else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (selected) tabColor else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) tabColor else TextSecondary
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent
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
                        containerColor = Color(0xFF0D1117),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        val mainTabs = listOf(
                            InstallerTab.FLASHER,
                            InstallerTab.TERMINAL,
                            InstallerTab.VAULT,
                            InstallerTab.TEST_BENCH
                        )
                        mainTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val tabColor = when (tab) {
                                InstallerTab.FLASHER -> CyberCyan
                                InstallerTab.TERMINAL -> PurpleAccent
                                InstallerTab.VAULT -> BlueAccent
                                InstallerTab.TEST_BENCH -> AmberSignal
                                else -> CyberCyan
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (selected) tabColor else TextSecondary
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
                            onOpenConsole = { currentTab = InstallerTab.TERMINAL },
                            onOpenOffGridVault = { currentTab = InstallerTab.VAULT },
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

                        InstallerTab.TERMINAL -> SerialConsoleScreen(
                            uiState = uiState,
                            onSendCommand = viewModel::sendConsoleCommand,
                            onTrigger1200BpsTouch = viewModel::trigger1200BpsDfuTouch,
                            onTriggerEspReset = viewModel::triggerEspReset,
                            onClearLogs = viewModel::clearConsoleLogs
                        )

                        InstallerTab.VAULT -> OffGridVaultScreen(
                            uiState = uiState,
                            onSelectBoard = { board ->
                                viewModel.selectBoard(board)
                                currentTab = InstallerTab.FLASHER
                            },
                            onDownloadAllForSelected = viewModel::downloadAllFirmwareForMyDevices,
                            onUseSavedEntry = { entry ->
                                viewModel.useSavedVaultEntryAsLocalFile(entry)
                                currentTab = InstallerTab.FLASHER
                            },
                            onToggleMyDevice = viewModel::toggleMyDevice,
                            onClearVault = viewModel::clearOfflineVault
                        )

                        InstallerTab.TEST_BENCH -> TestBenchScreen(
                            uiState = uiState,
                            onToggleLoopbackBench = viewModel::toggleLoopbackBench,
                            onTrigger1200BpsTouch = viewModel::trigger1200BpsDfuTouch,
                            onTriggerEspReset = viewModel::triggerEspReset,
                            onStartSimulatedFlash = {
                                viewModel.toggleLoopbackBench(true)
                                viewModel.startFirmwareFlash()
                                currentTab = InstallerTab.FLASHER
                            }
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
                    viewModel.closeOffGridSetupDialog()
                },
                onCancelDownload = viewModel::cancelBatchDownload,
                onClearVault = viewModel::clearOfflineVault,
                onDismiss = viewModel::closeOffGridSetupDialog
            )
        }
    }
}
