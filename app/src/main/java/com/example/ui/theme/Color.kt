package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// "Neon Black Theme" — Clean • Modern • Focused
// Exact palette from the Neon Focus Buddy UI Theme Board:
//   • Neon Cyan:    #00E5FF (Primary accent)
//   • Neon Magenta: #FF00E6 (Secondary accent)
//   • Neon Violet:  #7B61FF (Tertiary / gradient mid-stop)
//   • Neon Black:   #08080F (Deep pitch-black app background)
//   • Neon Surface: #1A1A24 (Dark slate-navy card surface)
// ============================================================

val NeonCyan = Color(0xFF00E5FF)
val NeonMagenta = Color(0xFFFF00E6)
val NeonViolet = Color(0xFF7B61FF)
val NeonBlack = Color(0xFF08080F)
val NeonSurface = Color(0xFF1A1A24)
val NeonSurfaceElevated = Color(0xFF222230)
val NeonBorderDim = Color(0xFF2B2B3D)

// Core semantic tokens mapped to the Neon Black Theme
val Void = NeonBlack                  // #08080F — App pitch-black background
val Panel = NeonSurface               // #1A1A24 — Card/surface background
val PanelElevated = NeonSurfaceElevated
val Hairline = Color(0xFF2E3248)      // Subtle cool neon-slate border/divider

val Phosphor = NeonCyan               // #00E5FF — Signature primary accent (Neon Cyan)
val Amber = NeonMagenta               // #FF00E6 — Secondary accent (Neon Magenta)
val Alert = Color(0xFFFF2E93)         // Blocked, strict, destructive neon pink-red
val AlertDim = Color(0xFF330A28)

val TextPrimary = Color(0xFFFFFFFF)   // Crisp pure white
val TextSecondary = Color(0xFFA0A6BE) // Cool modern silver-slate

// Signature Neon Gradients
val NeonPrimaryGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, NeonViolet, NeonMagenta)
)

val NeonReverseGradient = Brush.horizontalGradient(
    colors = listOf(NeonMagenta, NeonViolet, NeonCyan)
)

val NeonVerticalBarGradient = Brush.verticalGradient(
    colors = listOf(NeonMagenta, NeonViolet, NeonCyan)
)
