package com.example.ui.screens

import android.os.Build
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
                // Engine Status Card
                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ColorStatusOnline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Standalone Android SSH Engine",
                                    style = TypographyBodySM,
                                    color = ColorStatusOnline
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ServerUI connects directly from your Android device to remote Linux servers over SSH and SFTP. No desktop apps, companion daemons, or intermediate proxies are required.",
                                style = TypographyCaptionMonoSM,
                                color = ColorBody
                            )
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
                        XEyebrowMono("TELEMETRY REFRESH RATE")
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
                                text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • Modern JSch 0.2.23 SSH2 with RSA-SHA2, Ed25519 & ECDSA support",
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
