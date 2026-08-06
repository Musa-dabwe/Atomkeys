package com.musa.atomkeys.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.musa.atomkeys.engine.ImeAction

/**
 * Everything the keyboard needs to know about the *kind* of field it is editing, digested **once**
 * in `onStartInput` rather than re-derived per keystroke.
 *
 * Centralising this is a privacy control, not a tidiness preference. Scattering `inputType` checks
 * through key handling is how a keyboard ends up learning someone's password — there must be exactly
 * one place that decides whether correction, learning and announcements are permitted.
 *
 * Designed in `.planning/spikes/inputconnection.md`.
 */
data class FieldSpec(
    /** What the ✓ key does. [ImeAction.NEWLINE] means insert a line break instead. */
    val action: ImeAction,
    /** Whether auto-correction and the candidate strip are allowed. */
    val allowCorrection: Boolean,
    /** Whether typed words may be added to the user dictionary. */
    val allowLearning: Boolean,
    /** Whether auto-capitalisation is appropriate. */
    val autoCapitalize: Boolean,
    /** Whether ✓ should insert a newline rather than perform an action. */
    val isMultiline: Boolean,
    /**
     * Whether spoken key announcements must be obscured ("dot" instead of the character).
     * Reading out a password through the phone speaker is a security leak — see spike 0.6.
     */
    val obscureAnnouncements: Boolean,
) {
    companion object {

        /** Conservative default used when there is no EditorInfo at all. */
        val Fallback = FieldSpec(
            action = ImeAction.NEWLINE,
            allowCorrection = false,
            allowLearning = false,
            autoCapitalize = false,
            isMultiline = false,
            obscureAnnouncements = true,
        )

        fun from(info: EditorInfo?): FieldSpec =
            if (info == null) Fallback else from(info.inputType, info.imeOptions)

        /**
         * The actual rules, over the two raw ints.
         *
         * Split out from [from] so it can be unit-tested on the JVM without Robolectric: the
         * `InputType` / `EditorInfo` constants are compile-time `static final int`s and inline
         * cleanly, whereas instantiating an [EditorInfo] would need an Android runtime. These rules
         * are a privacy guarantee, so they need to be asserted rather than eyeballed.
         */
        fun from(inputType: Int, imeOptions: Int): FieldSpec {
            val isPassword = isPasswordField(inputType)
            val noSuggestions = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
            val noLearning = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            val multiline = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 &&
                imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0

            val correctionOk = !isPassword && !noSuggestions
            return FieldSpec(
                action = resolveAction(imeOptions, multiline),
                allowCorrection = correctionOk,
                // Learning is strictly narrower than correction: a field may permit correction while
                // forbidding personalisation (incognito browser tabs do exactly this).
                allowLearning = correctionOk && !noLearning,
                autoCapitalize = !isPassword && !isAddressLike(inputType),
                isMultiline = multiline,
                obscureAnnouncements = isPassword,
            )
        }

        /**
         * True for every password flavour.
         *
         * `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` is the one that gets missed: it is what many
         * "show password" toggles switch to, and it looks like ordinary text. Omitting it means the
         * keyboard happily learns passwords whenever the user reveals them.
         */
        private fun isPasswordField(inputType: Int): Boolean {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (cls) {
                InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

                InputType.TYPE_CLASS_NUMBER ->
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

                else -> false
            }
        }

        /** Email and URI fields, where sentence capitalisation is actively wrong. */
        private fun isAddressLike(inputType: Int): Boolean {
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
                -> true

                else -> false
            }
        }

        private fun resolveAction(imeOptions: Int, multiline: Boolean): ImeAction {
            if (multiline) return ImeAction.NEWLINE
            return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_GO -> ImeAction.GO
                EditorInfo.IME_ACTION_SEARCH -> ImeAction.SEARCH
                EditorInfo.IME_ACTION_SEND -> ImeAction.SEND
                EditorInfo.IME_ACTION_NEXT -> ImeAction.NEXT
                EditorInfo.IME_ACTION_DONE -> ImeAction.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> ImeAction.PREVIOUS
                else -> ImeAction.NEWLINE
            }
        }
    }
}
