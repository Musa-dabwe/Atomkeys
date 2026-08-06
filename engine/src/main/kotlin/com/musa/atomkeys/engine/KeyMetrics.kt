package com.musa.atomkeys.engine

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The keyboard's sizing scale, transcribed from `AtomKeys.dc.html`.
 *
 * The design derives *everything* from one number — the key height `h` — so this enum is the only
 * place a magnitude is written down. Gaps, corner radii and every label size fall out of it.
 *
 * This lives in `:engine` rather than `:design` deliberately: the math is the part worth testing,
 * and here it tests on the JVM in milliseconds without an emulator. `:design` wraps these plain
 * integers in `Dp` / `TextUnit`. The design doc's CSS pixels map 1:1 to dp and sp.
 *
 * Rounding is `Math.round` in the source, i.e. half-up. All values are positive, so Kotlin's
 * [roundToInt] (half away from zero) agrees on every input.
 */
enum class KeySize(
    /** `SIZES = { S: 50, M: 56, L: 62, XL: 68 }` — the height of one key row, in dp. */
    val heightDp: Int,
) {
    S(50),
    M(56),
    L(62),
    XL(68),
    ;

    /** `Math.round(h * 0.1)` — the gap between keys, and between rows. */
    val gapDp: Int get() = scaled(0.1)

    /** `Math.round(h * 0.18)` — keycap corner radius. */
    val cornerRadiusDp: Int get() = scaled(0.18)

    /** `max(9, Math.round(h * 0.17))` — the numeric hint on letters row 1. Floored at 9sp. */
    val hintSizeSp: Int get() = max(MIN_HINT_SP, scaled(0.17))

    /** `Math.round(h * 0.1)` — hint inset from the top of the keycap. */
    val hintTopDp: Int get() = scaled(0.1)

    /** `Math.round(h * 0.11)` — hint inset from the right of the keycap. */
    val hintRightDp: Int get() = scaled(0.11)

    /** `Math.round(h * 0.5)` — horizontal inset of the shimmering middle-row rule. */
    val middleRuleInsetDp: Int get() = scaled(0.5)

    /**
     * `bottom: h + 6` — how far above the keycap's own bottom edge the press bubble sits.
     * On the top row the bubble clears the keyboard entirely, which is what the reserved
     * headroom band from spike 0.2 exists for.
     */
    val pressBubbleOffsetDp: Int get() = heightDp + PRESS_BUBBLE_GAP_DP

    /** `padding: '8px ' + gap + 'px ' + (gap + 4) + 'px'` on the keyboard wrapper. */
    val wrapPaddingTopDp: Int get() = WRAP_PADDING_TOP_DP
    val wrapPaddingHorizontalDp: Int get() = gapDp
    val wrapPaddingBottomDp: Int get() = gapDp + WRAP_PADDING_BOTTOM_EXTRA_DP

    /**
     * `Math.round(h * scale)` — a keycap label size. Callers pass a [LabelScale] constant rather
     * than a bare number so every scale in the design has exactly one name.
     */
    fun labelSizeSp(scale: Double): Int = scaled(scale)

    private fun scaled(factor: Double): Int = (heightDp * factor).roundToInt()

    companion object {
        /** The design doc's default, and the shipping default per Phase 5.1. */
        val Default = L

        private const val MIN_HINT_SP = 9
        private const val PRESS_BUBBLE_GAP_DP = 6
        private const val WRAP_PADDING_TOP_DP = 8
        private const val WRAP_PADDING_BOTTOM_EXTRA_DP = 4
    }
}

/**
 * The label scale factors `AtomKeys.dc.html` passes to `labelStyle(kind, scale)`.
 *
 * These are per-key, not per-kind — the bottom row alone spans 0.26 to 0.4 — so they are named
 * after the key rather than after the key's visual kind.
 */
object LabelScale {
    /** Letters, symbols, the special (`☺`/`,`) key, `.` and `✓`. The source's `scale || 0.4` default. */
    const val LETTER = 0.4

    /** `123` / `ABC` layer toggle, and `#+=` on the symbols layer. */
    const val LAYER_TOGGLE = 0.28

    /** The optional permanent numbers row (frame L5). */
    const val NUMBERS_ROW = 0.32

    /** Backspace `⌫`. */
    const val BACKSPACE = 0.34

    /** Shift `⇧` / `⇪`. */
    const val SHIFT = 0.36

    /** The spacebar's `atom` brand label. */
    const val SPACEBAR = 0.26

    /** `AC` / `AC✕` auto-correct toggle (frame L8). */
    const val AUTOCORRECT = 0.26

    /** Emoji layer keys. */
    const val EMOJI = 0.52
}

/**
 * Fixed sizes the design doc states outright instead of deriving from `h`. They do **not** scale
 * with key height — the candidate strip is 48dp tall whether keys are S or XL.
 */
object AtomDimens {
    /** Candidate strip height, and the 40dp `⋯` / mic buttons that sit in it. */
    const val STRIP_HEIGHT_DP = 48
    const val STRIP_BUTTON_SIZE_DP = 40
    const val STRIP_BUTTON_RADIUS_DP = 12
    const val STRIP_ITEM_GAP_DP = 8
    const val STRIP_BOTTOM_MARGIN_DP = 2

    /** Candidate chips: `padding: '5px 14px', borderRadius: 16`, 15sp label. */
    const val CHIP_PADDING_HORIZONTAL_DP = 14
    const val CHIP_PADDING_VERTICAL_DP = 5
    const val CHIP_RADIUS_DP = 16
    const val CHIP_TEXT_SP = 15

    /** The `·`·`·` sub-dots under the active chip, indicating more candidates below. */
    const val SUB_DOTS_TEXT_SP = 11
    const val SUB_DOTS_LETTER_SPACING_SP = 2
    const val SUB_DOTS_OFFSET_DP = 9

    /** Press bubble: `width: 46, height: 54, borderRadius: 12`, 30sp label. */
    const val PRESS_BUBBLE_WIDTH_DP = 46
    const val PRESS_BUBBLE_HEIGHT_DP = 54
    const val PRESS_BUBBLE_RADIUS_DP = 12
    const val PRESS_BUBBLE_TEXT_SP = 30

    /**
     * Reserved headroom above the keyboard grid for the press bubble, gesture trail and (post-v1)
     * the extension deck. Spike 0.2's answer: reserve a band rather than overdraw with
     * `clipToBounds = false`, which is fragile inside an IME window.
     */
    const val OVERLAY_HEADROOM_DP = 56

    /** The middle-row rule: `height: 2.5, borderRadius: 2`, `opacity: 0.75`. */
    const val MIDDLE_RULE_HEIGHT_DP = 2.5f
    const val MIDDLE_RULE_RADIUS_DP = 2
    const val MIDDLE_RULE_VERTICAL_MARGIN_DP = 2
    const val MIDDLE_RULE_OPACITY = 0.75f

    /** Keycap chrome: `1px solid keyEdge` border over a `0 1.5px 0 keyShadow` hard shadow. */
    const val KEY_BORDER_WIDTH_DP = 1f
    const val KEY_SHADOW_HEIGHT_DP = 1.5f

    /** A pressed keycap draws at `transform: scale(0.95)` with its shadow removed. */
    const val PRESSED_SCALE = 0.95f

    /** Numeric hints render at `opacity: 0.55`. */
    const val HINT_OPACITY = 0.55f

    /** Fn-key labels carry `letterSpacing: 0.02em`. */
    const val FN_LETTER_SPACING_EM = 0.02f
}

/**
 * The `atomShimmer` animation on the middle-row rule:
 * `opacity 0.14 → 0.55 → 0.14 over 2.6s, ease-in-out, infinite`.
 *
 * Phase 8.5 requires this to stop when the keyboard is hidden. Spike 0.1's answer — recreate the
 * composition on each show — gives that for free, since the animation dies with the composition.
 */
object AtomShimmer {
    const val MIN_OPACITY = 0.14f
    const val MAX_OPACITY = 0.55f
    const val DURATION_MS = 2600
}
