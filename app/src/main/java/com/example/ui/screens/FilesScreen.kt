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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileEntry
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ServerUIViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: ServerUIViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileEntry?>(null) }
    var itemToDelete by remember { mutableStateOf<FileEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().background(ColorCanvas)) {
        // Path navigation & actions bar
        Surface(
            color = ColorCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUp() },
                        enabled = state.currentFilePath != "/" && state.currentFilePath.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Up a directory", tint = ColorInk)
                    }

                    IconButton(onClick = { viewModel.loadFiles("~") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home Directory", tint = ColorInk)
                    }

                    // Breadcrumb Scroll
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pathSegments = state.currentFilePath.split("/").filter { it.isNotEmpty() }
                        Text(
                            text = "/",
                            style = TypographyCaptionMonoSM,
                            color = ColorInk,
                            modifier = Modifier
                                .clickable { viewModel.loadFiles("/") }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                        var accumulated = ""
                        pathSegments.forEach { seg ->
                            accumulated += "/$seg"
                            val target = accumulated
                            Text(
                                text = "$seg /",
                                color = ColorBody,
                                style = TypographyCaptionMonoSM,
                                modifier = Modifier
                                    .clickable { viewModel.loadFiles(target) }
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.loadFiles(state.currentFilePath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ColorBodyMid)
                    }
                }

                // Quick Create Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    XPillOutlineButton(
                        text = "NEW FILE",
                        icon = Icons.Default.Add,
                        onClick = { showCreateFileDialog = true },
                        compact = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    XPillOutlineButton(
                        text = "NEW FOLDER",
                        icon = Icons.Default.CreateNewFolder,
                        onClick = { showCreateFolderDialog = true },
                        compact = true
                    )
                }
            }
        }
        XHairlineDivider()

        if (state.selectedServerId == null) {
            EmptyStateView(
                title = "No Server Selected",
                description = "Select or connect to a server from the Dashboard to explore remote files via SFTP.",
                icon = Icons.Default.Dns
            )
        } else if (state.isLoadingFiles) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorPrimary, modifier = Modifier.size(32.dp))
            }
        } else if (state.fileErrorMessage != null) {
            EmptyStateView(
                title = "Error Loading Directory",
                description = state.fileErrorMessage ?: "Could not list files",
                icon = Icons.Default.ErrorOutline,
                actionText = "Retry",
                onAction = { viewModel.loadFiles(state.currentFilePath) }
            )
        } else if (state.fileEntries.isEmpty()) {
            EmptyStateView(
                title = "Directory is Empty",
                description = "No files or folders found in ${state.currentFilePath}",
                icon = Icons.Default.FolderOpen
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
            ) {
                items(state.fileEntries, key = { it.path }) { entry ->
                    FileRowItem(
                        entry = entry,
                        onClick = { viewModel.openFile(entry) },
                        onRename = { itemToRename = entry },
                        onDelete = { itemToDelete = entry },
                        onCompress = { viewModel.compressFile(entry.path, "zip") }
                    )
                }
            }
        }
    }

    // File Preview / Text Editor Modal
    state.selectedPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.closeEditor() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preview.path.substringAfterLast('/'),
                            style = TypographyDisplayXS,
                            color = ColorInk
                        )
                        Text(
                            text = "${formatBytes(preview.size)} | ${preview.mime}",
                            style = TypographyCaptionMonoSM,
                            color = ColorBodyMid
                        )
                    }
                    if (preview.writable) {
                        Surface(
                            color = ColorCanvasSoft,
                            shape = CircleShape,
                            modifier = Modifier.border(1.dp, ColorHairline, CircleShape)
                        ) {
                            Text(
                                "WRITABLE",
                                style = TypographyCaptionMonoSM,
                                color = ColorStatusOnline,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            text = {
                if (preview.binary) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = ColorBodyMid, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Binary file preview is not supported in text editor.", style = TypographyBodySM, color = ColorBody)
                    }
                } else {
                    Column {
                        if (preview.truncated) {
                            Text(
                                "NOTE: LARGE FILE TRUNCATED TO FIRST PREVIEW BYTES",
                                style = TypographyCaptionMonoSM,
                                color = ColorAccentSunset,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        OutlinedTextField(
                            value = state.editorContent,
                            onValueChange = { viewModel.updateEditorContent(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .testTag("file_editor_textarea"),
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
                    }
                }
            },
            confirmButton = {
                if (!preview.binary && preview.writable) {
                    XPillButton(
                        text = if (state.isSavingFile) "SAVING..." else "SAVE FILE",
                        onClick = { viewModel.saveFile() },
                        enabled = !state.isSavingFile
                    )
                }
            },
            dismissButton = {
                XPillOutlineButton(text = "CLOSE", onClick = { viewModel.closeEditor() }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Create File Dialog
    if (showCreateFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New File", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. index.js, config.json)", style = TypographyCaptionMonoSM) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ColorCanvasSoft,
                        unfocusedContainerColor = ColorCanvasSoft,
                        focusedBorderColor = ColorPillOutline,
                        unfocusedBorderColor = ColorHairline,
                        focusedTextColor = ColorInk,
                        unfocusedTextColor = ColorInk
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                XPillButton(
                    text = "CREATE",
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(newFileName.trim())
                            showCreateFileDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { showCreateFileDialog = false }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name", style = TypographyCaptionMonoSM) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ColorCanvasSoft,
                        unfocusedContainerColor = ColorCanvasSoft,
                        focusedBorderColor = ColorPillOutline,
                        unfocusedBorderColor = ColorHairline,
                        focusedTextColor = ColorInk,
                        unfocusedTextColor = ColorInk
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                XPillButton(
                    text = "CREATE",
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createDirectory(newFolderName.trim())
                            showCreateFolderDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { showCreateFolderDialog = false }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Rename Dialog
    itemToRename?.let { entry ->
        var renameName by remember { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename ${if (entry.type == "dir") "Folder" else "File"}", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("New Name", style = TypographyCaptionMonoSM) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ColorCanvasSoft,
                        unfocusedContainerColor = ColorCanvasSoft,
                        focusedBorderColor = ColorPillOutline,
                        unfocusedBorderColor = ColorHairline,
                        focusedTextColor = ColorInk,
                        unfocusedTextColor = ColorInk
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                XPillButton(
                    text = "RENAME",
                    onClick = {
                        if (renameName.isNotBlank() && renameName != entry.name) {
                            viewModel.renameFile(entry.path, renameName.trim())
                            itemToRename = null
                        }
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { itemToRename = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorStatusError) },
            title = { Text("Delete ${if (entry.type == "dir") "Folder" else "File"}?", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Text("Are you sure you want to permanently delete '${entry.name}'?", style = TypographyBodySM, color = ColorBody)
            },
            confirmButton = {
                XPillButton(
                    text = "DELETE",
                    onClick = {
                        viewModel.deleteFile(entry.path)
                        itemToDelete = null
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { itemToDelete = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun FileRowItem(
    entry: FileEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDir = entry.type == "dir"

    val icon: ImageVector = when {
        isDir -> Icons.Default.Folder
        entry.name.endsWith(".zip") || entry.name.endsWith(".tar.gz") || entry.name.endsWith(".tgz") -> Icons.Default.Archive
        entry.mime?.startsWith("image/") == true -> Icons.Default.Image
        entry.mime?.startsWith("video/") == true -> Icons.Default.Movie
        entry.mime?.startsWith("audio/") == true -> Icons.Default.MusicNote
        entry.name.endsWith(".sh") || entry.name.endsWith(".py") || entry.name.endsWith(".go") || entry.name.endsWith(".js") || entry.name.endsWith(".json") -> Icons.Default.Code
        else -> Icons.Default.Description
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCanvasCard),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("file_entry_${entry.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ColorCanvasSoft,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.dp, ColorHairline, RoundedCornerShape(6.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ColorInk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.name,
                        style = TypographyBodySM,
                        color = ColorInk
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isDir) {
                            Text(
                                text = formatBytes(entry.size),
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                        }
                        if (entry.mode.isNotEmpty()) {
                            Text(
                                text = entry.mode,
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = ColorBodyMid)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(ColorCanvasCard)
                        .border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", style = TypographyBodySM, color = ColorInk) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ColorBodyMid) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compress (.zip)", style = TypographyBodySM, color = ColorInk) },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = ColorBodyMid) },
                        onClick = {
                            menuExpanded = false
                            onCompress()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", style = TypographyBodySM, color = ColorStatusError) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorStatusError) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
