package com.geozelot.homer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Type system (design-locked): a serif for the wordmark + book/chapter titles (the
// "classical" voice), the platform sans for all chrome. Android's FontFamily.Serif maps to
// Noto Serif — the closest FOSS stand-in for the mockup's Georgia, no bundled font needed.
private val Serif = FontFamily.Serif

/** Book/series/chapter titles and the wordmark. */
val SerifDisplay = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.01).sp,
)

val SerifTitle = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
)

/** Small-caps-ish section headers ("CONTINUE", "LIBRARY · 297"). */
val SectionLabel = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.16.em,
)

/** Numbers that should not jitter (counts, times, progress). */
val TabularSmall = TextStyle(
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontFeatureSettings = "tnum",
)

/**
 * Material type scale. Titles use the serif so component defaults (dialog titles, list
 * headers) inherit the right voice; body/label stay sans.
 */
val HomerTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    )
}
