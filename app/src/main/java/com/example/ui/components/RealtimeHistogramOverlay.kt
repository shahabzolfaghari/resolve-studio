package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.color.HistogramData
import com.example.color.SamsungLogColorScience
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class ScopeOverlayType {
    HISTOGRAM,
    VECTORSCOPE
}

enum class HistogramDisplayMode {
    LUMINANCE,
    RGB_PARADE,
    COMBINED
}

/**
 * Professional real-time Scope Overlay (Histogram & Vectorscope) for 4K Samsung S24 Log footage.
 * Renders on-screen over the video viewport (cinema monitor HUD style) with:
 * - 256-bin Histogram with IRE graticule, RGB separation, and live clipping alerts.
 * - Broadcast Vectorscope with 123° Skin Tone reference line, 75%/100% legal targets, and Cb/Cr scatter.
 */
@Composable
fun RealtimeHistogramOverlay(
    gradedBitmap: Bitmap?,
    rawBitmap: Bitmap?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scopeType by remember { mutableStateOf(ScopeOverlayType.HISTOGRAM) }
    var displayMode by remember { mutableStateOf(HistogramDisplayMode.COMBINED) }
    var inspectRawLog by remember { mutableStateOf(false) } // Compare flat Log vs graded Rec.709
    var isExpanded by remember { mutableStateOf(false) }

    val activeBitmap = if (inspectRawLog && rawBitmap != null) rawBitmap else gradedBitmap
    if (activeBitmap == null) return

    val histogram = remember(activeBitmap) {
        SamsungLogColorScience.calculateHistogram(activeBitmap)
    }

    val overlayWidth = if (isExpanded) 250.dp else 175.dp
    val overlayHeight = if (isExpanded) {
        if (scopeType == ScopeOverlayType.VECTORSCOPE) 180.dp else 145.dp
    } else {
        if (scopeType == ScopeOverlayType.VECTORSCOPE) 135.dp else 105.dp
    }

    Card(
        modifier = modifier
            .width(overlayWidth)
            .height(overlayHeight)
            .testTag("realtime_scope_overlay_card")
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xF00A0E17)),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF263248))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // ==========================================
            // 1. SCOPE HUD HEADER
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Scope Type Switcher: [HISTO] / [VECT]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF131924))
                        .padding(1.dp)
                ) {
                    ScopeTypeTab(
                        label = "HISTO",
                        isSelected = scopeType == ScopeOverlayType.HISTOGRAM,
                        onClick = { scopeType = ScopeOverlayType.HISTOGRAM }
                    )
                    ScopeTypeTab(
                        label = "VECT",
                        isSelected = scopeType == ScopeOverlayType.VECTORSCOPE,
                        onClick = { scopeType = ScopeOverlayType.VECTORSCOPE }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mode Toggle for Histogram
                    if (scopeType == ScopeOverlayType.HISTOGRAM) {
                        Surface(
                            onClick = {
                                displayMode = when (displayMode) {
                                    HistogramDisplayMode.COMBINED -> HistogramDisplayMode.LUMINANCE
                                    HistogramDisplayMode.LUMINANCE -> HistogramDisplayMode.RGB_PARADE
                                    HistogramDisplayMode.RGB_PARADE -> HistogramDisplayMode.COMBINED
                                }
                            },
                            color = Color(0xFF161F2E),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.height(18.dp).testTag("histogram_mode_toggle_pill")
                        ) {
                            Text(
                                text = when (displayMode) {
                                    HistogramDisplayMode.COMBINED -> "ALL"
                                    HistogramDisplayMode.LUMINANCE -> "LUM"
                                    HistogramDisplayMode.RGB_PARADE -> "RGB"
                                },
                                color = TextSecondary,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                    }

                    // Size Expand/Contract Button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(18.dp).testTag("histogram_expand_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                            contentDescription = "Resize Scope",
                            tint = TextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(18.dp).testTag("close_histogram_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Scope",
                            tint = TextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ==========================================
            // 2. ACTIVE GRAPH DISPLAY (HISTOGRAM OR VECTORSCOPE)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF070A10))
                    .border(0.5.dp, Color(0xFF1E2838), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (scopeType) {
                    ScopeOverlayType.HISTOGRAM -> {
                        HistogramOverlayCanvas(
                            histogram = histogram,
                            displayMode = displayMode
                        )
                    }
                    ScopeOverlayType.VECTORSCOPE -> {
                        VectorscopeOverlayCanvas(
                            bitmap = activeBitmap
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ==========================================
            // 3. TELEMETRY & STATUS BAR
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (scopeType == ScopeOverlayType.HISTOGRAM) {
                    Text(
                        text = "EXP ${(histogram.avgLum / 255f * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    when {
                        histogram.shadowClipPct > 1.5f && histogram.highlightClipPct > 1.5f -> {
                            Text("CRUSH & BLOWN", color = Color(0xFFEF4444), fontSize = 7.5.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        histogram.shadowClipPct > 1.5f -> {
                            Text("SHADOW CRUSH ${histogram.shadowClipPct.toInt()}%", color = Color(0xFFEF4444), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        histogram.highlightClipPct > 1.5f -> {
                            Text("HIGHLIGHT CLIP ${histogram.highlightClipPct.toInt()}%", color = Color(0xFFEF4444), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        else -> {
                            Text("DYNAMIC RANGE OK", color = ResolveGreen, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    // Vectorscope telemetry: 123° Skin Tone & broadcast legal check
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(ResolveAmber))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "SKIN 123° I-BAR",
                            color = ResolveAmber,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "75% BROADCAST",
                        color = ResolveCyan,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Log vs Graded source switch pill
                if (rawBitmap != null) {
                    Text(
                        text = if (inspectRawLog) "[RAW]" else "[REC]",
                        color = if (inspectRawLog) ResolveCyan else ResolveAmber,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { inspectRawLog = !inspectRawLog }
                            .padding(1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeTypeTab(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) ResolveAmber.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) ResolveAmber else TextMuted,
            fontSize = 7.5.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HistogramOverlayCanvas(
    histogram: HistogramData,
    displayMode: HistogramDisplayMode
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)) {
        val w = size.width
        val h = size.height

        // 1. IRE Graticule Grid Lines (0%, 18% Mid-Gray, 50%, 75%, 100%)
        val gridFractions = floatArrayOf(0.18f, 0.50f, 0.75f)
        for (frac in gridFractions) {
            val gx = frac * w
            drawLine(
                color = if (frac == 0.18f) ResolveGreen.copy(alpha = 0.35f) else Color(0xFF1E2838),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (frac == 0.18f) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null
            )
        }

        // Max bin sample count for auto-scaling
        var maxCount = 1
        for (i in 0..255) {
            if (displayMode == HistogramDisplayMode.LUMINANCE) {
                maxCount = maxOf(maxCount, histogram.lumBins[i])
            } else {
                maxCount = maxOf(
                    maxCount,
                    histogram.redBins[i],
                    histogram.greenBins[i],
                    histogram.blueBins[i],
                    histogram.lumBins[i]
                )
            }
        }

        // 2. Render Luminance (if LUMA or COMBINED)
        if (displayMode == HistogramDisplayMode.LUMINANCE || displayMode == HistogramDisplayMode.COMBINED) {
            val lumPath = Path()
            lumPath.moveTo(0f, h)
            for (i in 0..255) {
                val x = (i / 255f) * w
                val normH = (histogram.lumBins[i].toFloat() / maxCount) * h * 0.94f
                lumPath.lineTo(x, h - normH)
            }
            lumPath.lineTo(w, h)
            lumPath.close()

            drawPath(
                path = lumPath,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (displayMode == HistogramDisplayMode.LUMINANCE) 0.35f else 0.12f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            drawPath(
                path = lumPath,
                color = Color.White.copy(alpha = if (displayMode == HistogramDisplayMode.LUMINANCE) 0.9f else 0.5f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // 3. Render RGB Channels (if RGB_PARADE or COMBINED)
        if (displayMode == HistogramDisplayMode.RGB_PARADE || displayMode == HistogramDisplayMode.COMBINED) {
            val channels = listOf(
                Pair(histogram.redBins, Color(0xFFEF4444)),
                Pair(histogram.greenBins, Color(0xFF10B981)),
                Pair(histogram.blueBins, Color(0xFF38BDF8))
            )

            for ((bins, channelColor) in channels) {
                val path = Path()
                path.moveTo(0f, h)
                for (i in 0..255) {
                    val x = (i / 255f) * w
                    val normH = (bins[i].toFloat() / maxCount) * h * 0.90f
                    path.lineTo(x, h - normH)
                }
                path.lineTo(w, h)
                path.close()

                drawPath(path = path, color = channelColor.copy(alpha = 0.15f))
                drawPath(path = path, color = channelColor.copy(alpha = 0.85f), style = Stroke(width = 1.dp.toPx()))
            }
        }

        // 4. Crushed Black (0 IRE) alert line
        if (histogram.shadowClipPct > 1.0f) {
            drawLine(color = Color(0xFFEF4444), start = Offset(1.5f, 0f), end = Offset(1.5f, h), strokeWidth = 3.dp.toPx())
        }

        // 5. Blown White (100 IRE) alert line
        if (histogram.highlightClipPct > 1.0f) {
            drawLine(color = Color(0xFFEF4444), start = Offset(w - 1.5f, 0f), end = Offset(w - 1.5f, h), strokeWidth = 3.dp.toPx())
        }
    }
}

@Composable
private fun VectorscopeOverlayCanvas(bitmap: Bitmap) {
    Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = (minOf(w, h) / 2f) - 4.dp.toPx()

        // 1. Polar grid rings (75% broadcast safe, 100% legal limit)
        drawCircle(color = Color(0xFF263248), radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = Color(0xFF1C2434), radius = radius * 0.75f, center = center, style = Stroke(width = 1.dp.toPx()))

        // 2. Center crosshairs
        drawLine(color = Color(0xFF222B3B), start = Offset(center.x - radius, center.y), end = Offset(center.x + radius, center.y), strokeWidth = 1.dp.toPx())
        drawLine(color = Color(0xFF222B3B), start = Offset(center.x, center.y - radius), end = Offset(center.x, center.y + radius), strokeWidth = 1.dp.toPx())

        // 3. Skin Tone Indicator Line ("I-Bar" at angle ~123 degrees)
        val skinAngleRad = Math.toRadians(123.0).toFloat()
        val skinEndX = center.x + cos(skinAngleRad) * radius * 0.92f
        val skinEndY = center.y - sin(skinAngleRad) * radius * 0.92f
        drawLine(
            color = ResolveAmber,
            start = center,
            end = Offset(skinEndX, skinEndY),
            strokeWidth = 1.5.dp.toPx()
        )

        // 4. Target Color Boxes (Red, Green, Blue, Cyan, Magenta, Yellow)
        val targets = listOf(
            Triple(Color(0xFFEF4444), 103f, "R"),
            Triple(Color(0xFFEC4899), 60f, "Mg"),
            Triple(Color(0xFF3B82F6), 347f, "B"),
            Triple(Color(0xFF06B6D4), 283f, "Cy"),
            Triple(Color(0xFF10B981), 241f, "G"),
            Triple(Color(0xFFFBBF24), 167f, "Yl")
        )
        for ((col, angle, _) in targets) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val bx = center.x + cos(rad) * radius * 0.75f
            val by = center.y - sin(rad) * radius * 0.75f
            drawRect(
                color = col,
                topLeft = Offset(bx - 2.5.dp.toPx(), by - 2.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        // 5. Sample bitmap chroma pixels
        val step = 14
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        val pixels = IntArray(bmpW * bmpH)
        bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)

        for (y in 0 until bmpH step step) {
            val row = y * bmpW
            for (x in 0 until bmpW step step) {
                val p = pixels[row + x]
                val r = (p shr 16 and 0xFF) / 255f
                val g = (p shr 8 and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                // Cb, Cr chrominance coordinates
                val cb = -0.168736f * r - 0.331264f * g + 0.5f * b
                val cr = 0.5f * r - 0.418688f * g - 0.081312f * b

                val px = center.x + cb * radius * 1.8f
                val py = center.y - cr * radius * 1.8f

                drawCircle(
                    color = ResolveCyan.copy(alpha = 0.45f),
                    radius = 1.1.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}
