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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProcessInfo
import com.example.data.model.ServiceInfo
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessesServicesScreen(
    viewModel: ServerUIViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var processToKill by remember { mutableStateOf<ProcessInfo?>(null) }
    var serviceToControl by remember { mutableStateOf<Pair<ServiceInfo, String>?>(null) }

    Column(modifier = modifier.fillMaxSize().background(ColorCanvas)) {
        // xAI Pill Tab Selector Row
        Surface(
            color = ColorCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(CircleShape)
                    .background(ColorCanvasSoft)
                    .border(1.dp, ColorHairline, CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    color = if (selectedTab == 0) ColorCanvasCard else Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (selectedTab == 0) ColorPillOutline else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedTab = 0 }
                        .testTag("tab_processes")
                ) {
                    Text(
                        text = "PROCESSES (${state.metrics?.processes?.size ?: 0})",
                        style = TypographyCaptionMonoSM,
                        color = if (selectedTab == 0) ColorInk else ColorBodyMid,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Surface(
                    color = if (selectedTab == 1) ColorCanvasCard else Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (selectedTab == 1) ColorPillOutline else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedTab = 1 }
                        .testTag("tab_services")
                ) {
                    Text(
                        text = "SERVICES (${state.metrics?.services?.size ?: 0})",
                        style = TypographyCaptionMonoSM,
                        color = if (selectedTab == 1) ColorInk else ColorBodyMid,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (state.selectedServerId == null) {
            EmptyStateView(
                title = "No Server Selected",
                description = "Select or connect to a server from the Dashboard to view its processes and services.",
                icon = Icons.Default.Dns
            )
        } else if (selectedTab == 0) {
            // Processes Tab
            ProcessesTabContent(
                viewModel = viewModel,
                onKillProcess = { processToKill = it }
            )
        } else {
            // Services Tab
            ServicesTabContent(
                viewModel = viewModel,
                onControlService = { service, action -> serviceToControl = Pair(service, action) }
            )
        }
    }

    // Kill Process Confirmation Dialog
    processToKill?.let { proc ->
        AlertDialog(
            onDismissRequest = { processToKill = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorStatusError) },
            title = { Text("Terminate Process?", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Text("Send SIGTERM signal to process '${proc.name}' (PID ${proc.pid})?", style = TypographyBodySM, color = ColorBody)
            },
            confirmButton = {
                XPillButton(
                    text = "KILL PROCESS",
                    onClick = {
                        viewModel.killProcess(proc.pid)
                        processToKill = null
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { processToKill = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Service Control Confirmation Dialog
    serviceToControl?.let { (service, action) ->
        AlertDialog(
            onDismissRequest = { serviceToControl = null },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = ColorInk) },
            title = { Text("${action.replaceFirstChar { it.uppercase() }} Service?", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Text("Are you sure you want to $action systemd service '${service.name}'?", style = TypographyBodySM, color = ColorBody)
            },
            confirmButton = {
                XPillButton(
                    text = action.uppercase(),
                    onClick = {
                        viewModel.controlService(service.name, action)
                        serviceToControl = null
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { serviceToControl = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Process Detail Modal Sheet
    state.selectedProcessDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { viewModel.clearProcessDetail() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ColorInk)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Process Details (PID ${detail.pid})", style = TypographyDisplayXS, color = ColorInk)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XEyebrowMono("COMMAND")
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = detail.command.ifEmpty { detail.name },
                            style = TypographyCaptionMonoSM,
                            color = ColorInk,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("User: ${detail.user}", style = TypographyBodySM, color = ColorBody)
                        Text("PPID: ${detail.ppid}", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CPU: ${formatPercent(detail.cpu)}", style = TypographyCaptionMonoSM, color = ColorInk)
                        Text("Memory: ${formatPercent(detail.memory)}", style = TypographyCaptionMonoSM, color = ColorInk)
                    }
                    if (detail.threads > 0) {
                        Text("Threads: ${detail.threads}", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    }
                    if (detail.startedAt.isNotEmpty()) {
                        Text("Started: ${detail.startedAt}", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    }
                }
            },
            confirmButton = {
                XPillButton(
                    text = "KILL PROCESS",
                    onClick = {
                        val pid = detail.pid
                        viewModel.clearProcessDetail()
                        processToKill = ProcessInfo(pid = pid, name = detail.name, user = detail.user)
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CLOSE", onClick = { viewModel.clearProcessDetail() }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun ProcessesTabContent(
    viewModel: ServerUIViewModel,
    onKillProcess: (ProcessInfo) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val allProcesses = state.metrics?.processes ?: emptyList()
    val query = state.processSearchQuery

    val filtered = remember(allProcesses, query) {
        if (query.isBlank()) {
            allProcesses
        } else {
            allProcesses.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.pid.toString().contains(query) ||
                        it.user.contains(query, ignoreCase = true) ||
                        it.command.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setProcessSearch(it) },
            placeholder = { Text("Search by name, PID, or user...", color = ColorBodyMid, style = TypographyBodySM) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ColorBodyMid) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setProcessSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = ColorBodyMid)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("search_processes_input"),
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

        if (filtered.isEmpty()) {
            EmptyStateView(
                title = "No Processes Found",
                description = if (query.isEmpty()) "No processes reported from host." else "No processes matching '$query'.",
                icon = Icons.Default.FilterList
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.pid }) { proc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                            .clickable { viewModel.inspectProcess(proc.pid) }
                            .testTag("process_row_${proc.pid}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = ColorCanvasSoft,
                                        shape = CircleShape,
                                        modifier = Modifier.border(1.dp, ColorHairline, CircleShape)
                                    ) {
                                        Text(
                                            text = "PID ${proc.pid}",
                                            style = TypographyCaptionMonoSM,
                                            color = ColorBody,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = proc.name,
                                        style = TypographyBodySM,
                                        color = ColorInk
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "USER: ${proc.user.uppercase()} | STATUS: ${proc.status.uppercase()}",
                                    style = TypographyCaptionMonoSM,
                                    color = ColorBodyMid
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${formatPercent(proc.cpu)} CPU",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorInk
                                    )
                                    Text(
                                        text = "${formatPercent(proc.memory)} MEM",
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBodyMid
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onKillProcess(proc) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kill Process",
                                        tint = ColorStatusError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServicesTabContent(
    viewModel: ServerUIViewModel,
    onControlService: (ServiceInfo, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val allServices = state.metrics?.services ?: emptyList()
    val query = state.serviceSearchQuery

    val filtered = remember(allServices, query) {
        if (query.isBlank()) {
            allServices
        } else {
            allServices.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.active.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setServiceSearch(it) },
            placeholder = { Text("Search systemd services...", color = ColorBodyMid, style = TypographyBodySM) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ColorBodyMid) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setServiceSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = ColorBodyMid)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("search_services_input"),
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

        if (filtered.isEmpty()) {
            EmptyStateView(
                title = "No Services Found",
                description = if (query.isEmpty()) "No systemd services reported." else "No services matching '$query'.",
                icon = Icons.Default.Settings
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.name }) { service ->
                    val isActive = service.active == "active"
                    val isFailed = service.active == "failed"

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                            .testTag("service_card_${service.name}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isFailed -> ColorStatusError
                                                    isActive -> ColorStatusOnline
                                                    else -> ColorBodyMid
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = service.name,
                                        style = TypographyBodySM,
                                        color = ColorInk
                                    )
                                }

                                Surface(
                                    color = ColorCanvasSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.border(1.dp, ColorHairline, CircleShape)
                                ) {
                                    Text(
                                        text = "${service.active.uppercase()} (${service.sub.uppercase()})",
                                        style = TypographyCaptionMonoSM,
                                        color = when {
                                            isFailed -> ColorStatusError
                                            isActive -> ColorStatusOnline
                                            else -> ColorBodyMid
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (service.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = service.description,
                                    style = TypographyCaptionMonoSM,
                                    color = ColorBodyMid
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (isActive) {
                                    XPillOutlineButton(
                                        text = "RESTART",
                                        icon = Icons.Default.Refresh,
                                        onClick = { onControlService(service, "restart") },
                                        compact = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    XPillOutlineButton(
                                        text = "STOP",
                                        icon = Icons.Default.Stop,
                                        onClick = { onControlService(service, "stop") },
                                        compact = true
                                    )
                                } else {
                                    XPillButton(
                                        text = "START",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = { onControlService(service, "start") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
