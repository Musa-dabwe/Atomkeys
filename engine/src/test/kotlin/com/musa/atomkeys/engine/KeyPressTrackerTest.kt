package com.musa.atomkeys.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-pointer press state machine: fire-on-release, slide-off cancel, noise rejection, and the
 * Phase 0.3 "second finger must not cancel the first" property.
 */
class KeyPressTrackerTest {

    private val density = 3f
    private val width = 1080f

    private val geometry by lazy {
        KeyboardGeometry.resolve(
            rows = KeyboardLayout.rows(KeyboardConfig()),
            keySize = KeySize.L,
            widthPx = width,
            densityPxPerDp = density,
        )
    }

    /** A tracker backed by the real geometry: keyAt first, nearest as gap fallback. */
    private fun tracker() = KeyPressTracker(
        resolveKey = { x, y -> geometry.keyAt(x, y) ?: geometry.nearestKeyTo(x, y) },
        densityPxPerDp = density,
    )

    private fun rectFor(label: String): KeyRect = geometry.keys.first { it.key.label == label }

    /** The stable positional id behind a keycap label, for asserting [KeyPressTracker.pressedKeyIds]. */
    private fun idFor(label: String): String = rectFor(label).key.id

    /** The action committed by tapping the centre of the key with the given label. */
    private fun tap(tracker: KeyPressTracker, label: String, timeMillis: Long = 1000L): KeyAction? {
        val r = rectFor(label)
        tracker.onDown(0L, r.centerX, r.centerY, timeMillis)
        return tracker.onUp(0L, r.centerX, r.centerY, timeMillis + 60L)
    }

    // --- Fire on release ----------------------------------------------------------------------

    @Test
    fun `a clean tap on a key fires its action on release`() {
        val t = tracker()
        val action = tap(t, "q")
        assertEquals(KeyAction.Commit("q"), action)
    }

    @Test
    fun `the action is what the resolved key does, not the label`() {
        val t = tracker()
        assertEquals(KeyAction.Commit("z"), tap(t, "z"))
        assertEquals(KeyAction.Backspace, tap(t, "⌫"))
        assertEquals(KeyAction.Space, tap(t, "atom"))
    }

    @Test
    fun `a down on a gap resolves to the nearest key`() {
        val t = tracker()
        val q = rectFor("q")
        val gapX = q.right + (KeySize.L.gapDp * density) / 2f
        val pressed = t.onDown(0L, gapX, q.centerY, 1000L)
        assertTrue("gap tap should still land on a key", pressed)
        val action = t.onUp(0L, gapX, q.centerY, 1060L)
        assertTrue("gap tap should commit q or w", action == KeyAction.Commit("q") || action == KeyAction.Commit("w"))
    }

    // --- Slide-off cancel ---------------------------------------------------------------------

    @Test
    fun `sliding off a key before lift cancels the press`() {
        val t = tracker()
        val q = rectFor("q")
        val w = rectFor("w")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        // Slide all the way over to w — well beyond hysteresis.
        t.onMove(0L, w.centerX, w.centerY)
        assertNull("a cancelled press must not fire", t.onUp(0L, w.centerX, w.centerY, 1100L))
        assertFalse("a cancelled press is not reported as pressed", "q" in t.pressedKeyIds)
    }

    @Test
    fun `small drift within hysteresis keeps the press alive`() {
        val t = tracker()
        val q = rectFor("q")
        // Drift right by less than half the hysteresis radius.
        val drift = TouchThresholds.HYSTERESIS_DP * density / 2f
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        t.onMove(0L, q.right + drift, q.centerY)
        assertEquals(idFor("q"), t.pressedKeyIds.single())
        assertEquals(KeyAction.Commit("q"), t.onUp(0L, q.right + drift, q.centerY, 1100L))
    }

    @Test
    fun `a press cancelled on move cannot be resurrected by moving back`() {
        val t = tracker()
        val q = rectFor("q")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        t.onMove(0L, q.centerX + 400f, q.centerY) // far past hysteresis
        t.onMove(0L, q.centerX, q.centerY)        // back onto the key
        assertNull(t.onUp(0L, q.centerX, q.centerY, 1100L))
    }

    // --- Noise rejection ----------------------------------------------------------------------

    @Test
    fun `a fast flick across a key is noise and does not fire`() {
        val t = tracker()
        val q = rectFor("q")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        // Travel far, lift fast — a graze, not a tap.
        t.onMove(0L, q.centerX + TouchThresholds.NOISE_DISTANCE_DP * density * 2f, q.centerY)
        assertNull(t.onUp(0L, q.centerX + TouchThresholds.NOISE_DISTANCE_DP * density * 2f, q.centerY, 1020L))
    }

    @Test
    fun `a fast tap with no travel is not noise`() {
        val t = tracker()
        val q = rectFor("q")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        assertEquals(KeyAction.Commit("q"), t.onUp(0L, q.centerX, q.centerY, 1010L)) // fast but no travel
    }

    // --- Per-pointer tracking -----------------------------------------------------------------

    @Test
    fun `a second pointer down does not cancel the first`() {
        val t = tracker()
        val q = rectFor("q")
        val w = rectFor("w")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        t.onDown(1L, w.centerX, w.centerY, 1010L)

        assertEquals(setOf(idFor("q"), idFor("w")), t.pressedKeyIds)

        // First finger lifts after the second went down — must still fire.
        assertEquals(KeyAction.Commit("q"), t.onUp(0L, q.centerX, q.centerY, 1100L))
        assertEquals(KeyAction.Commit("w"), t.onUp(1L, w.centerX, w.centerY, 1110L))
    }

    @Test
    fun `cancelling one pointer leaves the other pressed`() {
        val t = tracker()
        val q = rectFor("q")
        val w = rectFor("w")
        t.onDown(0L, q.centerX, q.centerY, 1000L)
        t.onDown(1L, w.centerX, w.centerY, 1010L)

        t.onCancel(0L)
        assertEquals(setOf(idFor("w")), t.pressedKeyIds)
        assertNull("cancelled pointer must not fire on a later lift", t.onUp(0L, q.centerX, q.centerY, 1100L))
        assertEquals(KeyAction.Commit("w"), t.onUp(1L, w.centerX, w.centerY, 1110L))
    }

    @Test
    fun `pressedKeyIds is empty when nothing is down`() {
        val t = tracker()
        assertTrue(t.pressedKeyIds.isEmpty())
        tap(t, "a")
        assertTrue(t.pressedKeyIds.isEmpty())
    }

    // --- Movement reports ---------------------------------------------------------------------

    @Test
    fun `moves without a down are ignored`() {
        val t = tracker()
        t.onMove(0L, 100f, 100f)
        t.onMove(7L, 200f, 200f)
        assertNull(t.onUp(0L, 100f, 100f, 1000L))
    }
}