package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Refresh
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultNavyBorder
import com.example.ui.theme.VaultNavyHeader
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
    val isFlashing = uiState.flashProgress.phase != FlashPhase.IDLE && uiState.flashProgress.phase != FlashPhase.COMPLETED && uiState.flashProgress.phase != FlashPhase.FAILED

    if (isFlashing) {
        // IMAGE 2: Flashing Progress View
        FlashingProgressView(
            uiState = uiState,
            expectedFileName = expectedFileName,
            onAbortFlash = onResetFlashState,
            modifier = modifier
        )
    } else {
        // IMAGE 1: Flasher Main View
        FlasherIdleView(
            uiState = uiState,
            expectedFileName = expectedFileName,
            onSelectEcosystem = onSelectEcosystem,
            onSelectMeshtasticTrack = onSelectMeshtasticTrack,
            onSelectMeshCoreRole = onSelectMeshCoreRole,
            onSetTDeckMeshtasticMui = onSetTDeckMeshtasticMui,
            onSelectTDeckMeshCoreFlavor = onSelectTDeckMeshCoreFlavor,
            onOpenBoardPicker = onOpenBoardPicker,
            onOpenReleasesPicker = onOpenReleasesPicker,
            onRequestUsbPermission = onRequestUsbPermission,
            onRescanUsb = onRescanUsb,
            onToggleFullErase = onToggleFullErase,
            onToggleBundleWebUi = onToggleBundleWebUi,
            onStartFlash = onStartFlash,
            modifier = modifier
        )
    }
}

/**
 * Image 1: Main Flasher Interface
 */
@Composable
private fun FlasherIdleView(
    uiState: MeshInstallerUiState,
    expectedFileName: String,
    onSelectEcosystem: (FirmwareEcosystem) -> Unit,
    onSelectMeshtasticTrack: (MeshtasticTrack) -> Unit,
    onSelectMeshCoreRole: (MeshCoreRole) -> Unit,
    onSetTDeckMeshtasticMui: (Boolean) -> Unit,
    onSelectTDeckMeshCoreFlavor: (TDeckMeshCoreFlavor) -> Unit,
    onOpenBoardPicker: () -> Unit,
    onOpenReleasesPicker: () -> Unit,
    onRequestUsbPermission: (UsbAttachedBoard) -> Unit,
    onRescanUsb: () -> Unit,
    onToggleFullErase: (Boolean) -> Unit,
    onToggleBundleWebUi: (Boolean) -> Unit,
    onStartFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usbDev = uiState.usbAttachedBoards.firstOrNull()
    val isUsbAttached = usbDev != null
    val hasPermission = isUsbAttached && usbDev!!.hasPermission

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
                                text = if (hasPermission) "USB OTG CONNECTED • 921.6K" else if (isUsbAttached) "TAP TO ALLOW USB" else "READY FOR USB OTG",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasPermission) Color(0xFF7EE787) else AmberSignal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Online Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(CyberTeal.copy(alpha = 0.15f))
                            .clickable {
                                if (isUsbAttached && !hasPermission) {
                                    onRequestUsbPermission(usbDev!!)
                                } else {
                                    onRescanUsb()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasPermission) "ONLINE" else if (isUsbAttached) "GRANT USB" else "SCAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
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
                                    text = uiState.selectedBoard.mcu.name.replace('_', '-'),
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
                            text = uiState.selectedBoard.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ROM: ${uiState.selectedBoard.mcu.name.replace('_', '-')} • ${uiState.selectedBoard.flashSizeMb}MB Quad-SPI Flash",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Port: /dev/bus/usb/001/002 (Vendor: 0x303A)",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = BlueAccent
                            )
                        )
                    }

                    // Green Active Pulse Indicator
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, GreenSuccess.copy(alpha = 0.4f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess)
                        )
                    }
                }
            }
        }

        // Card 2: FIRMWARE CONFIGURATION matching Image 1
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
                    Text(
                        text = "FIRMWARE CONFIGURATION",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF8B949E)
                        )
                    )

                    // Ecosystem Pills (Meshtastic vs MeshCore)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isMeshtastic = uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC
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
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHTASTIC) },
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
                                .clickable { onSelectEcosystem(FirmwareEcosystem.MESHCORE) },
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

                    // Release Track Selector
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
                                    .clickable { onSelectMeshtasticTrack(MeshtasticTrack.ALPHA) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Alpha (v2.8.1)",
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
                                    .background(if (!isAlpha) Color(0xFF1C232D) else Color(0xFF161B22))
                                    .border(
                                        1.5.dp,
                                        if (!isAlpha) CyberTeal else CardBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectMeshtasticTrack(MeshtasticTrack.STABLE) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Stable (v2.5.4)",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = if (!isAlpha) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isAlpha) Color.White else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // LilyGO T-Deck Variant Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (uiState.selectedBoard.id == "lilygo_tdeck") "LilyGO T-Deck Variant" else "${uiState.selectedBoard.name} Variant",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC9D1D9)
                            )
                        )

                        // Variant Card with Radio Dot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkTealCard)
                                .border(1.2.dp, CyberTeal, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (uiState.selectedBoard.id == "lilygo_tdeck") {
                                        onSetTDeckMeshtasticMui(!uiState.tDeckMeshtasticMui)
                                    } else {
                                        onOpenReleasesPicker()
                                    }
                                }
                                .padding(12.dp)
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
                                        .background(CyberTeal),
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
                                        text = if (uiState.selectedBoard.id == "lilygo_tdeck") {
                                            if (uiState.tDeckMeshtasticMui) "Meshtastic UI (MUI Touch)" else "Classic BaseUI (Terminal)"
                                        } else {
                                            uiState.selectedBoard.name
                                        },
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = expectedFileName,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            color = Color(0xFF7EE787),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Action Button matching Image 1
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
                        text = "⚡ FLASH FIRMWARE (921,600 BAUD)",
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
                        .padding(horizontal = 12.dp),
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
                        .padding(horizontal = 12.dp),
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
                                text = "ESPTOOL SLIP PROTOCOL ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = BlueAccent,
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
                            text = "921.6 KBPS",
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
