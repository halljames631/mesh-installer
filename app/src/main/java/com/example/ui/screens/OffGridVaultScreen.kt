package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.BoardCatalog
import com.example.model.CachedFirmwareEntry
import com.example.model.FirmwareEcosystem
import com.example.model.HardwareBoard
import com.example.ui.MeshInstallerUiState
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardDarkBg
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.DarkTealCard
import com.example.ui.theme.DeepDarkBg
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VaultNavyBorder
import com.example.ui.theme.VaultNavyHeader
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OffGridVaultScreen(
    uiState: MeshInstallerUiState,
    onSelectBoard: (HardwareBoard) -> Unit,
    onDownloadAllForSelected: () -> Unit,
    onDownloadSingleBoard: (String) -> Unit = {},
    onUseSavedEntry: (CachedFirmwareEntry) -> Unit,
    onToggleMyDevice: (String) -> Unit,
    onDeleteBoardEntries: (String) -> Unit = {},
    onClearVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedCount = uiState.offlineVaultEntries.size
    val isDownloading = uiState.vaultBatchProgress.isDownloading
    val context = LocalContext.current

    fun shareFirmwareEntry(entry: CachedFirmwareEntry) {
        val file = File(entry.localFilePath)
        if (file.exists()) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, entry.fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export / Share Firmware"))
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Card matching Image 4
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(VaultNavyHeader, Color(0xFF1C354D))
                        )
                    )
                    .border(1.dp, VaultNavyBorder, RoundedCornerShape(14.dp))
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
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Offline Vault",
                                tint = BlueAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Offline Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val totalBytes = uiState.offlineVaultEntries.sumOf { it.sizeBytes }
                            val totalMb = totalBytes / (1024f * 1024f)
                            Text(
                                text = "Vault Storage: %.1f MB (%d binaries cached)".format(Locale.US, totalMb, savedCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberCyan,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Count Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(GreenSuccess.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (savedCount > 0) "$savedCount READY" else "VAULT READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }
        }

        // Info Banner matching Image 4
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkTealCard)
                    .border(1.dp, CyberTeal.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isDownloading) {
                        uiState.vaultBatchProgress.statusDetail.ifEmpty { "Downloading firmwares to local vault..." }
                    } else {
                        "All saved firmwares available offline in the wilderness"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7EE787),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Device Cards matching Image 4 with live Meshtastic + MeshCore cached firmware badges
        items(BoardCatalog.allBoards, key = { it.id }) { board ->
            val cachedEntries = uiState.offlineVaultEntries.filter { it.boardId == board.id }
            val isCached = cachedEntries.isNotEmpty()
            val cachedBytes = cachedEntries.sumOf { it.sizeBytes }
            val cachedMb = cachedBytes / (1024f * 1024f)
            val isInMyDevices = board.id in uiState.myDeviceIds

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDarkBg)
                    .border(
                        1.dp,
                        if (isInMyDevices) CyberTeal.copy(alpha = 0.45f) else CardBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectBoard(board) }
                    .padding(16.dp)
                    .testTag("vault_board_card_${board.id}")
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
                            // My Device toggle checkbox pill
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isInMyDevices) CyberTeal else Color(0xFF1C232D))
                                    .border(1.dp, if (isInMyDevices) CyberCyan else CardBorder, RoundedCornerShape(5.dp))
                                    .clickable { onToggleMyDevice(board.id) }
                                    .testTag("vault_toggle_device_${board.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isInMyDevices) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected in My Devices",
                                        tint = DeepDarkBg,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = board.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${board.mcu.shortName} • ${board.flashSizeMb}MB Flash • ${board.loraChip}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Badges Row: Shows real cached firmware chips (tappable to load!) or default target badges
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (cachedEntries.isNotEmpty()) {
                                cachedEntries.forEach { entry ->
                                    val isMeshtasticEntry = entry.ecosystem == FirmwareEcosystem.MESHTASTIC
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isMeshtasticEntry) DarkTealCard else Color(0xFF1C232D))
                                                .border(
                                                    1.dp,
                                                    if (isMeshtasticEntry) CyberTeal else BlueAccent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { onUseSavedEntry(entry) }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                                .testTag("use_saved_entry_${entry.key}")
                                        ) {
                                            Text(
                                                text = "${entry.ecosystem.displayName} ${entry.variantLabel} (${entry.versionTag})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMeshtasticEntry) CyberCyan else BlueAccent
                                            )
                                        }

                                        // Export / Share Button
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1C232D))
                                                .border(1.dp, CardBorder, CircleShape)
                                                .clickable { shareFirmwareEntry(entry) }
                                                .testTag("share_entry_${entry.key}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Export / Share ${entry.fileName}",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (board.id == "lilygo_tdeck") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkTealCard)
                                        .border(1.dp, CyberTeal, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Meshtastic MUI & BaseUI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1F2833))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "MeshCore Ripple & WadaMesh",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                            } else if (board.mcu.name.startsWith("NRF52")) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1C232D))
                                        .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Meshtastic (${board.meshtasticTarget}) • MeshCore (${board.meshCoreTarget})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PurpleAccent
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1C232D))
                                        .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Meshtastic (${board.meshtasticTarget}) • MeshCore (${board.meshCoreTarget})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueAccent
                                    )
                                }
                            }

                            // Cached / Download Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GreenSuccess.copy(alpha = 0.25f))
                                    .clickable { onDownloadSingleBoard(board.id) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("vault_download_board_${board.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!isCached) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Download board firmwares",
                                            tint = GreenSuccess,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isCached) {
                                            "CACHED (%.1f MB)".format(Locale.US, cachedMb)
                                        } else {
                                            "SYNC BOARD"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenSuccess
                                    )
                                }
                            }

                            // Delete Board Cache Set Button
                            if (isCached) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CoralRed.copy(alpha = 0.2f))
                                        .border(1.dp, CoralRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .clickable { onDeleteBoardEntries(board.id) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                        .testTag("delete_board_vault_${board.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete cache for ${board.name}",
                                            tint = CoralRed,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "DELETE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralRed
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action chevron button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (board.id == "lilygo_tdeck") CyberTeal.copy(alpha = 0.15f) else BlueAccent.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Select ${board.name}",
                            tint = if (board.id == "lilygo_tdeck") CyberCyan else BlueAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Bottom Action Button: SYNC & UPDATE ALL FIRMWARES
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onDownloadAllForSelected,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("vault_sync_all_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueAccent,
                    contentColor = Color.White
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "DOWNLOADING... (${uiState.vaultBatchProgress.currentIndex}/${uiState.vaultBatchProgress.totalCount})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYNC & UPDATE ALL FIRMWARES",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
