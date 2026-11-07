package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet

data class ControlDeckTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Matches the Neon Focus Buddy UI Theme Board bottom navigation bar while keeping
// the core route IDs intact for seamless navigation and deep-linking:
// Home ("home"), Stats ("insights"), Planner ("study_planner"), Blocks/More ("blocks_progress").
val controlDeckTabs = listOf(
    ControlDeckTab("home", "Home", Icons.Default.Home),
    ControlDeckTab("insights", "Stats", Icons.Default.BarChart),
    ControlDeckTab("study_planner", "Planner", Icons.Default.CalendarMonth),
    ControlDeckTab("blocks_progress", "More", Icons.Default.MoreHoriz)
)

/**
 * Neon Black Theme bottom navigation bar:
 * Deep #08080F / #12121C surface with a glowing cyan/magenta top hairline and
 * neon cyan (#00E5FF) active icon/label.
 */
@Composable
fun ControlDeckBottomNav(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveColor = Color(0xFF8E95B0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NeonSurface.copy(alpha = 0.96f),
                        NeonBlack
                    )
                )
            )
            .border(
                width = 0.9.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.45f),
                        NeonViolet.copy(alpha = 0.30f),
                        NeonMagenta.copy(alpha = 0.45f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            controlDeckTabs.forEach { tab ->
                val isActive = currentRoute == tab.route
                ControlDeckNavItem(
                    tab = tab,
                    isActive = isActive,
                    activeColor = NeonCyan,
                    inactiveColor = inactiveColor,
                    onClick = { onTabSelected(tab.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlDeckNavItem(
    tab: ControlDeckTab,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (isActive) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.22f),
                                NeonViolet.copy(alpha = 0.22f),
                                NeonMagenta.copy(alpha = 0.18f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .border(
                    width = if (isActive) 1.dp else 0.dp,
                    brush = if (isActive) {
                        Brush.horizontalGradient(
                            listOf(NeonCyan.copy(alpha = 0.65f), NeonMagenta.copy(alpha = 0.55f))
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 20.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (isActive) activeColor else inactiveColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = tab.label,
            color = if (isActive) activeColor else inactiveColor,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 11.5.sp
        )
    }
}
