package com.musa.atomkeys.engine

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Where every key actually is, computed arithmetically from the row/weight model.
 *
 * This exists because of a **confirmed** spike-0.3 failure, not a hypothetical one. The spike
 * resolved taps by linearly scanning laid-out key bounds and comparing grid-local pointer
 * coordinates against root-space bounds with a fudge factor. Typing `q t p a h l z m` committed
 * `qtpahlal` — both row-3 taps resolved to the row-2 keys sitting above them, because vertically
 * adjacent keys both matched and the first hit won.
 *
 * Two rules follow, and this class enforces both:
 *
 *  - **One coordinate space.** Everything here is relative to the top-left of the *key grid* — the
 *    container holding the rows, not the keyboard window and not the screen. The caller hands us
 *    pointer coordinates already local to that container, which is exactly what a `pointerInput`
 *    modifier on the grid provides. There is no offset to get wrong.
 *  - **Arithmetic, not search.** Rects come from the same weights the renderer uses, so a key's
 *    bounds cannot disagree with where it was drawn.
 *
 * Phase 6.2 needs this too: its correction scorer weights substitutions by distance between key
 * centres, so it consumes [KeyRect.centerX] / [KeyRect.centerY] directly.
 */

/** One key's bounds, in pixels, relative to the top-left of the key grid. */
data class KeyRect(
    val key: RowItem.Key,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    /**
     * Distance from a point to this rect's nearest edge — `0` when the point is inside.
     *
     * Edge distance rather than centre distance, so a wide key like the spacebar is not penalised
     * for being wide. Phase 6.2's proximity model uses centre distance instead, which is why both
     * are available.
     */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return hypot(dx, dy)
    }

    /** Straight-line distance to this key's centre, for the Phase 6.2 proximity scorer. */
    fun distanceToCenter(x: Float, y: Float): Float = hypot(x - centerX, y - centerY)
}

/** The resolved layout: every key's rect, plus the grid's own measured size. */
class KeyboardGeometry(
    val keys: List<KeyRect>,
    val widthPx: Float,
    val heightPx: Float,
) {
    private val byId: Map<String, KeyRect> = keys.associateBy { it.key.id }

    fun rectFor(id: String): KeyRect? = byId[id]

    /** The key containing this point, or `null` if it fell in a gap, a spacer or the middle rule. */
    fun keyAt(x: Float, y: Float): KeyRect? = keys.firstOrNull { it.contains(x, y) }

    /**
     * The key nearest this point, ignoring gaps.
     *
     * Taps land in the inter-key gaps constantly — the gap is `round(h * 0.1)`, so 6dp at height L
     * — and dropping them would feel broken. [keyAt] returning `null` is the honest answer to
     * "which key is under this pixel"; this is the answer to "which key did they mean".
     *
     * Phase 4.2 layers slide-off and hysteresis on top of this; it deliberately has no state.
     */
    fun nearestKeyTo(x: Float, y: Float): KeyRect? = keys.minByOrNull { it.distanceTo(x, y) }

    /**
     * Every key whose centre lies within [radiusPx] of the point, nearest first.
     *
     * The Phase 6.2 entry point: an `n` tapped near the `m` edge must yield both as plausible, so
     * the scorer needs the candidate set rather than a single winner.
     */
    fun keysNear(x: Float, y: Float, radiusPx: Float): List<KeyRect> =
        keys.filter { it.distanceToCenter(x, y) <= radiusPx }
            .sortedBy { it.distanceToCenter(x, y) }

    companion object {

        /**
         * Lays out [rows] into a grid [widthPx] wide.
         *
         * Mirrors what the renderer does with `Modifier.weight` inside a `Row` using
         * `Arrangement.spacedBy(gap)`: gaps sit between *every* item including zero-width spacers,
         * and the remaining width is divided in proportion to weight.
         *
         * Compose distributes leftover sub-pixels its own way, so a rect here can differ from the
         * drawn key by well under a pixel. That is immaterial for hit-testing keys ~100px wide and
         * for proximity scoring, and it is not the failure mode spike 0.3 hit — that was a
         * coordinate-space bug, which one shared origin eliminates outright.
         *
         * @param densityPxPerDp `Density.density` from Compose — dp are converted here so every
         *   dimension in the result is in the same pixel space as the incoming pointer events.
         */
        fun resolve(
            rows: List<Row>,
            keySize: KeySize,
            widthPx: Float,
            densityPxPerDp: Float,
        ): KeyboardGeometry {
            val gap = keySize.gapDp * densityPxPerDp
            val keyHeight = keySize.heightDp * densityPxPerDp
            val ruleHeight = (
                AtomDimens.MIDDLE_RULE_HEIGHT_DP +
                    2 * AtomDimens.MIDDLE_RULE_VERTICAL_MARGIN_DP
                ) * densityPxPerDp

            val rects = mutableListOf<KeyRect>()
            var y = 0f

            rows.forEachIndexed { index, row ->
                if (index > 0) y += gap
                when (row) {
                    is Row.MiddleRule -> y += ruleHeight

                    is Row.Keys -> {
                        val totalWeight = row.items.sumOf { it.weight.toDouble() }.toFloat()
                        val gapTotal = gap * (row.items.size - 1).coerceAtLeast(0)
                        // Width claimed by one unit of weight, after the fixed gaps are removed.
                        val perWeight = (widthPx - gapTotal) / totalWeight

                        var x = 0f
                        for (item in row.items) {
                            val itemWidth = perWeight * item.weight
                            if (item is RowItem.Key) {
                                rects += KeyRect(
                                    key = item,
                                    left = x,
                                    top = y,
                                    right = x + itemWidth,
                                    bottom = y + keyHeight,
                                )
                            }
                            x += itemWidth + gap
                        }
                        y += keyHeight
                    }
                }
            }

            return KeyboardGeometry(rects, widthPx, heightPx = y)
        }
    }
}

/**
 * Touch-noise rejection thresholds from AOSP `config-common.xml` (spike 0.7).
 *
 * A touch that travels further than [NOISE_DISTANCE_DP] and lifts within [NOISE_TIME_MS] is a palm
 * brush or a graze, not a keypress. The design doc does not mention this; without it the keyboard
 * registers phantom keys. Phase 4.2 owns the stateful side — these are the numbers.
 */
object TouchThresholds {
    const val NOISE_DISTANCE_DP = 12.6f
    const val NOISE_TIME_MS = 40L

    /**
     * While a pointer is down the active key's hit box expands, so small drift during a press does
     * not switch keys. AOSP defines this per-keyboard; 8dp is its sliding-modifier value and the
     * documented starting point.
     */
    const val HYSTERESIS_DP = 8f

    /** Whether a press that started on [from] should still count as [from] after drifting to a point. */
    fun withinHysteresis(from: KeyRect, x: Float, y: Float, densityPxPerDp: Float): Boolean =
        from.distanceTo(x, y) <= HYSTERESIS_DP * densityPxPerDp

    /** Whether a completed touch looks like noise rather than a deliberate tap. */
    fun isNoise(travelPx: Float, durationMs: Long, densityPxPerDp: Float): Boolean =
        durationMs < NOISE_TIME_MS && travelPx > NOISE_DISTANCE_DP * densityPxPerDp

    /** Convenience for callers tracking travel as separate axes. */
    fun travel(dx: Float, dy: Float): Float = hypot(abs(dx), abs(dy))
}
