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

// Cozy Lofi Night-Study (Espresso & Sunset Amber) base scheme - Design 6 default look.
private val InstrumentDarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color(0xFF1A1108),
    secondary = Amber,
    onSecondary = Color(0xFF1A1108),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFF1A1108),
    background = Void,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    onError = TextPrimary,
    errorContainer = AlertDim,
    onErrorContainer = Alert
)

// Deeper late-night espresso variant used when Smart Night Mode is active (10 PM - 6 AM).
private val InstrumentNightColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color(0xFF1A1108),
    secondary = Amber,
    onSecondary = Color(0xFF1A1108),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFF1A1108),
    background = Void,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    onError = TextPrimary,
    errorContainer = AlertDim,
    onErrorContainer = Alert
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD9822B),
    onPrimary = Color.White,
    secondary = Color(0xFFB55D35),
    onSecondary = Color.White,
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF231924),
    surface = Color.White,
    onSurface = Color(0xFF231924),
    error = Color(0xFFA32D2D),
    onError = Color.White
)

enum class AccentTheme(
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    LOFI_AMBER("Lofi Sunset Amber", Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFFFCC80)),
    PHOSPHOR_MINT("Phosphor Mint", Color(0xFF3DFFC4), Color(0xFFFFA630), Color(0xFF3DFFC4)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF29B6F6), Color(0xFFFFB74D), Color(0xFF29B6F6)),
    FOREST_GREEN("Forest Green", Color(0xFF66BB6A), Color(0xFFFFB74D), Color(0xFF66BB6A)),
    ROYAL_PURPLE("Royal Purple", Color(0xFFAB47BC), Color(0xFFFFB74D), Color(0xFFAB47BC)),
    NEON_CRIMSON("Neon Crimson", Color(0xFFEC407A), Color(0xFFFFB74D), Color(0xFFEC407A)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFFD500F9))
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    accentThemeName: String = "Lofi Sunset Amber",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isNightModeActive = SmartNightModeManager.isNightModeActive.collectAsState().value
    val accent = AccentTheme.values().find { it.displayName == accentThemeName } ?: AccentTheme.LOFI_AMBER

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
        else -> LightColorScheme.copy(
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
