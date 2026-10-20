package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// ─── Font Families ──────────────────────────────────────────────────────────
// Inter as open-source geometric sans substitute for Universal Sans
val UniversalSansFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal)
)

// JetBrains Mono as open-source substitute for Geist Mono
val GeistMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

// ─── xAI Typography Tokens (Strict Weight 400 with Custom Tracking) ─────────
val TypographyDisplayXL = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 96.sp,
    lineHeight = 96.sp,
    letterSpacing = (-2.4).sp
)

val TypographyDisplayLG = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 72.sp,
    lineHeight = 72.sp,
    letterSpacing = (-1.8).sp
)

val TypographyDisplayMD = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 48.sp,
    lineHeight = 48.sp,
    letterSpacing = (-1.2).sp
)

val TypographyDisplaySM = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.6).sp
)

val TypographyDisplayXS = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

val TypographyBodyLG = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

val TypographyBodyMD = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

val TypographyBodySM = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

val TypographyCaptionMono = TextStyle(
    fontFamily = GeistMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 1.4.sp
)

val TypographyCaptionMonoSM = TextStyle(
    fontFamily = GeistMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp
)

val TypographyButtonMD = TextStyle(
    fontFamily = UniversalSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

// ─── Material 3 Mappings ────────────────────────────────────────────────────
val Typography = Typography(
    displayLarge = TypographyDisplayLG,
    displayMedium = TypographyDisplayMD,
    displaySmall = TypographyDisplaySM,
    headlineLarge = TypographyDisplaySM,
    headlineMedium = TypographyDisplayXS,
    headlineSmall = TextStyle(
        fontFamily = UniversalSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TypographyDisplayXS,
    titleMedium = TextStyle(
        fontFamily = UniversalSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = UniversalSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TypographyBodyMD,
    bodyMedium = TypographyBodySM,
    bodySmall = TextStyle(
        fontFamily = UniversalSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TypographyButtonMD,
    labelMedium = TypographyCaptionMono,
    labelSmall = TypographyCaptionMonoSM
)
