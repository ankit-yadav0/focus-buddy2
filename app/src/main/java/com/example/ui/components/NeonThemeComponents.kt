package com.example.ui.components

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.viewmodel.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

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

// =========================================================================
// REAL PHONE ICON & USAGE STATS ENGINE
// Loads actual installed app icons via PackageManager and real foreground
// screen-time metrics via Android's UsageStatsManager.
// =========================================================================

private val appIconCache = object : LruCache<String, ImageBitmap>(140) {}

private fun drawableToImageBitmap(drawable: Drawable, targetPx: Int = 128): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val w = drawable.intrinsicWidth.coerceIn(1, targetPx)
    val h = drawable.intrinsicHeight.coerceIn(1, targetPx)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
fun rememberAppIconBitmap(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val cached = appIconCache.get(packageName)
    val bitmapState by produceState<ImageBitmap?>(initialValue = cached, key1 = packageName) {
        if (packageName.isBlank()) {
            value = null
            return@produceState
        }
        val existing = appIconCache.get(packageName)
        if (existing != null) {
            value = existing
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToImageBitmap(drawable)
            } catch (e: Exception) {
                null
            }
        }
        if (loaded != null) {
            appIconCache.put(packageName, loaded)
        }
        value = loaded
    }
    return bitmapState
}

/**
 * Renders the user's real installed app icon from PackageManager, falling back
 * to a neon squircle initial only if the package is not installed.
 */
@Composable
fun RealAppIconBadge(
    packageName: String,
    appName: String,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val iconBitmap = rememberAppIconBitmap(packageName)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = appName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(cornerRadius))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(
                        listOf(
                            NeonCyan.copy(alpha = 0.25f),
                            NeonViolet.copy(alpha = 0.25f),
                            NeonMagenta.copy(alpha = 0.25f)
                        )
                    )
                )
                .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

data class RealAppUsageItem(
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val formattedTime: String,
    val percentOfTotal: Int
)

fun formatUsageDuration(millis: Long): String {
    val totalSecs = (millis / 1000L).coerceAtLeast(0L)
    val hrs = totalSecs / 3600L
    val mins = (totalSecs % 3600L) / 60L
    val secs = totalSecs % 60L
    return when {
        hrs > 0L -> "${hrs}h ${mins}m"
        mins > 0L -> "${mins}m"
        else -> "${secs}s"
    }
}

/**
 * Queries real device screen-time usage from Android's UsageStatsManager.
 *   • "Today": exact foreground intervals since 00:00 local midnight today via UsageEvents
 *              (with queryUsageStats fallback for ROMs that restrict raw events).
 *   • "7 Days": aggregated foreground usage over the past 7 days.
 *   • "30 Days": aggregated foreground usage over the past 30 days.
 */
@Suppress("DEPRECATION")
fun queryRealPhoneAppUsage(context: Context, rangeMode: String): List<RealAppUsageItem> {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()

        val startMs = when (rangeMode) {
            "7 Days" -> now - 7L * 24L * 3600L * 1000L
            "30 Days" -> now - 30L * 24L * 3600L * 1000L
            else -> {
                Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        val durationByPkg = mutableMapOf<String, Long>()

        if (rangeMode == "Today") {
            // Compute exact foreground sessions from UsageEvents since midnight
            val events = usm.queryEvents(startMs, now)
            val foregroundStartByPkg = mutableMapOf<String, Long>()
            val ev = UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                when (ev.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        foregroundStartByPkg[pkg] = ev.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND, 23 /* ACTIVITY_STOPPED */ -> {
                        val fgStart = foregroundStartByPkg.remove(pkg)
                        if (fgStart != null && ev.timeStamp > fgStart) {
                            durationByPkg[pkg] = (durationByPkg[pkg] ?: 0L) + (ev.timeStamp - fgStart)
                        }
                    }
                }
            }
            // Close any app still in foreground right now
            for ((pkg, fgStart) in foregroundStartByPkg) {
                if (now > fgStart) {
                    durationByPkg[pkg] = (durationByPkg[pkg] ?: 0L) + (now - fgStart)
                }
            }
        }

        // Fallback or multi-day aggregation via queryUsageStats
        if (durationByPkg.isEmpty()) {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, now)
            if (statsList != null) {
                for (stat in statsList) {
                    if (stat.lastTimeUsed < startMs) continue
                    val fg = stat.totalTimeInForeground
                    if (fg > 0L) {
                        val pkg = stat.packageName
                        // Avoid double-counting overlapping daily buckets in Today mode
                        if (rangeMode == "Today") {
                            durationByPkg[pkg] = maxOf(durationByPkg[pkg] ?: 0L, fg)
                        } else {
                            durationByPkg[pkg] = (durationByPkg[pkg] ?: 0L) + fg
                        }
                    }
                }
            }
        }

        // Filter to launchable user/system apps with >= 5 seconds of usage
        val validEntries = mutableListOf<Triple<String, String, Long>>()
        for ((pkg, ms) in durationByPkg) {
            if (ms < 5_000L) continue
            // Only include apps that have a launcher entry (or is our own app)
            if (pkg != context.packageName && pm.getLaunchIntentForPackage(pkg) == null) continue
            val label = try {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast(".")
            }
            validEntries.add(Triple(pkg, label, ms))
        }

        validEntries.sortByDescending { it.third }
        val totalMs = validEntries.sumOf { it.third }.coerceAtLeast(1L)

        validEntries.take(25).map { (pkg, label, ms) ->
            val pct = ((ms * 100L) / totalMs).toInt().coerceIn(1, 100)
            RealAppUsageItem(
                packageName = pkg,
                appName = label,
                usageMillis = ms,
                formattedTime = formatUsageDuration(ms),
                percentOfTotal = pct
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Returns the user's real most recently used apps from UsageStatsManager,
 * falling back to installed apps if Usage Access is not yet granted.
 */
@Suppress("DEPRECATION")
fun queryRecentPhoneApps(
    context: Context,
    installedApps: List<AppInfo>,
    maxCount: Int = 4
): List<AppInfo> {
    if (installedApps.isEmpty()) return emptyList()
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 48L * 3600L * 1000L,
            now
        )
        if (!stats.isNullOrEmpty()) {
            val lastUsedMap = mutableMapOf<String, Long>()
            for (s in stats) {
                if (s.lastTimeUsed > (lastUsedMap[s.packageName] ?: 0L)) {
                    lastUsedMap[s.packageName] = s.lastTimeUsed
                }
            }
            val byPkg = installedApps.associateBy { it.packageName }
            val recent = lastUsedMap.entries
                .sortedByDescending { it.value }
                .mapNotNull { byPkg[it.key] }
                .take(maxCount)
            if (recent.isNotEmpty()) return recent
        }
        installedApps.take(maxCount)
    } catch (e: Exception) {
        installedApps.take(maxCount)
    }
}

/**
 * Classifies a real installed app into "Study", "Social", "Games", or "Tools"
 * using ApplicationInfo.category (API 26+) plus package/title heuristics and
 * the user's pinned study packages.
 */
fun classifyRealAppCategory(
    context: Context,
    packageName: String,
    appName: String,
    pinnedStudyPackages: Set<String>
): String {
    if (packageName in pinnedStudyPackages) return "Study"
    val lowerPkg = packageName.lowercase()
    val lowerName = appName.lowercase()

    // Check explicit study / ed-tech / document keywords first
    val studyKeywords = listOf(
        "pw.live", "physicswallah", "unacademy", "allen", "byjus", "doubtnut",
        "testbook", "adda247", "sankalp", "apnikaksha", "careerwill", "vedantu",
        "anki", "notion", "obsidian", "evernote", "keep", "docs", "sheets",
        "slides", "pdf", "acrobat", "reader", "udemy", "coursera", "khan",
        "duolingo", "dictionary", "wikipedia", "chatgpt", "gemini", "claude",
        "classroom", "study", "learn", "exam", "ncert", "jee", "neet", "notes"
    )
    if (studyKeywords.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
        return "Study"
    }

    val socialKeywords = listOf(
        "whatsapp", "instagram", "telegram", "snapchat", "facebook", "katana",
        "twitter", "threads", "discord", "reddit", "youtube", "pinterest",
        "linkedin", "messenger", "signal", "josh", "moj", "sharechat"
    )
    if (socialKeywords.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
        return "Social"
    }

    val gameKeywords = listOf(
        "game", "bgmi", "pubg", "freefire", "ludo", "clash", "chess",
        "roblox", "minecraft", "candies", "subwaysurf", " EA ", "gameloft", "supercell"
    )
    if (gameKeywords.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
        return "Games"
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> return "Games"
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                ApplicationInfo.CATEGORY_NEWS -> return "Study"
            }
        } catch (_: Exception) {
        }
    }

    return "Tools"
}

/**
 * Full-screen Neon Black background (#08080F) with diagonal laser light streaks
 * (#FF00E6 magenta, #00E5FF cyan, #7B61FF violet).
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

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(NeonCyan, NeonMagenta)
                ),
                radius = outerR * 0.20f,
                center = Offset(cx, cy)
            )

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

            drawCircle(
                color = NeonBlack,
                radius = r * 0.92f,
                center = Offset(cx, cy)
            )

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

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, NeonViolet, NeonMagenta, NeonCyan),
                    center = Offset(cx, cy)
                ),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )

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


@Composable
fun NeonStarfieldBackground(modifier: Modifier = Modifier) {
    NeonLaserBackground(modifier = modifier) {}
}

@Composable
fun NeonSplashScreen(modifier: Modifier = Modifier) {
    NeonLaserBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NeonTargetLightningLogo(size = 112.dp)
                FocusBuddyNeonTitle(fontSize = 32.sp, focusInCyan = false)
                Text(
                    text = "Block Distractions\nBuild Your Future",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
            NeonGradientProgressBar(
                progress = 0.65f,
                modifier = Modifier.width(150.dp),
                height = 4.dp
            )
        }
    }
}
