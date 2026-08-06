# Spikes 0.1 / 0.2 / 0.3 — Compose inside an IME

**Status:** **run on device — 0.1 and 0.2 closed; 0.3 closed except real-finger multitouch**
**Date:** 2026-08-06
**Gates:** Phase 1.3 (0.1), Phase 3.3 (0.2), Phase 3/4 (0.3)

**Test device:** OPPO Reno3 5G (PCLM50), Android 12, ColorOS, 1080×2400, density 480 (override 408),
**60 Hz** → frame budget **16.67 ms**.

Spike code lives in `app/src/main/java/com/musa/atomkeys/spike/` — **throwaway**, delete when Phase 1
starts for real. Registered in the manifest as a second IME (`AtomKeys (Phase 0 spike)`) so it can be
enabled alongside the user's normal keyboard.

- `SpikeImeService.kt` — the service + the view-tree owner plumbing (0.1)
- `SpikeKeyboard.kt` — grid, raw multitouch, reserved-headroom overlay (0.2, 0.3)
- `SpikeMetrics.kt` — latency / recomposition / pointer instrumentation (0.3)

---

## 0.1 — Compose inside an InputMethodService

### THE headline finding — owners must go on the **window decor view** ↯

The first on-device run **crashed immediately**, and this is the single most valuable thing Phase 0
produced:

```
java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from
  android.widget.LinearLayout{... android:id/parentPanel}
  at androidx.compose.ui.platform.WindowRecomposer_androidKt.createLifecycleAwareWindowRecomposer
  at androidx.compose.ui.platform.AbstractComposeView.resolveParentCompositionContext
  at androidx.compose.ui.platform.AbstractComposeView.onAttachedToWindow
  at android.inputmethodservice.InputMethodService.setInputView
```

**Setting the three owners on the view you return from `onCreateInputView()` is not sufficient.**
Compose resolves its window recomposer by walking **up** the view hierarchy, and
`InputMethodService.setInputView()` re-parents our view into the framework's own
`android:id/parentPanel` LinearLayout. Compose therefore starts its search from a view we never
touched, finds no owner, and throws.

**Fix:** set all three owners on the **IME window's decor view** — `window?.window?.decorView` — with
the *service* as the owner, since it outlives every input-view instance:

```kotlin
class SpikeImeService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private fun attachOwnersToWindowDecor() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
    }
}
```

Had this been discovered in Phase 1.3 it would have looked like "Compose doesn't work in an IME."
It does — the owners just have to go somewhere non-obvious.

Two consequences:

- Once the owners live on the decor view, **subclassing buys nothing** — `onCreateInputView` returns a
  plain `ComposeView` with `setContent {}`. Simpler than the `AbstractComposeView` route.
- **The lifecycle must reach `RESUMED`**, not just `CREATED`. Compose's lifecycle-aware recomposer
  pauses below `STARTED`, so a keyboard parked at `CREATED` renders once and then silently stops
  updating. Conveniently this is also what makes infinite animations (`atomShimmer`) stop on hide for
  free — roadmap 8.5's battery requirement.

### Also verified by compiling ✓

**`ComposeView` is `final` and cannot be subclassed.** The extension point is
`AbstractComposeView`, which takes content by overriding `@Composable fun Content()` rather than by
calling `setContent`:

```
e: SpikeImeService.kt:105:5 This type is final, so it cannot be extended.
```

(Moot in the final design, per above, but worth knowing.)

The three view-tree owners required, and where each comes from:

| Owner | Setter | Artifact |
|---|---|---|
| `LifecycleOwner` | `setViewTreeLifecycleOwner` | `androidx.lifecycle:lifecycle-runtime` |
| `ViewModelStoreOwner` | `setViewTreeViewModelStoreOwner` | `androidx.lifecycle:lifecycle-viewmodel` |
| `SavedStateRegistryOwner` | `setViewTreeSavedStateRegistryOwner` | `androidx.savedstate:savedstate` |

**Init ordering is load-bearing.** `SavedStateRegistryController.performAttach()` then
`performRestore(null)` must both run while the lifecycle is still `INITIALIZED`; only then advance to
`CREATED`. Restoring after the lifecycle has moved on throws.

### Ownership split, per the confirmed 0.1 decision

The composition is **recreated on each show**; state that must outlive a session lives in a
`ViewModelStore` **owned by the service**, not by the view:

```kotlin
class SpikeImeService : InputMethodService(), ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()   // survives view recreation
}
```

`onCreateInputView()` deliberately returns a *fresh* view each time. This is what makes the leak
plumbing unnecessary — there is no long-lived composition to leak — and it is why an infinite
animation like `atomShimmer` stops on hide for free (roadmap 8.5's battery requirement).

Lifecycle is driven from `onStartInputView` → `RESUMED`, `onFinishInputView` → `CREATED`,
`onDestroy` → `DESTROYED` + `disposeComposition()`.

### Measured on device ✓

- [x] **Composition renders.** Keycaps, Sage Mist palette, and the headroom band all draw correctly
      inside the IME window. Screenshot confirmed, not inferred.
- [x] **Survives 40 show/hide cycles with no leak.** The decisive metric is
      `dumpsys meminfo → Views`, which stayed **flat at 27** across all 40 shows — a leaked ComposeView
      tree per `onCreateInputView` would climb monotonically. `ViewRootImpl` 3, `AppContexts` 7,
      `Activities` 1, all flat.
      Java heap drifted 15.4 → 17.2 MB over the cycles but fell back to 16.3 MB after a forced trim,
      so it is collection lag rather than retention.
- [x] **Zero crashes across 40 cycles** (`grep -c 'FATAL EXCEPTION'` = 0 after the fix).
- [x] `onStartInputView` reached **#40**, all `restarting=false`.
- [ ] `restarting = true` path **not yet exercised** — needs a target app that restarts input on the
      same field (e.g. a search field that revalidates). Carry into Phase 1.3.
- [ ] Rotation not tested. Carry into Phase 8.6's matrix rather than re-opening the spike.

---

## 0.2 — Overlays without Popup

### Decision implemented: reserved headroom

Per the confirmed answer, the spike reserves a **56 dp band** at the top of the input view
(`HeadroomHeight`) and draws floating elements into it as an overlay `Box` layered over the grid,
inside the same composition. No `Popup`, no `Dialog`, no second window.

The press bubble is positioned from key bounds captured via `onGloballyPositioned` →
`positionInRoot()`, offset up by 60 dp. The design's `bottom: h + 6` places it outside the keycap's
own bounds, which is exactly why the band exists.

This one band then hosts all three floating elements the design needs — press bubble, gesture
pill/trail (3.3), and the extension deck (11.1) — rather than three separate solutions.

### Measured on device ✓

- [x] **The press bubble renders, with no `Popup`.** Held `h` down via
      `input motionevent DOWN` and screenshotted: the pressed key fills with `accent`, and the 30 sp
      bubble floats above it, overdrawing the row above. Drawn entirely inside the input view's
      composition. **0.2's approach works.**
- [x] Refinement: a bubble on a **middle- or bottom-row** key naturally overdraws the *key row above*,
      not the headroom band. Only **top-row** presses need the band. That matches how real keyboards
      behave, and means the 56 dp band is doing exactly one job — catching top-row bubbles.
- [ ] `Popup` still **not** measured as a negative control. The workaround is proven to work, so this
      is now low-value; leave it unless a future need for a real popup appears.
- [ ] Band height at key size XL still unchecked (spike is hardcoded to L/62 dp).

---

## 0.3 — Keypress latency & multitouch

### Implemented

- Touch handled with raw `pointerInput` + `awaitPointerEventScope`, **tracking each pointer id
  independently** into `pressedByPointer: Map<Long, String>`. A second finger down cannot cancel the
  first.
- **No `clickable`, no `Indication`, no ripple** anywhere on keys.
- Slide-off implemented by re-resolving the key on every move, removing the press if the pointer
  leaves all keys.
- Press state held in a `SnapshotStateMap` so ideally only affected keys recompose.

### Instrumentation

`SpikeMetrics` reports, into both the headroom band and logcat (tag `AtomSpike`):

| Metric | How |
|---|---|
| Time to visual feedback | pointer-down `System.nanoTime()` → `withFrameNanos` of the next frame |
| Time to commit | pointer-down → after `commitText()` returns |
| Key recompositions | `SideEffect` counter inside the key composable |
| Peak simultaneous pointers | max `changes.count { it.pressed }` |
| Lost pointers | pointer-ups with no matching tracked down |

Percentiles are p50/p95 over a rolling 200-sample window.

### Measured baseline ✓ — Compose is viable

**32 injected taps across all three rows, 60 Hz device, budget 16.67 ms:**

| Metric | p50 | p95 |
|---|---|---|
| pointer-down → next frame callback | **1.4 ms** | **1.5 ms** |
| pointer-down → `commitText()` returned | **6.7–7.4 ms** | 27.9 ms |

Feedback latency is **~1.4 ms against a 16.67 ms budget** — an order of magnitude of headroom.
**Compose is viable for the keyboard surface.** That was the question gating the whole project.

> **Scope caveat, stated plainly:** these are `adb shell input` **injected** events, so the numbers
> measure *our* processing from event receipt to frame callback. They exclude digitizer sampling and
> display latency, so they are **not** full touch-to-photon latency. They are the right baseline for
> Phase 8.5 regression-testing (same method, same device), and they establish that our code is not the
> bottleneck. A real touch-to-photon figure would need high-speed camera work and is not worth it here.

#### Harness bug found and fixed

The first run reported **`feedback p50 -5.2ms`** — negative. Cause: `withFrameNanos` supplies the time
the frame *started*, which can predate the pointer event that triggered the effect. Fixed by reading
`System.nanoTime()` *inside* the frame callback and ignoring the supplied `frameTimeNanos`. Worth
remembering for Phase 8.5, which will want the same measurement.

- [x] **Recomposition scoping — passes decisively.** On a 26-key grid, the `SideEffect` counter went
      **26 → 27 for a single press** (+1) and rose by ~23 over 32 taps. Had press state been unscoped it
      would have risen by 26 *per press*. Compose's parameter-level skipping handles this correctly
      with plain `Boolean` press params; **`drawWithCache`/`drawBehind` is not needed** for feedback.
- [x] **`lost` pointers: 0** throughout.
- [ ] **Multitouch genuinely unverified — `peakPtrs` never exceeded 1.** `adb shell input tap` emits one
      pointer at a time, so two- and three-finger simultaneity cannot be tested this way. **This needs
      real fingers** and is the one 0.3 item still open.

### Confirmed bug in the spike's hit-test — do not carry to Phase 3 ↯

Predicted as a limitation, then **empirically confirmed**. Tapping the sequence
`q t p a h l z m` committed **`qtpahlal`**: the two row-3 taps (`z`, `m`) resolved to the row-2 keys
above them (`a`, `l`).

Cause: `keyAt()` linearly scans key bounds and compares grid-local pointer coordinates against
root-space bounds with a `pos.y - size.height` fudge, so vertically adjacent keys both match and the
first hit wins.

- [ ] **Action for Phase 3:** compute key geometry once from the row/weight model (2.2) and hit-test
      arithmetically in a single coordinate space. Phase 6.2's raw-touch spatial model needs exactly
      this anyway, so build it properly once.

---

## How to run it

```sh
./gradlew installDebug          # needs a connected device or a running emulator
adb shell am start -n com.musa.atomkeys/.MainActivity
adb logcat -s AtomSpike
```

Then in the harness app: *Open input-method settings* → enable **AtomKeys (Phase 0 spike)** →
*Show keyboard picker* → select it → type in the field.

**Keep another keyboard enabled.** This is an unfinished IME; leaving it as the only input method is
how you end up unable to type.
