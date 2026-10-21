package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.DiskMount
import com.example.data.model.ProcessInfo
import com.example.data.model.ServerPublic
import com.example.data.model.ServiceInfo
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: ServerUIViewModel,
    onNavigateToServers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val metrics = state.metrics
    val selectedServer = state.servers.firstOrNull { it.id == state.selectedServerId }

    var dashboardSection by remember { mutableIntStateOf(0) } // 0: Telemetry, 1: Processes, 2: Services
    var processToKill by remember { mutableStateOf<ProcessInfo?>(null) }
    var serviceToControl by remember { mutableStateOf<Pair<ServiceInfo, String>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Section Selector Tabs (Telemetry / Processes / Services)
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
                DashboardTabChip(
                    label = "Telemetry",
                    icon = Icons.Default.Speed,
                    isSelected = dashboardSection == 0,
                    onClick = { dashboardSection = 0 },
                    modifier = Modifier.weight(1f)
                )
                DashboardTabChip(
                    label = "Processes",
                    icon = Icons.Default.Memory,
                    count = state.metrics?.processes?.size,
                    isSelected = dashboardSection == 1,
                    onClick = { dashboardSection = 1 },
                    modifier = Modifier.weight(1f)
                )
                DashboardTabChip(
                    label = "Services",
                    icon = Icons.Default.Settings,
                    count = state.metrics?.services?.size,
                    isSelected = dashboardSection == 2,
                    onClick = { dashboardSection = 2 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (dashboardSection) {
                0 -> {
                    // Telemetry & Fleet Overview
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = NavBarReserved)
                    ) {
                        // Active server SSH metrics warning if unreachable
                        if (state.metricsErrorMessage != null && selectedServer != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, ColorStatusError.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = ColorStatusError,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Host Telemetry Disconnected",
                                                style = TypographyBodySM,
                                                color = ColorInk
                                            )
                                            Text(
                                                text = state.metricsErrorMessage ?: "Unable to fetch telemetry from ${selectedServer.host}",
                                                style = TypographyCaptionMonoSM,
                                                color = ColorBodyMid
                                            )
                                        }
                                        XPillOutlineButton(
                                            text = "RECONNECT",
                                            onClick = { viewModel.startMetricsPolling() },
                                            compact = true
                                        )
                                    }
                                }
                            }
                        }

                        // Fleet Server Selector Strip (only shown if multi-server)
                        if (state.servers.size > 1) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        XEyebrowMono(text = "Fleet Servers (${state.servers.size})")
                                        XPillOutlineButton(
                                            text = "MANAGE",
                                            onClick = onNavigateToServers,
                                            compact = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        state.servers.forEach { server ->
                                            val isSelected = server.id == state.selectedServerId
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) ColorCanvasSoft else ColorCanvasCard
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .clickable { viewModel.selectServer(server.id) }
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) ColorPillOutline else ColorHairline,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .testTag("server_chip_${server.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                           .size(6.dp)
                                                           .clip(CircleShape)
                                                           .background(
                                                               if (server.connected == true || server.status == "online") ColorStatusOnline else ColorBodyMid
                                                           )
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = server.name,
                                                            style = TypographyBodySM,
                                                            color = ColorInk
                                                        )
                                                        Text(
                                                            text = "${server.username}@${server.host}",
                                                            style = TypographyCaptionMonoSM,
                                                            color = ColorBodyMid
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.servers.isEmpty() && !state.isLoadingServers) {
                            item {
                                EmptyStateView(
                                    title = "No Servers Configured",
                                    description = "Add your Linux servers with SSH password or private key to monitor them in real-time.",
                                    icon = Icons.Default.Dns,
                                    actionText = "Add Server",
                                    onAction = onNavigateToServers
                                )
                            }
                        } else if (metrics != null) {
                            // Selected Server System Overview Banner
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = metrics.hostname?.ifEmpty { metrics.name } ?: (metrics.name ?: "Server"),
                                                    style = TypographyDisplayXS,
                                                    color = ColorInk
                                                )
                                                Text(
                                                    text = "${metrics.username ?: "root"}@${metrics.host ?: ""}:${metrics.port ?: 22}",
                                                    style = TypographyCaptionMonoSM,
                                                    color = ColorBodyMid
                                                )
                                            }
                                            ServerStatusBadge(
                                                status = metrics.status ?: "online",
                                                connected = metrics.connected == true
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = ColorHairline, thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        val kernelRelease = remember(metrics.kernel) {
                                            val tokens = metrics.kernel.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                                            if (tokens.size >= 3) tokens[2] else metrics.kernel.ifEmpty { "Standard" }
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                XEyebrowMono("OS & Arch")
                                                Text(
                                                    text = "${metrics.osName.ifEmpty { "Linux" }} (${metrics.arch.ifEmpty { "x86_64" }})",
                                                    style = TypographyBodySM,
                                                    color = ColorInk
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                XEyebrowMono("Kernel")
                                                Text(
                                                    text = kernelRelease,
                                                    style = TypographyBodySM,
                                                    color = ColorInk
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                XEyebrowMono("Uptime")
                                                Text(
                                                    text = formatUptime(metrics.uptimeSeconds),
                                                    style = TypographyCaptionMono,
                                                    color = ColorInk
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "LOAD: 1m ${String.format(Locale.US, "%.2f", metrics.load1)} / 5m ${String.format(Locale.US, "%.2f", metrics.load5)} / 15m ${String.format(Locale.US, "%.2f", metrics.load15)}",
                                                style = TypographyCaptionMonoSM,
                                                color = ColorBodyMid
                                            )
                                            if (metrics.ipAddress.isNotEmpty()) {
                                                Text(
                                                    text = metrics.ipAddress,
                                                    style = TypographyCaptionMonoSM,
                                                    color = ColorBodyMid
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Gauges Grid
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // CPU Gauge
                                    MetricGaugeCard(
                                        title = "CPU Usage (${metrics.cpuCores} cores)",
                                        value = "${formatPercent(metrics.cpuUsage)} utilized",
                                        subValue = metrics.cpuModel.ifEmpty { "${metrics.cpuCores} Threads" },
                                        percent = metrics.cpuUsage,
                                        icon = Icons.Default.Memory
                                    )

                                    // Core Usage Mini-strip (if multi-core)
                                    if (metrics.cores.isNotEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                XEyebrowMono("CORE BREAKDOWN")
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    metrics.cores.forEach { core ->
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier.width(36.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(18.dp)
                                                                    .height(48.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(ColorCanvasSoft)
                                                                    .border(1.dp, ColorHairline, RoundedCornerShape(4.dp)),
                                                                contentAlignment = Alignment.BottomCenter
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .fillMaxHeight((core.usage / 100.0).coerceIn(0.05, 1.0).toFloat())
                                                                        .background(ColorPrimary)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "C${core.core}",
                                                                style = TypographyCaptionMonoSM,
                                                                fontSize = 9.sp,
                                                                color = ColorBodyMid
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Memory Gauge
                                    MetricGaugeCard(
                                        title = "Memory (RAM)",
                                        value = "${formatBytes(metrics.memoryUsedBytes)} / ${formatBytes(metrics.memoryTotalBytes)}",
                                        subValue = "Free: ${formatBytes(metrics.memoryAvailableBytes)}",
                                        percent = metrics.memoryUsage,
                                        icon = Icons.Default.Storage
                                    )

                                    // Disk Gauge
                                    MetricGaugeCard(
                                        title = "Disk Storage",
                                        value = "${formatBytes(metrics.diskUsedBytes)} / ${formatBytes(metrics.diskTotalBytes)}",
                                        subValue = "Free: ${formatBytes(metrics.diskFreeBytes)}",
                                        percent = metrics.diskUsage,
                                        icon = Icons.Default.Folder
                                    )

                                    // Disk Mounts breakdown
                                    if (metrics.diskMounts.isNotEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                XEyebrowMono("MOUNTED FILESYSTEMS")
                                                Spacer(modifier = Modifier.height(10.dp))
                                                metrics.diskMounts.take(4).forEach { mount ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = mount.mount,
                                                                style = TypographyBodySM,
                                                                color = ColorInk
                                                            )
                                                            Text(
                                                                text = "${mount.device} (${mount.fsType})",
                                                                style = TypographyCaptionMonoSM,
                                                                color = ColorBodyMid
                                                            )
                                                        }
                                                        Text(
                                                            text = "${formatBytes(mount.usedBytes)} / ${formatBytes(mount.totalBytes)} (${formatPercent(mount.usage)})",
                                                            style = TypographyCaptionMonoSM,
                                                            color = ColorBody
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Network & System Summary
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.SwapVert,
                                                    contentDescription = null,
                                                    tint = ColorInk,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                XEyebrowMono("NETWORK & FLEET ACTIVITY")
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    XEyebrowMono("DOWNLOAD (RX)")
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = formatRate(metrics.netRxBps),
                                                        style = TypographyBodySM,
                                                        color = ColorInk
                                                    )
                                                    Text(
                                                        text = "TOTAL: ${formatBytes(metrics.netRxBytes)}",
                                                        style = TypographyCaptionMonoSM,
                                                        color = ColorBodyMid
                                                    )
                                                }
                                                Column {
                                                    XEyebrowMono("UPLOAD (TX)")
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = formatRate(metrics.netTxBps),
                                                        style = TypographyBodySM,
                                                        color = ColorInk
                                                    )
                                                    Text(
                                                        text = "TOTAL: ${formatBytes(metrics.netTxBytes)}",
                                                        style = TypographyCaptionMonoSM,
                                                        color = ColorBodyMid
                                                    )
                                                }
                                                Column {
                                                    XEyebrowMono("CONNECTIONS")
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${metrics.netEstablished} EST.",
                                                        style = TypographyBodySM,
                                                        color = ColorInk
                                                    )
                                                    Text(
                                                        text = "${metrics.netListenPorts.size} PORTS",
                                                        style = TypographyCaptionMonoSM,
                                                        color = ColorBodyMid
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))
                                            HorizontalDivider(color = ColorHairline, thickness = 1.dp)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.List,
                                                        contentDescription = null,
                                                        tint = ColorPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${metrics.processCount} Processes (${metrics.processRunning} running)",
                                                        style = TypographyBodySM,
                                                        color = ColorBody
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = null,
                                                        tint = if (metrics.serviceFailed > 0) ColorStatusError else ColorStatusOnline,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${metrics.serviceRunning} Active${if (metrics.serviceFailed > 0) " (${metrics.serviceFailed} failed)" else ""}",
                                                        style = TypographyBodySM,
                                                        color = if (metrics.serviceFailed > 0) ColorStatusError else ColorBody
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Historical Telemetry Sparkline & Line Chart (Room-backed, 1h/24h/7d)
                                    HistoricalMetricsCard(
                                        snapshots = state.telemetrySnapshots,
                                        selectedWindow = state.selectedTimeWindow,
                                        onWindowSelected = { viewModel.setTimeWindow(it) },
                                        selectedMetric = state.selectedMetricType,
                                        onMetricSelected = { viewModel.setMetricType(it) },
                                        ringBufferCap = state.ringBufferCap,
                                        onSetRingBufferCap = { viewModel.setRingBufferCap(it) }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Processes Tab directly inside Dashboard
                    Box(modifier = Modifier.padding(bottom = NavBarReserved)) {
                        ProcessesTabContent(
                            viewModel = viewModel,
                            onKillProcess = { processToKill = it }
                        )
                    }
                }
                2 -> {
                    // Services Tab directly inside Dashboard
                    Box(modifier = Modifier.padding(bottom = NavBarReserved)) {
                        ServicesTabContent(
                            viewModel = viewModel,
                            onControlService = { service, action -> serviceToControl = Pair(service, action) }
                        )
                    }
                }
            }
        }
    }

    // Kill Process Confirmation Dialog
    processToKill?.let { proc ->
        AlertDialog(
            onDismissRequest = { processToKill = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ColorStatusError) },
            title = { Text("Terminate Process?", style = TypographyDisplayXS, color = ColorInk) },
            text = { Text("Send SIGTERM signal to process '${proc.name}' (PID ${proc.pid})?", style = TypographyBodySM, color = ColorBody) },
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
            text = { Text("Are you sure you want to $action systemd service '${service.name}'?", style = TypographyBodySM, color = ColorBody) },
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
fun DashboardTabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) ColorCanvasCard else Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = if (isSelected) ColorPillOutline else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) ColorInk else ColorBodyMid,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (count != null && count > 0) "${label.uppercase()} ($count)" else label.uppercase(),
                style = TypographyCaptionMonoSM,
                color = if (isSelected) ColorInk else ColorBodyMid,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
