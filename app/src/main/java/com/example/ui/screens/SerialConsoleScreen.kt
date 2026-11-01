package com.example.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardDarkBg
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.DarkTealCard
import com.example.ui.theme.DeepDarkBg
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleDarkBorder
import com.example.ui.theme.PurpleDarkCard
import com.example.ui.theme.PurpleDarkHeader
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val usbDev = uiState.usbAttachedBoards.firstOrNull()
    val isConnected = (uiState.selectedTransport == ConnectionTransport.USB_OTG && usbDev != null && usbDev.hasPermission) || uiState.loopbackTestBench

    LaunchedEffect(uiState.consoleLogs.size, autoScroll, isPaused) {
        if (autoScroll && !isPaused && uiState.consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.consoleLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Card matching Image 3
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(PurpleDarkHeader, PurpleDarkCard)
                    )
                )
                .border(1.dp, PurpleDarkBorder, RoundedCornerShape(14.dp))
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
                            .background(PurpleAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ">_",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurpleAccent
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Serial Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
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

                // Connected Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isConnected) PurpleAccent.copy(alpha = 0.2f) else CoralRed.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConnected) "CONNECTED" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) PurpleAccent else CoralRed
                    )
                }
            }
        }

        // Control Strip matching Image 3 (Baud, Auto-Scroll, Pause, Clear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardDarkBg)
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Baud Rate Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${uiState.selectedBaudRate} Baud ▾",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BlueAccent
                )
            }

            // Auto-Scroll Toggle Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (autoScroll) DarkTealCard else Color(0xFF21262D))
                    .border(
                        1.dp,
                        if (autoScroll) CyberTeal else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { autoScroll = !autoScroll }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("auto_scroll_toggle")
            ) {
                Text(
                    text = "Auto-Scroll",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (autoScroll) CyberCyan else TextSecondary
                )
            }

            // Pause Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPaused) AmberSignal.copy(alpha = 0.2f) else Color(0xFF21262D))
                    .border(
                        1.dp,
                        if (isPaused) AmberSignal else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { isPaused = !isPaused }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("pause_logs_toggle")
            ) {
                Text(
                    text = if (isPaused) "Resume" else "Pause",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPaused) AmberSignal else TextSecondary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Clear Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
                    .clickable { onClearLogs() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("clear_console_logs_button")
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CoralRed
                )
            }
        }

        // Serial Log Window matching Image 3
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D1117))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(14.dp))
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
                        color = TextSecondary
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

        // Input Field & Send Button matching Image 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardDarkBg)
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (cmdInput.isEmpty()) {
                    Text(
                        text = "Enter command (e.g. reboot, --info)...",
                        style = TextStyle(fontSize = 12.sp),
                        color = TextMuted
                    )
                }
                BasicTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(PurpleAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("serial_command_input")
                )
            }

            Button(
                onClick = {
                    if (cmdInput.isNotBlank()) {
                        onSendCommand(cmdInput)
                        cmdInput = ""
                    }
                },
                modifier = Modifier
                    .height(44.dp)
                    .width(76.dp)
                    .testTag("send_serial_command_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurpleAccent,
                    contentColor = Color(0xFF0B0F14)
                ),
                contentPadding = PaddingValues()
            ) {
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
        // Blue timestamp
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

        // Tag
        Text(
            text = log.tag,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tagColor
            ),
            modifier = Modifier.width(52.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Message
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
