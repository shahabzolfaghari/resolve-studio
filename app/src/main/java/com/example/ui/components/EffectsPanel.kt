package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ColorGradeSettings
import com.example.ui.theme.*

@Composable
fun EffectsPanel(
    settings: ColorGradeSettings,
    onSettingsChange: (ColorGradeSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("effects_panel")
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Section 1: Samsung S24 Ultra Log Input Recovery & Highlight Knee
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "SAMSUNG S24 LOG RECOVERY",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "10-bit Logarithmic Curve & Highlight Roll-Off",
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Switch(
                    checked = settings.samsungLogTransform,
                    onCheckedChange = { onSettingsChange(settings.copy(samsungLogTransform = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ResolveAmber,
                        checkedTrackColor = Color(0xFF3E2D0F)
                    ),
                    modifier = Modifier.testTag("samsung_log_transform_switch")
                )
            }

            if (settings.samsungLogTransform) {
                Spacer(modifier = Modifier.height(6.dp))
                GradeSliderRow(
                    label = "Highlight Knee",
                    value = settings.samsungHighlightRollOff,
                    defaultValue = 0.75f,
                    onValueChange = { onSettingsChange(settings.copy(samsungHighlightRollOff = it)) },
                    valueRange = 0.2f..1.0f,
                    activeColor = ResolveAmber,
                    testTag = "highlight_knee_slider"
                )
            }

            HorizontalDivider(color = StudioCardBorder, modifier = Modifier.padding(vertical = 8.dp))

            // Section 2: Film Halation, Grain Processor & Vignette
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ANALOG FILM EMULSION & OPTICAL FX",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Dedicated Organic Film Grain Processor
            FilmGrainControls(
                grain = settings.effects.grain,
                onGrainChange = { newGrain ->
                    onSettingsChange(
                        settings.copy(
                            effects = settings.effects.copy(
                                grain = newGrain,
                                grainIntensity = if (newGrain.enabled) newGrain.strength else 0f,
                                grainSize = newGrain.size
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            GradeSliderRow(
                label = "Halation Glow",
                value = settings.effects.halationIntensity,
                defaultValue = 0.0f,
                onValueChange = {
                    onSettingsChange(settings.copy(effects = settings.effects.copy(halationIntensity = it)))
                },
                valueRange = 0.0f..1.0f,
                activeColor = ResolveRed,
                formatString = "%.2f",
                testTag = "halation_slider"
            )

            if (settings.effects.halationIntensity > 0.01f) {
                GradeSliderRow(
                    label = "Halation Spread",
                    value = settings.effects.halationRadius,
                    defaultValue = 12f,
                    onValueChange = {
                        onSettingsChange(settings.copy(effects = settings.effects.copy(halationRadius = it)))
                    },
                    valueRange = 4.0f..28.0f,
                    activeColor = Color(0xFFFB923C),
                    formatString = "%.0f px",
                    testTag = "halation_radius_slider"
                )
            }

            GradeSliderRow(
                label = "Optical Vignette",
                value = settings.effects.vignetteIntensity,
                defaultValue = 0.0f,
                onValueChange = {
                    onSettingsChange(settings.copy(effects = settings.effects.copy(vignetteIntensity = it)))
                },
                valueRange = 0.0f..1.0f,
                activeColor = Color(0xFF64748B),
                formatString = "%.2f",
                testTag = "vignette_slider"
            )
        }
    }
}

@Composable
fun GradeSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    defaultValue: Float = 0f,
    formatString: String = "%.2f",
    testTag: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(formatString, value),
                    color = if (value != defaultValue) activeColor else TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { onValueChange(defaultValue) },
                    enabled = value != defaultValue,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset $label",
                        tint = if (value != defaultValue) activeColor else Color(0xFF334155),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0xFF1E2638)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .testTag(testTag)
        )
    }
}
