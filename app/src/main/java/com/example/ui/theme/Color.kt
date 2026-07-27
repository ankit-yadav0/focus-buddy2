package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Focus Colors - Orange/Amber warmth and dark background
val PrimaryFocus = Color(0xFFFF7043)       // Warm sunset orange for action
val SecondaryFocus = Color(0xFF26A69A)     // Cool calm teal for secondary elements
val TertiaryFocus = Color(0xFFFFB74D)      // Soft amber

val DarkBackground = Color(0xFF0F0C1E)     // Deep rich midnight space background
val DarkSurface = Color(0xFF17132F)        // Lighter container surface
val DarkSurfaceVariant = Color(0xFF211D3C) // Dark gray with purple undertones

val OnPrimaryFocus = Color(0xFFFFFFFF)
val OnSecondaryFocus = Color(0xFFFFFFFF)
val OnBackgroundFocus = Color(0xFFE3E1EC)
val OnSurfaceFocus = Color(0xFFF1EFF9)

// Light Palette (kept for compatibility, styled cleanly)
val LightPrimary = Color(0xFFD84315)
val LightSecondary = Color(0xFF00796B)
val LightBackground = Color(0xFFFBE9E7)

// Deep Space Console theme - matches the reference UI: HUD cyan, void black,
// violet for selected/active states, and alert red for blocked/danger states.
val SpaceVoidBackground = Color(0xFF03060C)   // Deep space black
val SpaceSurface = Color(0xFF0A101C)          // Panel/card surface
val SpaceSurfaceVariant = Color(0xFF111A2B)   // Slightly lighter structural surface
val SpaceCyan = Color(0xFF00E5FF)             // Primary HUD accent
val SpaceCyanDim = Color(0xFF0F2A33)          // Dim cyan for inactive rings/borders
val SpaceViolet = Color(0xFF8B2FF0)           // Secondary accent (selected day, active session)
val SpaceRed = Color(0xFFFF3B3B)              // Danger / blocked / access-blocked state
val SpaceTextPrimary = Color(0xFFE8F4FF)      // Primary readable text on dark
val SpaceTextSecondary = Color(0xFF7C93AE)    // Muted secondary text/icons
