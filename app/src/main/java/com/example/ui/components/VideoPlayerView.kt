package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MaskType
import com.example.model.PowerWindowMask
import com.example.ui.theme.*

enum class ViewportCropGuide(val label: String, val ratio: Float?) {
    NONE("16:9", null),
    REELS_9_16("9:16 Reels", 9f / 16f),
    SQUARE_1_1("1:1", 1f),
    ANAMORPHIC_2_39("2.39:1", 2.39f);

    fun next(): ViewportCropGuide {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

@Composable
fun VideoPlayerView(
    gradedBitmap: Bitmap?,
    rawBitmap: Bitmap?,
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    mask: PowerWindowMask,
    showMaskGuide: Boolean,
    onMaskChange: (PowerWindowMask) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onStepFrame: (Int) -> Unit,
    onBypassPress: (Boolean) -> Unit,
    showFalseColor: Boolean = false,
    onToggleFalseColor: () -> Unit = {},
    showHistogramOverlay: Boolean = false,
    onToggleHistogramOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var splitPosition by remember { mutableStateOf<Float?>(null) } // null = full graded, 0..1 = split wipe
    var cropGuide by remember { mutableStateOf(ViewportCropGuide.NONE) }
    var pinMaskWireframe by remember { mutableStateOf(true) }
    val isMaskWireframeVisible = (showMaskGuide || pinMaskWireframe) && mask.type != MaskType.NONE

    Card(
        modifier = modifier
            .testTag("video_player_viewport")
            .padding(horizontal = 4.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Top overlay bar: Timecode, Crop Guide, Scopes, Wipe split toggle, and Hold to Bypass
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                // Timecode + Aspect Crop Guide Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimecode(currentTimeMs),
                        color = ResolveAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Aspect Ratio Framing Guide Selector Pill (16:9 -> 9:16 Reels -> 1:1 -> 2.39:1)
                    Surface(
                        color = if (cropGuide != ViewportCropGuide.NONE) ResolveCyan.copy(alpha = 0.2f) else Color(0xFF1A202C),
                        shape = RoundedCornerShape(5.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (cropGuide != ViewportCropGuide.NONE) ResolveCyan else Color(0xFF2D3748)
                            )
                        ),
                        modifier = Modifier
                            .height(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { cropGuide = cropGuide.next() }
                            .testTag("aspect_guide_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = "Aspect Ratio Framing Guide",
                                tint = if (cropGuide != ViewportCropGuide.NONE) ResolveCyan else TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = cropGuide.label,
                                color = if (cropGuide != ViewportCropGuide.NONE) ResolveCyan else TextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Power Window Wireframe Toggle (shown when an active Power Window mask is enabled)
                    if (mask.type != MaskType.NONE) {
                        IconButton(
                            onClick = { pinMaskWireframe = !pinMaskWireframe },
                            modifier = Modifier.size(28.dp).testTag("mask_wireframe_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Power Window Wireframe",
                                tint = if (isMaskWireframeVisible) ResolveAmber else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // False Color Exposure Toggle
                    IconButton(
                        onClick = onToggleFalseColor,
                        modifier = Modifier.size(28.dp).testTag("false_color_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterVintage,
                            contentDescription = "False Color Exposure Meter",
                            tint = if (showFalseColor) ResolveGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Real-time Histogram & Vectorscope Overlay Toggle
                    IconButton(
                        onClick = onToggleHistogramOverlay,
                        modifier = Modifier.size(28.dp).testTag("histogram_overlay_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Real-time Scope Overlay",
                            tint = if (showHistogramOverlay) ResolveCyan else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Split Wipe toggle
                    IconButton(
                        onClick = {
                            splitPosition = if (splitPosition == null) 0.5f else null
                        },
                        modifier = Modifier.size(28.dp).testTag("split_wipe_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Split Screen Wipe",
                            tint = if (splitPosition != null) ResolveAmber else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Hold to Bypass Grade button
                    Surface(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onBypassPress(true)
                                        tryAwaitRelease()
                                        onBypassPress(false)
                                    }
                                )
                            }
                            .testTag("hold_to_bypass_button"),
                        color = Color(0xFF283142)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("BYPASS", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Video Surface Box (fitted 16:9 viewport with exact video-rect mask alignment)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight
                val bmp = gradedBitmap ?: rawBitmap
                val (contentWidth, contentHeight) = if (bmp != null && bmp.height > 0) {
                    val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat()
                    val boxAspect = 16f / 9f
                    if (bmpAspect > boxAspect) {
                        Pair(boxWidth, boxWidth / bmpAspect)
                    } else {
                        Pair(boxHeight * bmpAspect, boxHeight)
                    }
                } else {
                    Pair(boxWidth, boxHeight)
                }
                // 1. Render graded frame (full frame base)
                gradedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Graded Video Viewport",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. If Split wipe is active, render raw Log frame clipped to the left side of splitPosition
                val currentSplit = splitPosition
                if (currentSplit != null && rawBitmap != null) {
                    Image(
                        bitmap = rawBitmap.asImageBitmap(),
                        contentDescription = "Raw Log Split Comparison",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val splitX = currentSplit * size.width
                                clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    )

                    // Interactive Split Line & Handle Canvas + Side Labels
                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newPos = ((splitPosition ?: 0.5f) + dragAmount.x / size.width).coerceIn(0.05f, 0.95f)
                                        splitPosition = newPos
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val splitX = currentSplit * w

                            // Draw wipe divider line
                            drawLine(
                                color = ResolveAmber,
                                start = Offset(splitX, 0f),
                                end = Offset(splitX, h),
                                strokeWidth = 2.dp.toPx()
                            )
                            // Outer glow & handle puck
                            drawCircle(
                                color = ResolveAmber.copy(alpha = 0.3f),
                                radius = 12.dp.toPx(),
                                center = Offset(splitX, h / 2f)
                            )
                            drawCircle(
                                color = ResolveAmber,
                                radius = 6.dp.toPx(),
                                center = Offset(splitX, h / 2f)
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 2.dp.toPx(),
                                center = Offset(splitX, h / 2f)
                            )
                        }

                        // Left badge: S24 LOG RAW
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC0D1117))
                                .border(0.8.dp, ResolveCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RAW LOG",
                                color = ResolveCyan,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Right badge: GRADED REC.709
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC0D1117))
                                .border(0.8.dp, ResolveAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "GRADED",
                                color = ResolveAmber,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. Aspect Ratio Framing Guide Overlay (9:16 Reels, 1:1 Square, 2.39:1 CinemaScope)
                val targetRatio = cropGuide.ratio
                if (targetRatio != null) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val viewportRatio = w / h

                        val (cropW, cropH) = if (targetRatio < viewportRatio) {
                            // Pillarboxed (e.g., 9:16 Reels or 1:1 inside 16:9)
                            Pair(h * targetRatio, h)
                        } else {
                            // Letterboxed (e.g., 2.39:1 inside 16:9)
                            Pair(w, w / targetRatio)
                        }
                        val left = (w - cropW) / 2f
                        val top = (h - cropH) / 2f
                        val right = left + cropW
                        val bottom = top + cropH

                        val matteColor = Color.Black.copy(alpha = 0.58f)
                        // Draw darkened matte regions outside the active crop frame
                        if (left > 0f) {
                            drawRect(color = matteColor, topLeft = Offset(0f, 0f), size = Size(left, h))
                            drawRect(color = matteColor, topLeft = Offset(right, 0f), size = Size(w - right, h))
                        }
                        if (top > 0f) {
                            drawRect(color = matteColor, topLeft = Offset(0f, 0f), size = Size(w, top))
                            drawRect(color = matteColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
                        }

                        // Rule-of-thirds grid inside the target crop box
                        val thirdW = cropW / 3f
                        val thirdH = cropH / 3f
                        val gridColor = Color.White.copy(alpha = 0.22f)
                        for (i in 1..2) {
                            drawLine(
                                color = gridColor,
                                start = Offset(left + thirdW * i, top),
                                end = Offset(left + thirdW * i, bottom),
                                strokeWidth = 0.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(left, top + thirdH * i),
                                end = Offset(right, top + thirdH * i),
                                strokeWidth = 0.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                            )
                        }

                        // Crop boundary outline
                        drawRect(
                            color = ResolveCyan.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(cropW, cropH),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                // 4. Interactive Mask Overlay Guide (B-5: Aligned strictly to the fitted video content bounds)
                if (isMaskWireframeVisible) {
                    Box(
                        modifier = Modifier
                            .size(contentWidth, contentHeight)
                            .align(Alignment.Center)
                    ) {
                        PowerWindowVisualGuide(mask = mask, onMaskChange = onMaskChange)
                    }
                }

                // 5. Real-time Histogram & Vectorscope Overlay HUD
                if (showHistogramOverlay) {
                    RealtimeHistogramOverlay(
                        gradedBitmap = gradedBitmap,
                        rawBitmap = rawBitmap,
                        onClose = onToggleHistogramOverlay,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timeline Scrubber & Transport Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                // Step back 1 frame
                IconButton(
                    onClick = { onStepFrame(-1) },
                    modifier = Modifier.size(28.dp).testTag("step_back_button")
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Step Back", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                // Play / Pause
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(32.dp).testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ResolveAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Step forward 1 frame
                IconButton(
                    onClick = { onStepFrame(1) },
                    modifier = Modifier.size(28.dp).testTag("step_forward_button")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Step Forward", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Timeline Scrubber Slider
                val progress = if (totalDurationMs > 0) (currentTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = ResolveAmber,
                        activeTrackColor = ResolveAmber,
                        inactiveTrackColor = Color(0xFF283142)
                    ),
                    modifier = Modifier.weight(1f).height(24.dp).testTag("timeline_scrubber")
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Duration
                Text(
                    text = formatTimecode(totalDurationMs),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatTimecode(ms: Long): String {
    val totalSec = ms / 1000
    val frames = ((ms % 1000) / (1000f / 30f)).toInt()
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hrs = totalSec / 3600
    return String.format("%02d:%02d:%02d:%02d", hrs, min, sec, frames)
}
