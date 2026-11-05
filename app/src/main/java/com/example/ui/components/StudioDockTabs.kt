package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class StudioToolTab(val title: String, val icon: ImageVector) {
    WHEELS("Primaries", Icons.Default.Adjust),
    CURVES("Curves", Icons.Default.Timeline),
    CST("CST Node", Icons.Default.AccountTree),
    LUTS("LUTs", Icons.Default.Palette),
    PRESETS("Looks", Icons.Default.Bookmarks),
    MASK("Window", Icons.Default.Layers),
    EFFECTS("FX & Film", Icons.Default.AutoAwesome)
}

@Composable
fun StudioDockTabs(
    activeTab: StudioToolTab,
    onTabSelected: (StudioToolTab) -> Unit,
    showScopes: Boolean,
    onToggleScopes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurface)
            .padding(4.dp)
            .testTag("studio_dock_tabs")
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudioToolTab.values().forEach { tab ->
                val isSelected = activeTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ResolveAmber.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("dock_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) ResolveAmber else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) ResolveAmber else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Dedicated Scopes Toggle Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (showScopes) ResolveCyan.copy(alpha = 0.2f) else Color(0xFF1B222F))
                .clickable { onToggleScopes() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("toggle_scopes_dock_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Scopes",
                    tint = if (showScopes) ResolveCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scopes",
                    color = if (showScopes) ResolveCyan else TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = if (showScopes) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
