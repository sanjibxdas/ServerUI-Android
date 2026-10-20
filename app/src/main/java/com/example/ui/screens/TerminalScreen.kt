package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.TerminalState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.XEyebrowMono
import com.example.ui.components.XHairlineDivider
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

// Quick regex to strip ANSI color escape codes for clean mobile rendering
private val ansiRegex = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")

fun cleanAnsi(text: String): String {
    return text.replace(ansiRegex, "")
}

@Composable
fun TerminalScreen(
    viewModel: ServerUIViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var inputCommand by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new terminal lines arrive
    LaunchedEffect(state.terminalLines.size) {
        if (state.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(state.terminalLines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorCanvas)
    ) {
        // Top Terminal Bar
        Surface(
            color = ColorCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = ColorInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SSH Terminal",
                        style = TypographyDisplayXS,
                        color = ColorInk
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = ColorCanvasSoft,
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, ColorHairline, CircleShape)
                    ) {
                        Text(
                            text = when (state.terminalState) {
                                TerminalState.Connected -> "CONNECTED"
                                TerminalState.Connecting -> "CONNECTING..."
                                is TerminalState.Error -> "ERROR"
                                TerminalState.Disconnected -> "DISCONNECTED"
                            },
                            style = TypographyCaptionMonoSM,
                            color = when (state.terminalState) {
                                TerminalState.Connected -> ColorStatusOnline
                                TerminalState.Connecting -> ColorStatusConnecting
                                is TerminalState.Error -> ColorStatusError
                                TerminalState.Disconnected -> ColorBodyMid
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.terminalState == TerminalState.Connected) {
                        IconButton(onClick = { viewModel.disconnectTerminal() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Disconnect", tint = ColorStatusError)
                        }
                    } else {
                        IconButton(onClick = { viewModel.connectTerminal() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Connect", tint = ColorStatusOnline)
                        }
                    }
                    IconButton(onClick = { viewModel.clearTerminal() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = ColorBodyMid)
                    }
                }
            }
        }
        XHairlineDivider()

        if (state.selectedServerId == null) {
            EmptyStateView(
                title = "No Server Selected",
                description = "Select a connected server to start a live interactive SSH terminal session.",
                icon = Icons.Default.Dns
            )
        } else {
            // Scrollable Terminal Output Buffer (Clean near-black console)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorCanvasCard)
                    .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
            ) {
                if (state.terminalLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Terminal ready. Tap Connect to start SSH session.",
                            style = TypographyCaptionMonoSM,
                            color = ColorBodyMid
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        items(state.terminalLines) { rawLine ->
                            val cleaned = cleanAnsi(rawLine)
                            Text(
                                text = cleaned,
                                style = TypographyCaptionMonoSM.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = if (cleaned.startsWith("[") && cleaned.endsWith("]")) ColorInk else ColorBody
                            )
                        }
                    }
                }
            }

            // Virtual Keyboard Accessory Strip for Mobile Terminals
            Surface(
                color = ColorCanvas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalKeyButton(label = "ESC") { viewModel.sendTerminalInput("\u001b") }
                    TerminalKeyButton(label = "TAB") { viewModel.sendTerminalInput("\t") }
                    TerminalKeyButton(label = "Ctrl+C", isDanger = true) { viewModel.sendTerminalInput("\u0003") }
                    TerminalKeyButton(label = "Ctrl+D") { viewModel.sendTerminalInput("\u0004") }
                    TerminalKeyButton(label = "↑") { viewModel.sendTerminalInput("\u001b[A") }
                    TerminalKeyButton(label = "↓") { viewModel.sendTerminalInput("\u001b[B") }
                    TerminalKeyButton(label = "CLEAR") { viewModel.sendTerminalInput("clear\n") }
                }
            }
            XHairlineDivider()

            // Command input row
            Surface(
                color = ColorCanvas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputCommand,
                        onValueChange = { inputCommand = it },
                        placeholder = { Text("Enter command...", color = ColorBodyMid, style = TypographyCaptionMonoSM) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_input_field"),
                        textStyle = TypographyCaptionMonoSM.copy(color = ColorInk),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorCanvasSoft,
                            unfocusedContainerColor = ColorCanvasSoft,
                            focusedBorderColor = ColorPillOutline,
                            unfocusedBorderColor = ColorHairline,
                            focusedTextColor = ColorInk,
                            unfocusedTextColor = ColorInk
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputCommand.isNotEmpty()) {
                                viewModel.sendTerminalInput("$inputCommand\n")
                                inputCommand = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("terminal_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalKeyButton(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = ColorCanvasSoft,
        shape = CircleShape,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isDanger) ColorStatusError.copy(alpha = 0.5f) else ColorHairline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TypographyCaptionMonoSM,
                color = if (isDanger) ColorStatusError else ColorBody
            )
        }
    }
}
