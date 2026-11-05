package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.video.ExportAspectRatio
import com.example.video.ExportBitrateQuality
import com.example.video.ExportResolution
import com.example.video.ExportVideoCodec
import com.example.video.VideoExportConfig

@Composable
fun ExportDialog(
    isExporting: Boolean,
    exportProgress: Float,
    statusMessage: String,
    onDismiss: () -> Unit,
    onExportVideo: (VideoExportConfig) -> Unit,
    onExportStill: () -> Unit,
    onExportCubeLut: () -> Unit
) {
    var selectedAspectRatio by remember { mutableStateOf(ExportAspectRatio.REELS_9_16) }
    var selectedResolution by remember { mutableStateOf(ExportResolution.UHD_4K) }
    var selectedQuality by remember { mutableStateOf(ExportBitrateQuality.LOSSLESS_MASTER_4K) }
    var selectedCodec by remember { mutableStateOf(ExportVideoCodec.AVC_H264) }
    var saveToGallery by remember { mutableStateOf(true) }

    val currentConfig = remember(selectedAspectRatio, selectedResolution, selectedQuality, selectedCodec, saveToGallery) {
        VideoExportConfig(
            resolution = selectedResolution,
            aspectRatio = selectedAspectRatio,
            quality = selectedQuality,
            codec = selectedCodec,
            saveToGallery = saveToGallery
        )
    }

    val dimensions = currentConfig.getTargetDimensions()

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        containerColor = StudioSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = ResolveAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXPORT COLOR GRADE", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isExporting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { exportProgress },
                            color = ResolveAmber,
                            trackColor = Color(0xFF283142),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${(exportProgress * 100).toInt()}%",
                            color = ResolveAmber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                } else {
                    // ==========================================
                    // 1. ASPECT RATIO SELECTOR (INSTAGRAM REELS 9:16)
                    // ==========================================
                    Text(
                        text = "DELIVERY ASPECT RATIO",
                        color = ResolveCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportAspectRatio.values().forEach { ratio ->
                            val isSelected = selectedAspectRatio == ratio
                            Surface(
                                onClick = { selectedAspectRatio = ratio },
                                color = if (isSelected) ResolveCyan.copy(alpha = 0.2f) else StudioSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ResolveCyan)) else null,
                                modifier = Modifier.testTag("ratio_${ratio.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ratio.isVertical) Icons.Default.StayCurrentPortrait else Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = if (isSelected) ResolveCyan else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = ratio.shortName,
                                        color = if (isSelected) ResolveCyan else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 2. QUALITY / BITRATE (LOSSLESS 4K 100 MBPS)
                    // ==========================================
                    Text(
                        text = "ENCODING BITRATE & QUALITY",
                        color = ResolveAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportBitrateQuality.values().forEach { quality ->
                            val isSelected = selectedQuality == quality
                            Surface(
                                onClick = { selectedQuality = quality },
                                color = if (isSelected) ResolveAmber.copy(alpha = 0.16f) else StudioSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ResolveAmber)) else null,
                                modifier = Modifier.fillMaxWidth().testTag("quality_${quality.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedQuality = quality },
                                        colors = RadioButtonDefaults.colors(selectedColor = ResolveAmber),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = quality.label,
                                            color = if (isSelected) ResolveAmber else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = quality.description,
                                            color = TextMuted,
                                            fontSize = 8.5.sp,
                                            lineHeight = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 3. RESOLUTION (4K UHD vs 1080p FHD)
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "RESOLUTION",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportResolution.values().forEach { res ->
                                val isSelected = selectedResolution == res
                                Surface(
                                    onClick = { selectedResolution = res },
                                    color = if (isSelected) ResolveGreen.copy(alpha = 0.2f) else StudioSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("res_${res.name.lowercase()}")
                                ) {
                                    Text(
                                        text = if (res == ExportResolution.UHD_4K) "4K UHD" else "1080p",
                                        color = if (isSelected) ResolveGreen else TextMuted,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ==========================================
                    // 4. CODEC SELECTOR (H.264 vs HEVC) & GALLERY SAVE
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CODEC",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportVideoCodec.values().forEach { codec ->
                                val isSelected = selectedCodec == codec
                                Surface(
                                    onClick = { selectedCodec = codec },
                                    color = if (isSelected) ResolveCyan.copy(alpha = 0.2f) else StudioSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("codec_${codec.name.lowercase()}")
                                ) {
                                    Text(
                                        text = codec.shortName,
                                        color = if (isSelected) ResolveCyan else TextMuted,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Save to Phone Gallery",
                                color = TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-save to Movies/ResolveStudio",
                                color = TextMuted,
                                fontSize = 8.5.sp
                            )
                        }
                        Switch(
                            checked = saveToGallery,
                            onCheckedChange = { saveToGallery = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ResolveAmber,
                                checkedTrackColor = ResolveAmber.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("export_save_gallery_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Output Telemetry Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C1018))
                            .border(0.8.dp, Color(0xFF1F293D), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ResolveAmber))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "TARGET OUTPUT SPEC",
                                    color = ResolveAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${dimensions.first}×${dimensions.second} • ${selectedAspectRatio.shortName} • ${selectedQuality.bitrateBps / 1_000_000} Mbps ${selectedCodec.shortName}",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action: Render Video
                    ExportOptionCard(
                        icon = Icons.Default.Videocam,
                        title = "Render Master Video (${selectedAspectRatio.shortName})",
                        subtitle = "High-precision 10-bit color-graded MP4 at ${selectedQuality.bitrateBps / 1_000_000} Mbps",
                        accentColor = ResolveAmber,
                        onClick = { onExportVideo(currentConfig) },
                        testTag = "export_option_video"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Actions: Still Frame & 3D LUT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onExportStill,
                            color = StudioSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_option_still")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = ResolveCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Still (PNG)", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    Text("Uncompressed", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                        }

                        Surface(
                            onClick = onExportCubeLut,
                            color = StudioSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_option_cube")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = ResolveGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(".cube 3D LUT", color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    Text("DaVinci 33³", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_export_dialog")) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    )
}

@Composable
fun ExportOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        color = StudioSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.4f))),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = TextMuted, fontSize = 9.5.sp, lineHeight = 12.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
