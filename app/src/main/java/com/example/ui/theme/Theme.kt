package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.collectAsState
import com.example.ui.helper.SmartNightModeManager

private val PitchBlackColors = darkColorScheme(
    primary = Color(0xFFFFB74D),       // Soft amber
    onPrimary = Color(0xFF1E1400),
    secondary = Color(0xFF3F51B5),     // Deep indigo
    onSecondary = Color.White,
    tertiary = Color(0xFFE040FB),      // Pastel lavender
    background = Color(0xFF000000),    // AMOLED Black
    onBackground = Color(0xFFE0E1EC),  // Low-glare light lavender/gray
    surface = Color(0xFF0C0C0E),       // Extremely dark charcoal
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF16161A), // Low-glare deep structural elements
    onSurfaceVariant = Color(0xFFC7C5D0)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryFocus,
    onPrimary = OnPrimaryFocus,
    secondary = SecondaryFocus,
    onSecondary = OnSecondaryFocus,
    tertiary = TertiaryFocus,
    background = DarkBackground,
    onBackground = OnBackgroundFocus,
    surface = DarkSurface,
    onSurface = OnSurfaceFocus,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnBackgroundFocus
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF2E1C16),
    surface = Color.White,
    onSurface = Color(0xFF2E1C16)
)

enum class AccentTheme(
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    SUNSET_ORANGE("Sunset Orange", Color(0xFFFF7043), Color(0xFF26A69A), Color(0xFFFFB74D)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF29B6F6), Color(0xFF26A69A), Color(0xFFAB47BC)),
    FOREST_GREEN("Forest Green", Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFF26A69A)),
    ROYAL_PURPLE("Royal Purple", Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFFF7043)),
    NEON_CRIMSON("Neon Crimson", Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFFFF7043)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFFD500F9))
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true as Focuss Buddy is a premium dark theme app
    accentThemeName: String = "Sunset Orange",
    dynamicColor: Boolean = false, // Keep false to maintain our custom visual branding identity
    content: @Composable () -> Unit,
) {
    val isNightModeActive = SmartNightModeManager.isNightModeActive.collectAsState().value
    val accent = AccentTheme.values().find { it.displayName == accentThemeName } ?: AccentTheme.SUNSET_ORANGE

    val colorScheme = when {
        isNightModeActive -> PitchBlackColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accent.primary,
            onPrimary = OnPrimaryFocus,
            secondary = accent.secondary,
            onSecondary = OnSecondaryFocus,
            tertiary = accent.tertiary,
            background = DarkBackground,
            onBackground = OnBackgroundFocus,
            surface = DarkSurface,
            onSurface = OnSurfaceFocus,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = OnBackgroundFocus
        )
        else -> lightColorScheme(
            primary = accent.primary,
            onPrimary = Color.White,
            secondary = accent.secondary,
            onSecondary = Color.White,
            background = LightBackground,
            onBackground = Color(0xFF2E1C16),
            surface = Color.White,
            onSurface = Color(0xFF2E1C16)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
