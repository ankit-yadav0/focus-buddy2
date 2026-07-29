package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily

data class ControlDeckTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val controlDeckTabs = listOf(
    ControlDeckTab("home", "HOME", Icons.Default.Home),
    ControlDeckTab("timer", "FOCUS", Icons.Default.Timer),
    ControlDeckTab("schedule_manager", "STRICT", Icons.Default.Shield),
    ControlDeckTab("study_planner", "PLAN", Icons.Default.MenuBook)
)

/**
 * "Control deck" bottom navigation - the instrument-panel motif from the
 * home dial repeated here: tick-mark dividers between icons, and a bold
 * pill + glowing arc under the active tab so it reads as clearly "engaged"
 * rather than a subtle tint change.
 */
@Composable
fun ControlDeckBottomNav(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val phosphor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF012171F))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        controlDeckTabs.forEachIndexed { index, tab ->
            val isActive = currentRoute == tab.route
            ControlDeckNavItem(
                tab = tab,
                isActive = isActive,
                activeColor = phosphor,
                onClick = { onTabSelected(tab.route) }
            )
            if (index != controlDeckTabs.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }
    }
}

@Composable
private fun ControlDeckNavItem(
    tab: ControlDeckTab,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val glow by rememberInfiniteTransition(label = "nav_glow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nav_glow_alpha"
    )

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(
                if (isActive) Modifier.background(activeColor.copy(alpha = 0.16f)) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (isActive) activeColor else Color.White.copy(alpha = 0.38f),
            modifier = Modifier.size(if (isActive) 24.dp else 21.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(3.dp))
        if (isActive) {
            Text(
                text = tab.label,
                color = activeColor,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 22.dp else 0.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = glow) else Color.Transparent)
        )
    }
}
