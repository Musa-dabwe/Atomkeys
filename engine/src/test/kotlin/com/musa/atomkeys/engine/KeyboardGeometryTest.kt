package com.musa.atomkeys.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry resolution, and the regression test for the spike-0.3 hit-test bug.
 *
 * Dimensions here model the spike's own device: 1080 px wide at density 3.0 (dp ≈ px / 3).
 */
class KeyboardGeometryTest {

    private val density = 3f
    private val width = 1080f

    private fun geometryFor(config: KeyboardConfig = KeyboardConfig(), size: KeySize = KeySize.L) =
        KeyboardGeometry.resolve(
            rows = KeyboardLayout.rows(config),
            keySize = size,
            widthPx = width,
            densityPxPerDp = density,
        )

    // --- The bug that produced this class ------------------------------------------------------

    @Test
    fun `every key resolves to itself at its own centre`() {
        // The general form of the spike failure: a tap in the middle of a key must never resolve
        // to a different key. This alone would have caught `qtpahlal`.
        val geometry = geometryFor()
        for (rect in geometry.keys) {
            val hit = geometry.keyAt(rect.centerX, rect.centerY)
            assertNotNull("no key at the centre of '${rect.key.label}'", hit)
            assertEquals(
                "centre of '${rect.key.label}' resolved to '${hit!!.key.label}'",
                rect.key.id,
                hit.key.id,
            )
        }
    }

    @Test
    fun `the exact spike-0_3 failure no longer reproduces`() {
        // Spike 0.3 typed `q t p a h l z m` and got `qtpahlal`: the row-3 taps (z, m) resolved to
        // the row-2 keys above them (a, l). Replayed here against real geometry.
        val geometry = geometryFor()
        val typed = listOf("q", "t", "p", "a", "h", "l", "z", "m")

        val committed = typed.map { label ->
            val rect = geometry.keys.first { it.key.label == label }
            val hit = geometry.keyAt(rect.centerX, rect.centerY)!!
            (hit.key.action as KeyAction.Commit).text
        }

        assertEquals(typed, committed)
        assertEquals("qtpahlzm", committed.joinToString(""))
    }

    @Test
    fun `row 3 keys sit strictly below row 2 keys`() {
        // The structural reason the spike's scan mis-resolved: it never established this ordering.
        val geometry = geometryFor()
        val a = geometry.keys.first { it.key.label == "a" }
        val z = geometry.keys.first { it.key.label == "z" }
        assertTrue("row 3 must start below row 2's bottom edge", z.top >= a.bottom)
        assertNull("a point inside row 2 must not resolve to a row-3 key",
            geometry.keyAt(z.centerX, a.centerY)?.takeIf { it.key.label == "z" })
    }

    // --- Layout correctness --------------------------------------------------------------------

    @Test
    fun `a full-weight row spans the grid width exactly`() {
        val geometry = geometryFor()
        val rowTop = geometry.keys.first { it.key.label == "q" }.top
        val row = geometry.keys.filter { it.top == rowTop }.sortedBy { it.left }

        assertEquals(10, row.size)
        assertEquals(0f, row.first().left, 0.01f)
        assertEquals(width, row.last().right, 0.01f)
    }

    @Test
    fun `keys in a row are separated by exactly one gap and never overlap`() {
        val geometry = geometryFor()
        val gapPx = KeySize.L.gapDp * density
        val rowTop = geometry.keys.first { it.key.label == "q" }.top
        val row = geometry.keys.filter { it.top == rowTop }.sortedBy { it.left }

        row.zipWithNext { left, right ->
            assertEquals(gapPx, right.left - left.right, 0.01f)
        }
    }

    @Test
    fun `weights translate into proportional widths`() {
        val geometry = geometryFor()
        val space = geometry.keys.first { it.key.label == "atom" }   // weight 4.2
        val period = geometry.keys.first { it.top == space.top && it.key.label == "." } // weight 1
        assertEquals(4.2f, space.width / period.width, 0.01f)
    }

    @Test
    fun `spacers consume width without producing keys`() {
        val geometry = geometryFor()
        // Row 2 is spacer(0.5) + 9 keys + spacer(0.5), so it must not start at x = 0.
        val a = geometry.keys.first { it.key.label == "a" }
        assertTrue("row 2 should be inset by its leading spacer", a.left > 0f)

        val row = geometry.keys.filter { it.top == a.top }
        assertEquals(9, row.size)
    }

    @Test
    fun `the middle rule occupies vertical space without holding keys`() {
        val withRule = geometryFor(KeyboardConfig(middleRule = true))
        val without = geometryFor(KeyboardConfig(middleRule = false))

        assertEquals(withRule.keys.size, without.keys.size)
        assertTrue("the rule must push row 3 down", withRule.heightPx > without.heightPx)

        val ruleSpace = (
            AtomDimens.MIDDLE_RULE_HEIGHT_DP + 2 * AtomDimens.MIDDLE_RULE_VERTICAL_MARGIN_DP
            ) * density + KeySize.L.gapDp * density
        assertEquals(ruleSpace, withRule.heightPx - without.heightPx, 0.01f)
    }

    @Test
    fun `grid height accounts for every row and the gaps between them`() {
        val geometry = geometryFor(KeyboardConfig(middleRule = false))
        val rows = KeyboardLayout.rows(KeyboardConfig(middleRule = false)).size
        val expected = rows * KeySize.L.heightDp * density +
            (rows - 1) * KeySize.L.gapDp * density
        assertEquals(expected, geometry.heightPx, 0.01f)
    }

    @Test
    fun `geometry scales with key size`() {
        val small = geometryFor(size = KeySize.S)
        val large = geometryFor(size = KeySize.XL)
        assertTrue(large.heightPx > small.heightPx)
        // Width is fixed by the container, so only the vertical axis moves.
        assertEquals(small.widthPx, large.widthPx, 0f)
    }

    // --- Hit-testing behaviour -----------------------------------------------------------------

    @Test
    fun `a tap in a gap hits no key but still resolves to the nearest one`() {
        val geometry = geometryFor()
        val q = geometry.keys.first { it.key.label == "q" }
        val gapX = q.right + (KeySize.L.gapDp * density) / 2f

        assertNull("the gap itself contains no key", geometry.keyAt(gapX, q.centerY))
        val nearest = geometry.nearestKeyTo(gapX, q.centerY)!!
        assertTrue(
            "a gap tap should fall to one of its two neighbours",
            nearest.key.label == "q" || nearest.key.label == "w",
        )
    }

    @Test
    fun `keysNear returns adjacent keys for the Phase 6_2 proximity model`() {
        val geometry = geometryFor()
        val n = geometry.keys.first { it.key.label == "n" }
        // A tap on n's right edge should offer m as a plausible alternative.
        val near = geometry.keysNear(n.right, n.centerY, radiusPx = n.width * 1.2f)
        val labels = near.map { it.key.label }
        assertTrue("expected n among $labels", "n" in labels)
        assertTrue("expected m among $labels", "m" in labels)
    }

    @Test
    fun `rectFor round-trips a key id`() {
        val geometry = geometryFor()
        val q = geometry.keys.first { it.key.label == "q" }
        assertEquals(q, geometry.rectFor(q.key.id))
        assertNull(geometry.rectFor("nope"))
    }

    @Test
    fun `distanceTo is zero inside a key and grows outside it`() {
        val geometry = geometryFor()
        val q = geometry.keys.first { it.key.label == "q" }
        assertEquals(0f, q.distanceTo(q.centerX, q.centerY), 0f)
        assertTrue(q.distanceTo(q.right + 50f, q.centerY) > 0f)
        assertEquals(50f, q.distanceTo(q.right + 50f, q.centerY), 0.01f)
    }

    // --- Touch thresholds ----------------------------------------------------------------------

    @Test
    fun `noise rejection needs both a short touch and a long travel`() {
        val far = TouchThresholds.NOISE_DISTANCE_DP * density + 1f
        val near = TouchThresholds.NOISE_DISTANCE_DP * density - 1f

        assertTrue("fast and far is noise", TouchThresholds.isNoise(far, 20L, density))
        assertTrue("fast but short is a tap", !TouchThresholds.isNoise(near, 20L, density))
        assertTrue("far but slow is a deliberate slide", !TouchThresholds.isNoise(far, 200L, density))
    }

    @Test
    fun `hysteresis keeps a drifting press on its original key`() {
        val geometry = geometryFor()
        val q = geometry.keys.first { it.key.label == "q" }
        val justOutside = q.right + TouchThresholds.HYSTERESIS_DP * density / 2f
        val wellOutside = q.right + TouchThresholds.HYSTERESIS_DP * density * 2f

        assertTrue(TouchThresholds.withinHysteresis(q, justOutside, q.centerY, density))
        assertTrue(!TouchThresholds.withinHysteresis(q, wellOutside, q.centerY, density))
    }
}
