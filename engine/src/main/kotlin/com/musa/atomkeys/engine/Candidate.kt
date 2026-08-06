package com.musa.atomkeys.engine

/**
 * One entry in the candidate strip.
 *
 * Phase 6.2 produces these from the correction engine; Phase 3 only renders them. Kept in `:engine`
 * so the strip's contract is settled before the thing that fills it exists.
 */
data class Candidate(
    val text: String,
    /** The highlighted candidate — the one that goes in the composing region. */
    val isActive: Boolean = false,
    /**
     * Whether more candidates exist below this one, drawn as the `·`·`·` sub-dots. Gesture G3
     * (swipe down) is what cycles through them.
     */
    val hasMore: Boolean = false,
    /** Emoji render in the system font rather than DM Sans, as they do on keycaps. */
    val isEmoji: Boolean = false,
) {
    companion object {
        /**
         * The three chips the design doc draws in every frame. Used by previews and by the settings
         * app's live keyboard preview, which has no correction engine behind it.
         */
        val Placeholder = listOf(
            Candidate("my", isActive = true, hasMore = true),
            Candidate("there"),
            Candidate("👋", isEmoji = true),
        )
    }
}
