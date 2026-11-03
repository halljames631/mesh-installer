package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.FirmwareEcosystem
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.HardwareChipBadge
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.EspOrange
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.TextMuted
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

    val batch = uiState.vaultBatchProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Card: Clear instruction that this is single-choice target selection
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Radio Hardware Catalog",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap any radio to select it as your active flashing target",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (batch.isDownloading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = batch.statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { batch.overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                // Batch Vault download button (kept for full offline batch functionality)
                Button(
                    onClick = onDownloadAllForSelected,
                    enabled = uiState.myDeviceIds.isNotEmpty() && !batch.isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("boards_tab_download_all_button")
                ) {
                    if (batch.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloading ${batch.currentIndex}/${batch.totalCount}...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sync Firmware for Checked Devices (${uiState.myDeviceIds.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = uiState.boardSearchQuery,
            onValueChange = onSetSearchQuery,
            placeholder = { Text("Search Heltec, RAK4631, T-Deck, T-Beam, T-Echo, T1000-E…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search boards",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("board_search_input")
        )

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
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("mcu_filter_all")
            )
            McuArchitecture.entries.forEach { mcu ->
                FilterChip(
                    selected = uiState.mcuFilter == mcu,
                    onClick = { onSetMcuFilter(if (uiState.mcuFilter == mcu) null else mcu) },
                    label = { Text(mcu.shortName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("mcu_filter_${mcu.name.lowercase()}")
                )
            }
        }

        // Radios List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("boards_catalog_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredBoards, key = { it.id }) { board ->
                val isSelected = uiState.selectedBoard.id == board.id
                val isCheckedForOffline = board.id in uiState.myDeviceIds
                val savedForBoard = uiState.offlineVaultEntries.filter { it.boardId == board.id }
                val expectedCount = if (board.id == "lilygo_tdeck") 5 else if (board.mcu == McuArchitecture.NRF52840 || board.mcu == McuArchitecture.RP2040) 5 else 6

                BoardCatalogItemCard(
                    board = board,
                    isSelected = isSelected,
                    isCheckedForOffline = isCheckedForOffline,
                    isDownloading = batch.isDownloading,
                    savedEntries = savedForBoard,
                    expectedCount = expectedCount,
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
    onSelect: () -> Unit,
    onToggleOfflineCheck: () -> Unit,
    onDownloadBoard: () -> Unit,
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit
) {
    val mcuColor = when (board.mcu) {
        McuArchitecture.NRF52840 -> VioletNrf
        McuArchitecture.RP2040 -> AmberSignal
        else -> EspOrange
    }
    val isFullySaved = savedEntries.size >= expectedCount

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("board_item_${board.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Single-Choice Radio Selection Indicator + Name + Active Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Single-choice radio icon: Only the ONE active target is filled!
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = BorderStroke(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onPrimary
                                ) {}
                            }
                        }
                    }

                    Column {
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${board.manufacturer} • ${board.usbBridge}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Show prominent ACTIVE TARGET badge ONLY on the one selected board
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "ACTIVE TARGET ✓",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    HardwareChipBadge(text = board.mcu.shortName, color = mcuColor)
                }
            }

            // Specs badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HardwareChipBadge(text = board.loraChip, color = AmberSignal)
                HardwareChipBadge(text = "${board.flashSizeMb}MB FLASH", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (board.hasGps) HardwareChipBadge(text = "GNSS / GPS", color = MeshtasticGreen)
                if (board.hasEInk) HardwareChipBadge(text = "E-INK", color = MeshCoreCyan)
                else if (board.hasOled) HardwareChipBadge(text = "OLED/TFT", color = MeshCoreCyan)
            }

            // Bottom Actions: Vault Cache Checkbox button + Direct Download
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Vault Cache Action (retains testTag for automated testing)
                Surface(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleOfflineCheck() }
                        .testTag("board_offline_checkbox_${board.id}"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCheckedForOffline) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isCheckedForOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCheckedForOffline) Icons.Default.Check else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (isCheckedForOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCheckedForOffline) "Vault: Saved" else "Vault: Cache",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCheckedForOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Download & Save Button
                Button(
                    onClick = onDownloadBoard,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFullySaved) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFullySaved) GreenSuccess else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("catalog_download_board_${board.id}")
                ) {
                    Icon(
                        imageVector = if (isFullySaved) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFullySaved) {
                            "Cached (${savedEntries.size}/$expectedCount)"
                        } else {
                            "Download ($expectedCount)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Saved firmware files list (if cached)
            if (savedEntries.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CACHED LOCAL FIRMWARE (TAP TO LOAD):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                        savedEntries.forEach { entry ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onUseSavedEntry(entry) },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${entry.ecosystem.displayName} • ${entry.variantLabel} (${entry.versionTag})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "USE LOCAL",
                                        style = MaterialTheme.typography.labelSmall,
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
