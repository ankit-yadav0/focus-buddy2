package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.ui.helper.SmartNightModeManager

// Neon Black Theme (#08080F background, #1A1A24 cards, #00E5FF cyan, #FF00E6 magenta, #7B61FF violet)
private val InstrumentDarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF040810),
    primaryContainer = Color(0xFF0D2538),
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF330C35),
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1F1942),
    onTertiaryContainer = NeonViolet,
    background = NeonBlack,
    onBackground = TextPrimary,
    surface = NeonSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeonSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    onError = Color.White,
    errorContainer = AlertDim,
    onErrorContainer = Alert
)

// Deeper late-night AMOLED variant used when Smart Night Mode is active (10 PM - 6 AM).
private val InstrumentNightColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF040810),
    primaryContainer = Color(0xFF0D2538),
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF330C35),
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1F1942),
    onTertiaryContainer = NeonViolet,
    background = NeonBlack,
    onBackground = TextPrimary,
    surface = NeonSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeonSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    onError = Color.White,
    errorContainer = AlertDim,
    onErrorContainer = Alert
)

private val LightColorScheme = lightColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF040810),
    secondary = NeonMagenta,
    onSecondary = Color.White,
    tertiary = NeonViolet,
    onTertiary = Color.White,
    background = NeonBlack,
    onBackground = TextPrimary,
    surface = NeonSurface,
    onSurface = TextPrimary,
    error = Alert,
    onError = Color.White
)

enum class AccentTheme(
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    NEON_BLACK("Neon Black", Color(0xFF00E5FF), Color(0xFFFF00E6), Color(0xFF7B61FF)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF00E6), Color(0xFF00E5FF), Color(0xFF7B61FF)),
    ELECTRIC_VIOLET("Electric Violet", Color(0xFF7B61FF), Color(0xFF00E5FF), Color(0xFFFF00E6)),
    PHOSPHOR_MINT("Phosphor Mint", Color(0xFF3DFFC4), Color(0xFF00E5FF), Color(0xFF7B61FF)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF29B6F6), Color(0xFF7B61FF), Color(0xFFFF00E6)),
    NEON_CRIMSON("Neon Crimson", Color(0xFFFF2E93), Color(0xFF00E5FF), Color(0xFF7B61FF)),
    LOFI_AMBER("Lofi Sunset Amber", Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFFFCC80))
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    accentThemeName: String = "Neon Black",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isNightModeActive = SmartNightModeManager.isNightModeActive.collectAsState().value
    val accent = AccentTheme.values().find { it.displayName == accentThemeName } ?: AccentTheme.NEON_BLACK

    val colorScheme = when {
        isNightModeActive -> InstrumentNightColors.copy(
            primary = accent.primary,
            secondary = accent.secondary,
            tertiary = accent.tertiary
        )
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> InstrumentDarkColors.copy(
            primary = accent.primary,
            secondary = accent.secondary,
            tertiary = accent.tertiary
        )
        else -> InstrumentDarkColors.copy(
            primary = accent.primary,
            secondary = accent.secondary,
            tertiary = accent.tertiary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
