package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HslQualifierPreset
import com.example.model.HslQualifierSettings
import com.example.model.MaskType
import com.example.model.PowerWindowMask
import com.example.ui.theme.*

@Composable
fun PowerWindowControls(
    mask: PowerWindowMask,
    onMaskChange: (PowerWindowMask) -> Unit,
    hslQualifier: HslQualifierSettings = HslQualifierSettings(),
    onHslQualifierChange: (HslQualifierSettings) -> Unit = {},
    isTracking: Boolean = false,
    trackingDirection: Int = 1,
    onTrackForward: () -> Unit = {},
    onTrackBackward: () -> Unit = {},
    onTrackStep: () -> Unit = {},
    onClearTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("power_window_controls")
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Mask Type, Matte Toggle & Invert
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Mask",
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "POWER WINDOW MASK",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Alpha Matte View Mode (DaVinci Resolve Highlight mode)
                    FilterChip(
                        selected = mask.showMatte,
                        onClick = { onMaskChange(mask.copy(showMatte = !mask.showMatte)) },
                        label = { Text("Matte", fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Contrast, contentDescription = "Matte Mode", modifier = Modifier.size(12.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ResolveCyan.copy(alpha = 0.25f),
                            selectedLabelColor = ResolveCyan
                        ),
                        modifier = Modifier.height(28.dp).testTag("show_matte_toggle")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Invert Button
                    FilterChip(
                        selected = mask.invert,
                        onClick = { onMaskChange(mask.copy(invert = !mask.invert)) },
                        label = { Text("Invert", fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Flip, contentDescription = "Invert", modifier = Modifier.size(12.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                            selectedLabelColor = ResolveAmber
                        ),
                        modifier = Modifier.height(28.dp).testTag("invert_mask_toggle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mask Shape Selector Tabs: None, Elliptical, Rectangular, Triangle, Linear, Bezier
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10131B))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    Pair(MaskType.NONE, "Off"),
                    Pair(MaskType.ELLIPTICAL, "Oval"),
                    Pair(MaskType.RECTANGULAR, "Rect"),
                    Pair(MaskType.TRIANGLE, "Triangle"),
                    Pair(MaskType.LINEAR, "Linear"),
                    Pair(MaskType.CUSTOM_BEZIER, "Bezier")
                ).forEach { (type, label) ->
                    val isSelected = mask.type == type
                    Surface(
                        onClick = { onMaskChange(mask.copy(type = type)) },
                        color = if (isSelected) ResolveAmber.copy(alpha = 0.2f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).testTag("mask_type_${type.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) ResolveAmber else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (mask.type != MaskType.NONE) {
                Spacer(modifier = Modifier.height(10.dp))

                if (mask.type == MaskType.CUSTOM_BEZIER) {
                    // ==========================================
                    // BEZIER SPECIFIC TOOLBAR
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141923))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = null,
                                tint = ResolveCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${mask.bezierPoints.size} Nodes",
                                color = ResolveCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Add Node Button
                            Button(
                                onClick = {
                                    val pts = mask.bezierPoints.toMutableList()
                                    val idx = mask.selectedPointIndex.coerceIn(0, pts.size - 1)
                                    val nextIdx = (idx + 1) % pts.size
                                    val pA = pts[idx]
                                    val pB = pts[nextIdx]
                                    val midX = (pA.x + pB.x) / 2f
                                    val midY = (pA.y + pB.y) / 2f
                                    val newPt = com.example.model.BezierControlPoint(
                                        x = midX,
                                        y = midY,
                                        handleInX = midX - (pB.x - pA.x) * 0.15f,
                                        handleInY = midY - (pB.y - pA.y) * 0.15f,
                                        handleOutX = midX + (pB.x - pA.x) * 0.15f,
                                        handleOutY = midY + (pB.y - pA.y) * 0.15f
                                    )
                                    pts.add(idx + 1, newPt)
                                    onMaskChange(mask.copy(bezierPoints = pts, selectedPointIndex = idx + 1))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp).testTag("bezier_add_point_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add", color = ResolveAmber, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Delete Selected Node Button
                            Button(
                                onClick = {
                                    if (mask.bezierPoints.size > 3) {
                                        val pts = mask.bezierPoints.toMutableList()
                                        val idx = mask.selectedPointIndex.coerceIn(0, pts.size - 1)
                                        pts.removeAt(idx)
                                        val newIdx = (idx - 1).coerceAtLeast(0)
                                        onMaskChange(mask.copy(bezierPoints = pts, selectedPointIndex = newIdx))
                                    }
                                },
                                enabled = mask.bezierPoints.size > 3,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261D22)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp).testTag("bezier_del_point_btn")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = if (mask.bezierPoints.size > 3) Color(0xFFF87171) else TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Del", color = if (mask.bezierPoints.size > 3) Color(0xFFF87171) else TextMuted, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Reset Bezier Button
                            IconButton(
                                onClick = {
                                    onMaskChange(mask.copy(bezierPoints = com.example.model.defaultBezierPoints(), selectedPointIndex = 0))
                                },
                                modifier = Modifier.size(30.dp).testTag("bezier_reset_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Bezier", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    // Width Radius Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Width Radius", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format("%.2f", mask.radiusX), color = ResolveAmber, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = mask.radiusX,
                            onValueChange = { onMaskChange(mask.copy(radiusX = it)) },
                            valueRange = 0.05f..0.55f,
                            colors = SliderDefaults.colors(thumbColor = ResolveAmber, activeTrackColor = ResolveAmber),
                            modifier = Modifier.fillMaxWidth().height(34.dp).testTag("mask_width_slider")
                        )
                    }

                    // Height Radius Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Height Radius", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format("%.2f", mask.radiusY), color = ResolveAmber, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = mask.radiusY,
                            onValueChange = { onMaskChange(mask.copy(radiusY = it)) },
                            valueRange = 0.05f..0.55f,
                            colors = SliderDefaults.colors(thumbColor = ResolveAmber, activeTrackColor = ResolveAmber),
                            modifier = Modifier.fillMaxWidth().height(34.dp).testTag("mask_height_slider")
                        )
                    }
                }

                // Feather Softness Slider (Applies to all mask shapes including Bezier)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Feather Softness", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.2f", mask.feather), color = ResolveCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = mask.feather,
                        onValueChange = { onMaskChange(mask.copy(feather = it)) },
                        valueRange = 0.02f..0.6f,
                        colors = SliderDefaults.colors(thumbColor = ResolveCyan, activeTrackColor = ResolveCyan),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("mask_feather_slider")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // =====================================================================
                // DAVINCI RESOLVE POINT-CLOUD MOTION TRACKER CONSOLE
                // =====================================================================
                Surface(
                    color = Color(0xFF101520),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isTracking || mask.trackingKeyframes.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.55f)
                            else StudioCardBorder
                        )
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("power_window_tracker_console")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = "Window Tracker",
                                    tint = if (isTracking || mask.trackingKeyframes.isNotEmpty()) Color(0xFF10B981) else ResolveCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CLOUD TRACKER",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp
                                )
                            }

                            // Real keyframe count and measured NCC correlation confidence readout
                            val avgConfidence = if (mask.trackingKeyframes.isNotEmpty()) {
                                val avg = mask.trackingKeyframes.map { it.confidence }.average().toFloat()
                                (avg * 100).toInt().coerceIn(10, 99)
                            } else 0

                            Surface(
                                color = if (isTracking) Color(0xFF10B981).copy(alpha = 0.22f) else Color(0xFF1A2232),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = when {
                                        isTracking -> if (trackingDirection < 0) "TRACKING REV..." else "TRACKING FWD..."
                                        mask.trackingKeyframes.isNotEmpty() -> "${mask.trackingKeyframes.size} Keys • $avgConfidence% Lock"
                                        else -> String.format("X:%.2f Y:%.2f", mask.centerX, mask.centerY)
                                    },
                                    color = if (isTracking || mask.trackingKeyframes.isNotEmpty()) Color(0xFF10B981) else ResolveCyan,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Transport Buttons: Track Reverse, Step +1, Track Forward / Stop, Clear Track
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Track Backward / Stop
                            Surface(
                                onClick = onTrackBackward,
                                color = if (isTracking && trackingDirection < 0) Color(0xFF3A1D24) else Color(0xFF182030),
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isTracking && trackingDirection < 0) ResolveRed else Color(0xFF28344B)
                                    )
                                ),
                                modifier = Modifier.weight(1f).testTag("track_mask_reverse_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTracking && trackingDirection < 0) Icons.Default.Stop else Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = if (isTracking && trackingDirection < 0) ResolveRed else ResolveCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTracking && trackingDirection < 0) "Stop" else "Track Rev",
                                        color = if (isTracking && trackingDirection < 0) ResolveRed else TextPrimary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Step +1 Frame Track
                            Surface(
                                onClick = onTrackStep,
                                color = Color(0xFF182030),
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF28344B))
                                ),
                                modifier = Modifier.weight(1f).testTag("track_mask_step_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+1 Frame", color = ResolveAmber, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Track Forward / Stop
                            Surface(
                                onClick = onTrackForward,
                                color = if (isTracking && trackingDirection > 0) Color(0xFF3A1D24) else Color(0xFF132F28),
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isTracking && trackingDirection > 0) ResolveRed else Color(0xFF10B981)
                                    )
                                ),
                                modifier = Modifier.weight(1.25f).testTag("track_mask_forward_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTracking && trackingDirection > 0) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isTracking && trackingDirection > 0) ResolveRed else Color(0xFF10B981),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTracking && trackingDirection > 0) "Stop" else "Track Fwd",
                                        color = if (isTracking && trackingDirection > 0) ResolveRed else Color(0xFF10B981),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Clear Keyframes
                            Surface(
                                onClick = onClearTrack,
                                color = Color(0xFF1C1824),
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF33293F))
                                ),
                                modifier = Modifier.testTag("track_mask_clear_btn")
                            ) {
                                Text(
                                    text = "Clear",
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = StudioCardBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // =========================================================================
            // DAVINCI RESOLVE 3D HSL QUALIFIER (SECONDARY COLOR KEYER)
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Colorize,
                        contentDescription = "HSL Qualifier",
                        tint = ResolveCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "3D HSL QUALIFIER (SECONDARY KEY)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Isolate Skin Tones, Sky, Foliage or Neon by Hue/Sat/Lum",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hslQualifier.enabled) {
                        FilterChip(
                            selected = hslQualifier.showMatte,
                            onClick = { onHslQualifierChange(hslQualifier.copy(showMatte = !hslQualifier.showMatte)) },
                            label = { Text("Highlight", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                                selectedLabelColor = ResolveAmber
                            ),
                            modifier = Modifier.height(30.dp).testTag("hsl_matte_toggle")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Switch(
                        checked = hslQualifier.enabled,
                        onCheckedChange = { onHslQualifierChange(hslQualifier.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ResolveCyan,
                            checkedTrackColor = ResolveCyan.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("hsl_qualifier_enable_switch")
                    )
                }
            }

            if (hslQualifier.enabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1-Tap Color Target Presets (Skin Tones, Sky & Cyan, Foliage Green, Neon Magenta, Golden Amber)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    HslQualifierPreset.values().forEach { preset ->
                        val isSelected = kotlin.math.abs(hslQualifier.hueCenterDeg - preset.hueCenterDeg) < 4f
                        Surface(
                            onClick = {
                                onHslQualifierChange(
                                    hslQualifier.copy(
                                        hueCenterDeg = preset.hueCenterDeg,
                                        hueWidthDeg = preset.hueWidthDeg,
                                        hueSoftnessDeg = preset.hueSoftnessDeg
                                    )
                                )
                            },
                            color = if (isSelected) ResolveCyan.copy(alpha = 0.22f) else Color(0xFF141923),
                            shape = RoundedCornerShape(6.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) ResolveCyan else StudioCardBorder
                                )
                            ),
                            modifier = Modifier.testTag("hsl_preset_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.displayName,
                                color = if (isSelected) ResolveCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rainbow Hue Spectrum Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
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
                        )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Hue Center & Width Sliders
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Hue Center", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${hslQualifier.hueCenterDeg.toInt()}°", color = ResolveCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = hslQualifier.hueCenterDeg,
                        onValueChange = { onHslQualifierChange(hslQualifier.copy(hueCenterDeg = it)) },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = ResolveCyan, activeTrackColor = ResolveCyan),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("hsl_hue_center_slider")
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Hue Width", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${hslQualifier.hueWidthDeg.toInt()}°", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = hslQualifier.hueWidthDeg,
                        onValueChange = { onHslQualifierChange(hslQualifier.copy(hueWidthDeg = it)) },
                        valueRange = 8f..120f,
                        colors = SliderDefaults.colors(thumbColor = ResolveCyan, activeTrackColor = ResolveCyan),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("hsl_hue_width_slider")
                    )
                }

                // Isolated Color Refinement Sliders (Hue Shift, Sat, Lum)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Key Hue Shift", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${hslQualifier.hueShiftDeg.toInt()}°", color = ResolveAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = hslQualifier.hueShiftDeg,
                        onValueChange = { onHslQualifierChange(hslQualifier.copy(hueShiftDeg = it)) },
                        valueRange = -60f..60f,
                        colors = SliderDefaults.colors(thumbColor = ResolveAmber, activeTrackColor = ResolveAmber),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("hsl_hue_shift_slider")
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Key Saturation", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.2fx", hslQualifier.satAdjust), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = hslQualifier.satAdjust,
                        onValueChange = { onHslQualifierChange(hslQualifier.copy(satAdjust = it)) },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = ResolveAmber, activeTrackColor = ResolveAmber),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("hsl_sat_adjust_slider")
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Key Luminance", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format("%+.2f", hslQualifier.lumAdjust), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = hslQualifier.lumAdjust,
                        onValueChange = { onHslQualifierChange(hslQualifier.copy(lumAdjust = it)) },
                        valueRange = -0.5f..0.5f,
                        colors = SliderDefaults.colors(thumbColor = ResolveAmber, activeTrackColor = ResolveAmber),
                        modifier = Modifier.fillMaxWidth().height(34.dp).testTag("hsl_lum_adjust_slider")
                    )
                }
            }
        }
    }
}

/**
 * Visual Guide delegate that renders the MaskingOverlay
 */
@Composable
fun PowerWindowVisualGuide(
    mask: PowerWindowMask,
    onMaskChange: (PowerWindowMask) -> Unit,
    modifier: Modifier = Modifier
) {
    MaskingOverlay(mask = mask, onMaskChange = onMaskChange, modifier = modifier)
}
