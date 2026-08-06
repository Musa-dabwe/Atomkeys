package com.musa.atomkeys.engine

/**
 * In-memory [Editor] for tests.
 *
 * Records an ordered [calls] log as well as maintaining [text]. Phase 8.2 asserts against the log,
 * not only the final string: reaching the right text via the wrong sequence of operations still
 * flickers on a real device, and still breaks batching.
 *
 * Lives in `main` rather than a test source set so `:ime` and `:app` tests can use it too.
 */
class FakeEditor(
    initial: String = "",
    override val isConnected: Boolean = true,
    private val allCaps: Boolean = false,
) : Editor {

    private val buffer = StringBuilder(initial)

    /** Ordered log of operations, for assertions. Batches appear as `batch{` … `}batch`. */
    val calls = mutableListOf<String>()

    /** Current field contents, composing region included. */
    val text: String get() = buffer.toString()

    /** Range of the composing region within [text], or null if none. */
    var composingRange: IntRange? = null
        private set

    override val isComposing: Boolean get() = composingRange != null

    /** The composing text, or null. */
    val composingText: String?
        get() = composingRange?.let { buffer.substring(it.first, it.last + 1) }

    override fun commit(text: CharSequence) {
        calls += "commit($text)"
        replaceComposingOrAppend(text.toString())
        composingRange = null
    }

    override fun setComposing(text: CharSequence) {
        calls += "setComposing($text)"
        val start = replaceComposingOrAppend(text.toString())
        composingRange = if (text.isEmpty()) null else start until (start + text.length)
    }

    override fun finishComposing() {
        calls += "finishComposing()"
        composingRange = null
    }

    override fun deleteBefore(chars: Int) {
        calls += "deleteBefore($chars)"
        val n = chars.coerceAtMost(buffer.length)
        if (n > 0) buffer.delete(buffer.length - n, buffer.length)
        composingRange = null
    }

    /**
     * Deletes any trailing whitespace plus the word before it.
     *
     * **Edge case decided here:** when only whitespace precedes the cursor there is no word, and this
     * deletes the whitespace run rather than no-opping. A gesture that visibly does nothing reads as
     * broken, and the user clearly intended to remove something. Only a genuinely empty field
     * returns null. Phase 4.3's real G2 implementation must match this.
     */
    override fun deleteWordBefore(): String? {
        calls += "deleteWordBefore()"
        if (buffer.isEmpty()) return null
        var start = buffer.length
        while (start > 0 && buffer[start - 1].isWhitespace()) start--
        while (start > 0 && !buffer[start - 1].isWhitespace()) start--
        val removed = buffer.substring(start, buffer.length)
        buffer.delete(start, buffer.length)
        composingRange = null
        return removed
    }

    override fun textBeforeCursor(maxChars: Int): CharSequence =
        buffer.substring(maxOf(0, buffer.length - maxChars))

    override fun capsMode(): CapsMode = when {
        allCaps -> CapsMode.ALL_CAPS
        isAtSentenceStart() -> CapsMode.SENTENCE_START
        else -> CapsMode.NONE
    }

    override fun moveCursor(offsetChars: Int) {
        calls += "moveCursor($offsetChars)"
    }

    override fun selectAll() {
        calls += "selectAll()"
    }

    override fun performAction() {
        calls += "performAction()"
    }

    override fun sendKey(keyCode: Int) {
        calls += "sendKey($keyCode)"
    }

    override fun <T> batch(block: () -> T): T {
        calls += "batch{"
        return try {
            block()
        } finally {
            calls += "}batch"
        }
    }

    /** Discard the call log, keeping field contents. Useful between assertion phases. */
    fun clearCalls() = calls.clear()

    private fun replaceComposingOrAppend(text: String): Int {
        val range = composingRange
        return if (range == null) {
            val start = buffer.length
            buffer.append(text)
            start
        } else {
            buffer.replace(range.first, range.last + 1, text)
            range.first
        }
    }

    private fun isAtSentenceStart(): Boolean {
        val trimmed = buffer.toString().trimEnd()
        if (trimmed.isEmpty()) return true
        // Only treat it as a sentence start once whitespace follows the terminator.
        return trimmed.last() in ".!?" && buffer.isNotEmpty() && buffer.last().isWhitespace()
    }
}
