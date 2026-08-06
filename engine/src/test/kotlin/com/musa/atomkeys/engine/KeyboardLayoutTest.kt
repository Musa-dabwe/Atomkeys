package com.musa.atomkeys.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row composition asserted against the tables in `AtomKeys.dc.html`, weight by weight.
 *
 * These are transcription tests: their value is catching a wrong weight or a missing spacer, which
 * is invisible on screen until you hold the mockup next to it.
 */
class KeyboardLayoutTest {

    private fun rowsOf(config: KeyboardConfig) = KeyboardLayout.rows(config)

    private fun keyRows(config: KeyboardConfig) =
        rowsOf(config).filterIsInstance<Row.Keys>()

    private fun labels(row: Row.Keys) =
        row.items.filterIsInstance<RowItem.Key>().map { it.label }

    private fun weights(row: Row.Keys) = row.items.map { it.weight }

    // --- Letters layer -------------------------------------------------------------------------

    @Test
    fun `letters row 1 is ten keys at weight 1 with numeric hints`() {
        val row = keyRows(KeyboardConfig())[0]
        assertEquals("qwertyuiop".map { it.toString() }, labels(row))
        assertEquals(List(10) { 1f }, weights(row))
        assertEquals(
            "1234567890".map { it.toString() },
            row.items.filterIsInstance<RowItem.Key>().map { it.hint },
        )
    }

    @Test
    fun `letters row 2 is a half-weight spacer, nine keys, then another`() {
        val row = keyRows(KeyboardConfig())[1]
        assertEquals("asdfghjkl".map { it.toString() }, labels(row))
        assertEquals(
            listOf(0.5f) + List(9) { 1f } + listOf(0.5f),
            weights(row),
        )
    }

    @Test
    fun `letters row 3 is shift, spacer, seven keys, spacer, backspace`() {
        val row = keyRows(KeyboardConfig())[2]
        assertEquals(listOf("⇧") + "zxcvbnm".map { it.toString() } + listOf("⌫"), labels(row))
        assertEquals(
            listOf(1.5f, 0.15f) + List(7) { 1f } + listOf(0.15f, 1.5f),
            weights(row),
        )
    }

    @Test
    fun `bottom row matches the doc's weights`() {
        val row = keyRows(KeyboardConfig()).last()
        assertEquals(listOf("123", "☺", "atom", "AC", ".", "✓"), labels(row))
        assertEquals(listOf(1.3f, 1f, 4.2f, 1f, 1f, 1.5f), weights(row))
    }

    // --- Shift ---------------------------------------------------------------------------------

    @Test
    fun `shift uppercases every letter keycap and what it commits`() {
        val row = keyRows(KeyboardConfig(shift = ShiftState.MANUAL))[0]
        assertEquals("QWERTYUIOP".map { it.toString() }, labels(row))
        val q = row.items.filterIsInstance<RowItem.Key>().first()
        assertEquals(KeyAction.Commit("Q"), q.action)
    }

    @Test
    fun `four shift states render as three visuals`() {
        fun shiftKey(state: ShiftState) =
            keyRows(KeyboardConfig(shift = state))[2].items.filterIsInstance<RowItem.Key>().first()

        // OFF: an ordinary fn key.
        assertEquals(KeyKind.FN to "⇧", shiftKey(ShiftState.OFF).let { it.kind to it.label })
        // MANUAL and AUTOMATIC are distinct states that deliberately look identical (spike 0.7).
        assertEquals(KeyKind.ACCENT to "⇧", shiftKey(ShiftState.MANUAL).let { it.kind to it.label })
        assertEquals(KeyKind.ACCENT to "⇧", shiftKey(ShiftState.AUTOMATIC).let { it.kind to it.label })
        // LOCKED gets its own glyph.
        assertEquals(KeyKind.ACCENT to "⇪", shiftKey(ShiftState.LOCKED).let { it.kind to it.label })
    }

    // --- Layers --------------------------------------------------------------------------------

    @Test
    fun `symbols page one matches the doc and its toggle opens page two`() {
        val rows = keyRows(KeyboardConfig(layer = Layer.SYMBOLS_1))
        assertEquals("1234567890".map { it.toString() }, labels(rows[0]))
        assertEquals(listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""), labels(rows[1]))
        assertEquals(listOf("#+=", ".", ",", "?", "!", "'", "⌫"), labels(rows[2]))
        assertEquals(
            listOf(1.5f, 0.2f) + List(5) { 1f } + listOf(0.2f, 1.5f),
            weights(rows[2]),
        )

        val toggle = rows[2].items.filterIsInstance<RowItem.Key>().first()
        assertEquals(KeyAction.SwitchLayer(Layer.SYMBOLS_2), toggle.action)
    }

    @Test
    fun `symbols page two mirrors page one's shape and toggles back`() {
        val one = keyRows(KeyboardConfig(layer = Layer.SYMBOLS_1))
        val two = keyRows(KeyboardConfig(layer = Layer.SYMBOLS_2))

        // The extension page must not drift structurally from the page the doc actually draws.
        assertEquals(one.size, two.size)
        one.indices.forEach { assertEquals(weights(one[it]), weights(two[it])) }

        val toggle = two[2].items.filterIsInstance<RowItem.Key>().first()
        assertEquals("123", toggle.label)
        assertEquals(KeyAction.SwitchLayer(Layer.SYMBOLS_1), toggle.action)
    }

    @Test
    fun `ABC returns to letters from either symbols page and from emoji`() {
        for (layer in listOf(Layer.SYMBOLS_1, Layer.SYMBOLS_2, Layer.EMOJI)) {
            val first = keyRows(KeyboardConfig(layer = layer)).last()
                .items.filterIsInstance<RowItem.Key>().first()
            assertEquals("ABC", first.label)
            assertEquals(KeyAction.SwitchLayer(Layer.LETTERS), first.action)
        }
    }

    @Test
    fun `emoji layer is three rows of eight transparent keys`() {
        val rows = keyRows(KeyboardConfig(layer = Layer.EMOJI))
        val emojiRows = rows.dropLast(1) // the bottom row is not emoji
        assertEquals(3, emojiRows.size)
        emojiRows.forEach { row ->
            assertEquals(8, labels(row).size)
            assertTrue(row.items.filterIsInstance<RowItem.Key>().all { it.kind == KeyKind.EMOJI })
            assertEquals(List(8) { 1f }, weights(row))
        }
    }

    @Test
    fun `the special key opens emoji as a smiley and commits a comma otherwise`() {
        fun special(setting: SpecialKey) =
            keyRows(KeyboardConfig(specialKey = setting)).last()
                .items.filterIsInstance<RowItem.Key>()[1]

        assertEquals(KeyAction.SwitchLayer(Layer.EMOJI), special(SpecialKey.SMILEY).action)
        assertEquals("☺", special(SpecialKey.SMILEY).label)
        assertEquals(KeyAction.Commit(","), special(SpecialKey.COMMA).action)
        assertEquals(",", special(SpecialKey.COMMA).label)
    }

    // --- Optional chrome -----------------------------------------------------------------------

    @Test
    fun `the middle rule appears only on the letters layer, between rows 2 and 3`() {
        val rows = rowsOf(KeyboardConfig())
        assertEquals(Row.MiddleRule, rows[2])

        for (layer in listOf(Layer.SYMBOLS_1, Layer.SYMBOLS_2, Layer.EMOJI)) {
            assertTrue(rowsOf(KeyboardConfig(layer = layer)).none { it is Row.MiddleRule })
        }
    }

    @Test
    fun `display-middle-row off removes the rule`() {
        assertTrue(rowsOf(KeyboardConfig(middleRule = false)).none { it is Row.MiddleRule })
    }

    @Test
    fun `minimal mode drops the bottom row on every layer`() {
        for (layer in Layer.entries) {
            val rows = keyRows(KeyboardConfig(layer = layer, minimal = true))
            assertTrue(
                "bottom row still present on $layer",
                rows.none { labels(it).contains("✓") },
            )
        }
    }

    @Test
    fun `the numbers row is prepended, except on the emoji layer`() {
        val letters = keyRows(KeyboardConfig(numbersRow = true))
        assertEquals("1234567890".map { it.toString() }, labels(letters[0]))
        assertTrue(letters[0].items.filterIsInstance<RowItem.Key>().all { it.kind == KeyKind.FN })

        val emoji = keyRows(KeyboardConfig(layer = Layer.EMOJI, numbersRow = true))
        assertEquals(8, labels(emoji[0]).size) // still the first emoji row
    }

    @Test
    fun `the numbers row replaces the per-key hints`() {
        // Frame L5: the row and the hints are the same information, so they never both show.
        val row = keyRows(KeyboardConfig(numbersRow = true))[1] // letters row 1, after the numbers
        assertTrue(row.items.filterIsInstance<RowItem.Key>().all { it.hint == null })
        assertTrue(KeyboardConfig(numbersRow = true).effectiveKeyHints.not())
    }

    @Test
    fun `hints can be turned off independently`() {
        val row = keyRows(KeyboardConfig(keyHints = false))[0]
        assertTrue(row.items.filterIsInstance<RowItem.Key>().all { it.hint == null })
    }

    @Test
    fun `the AC key reflects the auto-correct state`() {
        fun ac(on: Boolean) = keyRows(KeyboardConfig(autoCorrect = on)).last()
            .items.filterIsInstance<RowItem.Key>()[3]
        assertEquals("AC", ac(true).label)
        assertEquals("AC✕", ac(false).label)      // frame L8
        assertEquals(KeyAction.ToggleAutoCorrect, ac(true).action)
    }

    @Test
    fun `the spacebar carries the brand label and the space action`() {
        val space = keyRows(KeyboardConfig()).last().items.filterIsInstance<RowItem.Key>()[2]
        assertEquals("atom", space.label)
        assertEquals(4.2f, space.weight, 0f)
        assertEquals(KeyAction.Space, space.action)
    }

    // --- Identity ------------------------------------------------------------------------------

    @Test
    fun `every key id is unique within a layout, across every configuration`() {
        val configs = Layer.entries.flatMap { layer ->
            listOf(
                KeyboardConfig(layer = layer),
                KeyboardConfig(layer = layer, numbersRow = true),
                KeyboardConfig(layer = layer, minimal = true),
                KeyboardConfig(layer = layer, shift = ShiftState.LOCKED),
                KeyboardConfig(layer = layer, middleRule = false),
            )
        }
        for (config in configs) {
            val ids = keyRows(config).flatMap { it.items.filterIsInstance<RowItem.Key>() }.map { it.id }
            assertEquals("duplicate ids for $config", ids.size, ids.toSet().size)
            assertTrue("blank id for $config", ids.none { it.isBlank() })
        }
    }
}
