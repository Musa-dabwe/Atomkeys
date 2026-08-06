package com.musa.atomkeys.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.design.AtomFonts

/**
 * Material3 typography for the settings app, in DM Sans — the family the design doc uses for all
 * UI, settings and chip text. Keycaps use Barlow Semi Condensed and are sized from key height, so
 * they never come through here.
 *
 * Only the roles the settings screens actually use are overridden; the rest inherit Material3's
 * defaults. The scaffold's 0.5sp `letterSpacing` is dropped — DM Sans is drawn at its natural
 * tracking everywhere in the design doc.
 */
private val DmSans = AtomFonts.DmSans

val Typography = Typography(
    // 64dp settings rows: a title with an optional secondary line beneath it.
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Section headers.
    titleMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // Segmented-control labels and right-aligned values.
    labelLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)
