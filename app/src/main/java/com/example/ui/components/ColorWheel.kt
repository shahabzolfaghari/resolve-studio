package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.color.SamsungLogColorScience
import com.example.model.WheelValue
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Tonal grading role for high-bitrate video footage.
 * Each role targets specific luminance ranges (Lift: shadows, Gamma: midtones, Gain: highlights).
 */
enum class WheelRole(
    val title: String,
    val tonalRange: String,
    val defaultAccent: Color,
    val defaultSensitivity: Float
) {
    LIFT("Lift", "Shadows (0-30 IRE)", WheelLiftColor, 0.012f),
    GAMMA("Gamma", "Midtones (30-70 IRE)", WheelGammaColor, 0.015f),
    GAIN("Gain", "Highlights (70-100+ IRE)", WheelGainColor, 0.014f),
    OFFSET("Offset", "Master Global", WheelOffsetColor, 0.010f);

    companion object {
        fun fromTitle(title: String): WheelRole {
            return values().firstOrNull { it.title.equals(title, ignoreCase = true) } ?: OFFSET
        }
    }
}

/**
 * Reusable, precision ColorWheel component in Jetpack Compose.
 * Provides real-time visual feedback for Hue and Saturation adjustments,
 * designed for professional colorists grading 10-bit/12-bit high-bitrate footage.
 * Supports direct touch-to-color tap, fine precision drag, RGB offset badges, and jog-wheel luminance.
 */
@Composable
fun ColorWheel(
    title: String,
    accentColor: Color = WheelRole.fromTitle(title).defaultAccent,
    value: WheelValue,
    onValueChange: (WheelValue) -> Unit,
    modifier: Modifier = Modifier,
    role: WheelRole = WheelRole.fromTitle(title),
    wheelDiameter: Dp = 126.dp,
    allowPrecisionMode: Boolean = true
) {
    var isPrecisionFineTune by remember { mutableStateOf(false) }

    // Calculate polar coordinates: Hue (0-360 deg) and Saturation (0-100%)
    val satMagnitude = sqrt(value.x * value.x + value.y * value.y).coerceIn(0f, 1f)
    val hueDegrees = remember(value.x, value.y) {
        if (satMagnitude < 0.005f) 0f
        else {
            val rawRad = atan2(value.y.toDouble(), value.x.toDouble())
            var deg = Math.toDegrees(rawRad).toFloat()
            if (deg < 0) deg += 360f
            deg
        }
    }

    // Dynamic color swatch representing the exact hue and saturation vector
    val activeTintColor = remember(hueDegrees, satMagnitude) {
        if (satMagnitude < 0.01f) {
            Color(0xFF8899A6)
        } else {
            Color.hsv(hue = hueDegrees, saturation = satMagnitude.coerceIn(0.15f, 1f), value = 1.0f)
        }
    }

    // Calculate live R, G, B channel offsets
    val (rShift, gShift, bShift) = remember(value.x, value.y) {
        SamsungLogColorScience.wheelToRgbShift(value.x, value.y)
    }

    Card(
        modifier = modifier
            .testTag("color_wheel_${title.lowercase()}")
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // ==========================================
            // 1. HEADER: Title, Tonal Range, Reset, Precision Toggle
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title.uppercase(),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = role.tonalRange,
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Precision Fine-Tune Mode Toggle (for 10-bit high bitrate adjustments)
                    if (allowPrecisionMode) {
                        IconButton(
                            onClick = { isPrecisionFineTune = !isPrecisionFineTune },
                            modifier = Modifier.size(22.dp).testTag("precision_mode_${title.lowercase()}")
                        ) {
                            Icon(
                                imageVector = if (isPrecisionFineTune) Icons.Default.CenterFocusStrong else Icons.Default.Tune,
                                contentDescription = "Precision Mode",
                                tint = if (isPrecisionFineTune) ResolveAmber else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // Reset button
                    IconButton(
                        onClick = { onValueChange(WheelValue(0f, 0f, 0f)) },
                        modifier = Modifier.size(24.dp).testTag("reset_wheel_${title.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset $title",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // 2. THE INTERACTIVE COLOR WHEEL CANVAS
            // Touch-based adjustments: direct tap & responsive trackball drag
            // ==========================================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(wheelDiameter)
                    .pointerInput(isPrecisionFineTune, value) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val center = Offset(size.width.toFloat() / 2f, size.height.toFloat() / 2f)
                                val radius = (minOf(size.width, size.height).toFloat() / 2f) - 6.dp.toPx()
                                val maxPuckDist = radius * 0.88f
                                val dx = (tapOffset.x - center.x) / maxPuckDist
                                val dy = -(tapOffset.y - center.y) / maxPuckDist // Inverted Y for screen coordinates
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist > 1f) {
                                    onValueChange(value.copy(x = (dx / dist).coerceIn(-1f, 1f), y = (dy / dist).coerceIn(-1f, 1f)))
                                } else {
                                    onValueChange(value.copy(x = dx.coerceIn(-1f, 1f), y = dy.coerceIn(-1f, 1f)))
                                }
                            },
                            onDoubleTap = {
                                onValueChange(value.copy(x = 0f, y = 0f))
                            }
                        )
                    }
                    .pointerInput(isPrecisionFineTune, value) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // High-bitrate precision sensitivity: 0.0035f vs normal 0.015f
                            val sensitivity = if (isPrecisionFineTune) 0.0035f else role.defaultSensitivity
                            val newX = (value.x + dragAmount.x * sensitivity).coerceIn(-1f, 1f)
                            val newY = (value.y - dragAmount.y * sensitivity).coerceIn(-1f, 1f)

                            val dist = sqrt(newX * newX + newY * newY)
                            if (dist > 1f) {
                                onValueChange(value.copy(x = newX / dist, y = newY / dist))
                            } else {
                                onValueChange(value.copy(x = newX, y = newY))
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f - 6.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Dark Neutral Studio Base
                    drawCircle(
                        color = Color(0xFF11141B),
                        radius = radius,
                        center = center
                    )

                    // 2. Concentric Saturation Reference Graticules (25%, 50%, 75% Saturation)
                    val satRings = floatArrayOf(0.25f, 0.50f, 0.75f)
                    for (satFrac in satRings) {
                        drawCircle(
                            color = Color(0xFF222B3B),
                            radius = radius * satFrac,
                            center = center,
                            style = Stroke(
                                width = 0.8.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))
                            )
                        )
                    }

                    // 3. 360-degree Color Spectrum Outer Ring
                    val spectrumSweep = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFEF4444), // Red 0°
                            Color(0xFFFBBF24), // Yellow 60°
                            Color(0xFF10B981), // Green 120°
                            Color(0xFF06B6D4), // Cyan 180°
                            Color(0xFF3B82F6), // Blue 240°
                            Color(0xFFEC4899), // Magenta 300°
                            Color(0xFFEF4444)  // Red 360°
                        ),
                        center = center
                    )

                    drawCircle(
                        brush = spectrumSweep,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // 4. Subtle Crosshairs
                    drawLine(
                        color = Color(0xFF263042),
                        start = Offset(center.x - radius * 0.88f, center.y),
                        end = Offset(center.x + radius * 0.88f, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF263042),
                        start = Offset(center.x, center.y - radius * 0.88f),
                        end = Offset(center.x, center.y + radius * 0.88f),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 5. Active Reticle Position
                    val puckX = center.x + value.x * radius * 0.88f
                    val puckY = center.y - value.y * radius * 0.88f // Inverted Y for screen coordinates
                    val puckOffset = Offset(puckX, puckY)

                    // 6. Visual Saturation Vector (Line from Center to Puck)
                    if (satMagnitude > 0.02f) {
                        drawLine(
                            color = activeTintColor.copy(alpha = 0.5f),
                            start = center,
                            end = puckOffset,
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // 7. Reticle Puck with Dynamic Hue Glow
                    drawCircle(
                        color = activeTintColor.copy(alpha = 0.35f),
                        radius = 8.5.dp.toPx(),
                        center = puckOffset
                    )
                    drawCircle(
                        color = activeTintColor,
                        radius = 4.5.dp.toPx(),
                        center = puckOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = puckOffset
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // 3. REAL-TIME HUE & SATURATION FEEDBACK BADGE
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1218))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                // Color Swatch Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeTintColor)
                            .border(0.8.dp, Color(0xFF475569), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "H: ${hueDegrees.toInt()}°",
                        color = TextPrimary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Saturation Percentage
                Text(
                    text = "S: ${(satMagnitude * 100).toInt()}%",
                    color = if (satMagnitude > 0.05f) ResolveCyan else TextMuted,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Precision indicator badge
                if (isPrecisionFineTune) {
                    Text(
                        text = "FINE",
                        color = ResolveAmber,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // ==========================================
            // 4. PRIMARY RGB BALANCES (DAVINCI STUDIO TELEMETRY)
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            ) {
                RgbOffsetBadge(label = "R", offset = rShift, color = Color(0xFFEF4444))
                RgbOffsetBadge(label = "G", offset = gShift, color = Color(0xFF10B981))
                RgbOffsetBadge(label = "B", offset = bShift, color = Color(0xFF38BDF8))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // 5. MASTER LUMINANCE JOG WHEEL SLIDER
            // ==========================================
            LuminanceWheelSlider(
                lumValue = value.lum,
                onLumChange = { newLum -> onValueChange(value.copy(lum = newLum)) }
            )
        }
    }
}

@Composable
private fun RgbOffsetBadge(label: String, offset: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0B0E14))
            .padding(horizontal = 4.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = String.format("%+.2f", offset),
            color = if (abs(offset) > 0.01f) TextPrimary else TextMuted,
            fontSize = 7.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * High-precision master luminance jog-wheel slider for color wheels.
 * Allows smooth shadow lift, midtone gamma balance, and highlight exposure trim.
 */
@Composable
fun LuminanceWheelSlider(
    lumValue: Float,
    onLumChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F1218))
                .border(0.8.dp, Color(0xFF1E2636), RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val sensitivity = 0.012f
                        val updated = (lumValue + dragAmount.x * sensitivity).coerceIn(-1f, 1f)
                        onLumChange(updated)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onLumChange(0f) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midX = w / 2f

                // Precision tick marks
                val numTicks = 21
                val spacing = w / (numTicks + 1)
                for (i in 1..numTicks) {
                    val tx = i * spacing
                    val isCenter = i == (numTicks + 1) / 2
                    val tickHeight = if (isCenter) h * 0.7f else h * 0.35f
                    val color = if (isCenter) ResolveAmber else Color(0xFF283142)
                    drawLine(
                        color = color,
                        start = Offset(tx, (h - tickHeight) / 2f),
                        end = Offset(tx, (h + tickHeight) / 2f),
                        strokeWidth = if (isCenter) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Active luminance needle
                val indicatorX = (midX + lumValue * (w * 0.42f)).coerceIn(4.dp.toPx(), w - 4.dp.toPx())
                drawLine(
                    color = ResolveAmber,
                    start = Offset(indicatorX, 0f),
                    end = Offset(indicatorX, h),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text("LUM MASTER", color = TextMuted, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format("%+.2f", lumValue),
                color = if (lumValue == 0f) TextSecondary else ResolveAmber,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
