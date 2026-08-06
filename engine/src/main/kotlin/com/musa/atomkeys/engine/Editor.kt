package com.musa.atomkeys.engine

/**
 * Everything the keyboard needs from the target text field.
 *
 * Designed in `.planning/spikes/inputconnection.md` (spike 0.4). This interface is the *only* way
 * keyboard logic is allowed to reach the target app — nothing outside `:ime` may touch
 * `InputConnection` directly. That is what makes gesture and correction logic testable on the JVM
 * instead of only on a device.
 *
 * Deliberately free of Android types so it can live in this pure-Kotlin module. The Android-backed
 * implementation and the `EditorInfo` digest ([FieldSpec] equivalent) live in `:ime`.
 *
 * Three properties of the underlying API shape this design:
 *  - The real connection is **nullable and replaced** when the target field changes, so [isConnected]
 *    exists and every call must be safe to make when it is false.
 *  - Reading text from the target is an **IPC round-trip**, so [textBeforeCursor] is expected to be
 *    served from a locally maintained shadow buffer rather than queried per keystroke.
 *  - Multi-operation edits must be **batched** or the target app renders intermediate states.
 */
interface Editor {

    /** False when no field is focused. Callers must no-op rather than assume success. */
    val isConnected: Boolean

    // --- output ---

    /** Commit text as final input. */
    fun commit(text: CharSequence)

    /**
     * Replace the composing region with [text], creating it if absent. The composing region is what
     * makes auto-correction and candidate replacement possible.
     */
    fun setComposing(text: CharSequence)

    /** Finalise any composing region, accepting it as typed. */
    fun finishComposing()

    /** Whether a composing region is currently active. Tracked locally, never queried. */
    val isComposing: Boolean

    // --- deletion ---

    /** Delete [chars] characters before the cursor. */
    fun deleteBefore(chars: Int)

    /**
     * Delete the whole word before the cursor (gesture G2).
     * @return the text removed, or null if there was nothing to delete.
     */
    fun deleteWordBefore(): String?

    // --- context ---

    /**
     * Text immediately before the cursor, up to [maxChars].
     * Served from the shadow buffer; may be shorter than requested.
     */
    fun textBeforeCursor(maxChars: Int): CharSequence

    /** Capitalisation state at the cursor, for auto-capitalisation (Phase 4.4). */
    fun capsMode(): CapsMode

    // --- cursor (gesture G5) ---

    /** Move the cursor by [offsetChars]; negative moves left. */
    fun moveCursor(offsetChars: Int)

    fun selectAll()

    // --- keys ---

    /** Perform the field's IME action — the ✓ key. Resolved per field from its EditorInfo. */
    fun performAction()

    /** Send a raw key event, for Enter and fallback paths. */
    fun sendKey(keyCode: Int)

    // --- batching ---

    /**
     * Run [block] as a single atomic edit. Required whenever more than one operation is involved
     * (e.g. replacing a corrected word = delete + commit), or the target app can observe and render
     * the intermediate state.
     */
    fun <T> batch(block: () -> T): T
}

/** Capitalisation state at the cursor. */
enum class CapsMode {
    /** No capitalisation implied. */
    NONE,

    /** Start of a sentence — capitalise the next letter. */
    SENTENCE_START,

    /** Field requests all characters capitalised. */
    ALL_CAPS,
}

/**
 * What the ✓ key does in the current field, derived from `EditorInfo.imeOptions`.
 * [NEWLINE] means the field is multiline and ✓ should insert a line break instead of acting.
 */
enum class ImeAction {
    GO,
    SEARCH,
    SEND,
    NEXT,
    DONE,
    PREVIOUS,
    NEWLINE,
}
