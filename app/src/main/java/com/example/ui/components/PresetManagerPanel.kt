package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GradePreset
import com.example.ui.theme.*

/**
 * DaVinci Resolve-inspired Preset Manager Panel.
 * Allows users to browse curated built-in looks and custom saved user grading configurations,
 * filter by category, inspect settings, and apply looks to active video clips with one tap.
 */
@Composable
fun PresetManagerPanel(
    presets: List<GradePreset>,
    onApplyPreset: (GradePreset) -> Unit,
    onCreatePresetClick: () -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>("All") }
    var presetToDelete by remember { mutableStateOf<GradePreset?>(null) }

    val categories = remember(presets) {
        listOf("All", "Built-In", "Saved Looks") +
                presets.map { it.category }.distinct().filterNot { it.isBlank() }
    }

    val filteredPresets = remember(presets, searchQuery, selectedCategoryFilter) {
        presets.filter { preset ->
            val matchesCategory = when (selectedCategoryFilter) {
                null, "All" -> true
                "Built-In" -> preset.isBuiltIn
                "Saved Looks" -> !preset.isBuiltIn
                else -> preset.category.equals(selectedCategoryFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true) ||
                    preset.category.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    Card(
        modifier = modifier
            .testTag("preset_manager_panel")
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
            // 1. HEADER & "+ SAVE CURRENT GRADE" CTA
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "Presets",
                            tint = ResolveAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LOOK PRESETS & SAVED GRADES",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "${presets.count { !it.isBuiltIn }} custom looks • ${presets.count { it.isBuiltIn }} factory calibrated",
                        color = ResolveCyan,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Save Current Look CTA Button
                Button(
                    onClick = onCreatePresetClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ResolveAmber),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("save_current_preset_cta")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF1E1400),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SAVE LOOK",
                        color = Color(0xFF1E1400),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 2. SEARCH BAR & QUICK FILTERS
            // ==========================================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search presets (e.g. Golden, Vintage, Punch)...", fontSize = 11.sp, color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ResolveAmber,
                    unfocusedBorderColor = StudioCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = StudioSurface,
                    unfocusedContainerColor = StudioSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("preset_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategoryFilter == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                            selectedLabelColor = ResolveAmber,
                            containerColor = StudioSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) ResolveAmber else StudioCardBorder
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp).testTag("preset_filter_$category")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 3. PRESET CARDS LIST
            // ==========================================
            if (filteredPresets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No presets matching \"$searchQuery\"" else "No presets in this category",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredPresets.forEach { preset ->
                        PresetCard(
                            preset = preset,
                            onApply = { onApplyPreset(preset) },
                            onDelete = { presetToDelete = preset }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Alert Dialog for Deleting User Preset
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = {
                Text(
                    text = "Delete Preset?",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${preset.name}\"? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(preset.id)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Delete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel", color = TextSecondary, fontSize = 11.sp)
                }
            },
            containerColor = StudioSurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun PresetCard(
    preset: GradePreset,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (preset.isBuiltIn) StudioCardBorder else ResolveAmber.copy(alpha = 0.4f)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preset_card_${preset.name.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header Row: Category Badge, Factory/User badge, Name, Delete button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Type Badge: Built-in vs User
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (preset.isBuiltIn) Color(0xFF1E283A) else Color(0xFF332612))
                            .border(
                                1.dp,
                                if (preset.isBuiltIn) ResolveCyan.copy(alpha = 0.5f) else ResolveAmber.copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (preset.isBuiltIn) "FACTORY" else "USER",
                            color = if (preset.isBuiltIn) ResolveCyan else ResolveAmber,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Category Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1B202C))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = preset.category.uppercase(),
                            color = TextMuted,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = preset.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Delete Button (only for user custom presets)
                if (!preset.isBuiltIn) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("delete_preset_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Preset",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (preset.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Technical Badges summary of what the preset modifies
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // LUT Badge
                PresetParamBadge(
                    label = "LUT",
                    value = preset.settings.activeLut.displayName.take(18)
                )

                // Grain Badge
                if (preset.settings.effects.grain.enabled) {
                    PresetParamBadge(
                        label = "GRAIN",
                        value = "${preset.settings.effects.grain.preset.displayName} (${(preset.settings.effects.grain.strength * 100).toInt()}%)",
                        accentColor = ResolveAmber
                    )
                }

                // Contrast
                if (preset.settings.contrast != 1.0f) {
                    PresetParamBadge(
                        label = "CONTRAST",
                        value = String.format("%.2fx", preset.settings.contrast)
                    )
                }

                // Saturation
                if (preset.settings.saturation != 1.0f) {
                    PresetParamBadge(
                        label = "SAT",
                        value = String.format("%.2fx", preset.settings.saturation)
                    )
                }

                // Temperature
                if (preset.settings.temperature != 0f) {
                    PresetParamBadge(
                        label = "TEMP",
                        value = if (preset.settings.temperature > 0) "+${(preset.settings.temperature * 100).toInt()}" else "${(preset.settings.temperature * 100).toInt()}",
                        accentColor = if (preset.settings.temperature > 0) Color(0xFFF97316) else ResolveCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Apply Look Button
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ResolveAmber.copy(alpha = 0.7f))),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("apply_preset_${preset.name.lowercase().replace(" ", "_")}")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ResolveAmber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "APPLY LOOK TO CLIP",
                    color = ResolveAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun PresetParamBadge(
    label: String,
    value: String,
    accentColor: Color = TextSecondary
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF121620))
            .border(0.5.dp, Color(0xFF283244), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
