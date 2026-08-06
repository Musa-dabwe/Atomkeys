package com.musa.atomkeys.spike

/*
 * THROWAWAY SPIKE CODE — Phase 0.1 / 0.2 / 0.3.
 *
 * Exists to answer three questions on a real device, not to be the eventual keyboard:
 *   0.1  Can Compose be hosted inside an InputMethodService, and does it survive show/hide?
 *   0.2  Can floating elements be drawn without Popup, in reserved headroom?
 *   0.3  What is the keypress latency, and does multitouch survive fast two-thumb typing?
 *
 * Delete this package once Phase 1 starts for real.
 */

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
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

/**
 * ## Phase 0.1 — the answer, corrected by a real device
 *
 * An [InputMethodService] is a Service, so it is not a LifecycleOwner and supplies none of the
 * view-tree owners Compose requires.
 *
 * **The non-obvious part, found by crashing on device:** setting the owners on the view you return
 * from [onCreateInputView] is *not enough*. It fails with
 *
 * ```
 * IllegalStateException: ViewTreeLifecycleOwner not found from
 *   android.widget.LinearLayout{... android:id/parentPanel}
 * ```
 *
 * because `AbstractComposeView.resolveParentCompositionContext()` resolves the window recomposer by
 * walking **up** the hierarchy, and the IME framework re-parents our view into its own
 * `android:id/parentPanel` LinearLayout — which has no owners. Compose therefore searches from a view
 * we never touched.
 *
 * The fix is to set all three owners on the **IME window's decor view**, which is the actual root of
 * that hierarchy. The service itself is the natural owner: it outlives every input-view instance.
 *
 * Per the confirmed 0.1 decision the composition is still **recreated on each show** — the state that
 * must outlive a session lives in [viewModelStore], owned here rather than by the view.
 */
class SpikeImeService :
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

    private val metrics = SpikeMetrics()
    private var ownersAttached = false

    override fun onCreate() {
        // Ordering is load-bearing: attach + restore while still INITIALIZED, then advance.
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        attachOwnersToWindowDecor()

        // A fresh ComposeView (and so a fresh composition) per show, per the 0.1 decision.
        // Note this is plain ComposeView, not a subclass — ComposeView is final, and subclassing
        // AbstractComposeView bought nothing once the owners moved to the decor view.
        return ComposeView(this).apply {
            setContent {
                SpikeKeyboard(metrics = metrics, onCommit = ::commit)
            }
        }
    }

    /**
     * The actual fix for the crash above. `getWindow()` on an [InputMethodService] is a Dialog; its
     * decor view is the root that Compose's recomposer lookup terminates at.
     */
    private fun attachOwnersToWindowDecor() {
        if (ownersAttached) return
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        ownersAttached = true
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        metrics.onStartInputView(restarting)
        // RESUMED is required: Compose's lifecycle-aware recomposer pauses below STARTED, so a
        // keyboard left at CREATED renders once and then stops updating.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Dropping below STARTED is what makes infinite animations (atomShimmer) stop while the
        // keyboard is hidden — roadmap 8.5's battery requirement, for free.
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    /**
     * Minimal commit path — the real one is the `Editor` wrapper designed in spike 0.4.
     * The null check matters: `currentInputConnection` is null with no focused field, and the
     * instance is replaced when the target field changes, so it must never be cached.
     */
    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        metrics.onCommitted()
    }
}
