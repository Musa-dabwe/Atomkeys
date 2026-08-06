# Spike 0.4 — InputConnection surface

**Status:** answered (desk design; validated against the compiled spike in 0.1–0.3)
**Date:** 2026-08-06
**Gates:** Phase 4 (all of it), Phase 6.2/6.3, Phase 8.2 (fake-backed tests)

---

## Answer

Wrap `InputConnection` behind a narrow internal interface (`Editor`) that Phase 4 logic talks to
exclusively. Two implementations: the real one over `currentInputConnection`, and a fake for tests.

Nothing outside the `:ime` service layer may touch `InputConnection` directly. That is what makes
gesture and correction logic testable on the JVM instead of only on a device.

## Why a wrapper, beyond testability

Four properties of the real API make direct use hazardous:

1. **`currentInputConnection` is nullable and it changes.** There is no connection when no field is
   focused, and the instance is replaced when the target field changes. A cached reference goes
   stale silently — edits then vanish with no error.
2. **`getTextBeforeCursor` is an IPC round-trip.** It crosses into the target app's process. It can
   return `null`, it can be slow, and calling it per keystroke puts another process on the typing
   hot path. See the shadow-buffer finding below.
3. **Multi-operation edits need batching.** Replacing a corrected word is delete + commit; without
   `beginBatchEdit`/`endBatchEdit` the target app can observe and render the intermediate state.
4. **The composing region has no reliable getter.** We must track it ourselves.

## The interface

```kotlin
/** Everything the keyboard needs from the target text field. */
interface Editor {
    /** False when no field is focused — callers must no-op rather than assume success. */
    val isConnected: Boolean

    // --- output ---
    fun commit(text: CharSequence)
    fun setComposing(text: CharSequence)
    fun finishComposing()
    /** True if a composing region is currently active. Tracked locally, not queried. */
    val isComposing: Boolean

    // --- deletion ---
    fun deleteBefore(chars: Int)
    /** Deletes the whole word before the cursor (gesture G2). Returns what it removed, or null. */
    fun deleteWordBefore(): String?

    // --- context (served from the shadow buffer, not per-call IPC) ---
    fun textBeforeCursor(maxChars: Int): CharSequence
    /** Sentence-boundary detection for auto-capitalization (4.4). */
    fun capsMode(): CapsMode

    // --- cursor (gesture G5) ---
    fun moveCursor(offsetChars: Int)
    fun selectAll()

    // --- keys ---
    /** Enter / the ✓ key, resolved per field via [FieldSpec.action]. */
    fun performAction()
    fun sendKey(keyCode: Int)

    // --- batching ---
    fun <T> batch(block: () -> T): T
}

enum class CapsMode { NONE, SENTENCE_START, ALL_CAPS }
```

`FieldSpec` is the digested form of `EditorInfo`, computed once in `onStartInput` rather than
re-derived per keystroke:

```kotlin
data class FieldSpec(
    val action: ImeAction,            // from imeOptions & IME_MASK_ACTION
    val allowCorrection: Boolean,     // false for password / NO_SUGGESTIONS fields
    val allowLearning: Boolean,       // false for password / NO_PERSONALIZED_LEARNING
    val autoCapitalize: Boolean,      // false for password, email, URI
    val isMultiline: Boolean,         // decides ✓ vs newline
) {
    companion object { fun from(info: EditorInfo): FieldSpec = /* ... */ }
}

enum class ImeAction { GO, SEARCH, SEND, NEXT, DONE, PREVIOUS, NEWLINE }
```

Collapsing `EditorInfo` into these five booleans/enums at `onStartInput` keeps the privacy rules
(4.4, 6.3) in **one** place. Scattering `inputType` checks through key handling is how a keyboard
ends up learning passwords.

## Finding — do not call `getTextBeforeCursor` on the hot path ↯

Auto-capitalization, delete-word, and double-space-period all need text context. Querying it
synchronously per keystroke means an IPC hop into the target app on every key, which directly
threatens the Phase 0.3 latency budget.

**Approach:** keep a local **shadow buffer** of the text near the cursor.

- Seed it in `onStartInput` from one `getTextBeforeCursor(N, 0)` call (N ≈ 64 is plenty for sentence
  and word context).
- Apply our own edits to it locally as we make them — we know exactly what we committed.
- **Re-sync from the real connection on `onUpdateSelection`**, which fires when the user taps
  elsewhere, another input source edits, or the app changes the text itself.
- Treat it as advisory: on any inconsistency, re-query rather than trusting the shadow.

This is the standard IME approach and it is why `textBeforeCursor` above is described as served from
the shadow, not from IPC.

- [ ] **Action for Phase 4:** implement the shadow buffer with `onUpdateSelection` invalidation, and
      unit-test the divergence cases (app-side edit, cursor jump, text replaced wholesale).

## Finding — `getCursorCapsMode` over hand-rolled parsing

Per the roadmap's 4.4 decision, use `InputConnection.getCursorCapsMode(0)` for auto-capitalization.
It resolves sentence boundaries across punctuation, spaces and emoji correctly, and respects the
field's own `inputType` capitalization flags — all things a hand-rolled parser gets wrong.

Caveat: it is also IPC. Call it on word/sentence boundaries (after committing a terminator), **not**
on every keystroke, and cache the result until the next boundary.

## Finding — minSdk 26 removes two compatibility branches ✓

Confirms the Phase 1.1 `minSdk = 26` decision:

| API | Added in | Effect |
|---|---|---|
| `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` | 26 | Incognito detection needs no version guard |
| `VibrationEffect` | 26 | Phase 7.1 haptics needs no legacy `vibrate(long)` fallback |

Both are used unconditionally.

## Password / no-learn detection

`allowCorrection` and `allowLearning` must be false for **all** of these, not just the obvious one:

- `TYPE_TEXT_VARIATION_PASSWORD`
- `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`
- `TYPE_TEXT_VARIATION_WEB_PASSWORD`
- `TYPE_NUMBER_VARIATION_PASSWORD`
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS` set
- `IME_FLAG_NO_PERSONALIZED_LEARNING` set (learning only)

`autoCapitalize` additionally off for `TYPE_TEXT_VARIATION_EMAIL_ADDRESS`,
`TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS`, and `TYPE_TEXT_VARIATION_URI`.

`TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` is the one that gets missed — it is what many "show password"
toggles switch to, and it looks like ordinary text.

- [ ] **Action for Phase 8.1:** table-driven unit test over `FieldSpec.from()` covering every
      variation above. This is a privacy guarantee; assert it rather than eyeballing it.

## The fake

```kotlin
class FakeEditor(initial: String = "") : Editor {
    val buffer = StringBuilder(initial)
    var composingRange: IntRange? = null
    val calls = mutableListOf<String>()   // ordered call log for assertions
    // ...
}
```

Phase 8.2 asserts against **`calls`**, not just the final buffer text. "Did the right sequence of
`InputConnection` operations happen, in the right order, inside one batch" is the property that
matters — a correct final string reached via a wrong sequence still flickers on real devices.

## Not wrapped

Deliberately excluded from `Editor`, to keep it narrow:

- `commitCorrection` — cosmetic, inconsistently honored across apps
- `requestCursorUpdates` — only needed if we later draw a floating cursor affordance
- `commitContent` — Phase 11 (image/GIF insertion), not v1
- `performPrivateCommand` — no use case
