package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerDraftRequest
import com.example.data.model.ServerInput
import com.example.data.model.ServerPublic
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

@Composable
fun ServersScreen(
    viewModel: ServerUIViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<ServerPublic?>(null) }
    var serverToDelete by remember { mutableStateOf<ServerPublic?>(null) }

    Box(modifier = modifier.fillMaxSize().background(ColorCanvas)) {
        if (state.servers.isEmpty() && !state.isLoadingServers) {
            EmptyStateView(
                title = "No Servers Configured",
                description = "Add your first Linux server to connect, monitor metrics, browse files, and open terminals.",
                icon = Icons.Default.Dns,
                actionText = "Add Server",
                onAction = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = NavBarReserved)
            ) {
                items(state.servers, key = { it.id }) { server ->
                    val isConnected = server.connected == true

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                            .testTag("server_card_${server.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = ColorCanvasSoft,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .border(1.dp, ColorHairline, RoundedCornerShape(6.dp))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Dns,
                                                contentDescription = null,
                                                tint = ColorInk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = server.name,
                                            style = TypographyDisplayXS,
                                            color = ColorInk
                                        )
                                        Text(
                                            text = "${server.username}@${server.host}:${server.port}",
                                            style = TypographyCaptionMonoSM,
                                            color = ColorBodyMid
                                        )
                                    }
                                }
                                ServerStatusBadge(status = server.status, connected = isConnected)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = ColorCanvasSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.border(1.dp, ColorHairline, CircleShape)
                                ) {
                                    Text(
                                        text = if (server.authType == "private_key") "SSH KEY" else "PASSWORD",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBody,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }

                                if (server.error != null && server.error.isNotEmpty()) {
                                    Text(
                                        text = server.error,
                                        style = TypographyCaptionMonoSM,
                                        color = ColorStatusError,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = ColorHairline, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (isConnected) {
                                        XPillOutlineButton(
                                            text = "DISCONNECT",
                                            onClick = { viewModel.disconnectServer(server.id) },
                                            compact = true
                                        )
                                    } else {
                                        XPillButton(
                                            text = "CONNECT",
                                            onClick = { viewModel.connectServer(server.id) }
                                        )
                                    }

                                    XPillOutlineButton(
                                        text = "TEST",
                                        onClick = { viewModel.testServer(server.id) },
                                        compact = true
                                    )
                                }

                                Row {
                                    IconButton(onClick = { serverToEdit = server }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = ColorBodyMid)
                                    }
                                    IconButton(onClick = { serverToDelete = server }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorStatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB to add server (xAI White-filled Circle)
        FloatingActionButton(
            onClick = {
                viewModel.clearDraftTest()
                showAddDialog = true
            },
            shape = CircleShape,
            containerColor = ColorPrimary,
            contentColor = ColorOnPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
                .testTag("add_server_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Server")
        }
    }

    // Add Server Dialog
    if (showAddDialog) {
        ServerFormDialog(
            title = "Add Server",
            initialServer = null,
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearDraftTest()
                showAddDialog = false
            },
            onSave = { input ->
                viewModel.createServer(input) {
                    showAddDialog = false
                }
            }
        )
    }

    // Edit Server Dialog
    serverToEdit?.let { s ->
        ServerFormDialog(
            title = "Edit Server",
            initialServer = s,
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearDraftTest()
                serverToEdit = null
            },
            onSave = { input ->
                viewModel.updateServer(s.id, input) {
                    serverToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    serverToDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorStatusError) },
            title = { Text("Delete Server?", style = TypographyDisplayXS, color = ColorInk) },
            text = { Text("Are you sure you want to delete server '${s.name}' (${s.host})?", style = TypographyBodySM, color = ColorBody) },
            confirmButton = {
                XPillButton(
                    text = "DELETE",
                    onClick = {
                        viewModel.deleteServer(s.id)
                        serverToDelete = null
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { serverToDelete = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun ServerFormDialog(
    title: String,
    initialServer: ServerPublic?,
    viewModel: ServerUIViewModel,
    onDismiss: () -> Unit,
    onSave: (ServerInput) -> Unit
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var port by remember { mutableStateOf((initialServer?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initialServer?.username ?: "root") }
    var authType by remember { mutableStateOf(initialServer?.authType ?: "password") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = TypographyDisplayXS, color = ColorInk) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name", style = TypographyCaptionMonoSM) },
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
                            .testTag("server_form_name_input")
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP", style = TypographyCaptionMonoSM) },
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
                                .weight(2f)
                                .testTag("server_form_host_input")
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Port", style = TypographyCaptionMonoSM) },
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
                                .weight(1f)
                                .testTag("server_form_port_input")
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username", style = TypographyCaptionMonoSM) },
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
                            .testTag("server_form_username_input")
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (authType == "password") ColorCanvasCard else ColorCanvasSoft,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, if (authType == "password") ColorPillOutline else ColorHairline, CircleShape)
                                .clickable { authType = "password" }
                        ) {
                            Text(
                                text = "PASSWORD",
                                style = TypographyCaptionMonoSM,
                                color = if (authType == "password") ColorInk else ColorBodyMid,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                        Surface(
                            color = if (authType == "private_key") ColorCanvasCard else ColorCanvasSoft,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, if (authType == "private_key") ColorPillOutline else ColorHairline, CircleShape)
                                .clickable { authType = "private_key" }
                        ) {
                            Text(
                                text = "SSH PRIVATE KEY",
                                style = TypographyCaptionMonoSM,
                                color = if (authType == "private_key") ColorInk else ColorBodyMid,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                if (authType == "password") {
                    item {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("SSH Password", style = TypographyCaptionMonoSM) },
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
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = ColorBodyMid
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_form_password_input")
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = { Text("Private Key (PEM / OpenSSH)", style = TypographyCaptionMonoSM) },
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
                                .height(120.dp)
                                .testTag("server_form_private_key_input"),
                            textStyle = TypographyCaptionMonoSM
                        )
                    }
                }

                // Test Connection Draft button & result
                item {
                    XPillOutlineButton(
                        text = if (state.isTestingDraft) "TESTING SSH..." else "TEST CONNECTION NOW",
                        icon = if (state.isTestingDraft) null else Icons.Default.Speed,
                        onClick = {
                            val portInt = port.toIntOrNull() ?: 22
                            viewModel.testServerDraft(
                                ServerDraftRequest(
                                    name = name,
                                    host = host,
                                    port = portInt,
                                    username = username,
                                    authType = authType,
                                    password = password.ifEmpty { null },
                                    privateKey = privateKey.ifEmpty { null },
                                    serverId = initialServer?.id
                                )
                            )
                        },
                        enabled = !state.isTestingDraft && host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                state.draftTestResult?.let { res ->
                    item {
                        Surface(
                            color = ColorCanvasSoft,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (res.reachable && res.authSuccess) ColorStatusOnline else ColorStatusError,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (res.reachable && res.authSuccess) "CONNECTION & AUTH SUCCESSFUL" else "CONNECTION TEST FAILED",
                                    style = TypographyCaptionMonoSM,
                                    color = if (res.reachable && res.authSuccess) ColorStatusOnline else ColorStatusError
                                )
                                if (res.banner != null) {
                                    Text(
                                        text = "Banner: ${res.banner}",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBody
                                    )
                                }
                                if (res.latencyMs > 0) {
                                    Text(
                                        text = "Latency: ${res.latencyMs} ms",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBodyMid
                                    )
                                }
                                if (res.error != null) {
                                    Text(
                                        text = res.error,
                                        style = TypographyCaptionMonoSM,
                                        color = ColorStatusError
                                    )
                                }
                            }
                        }
                    }
                }

                state.testErrorMessage?.let { errMsg ->
                    item {
                        Text(text = errMsg, color = ColorStatusError, style = TypographyCaptionMonoSM)
                    }
                }
            }
        },
        confirmButton = {
            XPillButton(
                text = "SAVE SERVER",
                onClick = {
                    val portInt = port.toIntOrNull() ?: 22
                    onSave(
                        ServerInput(
                            name = name.ifBlank { host },
                            host = host,
                            port = portInt,
                            username = username,
                            authType = authType,
                            password = password.ifBlank { null },
                            privateKey = privateKey.ifBlank { null }
                        )
                    )
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.testTag("save_server_button")
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
