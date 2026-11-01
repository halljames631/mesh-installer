package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.BoardSchematicBlueprint
import com.example.ui.components.FlashPartitionMemoryBar
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FlasherAdvancedToolsSection(
    uiState: MeshInstallerUiState,
    ecoColor: Color,
    roleOrTrackBadge: String,
    onSelectBaudRate: (Int) -> Unit,
    onToggleBundleWebUi: (Boolean) -> Unit,
    onToggleLoopbackBench: (Boolean) -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("toggle_advanced_tools_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = ecoColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Advanced Options & Hardware Map",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "PCB schematic, memory partitions, baud speed (${uiState.selectedBaudRate}) & test bench",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide advanced options" else "Show advanced options",
                tint = TextSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HorizontalDivider(color = SlateBorder)

                // Interactive PCB Schematic Blueprint
                BoardSchematicBlueprint(
                    board = uiState.selectedBoard,
                    ecosystem = uiState.selectedEcosystem,
                    roleOrTrackBadge = roleOrTrackBadge,
                    transport = uiState.selectedTransport,
                    flashState = uiState.flashProgress
                )

                // Flash Partition Layout Bar
                FlashPartitionMemoryBar(
                    board = uiState.selectedBoard,
                    bundleWebUi = uiState.bundleWebUiPartition,
                    flashState = uiState.flashProgress,
                    ecosystem = uiState.selectedEcosystem
                )

                // Manual Bootloader Signal Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Manual Bootloader Reset Signals",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "If your board does not enter flash mode automatically, use these buttons before flashing:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTriggerEspReset,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_esp_reset_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "ESP32 Bootloader Reset",
                                modifier = Modifier.size(16.dp),
                                tint = ecoColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ESP32 Boot Reset", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onTrigger1200BpsTouch,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_1200bps_dfu_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "1200bps UF2 Bootloader Touch",
                                modifier = Modifier.size(16.dp),
                                tint = AmberSignal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("nRF52 / UF2 Mode", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // USB Serial Speed (Baud Rate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USB Flash Speed (Baud Rate)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Lower to 115200 if your USB cable has connection drops",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(115200, 460800, 921600).forEach { baud ->
                            val active = uiState.selectedBaudRate == baud
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) ecoColor.copy(alpha = 0.2f) else ObsidianBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (active) ecoColor else SlateBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectBaudRate(baud) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("baud_$baud")
                            ) {
                                Text(
                                    text = "$baud",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) ecoColor else TextSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SlateBorder)

                // Include WebUI / LittleFS Partition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include WebUI & File System Partition",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Flashes onboard LittleFS storage (${uiState.selectedBoard.partitions.lastOrNull()?.addressHex ?: "0x300000"}) during full installs",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.bundleWebUiPartition,
                        onCheckedChange = onToggleBundleWebUi,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = ecoColor
                        ),
                        modifier = Modifier.testTag("bundle_webui_switch")
                    )
                }

                HorizontalDivider(color = SlateBorder)

                // Hardware Loopback / Dry-Run Protocol Bench Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demo / Dry-Run Mode (No Radio Required)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Test firmware downloads & packet encoding without a physical radio plugged in",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.loopbackTestBench,
                        onCheckedChange = onToggleLoopbackBench,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = AmberSignal
                        ),
                        modifier = Modifier.testTag("loopback_bench_switch")
                    )
                }
            }
        }
    }
}
