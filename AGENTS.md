# AGENTS.md — AtomKeys

Native Android keyboard (IME) in Kotlin + Jetpack Compose, built from two design-doc HTML files. Work is driven phase-by-phase off a roadmap with strict gates.

## Current position (verify against the roadmap before starting work)

- Phase 0 (spikes), 1 (foundation), 2 (design system) complete; gates passed and verified on device.
- Phase 3.1 row/weight model committed. Phase 3 rendering is **in progress and uncommitted**: `ime/.../AtomKeyboard.kt`, `AtomKeyboardPreview.kt`, `ChromePreviews.kt`, `ShiftStatesPreview.kt`, `engine/.../Candidate.kt`, and `AtomKeyboardService.onKeyAction` (service already wired to render `AtomKeyboard`). Finish and commit this before moving on.
- Phases 4–11 not started: no touch/gesture handling (4), no settings/persistence (5), no correction engine (6), no feedback/accessibility (7), no test sweep (8).

## Source of truth

- `.planning/development-roadmap.md` — the progress tracker; phase checkboxes are the single source of truth. Work phases in order, tick boxes, and don't start a phase before its Gate passes. Read the "Current position" summary at the top of each phase.
- `.planning/spikes/*.md` — hard-won findings. Read `compose-in-ime.md` before touching the service, `inputconnection.md` before Phase 4/6, `accessibility.md` before Phase 7.
- `AtomKeys.dc.html` (keyboard component: sizes, layers, weights, gestures) and `AtomKeyboard.dc.html` (design doc: 6 theme token sets, frames, settings) contain the **real numbers** — colors, dp values, scale factors. Read values out of the HTML rather than eyeballing renders. Where they conflict, `AtomKeyboard.dc.html`'s theme table is authoritative.

## Architecture

- Multi-module Gradle: `:engine` (pure Kotlin JVM, no Android deps — ms tests; layout/geometry/sizing/correction math lives here), `:design` (tokens, themes, keycap surface; Android library), `:ime` (InputMethodService + Compose keyboard UI; owns its own manifest + `res/xml/method.xml`), `:app` (settings/setup host, depends on all three).
- Dependency direction: `:engine` ← `:design` ← `:ime` ← `:app`. Things both Compose and non-Compose code need (e.g. `KeyKind`, `KeyAction`, `KeyboardConfig`) live in `:engine`, not `:design`.
- SDK levels live once in `gradle/libs.versions.toml` (compileSdk 37, targetSdk 36, minSdk 26), read via `libs.versions.compileSdk.get().toInt()`. No convention plugin.

## Toolchain gotchas (AGP 9 + Kotlin 2.4)

- **Never apply `org.jetbrains.kotlin.android`** — a hard error under AGP 9 (built-in Kotlin support). The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is separate and still required.
- `kotlinOptions { jvmTarget }` is gone → use top-level `kotlin { jvmToolchain(17) }`.
- Every plugin a subproject applies must also be declared at the root `build.gradle.kts` with `apply false`, or the build fails "already on the classpath".
- `gradle.properties` requires `-Xmx3g`; a smaller heap OOMs D8 as an opaque "Error while merging dex archives" (not a recognisable OOM).

## Build & test

- `./gradlew assembleDebug` — full build.
- `./gradlew :engine:test` — the fast JVM loop for any layout/geometry/sizing/correction change. This is the primary verification target; most math lives here.
- `./gradlew :ime:test` — JVM tests for the IME module (`FieldSpecTest`).
- No CI, no lint config beyond AGP defaults. Compose previews cannot be rendered from the CLI, so visual conformance is a manual Android Studio eyeball check against the `.dc.html`.

## IME-specific constraints (spike findings that bite)

- Compose inside an `InputMethodService`: set view-tree owners (lifecycle/ViewModelStore/SavedState) on `window.window.decorView`, **not** the view returned from `onCreateInputView()` — the framework re-parents it and Compose walks *up*, crashing with `ViewTreeLifecycleOwner not found`. Lifecycle must reach **RESUMED**. Recreate the composition on each show; keep per-session state in a service-owned `ViewModelStore`. The decor view is recreated on rotation — re-attach owners from `onConfigurationChanged`, tracked by decor-view identity.
- No `Popup`/`Dialog` inside the IME window (they allocate their own windows and misbehave). Floating UI (press bubble, gesture pill, extension deck) is an overlay `Box` inside the composition; reserve a ~56dp transparent headroom band at the top. Rows must not clip (keycap shadow paints below bounds).
- Keyboard surfaces deliberately avoid Material3: no `clickable`/ripple on keys (per-press allocation + latency budget), raw `pointerInput` with per-pointer tracking instead; use foundation `BasicText`/`Box`. Material3 is only for settings-app chrome.
- Shift is a **four**-state machine (`OFF/MANUAL/AUTOMATIC/LOCKED`) rendering as three visuals; the MANUAL/AUTOMATIC distinction is deliberate — don't collapse it.
- An IME crash leaves the user keyboardless: keep the `CrashGuard` → `FallbackKeyboard` degrade path intact, and never retry a failed composition in a loop (repeated crashes invite OEM restriction). The app declares no permissions at all, INTERNET included; crash logs are local files only.

## Device testing quirks

- Verified on-device (OPPO Reno3 5G, Android 12). `adb shell input` cannot simulate multitouch (needs real fingers — a known open spike item), and `ime enable`/`ime set` are blocked by ColorOS, so enabling the keyboard requires manual taps through the picker.
- `run_keyboard_test.sh` drives adb keycodes/screencaps into `debug_screens/`.
