package com.musa.atomkeys.engine

/**
 * The keyboard's structure: which keys exist, in what order, at what flex weight, and what each one
 * does. Transcribed from `renderVals()` in `AtomKeys.dc.html`.
 *
 * This lives in `:engine`, with no Compose types anywhere in it, for two reasons:
 *
 *  1. It is the input to [KeyboardGeometry], which spike 0.3 established must resolve taps
 *     **arithmetically from the row/weight model** rather than by scanning laid-out bounds. The
 *     spike's bounds-scan approach committed `qtpahlal` for `q t p a h l z m` — row-3 taps resolved
 *     to the row-2 keys above them.
 *  2. Phase 6.2's correction scorer needs key centres to weight substitutions by key proximity. It
 *     is a JVM-testable component, so its geometry source must be too.
 *
 * The renderer in `:ime` maps [RowItem.Key.weight] onto `Modifier.weight` and reads
 * [RowItem.Key.kind] to pick a keycap style; it invents nothing of its own.
 */

/** A keycap's visual family, from the design doc's `keyStyle(kind)` switch. */
enum class KeyKind {
    /** Letters, symbols, the spacebar and `.` — `key` fill, `keyEdge` border. */
    KEY,

    /** Shift, backspace, `123`/`ABC`, `#+=`, the special key, `AC` — `fnKey` fill. */
    FN,

    /** The ✓ key, and shift when engaged — `accent` fill. */
    ACCENT,

    /** Emoji-layer keys — fully transparent, no border, no shadow. */
    EMOJI,
}

/**
 * Which key set is showing.
 *
 * [SYMBOLS_2] is an **extension beyond the design doc**, which draws only one symbols page and
 * leaves `#+=` with nowhere to go. Confirmed decision: `#+=` opens a second page rather than being
 * a dead key.
 */
enum class Layer { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI }

/**
 * Shift as a **four**-state machine, per AOSP `KeyboardState.java` (spike 0.7) — even though only
 * **three** of them render differently.
 *
 * The distinction that matters is [MANUAL] vs [AUTOMATIC]: shift the user pressed, versus shift
 * auto-capitalization turned on. Backspacing over an auto-capitalized letter should undo the
 * automatic shift; backspacing under a manual shift must not silently discard the user's intent.
 * That behaviour is Phase 4.1's, but the states have to exist here or the renderer cannot be given
 * them later without a signature change.
 */
enum class ShiftState {
    OFF,
    MANUAL,
    AUTOMATIC,
    LOCKED,
    ;

    /** Whether letter keycaps show uppercase. */
    val isShifted: Boolean get() = this != OFF

    /** [MANUAL] and [AUTOMATIC] share one visual; [LOCKED] gets its own. */
    val isLocked: Boolean get() = this == LOCKED
}

/** The bottom row's configurable key, per *Look → Special key* in the settings. */
enum class SpecialKey { SMILEY, COMMA }

/**
 * What a key does when it fires. Phase 4 interprets these against the `Editor`; the layout model
 * only declares them, so that "what is this key" has exactly one answer and the renderer, the
 * hit-tester and the commit path cannot drift apart.
 */
sealed interface KeyAction {
    /** Emit literal text — letters, digits, symbols, `.`, and the comma variant of the special key. */
    data class Commit(val text: String) : KeyAction

    /** Switch key sets: `123`, `ABC`, `#+=`, and the smiley variant of the special key. */
    data class SwitchLayer(val layer: Layer) : KeyAction

    /** Cycle the shift state machine. */
    data object Shift : KeyAction

    /** Delete backwards, with repeat-on-hold (Phase 4.1). */
    data object Backspace : KeyAction

    /** The spacebar. Also the anchor for gesture G5's cursor control. */
    data object Space : KeyAction

    /** The ✓ key. Resolves against `EditorInfo.imeOptions` — search, send, next, or newline. */
    data object Action : KeyAction

    /** `AC` / `AC✕` — toggles auto-correction inline, per frame L8. */
    data object ToggleAutoCorrect : KeyAction
}

/** One entry in a row: either a real key or one of the mockup's invisible flex spacers. */
sealed interface RowItem {
    /** The flex weight this item claims, mapped onto `Modifier.weight` by the renderer. */
    val weight: Float

    data class Key(
        /**
         * Stable identity for press state, Compose `key()` calls and hit-test results. Positional
         * (`r2c4`) rather than label-derived, because labels repeat: `.` appears both in the
         * symbols punctuation row and on the bottom row.
         */
        val id: String,
        /** What the keycap shows. Already case-resolved — shift uppercases before it gets here. */
        val label: String,
        val kind: KeyKind,
        override val weight: Float,
        /** One of the [LabelScale] factors. */
        val labelScale: Double,
        val action: KeyAction,
        /** The numeric hint on letters row 1, or `null`. */
        val hint: String? = null,
    ) : RowItem

    /** An invisible flex spacer — the mockup's `spacer(0.5)` / `spacer(0.15)` / `spacer(0.2)`. */
    data class Spacer(override val weight: Float) : RowItem
}

/** A row of the keyboard grid. */
sealed interface Row {
    data class Keys(val items: List<RowItem>) : Row

    /**
     * The shimmering middle rule between letters rows 2 and 3. Occupies vertical space in the grid,
     * holds no keys, and appears on the letters layer only.
     */
    data object MiddleRule : Row
}

/**
 * Everything that changes which keys are on screen. Phase 5's settings and Phase 4's input state
 * both feed this; nothing else does.
 */
data class KeyboardConfig(
    val layer: Layer = Layer.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
    /** *Look → Display middle row.* */
    val middleRule: Boolean = true,
    /** *Look → Minimal mode* (frame L6) — hides the bottom row. */
    val minimal: Boolean = false,
    /** The permanent numbers row (frame L5). */
    val numbersRow: Boolean = false,
    /** *Typing → Longpress for numbers* shows hints on letters row 1. */
    val keyHints: Boolean = true,
    val specialKey: SpecialKey = SpecialKey.SMILEY,
    /** Drives the `AC` / `AC✕` label (frame L8). */
    val autoCorrect: Boolean = true,
    /** The spacebar's brand label. */
    val spaceLabel: String = "atom",
) {
    /**
     * Frame L5 turns hints off when the numbers row is on: the row **replaces** the per-key hints
     * rather than doubling up on them. Derived here so no caller has to remember the interaction.
     */
    val effectiveKeyHints: Boolean get() = keyHints && !numbersRow
}

object KeyboardLayout {

    // --- Key sets, verbatim from AtomKeys.dc.html --------------------------------------------

    private val LETTERS = listOf(
        "qwertyuiop".map { it.toString() },
        "asdfghjkl".map { it.toString() },
        "zxcvbnm".map { it.toString() },
    )

    private val NUM_HINTS = "1234567890".map { it.toString() }

    private val SYMBOLS_1 = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'"),
    )

    /**
     * The second symbols page. **Not from the design doc** — it is the content behind the confirmed
     * `#+=` decision, chosen to cover what page one omits: brackets, math, currency and the
     * typographic marks a keyboard is expected to reach.
     */
    private val SYMBOLS_2 = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"),
        listOf(".", ",", "?", "!", "'"),
    )

    private val EMOJI = listOf(
        listOf("😊", "😂", "😍", "🤔", "😴", "👏", "🙌", "👍"),
        listOf("✨", "🔥", "🌿", "🌙", "☕", "🍓", "🎧", "🍋"),
        listOf("❤️", "💜", "🎉", "💡", "⭐", "🌸", "🍀", "📌"),
    )

    private const val SHIFT_GLYPH = "⇧"
    private const val SHIFT_LOCK_GLYPH = "⇪"
    private const val BACKSPACE_GLYPH = "⌫"
    private const val SMILEY_GLYPH = "☺"
    private const val ACTION_GLYPH = "✓"

    /**
     * Builds the full row structure for a configuration.
     *
     * Pure and cheap — call it whenever config changes rather than caching it. The result is data;
     * it holds no Compose state and no geometry.
     */
    fun rows(config: KeyboardConfig): List<Row> {
        val rows = mutableListOf<Row>()

        fun keyRow(build: RowBuilder.() -> Unit) {
            rows += Row.Keys(RowBuilder().apply(build).items)
        }

        // `if (numbersRow && layer !== 'emoji')` — the emoji layer never carries it.
        if (config.numbersRow && config.layer != Layer.EMOJI) {
            keyRow {
                NUM_HINTS.forEach { digit ->
                    key(digit, KeyKind.FN, 1f, LabelScale.NUMBERS_ROW, KeyAction.Commit(digit))
                }
            }
        }

        when (config.layer) {
            Layer.EMOJI -> EMOJI.forEach { emojiRow ->
                keyRow {
                    emojiRow.forEach { glyph ->
                        key(glyph, KeyKind.EMOJI, 1f, LabelScale.EMOJI, KeyAction.Commit(glyph))
                    }
                }
            }

            // `#+=` advances to page two; `123` on page two comes back. `ABC` on the bottom row
            // leaves symbols entirely, from either page.
            Layer.SYMBOLS_1 -> symbolRows(SYMBOLS_1, "#+=", Layer.SYMBOLS_2).forEach(::keyRow)
            Layer.SYMBOLS_2 -> symbolRows(SYMBOLS_2, "123", Layer.SYMBOLS_1).forEach(::keyRow)

            Layer.LETTERS -> {
                val shifted = config.shift.isShifted

                // Row 1: ten keys at weight 1, each with a numeric hint.
                keyRow {
                    LETTERS[0].forEachIndexed { i, letter ->
                        letterKey(letter, shifted, hint = NUM_HINTS[i].takeIf { config.effectiveKeyHints })
                    }
                }

                // Row 2: spacer(0.5) + nine keys + spacer(0.5).
                keyRow {
                    spacer(0.5f)
                    LETTERS[1].forEach { letterKey(it, shifted) }
                    spacer(0.5f)
                }

                if (config.middleRule) rows += Row.MiddleRule

                // Row 3: shift(1.5) + spacer(0.15) + seven keys + spacer(0.15) + backspace(1.5).
                keyRow {
                    key(
                        label = if (config.shift.isLocked) SHIFT_LOCK_GLYPH else SHIFT_GLYPH,
                        // Three visuals over four states: OFF is an fn key, MANUAL and AUTOMATIC
                        // share the accent-filled `⇧`, LOCKED gets the accent-filled `⇪`. The
                        // mockup only draws two (it has a plain `shift` boolean); the roadmap's
                        // 3.2 asks for three, and this is the superset consistent with both.
                        kind = if (shifted) KeyKind.ACCENT else KeyKind.FN,
                        weight = 1.5f,
                        labelScale = LabelScale.SHIFT,
                        action = KeyAction.Shift,
                    )
                    spacer(0.15f)
                    LETTERS[2].forEach { letterKey(it, shifted) }
                    spacer(0.15f)
                    backspace()
                }
            }
        }

        // `if (!minimal)` — frame L6 drops the bottom row entirely, on every layer.
        if (!config.minimal) {
            keyRow {
                val toLetters = config.layer != Layer.LETTERS
                key(
                    label = if (toLetters) "ABC" else "123",
                    kind = KeyKind.FN,
                    weight = 1.3f,
                    labelScale = LabelScale.LAYER_TOGGLE,
                    action = KeyAction.SwitchLayer(if (toLetters) Layer.LETTERS else Layer.SYMBOLS_1),
                )
                when (config.specialKey) {
                    // Confirmed decision: `☺` opens the emoji layer rather than typing U+263A.
                    // Nothing else in the design reaches L4.
                    SpecialKey.SMILEY -> key(
                        SMILEY_GLYPH, KeyKind.FN, 1f, LabelScale.LETTER,
                        KeyAction.SwitchLayer(Layer.EMOJI),
                    )
                    SpecialKey.COMMA -> key(
                        ",", KeyKind.FN, 1f, LabelScale.LETTER, KeyAction.Commit(","),
                    )
                }
                key(config.spaceLabel, KeyKind.KEY, 4.2f, LabelScale.SPACEBAR, KeyAction.Space)
                key(
                    label = if (config.autoCorrect) "AC" else "AC✕",
                    kind = KeyKind.FN,
                    weight = 1f,
                    labelScale = LabelScale.AUTOCORRECT,
                    action = KeyAction.ToggleAutoCorrect,
                )
                key(".", KeyKind.KEY, 1f, LabelScale.LETTER, KeyAction.Commit("."))
                key(ACTION_GLYPH, KeyKind.ACCENT, 1.5f, LabelScale.LETTER, KeyAction.Action)
            }
        }

        return rows.withPositionalIds()
    }

    /**
     * Assigns `r{row}c{column}` ids in one pass over the finished structure.
     *
     * Done afterwards rather than during construction so no builder has to thread a row counter
     * through nested helpers — and so ids are guaranteed unique by construction rather than by
     * every call site remembering to increment.
     */
    private fun List<Row>.withPositionalIds(): List<Row> = mapIndexed { rowIndex, row ->
        when (row) {
            is Row.MiddleRule -> row
            is Row.Keys -> Row.Keys(
                row.items.mapIndexed { column, item ->
                    when (item) {
                        is RowItem.Spacer -> item
                        is RowItem.Key -> item.copy(id = "r${rowIndex}c$column")
                    }
                },
            )
        }
    }

    /**
     * Both symbols pages share a shape: two full rows, then
     * `toggle(1.5) + spacer(0.2) + five punctuation + spacer(0.2) + backspace(1.5)`.
     */
    private fun symbolRows(
        glyphs: List<List<String>>,
        toggleLabel: String,
        toggleTarget: Layer,
    ): List<RowBuilder.() -> Unit> = listOf(
        { glyphs[0].forEach { key(it, KeyKind.KEY, 1f, LabelScale.LETTER, KeyAction.Commit(it)) } },
        { glyphs[1].forEach { key(it, KeyKind.KEY, 1f, LabelScale.LETTER, KeyAction.Commit(it)) } },
        {
            key(
                toggleLabel, KeyKind.FN, 1.5f, LabelScale.LAYER_TOGGLE,
                KeyAction.SwitchLayer(toggleTarget),
            )
            spacer(0.2f)
            glyphs[2].forEach { key(it, KeyKind.KEY, 1f, LabelScale.LETTER, KeyAction.Commit(it)) }
            spacer(0.2f)
            backspace()
        },
    )

    /** Accumulates one row. Ids are filled in later by [withPositionalIds]. */
    class RowBuilder {
        val items = mutableListOf<RowItem>()

        fun key(
            label: String,
            kind: KeyKind,
            weight: Float,
            labelScale: Double,
            action: KeyAction,
            hint: String? = null,
        ) {
            items += RowItem.Key(
                id = "",
                label = label,
                kind = kind,
                weight = weight,
                labelScale = labelScale,
                action = action,
                hint = hint,
            )
        }

        fun spacer(weight: Float) {
            items += RowItem.Spacer(weight)
        }

        /** A letter key, case-resolved against the shift state. Always commits **lowercase-source**
         *  text uppercased to match the cap, so `A` commits `A`. */
        fun letterKey(letter: String, shifted: Boolean, hint: String? = null) {
            val text = if (shifted) letter.uppercase() else letter
            key(text, KeyKind.KEY, 1f, LabelScale.LETTER, KeyAction.Commit(text), hint)
        }

        fun backspace() =
            key(BACKSPACE_GLYPH, KeyKind.FN, 1.5f, LabelScale.BACKSPACE, KeyAction.Backspace)
    }
}
