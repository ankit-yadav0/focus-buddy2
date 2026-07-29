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

// Precision Instrument base scheme - the app's default look. Deep ink-navy,
// phosphor-mint as the signature accent, amber as secondary, red reserved
// for the Material "error" role (which lines up with blocked/strict states).
private val InstrumentDarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Void,
    secondary = Amber,
    onSecondary = Void,
    tertiary = Phosphor,
    onTertiary = Void,
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

// Even more austere variant used when auto night-mode is active - pushes the
// background closer to true black to save battery on AMOLED screens and cut
// glare late at night, while keeping the same phosphor/amber accent identity.
private val InstrumentNightColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color.Black,
    secondary = Amber,
    onSecondary = Color.Black,
    tertiary = Phosphor,
    background = Color(0xFF000000),
    onBackground = TextPrimary,
    surface = Color(0xFF090A0C),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF12141A),
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    error = Alert,
    onError = Color.Black,
    errorContainer = AlertDim,
    onErrorContainer = Alert
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F6E56),
    onPrimary = Color.White,
    secondary = Color(0xFFB5730B),
    onSecondary = Color.White,
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF171D1A),
    surface = Color.White,
    onSurface = Color(0xFF171D1A),
    error = Color(0xFFA32D2D),
    onError = Color.White
)

/**
 * User-selectable accent overrides (Settings > Accent theme). Kept as an
 * existing feature - values updated so each option still reads as a
 * cohesive, deliberate palette against the new ink-navy/instrument base
 * rather than clashing with it.
 */
enum class AccentTheme(
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    PHOSPHOR_MINT("Phosphor Mint", Phosphor, Amber, Phosphor),
    OCEAN_BLUE("Ocean Blue", Color(0xFF29B6F6), Amber, Color(0xFF29B6F6)),
    FOREST_GREEN("Forest Green", Color(0xFF66BB6A), Amber, Color(0xFF66BB6A)),
    ROYAL_PURPLE("Royal Purple", Color(0xFFAB47BC), Phosphor, Color(0xFFAB47BC)),
    NEON_CRIMSON("Neon Crimson", Color(0xFFEC407A), Amber, Color(0xFFEC407A)),
    CYBERPUNK("Cyberpunk", Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFFD500F9))
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true - Focuss Buddy is a premium dark theme app
    accentThemeName: String = "Phosphor Mint",
    dynamicColor: Boolean = false, // Keep false to maintain our custom visual branding identity
    content: @Composable () -> Unit,
) {
    val isNightModeActive = SmartNightModeManager.isNightModeActive.collectAsState().value
    val accent = AccentTheme.values().find { it.displayName == accentThemeName } ?: AccentTheme.PHOSPHOR_MINT

    val colorScheme = when {
        isNightModeActive -> InstrumentNightColors
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
