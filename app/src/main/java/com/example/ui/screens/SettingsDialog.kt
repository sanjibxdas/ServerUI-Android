package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.XEyebrowMono
import com.example.ui.components.XPillButton
import com.example.ui.components.XPillOutlineButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

@Composable
fun SettingsDialog(
    viewModel: ServerUIViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var timeoutSeconds by remember { mutableStateOf(state.sshTimeoutSeconds) }
    var pollSeconds by remember { mutableStateOf(state.pollIntervalSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = ColorInk)
                Spacer(modifier = Modifier.width(10.dp))
                Text("ServerUI Settings", style = TypographyDisplayXS, color = ColorInk)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Persistent Background Watchdog Service Card
                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AllInclusive,
                                        contentDescription = null,
                                        tint = if (state.isPersistentBackgroundEnabled) ColorStatusOnline else ColorBodyMid,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Persistent Background Runner",
                                            style = TypographyBodySM,
                                            color = ColorInk
                                        )
                                        Text(
                                            text = if (state.isForegroundServiceRunning) "Foreground Service ACTIVE" else "STOPPED",
                                            style = TypographyCaptionMonoSM,
                                            color = if (state.isForegroundServiceRunning) ColorStatusOnline else ColorBodyMid
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.isPersistentBackgroundEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.setPersistentBackgroundEnabled(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ColorInk,
                                        checkedTrackColor = ColorPrimary.copy(alpha = 0.5f),
                                        uncheckedThumbColor = ColorBodyMid,
                                        uncheckedTrackColor = ColorCanvasCard
                                    ),
                                    modifier = Modifier.testTag("persistent_background_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Runs an Android foreground service with a persistent status notification so watchdog health checks, sustained CPU alerts, and room telemetry logging continue even if you close or swipe away the app.",
                                style = TypographyCaptionMonoSM,
                                color = ColorBody
                            )

                            if (state.lastWatchdogSummary.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = ColorCanvasCard,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "LAST CHECK: ${state.lastWatchdogSummary}",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBodyMid,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (state.isPersistentBackgroundEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                XEyebrowMono("BACKGROUND CHECK INTERVAL")
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(30 to "30s", 60 to "1m", 120 to "2m", 300 to "5m", 900 to "15m").forEach { (sec, label) ->
                                        val isSel = state.backgroundPollIntervalSeconds == sec
                                        Surface(
                                            color = if (isSel) ColorCanvasCard else ColorCanvasSoft,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSel) ColorPillOutline else ColorHairline,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setBackgroundPollInterval(sec) }
                                        ) {
                                            Text(
                                                text = label,
                                                style = TypographyCaptionMonoSM,
                                                color = if (isSel) ColorInk else ColorBodyMid,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Battery Optimization Status & Permission Card
                                Surface(
                                    color = if (state.isBatteryOptimizationIgnored) ColorCanvasCard else Color(0xFF261805),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = if (state.isBatteryOptimizationIgnored) ColorHairline else ColorStatusWarning,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (state.isBatteryOptimizationIgnored) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                                    contentDescription = null,
                                                    tint = if (state.isBatteryOptimizationIgnored) ColorStatusOnline else ColorStatusWarning,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (state.isBatteryOptimizationIgnored) "Battery Exemption GRANTED" else "Battery Optimization ACTIVE",
                                                    style = TypographyCaptionMonoSM,
                                                    color = if (state.isBatteryOptimizationIgnored) ColorStatusOnline else ColorStatusWarning
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.refreshBatteryOptimizationStatus() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Refresh Battery Status",
                                                    tint = ColorBodyMid,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (state.isBatteryOptimizationIgnored) {
                                                "ServerUI is exempt from Android OS battery throttling. The background monitor will execute reliably without being terminated."
                                            } else {
                                                "Android may throttle or kill background SSH checks when you close the app unless battery optimization is set to Unrestricted."
                                            },
                                            style = TypographyCaptionMonoSM,
                                            color = ColorBody
                                        )

                                        if (!state.isBatteryOptimizationIgnored) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            XPillButton(
                                                text = "REQUEST BATTERY EXEMPTION",
                                                icon = Icons.Default.Power,
                                                onClick = { viewModel.requestIgnoreBatteryOptimizations() },
                                                compact = true,
                                                modifier = Modifier.fillMaxWidth().testTag("request_battery_exemption_button")
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                XPillOutlineButton(
                                    text = "TRIGGER BACKGROUND CHECK NOW",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { viewModel.runWatchdogCheckNow() },
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // SSH Connection Timeout
                item {
                    Column {
                        XEyebrowMono("SSH CONNECTION TIMEOUT")
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 15, 30).forEach { sec ->
                                val selected = timeoutSeconds == sec
                                Surface(
                                    color = if (selected) ColorCanvasCard else ColorCanvasSoft,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) ColorPillOutline else ColorHairline,
                                            shape = CircleShape
                                        )
                                        .clickable { timeoutSeconds = sec }
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = TypographyCaptionMonoSM,
                                        color = if (selected) ColorInk else ColorBodyMid,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Metrics Poll Rate
                item {
                    Column {
                        XEyebrowMono("TELEMETRY REFRESH RATE (FOREGROUND)")
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 3, 5, 10).forEach { sec ->
                                val selected = pollSeconds == sec
                                Surface(
                                    color = if (selected) ColorCanvasCard else ColorCanvasSoft,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) ColorPillOutline else ColorHairline,
                                            shape = CircleShape
                                        )
                                        .clickable { pollSeconds = sec }
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = TypographyCaptionMonoSM,
                                        color = if (selected) ColorInk else ColorBodyMid,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Privacy & Storage Card
                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = ColorInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                XEyebrowMono("ON-DEVICE PRIVACY & SECURITY")
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "All server profiles, passwords, and SSH private keys are stored exclusively in your phone's private app sandbox. Credentials never touch any third-party cloud.",
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                        }
                    }
                }

                // Technical Info Card
                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            XEyebrowMono("CLIENT ENVIRONMENT")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • Persistent Watchdog Service Active • Modern JSch 0.2.23 SSH2 Engine",
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            XPillButton(
                text = "SAVE SETTINGS",
                onClick = {
                    viewModel.saveSshSettings(timeoutSeconds, pollSeconds)
                    onDismiss()
                },
                modifier = Modifier.testTag("settings_save_button")
            )
        },
        dismissButton = {
            XPillOutlineButton(
                text = "CLOSE",
                onClick = onDismiss,
                compact = true
            )
        },
        containerColor = ColorCanvasCard,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
    )
}
