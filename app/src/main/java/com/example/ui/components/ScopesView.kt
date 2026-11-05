package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.color.SamsungLogColorScience
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class ScopeMode(val title: String) {
    WAVEFORM_PARADE("RGB Parade"),
    VECTORSCOPE("Vectorscope"),
    HISTOGRAM("Histogram")
}

@Composable
fun ScopesView(
    previewBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var scopeMode by remember { mutableStateOf(ScopeMode.WAVEFORM_PARADE) }

    Card(
        modifier = modifier
            .testTag("scopes_view")
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Header: Scope Switcher Tabs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "BROADCAST SCOPES",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10131B))
                        .padding(2.dp)
                ) {
                    ScopeMode.values().forEach { mode ->
                        val isSelected = scopeMode == mode
                        Surface(
                            onClick = { scopeMode = mode },
                            color = if (isSelected) ResolveAmber.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("scope_tab_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.title,
                                color = if (isSelected) ResolveAmber else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scope Display Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0C10))
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap == null) {
                    Text("No signal", color = TextMuted, fontSize = 11.sp)
                } else {
                    when (scopeMode) {
                        ScopeMode.WAVEFORM_PARADE -> WaveformParadeCanvas(previewBitmap)
                        ScopeMode.VECTORSCOPE -> VectorscopeCanvas(previewBitmap)
                        ScopeMode.HISTOGRAM -> HistogramCanvas(previewBitmap)
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformParadeCanvas(bitmap: Bitmap) {
    val waveform = remember(bitmap) {
        SamsungLogColorScience.calculateWaveform(bitmap)
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
        val w = size.width
        val h = size.height

        // Graticule horizontal lines (0, 25, 50, 75, 100 IRE)
        for (ire in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val y = (1f - ire) * h
            drawLine(
                color = if (ire == 0f || ire == 1f) Color(0xFF334155) else Color(0xFF1E2636),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw RGB Parade (3 columns: R, G, B side by side)
        val colWidth = w / 3f
        val gap = 4.dp.toPx()

        val grids = listOf(
            Triple(waveform.redGrid, Color(0xFFEF4444), 0),
            Triple(waveform.greenGrid, Color(0xFF10B981), 1),
            Triple(waveform.blueGrid, Color(0xFF3B82F6), 2)
        )

        for ((grid, color, colIdx) in grids) {
            val startX = colIdx * colWidth + gap / 2f
            val endX = (colIdx + 1) * colWidth - gap / 2f
            val subWidth = endX - startX

            // Column boundary divider
            if (colIdx > 0) {
                drawLine(
                    color = Color(0xFF263042),
                    start = Offset(colIdx * colWidth, 0f),
                    end = Offset(colIdx * colWidth, h),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw density dots/traces
            val stepCol = (waveform.columns / 3).coerceAtLeast(1)
            for (x in 0 until stepCol) {
                val px = startX + (x.toFloat() / stepCol) * subWidth
                val sampleX = (colIdx * stepCol + x).coerceIn(0, waveform.columns - 1)
                for (y in 0 until waveform.rows) {
                    val density = grid[sampleX][y]
                    if (density > 0f) {
                        val py = (1f - y.toFloat() / waveform.rows) * h
                        val alpha = (density * 0.35f).coerceIn(0.2f, 0.85f)
                        drawCircle(
                            color = color.copy(alpha = alpha),
                            radius = 1.2.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VectorscopeCanvas(bitmap: Bitmap) {
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = (minOf(w, h) / 2f) - 8.dp.toPx()

        // Polar grid rings (75% broadcast safe, 100% legal limit)
        drawCircle(
            color = Color(0xFF2A3448),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF1E2636),
            radius = radius * 0.75f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Center crosshairs
        drawLine(
            color = Color(0xFF242C3C),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFF242C3C),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.dp.toPx()
        )

        // Skin Tone Indicator Line ("I-Bar" at angle ~123 degrees)
        val skinAngleRad = Math.toRadians(123.0).toFloat()
        val skinEndX = center.x + cos(skinAngleRad) * radius * 0.9f
        val skinEndY = center.y - sin(skinAngleRad) * radius * 0.9f
        drawLine(
            color = ResolveAmber,
            start = center,
            end = Offset(skinEndX, skinEndY),
            strokeWidth = 1.5.dp.toPx()
        )

        // Target Color Boxes (Red, Green, Blue, Cyan, Magenta, Yellow)
        val targets = listOf(
            Triple(Color(0xFFEF4444), 103f, "R"),
            Triple(Color(0xFFEC4899), 60f, "Mg"),
            Triple(Color(0xFF3B82F6), 347f, "B"),
            Triple(Color(0xFF06B6D4), 283f, "Cy"),
            Triple(Color(0xFF10B981), 241f, "G"),
            Triple(Color(0xFFFBBF24), 167f, "Yl")
        )
        for ((col, angle, label) in targets) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val bx = center.x + cos(rad) * radius * 0.75f
            val by = center.y - sin(rad) * radius * 0.75f
            drawRect(
                color = col,
                topLeft = Offset(bx - 3.dp.toPx(), by - 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Sample bitmap chroma pixels
        val step = 16
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
                    radius = 1.2.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}

@Composable
fun HistogramCanvas(bitmap: Bitmap) {
    val histogram = remember(bitmap) {
        SamsungLogColorScience.calculateHistogram(bitmap)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val w = size.width
            val h = size.height

            // IRE Graticule Grid Lines (0%, 25%, 50%, 75%, 100%)
            for (fraction in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                val gx = fraction * w
                drawLine(
                    color = Color(0xFF1E2636),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Baseline 0 IRE & 100 IRE lines
            drawLine(
                color = Color(0xFF283245),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1.dp.toPx()
            )

            // Find max count to normalize
            var maxCount = 1
            for (i in 0..255) {
                maxCount = maxOf(maxCount, histogram.redBins[i], histogram.greenBins[i], histogram.blueBins[i], histogram.lumBins[i])
            }

            // 1. Draw Luminance Area (Subtle white/gray tone)
            val lumPath = Path()
            lumPath.moveTo(0f, h)
            for (i in 0..255) {
                val x = (i / 255f) * w
                val normHeight = (histogram.lumBins[i].toFloat() / maxCount) * h * 0.95f
                lumPath.lineTo(x, h - normHeight)
            }
            lumPath.lineTo(w, h)
            lumPath.close()

            drawPath(
                path = lumPath,
                color = Color.White.copy(alpha = 0.08f)
            )
            drawPath(
                path = lumPath,
                color = Color.White.copy(alpha = 0.45f),
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Draw RGB Channels
            val channels = listOf(
                Pair(histogram.redBins, Color(0xFFEF4444)),
                Pair(histogram.greenBins, Color(0xFF10B981)),
                Pair(histogram.blueBins, Color(0xFF3B82F6))
            )

            for ((bins, color) in channels) {
                val path = Path()
                path.moveTo(0f, h)
                for (i in 0..255) {
                    val x = (i / 255f) * w
                    val normHeight = (bins[i].toFloat() / maxCount) * h * 0.92f
                    path.lineTo(x, h - normHeight)
                }
                path.lineTo(w, h)
                path.close()

                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.22f)
                )
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.85f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Left edge indicator: 0 IRE
            if (histogram.shadowClipPct > 0.5f) {
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(1f, 0f),
                    end = Offset(1f, h),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // Right edge indicator: 100 IRE
            if (histogram.highlightClipPct > 0.5f) {
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(w - 1f, 0f),
                    end = Offset(w - 1f, h),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // Live Real-Time Telemetry HUD (IRE readings & clipping badges)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shadow Clip Badge
            if (histogram.shadowClipPct > 0.5f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFDC2626))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "BLACK CRUSH ${"%.1f".format(histogram.shadowClipPct)}%",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF131722))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("0 IRE (SAFE)", color = TextMuted, fontSize = 7.5.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Highlight Clip Badge
            if (histogram.highlightClipPct > 0.5f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFDC2626))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "WHITE CLIP ${"%.1f".format(histogram.highlightClipPct)}%",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF131722))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("100 IRE (SAFE)", color = TextMuted, fontSize = 7.5.sp)
                }
            }

            // Average Luma Readout
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF171D28))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                val avgPct = (histogram.avgLum / 255f * 100).toInt()
                Text(
                    text = "LUMA $avgPct%",
                    color = ResolveAmber,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
