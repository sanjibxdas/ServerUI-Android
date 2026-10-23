package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.TerminalState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.NavBarReserved
import com.example.ui.components.XHairlineDivider
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel
import org.connectbot.terminal.DelKeyMode
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.Terminal

class StickyModifierManager : ModifierManager {
    var ctrlActive by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var shiftActive by mutableStateOf(false)

    fun toggleCtrl() {
        ctrlActive = !ctrlActive
    }

    fun toggleAlt() {
        altActive = !altActive
    }

    override fun isCtrlActive(): Boolean = ctrlActive

    override fun isAltActive(): Boolean = altActive

    override fun isShiftActive(): Boolean = shiftActive

    override fun clearTransients() {
        ctrlActive = false
        altActive = false
        shiftActive = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    viewModel: ServerUIViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val modifierManager = remember { StickyModifierManager() }
    val isImeVisible = WindowInsets.isImeVisible
    var overflowExpanded by remember { mutableStateOf(false) }

    val pasteFromClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        if (!text.isNullOrEmpty()) {
            viewModel.sendTerminalInput(text)
        }
    }

    val sendExtraKey: (String) -> Unit = { sequence ->
        val ctrl = modifierManager.isCtrlActive()
        val alt = modifierManager.isAltActive()
        val payload = when {
            ctrl && sequence.length == 1 -> {
                val ch = sequence[0].uppercaseChar().code
                val ctrlByte = when (ch) {
                    in 0x40..0x5F -> (ch and 0x1F).toByte()
                    '/'.code -> 0x1F.toByte()
                    '-'.code -> 0x1F.toByte()
                    else -> ch.toByte()
                }
                byteArrayOf(ctrlByte)
            }
            alt -> "\u001B$sequence".toByteArray(Charsets.UTF_8)
            else -> sequence.toByteArray(Charsets.UTF_8)
        }
        viewModel.sendTerminalBytes(payload)
        modifierManager.clearTransients()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorCanvas)
            .imePadding()
            .padding(bottom = if (isImeVisible) 0.dp else NavBarReserved)
    ) {
        // Top Terminal Bar
        Surface(
            color = ColorCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
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
                        color = ColorInk,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = ColorCanvasCard,
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
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.terminalState == TerminalState.Connected) {
                        IconButton(
                            onClick = { viewModel.disconnectTerminal() },
                            modifier = Modifier.testTag("terminal_disconnect_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Disconnect", tint = ColorStatusError)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.connectTerminal() },
                            modifier = Modifier.testTag("terminal_connect_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Connect", tint = ColorStatusOnline)
                        }
                    }
                    IconButton(
                        onClick = pasteFromClipboard,
                        modifier = Modifier.testTag("terminal_paste_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ColorBodyMid)
                    }
                    IconButton(
                        onClick = { viewModel.clearTerminal() },
                        modifier = Modifier.testTag("terminal_clear_buffer_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear buffer", tint = ColorBodyMid)
                    }
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag("terminal_overflow_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More terminal options", tint = ColorBodyMid)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            modifier = Modifier
                                .background(ColorCanvasCard)
                                .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("CLEAR", style = TypographyCaptionMonoSM, color = ColorInk)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.sendTerminalInput("clear\r")
                                    viewModel.clearTerminal()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("PASTE", style = TypographyCaptionMonoSM, color = ColorInk)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    pasteFromClipboard()
                                }
                            )
                        }
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
            // Full-Screen Terminal Emulator Area between Top Bar and Extra-Keys Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ColorCanvasCard)
                    .testTag("terminal_emulator_container")
            ) {
                Terminal(
                    terminalEmulator = viewModel.terminalEmulator,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    typeface = Typeface.MONOSPACE,
                    initialFontSize = 12.sp,
                    minFontSize = 8.sp,
                    maxFontSize = 20.sp,
                    backgroundColor = ColorCanvasCard,
                    foregroundColor = ColorBody,
                    selectionBackgroundColor = ColorInk.copy(alpha = 0.25f),
                    selectionForegroundColor = ColorInk,
                    keyboardEnabled = true,
                    showSoftKeyboard = true,
                    focusRequester = focusRequester,
                    modifierManager = modifierManager,
                    delKeyMode = DelKeyMode.Delete,
                    onTerminalTap = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onPasteRequest = pasteFromClipboard
                )
            }

            XHairlineDivider()

            // Termux-style Two-Row Extra-Keys Bar
            Surface(
                color = ColorCanvas,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("terminal_extra_keys_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: ESC, /, -, HOME, UP, END, PGUP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TerminalKeyButton(label = "ESC") { sendExtraKey("\u001B") }
                        TerminalKeyButton(label = "/") { sendExtraKey("/") }
                        TerminalKeyButton(label = "-") { sendExtraKey("-") }
                        TerminalKeyButton(label = "HOME") { sendExtraKey("\u001B[H") }
                        TerminalKeyButton(label = "UP") { sendExtraKey("\u001B[A") }
                        TerminalKeyButton(label = "END") { sendExtraKey("\u001B[F") }
                        TerminalKeyButton(label = "PGUP") { sendExtraKey("\u001B[5~") }
                    }

                    // Row 2: TAB, CTRL, ALT, LEFT, DOWN, RIGHT, PGDN
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TerminalKeyButton(label = "TAB") { sendExtraKey("\t") }
                        TerminalKeyButton(
                            label = "CTRL",
                            isActive = modifierManager.ctrlActive,
                            onClick = { modifierManager.toggleCtrl() }
                        )
                        TerminalKeyButton(
                            label = "ALT",
                            isActive = modifierManager.altActive,
                            onClick = { modifierManager.toggleAlt() }
                        )
                        TerminalKeyButton(label = "LEFT") { sendExtraKey("\u001B[D") }
                        TerminalKeyButton(label = "DOWN") { sendExtraKey("\u001B[B") }
                        TerminalKeyButton(label = "RIGHT") { sendExtraKey("\u001B[C") }
                        TerminalKeyButton(label = "PGDN") { sendExtraKey("\u001B[6~") }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalKeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) ColorInk else ColorCanvasCard
    val contentColor = if (isActive) ColorCanvas else ColorBody
    val borderColor = if (isActive) ColorInk else ColorHairline

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .height(32.dp)
            .widthIn(min = 44.dp)
            .testTag("extra_key_${label.lowercase()}")
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TypographyCaptionMonoSM.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
