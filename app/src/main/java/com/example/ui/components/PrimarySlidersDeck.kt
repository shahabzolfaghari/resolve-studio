package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ColorGradeSettings
import com.example.ui.theme.*

/**
 * DaVinci Resolve Studio Primary Adjustment Sliders Deck.
 * Hosts the 8 core grading parameters:
 * Brightness (Exposure EV), Contrast, Temperature, Tint, Saturation, Shadows, Highlights, and Color Boost.
 */
@Composable
fun PrimarySlidersDeck(
    settings: ColorGradeSettings,
    onSettingsChange: (description: String, transform: (ColorGradeSettings) -> ColorGradeSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("primary_sliders_deck")
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10131B)),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF222B3D)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PRIMARY ADJUSTMENT BARS",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                // Reset All Primary Sliders
                Surface(
                    onClick = {
                        onSettingsChange("Reset Primary Sliders") {
                            it.copy(
                                brightness = 0.0f,
                                exposure = 0.0f,
                                contrast = 1.0f,
                                temperature = 0.0f,
                                tint = 0.0f,
                                saturation = 1.0f,
                                shadows = 0.0f,
                                highlights = 0.0f,
                                colorBoost = 0.0f
                            )
                        }
                    },
                    color = Color(0xFF1A2232),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("reset_primary_sliders_button")
                ) {
                    Text(
                        text = "RESET ALL",
                        color = ResolveAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Exposure EV Slider (Photometric Stop Multiplier)
            ResolveSliderItem(
                label = "Exposure (EV)",
                value = settings.exposure,
                valueRange = -2.0f..2.0f,
                defaultValue = 0.0f,
                formattedValue = String.format("%+.2f EV", settings.exposure),
                activeColor = ResolveCyan,
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Exposure") { it.copy(exposure = newVal) }
                },
                testTag = "slider_exposure"
            )

            // 2. Brightness / Black Floor Lift Slider
            ResolveSliderItem(
                label = "Brightness (Lift)",
                value = settings.brightness,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.brightness > 0) "+${(settings.brightness * 100).toInt()}" else "${(settings.brightness * 100).toInt()}",
                activeColor = ResolveAmber,
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Brightness") { it.copy(brightness = newVal) }
                },
                testTag = "slider_brightness"
            )

            // 3. Contrast Slider
            ResolveSliderItem(
                label = "Contrast",
                value = settings.contrast,
                valueRange = 0.5f..1.8f,
                defaultValue = 1.0f,
                formattedValue = String.format("%.2f", settings.contrast),
                activeColor = Color.White,
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Contrast") { it.copy(contrast = newVal) }
                },
                testTag = "slider_contrast"
            )

            // 3. Temperature Slider
            ResolveSliderItem(
                label = "Temperature (WB)",
                value = settings.temperature,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.temperature > 0) "+${(settings.temperature * 100).toInt()}" else "${(settings.temperature * 100).toInt()}",
                activeColor = Color(0xFFF59E0B),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Temperature") { it.copy(temperature = newVal) }
                },
                testTag = "slider_temperature"
            )

            // 4. Tint Slider
            ResolveSliderItem(
                label = "Tint (Green / Magenta)",
                value = settings.tint,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.tint > 0) "+${(settings.tint * 100).toInt()}" else "${(settings.tint * 100).toInt()}",
                activeColor = Color(0xFFEC4899),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Tint") { it.copy(tint = newVal) }
                },
                testTag = "slider_tint"
            )

            // 5. Saturation Slider
            ResolveSliderItem(
                label = "Saturation",
                value = settings.saturation,
                valueRange = 0.0f..2.0f,
                defaultValue = 1.0f,
                formattedValue = "${(settings.saturation * 100).toInt()}%",
                activeColor = Color(0xFF10B981),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Saturation") { it.copy(saturation = newVal) }
                },
                testTag = "slider_saturation"
            )

            // 6. Color Boost (Smart Vibrance)
            ResolveSliderItem(
                label = "Color Boost",
                value = settings.colorBoost,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.colorBoost > 0) "+${(settings.colorBoost * 100).toInt()}" else "${(settings.colorBoost * 100).toInt()}",
                activeColor = Color(0xFF8B5CF6),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Color Boost") { it.copy(colorBoost = newVal) }
                },
                testTag = "slider_color_boost"
            )

            // 7. Shadows Recovery Slider
            ResolveSliderItem(
                label = "Shadows",
                value = settings.shadows,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.shadows > 0) "+${(settings.shadows * 100).toInt()}" else "${(settings.shadows * 100).toInt()}",
                activeColor = Color(0xFF38BDF8),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Shadows") { it.copy(shadows = newVal) }
                },
                testTag = "slider_shadows"
            )

            // 8. Highlights Compression Slider
            ResolveSliderItem(
                label = "Highlights",
                value = settings.highlights,
                valueRange = -1.0f..1.0f,
                defaultValue = 0.0f,
                formattedValue = if (settings.highlights > 0) "+${(settings.highlights * 100).toInt()}" else "${(settings.highlights * 100).toInt()}",
                activeColor = Color(0xFFFB923C),
                onValueChange = { newVal ->
                    onSettingsChange("Adjust Highlights") { it.copy(highlights = newVal) }
                },
                testTag = "slider_highlights"
            )
        }
    }
}

@Composable
private fun ResolveSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    formattedValue: String,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp)
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
                    text = formattedValue,
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
