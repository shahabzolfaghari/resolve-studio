package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
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

/**
 * Modal Bottom Sheet presenting the studio dock tools when opened from the Floating Action Menu.
 * Keeps the video preview visible in the background and provides quick grading access on smaller screens.
 */
import com.example.model.GradePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioFloatingToolSheet(
    activeTab: StudioToolTab,
    onTabSelected: (StudioToolTab) -> Unit,
    settings: ColorGradeSettings,
    onSettingsChange: (description: String, transform: (ColorGradeSettings) -> ColorGradeSettings) -> Unit,
    previewBitmap: Bitmap?,
    onImportCubeClick: () -> Unit,
    onCreateCustomLutClick: () -> Unit,
    onDeleteCustomLut: (String) -> Unit,
    presets: List<GradePreset> = emptyList(),
    onApplyPreset: (GradePreset) -> Unit = {},
    onCreatePresetClick: () -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    isMaskTracking: Boolean = false,
    maskTrackingDirection: Int = 1,
    onTrackMaskForward: () -> Unit = {},
    onTrackMaskBackward: () -> Unit = {},
    onTrackMaskStep: () -> Unit = {},
    onClearMaskTrack: () -> Unit = {},
    onExpandToStaticDock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                BottomSheetDefaults.DragHandle(color = Color(0xFF4A5568))
            }
        },
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier.testTag("studio_floating_tool_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            // Sheet Top Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = activeTab.icon,
                        contentDescription = null,
                        tint = ResolveAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STUDIO DOCK • ${activeTab.title.uppercase()}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Expand to Static Dock Button
                    IconButton(
                        onClick = onExpandToStaticDock,
                        modifier = Modifier.size(28.dp).testTag("sheet_expand_to_dock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Pin Dock to Screen",
                            tint = ResolveCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close Sheet Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("close_tool_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Embedded StudioDockContainer hosting all tools and scopes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                StudioDockContainer(
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    previewBitmap = previewBitmap,
                    onImportCubeClick = onImportCubeClick,
                    onCreateCustomLutClick = onCreateCustomLutClick,
                    onDeleteCustomLut = onDeleteCustomLut,
                    presets = presets,
                    onApplyPreset = onApplyPreset,
                    onCreatePresetClick = onCreatePresetClick,
                    onDeletePreset = onDeletePreset,
                    isMaskTracking = isMaskTracking,
                    maskTrackingDirection = maskTrackingDirection,
                    onTrackMaskForward = onTrackMaskForward,
                    onTrackMaskBackward = onTrackMaskBackward,
                    onTrackMaskStep = onTrackMaskStep,
                    onClearMaskTrack = onClearMaskTrack,
                    onCollapseToFloating = onDismiss,
                    showCollapseButton = true,
                    showInternalTabBar = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
