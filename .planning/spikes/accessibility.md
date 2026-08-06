# Spike 0.6 — Accessibility in an IME window

**Status:** partially answered — mechanism established from AOSP; Compose behaviour needs device verification
**Date:** 2026-08-06
**Gates:** Phase 4.2 (touch handling branch), Phase 7.2 (all of it)

Source read: `packages/inputmethods/LatinIME/java/src/com/android/inputmethod/accessibility/AccessibilityUtils.java`

---

## Finding 1 — TalkBack delivers **hover** events, not touch ✓ verified

This is the answer to "how does TalkBack traverse an IME". AOSP:

```java
public static boolean isTouchExplorationEvent(final MotionEvent event) {
    final int action = event.getAction();
    return action == MotionEvent.ACTION_HOVER_ENTER
        || action == MotionEvent.ACTION_HOVER_EXIT
        || action == MotionEvent.ACTION_HOVER_MOVE;
}
```

When touch exploration is active the system routes the finger as **hover**. The keyboard announces the
key under the hovering finger; the actual keypress comes from a separate confirming action. Ordinary
`ACTION_DOWN`/`UP` handling never runs.

**Consequence:** the Phase 4.2 task "fire keys on lift instead of down" understates the change. It is
not a timing tweak — it is a **second, parallel input path** keyed on hover events. Plan for two
code paths, not one path with a flag.

- [ ] **Action for Phase 4.2:** implement a hover-driven input path, selected by
      `AccessibilityManager.isTouchExplorationEnabled`. Budget real time for it.

## Finding 2 — announcements are injected events, not TTS ✓ verified

AOSP does **not** use `TextToSpeech` to announce keys. It synthesizes an accessibility event and
pushes it at the screen reader, with an explicit comment calling this out:

> The following is a hack to avoid using the heavy-weight TextToSpeech class. Instead, we're just
> forcing a fake AccessibilityEvent into the screen reader to make it speak.

```java
final AccessibilityEvent event = AccessibilityEvent.obtain();
event.setPackageName(PACKAGE);
event.setClassName(CLASS);
event.setEventTime(SystemClock.uptimeMillis());
event.setEnabled(true);
event.getText().add(text);
event.setEventType(AccessibilityEventCompat.TYPE_ANNOUNCEMENT);
viewParent.requestSendAccessibilityEvent(view, event);
```

Note it goes through **`view.getParent()`** — `requestSendAccessibilityEvent` is called on the parent,
not the view. An IME input view without a suitable `ViewGroup` parent silently fails to announce
(AOSP logs and bails). Worth asserting during the device pass.

**This validates the roadmap's 7.2 decision** to gate our own TTS off when TalkBack is active. The two
channels are genuinely distinct:

| Situation | Channel |
|---|---|
| TalkBack / touch exploration on | Injected `TYPE_ANNOUNCEMENT` events — let the screen reader speak |
| *Voice-over feedback* setting on, TalkBack **off** | Our own `TextToSpeech` (the blind-typing case from the design doc) |

Never both. Using TTS while TalkBack is on double-announces every key.

## Finding 3 — passwords must not be spoken aloud ↯ risk, not in the design doc

`shouldObscureInput(EditorInfo)` gates on the `Settings.Secure` **`ACCESSIBILITY_SPEAK_PASSWORD`**
flag. When obscuring, AOSP speaks "dot" in place of the actual character, and separately handles a
*"connect a headset to hear passwords"* notification.

Announcing a password character-by-character through a phone speaker is a security leak. The design
doc says nothing about this — it is invisible in a mockup — and it is exactly the kind of omission
that ships.

- [ ] **Action for Phase 7.2:** obscure announced characters on password fields unless
      `ACCESSIBILITY_SPEAK_PASSWORD` is set or a headset is connected. Reuse `FieldSpec` (spike 0.4),
      which already computes password-ness — add an `obscureAnnouncements` flag there rather than
      re-deriving it.

## Finding 4 — accessibility long-press is 3000 ms

From `config-common.xml` (see spike 0.7): `config_accessibility_long_press_key_timeout = 3000`, versus
a 300 ms default. A 10× multiplier, not a tweak.

## Open — needs on-device verification ⚠

Everything above is the **View-based** AOSP mechanism. What is genuinely unresolved is how much of it
Compose gives us for free inside an IME window:

1. **Does Compose's semantics tree get traversed correctly in an IME window?** Compose maps
   `Modifier.semantics` onto `AccessibilityNodeInfo` via `AndroidComposeView`. Whether explore-by-touch
   hover events resolve to the right Compose node *inside an IME window* is unverified. IME windows are
   not normal app windows, which is the whole reason this spike exists.
2. **If it works:** per-key `Modifier.semantics { contentDescription = ... }` may be sufficient, and we
   skip the manual event injection entirely.
3. **If it does not:** we need AOSP's approach — intercept hover on the host `View` (outside the
   composition), hit-test against key bounds we publish out of Compose, and inject announcements
   manually. That is substantially more work and needs the key geometry exposed from the layout.

**This is the fork that cannot be resolved from source reading.** It needs TalkBack on a device.

- [ ] **Blocked task:** run the 0.1–0.3 spike app with TalkBack enabled and determine which branch
      applies. Record the answer here before Phase 7.2 starts.

## Contrast audit — deferred to 7.2, but note the method

The roadmap's 7.2 already carries contrast figures for the six themes. Two corrections to method when
that task runs:

- Keycap labels are large (letter label = `h * 0.4`, so **24.8 sp at height L**) and the press bubble
  is 30 sp. Both clear the WCAG "large text" bar, where AA is **3:1**, not 4.5:1.
- The pairs that actually risk failing are the **small** ones: candidate chip text at 15 sp on
  `chipActive`, and fn-key labels at `h * 0.28` (≈17 sp). Those need 4.5:1 and are the real audit
  targets — not the keycaps.

## Summary for Phase 7.2

| Item | Status |
|---|---|
| Hover-event input path | Mechanism verified — implement as a second path |
| Announcement channel | Verified — inject `TYPE_ANNOUNCEMENT`, do not use TTS when TalkBack is on |
| Password announcement obscuring | Verified requirement — **new**, not in the design doc |
| 3000 ms accessibility long-press | Verified value |
| Compose semantics sufficiency in an IME window | **Unresolved — needs a device** |
