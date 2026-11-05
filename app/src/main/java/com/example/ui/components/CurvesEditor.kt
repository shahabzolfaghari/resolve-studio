package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.color.HistogramData
import com.example.color.SamsungLogColorScience
import com.example.model.CurvePoint
import com.example.model.RgbCurves
import com.example.ui.theme.*
import kotlin.math.sqrt

enum class CurveEditorMode(val label: String) {
    YRGB_SPLINE("YRGB Spline"),
    COLOR_VS_COLOR("Hue / Lum Vs")
}

enum class CurveChannel(val label: String, val color: Color) {
    MASTER("Master Y", Color.White),
    RED("Red", Color(0xFFEF4444)),
    GREEN("Green", Color(0xFF10B981)),
    BLUE("Blue", Color(0xFF3B82F6))
}

enum class SecondaryCurveChannel(val label: String, val subtitle: String, val color: Color) {
    HUE_VS_HUE("Hue vs Hue", "Shift targeted Hue (±60°)", ResolveCyan),
    HUE_VS_SAT("Hue vs Sat", "Boost/Cut Saturation by Hue", ResolveAmber),
    HUE_VS_LUM("Hue vs Lum", "Darken/Brighten by Hue (Sky/Skin)", Color(0xFF38BDF8)),
    LUM_VS_SAT("Lum vs Sat", "Clean Black/White Casts by Luma", Color(0xFFA78BFA))
}

enum class HueAnchorTarget(val label: String, val xNorm: Float, val dotColor: Color) {
    RED("Red", 0.04f, Color(0xFFEF4444)),
    SKIN("Skin", 0.09f, Color(0xFFF59E0B)),
    YELLOW("Yellow", 0.17f, Color(0xFFEAB308)),
    GREEN("Green", 0.33f, Color(0xFF10B981)),
    CYAN("Cyan", 0.50f, Color(0xFF06B6D4)),
    BLUE("Blue", 0.67f, Color(0xFF3B82F6)),
    MAGENTA("Magenta", 0.83f, Color(0xFFD946EF))
}

enum class CurveQuickPreset(val label: String, val points: List<CurvePoint>) {
    LINEAR("Linear", listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))),
    S_CURVE(
        "S-Curve",
        listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.18f),
            CurvePoint(0.75f, 0.83f),
            CurvePoint(1f, 1f)
        )
    ),
    FILM_TOE(
        "Film Toe",
        listOf(
            CurvePoint(0f, 0.03f),
            CurvePoint(0.22f, 0.16f),
            CurvePoint(0.55f, 0.58f),
            CurvePoint(0.85f, 0.88f),
            CurvePoint(1f, 0.97f)
        )
    ),
    SOFT_ROLLOFF(
        "Soft Roll-Off",
        listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.50f, 0.52f),
            CurvePoint(0.80f, 0.78f),
            CurvePoint(1f, 0.90f)
        )
    ),
    MATTE_LIFT(
        "Matte Lift",
        listOf(
            CurvePoint(0f, 0.09f),
            CurvePoint(0.28f, 0.24f),
            CurvePoint(0.75f, 0.78f),
            CurvePoint(1f, 0.95f)
        )
    )
}

@Composable
fun CurvesEditor(
    curves: RgbCurves,
    onCurvesChange: (RgbCurves) -> Unit,
    histogramData: HistogramData? = null,
    modifier: Modifier = Modifier
) {
    var editorMode by remember { mutableStateOf(CurveEditorMode.YRGB_SPLINE) }
    var selectedChannel by remember { mutableStateOf(CurveChannel.MASTER) }
    var selectedSecondaryChannel by remember { mutableStateOf(SecondaryCurveChannel.HUE_VS_SAT) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val activeColor = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
        selectedChannel.color
    } else {
        selectedSecondaryChannel.color
    }

    val currentPoints = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
        when (selectedChannel) {
            CurveChannel.MASTER -> curves.master
            CurveChannel.RED -> curves.red
            CurveChannel.GREEN -> curves.green
            CurveChannel.BLUE -> curves.blue
        }
    } else {
        when (selectedSecondaryChannel) {
            SecondaryCurveChannel.HUE_VS_HUE -> curves.hueVsHue
            SecondaryCurveChannel.HUE_VS_SAT -> curves.hueVsSat
            SecondaryCurveChannel.HUE_VS_LUM -> curves.hueVsLum
            SecondaryCurveChannel.LUM_VS_SAT -> curves.lumVsSat
        }
    }

    val defaultNeutralPoints = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
        listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
    } else {
        listOf(CurvePoint(0f, 0.5f), CurvePoint(1f, 0.5f))
    }

    val updateCurrentPoints: (List<CurvePoint>) -> Unit = { newPoints ->
        val sorted = newPoints.sortedBy { it.x }
        val updated = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
            when (selectedChannel) {
                CurveChannel.MASTER -> curves.copy(master = sorted)
                CurveChannel.RED -> curves.copy(red = sorted)
                CurveChannel.GREEN -> curves.copy(green = sorted)
                CurveChannel.BLUE -> curves.copy(blue = sorted)
            }
        } else {
            when (selectedSecondaryChannel) {
                SecondaryCurveChannel.HUE_VS_HUE -> curves.copy(hueVsHue = sorted)
                SecondaryCurveChannel.HUE_VS_SAT -> curves.copy(hueVsSat = sorted)
                SecondaryCurveChannel.HUE_VS_LUM -> curves.copy(hueVsLum = sorted)
                SecondaryCurveChannel.LUM_VS_SAT -> curves.copy(lumVsSat = sorted)
            }
        }
        onCurvesChange(updated)
    }

    val latestPoints by rememberUpdatedState(currentPoints)
    val latestUpdatePoints by rememberUpdatedState(updateCurrentPoints)
    val latestDefaultPoints by rememberUpdatedState(defaultNeutralPoints)

    Card(
        modifier = modifier
            .testTag("curves_editor")
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Top Mode Switcher: [YRGB Spline] vs [Hue / Lum Vs] + Reset & Delete Knot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E121A))
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    CurveEditorMode.values().forEach { mode ->
                        val isModeSelected = editorMode == mode
                        Surface(
                            onClick = {
                                editorMode = mode
                                selectedPointIndex = null
                            },
                            color = if (isModeSelected) ResolveAmber.copy(alpha = 0.22f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("curve_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isModeSelected) ResolveAmber else TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Controls: Delete Knot + Reset Curve
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedPointIndex != null && selectedPointIndex != 0 && selectedPointIndex != currentPoints.size - 1) {
                        IconButton(
                            onClick = {
                                selectedPointIndex?.let { idx ->
                                    if (idx in 1 until currentPoints.size - 1) {
                                        val mutable = currentPoints.toMutableList()
                                        mutable.removeAt(idx)
                                        updateCurrentPoints(mutable)
                                        selectedPointIndex = null
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp).testTag("delete_curve_knot")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete point", tint = ResolveRed, modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            updateCurrentPoints(defaultNeutralPoints)
                            selectedPointIndex = null
                        },
                        modifier = Modifier.size(28.dp).testTag("reset_curve")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset curve", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-Channel Selector Row
            if (editorMode == CurveEditorMode.YRGB_SPLINE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Channel Segmented Buttons (Master Y, Red, Green, Blue)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10131B))
                            .padding(2.dp)
                    ) {
                        CurveChannel.values().forEach { channel ->
                            val isSelected = selectedChannel == channel
                            Surface(
                                onClick = {
                                    selectedChannel = channel
                                    selectedPointIndex = null
                                },
                                color = if (isSelected) channel.color.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("curve_tab_${channel.label.lowercase()}")
                            ) {
                                Text(
                                    text = channel.label,
                                    color = if (isSelected) channel.color else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 1-Tap Cinema Curve Presets Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = "Curve Presets",
                        tint = ResolveAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    CurveQuickPreset.values().forEach { preset ->
                        Surface(
                            onClick = {
                                updateCurrentPoints(preset.points)
                                selectedPointIndex = null
                            },
                            color = Color(0xFF141923),
                            shape = RoundedCornerShape(6.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder)
                            ),
                            modifier = Modifier.testTag("curve_preset_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.label,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                // Secondary Color vs Color Tabs: Hue vs Hue, Hue vs Sat, Hue vs Lum, Lum vs Sat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10131B))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SecondaryCurveChannel.values().forEach { sec ->
                        val isSelected = selectedSecondaryChannel == sec
                        Surface(
                            onClick = {
                                selectedSecondaryChannel = sec
                                selectedPointIndex = null
                            },
                            color = if (isSelected) sec.color.copy(alpha = 0.22f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).testTag("secondary_curve_tab_${sec.name.lowercase()}")
                        ) {
                            Text(
                                text = sec.label,
                                color = if (isSelected) sec.color else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 1-Tap DaVinci Resolve Color Target Anchor Pins (Red, Skin, Yellow, Green, Cyan, Blue, Magenta)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    HueAnchorTarget.values().forEach { target ->
                        Surface(
                            onClick = {
                                // Drop a localized 3-point anchor bracket around target.xNorm so dragging only affects that color
                                val leftAnchor = (target.xNorm - 0.08f).coerceIn(0.01f, 0.99f)
                                val rightAnchor = (target.xNorm + 0.08f).coerceIn(0.01f, 0.99f)
                                val mutable = currentPoints.toMutableList()
                                if (mutable.none { kotlin.math.abs(it.x - leftAnchor) < 0.025f }) {
                                    mutable.add(CurvePoint(leftAnchor, 0.5f))
                                }
                                if (mutable.none { kotlin.math.abs(it.x - rightAnchor) < 0.025f }) {
                                    mutable.add(CurvePoint(rightAnchor, 0.5f))
                                }
                                val existingIdx = mutable.indexOfFirst { kotlin.math.abs(it.x - target.xNorm) < 0.025f }
                                if (existingIdx == -1) {
                                    mutable.add(CurvePoint(target.xNorm, 0.58f))
                                }
                                val sorted = mutable.sortedBy { it.x }
                                updateCurrentPoints(sorted)
                                selectedPointIndex = sorted.indexOfFirst { kotlin.math.abs(it.x - target.xNorm) < 0.025f }
                            },
                            color = Color(0xFF141923),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(target.dotColor.copy(alpha = 0.65f))
                            ),
                            modifier = Modifier.testTag("hue_anchor_${target.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(target.dotColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = target.label,
                                    color = TextPrimary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2D Curves Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1218))
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
                    .pointerInput(editorMode, selectedChannel, selectedSecondaryChannel) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                // Double-tap an interior knot to delete it, or double-tap empty area to reset channel
                                val pts = latestPoints
                                val xNorm = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val yNorm = (1f - tapOffset.y / size.height).coerceIn(0f, 1f)
                                val hitRadiusNorm = 28.dp.toPx() / size.width
                                var hitIndex: Int? = null
                                for (i in pts.indices) {
                                    val pt = pts[i]
                                    val dx = pt.x - xNorm
                                    val dy = pt.y - yNorm
                                    if (sqrt(dx * dx + dy * dy) < hitRadiusNorm) {
                                        hitIndex = i
                                        break
                                    }
                                }
                                if (hitIndex != null && hitIndex > 0 && hitIndex < pts.size - 1) {
                                    val mutable = pts.toMutableList()
                                    mutable.removeAt(hitIndex)
                                    latestUpdatePoints(mutable)
                                    selectedPointIndex = null
                                } else {
                                    latestUpdatePoints(latestDefaultPoints)
                                    selectedPointIndex = null
                                }
                            },
                            onTap = { tapOffset ->
                                val pts = latestPoints
                                val xNorm = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val yNorm = (1f - tapOffset.y / size.height).coerceIn(0f, 1f)

                                val hitRadiusNorm = 26.dp.toPx() / size.width
                                var hitIndex: Int? = null

                                for (i in pts.indices) {
                                    val pt = pts[i]
                                    val dx = pt.x - xNorm
                                    val dy = pt.y - yNorm
                                    if (sqrt(dx * dx + dy * dy) < hitRadiusNorm) {
                                        hitIndex = i
                                        break
                                    }
                                }

                                if (hitIndex != null) {
                                    selectedPointIndex = hitIndex
                                } else {
                                    // Add new knot point
                                    val mutable = pts.toMutableList()
                                    val newPt = CurvePoint(xNorm, yNorm)
                                    mutable.add(newPt)
                                    val sorted = mutable.sortedBy { it.x }
                                    latestUpdatePoints(sorted)
                                    selectedPointIndex = sorted.indexOfFirst { it.x == xNorm && it.y == yNorm }
                                }
                            }
                        )
                    }
                    .pointerInput(editorMode, selectedChannel, selectedSecondaryChannel) {
                        var draggedIdx = -1
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val pts = latestPoints
                                val xNorm = (startOffset.x / size.width).coerceIn(0f, 1f)
                                val yNorm = (1f - startOffset.y / size.height).coerceIn(0f, 1f)
                                val hitRadiusNorm = 30.dp.toPx() / size.width

                                var bestIdx = -1
                                var bestDist = Float.MAX_VALUE
                                for (i in pts.indices) {
                                    val pt = pts[i]
                                    val dx = pt.x - xNorm
                                    val dy = pt.y - yNorm
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist < hitRadiusNorm && dist < bestDist) {
                                        bestDist = dist
                                        bestIdx = i
                                    }
                                }

                                if (bestIdx != -1) {
                                    draggedIdx = bestIdx
                                    selectedPointIndex = bestIdx
                                } else {
                                    // Create a new knot on drag start so single-gesture curve bending works immediately
                                    val mutable = pts.toMutableList()
                                    val clampedX = xNorm.coerceIn(0.03f, 0.97f)
                                    val newPt = CurvePoint(clampedX, yNorm)
                                    mutable.add(newPt)
                                    val sorted = mutable.sortedBy { it.x }
                                    draggedIdx = sorted.indexOfFirst { it.x == clampedX && it.y == yNorm }
                                    selectedPointIndex = draggedIdx
                                    latestUpdatePoints(sorted)
                                }
                            },
                            onDragEnd = { draggedIdx = -1 },
                            onDragCancel = { draggedIdx = -1 }
                        ) { change, dragAmount ->
                            change.consume()
                            val pts = latestPoints
                            val idx = draggedIdx
                            if (idx !in pts.indices) return@detectDragGestures

                            val dxNorm = dragAmount.x / size.width
                            val dyNorm = -dragAmount.y / size.height // Screen Y is inverted

                            val curPt = pts[idx]
                            val minX = if (idx == 0) 0f else if (idx == pts.size - 1) 1f else (pts[idx - 1].x + 0.025f)
                            val maxX = if (idx == 0) 0f else if (idx == pts.size - 1) 1f else (pts[idx + 1].x - 0.025f)
                            val newX = if (idx == 0) 0f else if (idx == pts.size - 1) 1f else (curPt.x + dxNorm).coerceIn(minX, maxX)
                            val newY = (curPt.y + dyNorm).coerceIn(0f, 1f)

                            val mutable = pts.toMutableList()
                            mutable[idx] = CurvePoint(newX, newY)
                            latestUpdatePoints(mutable)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    if (editorMode == CurveEditorMode.YRGB_SPLINE) {
                        // 0. Live Channel Histogram Backdrop (DaVinci Resolve Style)
                        if (histogramData != null) {
                            val bins = when (selectedChannel) {
                                CurveChannel.MASTER -> histogramData.lumBins
                                CurveChannel.RED -> histogramData.redBins
                                CurveChannel.GREEN -> histogramData.greenBins
                                CurveChannel.BLUE -> histogramData.blueBins
                            }
                            if (bins.isNotEmpty()) {
                                val maxBin = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                                val histPath = Path()
                                histPath.moveTo(0f, h)
                                for (i in 0..255) {
                                    val hx = (i / 255f) * w
                                    val normVal = (bins[i].toFloat() / maxBin).coerceIn(0f, 1f)
                                    val hy = h - (normVal * h * 0.82f)
                                    histPath.lineTo(hx, hy)
                                }
                                histPath.lineTo(w, h)
                                histPath.close()
                                drawPath(
                                    path = histPath,
                                    color = selectedChannel.color.copy(alpha = 0.09f)
                                )
                            }
                        }
                    } else {
                        // Color vs Color Spectrum Backdrop
                        val rainbowBrush = if (selectedSecondaryChannel == SecondaryCurveChannel.LUM_VS_SAT) {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF0A0D12), Color(0xFF64748B), Color(0xFFF8FAFC))
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFAA00),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0044FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        }
                        // Subtle spectrum wash across the canvas + vivid bottom spectrum bar
                        drawRect(brush = rainbowBrush, alpha = 0.14f)
                        drawRect(
                            brush = rainbowBrush,
                            topLeft = Offset(0f, h - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(w, 8.dp.toPx()),
                            alpha = 0.78f
                        )
                    }

                    // 1. Grid Lines (10%, 25%, 50%, 75%, 90% IRE)
                    val gridFractions = floatArrayOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)
                    for (frac in gridFractions) {
                        val gx = frac * w
                        val gy = (1f - frac) * h
                        // Vertical
                        drawLine(
                            color = Color(0xFF1B212D),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Horizontal
                        drawLine(
                            color = Color(0xFF1B212D),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Neutral Reference Line (Diagonal for YRGB, Horizontal 0.5 Center for Color vs Color)
                    if (editorMode == CurveEditorMode.YRGB_SPLINE) {
                        drawLine(
                            color = Color(0xFF283244),
                            start = Offset(0f, h),
                            end = Offset(w, 0f),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    } else {
                        drawLine(
                            color = Color(0xFF475569),
                            start = Offset(0f, h * 0.5f),
                            end = Offset(w, h * 0.5f),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }

                    // 3. Render Spline Curve Line & Gradient
                    val lut = SamsungLogColorScience.buildSplineLut(currentPoints)
                    val curvePath = Path()
                    curvePath.moveTo(0f, (1f - lut[0]) * h)
                    for (i in 1..255) {
                        val px = (i / 255f) * w
                        val py = (1f - lut[i]) * h
                        curvePath.lineTo(px, py)
                    }

                    // Gradient under curve
                    val baselineY = if (editorMode == CurveEditorMode.YRGB_SPLINE) h else h * 0.5f
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(w, baselineY)
                        lineTo(0f, baselineY)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw solid curve line
                    drawPath(
                        path = curvePath,
                        color = activeColor,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // 4. Render Control Knot Points
                    currentPoints.forEachIndexed { index, pt ->
                        val px = pt.x * w
                        val py = (1f - pt.y) * h
                        val isSelected = index == selectedPointIndex

                        // Outer ring for selected point
                        if (isSelected) {
                            drawCircle(
                                color = ResolveAmber,
                                radius = 7.dp.toPx(),
                                center = Offset(px, py),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Knot center
                        drawCircle(
                            color = if (isSelected) ResolveAmber else activeColor,
                            radius = 4.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 2.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Coordinates readout & hint
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Text(
                    text = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
                        "Tap to add knot • Drag to curve"
                    } else {
                        selectedSecondaryChannel.subtitle
                    },
                    color = TextMuted,
                    fontSize = 9.sp
                )
                selectedPointIndex?.let { idx ->
                    if (idx < currentPoints.size) {
                        val pt = currentPoints[idx]
                        Text(
                            text = if (editorMode == CurveEditorMode.YRGB_SPLINE) {
                                String.format("In: %.2f  Out: %.2f", pt.x, pt.y)
                            } else {
                                String.format("X: %.2f  Δ: %+.2f", pt.x, pt.y - 0.5f)
                            },
                            color = ResolveAmber,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
