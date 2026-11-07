package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DockMonitorMode(val label: String, val icon: ImageVector) {
    SPLIT_SCOPE("Scope + Tool", Icons.Default.ViewAgenda),
    FULL_TOOL("Full Tool", Icons.Default.Fullscreen),
    FULL_SCOPES("Full Scopes", Icons.Default.Equalizer)
}

/**
 * A dedicated DaVinci Resolve-inspired layout container for the UI Studio Dock.
 * Hosts and coordinates the primary color wheels, spline curve editors,
 * and live broadcast scopes (histogram, vectorscope, RGB waveform parade).
 */
@Composable
fun StudioDockContainer(
    activeTab: StudioToolTab,
    onTabSelected: (StudioToolTab) -> Unit,
    settings: ColorGradeSettings,
    onSettingsChange: (description: String, transform: (ColorGradeSettings) -> ColorGradeSettings) -> Unit,
    previewBitmap: Bitmap?,
    onImportCubeClick: () -> Unit,
    onCreateCustomLutClick: () -> Unit = {},
    onDeleteCustomLut: (String) -> Unit = {},
    presets: List<GradePreset> = emptyList(),
    onApplyPreset: (GradePreset) -> Unit = {},
    onCreatePresetClick: () -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    isMaskTracking: Boolean = false,
    maskTrackingDirection: Int = 1,
    onTrackMaskForward: () -> Unit = {},
    onTrackMaskBackward: () -> Unit = {},
    onTrackMaskStep: () -> Unit = {},
    onClearMaskTrack: () -> Unit = {},
    onCollapseToFloating: () -> Unit = {},
    showCollapseButton: Boolean = true,
    showInternalTabBar: Boolean = false,
    showScopesExternal: Boolean? = null,
    onScopesExternalChange: ((Boolean) -> Unit)? = null,
    initialUseLogWheels: Boolean = false,
    modifier: Modifier = Modifier
) {
    var monitorMode by remember { mutableStateOf(DockMonitorMode.FULL_TOOL) }
    var activeScopeMode by remember { mutableStateOf(ScopeMode.VECTORSCOPE) }
    var useGridWheels by remember { mutableStateOf(true) }
    var useLogWheels by remember { mutableStateOf(initialUseLogWheels) }

    // Synchronize external showScopes toggle with internal DockMonitorMode
    LaunchedEffect(showScopesExternal) {
        if (showScopesExternal != null) {
            if (showScopesExternal && monitorMode == DockMonitorMode.FULL_TOOL) {
                monitorMode = DockMonitorMode.SPLIT_SCOPE
            } else if (!showScopesExternal && monitorMode != DockMonitorMode.FULL_TOOL) {
                monitorMode = DockMonitorMode.FULL_TOOL
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("studio_dock_container")
            .padding(horizontal = 6.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // ==========================================
            // 1. DOCK HEADER CONSOLE BAR
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1218))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                // Title & Active Mode Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DOCK",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Channel Readout Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1F2634))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when (activeTab) {
                                StudioToolTab.WHEELS -> "PRIMARIES"
                                StudioToolTab.CURVES -> "SPLINES"
                                StudioToolTab.CST -> "CST NODE"
                                StudioToolTab.LUTS -> "3D LUTS"
                                StudioToolTab.PRESETS -> "PRESETS"
                                StudioToolTab.MASK -> "WINDOW"
                                StudioToolTab.EFFECTS -> "FILM FX"
                            },
                            color = ResolveAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 1-Tap Quick CST (Log -> Rec.709) Toggle Pill before editing
                    Surface(
                        onClick = {
                            val nextEnabled = !settings.cstNode.enabled
                            onSettingsChange(
                                if (nextEnabled) "Enable CST (Log → Rec.709)" else "Bypass CST (Raw Log)"
                            ) {
                                it.copy(cstNode = it.cstNode.copy(enabled = nextEnabled))
                            }
                        },
                        color = if (settings.cstNode.enabled) ResolveGreen.copy(alpha = 0.22f) else Color(0xFF1B2332),
                        shape = RoundedCornerShape(6.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (settings.cstNode.enabled) ResolveGreen else ResolveCyan.copy(alpha = 0.65f)
                            )
                        ),
                        modifier = Modifier.testTag("quick_cst_log_to_rec709_button")
                    ) {
                        Text(
                            text = if (settings.cstNode.enabled) "CST 709: ON" else "LOG → CST 709",
                            color = if (settings.cstNode.enabled) ResolveGreen else ResolveCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Scope & Tool Monitor View Toggles
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF171B24))
                        .padding(2.dp)
                ) {
                    DockMonitorMode.values().forEach { mode ->
                        val isSelected = monitorMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSelected) ResolveAmber.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable {
                                    monitorMode = mode
                                    onScopesExternalChange?.invoke(mode != DockMonitorMode.FULL_TOOL)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("dock_monitor_mode_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.label,
                                    tint = if (isSelected) ResolveAmber else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) ResolveAmber else TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                if (showCollapseButton) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onCollapseToFloating,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF171B24))
                            .testTag("collapse_dock_to_floating_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloseFullscreen,
                            contentDescription = "Collapse Dock to Floating Menu",
                            tint = ResolveCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // 2. REAL-TIME SCOPE MONITOR STRIP
            // (Visible in SPLIT_SCOPE and FULL_SCOPES modes)
            // ==========================================
            AnimatedVisibility(
                visible = monitorMode == DockMonitorMode.SPLIT_SCOPE || monitorMode == DockMonitorMode.FULL_SCOPES,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    // Scope Channel Selector Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE VIDEO ANALYSIS",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF10131B))
                                .padding(1.dp)
                        ) {
                            ScopeMode.values().forEach { sMode ->
                                val isSelected = activeScopeMode == sMode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isSelected) ResolveCyan.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable { activeScopeMode = sMode }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("dock_scope_selector_${sMode.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (sMode) {
                                            ScopeMode.WAVEFORM_PARADE -> "Parade"
                                            ScopeMode.VECTORSCOPE -> "Vectorscope"
                                            ScopeMode.HISTOGRAM -> "Histogram"
                                        },
                                        color = if (isSelected) ResolveCyan else TextMuted,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Scope Display Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (monitorMode == DockMonitorMode.FULL_SCOPES) 160.dp else 110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090B0E))
                            .border(1.dp, Color(0xFF1C2230), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap == null) {
                            Text("Awaiting live signal...", color = TextMuted, fontSize = 10.sp)
                        } else {
                            when (activeScopeMode) {
                                ScopeMode.WAVEFORM_PARADE -> WaveformParadeCanvas(previewBitmap)
                                ScopeMode.VECTORSCOPE -> VectorscopeCanvas(previewBitmap)
                                ScopeMode.HISTOGRAM -> HistogramCanvas(previewBitmap)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. COLOR GRADING TOOLS SECTION
            // (Visible in SPLIT_SCOPE and FULL_TOOL modes)
            // ==========================================
            AnimatedVisibility(
                visible = monitorMode != DockMonitorMode.FULL_SCOPES,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (activeTab) {
                        StudioToolTab.WHEELS -> {
                            // Primary & Log Zone Color Wheels Deck
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    // Mode Switcher: [PRIMARIES] vs [LOG WHEELS]
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0E121A))
                                            .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        Surface(
                                            onClick = { useLogWheels = false },
                                            color = if (!useLogWheels) ResolveAmber.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("wheels_mode_primaries")
                                        ) {
                                            Text(
                                                text = "PRIMARIES",
                                                color = if (!useLogWheels) ResolveAmber else TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                        Surface(
                                            onClick = { useLogWheels = true },
                                            color = if (useLogWheels) ResolveCyan.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("wheels_mode_log")
                                        ) {
                                            Text(
                                                text = "LOG WHEELS",
                                                color = if (useLogWheels) ResolveCyan else TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    // Layout Switcher: 2x2 Grid (No Scroll Conflict) vs 4x1 Strip
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF10131B))
                                            .padding(2.dp)
                                    ) {
                                        Surface(
                                            onClick = { useGridWheels = true },
                                            color = if (useGridWheels) ResolveAmber.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("wheels_layout_grid")
                                        ) {
                                            Text(
                                                text = "2×2 GRID",
                                                color = if (useGridWheels) ResolveAmber else TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                        Surface(
                                            onClick = { useGridWheels = false },
                                            color = if (!useGridWheels) ResolveAmber.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("wheels_layout_strip")
                                        ) {
                                            Text(
                                                text = "STRIP",
                                                color = if (!useGridWheels) ResolveAmber else TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (!useLogWheels) {
                                    // =========================================================
                                    // STANDARD DAVINCI RESOLVE PRIMARY WHEELS (LIFT/GAMMA/GAIN)
                                    // =========================================================
                                    if (useGridWheels) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                ColorWheel(
                                                    title = "Lift",
                                                    accentColor = WheelLiftColor,
                                                    value = settings.lift,
                                                    onValueChange = { newLift -> onSettingsChange("Adjust Lift (Shadows)") { it.copy(lift = newLift) } },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ColorWheel(
                                                    title = "Gamma",
                                                    accentColor = WheelGammaColor,
                                                    value = settings.gamma,
                                                    onValueChange = { newGamma -> onSettingsChange("Adjust Gamma (Midtones)") { it.copy(gamma = newGamma) } },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                ColorWheel(
                                                    title = "Gain",
                                                    accentColor = WheelGainColor,
                                                    value = settings.gain,
                                                    onValueChange = { newGain -> onSettingsChange("Adjust Gain (Highlights)") { it.copy(gain = newGain) } },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ColorWheel(
                                                    title = "Offset",
                                                    accentColor = WheelOffsetColor,
                                                    value = settings.offset,
                                                    onValueChange = { newOffset -> onSettingsChange("Adjust Offset") { it.copy(offset = newOffset) } },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 2.dp)
                                        ) {
                                            ColorWheel(
                                                title = "Lift",
                                                accentColor = WheelLiftColor,
                                                value = settings.lift,
                                                onValueChange = { newLift -> onSettingsChange("Adjust Lift (Shadows)") { it.copy(lift = newLift) } },
                                                modifier = Modifier.width(172.dp)
                                            )

                                            ColorWheel(
                                                title = "Gamma",
                                                accentColor = WheelGammaColor,
                                                value = settings.gamma,
                                                onValueChange = { newGamma -> onSettingsChange("Adjust Gamma (Midtones)") { it.copy(gamma = newGamma) } },
                                                modifier = Modifier.width(172.dp)
                                            )

                                            ColorWheel(
                                                title = "Gain",
                                                accentColor = WheelGainColor,
                                                value = settings.gain,
                                                onValueChange = { newGain -> onSettingsChange("Adjust Gain (Highlights)") { it.copy(gain = newGain) } },
                                                modifier = Modifier.width(172.dp)
                                            )

                                            ColorWheel(
                                                title = "Offset",
                                                accentColor = WheelOffsetColor,
                                                value = settings.offset,
                                                onValueChange = { newOffset -> onSettingsChange("Adjust Offset") { it.copy(offset = newOffset) } },
                                                modifier = Modifier.width(172.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // =========================================================
                                    // DAVINCI RESOLVE LOG ZONE WHEELS (SHADOW / MIDTONE / HIGHLIGHT)
                                    // =========================================================
                                    val log = settings.logWheels
                                    if (useGridWheels) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                ColorWheel(
                                                    title = "Shadow",
                                                    accentColor = Color(0xFF38BDF8),
                                                    value = log.shadow,
                                                    onValueChange = { newShadow ->
                                                        onSettingsChange("Adjust Log Shadow") {
                                                            it.copy(logWheels = it.logWheels.copy(shadow = newShadow))
                                                        }
                                                    },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ColorWheel(
                                                    title = "Midtone",
                                                    accentColor = ResolveAmber,
                                                    value = log.midtone,
                                                    onValueChange = { newMid ->
                                                        onSettingsChange("Adjust Log Midtone") {
                                                            it.copy(logWheels = it.logWheels.copy(midtone = newMid))
                                                        }
                                                    },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                ColorWheel(
                                                    title = "Highlight",
                                                    accentColor = Color(0xFFFACC15),
                                                    value = log.highlight,
                                                    onValueChange = { newHi ->
                                                        onSettingsChange("Adjust Log Highlight") {
                                                            it.copy(logWheels = it.logWheels.copy(highlight = newHi))
                                                        }
                                                    },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                ColorWheel(
                                                    title = "Log Offset",
                                                    accentColor = Color(0xFFA78BFA),
                                                    value = log.offset,
                                                    onValueChange = { newOff ->
                                                        onSettingsChange("Adjust Log Offset") {
                                                            it.copy(logWheels = it.logWheels.copy(offset = newOff))
                                                        }
                                                    },
                                                    wheelDiameter = 126.dp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 2.dp)
                                        ) {
                                            ColorWheel(
                                                title = "Shadow",
                                                accentColor = Color(0xFF38BDF8),
                                                value = log.shadow,
                                                onValueChange = { newShadow ->
                                                    onSettingsChange("Adjust Log Shadow") {
                                                        it.copy(logWheels = it.logWheels.copy(shadow = newShadow))
                                                    }
                                                },
                                                modifier = Modifier.width(172.dp)
                                            )
                                            ColorWheel(
                                                title = "Midtone",
                                                accentColor = ResolveAmber,
                                                value = log.midtone,
                                                onValueChange = { newMid ->
                                                    onSettingsChange("Adjust Log Midtone") {
                                                        it.copy(logWheels = it.logWheels.copy(midtone = newMid))
                                                    }
                                                },
                                                modifier = Modifier.width(172.dp)
                                            )
                                            ColorWheel(
                                                title = "Highlight",
                                                accentColor = Color(0xFFFACC15),
                                                value = log.highlight,
                                                onValueChange = { newHi ->
                                                    onSettingsChange("Adjust Log Highlight") {
                                                        it.copy(logWheels = it.logWheels.copy(highlight = newHi))
                                                    }
                                                },
                                                modifier = Modifier.width(172.dp)
                                            )
                                            ColorWheel(
                                                title = "Log Offset",
                                                accentColor = Color(0xFFA78BFA),
                                                value = log.offset,
                                                onValueChange = { newOff ->
                                                    onSettingsChange("Adjust Log Offset") {
                                                        it.copy(logWheels = it.logWheels.copy(offset = newOff))
                                                    }
                                                },
                                                modifier = Modifier.width(172.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Log Zone Crossover Range Controls (Low Range & High Range)
                                    Surface(
                                        color = Color(0xFF10141E),
                                        shape = RoundedCornerShape(10.dp),
                                        border = CardDefaults.outlinedCardBorder().copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder)
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "LOG TONAL RANGE CROSSOVERS",
                                                    color = ResolveCyan,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Surface(
                                                    onClick = {
                                                        onSettingsChange("Reset Log Zone Wheels") {
                                                            it.copy(logWheels = LogWheelSettings())
                                                        }
                                                    },
                                                    color = Color(0xFF1A2232),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.testTag("reset_log_wheels_btn")
                                                ) {
                                                    Text(
                                                        text = "Reset Log",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Low Range (Shadow Crossover)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${(log.lowRange * 100).toInt()}% IRE",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                Slider(
                                                    value = log.lowRange,
                                                    onValueChange = { v ->
                                                        onSettingsChange("Adjust Log Low Range") {
                                                            it.copy(logWheels = it.logWheels.copy(lowRange = v.coerceAtMost(it.logWheels.highRange - 0.08f)))
                                                        }
                                                    },
                                                    valueRange = 0.10f..0.55f,
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8)),
                                                    modifier = Modifier.fillMaxWidth().height(34.dp).testTag("log_low_range_slider")
                                                )
                                            }

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("High Range (Highlight Crossover)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${(log.highRange * 100).toInt()}% IRE",
                                                        color = Color(0xFFFACC15),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                Slider(
                                                    value = log.highRange,
                                                    onValueChange = { v ->
                                                        onSettingsChange("Adjust Log High Range") {
                                                            it.copy(logWheels = it.logWheels.copy(highRange = v.coerceAtLeast(it.logWheels.lowRange + 0.08f)))
                                                        }
                                                    },
                                                    valueRange = 0.45f..0.90f,
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFACC15), activeTrackColor = Color(0xFFFACC15)),
                                                    modifier = Modifier.fillMaxWidth().height(34.dp).testTag("log_high_range_slider")
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // DaVinci Resolve Primary Adjustment Bars (Exposure, Brightness, Contrast, Temp, Tint, Sat, Boost, Shadows, Highlights)
                                PrimarySlidersDeck(
                                    settings = settings,
                                    onSettingsChange = onSettingsChange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        StudioToolTab.CURVES -> {
                            val curveHistogram by produceState<com.example.color.HistogramData?>(initialValue = null, previewBitmap) {
                                value = previewBitmap?.let { bmp ->
                                    withContext(Dispatchers.Default) {
                                        com.example.color.SamsungLogColorScience.calculateHistogram(bmp)
                                    }
                                }
                            }
                            // Custom Spline Curves Editor Canvas with Live Histogram Backdrop
                            CurvesEditor(
                                curves = settings.curves,
                                onCurvesChange = { newCurves -> onSettingsChange("Adjust Spline Curves") { it.copy(curves = newCurves) } },
                                histogramData = curveHistogram,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StudioToolTab.CST -> {
                            // DaVinci Resolve Color Space Transform (CST) Final Output Node
                            CstNodePanel(
                                cst = settings.cstNode,
                                onCstChange = { newCst ->
                                    onSettingsChange("Update CST Output Node") { it.copy(cstNode = newCst) }
                                },
                                onNavigateToTab = onTabSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StudioToolTab.LUTS -> {
                            // Professional 3D LUT Manager with S24 Log mapping & A/B preview toggle
                            LutManager(
                                selectedLut = settings.activeLut,
                                lutIntensity = settings.lutIntensity,
                                lutBypass = settings.lutBypass,
                                customLuts = settings.customLuts,
                                selectedCustomLutId = settings.selectedCustomLutId,
                                onLutChange = { newLut ->
                                    onSettingsChange("Apply ${newLut.displayName}") { it.copy(activeLut = newLut) }
                                },
                                onIntensityChange = { newInt ->
                                    onSettingsChange("Adjust LUT Opacity") { it.copy(lutIntensity = newInt) }
                                },
                                onToggleLutBypass = {
                                    onSettingsChange(if (!settings.lutBypass) "Bypass LUT" else "Enable LUT") {
                                        it.copy(lutBypass = !it.lutBypass)
                                    }
                                },
                                onSelectCustomLut = { custom ->
                                    onSettingsChange("Apply ${custom.name}") {
                                        it.copy(
                                            activeLut = LutPreset.CUSTOM_SAVED,
                                            selectedCustomLutId = custom.id
                                        )
                                    }
                                },
                                onCreateCustomLutClick = onCreateCustomLutClick,
                                onDeleteCustomLut = onDeleteCustomLut,
                                onImportCubeClick = onImportCubeClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StudioToolTab.PRESETS -> {
                            // User Presets & Factory Looks Browser
                            PresetManagerPanel(
                                presets = presets,
                                onApplyPreset = onApplyPreset,
                                onCreatePresetClick = onCreatePresetClick,
                                onDeletePreset = onDeletePreset,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StudioToolTab.MASK -> {
                            // Power Window Masking + Tracker + 3D HSL Secondary Qualifier Controls
                            PowerWindowControls(
                                mask = settings.mask,
                                onMaskChange = { newMask -> onSettingsChange("Adjust Power Window") { it.copy(mask = newMask) } },
                                hslQualifier = settings.hslQualifier,
                                onHslQualifierChange = { newHsl -> onSettingsChange("Adjust 3D HSL Qualifier") { it.copy(hslQualifier = newHsl) } },
                                isTracking = isMaskTracking,
                                trackingDirection = maskTrackingDirection,
                                onTrackForward = onTrackMaskForward,
                                onTrackBackward = onTrackMaskBackward,
                                onTrackStep = onTrackMaskStep,
                                onClearTrack = onClearMaskTrack,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StudioToolTab.EFFECTS -> {
                            // Halation, Film Grain, Vignette, White Balance, Samsung Log Knee
                            EffectsPanel(
                                settings = settings,
                                onSettingsChange = { newSettings -> onSettingsChange("Adjust Film FX") { newSettings } },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. STUDIO DOCK TOOL SELECTOR TABS (Only shown when hosted in Floating Sheet)
            // ==========================================
            if (showInternalTabBar) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10131B))
                        .horizontalScroll(rememberScrollState())
                        .padding(3.dp)
                ) {
                    StudioToolTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ResolveAmber.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onTabSelected(tab) }
                                .padding(vertical = 5.dp, horizontal = 8.dp)
                                .testTag("dock_container_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) ResolveAmber else TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) ResolveAmber else TextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
