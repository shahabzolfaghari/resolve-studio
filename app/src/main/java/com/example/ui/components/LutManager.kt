package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.model.CustomLut
import com.example.model.LutCategory
import com.example.model.LutPreset
import com.example.ui.theme.*

/**
 * Professional DaVinci Resolve-inspired 3D LUT Manager.
 * Features built-in Samsung S24 Ultra Log -> Rec.709 transforms, cinema film stock emulations,
 * instant A/B preview toggle, custom user LUT creation, and .cube import/export.
 */
@Composable
fun LutManager(
    selectedLut: LutPreset,
    lutIntensity: Float,
    lutBypass: Boolean,
    customLuts: List<CustomLut>,
    selectedCustomLutId: String?,
    onLutChange: (LutPreset) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onToggleLutBypass: () -> Unit,
    onSelectCustomLut: (CustomLut) -> Unit,
    onCreateCustomLutClick: () -> Unit,
    onDeleteCustomLut: (String) -> Unit,
    onImportCubeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryFilter by remember { mutableStateOf<LutCategory?>(null) }

    val filteredPresets = remember(selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            LutPreset.values().toList()
        } else {
            LutPreset.values().filter { it.category == selectedCategoryFilter }
        }
    }

    Card(
        modifier = modifier
            .testTag("lut_manager_container")
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioCardBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // ==========================================
            // 1. TOP HEADER & INSTANT A/B PREVIEW TOGGLE
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "LUTs",
                            tint = ResolveAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "3D LUT MANAGER",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "Samsung S24 Log 10-bit Color Science",
                        color = ResolveCyan,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Instant A/B LUT Preview Toggle Button
                Surface(
                    onClick = onToggleLutBypass,
                    color = if (!lutBypass) ResolveGreen.copy(alpha = 0.2f) else Color(0xFF332025),
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (!lutBypass) ResolveGreen else Color(0xFFEF4444)
                        )
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("lut_preview_toggle_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (!lutBypass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle LUT Preview",
                            tint = if (!lutBypass) ResolveGreen else Color(0xFFEF4444),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (!lutBypass) "LUT ACTIVE (A)" else "BYPASS (B)",
                            color = if (!lutBypass) ResolveGreen else Color(0xFFEF4444),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // 2. CATEGORY FILTER CHIPS & ACTION ROW
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Chips Scrollable Row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // All Chip
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("All Looks", fontSize = 9.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                            selectedLabelColor = ResolveAmber,
                            containerColor = Color(0xFF131722),
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategoryFilter == null,
                            borderColor = if (selectedCategoryFilter == null) ResolveAmber else StudioCardBorder
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    LutCategory.values().forEach { category ->
                        val isSelected = selectedCategoryFilter == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryFilter = if (isSelected) null else category },
                            label = { Text(category.displayName, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                                selectedLabelColor = ResolveAmber,
                                containerColor = Color(0xFF131722),
                                labelColor = TextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) ResolveAmber else StudioCardBorder
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // "Create New LUT" CTA Button
                Button(
                    onClick = onCreateCustomLutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("create_custom_lut_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Save LUT", color = ResolveAmber, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Import .cube Button
                IconButton(
                    onClick = onImportCubeClick,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1B2332))
                        .testTag("import_cube_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Import .cube",
                        tint = ResolveCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // 3. BUILT-IN & CUSTOM LUT CARDS ROW
            // ==========================================
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // If viewing CUSTOM or ALL, show user saved custom LUTs first
                if (selectedCategoryFilter == null || selectedCategoryFilter == LutCategory.CUSTOM) {
                    items(customLuts) { custom ->
                        val isSelected = selectedLut == LutPreset.CUSTOM_SAVED && selectedCustomLutId == custom.id
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(82.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF26334D) else Color(0xFF141924))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ResolveAmber else Color(0xFF2E384D),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCustomLut(custom) }
                                .padding(8.dp)
                                .testTag("custom_lut_item_${custom.id}"),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = custom.name,
                                        color = if (isSelected) ResolveAmber else TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete LUT",
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable { onDeleteCustomLut(custom.id) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = custom.description,
                                    color = TextMuted,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Visual Look Swatch Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF1F2937),
                                                    ResolveAmber.copy(alpha = 0.8f),
                                                    Color(0xFF38BDF8)
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                // Standard Preset Library Cards
                items(filteredPresets) { preset ->
                    val isSelected = selectedLut == preset
                    val swatchGradient = getPresetGradient(preset)

                    Box(
                        modifier = Modifier
                            .width(155.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF242C3F) else Color(0xFF121620))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ResolveAmber else StudioCardBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onLutChange(preset) }
                            .padding(9.dp)
                            .testTag("lut_item_${preset.name.lowercase()}"),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preset.displayName,
                                    color = if (isSelected) ResolveAmber else TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ResolveAmber)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.description,
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Visual Color Signature Palette Swatch
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(swatchGradient)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 4. LUT OPACITY / INTENSITY SLIDER
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "LUT Intensity",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(lutIntensity * 100).toInt()}%",
                            color = ResolveAmber,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // 100% Reset Button
                        IconButton(
                            onClick = { onIntensityChange(1.0f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Opacity to 100%",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = lutIntensity,
                    onValueChange = onIntensityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ResolveAmber,
                        activeTrackColor = ResolveAmber,
                        inactiveTrackColor = Color(0xFF263244)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("lut_intensity_slider")
                )
            }
        }
    }
}

/**
 * Returns distinct visual color signature gradients for each LUT look.
 */
private fun getPresetGradient(preset: LutPreset): Brush {
    return when (preset) {
        LutPreset.NONE -> Brush.horizontalGradient(
            listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF94A3B8))
        )
        LutPreset.SAMSUNG_LOG_TO_REC709 -> Brush.horizontalGradient(
            listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFFE2E8F0))
        )
        LutPreset.SAMSUNG_REC709_PUNCH -> Brush.horizontalGradient(
            listOf(Color(0xFF0F172A), Color(0xFFF97316), Color(0xFF38BDF8))
        )
        LutPreset.SAMSUNG_CINE_WARM -> Brush.horizontalGradient(
            listOf(Color(0xFF451A03), Color(0xFFD97706), Color(0xFFFEF3C7))
        )
        // 7 Famous Fujifilm Film Recipes
        LutPreset.FUJI_CLASSIC_CHROME -> Brush.horizontalGradient(
            listOf(Color(0xFF2E3842), Color(0xFF5D707F), Color(0xFFC7D3D9))
        )
        LutPreset.FUJI_CLASSIC_NEGATIVE -> Brush.horizontalGradient(
            listOf(Color(0xFF381528), Color(0xFF047857), Color(0xFF38BDF8))
        )
        LutPreset.FUJI_NOSTALGIC_NEGATIVE -> Brush.horizontalGradient(
            listOf(Color(0xFF451A03), Color(0xFFD97706), Color(0xFFFDE68A))
        )
        LutPreset.FUJI_VELVIA -> Brush.horizontalGradient(
            listOf(Color(0xFF0369A1), Color(0xFF15803D), Color(0xFFDC2626))
        )
        LutPreset.FUJI_PROVIA -> Brush.horizontalGradient(
            listOf(Color(0xFF1E293B), Color(0xFF64748B), Color(0xFFF8FAFC))
        )
        LutPreset.FUJI_ASTIA -> Brush.horizontalGradient(
            listOf(Color(0xFF831843), Color(0xFFF472B6), Color(0xFFFED7AA))
        )
        LutPreset.FUJI_ACROS -> Brush.horizontalGradient(
            listOf(Color(0xFF000000), Color(0xFF475569), Color(0xFFE2E8F0))
        )
        LutPreset.FILM_EMULATION_KODAK -> Brush.horizontalGradient(
            listOf(Color(0xFF134E4A), Color(0xFFB45309), Color(0xFFFEF08A))
        )
        LutPreset.FILM_FUJI_ETERNA -> Brush.horizontalGradient(
            listOf(Color(0xFF1E293B), Color(0xFF4D7C0F), Color(0xFFFBCFE8))
        )
        LutPreset.BLOCKBUSTER_TEAL_ORANGE -> Brush.horizontalGradient(
            listOf(Color(0xFF0E7490), Color(0xFF0369A1), Color(0xFFEA580C))
        )
        LutPreset.CLEAN_COMMERCIAL -> Brush.horizontalGradient(
            listOf(Color(0xFF3B82F6), Color(0xFFF1F5F9), Color(0xFFFFFFFF))
        )
        LutPreset.MONOCHROME_NOIR -> Brush.horizontalGradient(
            listOf(Color(0xFF000000), Color(0xFF6B7280), Color(0xFFFFFFFF))
        )
        LutPreset.CYBER_NEON_NIGHT -> Brush.horizontalGradient(
            listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFEC4899))
        )
        LutPreset.VINTAGE_SEPIA -> Brush.horizontalGradient(
            listOf(Color(0xFF291A0E), Color(0xFF854D0E), Color(0xFFFDE68A))
        )
        LutPreset.CUSTOM_SAVED, LutPreset.CUSTOM_CUBE -> Brush.horizontalGradient(
            listOf(Color(0xFF312E81), ResolveAmber, Color(0xFF06B6D4))
        )
    }
}
