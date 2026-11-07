package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TimelineClip
import com.example.ui.theme.*
import com.example.video.SampleScene

/**
 * DaVinci Resolve Color Page Multi-Clip Timeline & Clip Strip.
 * Displays the multi-clip sequence bar with cut markers, per-clip grade badges,
 * and Copy Grade / Paste Grade / Sync All / Add Clip workflow controls.
 */
@Composable
fun MultiClipTimelineStrip(
    clips: List<TimelineClip>,
    activeClipIndex: Int,
    currentTimeMs: Long,
    totalDurationMs: Long,
    hasCopiedGrade: Boolean,
    onSelectClip: (Int) -> Unit,
    onCopyGrade: () -> Unit,
    onPasteGrade: () -> Unit,
    onSyncAllGrades: () -> Unit,
    onAddSampleClip: (SampleScene) -> Unit,
    onImportVideoClip: () -> Unit,
    onRemoveClip: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clips.isEmpty()) return

    var isExpanded by remember(clips.size) { mutableStateOf(clips.size > 1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_clip_timeline_strip")
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E121B))
            .border(1.dp, StudioCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        // =========================================================================
        // 1. TOP BAR: ACTIVE CLIP INDICATOR + COPY / PASTE / SYNC / +CLIP ACTIONS
        // =========================================================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left: Collapse/Expand Toggle + Clip Counter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 3.dp, horizontal = 4.dp)
                    .testTag("toggle_clip_strip_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ViewCarousel,
                    contentDescription = "Clips Strip",
                    tint = ResolveAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val activeNum = (activeClipIndex + 1).coerceAtMost(clips.size.coerceAtLeast(1))
                Text(
                    text = String.format("CLIP %02d/%02d", activeNum, clips.size),
                    color = ResolveAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand Clips",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Right: Copy Grade, Paste Grade, Sync All, + Import Clip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Copy Grade
                Surface(
                    onClick = onCopyGrade,
                    color = Color(0xFF171E2C),
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2B3548))
                    ),
                    modifier = Modifier.height(28.dp).testTag("copy_clip_grade_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Grade", tint = ResolveCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPY", color = ResolveCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // Paste Grade
                Surface(
                    onClick = { if (hasCopiedGrade) onPasteGrade() },
                    color = if (hasCopiedGrade) ResolveAmber.copy(alpha = 0.2f) else Color(0xFF131722),
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (hasCopiedGrade) ResolveAmber else Color(0xFF232B3A)
                        )
                    ),
                    modifier = Modifier.height(28.dp).testTag("paste_clip_grade_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste Grade",
                            tint = if (hasCopiedGrade) ResolveAmber else TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "PASTE",
                            color = if (hasCopiedGrade) ResolveAmber else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Sync Grade to All Clips
                Surface(
                    onClick = onSyncAllGrades,
                    color = Color(0xFF171E2C),
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2B3548))
                    ),
                    modifier = Modifier.height(28.dp).testTag("sync_all_clips_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync All", tint = ResolveGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ALL", color = ResolveGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // + Import Clip Button (Directly opens phone video picker)
                Surface(
                    onClick = onImportVideoClip,
                    color = ResolveAmber.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ResolveAmber)
                    ),
                    modifier = Modifier.height(28.dp).testTag("add_timeline_clip_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Clip", tint = ResolveAmber, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("CLIP", color = ResolveAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // =========================================================================
        // 2. PROPORTIONAL MULTI-CLIP SEQUENCE TIMELINE BAR WITH CUT MARKERS
        // =========================================================================
        val safeTotalMs = totalDurationMs.coerceAtLeast(1L).toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF161C28))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                clips.forEachIndexed { idx, clip ->
                    val weight = (clip.durationMs.toFloat() / safeTotalMs).coerceAtLeast(0.05f)
                    val isSelected = idx == activeClipIndex
                    val hasGrade = clip.settings.hasCustomGrade
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(
                                when {
                                    isSelected -> ResolveAmber.copy(alpha = 0.30f)
                                    hasGrade -> ResolveCyan.copy(alpha = 0.16f)
                                    else -> Color(0xFF1B2232)
                                }
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (isSelected) ResolveAmber else Color(0xFF0A0D13)
                            )
                            .clickable { onSelectClip(idx) }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = String.format("%02d %s", idx + 1, clip.title),
                            color = if (isSelected) ResolveAmber else TextSecondary,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 3. EXPANDABLE DAVINCI RESOLVE CLIP THUMBNAIL STRIP
        // =========================================================================
        AnimatedVisibility(visible = isExpanded) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(clips, key = { _, clip -> clip.id }) { idx, clip ->
                        val isSelected = idx == activeClipIndex
                        val hasGrade = clip.settings.hasCustomGrade

                        Box(
                            modifier = Modifier
                                .width(116.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isSelected) Color(0xFF1F2738) else Color(0xFF141822))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ResolveAmber else StudioCardBorder,
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .clickable { onSelectClip(idx) }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                .testTag("timeline_clip_card_$idx")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%02d", idx + 1),
                                            color = if (isSelected) ResolveAmber else TextMuted,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        // Graded status LED indicator
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (hasGrade) ResolveGreen else Color(0xFF4B5563))
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%.1fs", clip.durationMs / 1000f),
                                            color = TextMuted,
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (clips.size > 1) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Clip",
                                                tint = TextMuted,
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .clickable { onRemoveClip(idx) }
                                                    .testTag("remove_clip_btn_$idx")
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = clip.title,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = clip.settings.activeLut.displayName,
                                    color = if (hasGrade) ResolveCyan else TextMuted,
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
