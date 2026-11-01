package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.EspOrange
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNrf
import java.util.Locale

@Composable
fun BoardsCatalogScreen(
    uiState: MeshInstallerUiState,
    onSelectBoard: (HardwareBoard) -> Unit,
    onSetMcuFilter: (McuArchitecture?) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onToggleMyDevice: (String) -> Unit = {},
    onDownloadAllForSelected: () -> Unit = {},
    onDownloadSingleBoard: (String) -> Unit = {},
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ecoColor = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        MeshtasticGreen
    } else {
        MeshCoreCyan
    }

    val filteredBoards = remember(uiState.mcuFilter, uiState.boardSearchQuery) {
        BoardCatalog.allBoards.filter { board ->
            val matchesMcu = uiState.mcuFilter == null || board.mcu == uiState.mcuFilter
            val q = uiState.boardSearchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                board.name.lowercase().contains(q) ||
                board.manufacturer.lowercase().contains(q) ||
                board.loraChip.lowercase().contains(q) ||
                board.meshtasticTarget.lowercase().contains(q)
            matchesMcu && matchesQuery
        }
    }

    val selectedBoards = BoardCatalog.allBoards.filter { it.id in uiState.myDeviceIds }
    val totalPlannedImages = selectedBoards.sumOf { board ->
        if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6
    }
    val totalSavedMb = uiState.offlineVaultEntries.sumOf { it.sizeBytes } / (1024f * 1024f)
    val batch = uiState.vaultBatchProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Off-Grid Multi-Device Check & Download Banner right at the top of Radios tab!
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SlateCard)
                .border(1.dp, MeshtasticGreen.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OFF-GRID LOCAL FIRMWARE DOWNLOADER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeshtasticGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (batch.isDownloading) {
                            batch.statusDetail
                        } else if (uiState.offlineVaultEntries.isNotEmpty()) {
                            "${uiState.offlineVaultEntries.size} firmware files saved locally (%.1f MB) • Ready for local offline use".format(
                                Locale.US,
                                totalSavedMb
                            )
                        } else {
                            "Check the radios you use below, then tap Download & Save for Local Use"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                HardwareChipBadge(
                    text = "${uiState.myDeviceIds.size} CHECKED",
                    color = MeshCoreCyan
                )
            }

            if (batch.isDownloading) {
                LinearProgressIndicator(
                    progress = { batch.overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MeshtasticGreen,
                    trackColor = ObsidianBg
                )
            }

            Button(
                onClick = onDownloadAllForSelected,
                enabled = uiState.myDeviceIds.isNotEmpty() && !batch.isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshtasticGreen,
                    contentColor = Color(0xFF002612)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("boards_tab_download_all_button")
            ) {
                if (batch.isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF002612)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading ${batch.currentIndex}/${batch.totalCount} (${(batch.overallProgress * 100).toInt()}%)...",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.myDeviceIds.isEmpty()) {
                            "Check Radios Below to Download Firmware"
                        } else {
                            "Download & Save Checked Radios Locally ($totalPlannedImages Files)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.boardSearchQuery,
            onValueChange = onSetSearchQuery,
            placeholder = { Text("Search Heltec, RAK4631, T-Deck, T-Beam, T-Echo, T1000-E…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search boards"
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("board_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // MCU Architecture Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.mcuFilter == null,
                onClick = { onSetMcuFilter(null) },
                label = { Text("All (${BoardCatalog.allBoards.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                    selectedLabelColor = ecoColor
                ),
                modifier = Modifier.testTag("mcu_filter_all")
            )
            McuArchitecture.entries.forEach { mcu ->
                FilterChip(
                    selected = uiState.mcuFilter == mcu,
                    onClick = { onSetMcuFilter(if (uiState.mcuFilter == mcu) null else mcu) },
                    label = { Text(mcu.shortName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                        selectedLabelColor = ecoColor
                    ),
                    modifier = Modifier.testTag("mcu_filter_${mcu.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("boards_catalog_list"),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredBoards, key = { it.id }) { board ->
                val isCheckedForOffline = board.id in uiState.myDeviceIds
                val savedForBoard = uiState.offlineVaultEntries.filter { it.boardId == board.id }
                val expectedCount = if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6

                BoardCatalogItemCard(
                    board = board,
                    isSelected = uiState.selectedBoard.id == board.id,
                    isCheckedForOffline = isCheckedForOffline,
                    isDownloading = batch.isDownloading,
                    savedEntries = savedForBoard,
                    expectedCount = expectedCount,
                    ecoColor = ecoColor,
                    onSelect = { onSelectBoard(board) },
                    onToggleOfflineCheck = { onToggleMyDevice(board.id) },
                    onDownloadBoard = { onDownloadSingleBoard(board.id) },
                    onUseSavedEntry = onUseSavedEntry
                )
            }
        }
    }
}

@Composable
private fun BoardCatalogItemCard(
    board: HardwareBoard,
    isSelected: Boolean,
    isCheckedForOffline: Boolean,
    isDownloading: Boolean,
    savedEntries: List<CachedFirmwareEntry>,
    expectedCount: Int,
    ecoColor: Color,
    onSelect: () -> Unit,
    onToggleOfflineCheck: () -> Unit,
    onDownloadBoard: () -> Unit,
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val mcuColor = when (board.mcu) {
        McuArchitecture.NRF52840 -> VioletNrf
        McuArchitecture.RP2040 -> AmberSignal
        else -> EspOrange
    }
    val isFullySaved = savedEntries.size >= expectedCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCard)
            .border(
                width = if (isSelected || isCheckedForOffline) 1.8.dp else 1.dp,
                color = when {
                    isFullySaved -> MeshtasticGreen
                    isSelected -> ecoColor
                    isCheckedForOffline -> MeshCoreCyan
                    else -> SlateBorder
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp)
            .testTag("board_item_${board.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isCheckedForOffline) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isCheckedForOffline) "Checked for Offline" else "Check for Offline",
                    tint = if (isCheckedForOffline) MeshtasticGreen else TextMuted,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleOfflineCheck() }
                        .testTag("board_offline_checkbox_${board.id}")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Board",
                                tint = ecoColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "${board.manufacturer} • ${board.usbBridge}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            HardwareChipBadge(text = board.mcu.shortName, color = mcuColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HardwareChipBadge(text = board.loraChip, color = AmberSignal)
            HardwareChipBadge(text = "${board.flashSizeMb}MB FLASH", color = TextSecondary)
            if (board.hasGps) HardwareChipBadge(text = "GNSS / GPS", color = MeshtasticGreen)
            if (board.hasEInk) HardwareChipBadge(text = "E-INK", color = MeshCoreCyan)
            else if (board.hasOled) HardwareChipBadge(text = "OLED/TFT", color = MeshCoreCyan)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Direct Per-Board Download & Save Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onToggleOfflineCheck,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isCheckedForOffline) "Checked for Offline" else "Check Radio",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCheckedForOffline) MeshtasticGreen else TextSecondary
                )
            }

            Button(
                onClick = onDownloadBoard,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFullySaved) SlateSurface else MeshtasticGreen,
                    contentColor = if (isFullySaved) MeshtasticGreen else Color(0xFF002612)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("catalog_download_board_${board.id}")
            ) {
                Icon(
                    imageVector = if (isFullySaved) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFullySaved) {
                        "Saved Locally (${savedEntries.size}/$expectedCount)"
                    } else {
                        "Download & Save ($expectedCount Files)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Show Saved Local Firmware Files with "Use Local" button
        if (savedEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "SAVED LOCAL FIRMWARE FILES (TAP TO USE LOCAL):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshtasticGreen,
                    fontWeight = FontWeight.Bold
                )
                savedEntries.sortedWith(
                    compareBy<CachedFirmwareEntry> { it.ecosystem != FirmwareEcosystem.MESHTASTIC }
                        .thenBy { it.variantLabel }
                ).forEach { entry ->
                    val accent = if (entry.ecosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateCard)
                            .clickable { onUseSavedEntry(entry) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.variantLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${entry.fileName} (${entry.sizeBytes / 1024} KB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Use Local",
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Targets: ${board.meshtasticTarget} / ${board.meshCoreTarget}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("board_pinout_toggle_${board.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Bootloader & Pinout Info",
                    tint = ecoColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Hide Pinout" else "Bootloader & Pins",
                    style = MaterialTheme.typography.labelSmall,
                    color = ecoColor
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ecoColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .padding(10.dp)
            ) {
                Text(
                    text = "BOOTLOADER & DFU RECOVERY GUIDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberSignal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = board.bootloaderGuide,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "HARDWARE GPIO & RF PINOUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = ecoColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = board.pinoutSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!isSelected) {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ecoColor,
                            contentColor = Color(0xFF002211)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use ${board.name} in Flasher", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
