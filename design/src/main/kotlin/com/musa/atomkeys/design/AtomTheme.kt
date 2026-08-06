package com.musa.atomkeys.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.musa.atomkeys.engine.KeySize

/**
 * The keyboard's colour vocabulary: the 14 tokens every theme in `AtomKeyboard.dc.html` defines.
 *
 * Material3's `colorScheme` is deliberately **not** used for keyboard surfaces — nor is dynamic
 * colour. The design is a set of hand-picked dusty pastels whose relationships (keycap against its
 * edge against its shadow) do not survive being mapped onto Material roles. Material3 stays as
 * chrome for the settings app only.
 *
 * [Immutable] rather than `@Stable`: every field is a `val` holding a value type, so Compose can
 * skip recomposition of any composable whose only changed input is an equal theme.
 */
@Immutable
data class AtomTheme(
    val name: String,

    /** The keyboard's own background, behind every key and the candidate strip. */
    val kbBg: Color,

    /** A normal keycap's fill — letters, symbols, spacebar, `.`. */
    val key: Color,

    /** The 1dp border around a keycap. */
    val keyEdge: Color,

    /** The 1.5dp hard shadow under a keycap. Not a blur — see [Keycap]. */
    val keyShadow: Color,

    /** Label colour on a normal keycap. */
    val keyText: Color,

    /** Fill for function keys: shift, backspace, `123`/`ABC`, the special key, `AC`. */
    val fnKey: Color,

    /** Label colour on a function key. */
    val fnText: Color,

    /** Numeric hints on letters row 1, the mic glyph, and the candidate sub-dots. */
    val hint: Color,

    /** The one saturated colour: the ✓ key, shift-lock, the middle rule, gesture affordances. */
    val accent: Color,

    /** Label colour on an accent fill. */
    val onAccent: Color,

    /**
     * Candidate-chip and `⋯`-button background.
     *
     * **Translucent by design** — `rgba(0,0,0,0.05)` in the light themes and
     * `rgba(255,255,255,0.07)` in Claude Dark. It composites over [kbBg], so the alpha is kept
     * rather than flattened: flattening would break the moment a chip sits over anything but the
     * plain keyboard background.
     */
    val chip: Color,

    /** Label colour on an inactive chip. */
    val chipText: Color,

    /** Fill for the selected candidate chip. */
    val chipActive: Color,

    /** Label colour on the selected candidate chip. */
    val onChipActive: Color,
)

/**
 * The six token sets, transcribed verbatim from the `SAGE` / `CLAY` / `MAUVE` / `OAT` / `CL` / `CD`
 * constants in `AtomKeyboard.dc.html`.
 *
 * Only **five** are selectable in the theme picker (see [AtomThemes.selectable]); [Oat] is internal
 * — it is the extension deck's chrome (frame E1) and the component's own `FALLBACK`.
 */
object AtomThemes {

    /** `rgba(0,0,0,0.05)` — the chip fill shared by every theme except Claude Light and Dark. */
    private val BlackAlpha05 = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.05f)

    val SageMist = AtomTheme(
        name = "Sage Mist",
        kbBg = Color(0xFFDDE3DA),
        key = Color(0xFFF4F7F1),
        keyEdge = Color(0xFFC9D2C4),
        keyShadow = Color(0xFFC2CCBC),
        keyText = Color(0xFF333B33),
        fnKey = Color(0xFFCCD5C7),
        fnText = Color(0xFF4E574C),
        hint = Color(0xFF8D9A88),
        accent = Color(0xFF7E9A80),
        onAccent = Color(0xFFFFFFFF),
        chip = BlackAlpha05,
        chipText = Color(0xFF333B33),
        chipActive = Color(0xFF7E9A80),
        onChipActive = Color(0xFFFFFFFF),
    )

    val Clay = AtomTheme(
        name = "Clay",
        kbBg = Color(0xFFE9DCD3),
        key = Color(0xFFF9F2EC),
        keyEdge = Color(0xFFDCC9BC),
        keyShadow = Color(0xFFD5C1B2),
        keyText = Color(0xFF45372F),
        fnKey = Color(0xFFDDCCC0),
        fnText = Color(0xFF6A5648),
        hint = Color(0xFFA88F7E),
        accent = Color(0xFFBE8567),
        onAccent = Color(0xFFFFFFFF),
        chip = BlackAlpha05,
        chipText = Color(0xFF45372F),
        chipActive = Color(0xFFBE8567),
        onChipActive = Color(0xFFFFFFFF),
    )

    val Mauve = AtomTheme(
        name = "Mauve",
        kbBg = Color(0xFFE2DAE4),
        key = Color(0xFFF7F2F8),
        keyEdge = Color(0xFFD0C5D3),
        keyShadow = Color(0xFFC9BDCC),
        keyText = Color(0xFF3E3542),
        fnKey = Color(0xFFD3C9D6),
        fnText = Color(0xFF5D5164),
        hint = Color(0xFF9A8CA0),
        accent = Color(0xFF9A7FA6),
        onAccent = Color(0xFFFFFFFF),
        chip = BlackAlpha05,
        chipText = Color(0xFF3E3542),
        chipActive = Color(0xFF9A7FA6),
        onChipActive = Color(0xFFFFFFFF),
    )

    /**
     * **Not selectable.** Oat is the extension deck's chrome (frame E1) and the keyboard
     * component's `FALLBACK` theme — the one it renders when no theme is supplied at all.
     *
     * The two design docs disagree on one token: `AtomKeyboard.dc.html`'s `OAT` has
     * `hint: '#A0977F'` while `AtomKeys.dc.html`'s inline `FALLBACK` has `hint: '#A69C8B'`. The
     * theme table is taken as authoritative, since that is the file that defines themes; the
     * difference is a barely-visible shade on a 0.55-opacity hint.
     */
    val Oat = AtomTheme(
        name = "Oat",
        kbBg = Color(0xFFE8E3DA),
        key = Color(0xFFF8F5EF),
        keyEdge = Color(0xFFD8D1C4),
        keyShadow = Color(0xFFD0C8B8),
        keyText = Color(0xFF3B3730),
        fnKey = Color(0xFFDCD5C8),
        fnText = Color(0xFF5C564B),
        hint = Color(0xFFA0977F),
        accent = Color(0xFF8E9C7F),
        onAccent = Color(0xFFFFFFFF),
        chip = BlackAlpha05,
        chipText = Color(0xFF3B3730),
        chipActive = Color(0xFF8E9C7F),
        onChipActive = Color(0xFFFFFFFF),
    )

    val ClaudeLight = AtomTheme(
        name = "Claude Light",
        kbBg = Color(0xFFEFECE3),
        key = Color(0xFFFAF9F5),
        keyEdge = Color(0xFFE0DBCD),
        keyShadow = Color(0xFFDAD4C4),
        keyText = Color(0xFF191919),
        fnKey = Color(0xFFE4E0D3),
        fnText = Color(0xFF4A473F),
        hint = Color(0xFF9C978A),
        accent = Color(0xFFD97757),
        onAccent = Color(0xFFFFFFFF),
        // The one theme whose chip alpha differs: rgba(0,0,0,0.055), not 0.05.
        chip = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.055f),
        chipText = Color(0xFF191919),
        chipActive = Color(0xFFD97757),
        onChipActive = Color(0xFFFFFFFF),
    )

    val ClaudeDark = AtomTheme(
        name = "Claude Dark",
        kbBg = Color(0xFF1F1E1D),
        key = Color(0xFF30302E),
        keyEdge = Color(0xFF3E3E3A),
        keyShadow = Color(0xFF141413),
        keyText = Color(0xFFF5F4EF),
        fnKey = Color(0xFF282826),
        fnText = Color(0xFFC9C6BC),
        hint = Color(0xFF7E7A70),
        accent = Color(0xFFD97757),
        onAccent = Color(0xFF231F1D),
        // The only theme whose chip lightens rather than darkens: rgba(255,255,255,0.07).
        chip = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.07f),
        chipText = Color(0xFFF5F4EF),
        chipActive = Color(0xFFD97757),
        onChipActive = Color(0xFF231F1D),
    )

    /**
     * The five themes the picker offers, in the doc's own T1–T5 order. Oat is absent deliberately
     * — see [Oat].
     */
    val selectable: List<AtomTheme> = listOf(SageMist, Clay, Mauve, ClaudeLight, ClaudeDark)

    /** All six, including Oat. For tests, screenshot matrices (Phase 8.4) and deck chrome. */
    val all: List<AtomTheme> = listOf(SageMist, Clay, Mauve, Oat, ClaudeLight, ClaudeDark)

    /** What the keyboard renders when no theme has been chosen, matching the component's `FALLBACK`. */
    val Default = SageMist
}

/**
 * The ambient keyboard theme.
 *
 * `static` because a theme change is a wholesale re-theme of every surface — there is nothing to
 * gain from tracking reads individually, and the non-static variant would add per-read bookkeeping
 * on the keyboard's hot path.
 */
val LocalAtomTheme = staticCompositionLocalOf { AtomThemes.Default }

/** The keycap font family in force, per the *Look → Keyboard font: OS | Atom* setting. */
val LocalKeycapFont = staticCompositionLocalOf { KeycapFont.ATOM }

/** The key height in force, per the *Look → Keyboard height* setting. */
val LocalKeySize = staticCompositionLocalOf { KeySize.Default }

/**
 * Wraps keyboard content in a theme, a keycap font and a size scale. Every keyboard surface should
 * sit inside one of these; nothing below it should read a colour or a dimension from anywhere else.
 */
@Composable
fun AtomKeyboardTheme(
    theme: AtomTheme = AtomThemes.Default,
    keySize: KeySize = KeySize.Default,
    keycapFont: KeycapFont = KeycapFont.ATOM,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAtomTheme provides theme,
        LocalKeySize provides keySize,
        LocalKeycapFont provides keycapFont,
        content = content,
    )
}

/** Shorthand for the ambient theme, so call sites read `AtomKeyboard.theme.accent`. */
object AtomKeyboard {
    val theme: AtomTheme
        @Composable @ReadOnlyComposable get() = LocalAtomTheme.current

    val keySize: KeySize
        @Composable @ReadOnlyComposable get() = LocalKeySize.current

    val keycapFont: KeycapFont
        @Composable @ReadOnlyComposable get() = LocalKeycapFont.current
}
