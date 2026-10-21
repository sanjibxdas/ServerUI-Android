package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.db.TelemetrySnapshot
import com.example.ui.components.XEyebrowMono
import com.example.ui.components.formatPercent
import com.example.ui.components.formatRate
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Historical metric graph with sparklines / line charts for CPU, RAM, Disk, and Network
 * over 1h / 24h / 7d with Room-backed ring-buffer persistence.
 */
@Composable
fun HistoricalMetricsCard(
    snapshots: List<TelemetrySnapshot>,
    selectedWindow: String,
    onWindowSelected: (String) -> Unit,
    selectedMetric: String,
    onMetricSelected: (String) -> Unit,
    ringBufferCap: Int,
    onSetRingBufferCap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCapDialog by remember { mutableStateOf(false) }
    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Ring-buffer Cap Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = ColorInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    XEyebrowMono("HISTORICAL TELEMETRY (ROOM)")
                }

                Surface(
                    color = ColorCanvasSoft,
                    shape = CircleShape,
                    modifier = Modifier
                        .border(1.dp, ColorHairline, CircleShape)
                        .clickable { showCapDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${snapshots.size} / $ringBufferCap pts",
                            style = TypographyCaptionMonoSM,
                            color = ColorBodyMid
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time Range Tabs: 1H / 24H / 7D
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(ColorCanvasSoft)
                    .border(1.dp, ColorHairline, CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("1h" to "1H", "24h" to "24H", "7d" to "7D").forEach { (key, label) ->
                    val isSelected = selectedWindow.equals(key, ignoreCase = true)
                    Surface(
                        color = if (isSelected) ColorCanvasCard else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ColorPillOutline else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onWindowSelected(key) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                style = TypographyCaptionMonoSM,
                                color = if (isSelected) ColorInk else ColorBodyMid
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metric Selector Chips: CPU / RAM / DISK / NETWORK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "cpu" to "CPU",
                    "ram" to "RAM",
                    "disk" to "DISK",
                    "network" to "NETWORK"
                ).forEach { (key, label) ->
                    val isSelected = selectedMetric == key
                    Surface(
                        color = if (isSelected) ColorCanvasSoft else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isSelected) ColorPrimary else ColorHairline,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onMetricSelected(key) }
                    ) {
                        Text(
                            text = label,
                            style = TypographyCaptionMonoSM,
                            color = if (isSelected) ColorInk else ColorBodyMid,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Graph canvas or gathering message
            if (snapshots.size < 2) {
                Surface(
                    color = ColorCanvasSoft,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .border(1.dp, ColorHairline, RoundedCornerShape(6.dp))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (snapshots.isEmpty()) "GATHERING ROOM SNAPSHOTS..." else "1 snapshot recorded — awaiting more points...",
                            style = TypographyCaptionMonoSM,
                            color = ColorBodyMid
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Background worker & live polls persist metrics continuously",
                            style = TypographyBodySM,
                            fontSize = 11.sp,
                            color = ColorBodyMid
                        )
                    }
                }
            } else {
                val dataPoints: List<Double> = when (selectedMetric) {
                    "cpu" -> snapshots.map { it.cpuUsage }
                    "ram" -> snapshots.map { it.memoryUsage }
                    "disk" -> snapshots.map { it.diskUsage }
                    "network" -> snapshots.map { (it.networkRxRate + it.networkTxRate).toDouble() }
                    else -> snapshots.map { it.cpuUsage }
                }

                val minVal = dataPoints.minOrNull() ?: 0.0
                val maxVal = max(dataPoints.maxOrNull() ?: 100.0, if (selectedMetric == "network") 1024.0 else 100.0)
                val latestVal = dataPoints.lastOrNull() ?: 0.0

                // Current & Min/Max header readout
                val activePointIndex = scrubIndex ?: (dataPoints.size - 1)
                val activeSnapshot = snapshots.getOrNull(activePointIndex)
                val activeValue = dataPoints.getOrElse(activePointIndex) { latestVal }

                val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                val dateFmt = remember { SimpleDateFormat("MMM dd HH:mm", Locale.US) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        val formattedVal = when (selectedMetric) {
                            "network" -> formatRate(activeValue.toLong())
                            else -> "${formatPercent(activeValue)} utilized"
                        }
                        Text(
                            text = formattedVal,
                            style = TypographyDisplaySM,
                            color = ColorInk
                        )
                        activeSnapshot?.let { s ->
                            Text(
                                text = if (selectedWindow == "7d") dateFmt.format(Date(s.timestamp)) else timeFmt.format(Date(s.timestamp)),
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val maxFmt = if (selectedMetric == "network") formatRate(maxVal.toLong()) else "${formatPercent(maxVal)}"
                        val minFmt = if (selectedMetric == "network") formatRate(minVal.toLong()) else "${formatPercent(minVal)}"
                        Text(text = "MAX: $maxFmt", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                        Text(text = "MIN: $minFmt", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Canvas Line Chart with gradient glow & touch scrub
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ColorCanvasSoft)
                        .border(1.dp, ColorHairline, RoundedCornerShape(6.dp))
                        .pointerInput(dataPoints) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val count = dataPoints.size
                                    if (count > 1) {
                                        val idx = ((offset.x / size.width) * (count - 1)).toInt().coerceIn(0, count - 1)
                                        scrubIndex = idx
                                    }
                                }
                            )
                        }
                        .pointerInput(dataPoints) {
                            detectDragGestures(
                                onDragEnd = { scrubIndex = null },
                                onDragCancel = { scrubIndex = null },
                                onDrag = { change, _ ->
                                    val count = dataPoints.size
                                    if (count > 1) {
                                        val idx = ((change.position.x / size.width) * (count - 1)).toInt().coerceIn(0, count - 1)
                                        scrubIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                        val w = size.width
                        val h = size.height
                        val count = dataPoints.size
                        if (count < 2) return@Canvas

                        val range = if (maxVal - minVal > 0.0001) (maxVal - minVal) else 1.0

                        // Horizontal guideline (50% mark)
                        val midY = h * 0.5f
                        drawLine(
                            color = ColorHairline.copy(alpha = 0.5f),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1f
                        )

                        val points = dataPoints.mapIndexed { idx, v ->
                            val x = (idx.toFloat() / (count - 1)) * w
                            val normalized = ((v - minVal) / range).coerceIn(0.0, 1.0).toFloat()
                            val y = h - (normalized * h)
                            Offset(x, y)
                        }

                        // Gradient fill path below the line
                        val fillPath = Path().apply {
                            moveTo(points.first().x, h)
                            lineTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val cur = points[i]
                                val cpx = (prev.x + cur.x) / 2
                                quadraticTo(prev.x, prev.y, cpx, (prev.y + cur.y) / 2)
                            }
                            lineTo(points.last().x, points.last().y)
                            lineTo(points.last().x, h)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(ColorPrimary.copy(alpha = 0.28f), Color.Transparent),
                                startY = 0f,
                                endY = h
                            )
                        )

                        // Smooth polyline / curve stroke
                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val cur = points[i]
                                val cpx = (prev.x + cur.x) / 2
                                quadraticTo(prev.x, prev.y, cpx, (prev.y + cur.y) / 2)
                            }
                            lineTo(points.last().x, points.last().y)
                        }

                        drawPath(
                            path = strokePath,
                            color = ColorPrimary,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )

                        // Scrub indicator vertical hairline & dot
                        scrubIndex?.let { idx ->
                            val pt = points.getOrNull(idx) ?: return@let
                            drawLine(
                                color = ColorPillOutline,
                                start = Offset(pt.x, 0f),
                                end = Offset(pt.x, h),
                                strokeWidth = 1.5f
                            )
                            drawCircle(
                                color = ColorInk,
                                radius = 4.5f,
                                center = pt
                            )
                            drawCircle(
                                color = ColorPrimary,
                                radius = 2.5f,
                                center = pt
                            )
                        }
                    }
                }
            }
        }
    }

    // Configurable Ring-Buffer Cap Dialog
    if (showCapDialog) {
        AlertDialog(
            onDismissRequest = { showCapDialog = false },
            title = { Text("Ring-Buffer Retention Cap", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Configure the maximum number of historical telemetry snapshots retained locally in Room per server:",
                        style = TypographyBodySM,
                        color = ColorBody
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(500, 1000, 2000, 5000).forEach { cap ->
                        val isSelected = ringBufferCap == cap
                        Surface(
                            color = if (isSelected) ColorCanvasSoft else ColorCanvasCard,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isSelected) ColorPrimary else ColorHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSetRingBufferCap(cap)
                                    showCapDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$cap snapshots",
                                    style = TypographyBodySM,
                                    color = if (isSelected) ColorInk else ColorBody
                                )
                                if (isSelected) {
                                    Text("ACTIVE", style = TypographyCaptionMonoSM, color = ColorPrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCapDialog = false }) {
                    Text("DONE", style = TypographyCaptionMonoSM, color = ColorPrimary)
                }
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }
}
