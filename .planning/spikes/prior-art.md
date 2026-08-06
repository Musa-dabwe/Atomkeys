# Spike 0.7 — AOSP LatinIME prior art

**Status:** answered
**Date:** 2026-08-06
**Gates:** Phase 4.2 (touch handling), Phase 4.1 (shift state machine), Phase 6.2 (touch batching)

Values below are **read from AOSP source**, not recalled:

- `packages/inputmethods/LatinIME/java/res/values/config-common.xml`
- `packages/inputmethods/LatinIME/java/src/com/android/inputmethod/keyboard/internal/KeyboardState.java`

---

## Timing constants worth adopting wholesale

LatinIME has absorbed a decade of tuning. Do not invent these numbers; start here.

| Behaviour | AOSP value | Use in AtomKeys |
|---|---|---|
| Key-repeat initial delay | **400 ms** | Backspace repeat-on-hold (4.1) |
| Key-repeat interval | **50 ms** | Backspace repeat rate (4.1) |
| Long-press default | **300 ms** | The *Longpress delay* `M` setting |
| Long-press min / max / step | **100 / 700 / 10 ms** | Bounds for `S` and `L` — so `S ≈ 200`, `M = 300`, `L ≈ 500` |
| Shift-lock long-press | **1200 ms** | Long-press shift → shift-lock, as an alternative to double-tap |
| Long-press in accessibility mode | **3000 ms** | Phase 7.2 — TalkBack users need far longer |

**Note the accessibility value: 3000 ms, a 10× multiplier.** That is not a rounding tweak, it is a
different interaction model, and it is easy to omit. Wire it to
`AccessibilityManager.isTouchExplorationEnabled` in 7.2.

### Touch noise rejection — a behaviour the design doc does not mention

| Constant | Value |
|---|---|
| `config_touch_noise_threshold_distance` | **12.6 dp** |
| `config_touch_noise_threshold_time` | **40 ms** |

A touch that travels more than ~12.6 dp and is released within 40 ms is discarded as noise — a palm
brush or an accidental graze, not a keypress. Without this, a keyboard registers phantom keys.

- [ ] **Action for Phase 4.2:** implement noise rejection with these thresholds. Not optional.

### Key hysteresis

`config_key_hysteresis_distance_for_sliding_modifier` = **8.0 dp** is the only hysteresis constant in
`config-common.xml`; the general key-hysteresis distance is defined per-keyboard elsewhere, so treat
8 dp as the starting point rather than a verified global.

The mechanism matters more than the number: while a pointer is down, **the active key's hit box is
expanded**. Small drift during a press does not switch keys. Implement this in 4.2 alongside
slide-off — they are two halves of the same behaviour, and slide-off without hysteresis makes fast
typing lose characters.

## Shift state machine — four states, not two

`KeyboardState.java` distinguishes:

```java
private static final int UNSHIFT = 0;
private static final int MANUAL_SHIFT = 1;
private static final int AUTOMATIC_SHIFT = 2;
private static final int SHIFT_LOCK_SHIFTED = 3;
```

The roadmap (3.2) currently plans **three** visual states: off, on, locked. AOSP's extra distinction
is **`MANUAL_SHIFT` vs `AUTOMATIC_SHIFT`** — shift the user pressed, versus shift auto-capitalization
turned on.

They must behave differently:

- Backspacing over an auto-capitalized letter should **undo the automatic shift**; backspacing under
  a manual shift should not silently drop the user's explicit intent.
- Typing a letter clears both, but a punctuation/space may re-trigger automatic shift while manual
  shift should persist per its own rule.

This is invisible in a static mockup, which is exactly why it needs recording now.

- [ ] **Action for Phase 3.2 / 4.1:** model **four** logical shift states even though only **three**
      render differently. `AUTOMATIC_SHIFT` and `MANUAL_SHIFT` share the "on" visual.

### Chording and double-tap

- `mShiftKeyState.isChording()` — holding shift while tapping a letter is a *chord*, distinct from
  tap-shift-then-tap-letter. On release of a chord, state returns to `UNSHIFT` rather than staying on.
- `startDoubleTapShiftKeyTimer()` / `isInDoubleTapShiftKeyTimeout()` — double-tap → shift-lock is a
  timer, and it is **separate** from the 1200 ms long-press path. AOSP supports both routes to
  shift-lock.

The roadmap's 4.1 says "shift-lock on double-tap". Prior art says offer the long-press route too;
it costs almost nothing once the timer exists.

## Layer switching is also a state machine

```java
SWITCH_STATE_ALPHA, SWITCH_STATE_SYMBOL_BEGIN, SWITCH_STATE_SYMBOL,
SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL, SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE,
SWITCH_STATE_MOMENTARY_ALPHA_SHIFT
```

The `MOMENTARY_*` states implement **hold-`123`-to-peek**: press and hold the layer key, type one
symbol, release, and you snap back to letters — as opposed to a latching tap. `SYMBOL_BEGIN` exists
so the *first* keypress after entering symbols can behave differently from later ones (that is how
"auto-return to letters after a symbol" is scoped without making the whole layer momentary).

This directly informs roadmap 4.1's "auto-return to letters after a symbol where the design implies
it" — the design doc doesn't specify, and this is the well-trodden answer.

- [ ] **⚠ decision for Phase 4.1:** adopt momentary layer peek, or tap-latch only? Recommend
      supporting both, as AOSP does — the machinery is one shared state enum.

## Gesture typing: AtomKeys' model is deliberately much simpler ✓

LatinIME's continuous swipe-typing carries a large parameter set — dynamic distance/time thresholds
that decay (`600% → 50%`, `300 ms → 20 ms`), speed thresholds, sampling minimum distance, a
post-fast-typing static threshold of 500 ms, trail interpolation with angular thresholds.

**AtomKeys does not need any of this.** The design specifies five *discrete directional* gestures
(G1–G5) with a single dp threshold, not continuous word-shape recognition. That is a far smaller
problem, and the roadmap's `S = 32 / M = 40 / L = 48 dp` model is appropriate.

Two things are still worth stealing:

1. **`config_gesture_static_time_threshold_after_fast_typing` = 500 ms.** After fast typing, suppress
   gesture recognition briefly. Fast typists' finger drift otherwise fires spurious swipes. This
   directly protects roadmap 4.3's "a swipe must not stall a plain tap" requirement from the
   opposite failure — taps being misread as swipes.
2. **Sampling minimum distance** — do not evaluate a gesture on every touch move event; sample.

- [ ] **Action for Phase 4.3:** add a post-keystroke gesture suppression window (~500 ms starting
      point, tune during dogfooding).

## Touch-point batching for correction

LatinIME accumulates raw touch coordinates into an `InputPointers` batch and passes them, with a
`ProximityInfo` key-adjacency map, to a native suggestion engine over JNI.

The architecture is the confirmation the roadmap's 6.2 "Route A" decision needs: **AOSP keeps raw
coordinates through to the scorer** rather than resolving to characters first. AtomKeys' plan matches
established practice.

The JNI/C++ part is a performance optimization we should **not** copy in v1 — Kotlin in `:engine` is
the right call for a 46k-word list. Revisit only if Phase 8.5 benchmarks demand it.

## Where AtomKeys deliberately departs

| LatinIME | AtomKeys | Why it's fine |
|---|---|---|
| Continuous swipe-typing word recognition | Five discrete gestures | Different feature; design doc is explicit |
| Native C++ suggestion engine over JNI | Kotlin `:engine` | 46k words is small; JVM testability is worth more |
| Popup key-preview in a separate window | Overlay in reserved headroom (0.2) | `Popup` is unusable in an IME window |
| Views + XML keyboard definitions | Compose | The whole premise of the project |

## Nothing found that breaks the design doc's model

The simpler discrete-gesture model does not conflict with anything in LatinIME; it is a subset.
The two genuine gaps this spike surfaced are **touch noise rejection** and the
**MANUAL vs AUTOMATIC shift distinction** — both additive, neither contradicting the design.
