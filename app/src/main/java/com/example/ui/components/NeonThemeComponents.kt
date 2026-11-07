package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet

/**
 * Reusable UI building blocks for the "Neon Black Theme" (Clean • Modern • Focused)
 * matching `Neon Focus Buddy UI Theme Board.png`:
 *   • #00E5FF (Neon Cyan)
 *   • #FF00E6 (Neon Magenta)
 *   • #7B61FF (Neon Violet)
 *   • #08080F (Neon Black Background)
 *   • #1A1A24 (Dark Slate/Navy Card Surface)
 */

val NeonHorizontalBrush = Brush.horizontalGradient(
    colors = listOf(NeonCyan, NeonViolet, NeonMagenta)
)

val NeonCardBorderBrush = Brush.linearGradient(
    colors = listOf(
        NeonCyan.copy(alpha = 0.55f),
        NeonViolet.copy(alpha = 0.35f),
        NeonMagenta.copy(alpha = 0.55f)
    )
)

val NeonSubtleBorderBrush = Brush.linearGradient(
    colors = listOf(
        NeonCyan.copy(alpha = 0.32f),
        NeonViolet.copy(alpha = 0.22f),
        NeonMagenta.copy(alpha = 0.32f)
    )
)

/**
 * Full-screen Neon Black background (#08080F) with diagonal laser light streaks
 * (#FF00E6 magenta, #00E5FF cyan, #7B61FF violet) and subtle starlight particles,
 * matching the Splash Screen, Blocked Screen, Strict Mode Screen, and Home Screen.
 */
@Composable
fun NeonLaserBackground(
    modifier: Modifier = Modifier,
    bgAlpha: Float = 1.0f,
    showDiagonalLasers: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeonBlack.copy(alpha = bgAlpha))
    ) {
        if (showDiagonalLasers) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Top-left ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonMagenta.copy(alpha = 0.14f * bgAlpha),
                            NeonViolet.copy(alpha = 0.06f * bgAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.15f, h * 0.12f),
                        radius = w * 0.75f
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * 0.15f, h * 0.12f)
                )

                // Bottom-right ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.14f * bgAlpha),
                            NeonViolet.copy(alpha = 0.06f * bgAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.85f, h * 0.88f),
                        radius = w * 0.75f
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * 0.85f, h * 0.88f)
                )

                // Top-left diagonal neon laser streaks (Magenta & Cyan)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonMagenta.copy(alpha = 0.55f * bgAlpha),
                            NeonCyan.copy(alpha = 0.35f * bgAlpha),
                            Color.Transparent
                        ),
                        start = Offset(-w * 0.05f, h * 0.22f),
                        end = Offset(w * 0.52f, -h * 0.02f)
                    ),
                    start = Offset(-w * 0.05f, h * 0.22f),
                    end = Offset(w * 0.52f, -h * 0.02f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonCyan.copy(alpha = 0.45f * bgAlpha),
                            NeonViolet.copy(alpha = 0.30f * bgAlpha),
                            Color.Transparent
                        ),
                        start = Offset(-w * 0.02f, h * 0.28f),
                        end = Offset(w * 0.62f, 0f)
                    ),
                    start = Offset(-w * 0.02f, h * 0.28f),
                    end = Offset(w * 0.62f, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Bottom-left to bottom-right diagonal neon laser streaks
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonMagenta.copy(alpha = 0.50f * bgAlpha),
                            NeonViolet.copy(alpha = 0.35f * bgAlpha),
                            Color.Transparent
                        ),
                        start = Offset(w * 0.08f, h * 0.96f),
                        end = Offset(w * 0.68f, h * 0.72f)
                    ),
                    start = Offset(w * 0.08f, h * 0.96f),
                    end = Offset(w * 0.68f, h * 0.72f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonCyan.copy(alpha = 0.50f * bgAlpha),
                            Color.Transparent
                        ),
                        start = Offset(w * 0.28f, h * 0.99f),
                        end = Offset(w * 0.92f, h * 0.74f)
                    ),
                    start = Offset(w * 0.28f, h * 0.99f),
                    end = Offset(w * 0.92f, h * 0.74f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        content()
    }
}

/**
 * Dual-tone "Focus Buddy" title matching the Splash & Home screens:
 * "Focus " in bright Cyan/White and "Buddy" in glowing Neon Magenta (#FF00E6).
 */
@Composable
fun FocusBuddyNeonTitle(
    fontSize: TextUnit = 26.sp,
    focusInCyan: Boolean = true,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = if (focusInCyan) NeonCyan else Color.White,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(
                        color = NeonCyan.copy(alpha = 0.55f),
                        offset = Offset(0f, 0f),
                        blurRadius = 18f
                    )
                )
            ) {
                append("Focus ")
            }
            withStyle(
                SpanStyle(
                    color = NeonMagenta,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(
                        color = NeonMagenta.copy(alpha = 0.65f),
                        offset = Offset(0f, 0f),
                        blurRadius = 20f
                    )
                )
            ) {
                append("Buddy")
            }
        },
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/**
 * Target + Lightning Bolt logo from the Splash Screen and Settings header.
 */
@Composable
fun NeonTargetLightningLogo(
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outerR = this.size.minDimension * 0.42f

            // Ambient outer radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.28f),
                        NeonMagenta.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = outerR * 1.35f
                ),
                radius = outerR * 1.35f,
                center = Offset(cx, cy)
            )

            // Outer thick neon sweep gradient ring
            val outerStroke = (this.size.minDimension * 0.11f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, NeonViolet, NeonMagenta, NeonCyan),
                    center = Offset(cx, cy)
                ),
                startAngle = -35f,
                sweepAngle = 310f,
                useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2f, outerR * 2f),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )

            // Inner ring
            val innerR = outerR * 0.56f
            val innerStroke = (this.size.minDimension * 0.08f)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(NeonCyan, NeonMagenta)
                ),
                radius = innerR,
                center = Offset(cx, cy),
                style = Stroke(width = innerStroke)
            )

            // Bullseye center dot
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(NeonCyan, NeonMagenta)
                ),
                radius = outerR * 0.20f,
                center = Offset(cx, cy)
            )

            // Diagonal neon lightning bolt in top-right quadrant
            val boltPath = Path().apply {
                moveTo(cx + outerR * 0.55f, cy - outerR * 1.05f)
                lineTo(cx + outerR * 0.08f, cy - outerR * 0.25f)
                lineTo(cx + outerR * 0.38f, cy - outerR * 0.25f)
                lineTo(cx + outerR * 0.02f, cy + outerR * 0.35f)
                lineTo(cx + outerR * 0.72f, cy - outerR * 0.45f)
                lineTo(cx + outerR * 0.40f, cy - outerR * 0.45f)
                close()
            }
            drawPath(
                path = boltPath,
                brush = Brush.linearGradient(
                    colors = listOf(NeonMagenta, NeonCyan)
                )
            )
        }
    }
}

/**
 * Large glowing circular Lock emblem shown on the Blocked Screen & Strict Mode Screen.
 */
@Composable
fun NeonCircularLockEmblem(
    size: Dp = 148.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_lock_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_glow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = this.size.minDimension * 0.40f

            // Radial neon aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.30f * pulse),
                        NeonCyan.copy(alpha = 0.14f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.38f
                ),
                radius = r * 1.38f,
                center = Offset(cx, cy)
            )

            // Dark inner core
            drawCircle(
                color = NeonBlack,
                radius = r * 0.92f,
                center = Offset(cx, cy)
            )

            // Outer soft glow ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, NeonViolet, NeonMagenta, NeonCyan),
                    center = Offset(cx, cy)
                ),
                radius = r,
                center = Offset(cx, cy),
                alpha = 0.35f * pulse,
                style = Stroke(width = 10.dp.toPx())
            )

            // Main crisp neon gradient ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, NeonViolet, NeonMagenta, NeonCyan),
                    center = Offset(cx, cy)
                ),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner subtle ring
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(NeonMagenta.copy(alpha = 0.45f), NeonCyan.copy(alpha = 0.45f))
                ),
                radius = r * 0.76f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            tint = NeonMagenta,
            modifier = Modifier.size(size * 0.36f)
        )
    }
}

/**
 * Primary Button from the Neon Black Theme board:
 * Full-width or custom pill button with horizontal #00E5FF -> #7B61FF -> #FF00E6 gradient fill
 * and glowing neon border.
 */
@Composable
fun NeonPrimaryGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    fontSize: TextUnit = 16.sp,
    leadingIcon: ImageVector? = null
) {
    val brush = if (enabled) {
        NeonHorizontalBrush
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .border(
                width = 1.2.dp,
                brush = if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f),
                            NeonCyan.copy(alpha = 0.65f),
                            NeonMagenta.copy(alpha = 0.65f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))
                    )
                },
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    shadow = if (enabled) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    } else null
                )
            )
        }
    }
}

/**
 * Secondary Button from the Neon Black Theme board:
 * Dark #1A1A24 / #08080F pill with a glowing #00E5FF -> #7B61FF -> #FF00E6 gradient border
 * (used for "Go Back" on Blocked Screen, "> Pause" on Home Screen, "+ Add Test", etc.).
 */
@Composable
fun NeonOutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fontSize: TextUnit = 15.sp,
    leadingIcon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(NeonSurface.copy(alpha = 0.85f))
            .border(
                width = 1.6.dp,
                brush = if (enabled) {
                    NeonHorizontalBrush
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f))
                    )
                },
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) NeonCyan else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Horizontal Neon Gradient Progress Bar (#00E5FF -> #7B61FF -> #FF00E6) with glowing tip.
 */
@Composable
fun NeonGradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2f

        // Track
        drawRoundRect(
            color = Color(0xFF252536),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r)
        )

        if (clamped > 0f) {
            val fillW = (w * clamped).coerceAtLeast(h)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(NeonCyan, NeonViolet, NeonMagenta),
                    startX = 0f,
                    endX = fillW
                ),
                size = Size(fillW, h),
                cornerRadius = CornerRadius(r, r)
            )
        }
    }
}

/**
 * Neon App Icon Squircle Badge matching the "App Icons (Style)" section of the theme board.
 */
@Composable
fun NeonSquircleIconBadge(
    icon: ImageVector,
    tint: Color = NeonCyan,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(NeonSurface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.65f),
                        NeonViolet.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/**
 * Footer quote from the Neon Black Theme spec card:
 * "Discipline Today / Freedom Tomorrow"
 */
@Composable
fun NeonDisciplineFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Discipline Today",
            color = NeonCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(color = NeonCyan.copy(alpha = 0.45f), blurRadius = 10f)
            )
        )
        Text(
            text = "Freedom Tomorrow",
            color = NeonMagenta,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(color = NeonMagenta.copy(alpha = 0.45f), blurRadius = 10f)
            )
        )
    }
}
