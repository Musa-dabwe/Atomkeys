package com.musa.atomkeys.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the test double itself. Worth doing: [FakeEditor] is the oracle every Phase 4 and Phase 6
 * test will be checked against, so a bug here would silently invalidate all of them.
 *
 * Also proves the `:engine` JVM test loop works — these run without an emulator.
 */
class FakeEditorTest {

    @Test
    fun `commit appends text`() {
        val e = FakeEditor()
        e.commit("h")
        e.commit("i")
        assertEquals("hi", e.text)
        assertEquals(listOf("commit(h)", "commit(i)"), e.calls)
    }

    @Test
    fun `composing region is replaced, not appended`() {
        val e = FakeEditor()
        e.setComposing("teh")
        e.setComposing("the")
        assertEquals("the", e.text)
        assertEquals("the", e.composingText)
        assertTrue(e.isComposing)
    }

    @Test
    fun `commit finalises the composing region`() {
        val e = FakeEditor()
        e.setComposing("teh")
        e.commit("the")
        assertEquals("the", e.text)
        assertNull(e.composingText)
        assertTrue(!e.isComposing)
    }

    @Test
    fun `composing text does not accumulate across a word`() {
        val e = FakeEditor("hello ")
        e.setComposing("w")
        e.setComposing("wo")
        e.setComposing("wor")
        assertEquals("hello wor", e.text)
    }

    @Test
    fun `deleteWordBefore removes trailing whitespace and the word`() {
        val e = FakeEditor("hello world  ")
        assertEquals("world  ", e.deleteWordBefore())
        assertEquals("hello ", e.text)
    }

    @Test
    fun `deleteWordBefore on empty field returns null`() {
        assertNull(FakeEditor().deleteWordBefore())
    }

    @Test
    fun `deleteWordBefore on whitespace-only field removes the whitespace`() {
        // Decided in FakeEditor.deleteWordBefore's docs: with no word to delete, remove the
        // whitespace run rather than no-op, because a gesture that does nothing looks broken.
        val e = FakeEditor("   ")
        assertEquals("   ", e.deleteWordBefore())
        assertEquals("", e.text)
    }

    @Test
    fun `deleteWordBefore leaves the preceding word intact`() {
        val e = FakeEditor("one two three")
        assertEquals("three", e.deleteWordBefore())
        assertEquals("one two ", e.text)
    }

    @Test
    fun `deleteBefore clamps to buffer length`() {
        val e = FakeEditor("ab")
        e.deleteBefore(10)
        assertEquals("", e.text)
    }

    @Test
    fun `textBeforeCursor clamps rather than throwing`() {
        assertEquals("ab", FakeEditor("ab").textBeforeCursor(100))
        assertEquals("b", FakeEditor("ab").textBeforeCursor(1))
    }

    @Test
    fun `capsMode is SENTENCE_START on empty field`() {
        assertEquals(CapsMode.SENTENCE_START, FakeEditor().capsMode())
    }

    @Test
    fun `capsMode is SENTENCE_START after a terminator plus space`() {
        assertEquals(CapsMode.SENTENCE_START, FakeEditor("Hi. ").capsMode())
    }

    @Test
    fun `capsMode is NONE immediately after the terminator`() {
        // No space yet, so the sentence has not actually started.
        assertEquals(CapsMode.NONE, FakeEditor("Hi.").capsMode())
    }

    @Test
    fun `capsMode is NONE mid-word`() {
        assertEquals(CapsMode.NONE, FakeEditor("hello wor").capsMode())
    }

    @Test
    fun `allCaps field reports ALL_CAPS`() {
        assertEquals(CapsMode.ALL_CAPS, FakeEditor("x", allCaps = true).capsMode())
    }

    @Test
    fun `batch brackets the operations it contains`() {
        val e = FakeEditor("teh ")
        e.batch {
            e.deleteBefore(4)
            e.commit("the ")
        }
        assertEquals("the ", e.text)
        assertEquals(
            listOf("batch{", "deleteBefore(4)", "commit(the )", "}batch"),
            e.calls,
        )
    }

    @Test
    fun `batch closes its bracket even when the block throws`() {
        val e = FakeEditor()
        runCatching { e.batch<Unit> { error("boom") } }
        assertEquals(listOf("batch{", "}batch"), e.calls)
    }

    @Test
    fun `disconnected editor is observable`() {
        assertTrue(!FakeEditor(isConnected = false).isConnected)
    }
}
