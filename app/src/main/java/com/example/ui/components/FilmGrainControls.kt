package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.color.FilmGrainProcessor
import com.example.model.FilmGrainSettings
import com.example.model.FilmStockPreset
import com.example.ui.theme.*

/**
 * Professional DaVinci Resolve-inspired Film Grain Processor UI.
 * Controls authentic organic grain textures with adjustments for size, roughness, and strength.
 */
@Composable
fun FilmGrainControls(
    grain: FilmGrainSettings,
    onGrainChange: (FilmGrainSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate live magnified grain texture swatch
    val textureSample: Bitmap = remember(grain.size, grain.roughness, grain.strength, grain.monochrome) {
        FilmGrainProcessor.generateGrainTextureSample(
            width = 160,
            height = 70,
            settings = grain
        )
    }

    Card(
        modifier = modifier
            .testTag("film_grain_processor_card")
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF263248)))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // ==========================================
            // 1. HEADER ROW & MASTER GRAIN SWITCH
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Grain,
                        contentDescription = "Film Grain",
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "ORGANIC FILM GRAIN PROCESSOR",
                            color = TextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Silver Halide & Dye Cloud Emulsion Texture",
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Switch(
                    checked = grain.enabled,
                    onCheckedChange = { onGrainChange(grain.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ResolveAmber,
                        checkedTrackColor = Color(0xFF3E2D0F)
                    ),
                    modifier = Modifier.testTag("film_grain_enabled_switch")
                )
            }

            AnimatedVisibility(visible = grain.enabled) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // ==========================================
                    // 2. FILM STOCK PRESETS CHIPS
                    // ==========================================
                    Text(
                        text = "FILM STOCK PRESETS",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilmStockPreset.values().forEach { preset ->
                            val isSelected = grain.preset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (preset != FilmStockPreset.CUSTOM) {
                                        onGrainChange(
                                            grain.copy(
                                                preset = preset,
                                                size = preset.size,
                                                roughness = preset.roughness,
                                                strength = preset.strength
                                            )
                                        )
                                    } else {
                                        onGrainChange(grain.copy(preset = FilmStockPreset.CUSTOM))
                                    }
                                },
                                label = { Text(preset.displayName, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ResolveAmber.copy(alpha = 0.22f),
                                    selectedLabelColor = ResolveAmber,
                                    containerColor = Color(0xFF0F1218),
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) ResolveAmber else StudioCardBorder
                                ),
                                modifier = Modifier.height(28.dp).testTag("grain_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ==========================================
                    // 3. LIVE GRAIN LOUPE TEXTURE PREVIEW
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "LIVE TEXTURE LOUPE (100% MAGNIFICATION)",
                            color = TextMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = if (grain.monochrome) "Silver Halide (B&W)" else "Color Dye (3-Layer)",
                            color = ResolveCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF242E40), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = textureSample.asImageBitmap(),
                            contentDescription = "Grain Texture Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )

                        // Subtle center reticle
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ==========================================
                    // 4. PRIMARY CONTROLS: STRENGTH, SIZE, ROUGHNESS
                    // ==========================================

                    // Grain Strength Slider
                    GrainSliderRow(
                        label = "Strength",
                        value = grain.strength,
                        valueRange = 0.0f..1.0f,
                        activeColor = ResolveAmber,
                        displayValue = "${(grain.strength * 100).toInt()}%",
                        onValueChange = {
                            onGrainChange(grain.copy(strength = it, preset = FilmStockPreset.CUSTOM))
                        },
                        testTag = "grain_strength_slider"
                    )

                    // Grain Size Slider
                    GrainSliderRow(
                        label = "Size",
                        value = grain.size,
                        valueRange = 0.5f..3.0f,
                        activeColor = ResolveCyan,
                        displayValue = "%.2fx".format(grain.size),
                        onValueChange = {
                            onGrainChange(grain.copy(size = it, preset = FilmStockPreset.CUSTOM))
                        },
                        testTag = "grain_size_slider"
                    )

                    // Grain Roughness Slider
                    GrainSliderRow(
                        label = "Roughness",
                        value = grain.roughness,
                        valueRange = 0.0f..1.0f,
                        activeColor = Color(0xFFEC4899),
                        displayValue = "%.2f".format(grain.roughness),
                        onValueChange = {
                            onGrainChange(grain.copy(roughness = it, preset = FilmStockPreset.CUSTOM))
                        },
                        testTag = "grain_roughness_slider"
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ==========================================
                    // 5. EMULSION COLOR DYE & TONAL RESPONSE
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = "Monochrome Silver",
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )

                        Switch(
                            checked = grain.monochrome,
                            onCheckedChange = { onGrainChange(grain.copy(monochrome = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE2E8F0),
                                checkedTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("grain_monochrome_switch")
                        )
                    }

                    // Midtone Emulsion Bias Slider
                    GrainSliderRow(
                        label = "Midtone Bias",
                        value = grain.midtoneBias,
                        valueRange = 0.2f..1.0f,
                        activeColor = Color(0xFF10B981),
                        displayValue = "%.2f".format(grain.midtoneBias),
                        onValueChange = {
                            onGrainChange(grain.copy(midtoneBias = it, preset = FilmStockPreset.CUSTOM))
                        },
                        testTag = "grain_midtone_bias_slider"
                    )
                }
            }
        }
    }
}

@Composable
private fun GrainSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(78.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0xFF202636)
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = displayValue,
            color = TextPrimary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
