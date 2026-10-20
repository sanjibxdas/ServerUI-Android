package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.example.ui.components.XEyebrowMono
import com.example.ui.components.XHairlineDivider
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ServerUITheme {
                ServerUIApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerUIApp(viewModel: ServerUIViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var currentDestination by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var serverPickerExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

                                // Active Server Selector Dropdown (xAI Outline Pill)
                                if (state.servers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val selectedServer = state.servers.firstOrNull { it.id == state.selectedServerId }
                                    Box {
                                        Surface(
                                            color = ColorCanvasSoft,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .border(1.dp, ColorHairline, CircleShape)
                                                .clickable { serverPickerExpanded = true }
                                                .testTag("server_picker_dropdown_trigger")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
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
                                                Text(
                                                    text = selectedServer?.name ?: "SELECT SERVER",
                                                    style = TypographyCaptionMonoSM,
                                                    color = ColorInk,
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
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (server.connected == true || server.status == "online")
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
                            // Backend connection health indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.backendHealthy) {
                                            true -> ColorStatusOnline
                                            false -> ColorStatusError
                                            null -> ColorBodyMid
                                        }
                                    )
                                    .testTag("backend_health_indicator")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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
                if (!isWideScreen) {
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
        // Glowing White Pill Navigation Bar
        Surface(
            color = Color(0xFF0C0C0C),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                // Multi-tiered White Glow Effect
                .drawBehind {
                    val cornerRadiusPx = size.height / 2f

                    // Layer 1: Wide diffused outer glow
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(-10.dp.toPx(), -8.dp.toPx()),
                        size = Size(size.width + 20.dp.toPx(), size.height + 16.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx + 8.dp.toPx())
                    )
                    // Layer 2: Medium soft bloom
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(-5.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 10.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx + 4.dp.toPx())
                    )
                    // Layer 3: Intense white proximity aura
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f),
                        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                        size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx + 2.dp.toPx())
                    )

                    // Hardware-accelerated Gaussian Blur if supported
                    try {
                        val glowPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(70, 255, 255, 255)
                            maskFilter = android.graphics.BlurMaskFilter(14.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            0f, 0f, size.width, size.height,
                            cornerRadiusPx, cornerRadiusPx,
                            glowPaint
                        )
                    } catch (_: Throwable) {
                        // Software renderer fallback handled by layered drawRoundRect above
                    }
                }
                // Crisp White Outline
                .border(
                    width = 1.5.dp,
                    color = Color.White,
                    shape = CircleShape
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color(0xFF0C0C0C)
                        )
                    ),
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
