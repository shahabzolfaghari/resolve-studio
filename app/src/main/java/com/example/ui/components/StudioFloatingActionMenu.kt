package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * A DaVinci Resolve-inspired Floating Action Menu (Speed Dial)
 * used when the studio dock is collapsed on smaller screens to keep the video preview full-width.
 */
@Composable
fun StudioFloatingActionMenu(
    activeTab: StudioToolTab,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectTab: (StudioToolTab) -> Unit,
    onOpenToolSheet: () -> Unit,
    onExpandDock: () -> Unit,
    onBypassToggle: () -> Unit,
    isBypassActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("studio_floating_action_menu")
            .padding(end = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==========================================
            // SPEED DIAL POP-OUT ACTION BUTTONS
            // ==========================================
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom, animationSpec = spring()),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = spring())
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // 1. Expand to Full Static Dock Action
                    FloatingMenuItem(
                        label = "Expand Dock",
                        icon = Icons.Default.OpenInFull,
                        accentColor = ResolveCyan,
                        onClick = onExpandDock,
                        testTag = "speed_dial_expand_dock"
                    )

                    // 2. A/B Bypass Quick Toggle Action
                    FloatingMenuItem(
                        label = if (isBypassActive) "Enable Grade" else "Bypass Grade",
                        icon = if (isBypassActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        accentColor = if (isBypassActive) Color(0xFFEF4444) else ResolveGreen,
                        onClick = onBypassToggle,
                        testTag = "speed_dial_bypass_toggle"
                    )

                    HorizontalDivider(
                        color = Color(0xFF263248),
                        modifier = Modifier.width(130.dp).padding(vertical = 2.dp)
                    )

                    // 3. Tool Tabs (Wheels, Curves, LUTs, Mask, Effects)
                    StudioToolTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        FloatingMenuItem(
                            label = tab.title,
                            icon = tab.icon,
                            accentColor = if (isSelected) ResolveAmber else TextSecondary,
                            isSelected = isSelected,
                            onClick = {
                                onSelectTab(tab)
                                onOpenToolSheet()
                            },
                            testTag = "speed_dial_tab_${tab.name.lowercase()}"
                        )
                    }
                }
            }

            // ==========================================
            // MAIN FLOATING HUD PILL / FAB
            // ==========================================
            Surface(
                color = Color(0xFF141822),
                shape = RoundedCornerShape(24.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ResolveAmber.copy(alpha = 0.65f))
                ),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .height(48.dp)
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .testTag("floating_dock_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    // Tap to Open Tool Grading Sheet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenToolSheet() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("open_floating_tool_sheet_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ResolveAmber.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = activeTab.icon,
                                contentDescription = activeTab.title,
                                tint = ResolveAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "STUDIO TOOL",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = activeTab.title.uppercase(),
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Adjust",
                            tint = ResolveAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    VerticalDivider(
                        color = Color(0xFF283244),
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 4.dp)
                    )

                    // Expand / Collapse Speed Dial Menu Button
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isExpanded) ResolveAmber else Color(0xFF202736))
                            .testTag("toggle_speed_dial_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.MenuOpen,
                            contentDescription = "Studio Menu",
                            tint = if (isExpanded) Color(0xFF1E1400) else ResolveAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMenuItem(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    isSelected: Boolean = false
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF222B3D) else Color(0xFF141924),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) ResolveAmber else Color(0xFF2A3448)
            )
        ),
        shadowElevation = 6.dp,
        modifier = Modifier
            .height(38.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) ResolveAmber else TextPrimary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
