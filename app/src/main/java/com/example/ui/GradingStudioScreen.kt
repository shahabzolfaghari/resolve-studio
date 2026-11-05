package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.state.*
import com.example.ui.theme.*
import com.example.video.SampleScene
import com.example.video.VideoExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradingStudioScreen(
    viewModel: GradingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.gradeSettings.collectAsStateWithLifecycle()
    val currentScene by viewModel.currentScene.collectAsStateWithLifecycle()
    val rawBitmap by viewModel.rawFrameBitmap.collectAsStateWithLifecycle()
    val gradedBitmap by viewModel.gradedFrameBitmap.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val totalDurationMs by viewModel.totalDurationMs.collectAsStateWithLifecycle()

    val timelineClips by viewModel.timelineClips.collectAsStateWithLifecycle()
    val activeClipIndex by viewModel.activeClipIndex.collectAsStateWithLifecycle()
    val copiedClipGrade by viewModel.copiedClipGrade.collectAsStateWithLifecycle()

    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val historyEntries by viewModel.historyEntries.collectAsStateWithLifecycle()
    val historyActionToast by viewModel.historyActionToast.collectAsStateWithLifecycle()

    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportStatusMessage by viewModel.exportStatusMessage.collectAsStateWithLifecycle()

    val uiStateManager = rememberStudioUiStateManager()
    var activeTab by remember { mutableStateOf(StudioToolTab.WHEELS) }
    var showScopes by remember { mutableStateOf(false) }
    var showSceneMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSaveLutDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showPresetManagerDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHistogramOverlay by remember { mutableStateOf(false) }
    val savedPresets by viewModel.savedPresets.collectAsStateWithLifecycle()

    // Auto-dismiss history toast after 2 seconds
    LaunchedEffect(historyActionToast) {
        if (historyActionToast != null) {
            kotlinx.coroutines.delay(2000L)
            viewModel.clearActionToast()
        }
    }

    // Android Zero-Permission Photo/Video Picker (compliant with Google Play policies)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImportVideoUri(uri)
            Toast.makeText(context, "Loaded Samsung S24 video clip", Toast.LENGTH_SHORT).show()
        }
    }

    // .cube LUT File Picker
    val cubeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    viewModel.onImportCubeLut(content)
                    Toast.makeText(context, "Imported custom .cube 3D LUT!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read .cube file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground),
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        // Film / Color Tuner Logo Icon
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = ResolveAmber,
                            modifier = Modifier
                                .size(17.dp)
                                .padding(end = 4.dp)
                        )

                        // Combined non-wrapping Title (RESOLVE in White, STUDIO in Amber)
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Black)) {
                                    append("RESOLVE")
                                }
                                withStyle(SpanStyle(color = ResolveAmber, fontWeight = FontWeight.Black)) {
                                    append("STUDIO")
                                }
                            },
                            fontSize = 13.5.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        // Compact S24 Log Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E2838))
                                .border(1.dp, Color(0xFF2E3E58), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "S24 LOG",
                                color = ResolveCyan,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.size(30.dp).testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) ResolveAmber else TextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.size(30.dp).testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) ResolveAmber else TextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Video Clip & Scene Selector Menu
                    Box {
                        IconButton(
                            onClick = { showSceneMenu = true },
                            modifier = Modifier.size(30.dp).testTag("scene_selector_button")
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Scenes", tint = TextSecondary, modifier = Modifier.size(17.dp))
                        }

                        DropdownMenu(
                            expanded = showSceneMenu,
                            onDismissRequest = { showSceneMenu = false },
                            modifier = Modifier.background(StudioSurfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Camera Roll Video...", color = ResolveCyan, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = ResolveCyan) },
                                onClick = {
                                    showSceneMenu = false
                                    videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                modifier = Modifier.testTag("import_video_menu_item")
                            )

                            HorizontalDivider(color = StudioCardBorder)

                            SampleScene.values().forEach { scene ->
                                DropdownMenuItem(
                                    text = { Text(scene.sceneName, color = TextPrimary, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = if (currentScene == scene) ResolveAmber else TextMuted
                                        )
                                    },
                                    onClick = {
                                        showSceneMenu = false
                                        viewModel.loadScene(scene)
                                    }
                                )
                            }
                        }
                    }

                    // Export Button CTA
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ResolveAmber),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("open_export_dialog_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF1E1400), modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("EXPORT", color = Color(0xFF1E1400), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Studio Overflow Menu (History, Presets, Dock mode, Reset)
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(30.dp).testTag("studio_more_options_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Studio Options", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(StudioSurfaceVariant)
                        ) {
                            // Look Presets Browser
                            DropdownMenuItem(
                                text = { Text("Look Presets (${savedPresets.size})", color = TextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, tint = ResolveAmber) },
                                onClick = {
                                    showMoreMenu = false
                                    showPresetManagerDialog = true
                                },
                                modifier = Modifier.testTag("presets_button")
                            )

                            // History Stack
                            DropdownMenuItem(
                                text = { Text("History (${historyEntries.size - 1} steps)", color = TextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = ResolveCyan) },
                                onClick = {
                                    showMoreMenu = false
                                    showHistoryDialog = true
                                },
                                modifier = Modifier.testTag("history_button")
                            )

                            // Real-time Histogram Scope Overlay Toggle
                            DropdownMenuItem(
                                text = { Text(if (showHistogramOverlay) "Hide Histogram Overlay" else "Show Histogram Scope Overlay", color = TextPrimary, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null, tint = ResolveCyan) },
                                onClick = {
                                    showMoreMenu = false
                                    showHistogramOverlay = !showHistogramOverlay
                                },
                                modifier = Modifier.testTag("menu_histogram_overlay_toggle")
                            )

                            // Toggle Dock Mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (uiStateManager.dockMode == DockPresentationMode.COLLAPSED_FLOATING)
                                            "Pin Studio Dock" else "Collapse to Floating Menu",
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (uiStateManager.dockMode == DockPresentationMode.COLLAPSED_FLOATING)
                                            Icons.Default.Dock else Icons.Default.CloseFullscreen,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    uiStateManager.toggleDockMode()
                                },
                                modifier = Modifier.testTag("toggle_dock_mode_button")
                            )

                            HorizontalDivider(color = StudioCardBorder)

                            // Reset Grade
                            DropdownMenuItem(
                                text = { Text("Reset Grade to Default", color = Color(0xFFF87171), fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFF87171)) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.resetGrade()
                                    Toast.makeText(context, "Reset all color wheels and curves", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("reset_all_grade_button")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioSurface)
            )
        },
        bottomBar = {
            // Studio Dock Bottom Navigation (only visible when in DOCKED mode)
            if (uiStateManager.dockMode == DockPresentationMode.DOCKED) {
                StudioDockTabs(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    showScopes = showScopes,
                    onToggleScopes = { showScopes = !showScopes },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val widthDp = maxWidth
                val heightDp = maxHeight

                LaunchedEffect(widthDp, heightDp) {
                    uiStateManager.updateScreenDimensions(widthDp, heightDp)
                }

                when (uiStateManager.dockMode) {
                    DockPresentationMode.COLLAPSED_FLOATING, DockPresentationMode.FLOATING_SHEET -> {
                        // ==========================================
                        // COLLAPSED STUDIO MODE (SMALL SCREENS)
                        // Video preview is 100% full-width and immersive.
                        // Studio dock is collapsed into the Floating Action Menu.
                        // ==========================================
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 76.dp)
                            ) {
                                VideoPlayerView(
                                    gradedBitmap = gradedBitmap,
                                    rawBitmap = rawBitmap,
                                    isPlaying = isPlaying,
                                    currentTimeMs = currentTimeMs,
                                    totalDurationMs = totalDurationMs,
                                    mask = settings.mask,
                                    showMaskGuide = activeTab == StudioToolTab.MASK,
                                    onMaskChange = { newMask ->
                                        viewModel.updateSettings(description = "Adjust Power Window") { it.copy(mask = newMask) }
                                    },
                                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onStepFrame = { viewModel.stepFrame(it) },
                                    onBypassPress = { viewModel.setBypass(it) },
                                    showFalseColor = settings.showFalseColor,
                                    onToggleFalseColor = {
                                        viewModel.updateSettings("Toggle False Color") { it.copy(showFalseColor = !it.showFalseColor) }
                                    },
                                    showHistogramOverlay = showHistogramOverlay,
                                    onToggleHistogramOverlay = { showHistogramOverlay = !showHistogramOverlay },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                MultiClipTimelineStrip(
                                    clips = timelineClips,
                                    activeClipIndex = activeClipIndex,
                                    currentTimeMs = currentTimeMs,
                                    totalDurationMs = totalDurationMs,
                                    hasCopiedGrade = copiedClipGrade != null,
                                    onSelectClip = { viewModel.selectTimelineClip(it) },
                                    onCopyGrade = { viewModel.copyActiveClipGrade() },
                                    onPasteGrade = { viewModel.pasteGradeToActiveClip() },
                                    onSyncAllGrades = { viewModel.syncGradeToAllClips() },
                                    onAddSampleClip = { viewModel.addClipToTimeline(it) },
                                    onImportVideoClip = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    onRemoveClip = { viewModel.removeClipAt(it) },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                if (showScopes) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ScopesView(
                                        previewBitmap = gradedBitmap,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            // DaVinci Resolve Floating Action Menu (Speed Dial)
                            StudioFloatingActionMenu(
                                activeTab = activeTab,
                                isExpanded = uiStateManager.isFloatingMenuExpanded,
                                onToggleExpanded = { uiStateManager.toggleFloatingMenu() },
                                onSelectTab = { activeTab = it },
                                onOpenToolSheet = { uiStateManager.openToolSheet() },
                                onExpandDock = { uiStateManager.expandToDocked() },
                                onBypassToggle = { viewModel.setBypass(!settings.bypass) },
                                isBypassActive = settings.bypass,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            )
                        }
                    }

                    DockPresentationMode.DOCKED -> {
                        // ==========================================
                        // DOCKED STUDIO MODE (EXPANDED SCREENS / PINNED)
                        // ==========================================
                        val isLandscape = maxWidth > maxHeight && maxWidth > 560.dp

                        if (isLandscape) {
                            // Landscape Studio Mode: Side-by-side Video Monitor and Studio Dock
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                ) {
                                    VideoPlayerView(
                                        gradedBitmap = gradedBitmap,
                                        rawBitmap = rawBitmap,
                                        isPlaying = isPlaying,
                                        currentTimeMs = currentTimeMs,
                                        totalDurationMs = totalDurationMs,
                                        mask = settings.mask,
                                        showMaskGuide = activeTab == StudioToolTab.MASK,
                                        onMaskChange = { newMask ->
                                            viewModel.updateSettings(description = "Adjust Power Window") { it.copy(mask = newMask) }
                                        },
                                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                                        onSeek = { viewModel.seekTo(it) },
                                        onStepFrame = { viewModel.stepFrame(it) },
                                        onBypassPress = { viewModel.setBypass(it) },
                                        showFalseColor = settings.showFalseColor,
                                        onToggleFalseColor = {
                                            viewModel.updateSettings("Toggle False Color") { it.copy(showFalseColor = !it.showFalseColor) }
                                        },
                                        showHistogramOverlay = showHistogramOverlay,
                                        onToggleHistogramOverlay = { showHistogramOverlay = !showHistogramOverlay },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(4.dp)
                                ) {
                                    MultiClipTimelineStrip(
                                        clips = timelineClips,
                                        activeClipIndex = activeClipIndex,
                                        currentTimeMs = currentTimeMs,
                                        totalDurationMs = totalDurationMs,
                                        hasCopiedGrade = copiedClipGrade != null,
                                        onSelectClip = { viewModel.selectTimelineClip(it) },
                                        onCopyGrade = { viewModel.copyActiveClipGrade() },
                                        onPasteGrade = { viewModel.pasteGradeToActiveClip() },
                                        onSyncAllGrades = { viewModel.syncGradeToAllClips() },
                                        onAddSampleClip = { viewModel.addClipToTimeline(it) },
                                        onImportVideoClip = {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        onRemoveClip = { viewModel.removeClipAt(it) },
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    StudioDockContainer(
                                        activeTab = activeTab,
                                        onTabSelected = { activeTab = it },
                                        settings = settings,
                                        onSettingsChange = { description, transform ->
                                            viewModel.updateSettings(description = description, transform = transform)
                                        },
                                        previewBitmap = gradedBitmap,
                                        onImportCubeClick = { cubeFileLauncher.launch(arrayOf("*/*")) },
                                        onCreateCustomLutClick = { showSaveLutDialog = true },
                                        onDeleteCustomLut = { viewModel.deleteCustomLut(it) },
                                        presets = savedPresets,
                                        onApplyPreset = { viewModel.applyPreset(it) },
                                        onCreatePresetClick = { showSavePresetDialog = true },
                                        onDeletePreset = { viewModel.deletePreset(it) },
                                        onCollapseToFloating = { uiStateManager.collapseToFloating() },
                                        showCollapseButton = true,
                                        showInternalTabBar = false,
                                        showScopesExternal = showScopes,
                                        onScopesExternalChange = { showScopes = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Portrait Mobile Mode: Pinned Video Viewport at Top + Scrollable Tool Workspace Below
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                VideoPlayerView(
                                    gradedBitmap = gradedBitmap,
                                    rawBitmap = rawBitmap,
                                    isPlaying = isPlaying,
                                    currentTimeMs = currentTimeMs,
                                    totalDurationMs = totalDurationMs,
                                    mask = settings.mask,
                                    showMaskGuide = activeTab == StudioToolTab.MASK,
                                    onMaskChange = { newMask ->
                                        viewModel.updateSettings(description = "Adjust Power Window") { it.copy(mask = newMask) }
                                    },
                                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onStepFrame = { viewModel.stepFrame(it) },
                                    onBypassPress = { viewModel.setBypass(it) },
                                    showFalseColor = settings.showFalseColor,
                                    onToggleFalseColor = {
                                        viewModel.updateSettings("Toggle False Color") { it.copy(showFalseColor = !it.showFalseColor) }
                                    },
                                    showHistogramOverlay = showHistogramOverlay,
                                    onToggleHistogramOverlay = { showHistogramOverlay = !showHistogramOverlay },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                MultiClipTimelineStrip(
                                    clips = timelineClips,
                                    activeClipIndex = activeClipIndex,
                                    currentTimeMs = currentTimeMs,
                                    totalDurationMs = totalDurationMs,
                                    hasCopiedGrade = copiedClipGrade != null,
                                    onSelectClip = { viewModel.selectTimelineClip(it) },
                                    onCopyGrade = { viewModel.copyActiveClipGrade() },
                                    onPasteGrade = { viewModel.pasteGradeToActiveClip() },
                                    onSyncAllGrades = { viewModel.syncGradeToAllClips() },
                                    onAddSampleClip = { viewModel.addClipToTimeline(it) },
                                    onImportVideoClip = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    onRemoveClip = { viewModel.removeClipAt(it) },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                // Scrollable Studio Dock Workspace (Video stays pinned above!)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    StudioDockContainer(
                                        activeTab = activeTab,
                                        onTabSelected = { activeTab = it },
                                        settings = settings,
                                        onSettingsChange = { description, transform ->
                                            viewModel.updateSettings(description = description, transform = transform)
                                        },
                                        previewBitmap = gradedBitmap,
                                        onImportCubeClick = { cubeFileLauncher.launch(arrayOf("*/*")) },
                                        onCreateCustomLutClick = { showSaveLutDialog = true },
                                        onDeleteCustomLut = { viewModel.deleteCustomLut(it) },
                                        presets = savedPresets,
                                        onApplyPreset = { viewModel.applyPreset(it) },
                                        onCreatePresetClick = { showSavePresetDialog = true },
                                        onDeletePreset = { viewModel.deletePreset(it) },
                                        onCollapseToFloating = { uiStateManager.collapseToFloating() },
                                        showCollapseButton = true,
                                        showInternalTabBar = false,
                                        showScopesExternal = showScopes,
                                        onScopesExternalChange = { showScopes = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating History Action HUD Banner
            AnimatedVisibility(
                visible = historyActionToast != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = innerPadding.calculateTopPadding() + 8.dp)
            ) {
                historyActionToast?.let { toastText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xEE1E2433))
                            .border(1.dp, ResolveAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (toastText.startsWith("Undo")) Icons.Default.Undo else Icons.Default.Redo,
                                contentDescription = null,
                                tint = ResolveAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = toastText,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Save Custom LUT Dialog
    if (showSaveLutDialog) {
        SaveLutDialog(
            onDismiss = { showSaveLutDialog = false },
            onSaveLut = { name, description ->
                viewModel.saveCurrentGradeAsLut(name, description)
                showSaveLutDialog = false
                Toast.makeText(context, "Saved LUT '$name' to your 3D library!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Studio Floating Tool Sheet Modal (opened from Floating Action Menu on smaller screens)
    if (uiStateManager.isToolSheetOpen) {
        StudioFloatingToolSheet(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            settings = settings,
            onSettingsChange = { description, transform ->
                viewModel.updateSettings(description = description, transform = transform)
            },
            previewBitmap = gradedBitmap,
            onImportCubeClick = { cubeFileLauncher.launch(arrayOf("*/*")) },
            onCreateCustomLutClick = { showSaveLutDialog = true },
            onDeleteCustomLut = { viewModel.deleteCustomLut(it) },
            presets = savedPresets,
            onApplyPreset = { viewModel.applyPreset(it) },
            onCreatePresetClick = { showSavePresetDialog = true },
            onDeletePreset = { viewModel.deletePreset(it) },
            onExpandToStaticDock = {
                uiStateManager.expandToDocked()
                uiStateManager.closeToolSheet()
            },
            onDismiss = { uiStateManager.closeToolSheet() }
        )
    }

    // Save User Preset Dialog
    if (showSavePresetDialog) {
        SavePresetDialog(
            currentSettings = settings,
            onDismiss = { showSavePresetDialog = false },
            onSavePreset = { name, description, category ->
                viewModel.saveCurrentAsPreset(name, description, category)
                showSavePresetDialog = false
                Toast.makeText(context, "Saved look \"$name\" to presets!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Preset Manager Modal Dialog
    if (showPresetManagerDialog) {
        PresetManagerDialog(
            presets = savedPresets,
            onDismiss = { showPresetManagerDialog = false },
            onApplyPreset = { preset ->
                viewModel.applyPreset(preset)
                Toast.makeText(context, "Applied look: ${preset.name}", Toast.LENGTH_SHORT).show()
            },
            onCreatePresetClick = {
                showPresetManagerDialog = false
                showSavePresetDialog = true
            },
            onDeletePreset = { presetId ->
                viewModel.deletePreset(presetId)
                Toast.makeText(context, "Preset deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Grade History Dialog
    if (showHistoryDialog) {
        HistoryDialog(
            historyEntries = historyEntries,
            onDismiss = { showHistoryDialog = false },
            onSelectEntry = { entry ->
                viewModel.jumpToHistory(entry)
                showHistoryDialog = false
            },
            onResetAll = {
                viewModel.resetGrade()
                showHistoryDialog = false
            }
        )
    }

    // Export Modal Dialog
    if (showExportDialog) {
        ExportDialog(
            isExporting = isExporting,
            exportProgress = exportProgress,
            statusMessage = exportStatusMessage,
            onDismiss = { showExportDialog = false },
            onExportVideo = { config ->
                viewModel.exportVideo(config) { exportedFile ->
                    showExportDialog = false
                    Toast.makeText(context, "Master video ready: ${exportedFile.name}", Toast.LENGTH_LONG).show()
                    VideoExporter.shareFile(context, exportedFile, "video/mp4", "Graded Video: ${exportedFile.name}")
                }
            },
            onExportStill = {
                viewModel.exportStillFrame { exportedFile ->
                    showExportDialog = false
                    Toast.makeText(context, "Saved frame: ${exportedFile.name}", Toast.LENGTH_LONG).show()
                    VideoExporter.shareFile(context, exportedFile, "image/png", "Graded Still: ${exportedFile.name}")
                }
            },
            onExportCubeLut = {
                viewModel.exportCubeLut { exportedFile ->
                    showExportDialog = false
                    Toast.makeText(context, "Exported 3D LUT: ${exportedFile.name}", Toast.LENGTH_LONG).show()
                    VideoExporter.shareFile(context, exportedFile, "text/plain", "DaVinci Resolve 3D LUT")
                }
            }
        )
    }
}
