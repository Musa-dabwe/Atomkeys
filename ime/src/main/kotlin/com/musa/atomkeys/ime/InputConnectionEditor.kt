package com.musa.atomkeys.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.musa.atomkeys.engine.CapsMode
import com.musa.atomkeys.engine.Editor
import com.musa.atomkeys.engine.ImeAction

/**
 * [Editor] backed by a real [InputConnection].
 *
 * Two design constraints from spike 0.4, both load-bearing:
 *
 * 1. **The connection is never cached.** It is null when no field is focused and is *replaced* when
 *    the target field changes, so every operation re-reads it through [connection]. A stale reference
 *    silently swallows edits with no error.
 *
 * 2. **Reads are served from a shadow buffer, not from IPC.** `getTextBeforeCursor` crosses into the
 *    target app's process. Calling it per keystroke would put another process on the typing hot path,
 *    which directly threatens the measured 1.4 ms feedback budget. Instead we mirror the text near the
 *    cursor locally, apply our own edits to the mirror, and re-sync from the real connection whenever
 *    the framework tells us the selection moved ([onUpdateSelection]).
 */
class InputConnectionEditor(
    private val connection: () -> InputConnection?,
    private val fieldSpec: () -> FieldSpec,
) : Editor {

    /** Local mirror of the text immediately before the cursor. Advisory — see [onUpdateSelection]. */
    private val shadow = StringBuilder()

    private var composingLength = 0

    override val isConnected: Boolean get() = connection() != null

    override val isComposing: Boolean get() = composingLength > 0

    // ---------------------------------------------------------------- lifecycle hooks

    /**
     * Seed the shadow buffer for a newly focused field. One IPC read, then we track locally.
     */
    fun onStartInput() {
        composingLength = 0
        shadow.setLength(0)
        val before = connection()?.getTextBeforeCursor(SHADOW_WINDOW, 0)
        if (before != null) shadow.append(before)
    }

    /**
     * Re-sync from the real connection. Called from `onUpdateSelection`, which fires when the user
     * taps elsewhere, the app edits its own text, or another input source intervenes — all cases where
     * the mirror has diverged and cannot be repaired by replaying our own edits.
     */
    fun onUpdateSelection() {
        composingLength = 0
        shadow.setLength(0)
        val before = connection()?.getTextBeforeCursor(SHADOW_WINDOW, 0)
        if (before != null) shadow.append(before)
    }

    // ---------------------------------------------------------------- output

    override fun commit(text: CharSequence) {
        val ic = connection() ?: return
        ic.commitText(text, 1)
        dropComposingFromShadow()
        shadow.append(text)
        trimShadow()
        composingLength = 0
    }

    override fun setComposing(text: CharSequence) {
        val ic = connection() ?: return
        ic.setComposingText(text, 1)
        dropComposingFromShadow()
        shadow.append(text)
        trimShadow()
        composingLength = text.length
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
        composingLength = 0
    }

    // ---------------------------------------------------------------- deletion

    override fun deleteBefore(chars: Int) {
        val ic = connection() ?: return
        ic.deleteSurroundingText(chars, 0)
        val n = chars.coerceAtMost(shadow.length)
        if (n > 0) shadow.delete(shadow.length - n, shadow.length)
        composingLength = 0
    }

    /**
     * Deletes trailing whitespace plus the word before it (gesture G2).
     *
     * With only whitespace before the cursor there is no word, and this deletes the whitespace run
     * rather than no-opping — a gesture that visibly does nothing reads as broken. Matches
     * `FakeEditor.deleteWordBefore`, which is what the unit tests assert against.
     */
    override fun deleteWordBefore(): String? {
        val ic = connection() ?: return null
        if (shadow.isEmpty()) return null

        var start = shadow.length
        while (start > 0 && shadow[start - 1].isWhitespace()) start--
        while (start > 0 && !shadow[start - 1].isWhitespace()) start--

        val count = shadow.length - start
        if (count <= 0) return null
        val removed = shadow.substring(start, shadow.length)

        return batch {
            ic.deleteSurroundingText(count, 0)
            shadow.delete(start, shadow.length)
            composingLength = 0
            removed
        }
    }

    // ---------------------------------------------------------------- context

    override fun textBeforeCursor(maxChars: Int): CharSequence =
        shadow.substring(maxOf(0, shadow.length - maxChars))

    /**
     * Auto-capitalisation state.
     *
     * Uses [InputConnection.getCursorCapsMode], which resolves sentence boundaries across
     * punctuation, spaces and emoji correctly and honours the field's own capitalisation flags — all
     * things a hand-rolled parser gets wrong. It is IPC, so callers should ask on word/sentence
     * boundaries rather than per keystroke.
     */
    override fun capsMode(): CapsMode {
        if (!fieldSpec().autoCapitalize) return CapsMode.NONE
        val ic = connection() ?: return CapsMode.NONE
        val mode = ic.getCursorCapsMode(InputType.TYPE_CLASS_TEXT)
        return when {
            mode and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> CapsMode.ALL_CAPS
            mode and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> CapsMode.SENTENCE_START
            mode and EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> CapsMode.SENTENCE_START
            else -> CapsMode.NONE
        }
    }

    // ---------------------------------------------------------------- cursor

    override fun moveCursor(offsetChars: Int) {
        if (offsetChars == 0) return
        val key = if (offsetChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        batch {
            repeat(kotlin.math.abs(offsetChars)) { sendKey(key) }
        }
        // Cursor moved by means we cannot mirror; the framework will call onUpdateSelection.
    }

    override fun selectAll() {
        connection()?.performContextMenuAction(android.R.id.selectAll)
    }

    // ---------------------------------------------------------------- keys

    override fun performAction() {
        val ic = connection() ?: return
        when (val action = fieldSpec().action) {
            ImeAction.NEWLINE -> commit("\n")
            else -> ic.performEditorAction(action.toEditorAction())
        }
    }

    override fun sendKey(keyCode: Int) {
        val ic = connection() ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    // ---------------------------------------------------------------- batching

    override fun <T> batch(block: () -> T): T {
        val ic = connection() ?: return block()
        ic.beginBatchEdit()
        return try {
            block()
        } finally {
            ic.endBatchEdit()
        }
    }

    // ---------------------------------------------------------------- shadow upkeep

    /** Composing text already sits in the shadow; remove it before appending its replacement. */
    private fun dropComposingFromShadow() {
        if (composingLength > 0) {
            val n = composingLength.coerceAtMost(shadow.length)
            shadow.delete(shadow.length - n, shadow.length)
        }
    }

    private fun trimShadow() {
        if (shadow.length > SHADOW_WINDOW) {
            shadow.delete(0, shadow.length - SHADOW_WINDOW)
        }
    }

    private companion object {
        /** Plenty for sentence and word context; keeps the mirror cheap. */
        const val SHADOW_WINDOW = 64
    }
}

private fun ImeAction.toEditorAction(): Int = when (this) {
    ImeAction.GO -> EditorInfo.IME_ACTION_GO
    ImeAction.SEARCH -> EditorInfo.IME_ACTION_SEARCH
    ImeAction.SEND -> EditorInfo.IME_ACTION_SEND
    ImeAction.NEXT -> EditorInfo.IME_ACTION_NEXT
    ImeAction.DONE -> EditorInfo.IME_ACTION_DONE
    ImeAction.PREVIOUS -> EditorInfo.IME_ACTION_PREVIOUS
    ImeAction.NEWLINE -> EditorInfo.IME_ACTION_NONE
}
