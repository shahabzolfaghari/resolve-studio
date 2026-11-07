package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

/**
 * Professional DaVinci Resolve Studio Color Space Transform (CST) Node Panel.
 * Represents the final transform node in the color grading pipeline, mapping wide-gamut
 * working spaces (DaVinci Wide Gamut, Samsung S24 Log / BT.2020) to delivery spaces (Rec.709, DCI-P3).
 */
@Composable
fun CstNodePanel(
    cst: CstNodeSettings,
    onCstChange: (CstNodeSettings) -> Unit,
    onNavigateToTab: (StudioToolTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cst_node_panel")
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
            // 1. NODE GRAPH VISUALIZER STRIP
            // ==========================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "CST Node",
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NODE GRAPH • COLOR SPACE TRANSFORM (CST)",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Node Enable / Bypass Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (cst.enabled) "ACTIVE" else "BYPASSED",
                        color = if (cst.enabled) ResolveGreen else TextMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = cst.enabled,
                        onCheckedChange = { onCstChange(cst.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ResolveGreen,
                            checkedTrackColor = Color(0xFF1B3D2F),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF262E3D)
                        ),
                        modifier = Modifier
                            .scale(0.8f)
                            .testTag("cst_node_bypass_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Interactive Node Chain Flow (Tap any node to jump to its grading tool)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F131C))
                    .border(0.5.dp, Color(0xFF242E40), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NodeBadge(
                    label = "01 S24 LOG",
                    isActive = true,
                    onClick = { onNavigateToTab(StudioToolTab.EFFECTS) }
                )
                NodeConnector()
                NodeBadge(
                    label = "02 WHEELS",
                    isActive = true,
                    onClick = { onNavigateToTab(StudioToolTab.WHEELS) }
                )
                NodeConnector()
                NodeBadge(
                    label = "03 CURVES",
                    isActive = true,
                    onClick = { onNavigateToTab(StudioToolTab.CURVES) }
                )
                NodeConnector()
                NodeBadge(
                    label = "04 WINDOW",
                    isActive = true,
                    onClick = { onNavigateToTab(StudioToolTab.MASK) }
                )
                NodeConnector()
                NodeBadge(
                    label = "05 CST OUT",
                    isActive = cst.enabled,
                    isCurrent = true,
                    accentColor = if (cst.enabled) ResolveAmber else TextMuted,
                    onClick = { onCstChange(cst.copy(enabled = !cst.enabled)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 2. QUICK CST PRESETS
            // ==========================================
            Text(
                text = "STUDIO CST TEMPLATES",
                color = TextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CstTemplateChip(
                    title = "DaVinci Wide Gamut → Rec.709",
                    subtitle = "Resolve Intermediate Standard",
                    isSelected = cst.inputColorSpace == InputColorSpace.DAVINCI_WIDE_GAMUT && cst.outputColorSpace == OutputColorSpace.REC709,
                    onClick = {
                        onCstChange(
                            cst.copy(
                                enabled = true,
                                inputColorSpace = InputColorSpace.DAVINCI_WIDE_GAMUT,
                                inputGamma = InputGamma.DAVINCI_INTERMEDIATE,
                                outputColorSpace = OutputColorSpace.REC709,
                                outputGamma = OutputGamma.GAMMA_2_4,
                                toneMapping = ToneMappingMethod.DAVINCI
                            )
                        )
                    }
                )

                CstTemplateChip(
                    title = "Samsung S24 Log → Rec.709",
                    subtitle = "10-bit BT.2020 Broadcast",
                    isSelected = cst.inputColorSpace == InputColorSpace.SAMSUNG_S24_GAMUT && cst.outputColorSpace == OutputColorSpace.REC709,
                    onClick = {
                        onCstChange(
                            cst.copy(
                                enabled = true,
                                inputColorSpace = InputColorSpace.SAMSUNG_S24_GAMUT,
                                inputGamma = InputGamma.SAMSUNG_LOG,
                                outputColorSpace = OutputColorSpace.REC709,
                                outputGamma = OutputGamma.GAMMA_2_4,
                                toneMapping = ToneMappingMethod.DAVINCI
                            )
                        )
                    }
                )

                CstTemplateChip(
                    title = "Samsung S24 Log → DCI-P3",
                    subtitle = "Theatrical Wide Color",
                    isSelected = cst.outputColorSpace == OutputColorSpace.DCI_P3,
                    onClick = {
                        onCstChange(
                            cst.copy(
                                enabled = true,
                                inputColorSpace = InputColorSpace.SAMSUNG_S24_GAMUT,
                                inputGamma = InputGamma.SAMSUNG_LOG,
                                outputColorSpace = OutputColorSpace.DCI_P3,
                                outputGamma = OutputGamma.GAMMA_2_4,
                                toneMapping = ToneMappingMethod.DAVINCI
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = StudioCardBorder, modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // 3. INPUT / OUTPUT COLOR SPACE CONFIG
            // ==========================================
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Input Color Space
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INPUT COLOR SPACE",
                        color = ResolveCyan,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    InputColorSpace.values().forEach { space ->
                        val isSel = cst.inputColorSpace == space
                        SelectablePill(
                            label = space.displayName,
                            isSelected = isSel,
                            accentColor = ResolveCyan,
                            onClick = { onCstChange(cst.copy(inputColorSpace = space)) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                // Output Color Space
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OUTPUT COLOR SPACE",
                        color = ResolveAmber,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutputColorSpace.values().forEach { space ->
                        val isSel = cst.outputColorSpace == space
                        SelectablePill(
                            label = space.displayName,
                            isSelected = isSel,
                            accentColor = ResolveAmber,
                            onClick = { onCstChange(cst.copy(outputColorSpace = space)) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 4. INPUT / OUTPUT GAMMA CONFIG
            // ==========================================
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Input Gamma
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INPUT GAMMA",
                        color = ResolveCyan,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    InputGamma.values().forEach { gamma ->
                        val isSel = cst.inputGamma == gamma
                        SelectablePill(
                            label = gamma.displayName,
                            isSelected = isSel,
                            accentColor = ResolveCyan,
                            onClick = { onCstChange(cst.copy(inputGamma = gamma)) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }

                // Output Gamma
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OUTPUT GAMMA",
                        color = ResolveAmber,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutputGamma.values().forEach { gamma ->
                        val isSel = cst.outputGamma == gamma
                        SelectablePill(
                            label = gamma.displayName,
                            isSelected = isSel,
                            accentColor = ResolveAmber,
                            onClick = { onCstChange(cst.copy(outputGamma = gamma)) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 5. TONE MAPPING & GAMUT COMPRESSION
            // ==========================================
            Text(
                text = "TONE MAPPING & HIGHLIGHT ROLL-OFF",
                color = TextPrimary,
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
                ToneMappingMethod.values().forEach { method ->
                    val isSel = cst.toneMapping == method
                    FilterChip(
                        selected = isSel,
                        onClick = { onCstChange(cst.copy(toneMapping = method)) },
                        label = { Text(method.displayName, fontSize = 9.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ResolveAmber.copy(alpha = 0.25f),
                            selectedLabelColor = ResolveAmber,
                            containerColor = Color(0xFF141924),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSel,
                            borderColor = if (isSel) ResolveAmber else Color(0xFF263246)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            if (cst.toneMapping != ToneMappingMethod.OFF) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Knee Shoulder",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format("%.2f", cst.kneeThreshold),
                            color = ResolveAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = cst.kneeThreshold,
                        onValueChange = { onCstChange(cst.copy(kneeThreshold = it)) },
                        valueRange = 0.5f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = ResolveAmber,
                            activeTrackColor = ResolveAmber,
                            inactiveTrackColor = Color(0xFF222B3D)
                        ),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Gamut Mapping (Saturation Compression)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF121622))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = "Gamut Saturation Compression",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Smoothly desaturates out-of-gamut colors to prevent harsh broadcast clipping",
                        color = TextMuted,
                        fontSize = 8.sp
                    )
                }

                Switch(
                    checked = cst.gamutMapping == GamutMappingMethod.SATURATION_COMPRESSION,
                    onCheckedChange = {
                        onCstChange(
                            cst.copy(
                                gamutMapping = if (it) GamutMappingMethod.SATURATION_COMPRESSION else GamutMappingMethod.NONE
                            )
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ResolveAmber,
                        checkedTrackColor = Color(0xFF3E2D0F)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

@Composable
private fun NodeBadge(
    label: String,
    isActive: Boolean,
    isCurrent: Boolean = false,
    accentColor: Color = ResolveCyan,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCurrent) Color(0xFF232D40) else Color(0xFF141924))
            .border(
                1.dp,
                if (isCurrent) accentColor else if (isActive) Color(0xFF2D3950) else Color(0xFF1E2432),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) ResolveGreen else TextMuted)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (isCurrent) accentColor else if (isActive) TextPrimary else TextMuted,
                fontSize = 8.5.sp,
                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun NodeConnector() {
    Icon(
        imageVector = Icons.Default.ArrowForward,
        contentDescription = null,
        tint = Color(0xFF374660),
        modifier = Modifier.size(10.dp)
    )
}

@Composable
private fun CstTemplateChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF243046) else Color(0xFF131722),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) ResolveAmber else Color(0xFF253042))
        ),
        modifier = Modifier.height(42.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) ResolveAmber else TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SelectablePill(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF131722))
            .border(
                0.8.dp,
                if (isSelected) accentColor else Color(0xFF222B3A),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                color = if (isSelected) accentColor else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
