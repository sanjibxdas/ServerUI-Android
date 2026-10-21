package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.PendingShareFile
import com.example.ui.components.XEyebrowMono
import com.example.ui.components.XHairlineDivider
import com.example.ui.components.XPillButton
import com.example.ui.components.XPillOutlineButton
import com.example.ui.components.formatBytes
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

enum class NavDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    TERMINAL("Terminal", Icons.Default.Terminal),
    FILES("Files", Icons.Default.Folder),
    SERVERS("Servers", Icons.Default.Dns)
}

class MainActivity : ComponentActivity() {
    private val viewModel: ServerUIViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            ServerUITheme {
                ServerUIApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uris = mutableListOf<Uri>()

        if (Intent.ACTION_SEND == action) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) uris.add(uri)
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            @Suppress("DEPRECATION")
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris.addAll(list)
        }

        if (uris.isNotEmpty()) {
            val pending = uris.map { uri ->
                var name = "shared_file_${System.currentTimeMillis()}"
                var size = 0L
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                            if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (_: Exception) {}
                PendingShareFile(uriString = uri.toString(), name = name, size = size)
            }
            viewModel.setPendingShareFiles(pending)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerUIApp(viewModel: ServerUIViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val isImeVisible = WindowInsets.isImeVisible
    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWatchdogDialog by remember { mutableStateOf(false) }
    var serverPickerExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val hasAnyAlert = remember(state.servers) { state.servers.any { it.isAlerting } }
    val selectedServer = remember(state.servers, state.selectedServerId) {
        state.servers.firstOrNull { it.id == state.selectedServerId }
    }
    val isSelectedAlerting = selectedServer?.isAlerting == true

    // Notification permission request for background alert notifications (Android 13+ / API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Refresh battery optimization exemption status when returning to app from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect snackbar messages
    LaunchedEffect(state.activeSnackbarMessage) {
        state.activeSnackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Back handling: return to Dashboard if in other tabs
    BackHandler(enabled = currentDestination != NavDestination.DASHBOARD) {
        currentDestination = NavDestination.DASHBOARD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ColorCanvas)) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ServerUI",
                                    style = TypographyDisplayXS,
                                    color = ColorInk
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                XEyebrowMono(
                                    text = "CORE",
                                    color = ColorBodyMid,
                                    style = TypographyCaptionMonoSM
                                )

                                // Active Server Selector Dropdown (xAI Outline Pill with Watchdog Alert Badge)
                                if (state.servers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box {
                                        Surface(
                                            color = if (isSelectedAlerting) Color(0xFF261010) else ColorCanvasSoft,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .border(
                                                    1.dp,
                                                    if (isSelectedAlerting) ColorStatusError else if (hasAnyAlert) ColorAccentSunset else ColorHairline,
                                                    CircleShape
                                                )
                                                .clickable { serverPickerExpanded = true }
                                                .testTag("server_picker_dropdown_trigger")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                if (hasAnyAlert) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Alert active",
                                                        tint = if (isSelectedAlerting) ColorStatusError else ColorAccentSunset,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (selectedServer?.connected == true || selectedServer?.status == "online")
                                                                    ColorStatusOnline
                                                                else
                                                                    ColorBodyMid
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = selectedServer?.name ?: "SELECT SERVER",
                                                    style = TypographyCaptionMonoSM,
                                                    color = if (isSelectedAlerting) ColorStatusError else ColorInk,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = ColorBodyMid,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = serverPickerExpanded,
                                            onDismissRequest = { serverPickerExpanded = false },
                                            modifier = Modifier
                                                .background(ColorCanvasCard)
                                                .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                        ) {
                                            state.servers.forEach { server ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            if (server.isAlerting)
                                                                                ColorStatusError
                                                                            else if (server.connected == true || server.status == "online")
                                                                                ColorStatusOnline
                                                                            else
                                                                                ColorBodyMid
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
                                                            if (server.isAlerting) {
                                                                Surface(
                                                                    color = Color(0xFF381010),
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    modifier = Modifier.padding(start = 8.dp)
                                                                ) {
                                                                    Text(
                                                                        "ALERT",
                                                                        style = TypographyCaptionMonoSM,
                                                                        color = ColorStatusError,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.selectServer(server.id)
                                                        serverPickerExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        actions = {
                            // Watchdog Alert Quick-Access Button with Badge
                            IconButton(
                                onClick = { showWatchdogDialog = true },
                                modifier = Modifier.testTag("watchdog_top_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (hasAnyAlert) {
                                            Badge(
                                                containerColor = ColorStatusError,
                                                contentColor = ColorInk
                                            ) {
                                                Text(state.servers.count { it.isAlerting }.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (hasAnyAlert) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = "Watchdog Alerts",
                                        tint = if (hasAnyAlert) ColorStatusError else ColorBodyMid
                                    )
                                }
                            }

                            // SSH engine / server connection status indicator dot
                            val activeServer = state.servers.firstOrNull { it.id == state.selectedServerId }
                            val indicatorColor = when {
                                activeServer != null && activeServer.isAlerting -> ColorStatusError
                                activeServer != null && (activeServer.connected == true || activeServer.status == "online" || state.metrics != null) -> ColorStatusOnline
                                activeServer != null && state.metricsErrorMessage != null -> ColorStatusError
                                else -> ColorBodyMid
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                                    .testTag("backend_health_indicator")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.testTag("settings_top_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = ColorInk)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ColorCanvas,
                            titleContentColor = ColorInk
                        )
                    )
                    XHairlineDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = ColorCanvas
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen) {
                        NavigationRail(
                            containerColor = ColorCanvas,
                            contentColor = ColorInk,
                            modifier = Modifier.border(width = 1.dp, color = ColorHairline)
                        ) {
                            NavDestination.entries.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = {
                                        Text(
                                            text = dest.label.uppercase(),
                                            style = TypographyCaptionMonoSM
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ColorInk,
                                        selectedTextColor = ColorInk,
                                        indicatorColor = ColorCanvasSoft,
                                        unselectedIconColor = ColorBodyMid,
                                        unselectedTextColor = ColorBodyMid
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentDestination) {
                            NavDestination.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToServers = { currentDestination = NavDestination.SERVERS },
                                onOpenSettings = { showSettingsDialog = true }
                            )
                            NavDestination.TERMINAL -> TerminalScreen(viewModel = viewModel)
                            NavDestination.FILES -> FilesScreen(viewModel = viewModel)
                            NavDestination.SERVERS -> ServersScreen(viewModel = viewModel)
                        }
                    }
                }

                // xAI Outline Pill Bottom Navigation Bar (No Shadows, Hairline Border)
                if (!isWideScreen && !isImeVisible) {
                    XNavBar(
                        currentDestination = currentDestination,
                        onDestinationSelected = { currentDestination = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Unconditional Share Target Confirmation Modal (ACTION_SEND & ACTION_SEND_MULTIPLE)
    state.pendingShareFiles?.let { pendingFiles ->
        var targetServerId by remember(state.selectedServerId, state.servers) {
            mutableStateOf(state.selectedServerId ?: state.servers.firstOrNull()?.id ?: "")
        }
        var targetPath by remember(state.currentFilePath) {
            mutableStateOf(if (state.currentFilePath.isNotBlank() && state.currentFilePath != "~") state.currentFilePath else "/tmp")
        }
        var serverDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.setPendingShareFiles(null) },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ColorPrimary) },
            title = {
                Text("Upload Shared File(s)", style = TypographyDisplayXS, color = ColorInk)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Upload ${pendingFiles.size} shared item(s) to remote server via SFTP:",
                        style = TypographyBodySM,
                        color = ColorBody
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Target Server Selector
                    Text("TARGET SERVER", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val activeServer = state.servers.firstOrNull { it.id == targetServerId }
                        Surface(
                            color = ColorCanvasSoft,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                                .clickable { serverDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    activeServer?.let { "${it.name} (${it.host})" } ?: "No server available",
                                    style = TypographyBodySM,
                                    color = ColorInk
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ColorBodyMid)
                            }
                        }
                        DropdownMenu(
                            expanded = serverDropdownExpanded,
                            onDismissRequest = { serverDropdownExpanded = false },
                            modifier = Modifier.background(ColorCanvasCard).border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                        ) {
                            state.servers.forEach { srv ->
                                DropdownMenuItem(
                                    text = { Text("${srv.name} (${srv.host})", style = TypographyBodySM, color = ColorInk) },
                                    onClick = {
                                        targetServerId = srv.id
                                        serverDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Target Remote Directory
                    Text("TARGET REMOTE DIRECTORY", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = targetPath,
                        onValueChange = { targetPath = it },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorCanvasSoft,
                            unfocusedContainerColor = ColorCanvasSoft,
                            focusedBorderColor = ColorPillOutline,
                            unfocusedBorderColor = ColorHairline,
                            focusedTextColor = ColorInk,
                            unfocusedTextColor = ColorInk
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Files list with filenames and sizes
                    Text("FILES TO UPLOAD (${pendingFiles.size})", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = ColorCanvasSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(pendingFiles) { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        file.name,
                                        style = TypographyCaptionMonoSM,
                                        color = ColorInk,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        formatBytes(file.size),
                                        style = TypographyCaptionMonoSM,
                                        color = ColorBodyMid
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                XPillButton(
                    text = "UPLOAD",
                    enabled = targetServerId.isNotBlank(),
                    onClick = {
                        viewModel.confirmShareUpload(targetServerId, targetPath.trim())
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(
                    text = "CANCEL",
                    onClick = { viewModel.setPendingShareFiles(null) },
                    compact = true
                )
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Watchdog Threshold & Mute Window Configuration Dialog
    if (showWatchdogDialog) {
        val targetServer = selectedServer ?: state.servers.firstOrNull()
        if (targetServer != null) {
            ServerAlertSettingsDialog(
                server = targetServer,
                onDismiss = { showWatchdogDialog = false },
                onSave = { config ->
                    viewModel.updateServerAlertConfig(targetServer.id, config)
                    showWatchdogDialog = false
                },
                onRunWatchdogNow = {
                    viewModel.runWatchdogCheckNow()
                }
            )
        } else {
            showWatchdogDialog = false
        }
    }
}

/**
 * xAI-Inspired Bottom Navigation:
 * Strict near-black canvas, 1px solid hairline border, sliding outline pill indicator,
 * GeistMono uppercase labels, zero drop shadows.
 */
@Composable
fun XNavBar(
    currentDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = NavDestination.entries
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF0C0C0C),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .border(
                    width = 1.dp,
                    color = ColorPillOutline,
                    shape = CircleShape
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val itemWidth = totalWidth / destinations.size

                val animatedOffsetLeft by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 240f
                    ),
                    label = "xai_nav_slider_offset"
                )

                // White outline pill slider behind active tab item with subtle white highlight
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffsetLeft)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(ColorCanvasSoft)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                    )
                }

                // Row of 4 tab items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { index, dest ->
                        val isSelected = index == selectedIndex
                        XNavItem(
                            destination = dest,
                            isSelected = isSelected,
                            onClick = { onDestinationSelected(dest) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun XNavItem(
    destination: NavDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp, color = ColorInk),
                onClick = onClick
            )
            .testTag("nav_item_${destination.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (isSelected) ColorInk else ColorBodyMid,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = destination.label.uppercase(),
                style = TypographyCaptionMonoSM,
                fontSize = 11.sp,
                color = if (isSelected) ColorInk else ColorBodyMid
            )
        }
    }
}
