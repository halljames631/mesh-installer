package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionTransport
import com.example.model.FirmwareEcosystem
import com.example.model.FlashPhase
import com.example.model.FlashProgressState
import com.example.model.HardwareBoard
import com.example.model.McuArchitecture
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.EspOrange
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNrf

@Composable
fun BoardSchematicBlueprint(
    board: HardwareBoard,
    ecosystem: FirmwareEcosystem,
    roleOrTrackBadge: String,
    transport: ConnectionTransport,
    flashState: FlashProgressState,
    modifier: Modifier = Modifier
) {
    val isFlashing = flashState.phase != FlashPhase.IDLE &&
        flashState.phase != FlashPhase.COMPLETED &&
        flashState.phase != FlashPhase.FAILED

    val infiniteTransition = rememberInfiniteTransition(label = "rf_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isFlashing) 900 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val ecoColor = if (ecosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
    val mcuAccent = when (board.mcu) {
        McuArchitecture.NRF52840 -> VioletNrf
        McuArchitecture.ESP32_S3, McuArchitecture.ESP32, McuArchitecture.ESP32_C3 -> EspOrange
        McuArchitecture.RP2040 -> AmberSignal
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF07111C), Color(0xFF0D1F30), Color(0xFF091522))
                )
            )
            .border(1.dp, ecoColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ecoColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HARDWARE TELEMETRY BLUEPRINT • ${board.mcu.shortName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ecoColor
                    )
                }
                Text(
                    text = "VID:0x%04X PID:0x%04X".format(board.vendorId, board.productId),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height

                    // Subtle blueprint grid
                    val gridStep = 18.dp.toPx()
                    var gx = 0f
                    while (gx < w) {
                        drawLine(
                            color = Color(0xFF173047).copy(alpha = 0.45f),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                        gx += gridStep
                    }
                    var gy = 0f
                    while (gy < h) {
                        drawLine(
                            color = Color(0xFF173047).copy(alpha = 0.45f),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                        gy += gridStep
                    }

                    // PCB Substrate Outline
                    val pcbLeft = w * 0.08f
                    val pcbTop = h * 0.10f
                    val pcbWidth = w * 0.78f
                    val pcbHeight = h * 0.80f

                    drawRoundRect(
                        color = Color(0xFF0B2228),
                        topLeft = Offset(pcbLeft, pcbTop),
                        size = Size(pcbWidth, pcbHeight),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                    drawRoundRect(
                        color = ecoColor.copy(alpha = 0.65f),
                        topLeft = Offset(pcbLeft, pcbTop),
                        size = Size(pcbWidth, pcbHeight),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Gold GPIO header pads along top & bottom edges of PCB
                    val padCount = 13
                    val padSpacing = (pcbWidth - 36.dp.toPx()) / padCount
                    for (i in 0 until padCount) {
                        val px = pcbLeft + 22.dp.toPx() + i * padSpacing
                        drawCircle(
                            color = AmberSignal.copy(alpha = 0.85f),
                            radius = 2.6.dp.toPx(),
                            center = Offset(px, pcbTop + 6.dp.toPx())
                        )
                        drawCircle(
                            color = AmberSignal.copy(alpha = 0.85f),
                            radius = 2.6.dp.toPx(),
                            center = Offset(px, pcbTop + pcbHeight - 6.dp.toPx())
                        )
                    }

                    // USB-C Receptacle on Left Edge
                    val usbW = 16.dp.toPx()
                    val usbH = pcbHeight * 0.42f
                    val usbTop = pcbTop + (pcbHeight - usbH) / 2f
                    drawRoundRect(
                        color = Color(0xFF90A4AE),
                        topLeft = Offset(pcbLeft - 5.dp.toPx(), usbTop),
                        size = Size(usbW, usbH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Active USB / BLE Data Stream Trace from USB-C to MCU
                    val mcuX = pcbLeft + pcbWidth * 0.25f
                    val mcuY = pcbTop + pcbHeight * 0.24f
                    val mcuSize = pcbHeight * 0.52f

                    val traceColor = if (isFlashing) ecoColor else ecoColor.copy(alpha = 0.4f)
                    drawLine(
                        color = traceColor,
                        start = Offset(pcbLeft + usbW - 5.dp.toPx(), pcbTop + pcbHeight * 0.45f),
                        end = Offset(mcuX, pcbTop + pcbHeight * 0.45f),
                        strokeWidth = if (isFlashing) 3.dp.toPx() else 1.8.dp.toPx(),
                        pathEffect = if (isFlashing) {
                            PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = -pulsePhase * 40f)
                        } else null
                    )
                    drawLine(
                        color = traceColor,
                        start = Offset(pcbLeft + usbW - 5.dp.toPx(), pcbTop + pcbHeight * 0.55f),
                        end = Offset(mcuX, pcbTop + pcbHeight * 0.55f),
                        strokeWidth = if (isFlashing) 3.dp.toPx() else 1.8.dp.toPx(),
                        pathEffect = if (isFlashing) {
                            PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = pulsePhase * 40f)
                        } else null
                    )

                    // TX / RX Activity LEDs near USB port
                    val txLedActive = isFlashing && (pulsePhase * 10).toInt() % 2 == 0
                    drawCircle(
                        color = if (txLedActive) MeshtasticGreen else Color(0xFF1F3A3D),
                        radius = 3.5.dp.toPx(),
                        center = Offset(pcbLeft + 18.dp.toPx(), pcbTop + 18.dp.toPx())
                    )
                    drawCircle(
                        color = if (isFlashing && !txLedActive) MeshCoreCyan else AmberSignal.copy(alpha = 0.7f),
                        radius = 3.5.dp.toPx(),
                        center = Offset(pcbLeft + 18.dp.toPx(), pcbTop + pcbHeight - 18.dp.toPx())
                    )

                    // MCU Silicon Package (ESP32-S3 / nRF52840)
                    drawRoundRect(
                        color = Color(0xFF151C24),
                        topLeft = Offset(mcuX, mcuY),
                        size = Size(mcuSize, mcuSize),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = mcuAccent,
                        topLeft = Offset(mcuX, mcuY),
                        size = Size(mcuSize, mcuSize),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // OLED / E-Ink Display Module on right half of PCB
                    val dispX = mcuX + mcuSize + 14.dp.toPx()
                    val dispW = pcbWidth * 0.34f
                    val dispH = pcbHeight * 0.54f
                    val dispY = pcbTop + (pcbHeight - dispH) / 2f

                    drawRoundRect(
                        color = if (board.hasEInk) Color(0xFFDCE4EC) else Color(0xFF050A0F),
                        topLeft = Offset(dispX, dispY),
                        size = Size(dispW, dispH),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    drawRoundRect(
                        color = ecoColor.copy(alpha = 0.8f),
                        topLeft = Offset(dispX, dispY),
                        size = Size(dispW, dispH),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Simulated Mesh Topology bars inside the mini OLED/E-Ink display
                    val barColor = if (board.hasEInk) Color(0xFF102027) else ecoColor
                    drawLine(
                        color = barColor,
                        start = Offset(dispX + 8.dp.toPx(), dispY + dispH * 0.32f),
                        end = Offset(dispX + dispW * 0.75f, dispY + dispH * 0.32f),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = barColor.copy(alpha = 0.65f),
                        start = Offset(dispX + 8.dp.toPx(), dispY + dispH * 0.56f),
                        end = Offset(dispX + dispW * 0.55f, dispY + dispH * 0.56f),
                        strokeWidth = 2.dp.toPx()
                    )
                    if (isFlashing) {
                        val progW = (dispW - 16.dp.toPx()) * flashState.overallProgress.coerceIn(0.08f, 1f)
                        drawLine(
                            color = AmberSignal,
                            start = Offset(dispX + 8.dp.toPx(), dispY + dispH * 0.78f),
                            end = Offset(dispX + 8.dp.toPx() + progW, dispY + dispH * 0.78f),
                            strokeWidth = 3.5.dp.toPx()
                        )
                    }

                    // LoRa SX1262 / LR1110 RF Shield & Gold SMA Antenna Connector on Right Edge
                    val smaX = pcbLeft + pcbWidth
                    val smaY = pcbTop + pcbHeight * 0.5f
                    drawRoundRect(
                        color = AmberSignal,
                        topLeft = Offset(smaX - 2.dp.toPx(), smaY - 8.dp.toPx()),
                        size = Size(12.dp.toPx(), 16.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // Animated LoRa / BLE RF Waves radiating from SMA connector
                    val waveCenter = Offset(smaX + 10.dp.toPx(), smaY)
                    for (ring in 0..2) {
                        val ringProg = ((pulsePhase + ring * 0.33f) % 1f)
                        val radius = 8.dp.toPx() + ringProg * 28.dp.toPx()
                        drawCircle(
                            color = ecoColor.copy(alpha = (1f - ringProg) * 0.65f),
                            radius = radius,
                            center = waveCenter,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Overlay crisp labels aligned with the PCB features
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = if (transport == ConnectionTransport.USB_OTG) "USB-C OTG" else "BLE GATT",
                        style = MaterialTheme.typography.labelSmall,
                        color = ecoColor
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HardwareChipBadge(text = board.loraChip, color = AmberSignal)
                    HardwareChipBadge(text = "${board.flashSizeMb}MB FLASH", color = mcuAccent)
                    HardwareChipBadge(text = roleOrTrackBadge, color = ecoColor)
                }
            }
        }
    }
}

@Composable
fun HardwareChipBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FlashPartitionMemoryBar(
    board: HardwareBoard,
    bundleWebUi: Boolean,
    flashState: FlashProgressState,
    ecosystem: FirmwareEcosystem,
    modifier: Modifier = Modifier
) {
    val ecoColor = if (ecosystem == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
    val partitions = board.partitions.filter {
        it.isRequired || (bundleWebUi && it.name.contains("LittleFS", ignoreCase = true))
    }

    val segmentColors = listOf(
        AmberSignal,
        VioletNrf,
        EspOrange,
        ecoColor,
        MeshCoreCyan
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FLASH MEMORY PARTITION LAYOUT (${board.flashSizeMb} MB)",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
            Text(
                text = flashState.currentAddressHex,
                style = MaterialTheme.typography.labelMedium,
                color = ecoColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Partition Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(ObsidianBg)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val totalWeight = partitions.sumOf { it.sizeKb.coerceAtLeast(64) }.toFloat()
            partitions.forEachIndexed { idx, part ->
                val weight = (part.sizeKb.coerceAtLeast(64) / totalWeight).coerceAtLeast(0.12f)
                val segColor = segmentColors[idx % segmentColors.size]
                val isCurrentlyWriting = flashState.currentPartitionName.contains(part.name, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isCurrentlyWriting) segColor else segColor.copy(alpha = 0.65f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Partition Legend with Hex Offsets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            partitions.forEachIndexed { idx, part ->
                val segColor = segmentColors[idx % segmentColors.size]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(segColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = part.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = part.addressHex,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
