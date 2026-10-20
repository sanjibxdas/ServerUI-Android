package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var itemToChmod by remember { mutableStateOf<FileEntry?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var bulkDestAction by remember { mutableStateOf<String?>(null) } // "copy" or "move"

    // SAF Document download state
    var pendingDownloadEntry by remember { mutableStateOf<FileEntry?>(null) }
    val safCreateDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        if (destUri != null && pendingDownloadEntry != null) {
            viewModel.downloadFile(pendingDownloadEntry!!.path, destUri)
            pendingDownloadEntry = null
        }
    }

    // SAF Document upload state
    val safOpenDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { sourceUris: List<Uri> ->
        if (sourceUris.isNotEmpty()) {
            viewModel.uploadFiles(sourceUris, state.currentFilePath)
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    val isCurrentBookmarked = state.bookmarks.contains(state.currentFilePath)

    Column(modifier = modifier.fillMaxSize().background(ColorCanvas)) {
        // Multi-select Action Bar OR Path Navigation Bar
        if (state.isMultiSelectMode) {
            Surface(
                color = ColorCanvasCard,
                modifier = Modifier.fillMaxWidth().border(1.dp, ColorPillOutline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearFileSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Multi-Select", tint = ColorInk)
                        }
                        Text(
                            text = "${state.selectedFilePaths.size} selected",
                            style = TypographyDisplayXS,
                            color = ColorInk
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        XPillOutlineButton(
                            text = if (state.selectedFilePaths.size == state.fileEntries.size) "NONE" else "ALL",
                            onClick = {
                                if (state.selectedFilePaths.size == state.fileEntries.size) {
                                    viewModel.toggleMultiSelectMode()
                                    viewModel.toggleMultiSelectMode()
                                } else {
                                    viewModel.selectAllFiles()
                                }
                            },
                            compact = true
                        )
                        XPillOutlineButton(
                            text = "COPY",
                            icon = Icons.Default.ContentCopy,
                            onClick = { bulkDestAction = "copy" },
                            compact = true,
                            enabled = state.selectedFilePaths.isNotEmpty()
                        )
                        XPillOutlineButton(
                            text = "MOVE",
                            icon = Icons.Default.DriveFileMove,
                            onClick = { bulkDestAction = "move" },
                            compact = true,
                            enabled = state.selectedFilePaths.isNotEmpty()
                        )
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = state.selectedFilePaths.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Bulk Delete", tint = ColorStatusError)
                        }
                    }
                }
            }
        } else {
            // Standard Path Navigation & Actions Bar
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

                        // Bookmark toggle
                        IconButton(onClick = {
                            if (isCurrentBookmarked) {
                                viewModel.removeBookmark(state.currentFilePath)
                            } else {
                                viewModel.addBookmark(state.currentFilePath)
                            }
                        }) {
                            Icon(
                                imageVector = if (isCurrentBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Bookmark Directory",
                                tint = if (isCurrentBookmarked) ColorAccentAmber else ColorBodyMid
                            )
                        }

                        // Search toggle
                        IconButton(onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) {
                                viewModel.clearSearch()
                                searchInput = ""
                            }
                        }) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = "Search Files",
                                tint = if (isSearchExpanded) ColorPrimary else ColorBodyMid
                            )
                        }

                        IconButton(onClick = { viewModel.loadFiles(state.currentFilePath) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ColorBodyMid)
                        }
                    }

                    // Directory Bookmarks row
                    if (state.bookmarks.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Text("FAVORITES:", style = TypographyCaptionMonoSM, color = ColorBodyMid, modifier = Modifier.padding(end = 4.dp))
                            }
                            items(state.bookmarks) { bPath ->
                                Surface(
                                    color = if (state.currentFilePath == bPath) ColorCanvasCard else ColorCanvasSoft,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .border(1.dp, if (state.currentFilePath == bPath) ColorPillOutline else ColorHairline, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.loadFiles(bPath) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = ColorAccentAmber, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(bPath, style = TypographyCaptionMonoSM, color = ColorInk)
                                    }
                                }
                            }
                        }
                    }

                    // Search input bar
                    if (isSearchExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = {
                                    searchInput = it
                                    viewModel.searchFiles(it)
                                },
                                placeholder = { Text("Recursive search in current directory...", style = TypographyCaptionMonoSM) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = TypographyCaptionMonoSM.copy(color = ColorInk),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ColorCanvasSoft,
                                    unfocusedContainerColor = ColorCanvasSoft,
                                    focusedBorderColor = ColorPillOutline,
                                    unfocusedBorderColor = ColorHairline,
                                    focusedTextColor = ColorInk,
                                    unfocusedTextColor = ColorInk
                                ),
                                trailingIcon = {
                                    if (searchInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchInput = ""
                                            viewModel.clearSearch()
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = ColorBodyMid)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Action Buttons Row (Upload, New File, New Folder, Multi-select)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XPillOutlineButton(
                            text = "MULTI-SELECT",
                            icon = Icons.Default.Checklist,
                            onClick = { viewModel.toggleMultiSelectMode() },
                            compact = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            XPillButton(
                                text = "UPLOAD",
                                icon = Icons.Default.CloudUpload,
                                onClick = { safOpenDocsLauncher.launch(arrayOf("*/*")) },
                                compact = true
                            )
                            XPillOutlineButton(
                                text = "NEW FILE",
                                icon = Icons.Default.Add,
                                onClick = { showCreateFileDialog = true },
                                compact = true
                            )
                            XPillOutlineButton(
                                text = "NEW FOLDER",
                                icon = Icons.Default.CreateNewFolder,
                                onClick = { showCreateFolderDialog = true },
                                compact = true
                            )
                        }
                    }
                }
            }
        }
        XHairlineDivider()

        // Content Area
        val displayEntries = state.searchResults ?: state.fileEntries

        if (state.selectedServerId == null) {
            EmptyStateView(
                title = "No Server Selected",
                description = "Select or connect to a server from the Dashboard to explore remote files via SFTP.",
                icon = Icons.Default.Dns
            )
        } else if (state.isLoadingFiles || state.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ColorPrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (state.isSearching) "Searching recursively..." else "Loading files...", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                }
            }
        } else if (state.fileErrorMessage != null) {
            EmptyStateView(
                title = "Error Loading Directory",
                description = state.fileErrorMessage ?: "Could not list files",
                icon = Icons.Default.ErrorOutline,
                actionText = "Retry",
                onAction = { viewModel.loadFiles(state.currentFilePath) }
            )
        } else if (displayEntries.isEmpty()) {
            EmptyStateView(
                title = if (state.searchResults != null) "No Search Matches" else "Directory is Empty",
                description = if (state.searchResults != null) "No files match '${state.searchQuery}'" else "No files or folders found in ${state.currentFilePath}",
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
                if (state.searchResults != null) {
                    item {
                        Surface(
                            color = ColorCanvasCard,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Search results for '${state.searchQuery}' (${displayEntries.size})", style = TypographyCaptionMonoSM, color = ColorAccentAmber)
                                TextButton(onClick = { viewModel.clearSearch(); searchInput = "" }) {
                                    Text("CLEAR", style = TypographyCaptionMonoSM, color = ColorInk)
                                }
                            }
                        }
                    }
                }

                items(displayEntries, key = { it.path }) { entry ->
                    val isSelected = state.selectedFilePaths.contains(entry.path)
                    FileRowItem(
                        entry = entry,
                        isMultiSelectMode = state.isMultiSelectMode,
                        isSelected = isSelected,
                        onClick = {
                            if (state.isMultiSelectMode) {
                                viewModel.toggleFileSelection(entry.path)
                            } else {
                                viewModel.openFile(entry)
                            }
                        },
                        onLongClick = {
                            if (!state.isMultiSelectMode) {
                                viewModel.toggleMultiSelectMode(entry.path)
                            } else {
                                viewModel.toggleFileSelection(entry.path)
                            }
                        },
                        onRename = { itemToRename = entry },
                        onDelete = { itemToDelete = entry },
                        onChmod = { itemToChmod = entry },
                        onDownload = {
                            pendingDownloadEntry = entry
                            safCreateDocLauncher.launch(entry.name)
                        },
                        onCompress = { viewModel.compressFile(entry.path, "zip") }
                    )
                }
            }
        }
    }

    // File Preview / Text Editor Modal with Syntax Highlighting
    state.selectedPreview?.let { preview ->
        val extension = preview.path.substringAfterLast('.', "")
        val syntaxTransformation = remember(extension) {
            SyntaxVisualTransformation(extension)
        }

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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatBytes(preview.size)} | ${preview.mime}",
                                style = TypographyCaptionMonoSM,
                                color = ColorBodyMid
                            )
                            if (extension.isNotBlank()) {
                                Surface(
                                    color = ColorCanvasSoft,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(4.dp))
                                ) {
                                    Text(
                                        text = extension.uppercase(),
                                        style = TypographyCaptionMonoSM,
                                        color = SyntaxKeyword,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
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
                                .height(320.dp)
                                .testTag("file_editor_textarea"),
                            visualTransformation = syntaxTransformation,
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

    // Chmod / Chown Permissions Dialog
    itemToChmod?.let { entry ->
        ChmodChownDialog(
            entry = entry,
            onDismiss = { itemToChmod = null },
            onApply = { octal, owner, group ->
                viewModel.chmodFile(entry.path, octal)
                if (owner.isNotBlank()) {
                    viewModel.chownFile(entry.path, owner, group)
                }
                itemToChmod = null
            }
        )
    }

    // Bulk Copy / Move Dialog
    bulkDestAction?.let { action ->
        var destDir by remember { mutableStateOf(state.currentFilePath) }
        AlertDialog(
            onDismissRequest = { bulkDestAction = null },
            title = { Text("${action.uppercase()} ${state.selectedFilePaths.size} Item(s)", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Column {
                    Text("Select destination directory on the remote server:", style = TypographyBodySM, color = ColorBody)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = destDir,
                        onValueChange = { destDir = it },
                        label = { Text("Destination Directory", style = TypographyCaptionMonoSM) },
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
                }
            },
            confirmButton = {
                XPillButton(
                    text = action.uppercase(),
                    onClick = {
                        if (action == "copy") {
                            viewModel.bulkCopySelected(destDir.trim())
                        } else {
                            viewModel.bulkMoveSelected(destDir.trim())
                        }
                        bulkDestAction = null
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { bulkDestAction = null }, compact = true)
            },
            shape = RoundedCornerShape(8.dp),
            containerColor = ColorCanvasCard,
            modifier = Modifier.border(1.dp, ColorHairline, RoundedCornerShape(8.dp))
        )
    }

    // Bulk Delete Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorStatusError) },
            title = { Text("Delete ${state.selectedFilePaths.size} Item(s)?", style = TypographyDisplayXS, color = ColorInk) },
            text = {
                Text(
                    "Are you sure you want to permanently delete ${state.selectedFilePaths.size} selected items? This action cannot be undone.",
                    style = TypographyBodySM,
                    color = ColorBody
                )
            },
            confirmButton = {
                XPillButton(
                    text = "DELETE ALL",
                    onClick = {
                        viewModel.bulkDeleteSelected()
                        showBulkDeleteDialog = false
                    }
                )
            },
            dismissButton = {
                XPillOutlineButton(text = "CANCEL", onClick = { showBulkDeleteDialog = false }, compact = true)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    entry: FileEntry,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChmod: () -> Unit,
    onDownload: () -> Unit,
    onCompress: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDir = entry.type == "dir"

    val icon: ImageVector = when {
        isDir -> Icons.Default.Folder
        entry.name.endsWith(".zip") || entry.name.endsWith(".tar.gz") || entry.name.endsWith(".tgz") -> Icons.Default.Archive
        entry.name.endsWith(".sh") || entry.name.endsWith(".py") || entry.name.endsWith(".go") || entry.name.endsWith(".js") || entry.name.endsWith(".json") || entry.name.endsWith(".yaml") || entry.name.endsWith(".yml") -> Icons.Default.Code
        entry.name.endsWith(".env") || entry.name.endsWith(".ini") || entry.name.endsWith(".conf") -> Icons.Default.Tune
        else -> Icons.Default.Description
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ColorCanvasSoft else ColorCanvasCard
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isSelected) ColorPillOutline else ColorHairline, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ColorPillOutline,
                            checkmarkColor = ColorCanvas,
                            uncheckedColor = ColorBodyMid
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

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
                            tint = if (isDir) ColorAccentAmber else ColorInk,
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
                    if (!isDir) {
                        DropdownMenuItem(
                            text = { Text("Download (SAF)", style = TypographyBodySM, color = ColorInk) },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = ColorPrimary) },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Permissions (chmod/chown)", style = TypographyBodySM, color = ColorInk) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = ColorBodyMid) },
                        onClick = {
                            menuExpanded = false
                            onChmod()
                        }
                    )
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

/**
 * Chmod & Chown permissions editor dialog with 3x3 checkboxes and octal display.
 */
@Composable
fun ChmodChownDialog(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onApply: (octal: Int, owner: String, group: String) -> Unit
) {
    val initialFlags = remember(entry.mode) {
        ChmodUtils.parsePermissionsString(entry.mode)
    }

    var flags by remember { mutableStateOf(initialFlags) }
    var ownerName by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }

    val currentOctal = remember(flags) {
        ChmodUtils.booleansToOctal(flags)
    }
    val octalDisplay = remember(currentOctal) {
        ChmodUtils.formatOctalString(currentOctal)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Permissions: ${entry.name}", style = TypographyDisplayXS, color = ColorInk)
                Surface(
                    color = ColorCanvasSoft,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.dp, ColorPillOutline, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        octalDisplay,
                        style = TypographyDisplayXS,
                        color = ColorPillOutline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Table of Owner, Group, Other x r, w, x
                val roles = listOf("Owner", "Group", "Other")
                roles.forEachIndexed { rIdx, role ->
                    val baseIdx = rIdx * 3
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(role, style = TypographyBodySM, color = ColorInk, modifier = Modifier.width(60.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckboxItem(
                                label = "r",
                                checked = flags[baseIdx],
                                onCheckedChange = { chk ->
                                    val copy = flags.clone()
                                    copy[baseIdx] = chk
                                    flags = copy
                                }
                            )
                            CheckboxItem(
                                label = "w",
                                checked = flags[baseIdx + 1],
                                onCheckedChange = { chk ->
                                    val copy = flags.clone()
                                    copy[baseIdx + 1] = chk
                                    flags = copy
                                }
                            )
                            CheckboxItem(
                                label = "x",
                                checked = flags[baseIdx + 2],
                                onCheckedChange = { chk ->
                                    val copy = flags.clone()
                                    copy[baseIdx + 2] = chk
                                    flags = copy
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                XHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("CHOWN (Optional)", style = TypographyCaptionMonoSM, color = ColorBodyMid)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner", style = TypographyCaptionMonoSM) },
                        placeholder = { Text("e.g. root", style = TypographyCaptionMonoSM) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group", style = TypographyCaptionMonoSM) },
                        placeholder = { Text("e.g. www-data", style = TypographyCaptionMonoSM) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
            XPillButton(
                text = "APPLY",
                onClick = {
                    onApply(currentOctal, ownerName.trim(), groupName.trim())
                }
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

@Composable
fun CheckboxItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = ColorPillOutline,
                checkmarkColor = ColorCanvas,
                uncheckedColor = ColorBodyMid
            )
        )
        Text(label, style = TypographyCaptionMonoSM, color = ColorInk)
    }
}
