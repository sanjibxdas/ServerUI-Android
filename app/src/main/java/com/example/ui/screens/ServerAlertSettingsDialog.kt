package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerAlertConfig
import com.example.data.model.ServerPublic
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ServerAlertSettingsDialog(
    server: ServerPublic,
    onDismiss: () -> Unit,
    onSave: (ServerAlertConfig) -> Unit,
    onRunWatchdogNow: () -> Unit
) {
    var enabled by remember { mutableStateOf(server.alertConfig.enabled) }
    var cpuThreshold by remember { mutableFloatStateOf(server.alertConfig.cpuThreshold.toFloat()) }
    var cpuSustainedMinutes by remember { mutableIntStateOf(server.alertConfig.cpuSustainedMinutes) }
    var ramThreshold by remember { mutableFloatStateOf(server.alertConfig.ramThreshold.toFloat()) }
    var diskThreshold by remember { mutableFloatStateOf(server.alertConfig.diskThreshold.toFloat()) }
    var alertOnFailedService by remember { mutableStateOf(server.alertConfig.alertOnFailedService) }
    var alertOnUnreachable by remember { mutableStateOf(server.alertConfig.alertOnUnreachable) }

    var muteWindowEnabled by remember { mutableStateOf(server.alertConfig.muteWindowEnabled) }
    var muteStartHour by remember { mutableIntStateOf(server.alertConfig.muteStartHour) }
    var muteStartMinute by remember { mutableIntStateOf(server.alertConfig.muteStartMinute) }
    var muteEndHour by remember { mutableIntStateOf(server.alertConfig.muteEndHour) }
    var muteEndMinute by remember { mutableIntStateOf(server.alertConfig.muteEndMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Watchdog Alerts", style = TypographyDisplayXS, color = ColorInk)
                    Text(
                        server.name,
                        style = TypographyCaptionMonoSM,
                        color = ColorBodyMid
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ColorInk,
                        checkedTrackColor = ColorPrimary.copy(alpha = 0.5f),
                        uncheckedThumbColor = ColorBodyMid,
                        uncheckedTrackColor = ColorCanvasSoft
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = ColorCanvasSoft,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ColorHairline, RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = ColorStatusOnline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Autonomous Background Monitor: Runs continuously via Android Foreground Service even if you close or swipe away ServerUI.",
                            style = TypographyCaptionMonoSM,
                            color = ColorBody
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // CPU Threshold Slider
                Text(
                    "CPU THRESHOLD: ${cpuThreshold.toInt()}%",
                    style = TypographyCaptionMonoSM,
                    color = ColorInk
                )
                Slider(
                    value = cpuThreshold,
                    onValueChange = { cpuThreshold = it },
                    valueRange = 50f..99f,
                    steps = 48,
                    colors = SliderDefaults.colors(
                        thumbColor = ColorPillOutline,
                        activeTrackColor = ColorPrimary
                    )
                )

                // CPU Sustained Window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sustained Window:", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    Text("${cpuSustainedMinutes} min", style = TypographyCaptionMonoSM, color = ColorInk)
                }
                Slider(
                    value = cpuSustainedMinutes.toFloat(),
                    onValueChange = { cpuSustainedMinutes = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = ColorPillOutline,
                        activeTrackColor = ColorPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // RAM Threshold Slider
                Text(
                    "RAM THRESHOLD: ${ramThreshold.toInt()}%",
                    style = TypographyCaptionMonoSM,
                    color = ColorInk
                )
                Slider(
                    value = ramThreshold,
                    onValueChange = { ramThreshold = it },
                    valueRange = 50f..99f,
                    steps = 48,
                    colors = SliderDefaults.colors(
                        thumbColor = ColorPillOutline,
                        activeTrackColor = ColorPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Disk Threshold Slider
                Text(
                    "DISK THRESHOLD: ${diskThreshold.toInt()}%",
                    style = TypographyCaptionMonoSM,
                    color = ColorInk
                )
                Slider(
                    value = diskThreshold,
                    onValueChange = { diskThreshold = it },
                    valueRange = 50f..99f,
                    steps = 48,
                    colors = SliderDefaults.colors(
                        thumbColor = ColorPillOutline,
                        activeTrackColor = ColorPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
                XHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Toggles for failed units & unreachable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Alert on failed systemd service", style = TypographyBodySM, color = ColorInk)
                    Checkbox(
                        checked = alertOnFailedService,
                        onCheckedChange = { alertOnFailedService = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ColorPillOutline,
                            checkmarkColor = ColorCanvas,
                            uncheckedColor = ColorBodyMid
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Alert on SSH port unreachable", style = TypographyBodySM, color = ColorInk)
                    Checkbox(
                        checked = alertOnUnreachable,
                        onCheckedChange = { alertOnUnreachable = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ColorPillOutline,
                            checkmarkColor = ColorCanvas,
                            uncheckedColor = ColorBodyMid
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                XHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Mute Window Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Mute Window (Do Not Disturb)", style = TypographyBodySM, color = ColorInk)
                        Text(
                            if (muteWindowEnabled) {
                                String.format(java.util.Locale.US, "%02d:%02d - %02d:%02d", muteStartHour, muteStartMinute, muteEndHour, muteEndMinute)
                            } else "Disabled",
                            style = TypographyCaptionMonoSM,
                            color = ColorBodyMid
                        )
                    }
                    Switch(
                        checked = muteWindowEnabled,
                        onCheckedChange = { muteWindowEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorInk,
                            checkedTrackColor = ColorPrimary.copy(alpha = 0.5f),
                            uncheckedThumbColor = ColorBodyMid,
                            uncheckedTrackColor = ColorCanvasSoft
                        )
                    )
                }

                if (muteWindowEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = String.format(java.util.Locale.US, "%02d:%02d", muteStartHour, muteStartMinute),
                            onValueChange = { str ->
                                val parts = str.split(":")
                                if (parts.size == 2) {
                                    val h = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    if (h != null && h in 0..23) muteStartHour = h
                                    if (m != null && m in 0..59) muteStartMinute = m
                                }
                            },
                            label = { Text("Start (HH:MM)", style = TypographyCaptionMonoSM) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ColorCanvasSoft,
                                unfocusedContainerColor = ColorCanvasSoft,
                                focusedBorderColor = ColorPillOutline,
                                unfocusedBorderColor = ColorHairline,
                                focusedTextColor = ColorInk,
                                unfocusedTextColor = ColorInk
                            )
                        )
                        OutlinedTextField(
                            value = String.format(java.util.Locale.US, "%02d:%02d", muteEndHour, muteEndMinute),
                            onValueChange = { str ->
                                val parts = str.split(":")
                                if (parts.size == 2) {
                                    val h = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    if (h != null && h in 0..23) muteEndHour = h
                                    if (m != null && m in 0..59) muteEndMinute = m
                                }
                            },
                            label = { Text("End (HH:MM)", style = TypographyCaptionMonoSM) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ColorCanvasSoft,
                                unfocusedContainerColor = ColorCanvasSoft,
                                focusedBorderColor = ColorPillOutline,
                                unfocusedBorderColor = ColorHairline,
                                focusedTextColor = ColorInk,
                                unfocusedTextColor = ColorInk
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // One-tap Watchdog Test Button
                XPillOutlineButton(
                    text = "RUN WATCHDOG CHECK NOW",
                    icon = Icons.Default.PlayArrow,
                    onClick = onRunWatchdogNow,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            XPillButton(
                text = "SAVE CONFIG",
                onClick = {
                    val config = ServerAlertConfig(
                        enabled = enabled,
                        cpuThreshold = cpuThreshold.toDouble(),
                        cpuSustainedMinutes = cpuSustainedMinutes,
                        ramThreshold = ramThreshold.toDouble(),
                        diskThreshold = diskThreshold.toDouble(),
                        alertOnFailedService = alertOnFailedService,
                        alertOnUnreachable = alertOnUnreachable,
                        muteWindowEnabled = muteWindowEnabled,
                        muteStartHour = muteStartHour,
                        muteStartMinute = muteStartMinute,
                        muteEndHour = muteEndHour,
                        muteEndMinute = muteEndMinute
                    )
                    onSave(config)
                }
            )
        },
        dismissButton = {
            XPillOutlineButton(text = "CANCEL", onClick = onDismiss, compact = true)
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = ColorCanvasCard,
        modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
    )
}
