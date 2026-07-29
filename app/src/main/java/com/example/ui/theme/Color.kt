package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// "Precision Instrument" palette - lab/oscilloscope inspired.
// Deep ink-navy rather than pure black, phosphor-mint as the
// single high-energy accent, amber for streak/energy states,
// alert red reserved for blocked/strict/danger states only.
// ============================================================

val Void = Color(0xFF0A0E16)          // App background - deep ink-navy, not pure black
val Panel = Color(0xFF12171F)         // Card/surface background
val PanelElevated = Color(0xFF171D27) // Slightly raised surface (nested cards, sheets)
val Hairline = Color(0xFF232B36)      // Card borders / dividers

val Phosphor = Color(0xFF3DFFC4)      // Signature accent - active/focus states, dial glow
val PhosphorDim = Color(0xFF1E4A40)   // Phosphor at rest / dim background fill
val Amber = Color(0xFFFFA630)         // Streak, energy, secondary accent
val AmberDim = Color(0xFF4A3416)
val Alert = Color(0xFFFF4757)         // Blocked, strict, destructive actions
val AlertDim = Color(0xFF4A1A20)

val TextPrimary = Color(0xFFE7ECEF)
val TextSecondary = Color(0xFF7C8894)
val TextFaint = Color(0xFF485058)

// Legacy names kept as aliases so any lingering references elsewhere in the
// (very large) codebase still resolve instead of breaking the build. Prefer
// the tokens above in new code.
val PrimaryFocus = Phosphor
val SecondaryFocus = Amber
val TertiaryFocus = Amber
val DarkBackground = Void
val DarkSurface = Panel
val DarkSurfaceVariant = PanelElevated
val OnPrimaryFocus = Void
val OnSecondaryFocus = Void
val OnBackgroundFocus = TextPrimary
val OnSurfaceFocus = TextPrimary
val LightPrimary = Amber
val LightSecondary = Phosphor
val LightBackground = Void
