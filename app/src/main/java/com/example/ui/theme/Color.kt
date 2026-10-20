package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ─── xAI Design System Tokens ───────────────────────────────────────────────
// Canvas & Surfaces
val ColorCanvas = Color(0xFF0A0A0A)          // Strict near-black canvas #0a0a0a
val ColorCanvasSoft = Color(0xFF1A1C20)      // Inputs, hovered nav, tooltips #1a1c20
val ColorCanvasCard = Color(0xFF191919)      // Charcoal card fill #191919
val ColorCanvasMid = Color(0xFF363A3F)       // Nested surfaces, code backgrounds #363a3f
val ColorHairline = Color(0xFF212327)        // 1px hairline dividers & card borders #212327

// Text & Ink
val ColorPrimary = Color(0xFFFFFFFF)         // Brand primary #ffffff
val ColorOnPrimary = Color(0xFF0A0A0A)       // #0a0a0a
val ColorInk = Color(0xFFFFFFFF)             // Pure white #ffffff
val ColorInkHover = Color(0xFFFAFAF7)        // Slightly off-white #fafaf7
val ColorBody = Color(0xFFDADBDF)            // Secondary body #dadbdf
val ColorBodyMid = Color(0xFF7D8187)         // Captions, mute text #7d8187
val ColorMute = Color(0xFF7D8187)

// Illustrative & Status Accents
val ColorAccentSunset = Color(0xFFFF7A17)    // Warm orange #ff7a17 (warn / connecting)
val ColorAccentSunsetSoft = Color(0xFFFFC285)// #ffc285
val ColorAccentDusk = Color(0xFF7C3AED)      // Deep purple #7c3aed
val ColorAccentTwilight = Color(0xFFC4B5FD)  // Soft violet #c4b5fd
val ColorAccentBreeze = Color(0xFFA0C3EC)    // Soft blue #a0c3ec (telemetry / online)
val ColorAccentMidnight = Color(0xFF0D1726)  // Deep blue-black #0d1726

// Interactive Pill Borders
val ColorPillOutline = Color(0x40FFFFFF)     // rgba(255, 255, 255, 0.25)
val ColorPillOutlineSubtle = Color(0x26FFFFFF) // rgba(255, 255, 255, 0.15)

// Status Cues
val ColorStatusOnline = Color(0xFF22C55E)    // Green indicator
val ColorStatusConnecting = Color(0xFFFF7A17)// Sunset orange
val ColorStatusError = Color(0xFFEF4444)     // Red error

// ─── Legacy Mappings for Backward Compatibility ─────────────────────────────
val Slate950 = ColorCanvas
val Slate900 = ColorCanvasCard
val Slate850 = ColorCanvasSoft
val Slate800 = ColorCanvasMid
val Slate700 = ColorHairline
val Slate600 = ColorBodyMid
val Slate500 = ColorBodyMid
val Slate400 = ColorBodyMid
val Slate300 = ColorBody
val Slate200 = ColorBody
val Slate100 = ColorInk

val CyanPrimary = ColorPrimary
val CyanPrimaryContainer = ColorCanvasSoft
val CyanOnPrimaryContainer = ColorInk

val GreenOnline = ColorStatusOnline
val GreenContainer = Color(0xFF064E3B)
val AmberWarn = ColorAccentSunset
val AmberContainer = Color(0xFF78350F)
val RoseError = ColorStatusError
val RoseContainer = Color(0xFF7F1D1D)

val PurpleAccent = ColorAccentDusk
val IndigoAccent = ColorAccentTwilight
