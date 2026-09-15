package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Fonts are bundled directly in res/font/ (variable-font TTFs from
// google/fonts, each with a font-family XML selecting specific weight
// instances via fontVariationSettings). Bundling rather than using the
// Google Fonts downloadable-fonts API means these always work offline and
// don't depend on Google Play Services being present/up to date on the
// device - important for a low-end/low-connectivity target device.

// Space Grotesk - technical, sharp geometric sans. Used for headings/titles.
val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk))

// JetBrains Mono - lab-instrument readout feel. Used for ALL numerals: timer
// digits, countdowns, stat values, dates. This is the typographic signature
// tying the "precision instrument" concept together.
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono))

// Inter - neutral, highly readable humanist sans. Used for body copy.
val InterFamily = FontFamily(Font(R.font.inter))

/**
 * Dedicated style for numeral-heavy displays (timer digits, dial readouts,
 * countdown flaps, stat values). Not part of Material's Typography scale -
 * reference this directly wherever a "gauge readout" look is wanted.
 */
val MonoNumeralLarge = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    letterSpacing = 1.sp
)

val MonoNumeralMedium = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    letterSpacing = 0.sp
)

val MonoLabel = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 2.sp
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp
    )
)
