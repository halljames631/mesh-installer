package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionTransport
import com.example.model.ConsoleLogEntry
import com.example.model.LogLevel
import com.example.ui.MeshInstallerUiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleDarkCard
import com.example.ui.theme.TextMuted

@Composable
fun SerialConsoleScreen(
    uiState: MeshInstallerUiState,
    onSendCommand: (String) -> Unit,
    onTrigger1200BpsTouch: () -> Unit,
    onTriggerEspReset: () -> Unit,
    onClearLogs: () -> Unit,
    onStartSerialMonitor: (Int) -> Unit = {},
    onStopSerialMonitor: () -> Unit = {},
    onSelectBaudRate: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var cmdInput by remember { mutableStateOf("") }
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var baudDropdownOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val usbDev = uiState.usbAttachedBoards.firstOrNull()
    val isPhysicalUsbReady = (uiState.selectedTransport == ConnectionTransport.USB_OTG && usbDev != null && usbDev.hasPermission)
    val isConnected = isPhysicalUsbReady || uiState.loopbackTestBench

    DisposableEffect(isPhysicalUsbReady, uiState.selectedBaudRate) {
        if (isPhysicalUsbReady) {
            onStartSerialMonitor(uiState.selectedBaudRate)
        }
        onDispose {
            onStopSerialMonitor()
        }
    }

    LaunchedEffect(uiState.consoleLogs.size, autoScroll, isPaused) {
        if (autoScroll && !isPaused && uiState.consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.consoleLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top M3 Elevated Header Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = PurpleDarkCard
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
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
                        color = PurpleAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Serial Monitor",
                                tint = PurpleAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Serial Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "8N1 SERIAL • USB CDC STREAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PurpleAccent,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConnected) PurpleAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (isConnected) "CONNECTED" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) PurpleAccent else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Control Strip Card (Baud Dropdown, Auto-Scroll, Pause, Copy, Export, Clear)
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Baud Rate Dropdown
                Box {
                    AssistChip(
                        onClick = { baudDropdownOpen = true },
                        label = {
                            Text(
                                text = "${uiState.selectedBaudRate} ▾",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("baud_rate_dropdown_button")
                    )
                    DropdownMenu(
                        expanded = baudDropdownOpen,
                        onDismissRequest = { baudDropdownOpen = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        listOf(115200, 230400, 460800, 921600).forEach { baud ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$baud Baud",
                                        color = if (baud == uiState.selectedBaudRate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (baud == uiState.selectedBaudRate) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    baudDropdownOpen = false
                                    onSelectBaudRate(baud)
                                    onStartSerialMonitor(baud)
                                },
                                modifier = Modifier.testTag("baud_option_$baud")
                            )
                        }
                    }
                }

                // Auto-Scroll FilterChip
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = {
                        Text(
                            text = "Scroll",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("auto_scroll_toggle")
                )

                // Pause FilterChip
                FilterChip(
                    selected = isPaused,
                    onClick = { isPaused = !isPaused },
                    label = {
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("pause_logs_toggle")
                )

                // Copy Logs AssistChip
                AssistChip(
                    onClick = {
                        val text = uiState.consoleLogs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Serial Logs", text))
                    },
                    label = {
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("copy_console_logs_button")
                )

                // Export Logs AssistChip
                AssistChip(
                    onClick = {
                        val text = uiState.consoleLogs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Serial Logs"))
                    },
                    label = {
                        Text(
                            text = "Export",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("export_console_logs_button")
                )

                // Clear Button
                AssistChip(
                    onClick = { onClearLogs() },
                    label = {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("clear_console_logs_button")
                )
            }
        }

        // Serial Log Window Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color(0xFF0D1117)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (uiState.consoleLogs.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "// Serial stream listening on 8N1 interface...",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Plug in your radio or send a command below",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("serial_console_log_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.consoleLogs, key = { it.id }) { log ->
                            ConsoleLine(log = log)
                        }
                    }
                }
            }
        }

        // Quick-Action Command SuggestionChips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val chips = listOf("--info", "nodes", "reboot", "factory-reset", "advert", "dfu")
            chips.forEach { chip ->
                SuggestionChip(
                    onClick = { onSendCommand(chip) },
                    label = {
                        Text(
                            text = chip,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("chip_cmd_$chip")
                )
            }
        }

        // M3 OutlinedTextField & Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = {
                    Text(
                        text = "Enter command (e.g. reboot, --info)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = PurpleAccent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("serial_command_input")
            )

            Button(
                onClick = {
                    if (cmdInput.isNotBlank()) {
                        onSendCommand(cmdInput)
                        cmdInput = ""
                    }
                },
                enabled = cmdInput.isNotBlank(),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("send_serial_command_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurpleAccent,
                    contentColor = Color(0xFF0B0F14),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SEND",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ConsoleLine(log: ConsoleLogEntry) {
    val (tagColor, textColor) = when (log.level) {
        LogLevel.TX -> Pair(PurpleAccent, Color(0xFFBC8CFF))
        LogLevel.RX -> Pair(CyberCyan, Color(0xFF7EE787))
        LogLevel.SUCCESS -> Pair(GreenSuccess, Color(0xFF7EE787))
        LogLevel.WARN -> Pair(AmberSignal, Color(0xFFD29922))
        LogLevel.ERROR -> Pair(CoralRed, Color(0xFFF85149))
        LogLevel.INFO -> Pair(GreenSuccess, Color(0xFFE6EDF3))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${log.timestamp}]",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = BlueAccent
            ),
            modifier = Modifier.width(82.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = log.tag,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tagColor
            ),
            modifier = Modifier.width(56.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = log.message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = textColor
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
