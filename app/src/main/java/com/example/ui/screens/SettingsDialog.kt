package com.example.ui.screens

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
    var urlInput by remember { mutableStateOf(state.backendUrl) }
    var tokenInput by remember { mutableStateOf(state.localToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = ColorInk)
                Spacer(modifier = Modifier.width(10.dp))
                Text("ServerUI Connection", style = TypographyDisplayXS, color = ColorInk)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Connect to your running ServerUI Go API backend. The native Android app dials this endpoint to manage SSH sessions, fleet telemetry, SFTP files, and WebSocket terminal.",
                        style = TypographyBodySM,
                        color = ColorBody
                    )
                }

                item {
                    Column {
                        XEyebrowMono("INSTANCE URL")
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("http://10.0.2.2:8080", color = ColorBodyMid) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorPillOutline,
                                unfocusedBorderColor = ColorHairline,
                                focusedContainerColor = ColorCanvasSoft,
                                unfocusedContainerColor = ColorCanvasSoft,
                                focusedTextColor = ColorInk,
                                unfocusedTextColor = ColorInk
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_server_url_input"),
                            textStyle = TypographyCaptionMonoSM
                        )
                    }
                }

                // Quick presets
                item {
                    XEyebrowMono("QUICK PRESETS")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = ColorCanvasSoft,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, ColorHairline, CircleShape)
                                .clickable { urlInput = "http://10.0.2.2:8080" }
                        ) {
                            Text(
                                text = "EMULATOR (10.0.2.2)",
                                style = TypographyCaptionMonoSM,
                                color = ColorBody,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            color = ColorCanvasSoft,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, ColorHairline, CircleShape)
                                .clickable { urlInput = "http://127.0.0.1:8080" }
                        ) {
                            Text(
                                text = "LOCALHOST",
                                style = TypographyCaptionMonoSM,
                                color = ColorBody,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                item {
                    Column {
                        XEyebrowMono("LOCAL AUTH TOKEN (OPTIONAL)")
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("X-ServerUI-Local-Token", color = ColorBodyMid) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorPillOutline,
                                unfocusedBorderColor = ColorHairline,
                                focusedContainerColor = ColorCanvasSoft,
                                unfocusedContainerColor = ColorCanvasSoft,
                                focusedTextColor = ColorInk,
                                unfocusedTextColor = ColorInk
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_local_token_input"),
                            textStyle = TypographyCaptionMonoSM
                        )
                    }
                }

                // Backend Health Check Status
                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isCheckingBackend) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ColorInk)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pinging /healthz...", style = TypographyCaptionMonoSM, color = ColorBody)
                            } else {
                                Icon(
                                    imageVector = when (state.backendHealthy) {
                                        true -> Icons.Default.CheckCircle
                                        false -> Icons.Default.Cancel
                                        null -> Icons.Default.HelpOutline
                                    },
                                    contentDescription = null,
                                    tint = when (state.backendHealthy) {
                                        true -> ColorStatusOnline
                                        false -> ColorStatusError
                                        null -> ColorBodyMid
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when (state.backendHealthy) {
                                            true -> "ServerUI API Connected (/healthz OK)"
                                            false -> "Cannot connect to ServerUI"
                                            null -> "Health unchecked"
                                        },
                                        style = TypographyBodySM,
                                        color = when (state.backendHealthy) {
                                            true -> ColorStatusOnline
                                            false -> ColorStatusError
                                            null -> ColorBodyMid
                                        }
                                    )
                                    if (state.backendErrorMessage != null) {
                                        Text(
                                            text = state.backendErrorMessage!!,
                                            style = TypographyCaptionMonoSM,
                                            color = ColorBodyMid
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            XEyebrowMono("NATIVE ARM64 CLIENT")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Built 100% natively in Kotlin & Jetpack Compose. Runs on 64-bit ARM Android devices and x86_64 emulators via standard Android Runtime (ART) with AOT/JIT compilation.",
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
                text = "SAVE & CONNECT",
                onClick = {
                    viewModel.saveBackendConfig(urlInput.trim(), tokenInput.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("settings_save_button")
            )
        },
        dismissButton = {
            XPillOutlineButton(
                text = "CANCEL",
                onClick = onDismiss,
                compact = true
            )
        },
        containerColor = ColorCanvasCard,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
    )
}
