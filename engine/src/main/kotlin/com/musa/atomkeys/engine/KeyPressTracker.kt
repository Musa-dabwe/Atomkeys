package com.musa.atomkeys.engine

/**
 * The per-pointer press state machine behind the whole keyboard surface.
 *
 * Designed in spike 0.3 (`.planning/spikes/compose-in-ime.md`) and specced by roadmap 4.2/4.3:
 *
 *  - **Each pointer is tracked independently**, keyed by pointer id. A second finger down must never
 *    cancel the first — that was a Phase 0 open item, and it is the single property that decides
 *    whether two-thumb typing works.
 *  - A press **fires on release**, not on down. Roadmap 4.3's reconciliation: a tap that stays on one
 *    key fires; a pointer that slides off the key before lift is cancelled rather than knocked in.
 *    (Firing on release is also what lets the phase-0.3 latency budget measure the *down* time
 *    separately from the commit, which are different meters.)
 *  - **Slide-off cancels.** Once a pointer leaves its starting key beyond the hysteresis distance it
 *    stops counting as that key; lifting there commits nothing. This is the 4.2 slide-off rule.
 *  - **Noise rejection** (AOSP 12.6 dp / 40 ms, spike 0.7) suppresses palm brushes and grazes.
 *
 * The tracker is deliberately geometry-agnostic: it asks the caller to resolve a point to a key.
 * That keeps it a state machine over pure inputs — testable on the JVM without Compose, which is
 * the whole point of putting it in `:engine`.
 *
 * @param resolveKey answers "which key is under this point". Play for a tap landing in an inter-key
 *   gap: the composition passes [KeyboardGeometry.keyAt] first and [KeyboardGeometry.nearestKeyTo]
 *   as its fallback, since a gap tap is a mis-aimed key press, not a no-op.
 */
class KeyPressTracker(
    private val resolveKey: (x: Float, y: Float) -> KeyRect?,
    private val densityPxPerDp: Float,
) {
    /** One in-flight press, holding everything needed to decide its fate at lift. */
    private class Press(
        val key: RowItem.Key,
        val keyRect: KeyRect,
        val startX: Float,
        val startY: Float,
        val startTimeMillis: Long,
    ) {
        /** Euclidean distance travelled since down, for the noise-rejection test. */
        var travelPx: Float = 0f
        var lastX: Float = startX
        var lastY: Float = startY
        var cancelled: Boolean = false
    }

    private val presses = LinkedHashMap<Long, Press>()

    /** The keys currently held, in press order. Empty when nothing is down. */
    val pressedKeyIds: Set<String>
        get() = presses.values.asSequence().filterNot { it.cancelled }.map { it.key.id }.toSet()

    /** A pointer went down. Returns true if it landed on a key that can fire. */
    fun onDown(pointerId: Long, x: Float, y: Float, timeMillis: Long): Boolean {
        val rect = resolveKey(x, y) ?: return false
        presses[pointerId] = Press(rect.key, rect, x, y, timeMillis)
        return true
    }

    /**
     * A pointer moved while down. If it drifts outside its starting key's hysteresis region it is
     * cancelled — lifting there commits nothing.
     */
    fun onMove(pointerId: Long, x: Float, y: Float) {
        val press = presses[pointerId] ?: return
        press.travelPx += TouchThresholds.travel(x - press.lastX, y - press.lastY)
        press.lastX = x
        press.lastY = y
        if (press.cancelled) return
        if (!TouchThresholds.withinHysteresis(press.keyRect, x, y, densityPxPerDp)) {
            press.cancelled = true
        }
    }

    /**
     * A pointer lifted. Returns the action to fire, or null.
     *
     * Fires only when the press survived to lift: not cancelled, and not a palm graze per
     * [TouchThresholds]. It was already resolved at down, so a pointer is held to what it pressed
     * even if the finger lands in a gap and the fallback picks a neighbour.
     */
    fun onUp(pointerId: Long, x: Float, y: Float, timeMillis: Long): KeyAction? {
        val press = presses.remove(pointerId) ?: return null
        if (press.cancelled) return null
        val durationMs = timeMillis - press.startTimeMillis
        if (TouchThresholds.isNoise(press.travelPx, durationMs, densityPxPerDp)) return null
        return press.key.action
    }

    /** The pointer was cancelled by the system (window lost focus, etc.). */
    fun onCancel(pointerId: Long) {
        presses.remove(pointerId)
    }
}