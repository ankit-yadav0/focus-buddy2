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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Park
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ControlDeckTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Matches Design 6's exact 4 bottom navigation tabs: Home, Blocks, Stats, Forest
// (Timer and Schedule Manager are also included for route matching and reachable from Home).
val controlDeckTabs = listOf(
    ControlDeckTab("home", "Home", Icons.Default.Home),
    ControlDeckTab("blocks_progress", "Blocks", Icons.Default.GridView),
    ControlDeckTab("insights", "Stats", Icons.Default.BarChart),
    ControlDeckTab("forest_gallery", "Forest", Icons.Default.Park)
)

/**
 * Design 6 ("Cozy Lofi Night-Study") bottom navigation bar:
 * Deep warm espresso bar (#211822) with a warm bronze pill around the active icon
 * and golden-amber (#FFB74D) active icon/label.
 */
@Composable
fun ControlDeckBottomNav(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val amberAccent = Color(0xFFFFB74D)
    val creamInactive = Color(0xFFEAE0D5)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF211822))
            .border(width = 0.8.dp, color = Color(0x26FFB74D))
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
                    activeColor = amberAccent,
                    inactiveColor = creamInactive,
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
                .background(if (isActive) Color(0xFF563B25) else Color.Transparent)
                .padding(horizontal = 22.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (isActive) activeColor else inactiveColor.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = if (isActive) activeColor else inactiveColor.copy(alpha = 0.80f),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}
