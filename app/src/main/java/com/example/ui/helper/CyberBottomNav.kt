package com.example.ui.helper

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpaceCyan
import com.example.ui.theme.SpaceCyanDim
import com.example.ui.theme.SpaceSurface
import com.example.ui.theme.SpaceTextSecondary
import com.example.ui.theme.SpaceVoidBackground

enum class CyberNavTab(val route: String, val label: String) {
    HOME("home", "Home"),
    PLANNER("study_plan_dashboard", "Planner"),
    BLOCKLIST("app_selection", "Blocklist"),
    STATS("stats", "Stats")
}

/**
 * Bottom navigation bar matching the reference design: four flanking tabs around a raised
 * circular "radar" button that starts a focus session. Every route this bar links to already
 * exists in the nav graph - this only changes how they're reached, nothing is removed.
 */
@Composable
fun CyberBottomNav(
    currentRoute: String?,
    onTabSelected: (CyberNavTab) -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceVoidBackground.copy(alpha = 0.97f))
                .padding(top = 10.dp, bottom = 14.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavTabItem(CyberNavTab.HOME, currentRoute, onTabSelected)
            NavTabItem(CyberNavTab.PLANNER, currentRoute, onTabSelected)
            Box(modifier = Modifier.size(52.dp)) // spacer under the raised center button
            NavTabItem(CyberNavTab.BLOCKLIST, currentRoute, onTabSelected)
            NavTabItem(CyberNavTab.STATS, currentRoute, onTabSelected)
        }

        RadarStartButton(
            onClick = onStartSession,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
        )
    }
}

@Composable
private fun RowScope.NavTabItem(
    tab: CyberNavTab,
    currentRoute: String?,
    onTabSelected: (CyberNavTab) -> Unit
) {
    val isSelected = currentRoute == tab.route
    val tint = if (isSelected) SpaceCyan else SpaceTextSecondary
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onTabSelected(tab) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = when (tab) {
                CyberNavTab.HOME -> Icons.Filled.Home
                CyberNavTab.PLANNER -> Icons.Filled.CalendarMonth
                CyberNavTab.BLOCKLIST -> Icons.Filled.Shield
                CyberNavTab.STATS -> Icons.Filled.BarChart
            },
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RadarStartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "radar_sweep")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(SpaceSurface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(52.dp)) {
            drawCircle(color = SpaceCyanDim, radius = size.minDimension / 2f, style = Stroke(width = 3f))
            drawCircle(color = SpaceCyan, radius = size.minDimension / 2f - 6f, style = Stroke(width = 2f))
            rotate(degrees = sweep) {
                drawArc(
                    color = SpaceCyan.copy(alpha = 0.35f),
                    startAngle = 0f,
                    sweepAngle = 40f,
                    useCenter = true,
                    topLeft = Offset(3f, 3f),
                    size = androidx.compose.ui.geometry.Size(size.width - 6f, size.height - 6f)
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Start focus session",
            tint = SpaceCyan,
            modifier = Modifier.size(22.dp)
        )
    }
}
