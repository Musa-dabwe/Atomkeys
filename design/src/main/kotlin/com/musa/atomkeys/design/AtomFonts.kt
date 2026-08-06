package com.musa.atomkeys.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The two type families the design doc specifies (`AtomKeyboard.dc.html` § Tokens):
 *
 *  - **Barlow Semi Condensed 500** — keycaps and hint labels
 *  - **DM Sans 400/500/700** — UI, settings, candidate chips
 *
 * Both are OFL-licensed; the licence text ships in `res/raw/license_ofl_*.txt`, which is what the OFL
 * requires of a distributed binary. Surface it from an in-app licences screen (Phase 9.2).
 */
object AtomFonts {

    /** Keycaps and hints. Static instances at 400/500/600. */
    val BarlowSemiCondensed = FontFamily(
        Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
        Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    )

    /**
     * UI, settings and candidate chips.
     *
     * DM Sans is published **only as a variable font** (`DMSans[opsz,wght].ttf`) — Google Fonts no
     * longer ships static instances. One file covers every weight, which is smaller than three
     * statics would be, but each weight must be requested explicitly through its `wght` axis:
     * declaring `FontWeight.Medium` alone would render at the font's default weight.
     *
     * Variable-font axis support requires API 26+, which is our `minSdk`, so no fallback is needed.
     */
    val DmSans = FontFamily(
        variableFont(R.font.dm_sans_variable, FontWeight.Normal),
        variableFont(R.font.dm_sans_variable, FontWeight.Medium),
        variableFont(R.font.dm_sans_variable, FontWeight.Bold),
    )

    // FontVariation is still @ExperimentalTextApi. Contained to this one function so the opt-in does
    // not spread; if the API changes, this is the only call site to update.
    @OptIn(ExperimentalTextApi::class)
    private fun variableFont(resId: Int, weight: FontWeight) = Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

/**
 * Which family the keycaps use, per the design doc's *Look → Keyboard font: OS | Atom* setting.
 * [OS] hands keycaps to the system font; [ATOM] uses Barlow Semi Condensed.
 */
enum class KeycapFont {
    OS,
    ATOM,
    ;

    // Not `@Composable`: both branches are plain values that read nothing from the composition, and
    // a composable getter cannot be called from the `@ReadOnlyComposable` text styles in AtomType.
    val family: FontFamily
        get() = when (this) {
            OS -> FontFamily.Default
            ATOM -> AtomFonts.BarlowSemiCondensed
        }
}
