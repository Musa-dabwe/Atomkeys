package com.musa.atomkeys.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The text styles the design doc uses outside the keycaps themselves.
 *
 * Keycap labels are **not** here — they derive from key height, so they are computed in [Keycap]
 * from the sizing scale rather than being fixed styles. What lives here is the chrome that does not
 * scale: candidate chips, the sub-dots, the strip buttons and the press bubble.
 *
 * All of it is DM Sans except the press bubble, which shows a keycap glyph and therefore follows
 * the keycap font setting.
 */
object AtomType {

    /** A candidate chip's label: `fontSize: 15`, weight 500 when active and 400 otherwise. */
    @Composable
    @ReadOnlyComposable
    fun chip(active: Boolean): TextStyle {
        val theme = AtomKeyboard.theme
        return base.copy(
            fontSize = AtomSizes.chipText,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) theme.onChipActive else theme.chipText,
        )
    }

    /** The `·`·`·` under the active chip: 11sp at 2sp letter-spacing, in [AtomTheme.hint]. */
    @Composable
    @ReadOnlyComposable
    fun subDots(): TextStyle = base.copy(
        fontSize = AtomSizes.subDotsText,
        letterSpacing = AtomSizes.subDotsLetterSpacing,
        color = AtomKeyboard.theme.hint,
    )

    /** The `⋯` extension button: 20sp in [AtomTheme.fnText] on a [AtomTheme.chip] fill. */
    @Composable
    @ReadOnlyComposable
    fun stripButton(): TextStyle = base.copy(
        fontSize = 20.sp,
        color = AtomKeyboard.theme.fnText,
    )

    /** The mic button: 13sp in [AtomTheme.hint], on no fill. */
    @Composable
    @ReadOnlyComposable
    fun micButton(): TextStyle = base.copy(
        fontSize = 13.sp,
        color = AtomKeyboard.theme.hint,
    )

    /**
     * The press bubble's 30sp glyph. Follows the *keycap* font, not DM Sans — it is showing the
     * same character the keycap under it shows.
     */
    @Composable
    @ReadOnlyComposable
    fun pressBubble(): TextStyle = base.copy(
        fontFamily = AtomKeyboard.keycapFont.family,
        fontSize = AtomSizes.pressBubbleText,
        color = AtomKeyboard.theme.keyText,
    )

    /**
     * The gesture pill (G1–G5): 12sp medium in [AtomTheme.onAccent].
     *
     * Per the Phase 3.3 decision these are debug-only — the design doc's pills are annotations
     * describing each gesture, not shipping chrome.
     */
    @Composable
    @ReadOnlyComposable
    fun gesturePill(): TextStyle = base.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = AtomKeyboard.theme.onAccent,
    )

    /**
     * `lineHeight: 1` with no font padding, matching the CSS the whole design is specified in.
     * Every style above inherits it, so nothing in the keyboard picks up the platform's default
     * ascent/descent padding.
     */
    @Suppress("DEPRECATION")
    private val base = TextStyle(
        fontFamily = AtomFonts.DmSans,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
