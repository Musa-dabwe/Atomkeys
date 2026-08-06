package com.musa.atomkeys.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.musa.atomkeys.engine.CapsMode

/**
 * The AtomKeys input method.
 *
 * ## Hosting Compose in an IME — the non-obvious part
 *
 * An [InputMethodService] is a Service: not a `LifecycleOwner`, and it supplies none of the view-tree
 * owners Compose needs. Setting those owners on the view returned from [onCreateInputView] **does not
 * work** — it fails with:
 *
 * ```
 * IllegalStateException: ViewTreeLifecycleOwner not found from
 *   android.widget.LinearLayout{... android:id/parentPanel}
 * ```
 *
 * because `setInputView()` re-parents our view into the framework's own `parentPanel`, and Compose
 * resolves its window recomposer by walking **up** from there. The owners must therefore go on the
 * **IME window's decor view**, with this service as the owner since it outlives every input view.
 *
 * Established on-device in Phase 0.1 — see `.planning/spikes/compose-in-ime.md`.
 *
 * Two further consequences:
 *  - The lifecycle must reach **RESUMED**, not merely CREATED: Compose's lifecycle-aware recomposer
 *    pauses below STARTED, so a keyboard parked at CREATED renders once and then silently stops
 *    updating. Dropping back below STARTED on hide is also what stops infinite animations for free.
 *  - Because the owners live on the decor view, no custom view subclass is needed — a plain
 *    [ComposeView] suffices.
 */
class AtomKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val crashState = KeyboardCrashState()

    /** Digested EditorInfo for the current field. Recomputed once per `onStartInput`. */
    private var fieldSpec: FieldSpec = FieldSpec.Fallback

    /** Drives recomposition when we swap to the fallback keyboard. */
    private var showFallback by mutableStateOf(false)

    private val editor = InputConnectionEditor(
        connection = { currentInputConnection },
        fieldSpec = { fieldSpec },
    )

    /**
     * The decor view we last attached owners to.
     *
     * Not a boolean latch: the IME window's decor view can be **recreated** on a configuration
     * change (rotation, theme switch, split-screen). A latched flag would skip re-attaching, and the
     * next composition would crash with the same `ViewTreeLifecycleOwner not found` that Phase 0.1
     * diagnosed — but only after a rotation, which is exactly the kind of bug that ships.
     */
    private var ownedDecorView: View? = null

    override fun onCreate() {
        // Attach + restore must happen while the lifecycle is still INITIALIZED; restoring after it
        // has advanced throws.
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        attachOwnersToWindowDecor()
        return ComposeView(this).apply {
            setContent {
                // Any composition failure degrades to the fallback rather than killing the service:
                // a crashed IME can leave the user unable to type at all.
                if (showFallback || crashState.hasFailed) {
                    FallbackKeyboard(
                        onKey = { ch -> guarded("fallback key") { editor.commit(ch.toString()) } },
                        onBackspace = { guarded("fallback backspace") { editor.deleteBefore(1) } },
                        onSpace = { guarded("fallback space") { editor.commit(" ") } },
                    )
                } else {
                    PhaseOneKeyboard(
                        onKey = ::onCharKey,
                        onBackspace = { guarded("backspace") { editor.deleteBefore(1) } },
                        onSpace = { guarded("space") { editor.commit(" ") } },
                        onEnter = ::onEnterKey,
                    )
                }
            }
        }
    }

    /** The Phase 0.1 fix. `getWindow()` here is a Dialog; its decor view is the hierarchy root. */
    private fun attachOwnersToWindowDecor() {
        val decor = window?.window?.decorView ?: return
        if (decor === ownedDecorView) return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        ownedDecorView = decor
    }

    /**
     * Re-attach owners after a configuration change, in case the window was rebuilt.
     * [attachOwnersToWindowDecor] is idempotent and compares identity, so this is cheap.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        guarded("onConfigurationChanged") { attachOwnersToWindowDecor() }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        // One digest per field, so the privacy rules are decided in exactly one place.
        fieldSpec = CrashGuard.runGuarded(this, "FieldSpec.from", FieldSpec.Fallback) {
            FieldSpec.from(info)
        }
        guarded("editor.onStartInput") { editor.onStartInput() }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        // The shadow buffer has diverged — the user tapped elsewhere, or the app edited its own text.
        guarded("editor.onUpdateSelection") { editor.onUpdateSelection() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ key handling

    private fun onCharKey(ch: Char) = guarded("key '$ch'") {
        // Minimal auto-capitalisation, to prove the FieldSpec/Editor path end to end.
        // Phase 4.4 owns the real behaviour.
        val shouldCap = ch.isLetter() && editor.capsMode() != CapsMode.NONE
        editor.commit(if (shouldCap) ch.uppercaseChar().toString() else ch.toString())
    }

    private fun onEnterKey() = guarded("enter") {
        // ✓ means different things per field — send/search/next, or a newline in a multiline field.
        if (fieldSpec.isMultiline) {
            editor.sendKey(KeyEvent.KEYCODE_ENTER)
        } else {
            editor.performAction()
        }
    }

    /**
     * Runs [block] with crash containment. A failure loses one keystroke and is logged locally; a
     * repeat failure latches the fallback keyboard rather than retrying a broken composition.
     */
    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            CrashGuard.report(this, what, t)
            if (crashState.hasFailed) return
            crashState.trip()
            showFallback = true
        }
    }
}
