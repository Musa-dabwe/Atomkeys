package com.musa.atomkeys.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expected values are the design doc's formulas evaluated at all four heights, cross-checked
 * against JavaScript `Math.round` semantics (half-up) so a Kotlin rounding difference would show up
 * here rather than as a one-pixel drift on device.
 *
 * Written as full tables rather than by re-applying the formula: a test that recomputes the
 * implementation proves nothing.
 */
class KeyMetricsTest {

    private val allSizes = listOf(KeySize.S, KeySize.M, KeySize.L, KeySize.XL)

    @Test
    fun `heights match the SIZES table`() {
        assertEquals(50, KeySize.S.heightDp)
        assertEquals(56, KeySize.M.heightDp)
        assertEquals(62, KeySize.L.heightDp)
        assertEquals(68, KeySize.XL.heightDp)
    }

    @Test
    fun `default height is L, as the design doc and Phase 5 defaults both specify`() {
        assertEquals(KeySize.L, KeySize.Default)
    }

    @Test
    fun `gap is round(h * 0_1)`() {
        assertEquals(listOf(5, 6, 6, 7), allSizes.map { it.gapDp })
    }

    @Test
    fun `corner radius is round(h * 0_18)`() {
        assertEquals(listOf(9, 10, 11, 12), allSizes.map { it.cornerRadiusDp })
    }

    @Test
    fun `middle rule inset is round(h * 0_5)`() {
        assertEquals(listOf(25, 28, 31, 34), allSizes.map { it.middleRuleInsetDp })
    }

    @Test
    fun `hint insets are round(h * 0_1) from the top and round(h * 0_11) from the right`() {
        assertEquals(listOf(5, 6, 6, 7), allSizes.map { it.hintTopDp })
        assertEquals(listOf(6, 6, 7, 7), allSizes.map { it.hintRightDp })
    }

    @Test
    fun `hint size is round(h * 0_17) floored at 9sp`() {
        assertEquals(listOf(9, 10, 11, 12), allSizes.map { it.hintSizeSp })
    }

    @Test
    fun `hint size never falls below the 9sp floor`() {
        // round(50 * 0.17) is 8.5 → 9 by half-up rounding, so the floor and the formula agree at S
        // and the floor never binds at any shipping height. It is kept because the formula is the
        // source's, and a future smaller height would need it.
        assertTrue(allSizes.all { it.hintSizeSp >= 9 })
    }

    @Test
    fun `press bubble sits h + 6 above the keycap`() {
        assertEquals(listOf(56, 62, 68, 74), allSizes.map { it.pressBubbleOffsetDp })
    }

    @Test
    fun `wrap padding is 8dp top, gap horizontal, gap + 4 bottom`() {
        assertTrue(allSizes.all { it.wrapPaddingTopDp == 8 })
        assertEquals(listOf(5, 6, 6, 7), allSizes.map { it.wrapPaddingHorizontalDp })
        assertEquals(listOf(9, 10, 10, 11), allSizes.map { it.wrapPaddingBottomDp })
    }

    @Test
    fun `letter labels are round(h * 0_4)`() {
        assertEquals(listOf(20, 22, 25, 27), allSizes.map { it.labelSizeSp(LabelScale.LETTER) })
    }

    @Test
    fun `layer toggle labels are round(h * 0_28)`() {
        assertEquals(listOf(14, 16, 17, 19), allSizes.map { it.labelSizeSp(LabelScale.LAYER_TOGGLE) })
    }

    @Test
    fun `numbers row labels are round(h * 0_32)`() {
        assertEquals(listOf(16, 18, 20, 22), allSizes.map { it.labelSizeSp(LabelScale.NUMBERS_ROW) })
    }

    @Test
    fun `backspace labels are round(h * 0_34)`() {
        assertEquals(listOf(17, 19, 21, 23), allSizes.map { it.labelSizeSp(LabelScale.BACKSPACE) })
    }

    @Test
    fun `shift labels are round(h * 0_36)`() {
        assertEquals(listOf(18, 20, 22, 24), allSizes.map { it.labelSizeSp(LabelScale.SHIFT) })
    }

    @Test
    fun `spacebar and autocorrect labels are round(h * 0_26)`() {
        assertEquals(listOf(13, 15, 16, 18), allSizes.map { it.labelSizeSp(LabelScale.SPACEBAR) })
        assertEquals(
            allSizes.map { it.labelSizeSp(LabelScale.SPACEBAR) },
            allSizes.map { it.labelSizeSp(LabelScale.AUTOCORRECT) },
        )
    }

    @Test
    fun `emoji labels are round(h * 0_52)`() {
        assertEquals(listOf(26, 29, 32, 35), allSizes.map { it.labelSizeSp(LabelScale.EMOJI) })
    }

    @Test
    fun `every derived value grows monotonically with height`() {
        // Not from the doc, but a property the whole scale depends on: picking a bigger keyboard
        // must never make any part of it smaller.
        val derived: List<(KeySize) -> Int> = listOf(
            { it.heightDp }, { it.gapDp }, { it.cornerRadiusDp }, { it.hintSizeSp },
            { it.middleRuleInsetDp }, { it.pressBubbleOffsetDp },
            { it.labelSizeSp(LabelScale.LETTER) }, { it.labelSizeSp(LabelScale.EMOJI) },
        )
        for (f in derived) {
            val values = allSizes.map(f)
            assertEquals(values, values.sorted())
        }
    }
}
