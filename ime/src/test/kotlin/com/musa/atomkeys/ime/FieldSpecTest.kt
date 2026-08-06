package com.musa.atomkeys.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.musa.atomkeys.engine.ImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy guarantee, asserted rather than eyeballed.
 *
 * These rules decide whether the keyboard is allowed to correct, learn from, and speak aloud what the
 * user types. Getting one variation wrong means learning someone's password. Spike 0.4 flagged this as
 * needing a table-driven test; this is it.
 *
 * Runs as a plain JVM test — no Robolectric — because `InputType`/`EditorInfo` constants are
 * compile-time ints and [FieldSpec.from] has an overload taking them directly.
 */
class FieldSpecTest {

    private fun spec(inputType: Int, imeOptions: Int = 0) =
        FieldSpec.from(inputType, imeOptions)

    private val plainText =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL

    // ---------------------------------------------------------------- passwords

    @Test
    fun `every password variation blocks correction, learning and announcement`() {
        val passwordTypes = mapOf(
            "text password" to
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            // The one that gets missed: "show password" toggles switch to this, and it looks
            // like ordinary text.
            "visible password" to
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            "web password" to
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            "numeric PIN" to
                (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        )

        for ((name, type) in passwordTypes) {
            val s = spec(type)
            assertFalse("$name must not allow correction", s.allowCorrection)
            assertFalse("$name must not allow learning", s.allowLearning)
            assertFalse("$name must not auto-capitalise", s.autoCapitalize)
            assertTrue("$name must obscure announcements", s.obscureAnnouncements)
        }
    }

    // ---------------------------------------------------------------- ordinary text

    @Test
    fun `plain text field allows everything`() {
        val s = spec(plainText)
        assertTrue(s.allowCorrection)
        assertTrue(s.allowLearning)
        assertTrue(s.autoCapitalize)
        assertFalse(s.obscureAnnouncements)
    }

    // ---------------------------------------------------------------- suggestions / learning

    @Test
    fun `NO_SUGGESTIONS blocks both correction and learning`() {
        val s = spec(plainText or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertFalse(s.allowCorrection)
        assertFalse(s.allowLearning)
    }

    @Test
    fun `NO_PERSONALIZED_LEARNING blocks learning but still allows correction`() {
        // Incognito behaviour: correct my typing, but do not remember it.
        val s = spec(plainText, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue("correction is still useful in incognito", s.allowCorrection)
        assertFalse("but nothing may be learned", s.allowLearning)
    }

    // ---------------------------------------------------------------- capitalisation

    @Test
    fun `email and URI fields do not auto-capitalise`() {
        val addressTypes = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )
        for (type in addressTypes) {
            assertFalse(spec(type).autoCapitalize)
            // ...but they are not passwords, so correction stays on.
            assertTrue(spec(type).allowCorrection)
        }
    }

    // ---------------------------------------------------------------- the action key

    @Test
    fun `imeOptions selects the action`() {
        val cases = mapOf(
            EditorInfo.IME_ACTION_GO to ImeAction.GO,
            EditorInfo.IME_ACTION_SEARCH to ImeAction.SEARCH,
            EditorInfo.IME_ACTION_SEND to ImeAction.SEND,
            EditorInfo.IME_ACTION_NEXT to ImeAction.NEXT,
            EditorInfo.IME_ACTION_DONE to ImeAction.DONE,
            EditorInfo.IME_ACTION_PREVIOUS to ImeAction.PREVIOUS,
        )
        for ((option, expected) in cases) {
            assertEquals(expected, spec(plainText, option).action)
        }
    }

    @Test
    fun `no action specified means newline`() {
        assertEquals(ImeAction.NEWLINE, spec(plainText).action)
    }

    @Test
    fun `multiline field overrides the action with newline`() {
        val s = spec(
            plainText or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_SEND,
        )
        assertTrue(s.isMultiline)
        assertEquals(
            "a multiline field's Enter must break the line, not send",
            ImeAction.NEWLINE,
            s.action,
        )
    }

    @Test
    fun `NO_ENTER_ACTION keeps a multiline field acting on its action`() {
        val s = spec(
            plainText or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
        )
        assertFalse(s.isMultiline)
        assertEquals(ImeAction.SEND, s.action)
    }

    // ---------------------------------------------------------------- fallback

    @Test
    fun `null EditorInfo yields the conservative fallback`() {
        val s = FieldSpec.from(null)
        assertFalse(s.allowCorrection)
        assertFalse(s.allowLearning)
        assertFalse(s.autoCapitalize)
        assertTrue("unknown field must be treated as sensitive", s.obscureAnnouncements)
    }
}
