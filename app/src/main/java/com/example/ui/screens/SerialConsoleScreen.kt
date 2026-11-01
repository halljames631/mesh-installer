package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConsoleLogEntry
import com.example.model.FirmwareEcosystem
import com.example.model.LogLevel
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.HardwareChipBadge
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.CoralError
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class ConsoleFilter(val label: String) {
    ALL("All Logs"),
    ERRORS("Errors & Warnings"),
    SERIAL("Radio TX / RX")
}

@Composable
fun SerialConsoleScreen(
    uiState: MeshInstallerUiState,
    onSendCommand: (String) -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cmdInput by remember { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ConsoleFilter.ALL) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val ecoColor = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        MeshtasticGreen
    } else {
        MeshCoreCyan
    }

    val filteredLogs = remember(uiState.consoleLogs, selectedFilter) {
        when (selectedFilter) {
            ConsoleFilter.ALL -> uiState.consoleLogs
            ConsoleFilter.ERRORS -> uiState.consoleLogs.filter {
                it.level == LogLevel.ERROR || it.level == LogLevel.WARN
            }
            ConsoleFilter.SERIAL -> uiState.consoleLogs.filter {
                it.level == LogLevel.TX || it.level == LogLevel.RX
            }
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Install Logs & Radio Console",
                    tint = ecoColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "INSTALL LOGS & RADIO CLI",
                        style = MaterialTheme.typography.labelMedium,
                        color = ecoColor
                    )
                    Text(
                        text = "${uiState.selectedBoard.name} • ${uiState.selectedBaudRate} Baud",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val text = filteredLogs.joinToString("\n") {
                            "[${it.timestamp}] [${it.tag}] ${it.message}"
                        }
                        clipboardManager.setText(AnnotatedString(text))
                    },
                    modifier = Modifier.testTag("copy_console_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Logs",
                        tint = TextSecondary
                    )
                }

                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_console_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Console Logs",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Filter Chips (All Logs, Errors & Warnings, Radio TX/RX)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConsoleFilter.entries.forEach { filter ->
                val active = selectedFilter == filter
                FilterChip(
                    selected = active,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ecoColor.copy(alpha = 0.2f),
                        selectedLabelColor = ecoColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick CLI & Bootloader Macros
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickCommands = listOf(
                "ver" to "Check Version",
                "info" to "Radio Info",
                "advert" to "Send Ping",
                "help" to "CLI Help",
                "reboot" to "Reboot Radio"
            )
            quickCommands.forEach { (cmd, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { onSendCommand(cmd) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("cli_macro_$cmd")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = ecoColor
                    )
                }
            }

            OutlinedButton(
                onClick = onTrigger1200BpsTouch,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AmberSignal
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("UF2 Mode", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onTriggerEspReset,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = ecoColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("ESP Boot Reset", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Terminal Output Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianBg)
                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    text = "No matching logs yet. Start an install or tap a command above.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("serial_console_log_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        ConsoleLogRow(entry = entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Serial CLI Command Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("Type a command (ver, info, reboot)…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("serial_command_input")
            )
            IconButton(
                onClick = {
                    if (cmdInput.isNotBlank()) {
                        onSendCommand(cmdInput)
                        cmdInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ecoColor)
                    .testTag("send_serial_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Serial Command",
                    tint = ObsidianBg
                )
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: ConsoleLogEntry) {
    val badgeColor = when (entry.level) {
        LogLevel.INFO -> TextSecondary
        LogLevel.TX -> MeshCoreCyan
        LogLevel.RX -> AmberSignal
        LogLevel.WARN -> AmberSignal
        LogLevel.SUCCESS -> MeshtasticGreen
        LogLevel.ERROR -> CoralError
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(68.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        HardwareChipBadge(text = entry.tag, color = badgeColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelMedium,
            color = when (entry.level) {
                LogLevel.ERROR -> CoralError
                LogLevel.SUCCESS -> MeshtasticGreen
                LogLevel.TX -> MeshCoreCyan
                else -> TextPrimary
            },
            fontWeight = if (entry.level == LogLevel.SUCCESS || entry.level == LogLevel.ERROR) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            modifier = Modifier.weight(1f)
        )
    }
}
