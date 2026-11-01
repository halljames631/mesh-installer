package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OfflinePin
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.FirmwareEcosystem
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.HardwareChipBadge
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun OffGridVaultDialog(
    uiState: MeshInstallerUiState,
    onToggleMyDevice: (String) -> Unit,
    onSelectPopular: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadAllForSelected: () -> Unit,
    onDownloadSingleBoard: (String) -> Unit = {},
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit = {},
    onCancelDownload: () -> Unit,
    onClearVault: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val batch = uiState.vaultBatchProgress
    val totalSavedBytes = uiState.offlineVaultEntries.sumOf { it.sizeBytes }
    val totalSavedMb = totalSavedBytes / (1024f * 1024f)

    // Calculate how many firmware images will be downloaded for the checked boards
    val selectedBoards = BoardCatalog.allBoards.filter { it.id in uiState.myDeviceIds }
    val totalPlannedImages = selectedBoards.sumOf { board ->
        if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("offgrid_vault_dialog"),
        color = ObsidianBg
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeshtasticGreen.copy(alpha = 0.16f))
                            .border(1.dp, MeshtasticGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = "Off-Grid Firmware Vault",
                            tint = MeshtasticGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Off-Grid Local Firmware Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Check your radios below, then tap Download & Save for Local Use",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_offgrid_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Off-Grid Vault",
                        tint = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = SlateBorder.copy(alpha = 0.7f))

            // Sticky Top Action & Selection Bar (Always Visible Right Below Header!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HardwareChipBadge(
                            text = "${uiState.myDeviceIds.size} RADIOS CHECKED",
                            color = MeshCoreCyan
                        )
                        HardwareChipBadge(
                            text = "${uiState.offlineVaultEntries.size} SAVED LOCALLY (%.1f MB)".format(Locale.US, totalSavedMb),
                            color = if (uiState.offlineVaultEntries.isNotEmpty()) MeshtasticGreen else TextSecondary
                        )
                    }
                    if (uiState.offlineVaultEntries.isNotEmpty() && !batch.isDownloading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onClearVault() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Offline Vault",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Quick Preset Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.myDeviceIds.size == 4 &&
                            uiState.myDeviceIds.containsAll(setOf("lilygo_tdeck", "station_g2", "heltec_v3", "rak4631")),
                        onClick = onSelectPopular,
                        label = { Text("Popular Kit (4)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeshtasticGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MeshtasticGreen
                        ),
                        modifier = Modifier.testTag("offgrid_select_popular")
                    )
                    FilterChip(
                        selected = uiState.myDeviceIds.size == BoardCatalog.allBoards.size,
                        onClick = onSelectAll,
                        label = { Text("Check All (${BoardCatalog.allBoards.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeshCoreCyan.copy(alpha = 0.2f),
                            selectedLabelColor = MeshCoreCyan
                        ),
                        modifier = Modifier.testTag("offgrid_select_all")
                    )
                    FilterChip(
                        selected = uiState.myDeviceIds.isEmpty(),
                        onClick = onClearSelection,
                        label = { Text("Uncheck All") },
                        modifier = Modifier.testTag("offgrid_clear_selection")
                    )
                }

                // PROMINENT TOP DOWNLOAD & SAVE BUTTON (Never hidden or cut off!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (batch.isDownloading) {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("top_cancel_offline_download_button")
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AmberSignal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Downloading ${batch.currentIndex}/${batch.totalCount} (${(batch.overallProgress * 100).toInt()}%) • Tap to Stop",
                                color = AmberSignal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onDownloadAllForSelected,
                            enabled = uiState.myDeviceIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeshtasticGreen,
                                contentColor = Color(0xFF002612)
                            ),
                            modifier = Modifier
                                .weight(1.8f)
                                .height(48.dp)
                                .testTag("top_download_all_offline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.myDeviceIds.isEmpty()) {
                                    "Check Radios Below to Download"
                                } else {
                                    "Download & Save Selected ($totalPlannedImages Files)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(48.dp)
                                .testTag("top_done_offgrid_button")
                        ) {
                            Text(
                                text = if (uiState.offlineVaultEntries.isNotEmpty()) "Done" else "Close",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Batch Download Progress Banner (when active or recently completed)
            if (batch.isDownloading || batch.statusDetail.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateElevated)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
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
                            if (batch.isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MeshtasticGreen
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Complete",
                                    tint = MeshtasticGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = batch.statusDetail,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (batch.isDownloading) {
                            Text(
                                text = "${(batch.overallProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MeshtasticGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { batch.overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MeshtasticGreen,
                        trackColor = SlateSurface
                    )
                }
            }

            HorizontalDivider(color = SlateBorder.copy(alpha = 0.7f))

            // Scrollable List of All Radios + Per-Card Download & Local File Buttons
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "CHECK YOUR RADIOS TO DOWNLOAD & SAVE MESHTASTIC + MESHCORE LOCALLY:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshtasticGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(BoardCatalog.allBoards, key = { it.id }) { board ->
                    val isSelected = board.id in uiState.myDeviceIds
                    val savedForBoard = uiState.offlineVaultEntries.filter { it.boardId == board.id }
                    val expectedCount = if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6

                    OffGridBoardSelectionCard(
                        board = board,
                        isSelected = isSelected,
                        isDownloading = batch.isDownloading,
                        savedEntries = savedForBoard,
                        expectedCount = expectedCount,
                        onToggle = { onToggleMyDevice(board.id) },
                        onDownloadBoard = { onDownloadSingleBoard(board.id) },
                        onUseSavedEntry = onUseSavedEntry
                    )
                }
            }

            HorizontalDivider(color = SlateBorder.copy(alpha = 0.7f))

            // Sticky Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (batch.isDownloading) {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cancel_offline_download_button")
                    ) {
                        Text("Pause Download", color = AmberSignal, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDownloadAllForSelected,
                        enabled = uiState.myDeviceIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeshtasticGreen,
                            contentColor = Color(0xFF002612)
                        ),
                        modifier = Modifier
                            .weight(1.7f)
                            .height(48.dp)
                            .testTag("download_all_offline_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download & Save All ($totalPlannedImages Files)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("done_offgrid_button")
                    ) {
                        Text(
                            text = if (uiState.offlineVaultEntries.isNotEmpty()) "Use Local Files" else "Back",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OffGridBoardSelectionCard(
    board: HardwareBoard,
    isSelected: Boolean,
    isDownloading: Boolean,
    savedEntries: List<CachedFirmwareEntry>,
    expectedCount: Int,
    onToggle: () -> Unit,
    onDownloadBoard: () -> Unit,
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit
) {
    val savedCount = savedEntries.size
    val isFullySaved = savedCount >= expectedCount
    val borderColor = when {
        isFullySaved -> MeshtasticGreen
        isSelected -> MeshCoreCyan
        else -> SlateBorder
    }

    val includedFirmwareDesc = if (board.id == "lilygo_tdeck") {
        "Meshtastic (MUI + BaseUI) • MeshCore (Ripple Ultra UI + WadaMesh)"
    } else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) {
        "Meshtastic UF2 • MeshCore (Companion BLE, Companion USB, Repeater, Room Server)"
    } else {
        "Meshtastic (Factory + Update) • MeshCore (Companion BLE, Companion USB, Repeater, Room Server)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SlateElevated else SlateCard)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(12.dp)
            .testTag("offgrid_board_${board.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Checked" else "Not checked",
                tint = if (isSelected) MeshtasticGreen else TextMuted,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = board.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    HardwareChipBadge(
                        text = board.mcu.shortName,
                        color = TextSecondary
                    )
                }

                Text(
                    text = includedFirmwareDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            HardwareChipBadge(
                text = when {
                    isFullySaved -> "SAVED ($savedCount/$expectedCount)"
                    savedCount > 0 -> "$savedCount/$expectedCount SAVED"
                    isSelected -> "$expectedCount FILES"
                    else -> "CHECK TO ADD"
                },
                color = when {
                    isFullySaved -> MeshtasticGreen
                    savedCount > 0 -> AmberSignal
                    isSelected -> MeshCoreCyan
                    else -> TextMuted
                }
            )
        }

        // Direct Per-Card "Download & Save This Radio" Button whenever checked or partially saved!
        if (isSelected && !isDownloading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFullySaved) {
                        "All $savedCount Meshtastic + MeshCore files saved on phone for local offline flashing:"
                    } else {
                        "Checked — tap to download & save Meshtastic + MeshCore ($expectedCount files) for ${board.name}:"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFullySaved) MeshtasticGreen else MeshCoreCyan,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDownloadBoard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFullySaved) SlateSurface else MeshtasticGreen,
                        contentColor = if (isFullySaved) MeshtasticGreen else Color(0xFF002612)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("download_single_board_${board.id}")
                ) {
                    Icon(
                        imageVector = if (isFullySaved) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isFullySaved) "Re-Save ($expectedCount)" else "Download & Save ($expectedCount)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Saved Local Firmware Files for this Board — tap any file to load it for local flashing!
        if (savedEntries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SAVED LOCAL FIRMWARE FILES (TAP 'USE LOCAL' TO LOAD INTO FLASHER):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshtasticGreen,
                    fontWeight = FontWeight.Bold
                )
                savedEntries.sortedWith(
                    compareBy<CachedFirmwareEntry> { it.ecosystem != FirmwareEcosystem.MESHTASTIC }
                        .thenBy { it.variantLabel }
                ).forEach { entry ->
                    val ecoAccent = if (entry.ecosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateCard)
                            .clickable { onUseSavedEntry(entry) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("use_saved_entry_${entry.key}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.variantLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = ecoAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${entry.fileName} • ${entry.sizeBytes / 1024} KB • Saved on phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ecoAccent.copy(alpha = 0.18f))
                                .border(1.dp, ecoAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Use Local Firmware",
                                tint = ecoAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = ecoAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
