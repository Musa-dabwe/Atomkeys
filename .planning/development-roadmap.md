# AtomKeys — Development Roadmap

A native Android keyboard (IME) built in Kotlin + Jetpack Compose, implementing the design
specified in `AtomKeyboard.dc.html` and `AtomKeys.dc.html`.

---

## How to use this file

- Work phases in order. Each phase has a **Gate** — do not start the next phase until it passes.
- Tasks are checkboxes. Tick them as you go; the file is the single source of progress truth.
- Anything marked **⚠ decision** is an open question the design docs do not answer. Resolve it
  and record the answer inline rather than guessing twice.
- Anything marked **↯ risk** is a known way this project can go wrong. Phase 0 exists to defuse most of them.

### Source of truth

| File | What it defines |
|---|---|
| `AtomKeys.dc.html` | The keyboard component: sizing math, layers, key states, row weights, candidate strip, gestures, press bubble |
| `AtomKeyboard.dc.html` | The design doc: 6 theme token sets, typography, frames D1–D3 / L1–L8 / G1–G5, extension deck (E1), 6 settings sections |

Both are Design-Doc HTML — they contain the real numbers (colors, dp values, weights, scale
factors). When implementing, read the values out of these files rather than eyeballing renders.

### Confirmed scope decisions

| Decision | Choice |
|---|---|
| Auto-correction | Built in-house, **phased** — basic engine in v1, quality work after |
| v1 scope | Core keyboard + full settings / setup, **English only**. Theme picker exposes **five** selectable themes (Sage Mist, Clay, Mauve, Claude Light, Claude Dark); **Oat stays internal** (deck chrome + fallback). Extension deck and multi-language come after v1 |
| Project structure | **Multi-module** (`:engine`, `:design`, `:ime`, `:app`) |
| Distribution | **Sideload / personal use** — signed release APK + GitHub release. No Play Console work |

### Target module graph

```
:app      settings screens + setup wizard        → :ime, :design, :engine
:ime      InputMethodService, keyboard UI,       → :design, :engine
          touch/gesture handling, InputConnection
:design   tokens, 6 themes, typography, sizing   → (android library)
:engine   dictionary, correction, layout math    → (pure Kotlin JVM — no Android deps)
```

`:engine` is deliberately Android-free so its tests run on the JVM in milliseconds. The
correction engine is the part that needs the most iteration, so it needs the fastest test loop.

### Starting state (as of this roadmap)

The repo is a stock Android Studio "Empty Compose Activity" scaffold. Nothing keyboard-related
exists yet, and it is **not under version control**.

- `gradle/libs.versions.toml` — AGP 8.5.1, Kotlin 2.0.0, Compose BOM 2024.06.00
- `app/build.gradle.kts` — compileSdk/targetSdk 34, minSdk 25
- `app/src/main/AndroidManifest.xml` — `MainActivity` only, no IME service
- `app/src/main/java/com/musa/atomkeys/MainActivity.kt` — `Greeting("AtomKeys")` stub
- `app/src/main/java/com/musa/atomkeys/ui/theme/{Theme,Color,Type}.kt` — template Purple/Pink
  Material3 with `dynamicColor = true`

> **These theme files get replaced, not extended.** The keyboard drives its own 14-token color
> system from the design doc. Material3 dynamic color and `colorScheme` are bypassed entirely for
> keyboard surfaces; Material3 stays only as chrome for the settings app.

---

## Phase 0 — Research & de-risking spikes

Throwaway code, written to answer a question. Each spike gets a short written answer in
`.planning/spikes/`. These are the findings most likely to invalidate later work if discovered late.

> **Status as of 2026-08-06 — Phase 0 essentially complete.** All seven spikes written up, and 0.1–0.3
> were **run on a real device** (OPPO Reno3 5G, Android 12, 60 Hz).
>
> | Spike | Written up in | Status |
> |---|---|---|
> | 0.1 Compose in an IME | `spikes/compose-in-ime.md` | **closed** ✓ — works, no leak over 40 cycles |
> | 0.2 Overlays without Popup | `spikes/compose-in-ime.md` | **closed** ✓ — reserved headroom works |
> | 0.3 Latency & multitouch | `spikes/compose-in-ime.md` | **closed** ✓ except real-finger multitouch |
> | 0.4 InputConnection surface | `spikes/inputconnection.md` | **answered** ✓ |
> | 0.5 Word-list licensing | `spikes/wordlist.md` | **answered** ✓ |
> | 0.6 Accessibility | `spikes/accessibility.md` | mechanism answered ✓ · one fork needs TalkBack on device |
> | 0.7 Prior art | `spikes/prior-art.md` | **answered** ✓ |
>
> ### The verdict that mattered: **Compose is viable** ✓
>
> Feedback latency **p50 1.4 ms / p95 1.5 ms against a 16.67 ms frame budget**, and recomposition is
> correctly scoped (**+1 per press on a 26-key grid**, not +26 — so no `drawWithCache` workaround is
> needed). Commit path p50 ~7 ms. Measured with injected events, so this is our processing cost, not
> touch-to-photon; it is the right baseline for Phase 8.5 to regress against.
>
> ### The finding that would have derailed Phase 1.3 ↯
>
> **Compose's view-tree owners must be set on the IME window's *decor view*, not on the view returned
> from `onCreateInputView()`.** Doing the obvious thing crashes with `ViewTreeLifecycleOwner not found
> from ... android:id/parentPanel`, because `setInputView()` re-parents our view into the framework's
> own LinearLayout and Compose resolves its recomposer by walking *up*. The service is the owner; the
> lifecycle must reach **RESUMED**, since Compose's recomposer pauses below STARTED. Details in
> `spikes/compose-in-ime.md`.
>
> **New work the spikes surfaced**, now folded into later phases:
> - Touch **noise rejection** (12.6 dp / 40 ms) — 4.2. Not in the design doc; without it the keyboard
>   registers phantom keys.
> - **MANUAL vs AUTOMATIC shift** as distinct logical states — 3.2/4.1. Four states, three visuals.
> - TalkBack drives an IME through **hover events**, so accessibility is a *second input path*, not a
>   flag on the existing one — 4.2/7.2.
> - **Password characters must not be announced aloud** — 7.2. A security leak the design doc omits.
> - **Profanity must never be an auto-correct target** — 6.2. It ranks 277–602 in the chosen word list.
> - Post-keystroke **gesture suppression window** (~500 ms) — 4.3.
> - **Key hit-testing must be arithmetic, from the row/weight model** — 3.1. The spike's bounds-scan
>   approach demonstrably mis-resolved row-3 taps to row-2 keys. Phase 6.2's spatial model needs real
>   geometry anyway.
> - **`ime enable` / `ime set` are blocked over adb on ColorOS** (Oppo restricts `WRITE_SECURE_SETTINGS`),
>   so enabling the keyboard cannot be scripted on this device — it needs taps through the picker.
>   Affects how Phase 8.3's instrumentation test and any CI automation are built.

### 0.1 Compose inside an InputMethodService ↯ risk

An `InputMethodService` is a `Service`, not a `LifecycleOwner`. A bare `ComposeView` returned from
`onCreateInputView()` **will not compose** — it needs view-tree owners that a Service does not provide.

- [x] Build a `ComposeKeyboardView` that implements `LifecycleOwner`, `ViewModelStoreOwner` and
      `SavedStateRegistryOwner`, backed by `LifecycleRegistry`, `ViewModelStore` and
      `SavedStateRegistryController`
      → **the *service* is the owner, not a custom view.** `ComposeView` is `final`, and once the owners
      move to the decor view (below) subclassing buys nothing — `onCreateInputView()` returns a plain
      `ComposeView` with `setContent {}`. `performAttach()` + `performRestore(null)` must run while the
      lifecycle is still `INITIALIZED`, before advancing to `CREATED`.
- [x] Wire it with `setViewTreeLifecycleOwner`, `setViewTreeViewModelStoreOwner`,
      `setViewTreeSavedStateRegistryOwner`
      → **on `window?.window?.decorView`, NOT on the input view.** Setting them on the input view
      crashes: `setInputView()` re-parents it into `android:id/parentPanel` and Compose walks *up* to
      resolve its recomposer. Also: the lifecycle must reach **RESUMED**, not just CREATED, or the
      composition renders once and silently stops updating.
- [x] Drive lifecycle transitions from the service callbacks: `onCreate`, `onStartInput`,
      `onStartInputView`, `onWindowShown`, `onWindowHidden`, `onFinishInput`, `onDestroy`
- [x] Verify no leak across show/hide cycles and across `onStartInputView(restarting = true)`
      → **40 show/hide cycles, zero crashes, `dumpsys meminfo → Views` flat at 27** (a leaked view tree
      would climb). Heap drift 15.4→17.2 MB reclaimed to 16.3 MB on forced trim, so GC lag not
      retention. `restarting=true` never triggered — carry that one case into Phase 1.3.
- **Answer:** **recreate the composition on each show.** Drive lifecycle minimal; keep any
      per-session/keyboard state in a `ViewModelStore` owned by the `InputMethodService` (which
      lives across sessions), not in the composition. This avoids leak plumbing, and lets the
      `atomShimmer` animation stop naturally when the view is destroyed on hide (see 8.5).

### 0.2 Overlays without Popup ↯ risk

Compose `Popup` and `Dialog` allocate their **own windows**. In an IME window this misbehaves —
wrong positioning, focus stealing, or nothing rendered at all. The design has three floating
elements that would naively reach for `Popup`:

- the key press bubble (`AtomKeys.dc.html` → `popStyle`, 46×54 at 12dp radius, positioned
  `bottom: h + 6` — i.e. **it floats above the keycap, outside the key's own bounds**)
- the long-press accent picker (mentioned as future work in the design doc)
- the extension deck

- [ ] Confirm `Popup` is unusable here (don't take it on faith — measure it) — **still unmeasured.**
      The spike implements the workaround without having demonstrated the problem; worth 10 minutes
      on-device to avoid cargo-culting.
- [x] Implement floating elements as an **overlay layer inside the input view's own composition**
      (a `Box` layered over the keyboard grid) → **works on device**, verified by screenshot with a key
      held down: pressed key fills with `accent`, 30 sp bubble floats above it. No `Popup` anywhere.
- [x] Determine whether the bubble can overdraw above the keyboard bounds via `clipToBounds = false`
      plus a non-clipping parent view, **or** whether `onCreateInputView` must reserve headroom at
      the top of the input view → **reserved headroom, 56 dp band.** Refinement from the device run:
      bubbles on middle/bottom-row keys overdraw the *key row above* them, so only **top-row** presses
      actually need the band. Band height at XL still unchecked.
- **Answer:** **reserve headroom at the top of the input view** (a clear band ~56px tall) rather
      than rely on `clipToBounds = false` overdraw, which is fragile inside an IME. One reserved
      band then cleanly hosts all three "float above the grid" elements (press bubble, gesture
      pill/trail in 3.4, and the deck in 11.1). Floating elements render as an overlay `Box`
      layered over the grid inside the input view's own composition — no `Popup`/`Dialog`.

### 0.3 Keypress latency & multitouch ↯ risk

This is the spike that decides whether Compose is viable for the keyboard surface at all. Fast
two-thumb typists put down overlapping pointers; a keyboard that drops or reorders those is unusable.

- [x] Handle touch with raw `pointerInput` + `awaitPointerEventScope`, tracking **each pointer id
      independently** to its own key
- [x] Confirm **no** Material `clickable` / `Indication` / ripple anywhere on keys — ripple
      allocates and animates per press
- [x] Confirm a press recomposes **only the touched key**, not the grid — hoist press state per key
      or draw press feedback with `drawWithCache` / `drawBehind`
      → **passes: 26 → 27 on a single press (+1), not +26.** Compose's parameter-level skipping handles
      it with a plain `Boolean` press param, so **`drawWithCache` is not needed.**
- [x] Measure touch-down → visible feedback and touch-down → `commitText`
      → **feedback p50 1.4 ms / p95 1.5 ms** against a 16.67 ms budget; **commit p50 ~7 ms**.
      Injected events, so this is our processing cost rather than touch-to-photon — the right baseline
      for Phase 8.5 to regress against. (Harness bug fixed en route: `withFrameNanos`' `frameTimeNanos`
      can predate the pointer event, which produced *negative* latencies; read the clock inside the
      callback instead.)
- [ ] Test with two and three simultaneous pointers, and with rapid alternating taps
      → **still open.** `adb shell input tap` emits one pointer at a time, so `peakPtrs` never exceeded
      1. **Needs real fingers** — the last open 0.3 item.
- **Budget to set here:** touch-down → visual feedback within one frame (~16ms at 60Hz).
      Record the measured baseline; Phase 8 benchmarks against it.

### 0.4 InputConnection surface

Map the API surface before designing around it. The keyboard talks to the target app **only**
through `currentInputConnection`.

- [x] `commitText` — normal key output
- [x] `setComposingText` / `finishComposingText` — the composing region that makes auto-correction
      and candidate replacement possible
- [x] `deleteSurroundingText` — backspace, and swipe-left word deletion
- [x] `getTextBeforeCursor` — word boundaries for delete-word, and context for auto-capitalization
- [x] `sendKeyEvent` — Enter and fallback paths
- [x] `EditorInfo.inputType` / `imeOptions` — picks the ✓-vs-newline action key, and identifies
      password/email/URL fields where correction and learning must be **off**
- [x] `EditorInfo.actionLabel` / `IME_ACTION_*` — what the ✓ key actually does per field
- [x] `IME_FLAG_NO_PERSONALIZED_LEARNING` — incognito fields must not feed the user dictionary
- **Answer:** ✓ `spikes/inputconnection.md`. An `Editor` interface plus a `FieldSpec` that digests
      `EditorInfo` **once** in `onStartInput`, so the privacy rules live in one place. Two findings
      beyond the plan: (1) `getTextBeforeCursor` is an IPC round-trip and must **not** be called per
      keystroke — keep a **shadow buffer** re-synced on `onUpdateSelection`; (2) the password variation
      that gets missed is `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, which looks like ordinary text.

### 0.5 Word-list licensing

- [x] Find a permissively licensed, **frequency-ranked** English word list
- [x] Record the license and its attribution requirements in `.planning/spikes/wordlist.md`
      → MIT confirmed by fetching the LICENSE file. The notice **must ship** with the asset.
- [x] Check size on disk — it ships inside the APK
      → 50k raw = 609 KB; after cleaning, **46,693 words / 177 KB gzipped**. Key finding: the list is
      already frequency-sorted, so **line index *is* the rank** — drop the counts entirely.
- **Decision (confirmed):** **FrequencyWords (HermitDave)** — MIT license, ~20k frequency-ranked
      English words, small on disk, easy trie build. Source it, record the MIT license
      + any attribution requirement, and confirm on-disk size in `.planning/spikes/wordlist.md`.
      A separate hand-built golden corpus (6.4) is required regardless and is independent of this.
### 0.6 Accessibility in an IME window ↯ risk

Genuinely uncertain territory, and easy to get wrong invisibly. TalkBack does **not** announce keys
in an IME window the way it announces controls in an app, and when touch exploration is on, touch
delivery changes — keys should fire on **lift**, not on down.

- [x] Determine how TalkBack actually traverses an IME rendered with Compose
      → **hover events** (`ACTION_HOVER_ENTER/EXIT/MOVE`), not touch. Ordinary down/up never fires.
- [x] Read `AccessibilityManager.isTouchExplorationEnabled` and branch touch handling accordingly
- [x] Spike explicit announcements for key identity
      → AOSP injects a synthetic `TYPE_ANNOUNCEMENT` `AccessibilityEvent` via
      `view.parent.requestSendAccessibilityEvent(...)` and explicitly **avoids** `TextToSpeech`.
- [x] Note how this backs the spec's *"Voice-over feedback · speaks each key for blind typing"*
      setting (`AtomKeyboard.dc.html` → Sound section) → confirms the 7.2 decision: injected events
      when TalkBack is on, our own TTS **only** when it is off.
- **Answer:** ✓ `spikes/accessibility.md`. Mechanism established; **one fork still open** — whether
      Compose's semantics tree is traversed correctly inside an IME window (cheap: per-key
      `contentDescription`) or whether we must hit-test hover on the host View and inject
      announcements manually (expensive: needs key geometry exported out of Compose). Needs a device
      with TalkBack. Also surfaced: **password characters must not be spoken aloud** unless
      `ACCESSIBILITY_SPEAK_PASSWORD` is set or a headset is connected — absent from the design doc.

### 0.7 Prior art

- [x] Read AOSP `LatinIME` for its handling of: backspace repeat-on-hold, slide-off-key behaviour,
      the shift / shift-lock state machine, and how it batches touch points for correction
      → real values read from `config-common.xml` and `KeyboardState.java`: key repeat **400 ms then
      50 ms**; long-press **300 ms** (min 100 / max 700); shift-lock long-press **1200 ms**;
      accessibility long-press **3000 ms**; touch-noise rejection **12.6 dp / 40 ms**. Shift is a
      **four**-state machine (`UNSHIFT / MANUAL_SHIFT / AUTOMATIC_SHIFT / SHIFT_LOCK_SHIFTED`).
      AOSP also keeps **raw coordinates** through to its scorer, confirming the 6.2 Route-A decision.
- [x] Note anything it does that the design doc's simpler model would break
      → **nothing breaks.** AtomKeys' five discrete gestures are a strict subset of LatinIME's
      continuous swipe-typing, and its huge gesture parameter set is not needed. Two additive gaps
      found: touch-noise rejection, and the MANUAL/AUTOMATIC shift distinction.

**Gate:** every spike above has a written answer in `.planning/spikes/`. 0.1, 0.2 and 0.3 are
blocking — the rest can be answered in parallel with Phase 1.
**Gate status: PASSED — Phase 1 is unblocked.** All seven spikes answered and written up; 0.1–0.3 were
run on a real device. Compose is confirmed viable (p50 1.4 ms feedback, correctly scoped
recomposition), the decor-view owner requirement is understood, and the reserved-headroom overlay
works.

Two small items intentionally left open, neither blocking:

- [ ] **Real-finger multitouch** (two and three simultaneous pointers) — cannot be injected via adb.
      Do this by hand before Phase 4.2 relies on per-pointer tracking.
- [ ] **TalkBack traversal of the Compose semantics tree** in an IME window (spike 0.6's one open fork)
      — decides whether Phase 7.2 is cheap (`contentDescription`) or expensive (manual hover hit-testing
      plus exported key geometry). Needed before Phase 7.2, not before Phase 1.

---

## Phase 1 — Foundation

Goal: a keyboard that can be enabled on a real device and commits one character. Nothing pretty.

### 1.1 Version control & toolchain

- [x] `git init`, commit the current scaffold and the two design docs as the baseline
- [x] Confirm `.gitignore` covers `local.properties`, `build/`, `.gradle/`, and (ahead of Phase 10)
      keystore files
      → **the scaffold's rule was wrong**: it ignored `/build` (root only), which would have committed
      every module's build output once the split happened. Now `build/`, plus `*.jks` / `*.keystore` /
      `keystore.properties` for Phase 10.
- [x] Upgrade AGP, Kotlin, the Compose BOM and `compileSdk` / `targetSdk` to current stable.
      **DONE 2026-08-06.** Versions verified against Google Maven / Maven Central metadata rather than
      assumed; three of the planned targets were wrong.

      | Item | Old (stale) | Planned | **Actually applied** | Note |
      |---|---|---|---|---|
      | AGP | 8.5.1 | 9.3.0 | **9.3.1** | current patch of the 9.3 line |
      | Gradle wrapper | 8.14.3 | 9.5.0 | **9.6.1** | current stable; ≥9.5 satisfies AGP 9.3 |
      | Kotlin | 2.0.0 | 2.2.x | **2.4.10** | plan was stale — 2.2 is not current |
      | Compose BOM | 2024.06.00 | 2026.06.00 | **2026.06.01** | |
      | compileSdk | 34 | 36 | **37** | forced: `core-ktx 1.19.0` requires 37+ |
      | targetSdk | 34 | 36 | **36** | left at 36 deliberately |
      | JDK | 17 | 17 | 17 | no change |

      **Verified:** `./gradlew assembleDebug` green, APK builds, `compileSdkVersion='37'`,
      `targetSdkVersion='36'`.

      Four things the upgrade turned up that the plan did not anticipate — all now fixed:

      1. **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` is a **hard
         error**, not a deprecation warning. It is removed from both build files. The Compose compiler
         plugin (`org.jetbrains.kotlin.plugin.compose`) is still separate and still required.
      2. **`kotlinOptions { jvmTarget }` is gone**, replaced by a top-level `kotlin { jvmToolchain(17) }`.
      3. **The scaffold had no `gradle.properties` at all.** The default 512 MB heap OOMs during D8 dex
         merging with the Compose dependency set, and surfaces as an opaque
         `Error while merging dex archives` — *not* as a recognisable OOM. Created with `-Xmx3g`,
         plus parallel/caching/configuration-cache.
      4. **The scaffold referenced `@mipmap/ic_launcher` but shipped no icon resources**, so resource
         linking failed. Added placeholder adaptive icons. Because minSdk is 26, `mipmap-anydpi-v26`
         alone is sufficient — **no density-specific PNGs needed**. Real icons remain a Phase 9.2 item.

      Also installed locally: `platforms;android-37.0` and `build-tools;37.0.0` (only 34 and 36 were
      present).
- [x] Decide `minSdk`. Currently 25; nothing in the design requires going that low, and a higher
      floor lowers compatibility branches (`e.g.` `VibrationEffect`, per-app language, etc.)
      **Decision (confirmed):** **minSdk 26** — removes the
      `VibrationEffect` (API 26+) fallback and most legacy-IME branches, and covers effectively all
      real devices for a personal sideload. **DONE.** Spike 0.4 confirmed a third payoff:
      `IME_FLAG_NO_PERSONALIZED_LEARNING` is also API 26, so incognito detection needs no version
      guard either. Spike 0.1 found a fourth: adaptive launcher icons need no legacy PNG densities.

### 1.2 Module split

- [x] Create `:engine` as a pure Kotlin JVM library (`org.jetbrains.kotlin.jvm`) — **no Android
      dependencies, enforced by having no Android plugin applied**
      → holds the spike-0.4 `Editor` interface plus `FakeEditor`, which records an **ordered call log**
      so Phase 4/8.2 can assert the operation *sequence*, not just resulting text. 18 tests.
- [x] Create `:design` as an Android library with Compose enabled
- [x] Create `:ime` as an Android library with Compose enabled — owns its own manifest and
      `method.xml`, so `:app` need not know the IME registration details.
- [x] Keep `:app` as the application module; wire dependencies per the module graph above
- [x] Register all modules in `settings.gradle.kts`
- [x] Move Compose/Android config into a convention plugin or shared block so four modules don't
      drift apart
      → **chose the version catalog over a buildSrc convention plugin.** `compileSdk`/`targetSdk`/
      `minSdk` live once in `libs.versions.toml`, read as `libs.versions.compileSdk.get().toInt()`.
      For four modules a convention plugin would add an AGP/KGP classpath to juggle and invalidate
      every build on change, to centralise three integers. Revisit if config diverges.
      Gotcha hit: every plugin a subproject applies must also be declared at the root with
      `apply false`, or it fails with *"already on the classpath with an unknown version"*.
- [ ] `:ime` exposes a reusable `AtomKeyboardPreview` composable (no service wiring) so `:app`
      settings can render a **live keyboard preview** (5.2) and Roborazzi (8.4) can screenshot the
      matrix through one shared renderer.
      → **deferred to Phase 2/3.** There is no real keyboard to preview yet; `PhaseOneKeyboard` is
      throwaway scaffolding. Build this against the actual composable rather than the placeholder.

### 1.3 IME registration

- [x] `AtomKeyboardService : InputMethodService` in `:ime`
- [x] Declare it in the manifest with `android:permission="android.permission.BIND_INPUT_METHOD"`,
      `android:exported="true"`, an `<intent-filter>` for `android.view.InputMethod`, and
      ~~`android:configChanges` covering orientation/screen-size/keyboard-hidden~~
      → **this roadmap item was wrong.** `android:configChanges` is an `<activity>` attribute; a
      `<service>` is not recreated on a configuration change, so it does not apply. The IME instead
      receives `Service.onConfigurationChanged`. Declared in `:ime`'s own manifest and merged into
      `:app`, so `:app` need not know the registration details.
- [x] `<meta-data android:name="android.view.im" android:resource="@xml/method"/>`
- [x] `res/xml/method.xml` with an English (US) QWERTY subtype, and `android:settingsActivity`
      pointing at the settings screen
- [x] Apply the Phase 0.1 lifecycle-owner pattern to the input view
      → owners on `window.window.decorView`, lifecycle to RESUMED on `onStartInputView`.
      **Hardened beyond the spike:** the spike latched a boolean once attached, but the decor view can
      be *recreated* on a configuration change, which would resurrect the
      `ViewTreeLifecycleOwner not found` crash after a rotation. Now tracks the decor-view identity and
      re-attaches from `onConfigurationChanged`.

### 1.4 Crash containment ↯ risk

An IME crash is uniquely bad: the user is left with **no keyboard**, and may not be able to type
their way out of it.

- [x] Install a top-level guard around key handling and composition so an unexpected exception
      degrades to a plain fallback keyboard rather than killing the service
      → `CrashGuard` + `KeyboardCrashState`. A failure **latches** the fallback rather than retrying:
      retrying a broken composition in a loop crashes repeatedly, and ColorOS marked the app
      "unstable" when the Phase 0 spike crashed — repeated crashes invite OEM restriction.
- [x] Log to a local file (no network) so a crash during dogfooding is diagnosable
      → `filesDir/crash.log`, capped at 128 KB, and tolerant of its own failure so a logging error
      cannot become a second crash. The app declares **no permissions at all**, INTERNET included.
- [x] Add a "reset to defaults" path reachable from the settings app, not only from the keyboard
      → crash-log viewer + clear button in `MainActivity`. Full settings reset lands with Phase 5.1,
      which is when there are actually settings to reset.

### 1.5 Fonts

- [x] Bundle Barlow Semi Condensed (400/500/600) into `res/font` — used for keycaps and hints
      → static instances, 105–110 KB each.
- [x] Bundle DM Sans (400/500/700) into `res/font` — used for UI, settings and candidate chips
      → **DM Sans now ships only as a variable font** (`DMSans[opsz,wght].ttf`); Google Fonts no longer
      publishes static instances. One 240 KB file covers every weight, but each must be requested
      through its `wght` axis via `FontVariation` — declaring `FontWeight.Medium` alone renders at the
      default weight. Needs API 26+, which is our minSdk. `FontVariation` is still
      `@ExperimentalTextApi`, contained to one function.
- [x] Verify licenses permit bundling (both are OFL on Google Fonts — confirm, don't assume)
      → confirmed by fetching both `OFL.txt`; the licence explicitly permits *embed*. Its condition is
      that the notice accompany distribution, so both texts ship in `res/raw/`. Surface them from an
      in-app licences screen in Phase 9.2.
- [ ] Subset if APK size matters; keycaps need a small glyph range, DM Sans needs more
      → **not needed yet.** 552 KB total for all four files. Revisit against Phase 8.5's APK budget.

**Gate: PASSED (verified on device 2026-08-06, OPPO Reno3 5G / Android 12).**

- [x] AtomKeys appears in *Manage keyboards* and can be enabled and selected
- [x] Typing into a real field commits characters — verified `"my name is musa\n"`, exercising the
      character, space and ✓/Enter paths through `Editor` and `FieldSpec`
- [x] Rotating the device does not crash it — portrait → landscape, keyboard re-raised and typed in
      landscape, **0 FATAL EXCEPTION, 0 `ViewTreeLifecycleOwner` errors, no `crash.log` written**
- [x] Switching apps does not crash it — home and back, keyboard re-raised

**Observed, feeding later phases:** at key height L the keyboard occupies most of a landscape screen.
That is empirical support for the 8.6 decision to clamp effective height to `M` in landscape, hide the
numbers row, and collapse the candidate strip.

---

## Phase 2 — Design system port

Goal: every number and color from the design doc lives in `:design`, typed and testable. No
keyboard yet — just the vocabulary the keyboard will be built from.

### 2.1 Theme tokens

The design doc defines **six** token sets, each with the **same 14 keys**:

`kbBg`, `key`, `keyEdge`, `keyShadow`, `keyText`, `fnKey`, `fnText`, `hint`, `accent`, `onAccent`,
`chip`, `chipText`, `chipActive`, `onChipActive`

- [x] `AtomTheme` data class over the 14 tokens, plus a `name`
      → `design/AtomTheme.kt`. `@Immutable`, so Compose skips any composable whose only changed
      input is an equal theme.
- [x] Transcribe all six token sets **verbatim** from `AtomKeyboard.dc.html` (the `SAGE`, `CLAY`,
      `MAUVE`, `OAT`, `CL`, `CD` constants): Sage Mist, Clay, Mauve, Oat, Claude Light, Claude Dark.
      **Decision (confirmed):** only **five are selectable** in the theme picker (Sage Mist, Clay,
      Mauve, Claude Light, Claude Dark) per the doc; **Oat stays internal** (extension-deck chrome
      E1 + FALLBACK). All six token tables are still transcribed and tested; the picker exposes five.
      → `AtomThemes.selectable` (5) vs `AtomThemes.all` (6) makes the distinction structural rather
      than a comment. **Discrepancy found between the two design docs:** `AtomKeyboard.dc.html`'s
      `OAT` has `hint: '#A0977F'`, `AtomKeys.dc.html`'s inline `FALLBACK` has `hint: '#A69C8B'`.
      Took the theme table as authoritative; recorded in the `Oat` KDoc.
- [x] `LocalAtomTheme` CompositionLocal + an `AtomKeyboardTheme` wrapper composable
      → `staticCompositionLocalOf`, not the tracking variant: a theme change re-themes every surface,
      so per-read bookkeeping on the typing hot path buys nothing. Two further locals ride along —
      `LocalKeySize` and `LocalKeycapFont` — so a keycap needs no size/font parameters.
- [x] Note that `chip` is a translucent value (`rgba(0,0,0,0.05)`, and `rgba(255,255,255,0.07)` in
      Claude Dark) — it composites over `kbBg`, so keep the alpha rather than flattening it
      → kept as alpha. Claude Light is a third case the roadmap did not note: **0.055**, not 0.05.
- [x] Delete the template `Color.kt` purple/pink values and the `dynamicColor` branch in `Theme.kt`
- [x] Keep a separate Material3 theme for the settings app chrome only
      → `:app`'s `AtomKeysTheme` now carries a warm-sand/coral scheme instead of Purple/Pink, and
      `Typography` uses DM Sans. Dynamic colour is gone deliberately: the settings screens host a
      **live keyboard preview** (5.2), and wallpaper-derived chrome around a fixed-palette keyboard
      would read as a mismatch.

### 2.2 Sizing scale

From `AtomKeys.dc.html`: `SIZES = { S: 50, M: 56, L: 62, XL: 68 }`, and everything else derives
from the chosen height `h`:

| Quantity | Formula |
|---|---|
| gap | `round(h * 0.1)` |
| key corner radius | `round(h * 0.18)` |
| letter label | `round(h * 0.4)` |
| fn label | `round(h * 0.28)` … `round(h * 0.36)` (per key — read the source) |
| spacebar label | `round(h * 0.26)` |
| emoji label | `round(h * 0.52)` |
| hint label | `max(9, round(h * 0.17))`, positioned `top h*0.1`, `right h*0.11`, 0.55 opacity |
| wrap padding | `8dp` top, `gap` horizontal, `gap + 4` bottom |

- [x] `KeySize` enum (S/M/L/XL) with `height` and the derived values as computed properties
      → `engine/KeyMetrics.kt`. Alongside it, `LabelScale` names all eight per-key scale factors the
      source passes to `labelStyle()`, and `AtomDimens` holds the sizes the doc states outright
      rather than derives (strip 48dp, chip 16dp radius, bubble 46×54, …).
- [x] Put the derivation math in `:engine` so it is unit-testable without an emulator
      → `:design` gained an `api(project(":engine"))` edge; `design/Dimens.kt` only wraps the
      integers in `Dp`/`TextUnit` and computes nothing.
- [x] Unit-test the derived values against the numbers above at all four sizes
      → 16 tests, written as full expected-value tables rather than by re-applying the formula (a
      test that recomputes the implementation proves nothing). Expected values cross-checked against
      **JavaScript `Math.round` semantics** — the source rounds half-*up*, Kotlin's `roundToInt`
      rounds half *away from zero*; they agree here only because every input is positive, and the
      one input that lands exactly on `.5` (`50 * 0.17`) is checked explicitly.

### 2.3 Keycap surface

- [x] Keycap composable reproducing the design's raised look: `1px solid keyEdge` border plus a
      `1.5dp` hard bottom shadow in `keyShadow` (`box-shadow: 0 1.5px 0`)
      → `design/Keycap.kt`. Fill, border and shadow are one `drawBehind` pass. The shadow spills
      1.5dp **below the keycap's own bounds**, exactly as CSS `box-shadow` does — it is paint, not
      layout — so Phase 3's row containers must not clip.
- [x] Draw it — **do not use Material elevation**, which produces a soft blurred shadow and
      allocates a render layer per key
      → and the same reasoning applied to the pressed `scale(0.95)`: `graphicsLayer` is attached
      **only while a key is pressed**, so a resting grid allocates zero render layers. Spike 0.3
      measured that press feedback needs nothing beyond Compose's parameter-level skipping, so 26
      permanent RenderNodes would be cost the measurement says we don't have to pay.
- [x] Variants from the source `keyStyle(kind)`: `key`, `fn`, `accent`, `emoji` (transparent, no
      border), `pressed` (fnKey fill, accent border, `scale 0.95`, shadow removed), and
      `invisible` (transparent with a dashed `keyEdge` border)
      → `KeyKind` covers the four *appearances*; `pressed` and `invisible` are orthogonal flags,
      matching the source, where they override the fill treatment but the label keeps its original
      kind's colour. One deliberate non-transcription: CSS does not specify `dashed`'s period, so
      L7's dash is a chosen 3dp on / 3dp off.
- [x] Label styling per kind, including `letterSpacing 0.02em` on fn keys
      → the source's `lineHeight: 1` needs **both** `includeFontPadding = false` and a trimmed
      `LineHeightStyle` in Compose; without them every label sits a few dp low and the whole grid
      reads as subtly misaligned.

### 2.4 Typography

- [x] `AtomType` exposing the two families and the weights the doc uses
      → `design/AtomType.kt` covers the chrome that does **not** scale with key height: chips,
      sub-dots, strip buttons, press bubble, gesture pill. Keycap labels are deliberately absent —
      they derive from `h`, so `Keycap` computes them from the sizing scale instead.
- [x] Support the *"Keyboard font: OS | Atom"* setting from the Look section — a switch between
      `FontFamily.Default` and Barlow Semi Condensed for keycaps
      → `KeycapFont` (already present from 1.5) is now wired through `LocalKeycapFont`. Its `family`
      getter was `@Composable` and is no longer: both branches are plain values, and a composable
      getter cannot be called from `AtomType`'s `@ReadOnlyComposable` styles.

**Gate: code complete — awaiting the visual comparison.**

- [x] Sizing math unit tests pass — 16 tests green in `:engine`
- [x] A Compose preview renders a keycap in all six themes at all four sizes
      → `design/KeycapPreview.kt`, three previews: the 6 × 4 gate matrix, every `keyStyle(kind)`
      branch and state, and the doc's own theme swatch rows.
- [ ] **MANUAL — the comparison itself.** Open `KeycapPreview.kt` in Android Studio and hold
      `AtomKeyboard.dc.html` next to it. Previews cannot be rendered from the command line, so
      "matches the design doc's swatches and proportions" is not something this build can assert.
      Phase 8.4's Roborazzi goldens are what make it assertable; until then it is an eyeball check.

---

## Phase 3 — Keyboard UI

Goal: reproduce every static frame in the design doc. Still no real input handling — state is
driven by parameters, exactly as the mockup does it.

### 3.1 Row & weight model

The mockup lays keys out with CSS flex weights. These map directly to `Modifier.weight`, including
the invisible spacers.

**DONE 2026-08-06.** The model lives in `:engine` — `KeyboardLayout.kt` (structure) and
`KeyboardGeometry.kt` (resolved rects + hit-testing), 34 tests. It holds no Compose types, for two
reasons that both trace back to Phase 0:

1. **Spike 0.3's confirmed hit-test bug.** The spike scanned laid-out bounds and compared
   grid-local pointer coordinates against root-space bounds with a fudge factor; `q t p a h l z m`
   committed **`qtpahlal`**. `KeyboardGeometry` resolves arithmetically from the same weights the
   renderer uses, in **one coordinate space** (origin = top-left of the key grid, which is exactly
   what a `pointerInput` on the grid reports). The spike's failing sequence is now a regression test.
2. **Phase 6.2 needs key centres** for proximity-weighted scoring, and it is a JVM-tested component,
   so its geometry source must be JVM-testable too.

`KeyKind` **moved from `:design` to `:engine`** — the row model has to classify keys, and `:design`
depends on `:engine`, not the reverse. It is a plain enum, which Compose already treats as stable,
so the `@Immutable` annotation was dropped rather than replaced.

- [x] Letters row 1: 10 keys at weight 1, each with a numeric hint (`1234567890`)
- [x] Letters row 2: `spacer(0.5)` + 9 keys + `spacer(0.5)`
- [x] Letters row 3: shift (1.5) + `spacer(0.15)` + 7 keys + `spacer(0.15)` + backspace (1.5)
- [x] Bottom row: `123`/`ABC` (1.3), special key (1), spacebar (4.2), `AC` (1), `.` (1), ✓ (1.5)
- [x] Symbols layer rows, including the `#+=` (1.5) + `spacer(0.2)` + punctuation + `spacer(0.2)` +
      backspace (1.5) row
      **Decision (CONFIRMED 2026-08-06):** `#+=` opens a **second symbols page**. `Layer` is
      therefore `LETTERS | SYMBOLS_1 | SYMBOLS_2 | EMOJI`. Page two carries brackets, math,
      currency and typographic marks (`[ ] { } # % ^ * + =` / `_ \ | ~ < > € £ ¥ •`) — chosen to
      cover what page one omits. `#+=` advances, `123` returns, `ABC` leaves symbols from either
      page. A test asserts page two never drifts structurally from the page the doc actually draws.
- [x] Emoji layer: 3 rows of 8, transparent keys
      **Decision (CONFIRMED 2026-08-06):** the bottom-row `☺` special key **opens the emoji layer**;
      the `,` variant commits a literal comma. Without this nothing in the design reaches L4.
- [x] Optional numbers row (permanent top row, fn-styled at `scale 0.32`) — note it **replaces**
      the per-key hints when enabled (`keyHints = false` in frame L5)
      → encoded as `KeyboardConfig.effectiveKeyHints`, so no caller has to remember the interaction.
      Also skipped on the emoji layer, per `layer !== 'emoji'` in the source.

**Beyond the checklist**, because the row model was the right place for them:

- **`KeyAction`** — a sealed hierarchy declaring what each key *does* (`Commit`, `SwitchLayer`,
  `Shift`, `Backspace`, `Space`, `Action`, `ToggleAutoCorrect`). Phase 4 interprets these; declaring
  them here means the renderer, the hit-tester and the commit path cannot drift apart.
- **`ShiftState`** — the **four** logical states spike 0.7 found in AOSP `KeyboardState.java`,
  rendering as **three** visuals. `MANUAL` and `AUTOMATIC` deliberately look identical; a test
  asserts that so the distinction is not "simplified away" later.
  One departure recorded: the mockup has a plain `shift` boolean and draws only **two** visuals
  (`⇧` fn / `⇪` accent). The roadmap's 3.2 asks for three. Implemented as the superset —
  OFF `⇧` fn, MANUAL/AUTOMATIC `⇧` accent, LOCKED `⇪` accent.
- **`TouchThresholds`** — spike 0.7's noise-rejection (12.6dp / 40ms) and hysteresis (8dp) numbers,
  with the stateless predicates. Phase 4.2 owns the stateful side.
- **Stable positional key ids** (`r2c4`), assigned in a post-pass. Needed for per-key press state
  and Compose `key()` calls; positional rather than label-derived because labels repeat — `.`
  appears both in the symbols punctuation row and on the bottom row.

### 3.2 Layers & states

Each of these corresponds to a numbered frame in the design doc — build them as previews so they
can be compared side by side with the source:

- [ ] **L1** Letters, with press bubble on a key
- [ ] **L2** Shift locked, case-sensitive keycaps (labels uppercase when shift is on)
- [ ] **L3** Symbols layer
- [ ] **L4** Emoji layer
- [ ] **L5** Numbers row extension
- [ ] **L6** Minimal mode — bottom row hidden, no middle rule
- [ ] **L7** Invisible keyboard — transparent keys, dashed edges, transparent labels
- [ ] **L8** Auto-correct off (`AC✕`) with comma as the special key
- [ ] Shift has three visual states: off (`⇧` fn), on, and locked (`⇪` accent-filled)

### 3.3 Chrome

- [ ] Candidate strip: 48dp tall — 40dp `⋯` extension button on a `chip` background, scrolling chip
      row (5×14 padding, 16dp radius, `chipActive`/`onChipActive` for the selected chip), 40dp mic
      button
- [ ] Sub-dots under the active chip (`·`·`·`, 11sp, 2px letter-spacing) indicating more candidates
      are available below
- [ ] Middle-row rule: 2.5dp tall, gradient `transparent → accent → transparent`, inset by
      `h * 0.5` horizontally, with the `atomShimmer` animation (opacity 0.14 → 0.55 → 0.14 over
      2.6s, ease-in-out, infinite). Use `rememberInfiniteTransition`
- [ ] Press bubble per Phase 0.2's answer — 46×54, 12dp radius, `key` fill, `keyEdge` border, a
      soft drop shadow, 30sp label, positioned above the pressed keycap
- [ ] Gesture affordances (G1–G5): the directional gradient trail and the accent pill label.
      **Decision (recommended):** shipping keyboard shows **no** gesture pill — it was annotation /
      documentation (the frames are labelled "trail + pill shown at M swipe length" as
      annotations), and showing every keystroke is noise. Gate them behind `BuildConfig.DEBUG` /
      a hidden flag so gestures can still be dogfooded.

### 3.4 Frame accuracy

- [ ] Reproduce **D1–D3** (in-context device frames): Sage Mist while composing, Claude Light with
      shift held, Claude Dark on the emoji layer
- [ ] Verify against the four key heights (S/M/L/XL) with `showStrip = false, minimal = true`, as
      the height section of the doc renders them

**Gate:** every frame L1–L8, D1–D3 and the four heights is reproducible as a Compose preview and
visually matches the design doc. A reviewer holding the HTML next to the previews finds no
discrepancy in color, weight or proportion.

---

## Phase 4 — Input & gestures

Goal: the keyboard actually types. This is where the Phase 0.3 latency budget must hold.

### 4.1 Commit path

- [ ] Route key output through the Phase 0.4 `InputConnection` wrapper
- [ ] Layer switching (`123` / `ABC` / `#+=`), and auto-return to letters after a symbol where the
      design implies it
- [ ] Shift: one-shot (auto-clears after one letter), and shift-lock on double-tap
- [ ] Enter / ✓ behaviour driven by `EditorInfo.imeOptions` — search, send, next, or newline
- [ ] Backspace with repeat-on-hold (accelerating), deleting composing text before committed text

### 4.2 Touch handling

- [ ] Per-pointer key tracking (from Phase 0.3) — a second finger down must not cancel the first
- [ ] Slide-off: moving off a key before lift cancels it
- [ ] Long-press with delay from the *Longpress delay* S/M/L setting — surfaces the numeric hint on
      letters row 1, and (post-v1) accented characters
- [ ] Fire on lift instead of down when touch exploration is enabled (Phase 0.6)

### 4.3 The five gestures

Thresholds come from the *Swipe length* S/M/L setting, expressed in dp (the doc annotates the
gesture frames as "M swipe length (40dp)", so M ≈ 40dp — calibrate S and L around it).
**Decision (recommended):** geometric scale mirroring the height enum — **S = 32, M = 40, L = 48dp.**

- [ ] Gesture detection and the tap-to-key commit model must be reconciled so a swipe does not
      stall a plain tap: a tap that *stays on one key* fires on release; if the pointer slides off
      the key past the threshold it is re-interpreted as a gesture candidate. Defined here so
      Phase 0.3's latency budget measures the right thing.

- [ ] **G1** Swipe right → insert a space, or accept the highlighted candidate if one is active
- [ ] **G2** Swipe left → delete the previous whole word (find the boundary via
      `getTextBeforeCursor`, then `deleteSurroundingText`)
- [ ] **G3** Swipe down → cycle to the next candidate
- [ ] **G4** Swipe up → revert the correction, and learn the raw word
- [ ] **G5** Hold spacebar → fine cursor control (relative horizontal drag moves the caret) and
      select-all. Highlight the spacebar region with `accent` at ~15% while active
- [ ] Each gesture independently toggleable per the Gestures settings section
- [ ] Ensure gesture detection does not add latency to plain taps — a tap must not wait to see
      whether it becomes a swipe

### 4.4 Typing behaviours

From the Typing and Dictionary settings sections:

- [ ] Double-space → period
- [ ] Autospace after punctuation (`,` `.` `!` `?`)
- [ ] Auto-capitalization (sentence start). **Decision:** use `InputConnection.getCursorCapsMode(0)`
      as the primary signal (it detects sentence boundaries correctly across emoji/spaces/
      punctuation) rather than hand-rolling context parsing; fall back to `getTextBeforeCursor`.
- [ ] Case-sensitive layout toggle (keycaps follow shift state)
- [ ] Suppress correction, suggestions **and** learning on password, email and URL fields
- [ ] Honour `IME_FLAG_NO_PERSONALIZED_LEARNING`

**Gate:** the keyboard is usable for real messages. All five gestures fire reliably. Latency
matches or beats the Phase 0.3 baseline. Typing in a password field neither corrects nor learns.

---

## Phase 5 — Settings, persistence & setup wizard

Goal: every switch in the design doc is real and takes effect immediately.

### 5.1 Persistence

- [ ] DataStore Preferences as the store, exposed as a `StateFlow` of a typed settings object
- [ ] The keyboard collects it, so a change in the settings app is reflected on the keyboard
      **without restarting the IME**
- [ ] Keep the IME and the settings activity in the **same process** (do not set `android:process`)
      so they share the DataStore instance cleanly
- [ ] Sensible defaults matching the design doc's shown states (height L, theme Sage, special key
      `☺`, swipe length M, longpress M, volume 2, vibration S, sound type 3)

### 5.2 Settings UI

Six sections, 64dp rows on the doc's warm sand surface, with four row types: **toggle** (42×25
track, 21dp knob), **segmented** (26dp tall, 8dp radius, accent fill on active), **value** (right-
aligned text), and plain navigation.

- [ ] **Languages** — active/downloaded/available list, spacebar-swipe-to-switch toggle.
      *v1 shows English only; the multi-language machinery is Phase 11, so this section is
      partially stubbed in v1 — mark clearly rather than shipping dead controls*
- [ ] **Look** — keyboard height (S/M/L/XL), minimal mode, padding size, keyboard font (OS/Atom),
      display middle row, special key (`☺`/`,`), theme picker across the five selectable themes
- [ ] **Gestures** — swipe length, longpress delay, and per-gesture toggles
- [ ] **Typing** — longpress for numbers, double-space period, autospace, case-sensitive layout
- [ ] **Dictionary** — auto-correction, correction after punctuation, auto-capitalization,
      auto-learn, my dictionary (with live word count, not the doc's placeholder 1,284)
- [ ] **Sound** — volume (0–3), vibration strength (S/M/L), sound type (1–4), voice-over feedback
- [ ] Live keyboard preview on the theme and height screens, so choices are visible without
      leaving settings

### 5.3 Setup wizard

A keyboard is useless until enabled, and Android's enable flow is genuinely confusing.

- [ ] Step 1: enable AtomKeys — deep link via `Settings.ACTION_INPUT_METHOD_SETTINGS`
- [ ] Step 2: select it — `InputMethodManager.showInputMethodPicker()`
- [ ] Step 3: a test field to type in, confirming it works
- [ ] Detect current state so the wizard skips completed steps and doesn't reappear once done
- [ ] Replace the `Greeting` stub in `MainActivity.kt` with the wizard-or-settings host

**Gate:** every control in the design doc's six sections exists, persists across process death, and
takes effect on the keyboard without a restart. A fresh install walks to a working keyboard.

---

## Phase 6 — Correction engine v1

Goal: correction good enough to type on daily, built in `:engine` with fast JVM tests. Per the
confirmed decision this is **phased** — v1 is a working baseline, not a tuned one.

The design doc's framing is specific: *"Auto-correction — tap-region analysis on raw touch points"*.
That means correction operates on **where fingers actually landed**, not just on the resolved
character sequence. Keep raw touch coordinates in the pipeline from the start; retrofitting them
later means rewriting the scorer.

### 6.1 Dictionary

- [ ] Bundle the Phase 0.5 frequency-ranked word list as a compact asset
- [ ] Build a trie (or DAWG if size demands it) for prefix lookup, loaded off the main thread
      **Decision (recommended):** start with a **compressed trie**; move to a DAWG only if memory
      overflows at the Phase 0.5 word-list size (~50k is fine in a trie).
- [ ] Measure load time and memory — this loads on every keyboard show unless cached
- [ ] Room-backed user dictionary: word + use count + timestamp
      **Decision (confirmed):** **Room** — **2.7 stable** (not the Room 3 alpha), tiny
      `word + count + timestamp` schema. The queries (my-list, count, delete) justify SQL; no
      alternate file-backed path.

### 6.2 Candidate generation

- [ ] Retain per-keystroke raw touch points alongside the resolved key
- [ ] Key-proximity model: distance from each touch point to nearby key centres, so an `n` tapped
      near the `m` edge yields both as plausible
- [ ] Key-proximity-weighted Damerau-Levenshtein against trie candidates (substitutions between
      adjacent keys cost less than between distant ones; transpositions are cheap)
- [ ] Score = spatial likelihood × unigram frequency, with a boost for user-dictionary words
      **Decision (recommended) — architecture fork:** commit to the doc's **raw-touch spatial model
      (Route A)** from the start: per keystroke keep raw pointer coordinates, compute a
      distance-weighted set of plausible resolved keys, then key-proximity-weighted Damerau-
      Levenshtein against the trie. Do **not** build the simpler "resolved-letters only" editor
      (Route B) — retrofitting raw touch later is a scorer rewrite. This gates Phase 4 to pass raw
      touch coordinates up the pipeline.
- [ ] Return a ranked list; top candidate goes in the composing region, the rest fill the chip row
- [ ] Correction after punctuation as a separate toggle (per the Dictionary section)

### 6.3 Learning

- [ ] Auto-learn on accept (space, ✓, or explicit chip tap), incrementing the use count
- [ ] Swipe-up (G4) reverts the correction **and** learns the raw word — the doc is explicit that
      it does both
- [ ] `AC` / `AC✕` key toggles correction inline without going to settings (frame L8)
- [ ] Never learn from password/email/URL fields or `NO_PERSONALIZED_LEARNING` contexts
- [ ] My-dictionary management (view, delete) in the settings app

### 6.4 Quality measurement ↯ risk

Correction quality is the long pole of this project and cannot be assessed by feel.

- [ ] Assemble a golden corpus of typo → intended-word pairs, ideally sampled from your own typing
      **Decision (recommended):** assemble ~200+ hand-annotated typo→intended pairs in parallel with
      6.1, drawing from your real typing. Do not rely on a borrowed corpus — a baseline accuracy
      target is set against *this* set. Requires a manual pass by you. (MANUAL but data work, not a
      one-line pick.)
- [ ] Automated accuracy metric over the corpus, run in CI-style on every engine change
- [ ] Set a target accuracy and a **no-regression rule** — a change that improves one case and
      breaks two is a loss
- [ ] Track false corrections separately from missed corrections; a keyboard that "corrects"
      correct words is worse than one that under-corrects

**Deferred to Phase 11:** bigram/context scoring, per-user spatial-model adaptation, and tuning
passes over the weights.

**Gate:** engine accuracy meets the stated target on the golden corpus. Daily typing is smoother
with correction on than off. No correction on password fields.

---

## Phase 7 — Feedback & accessibility

### 7.1 Sound & haptics

- [ ] Four sound types (per the Sound section), volume 0–3, with 0 = silent
- [ ] Respect the system's own keyboard-click setting — if the user silenced key clicks
      system-wide, honour it
- [ ] Vibration at S/M/L via `VibrationEffect`, respecting the system haptics setting
- [ ] Keep both off the main thread's critical path — audio/haptic dispatch must not delay commit

### 7.2 Accessibility

- [ ] Apply the Phase 0.6 findings: semantics or manual accessibility events, whichever the spike
      concluded
- [ ] Touch-exploration mode: fire keys on lift, announce on hover
- [ ] Voice-over feedback setting — speak each key for blind typing (TTS), per the doc
      **Decision (recommended):** gate this off whenever touch exploration (TalkBack) is active —
      otherwise TTS + TalkBack double-announce every key. Voice-over is *our* speech used only in
      the non-TalkBack blind-typing case.
- [ ] Verify contrast of all six themes against WCAG AA for the `keyText`/`key` and
      `onAccent`/`accent` pairs. **↯ risk:** dusty pastels look good and can quietly fail contrast
      **Decision (recommended):** `keyText` on `key` passes in all six (≈12:1). The failing pairs
      are `onAccent` (`#FFF`) on pastel accents — Sage `#7E9A80` ≈3.1:1, Clay `#BE8567` ≈3.1:1,
      Mauve `#9A7FA6` ≈3.6:1. All pass at large-text (AA large = 3:1) so keycaps/30sp bubble are
      fine; tune or document the smaller accent-on-chip labels. Claude Dark `onAccent` `#231F1D` on
      `#D97757` passes. Record failures deliberately in 7.2 — don't silently ship them.
- [ ] Respect system font scale in the settings UI (the keycaps themselves are intentionally fixed
      to the height scale, but the settings app should scale)
- [ ] Test the whole keyboard once end-to-end with TalkBack on

**Gate:** the keyboard is operable with TalkBack. All six themes pass AA on the text pairs above,
or their failures are documented and accepted deliberately.

---

## Phase 8 — Testing & hardening

Some testing happens per-phase; this phase is the systematic sweep.

### 8.1 Unit tests (`:engine`, JVM)

- [ ] Sizing/geometry math at all four heights
- [ ] Trie lookup and prefix behaviour
- [ ] Correction scoring — including the golden corpus from 6.4
- [ ] Word-boundary logic for swipe-left delete, against awkward inputs (emoji, punctuation runs,
      CJK, leading/trailing whitespace, empty field)
- [ ] Auto-capitalization context detection

### 8.2 Compose UI tests (`:ime`)

- [ ] Test keyboard composables in isolation with `createComposeRule` — do **not** try to test
      through the real service. Generate goldens from **our own Compose frames** (L1–L8 and
      themes), not from the `.dc.html` (whose `support.js`/design-doc runtime is not in this repo
      and may not render standalone). Roborazzi diffs our Compose render against committed pixel
      goldens of the same tree.
- [ ] Assert layer switching, shift states, minimal/invisible modes render the right key sets
- [ ] Drive the commit path against a **fake `InputConnection`** and assert exact emitted calls —
      this is where gesture and correction logic is genuinely verifiable

### 8.3 Instrumentation

- [ ] One end-to-end test: host activity with an `EditText`, real IME bound, type and assert the
      field's contents. Proves the real `InputConnection` path works; keep it to a smoke test
      because these are slow and flaky

### 8.4 Screenshot regression

- [ ] Roborazzi over the matrix: **6 themes × 4 heights × {letters, symbols, emoji, shift,
      minimal, invisible, numbers row}**
- [ ] Treat the design doc frames as the goldens
- [ ] Wire it so a color or spacing regression fails the build rather than being noticed months later

### 8.5 Performance

- [ ] Macrobenchmark for keypress latency, against the Phase 0.3 baseline
- [ ] JankStats during sustained typing — look for dropped frames under fast input
- [ ] Memory: dictionary footprint, and no leak across repeated show/hide (the Phase 0.1 risk)
- [ ] Battery: confirm nothing spins while the keyboard is hidden — no timers, no animation
      running off-screen (the shimmer must stop when hidden)
- [ ] APK size budget

### 8.6 Device & context matrix

- [ ] Small phone, large phone, tablet
- [ ] Portrait and landscape (landscape is where key heights get tight)
      **Decision (recommended):** in landscape/narrow widths, clamp effective height to `M`, hide
      the optional numbers row, and collapse/shrink the candidate strip — a usable-height rule the
      design doc does not define, so record it explicitly. (MANUAL if you want a different rule.)
- [ ] Split-screen and freeform windowing
- [ ] Gesture navigation vs 3-button navigation — this is what the *padding size* setting exists for
- [ ] System dark mode transitions
- [ ] RTL system locale (layout must not mirror the QWERTY grid, but chrome should)
- [ ] Across real target apps: messaging, browser URL bar, search fields, password fields, terminal
      emulators, and at least one app with a custom `EditText`

**Gate:** all suites green. Screenshot goldens committed. Latency at or better than baseline. The
matrix above is walked with findings logged.

---

## Phase 9 — Product completion review

Goal: an explicit, checkable answer to "is this the product that was designed?"

### 9.1 Design conformance checklist

Walk the design doc section by section with the build in hand:

- [ ] **Tokens** — all six theme swatch sets match, both type families in use as specified
- [ ] **D1–D3** — the three in-context device frames
- [ ] **L1–L8** — all layer and state frames
- [ ] **G1–G5** — all five gestures behave as annotated
- [ ] **Heights** — S/M/L/XL with gap, radius and label size scaling correctly
- [ ] **Settings** — all six sections, all rows, correct control types
- [ ] **Themes** — all six render correctly across every layer
- [ ] *(E1 extension deck is explicitly out of v1 scope — confirm it is deferred, not forgotten)*

### 9.2 Resolve the doc's own open questions

`AtomKeyboard.dc.html` ends by naming its own loose ends. Decide each:

- [ ] **⚠ decision** Replace the stand-in glyphs (`⋯`, `◉`, `⌫`, `⇧`, `✓`, settings icons) with a
      real icon set. The doc says these are placeholders — shipping them as-is is a choice, so make
      it consciously. **Decision (recommended): deploy an in-app vector icon set for the keyboard
      glyphs and a Material icon set for settings chrome in v1 — do not ship raw placeholder glyphs.
      (Not blocking earlier phases; glyphs are fine as intermediates.)**
- [ ] **⚠ decision** Long-press accented characters — v1 or Phase 11?
      **Decision (recommended):** **Phase 11.** v1 ships only the numeric-hint long-press (4.2);
      accented characters ride on the Phase 11 correction/layout work.
- [ ] **⚠ decision** Spacebar language switcher — tied to Phase 11 multi-language.
      **Decision (recommended):** **Phase 11.** In v1 the spacebar carries the `atom` brand only and
      does not cycle languages.
- [ ] **⚠ decision** The doc's stated assumptions: spacebar reads `atom`; extension tile colors are
      re-tinted to deeper pastels; Claude themes reuse Claude's own paper/ink/coral values. Confirm
      these still hold or correct them. **Decision (recommended): all three hold — transcribed
      verbatim from the constants; record them as confirmed, no correction needed.**

### 9.3 Dogfood

- [ ] Use AtomKeys as your **only** keyboard for at least two weeks
- [ ] Keep a running friction log — every moment you wanted to switch back is a defect
- [ ] Fix the friction log before calling v1 done
- [ ] Confirm the budgets hold in real use: latency, battery drain over a full day, APK size

**Gate:** conformance checklist complete, open decisions resolved and recorded, dogfood friction
log emptied.

---

## Phase 10 — Deployment (sideload)

Distribution is signed APK + GitHub release. No Play Console, no store listing, no policy forms.

### 10.1 Signing

- [ ] Generate a release keystore. **Back it up somewhere durable** — losing it means future builds
      can no longer be installed as updates over existing ones
- [ ] Keep the keystore and its credentials **out of the repo** — reference via `local.properties`
      or environment variables, and confirm `.gitignore` covers both
- [ ] Wire the release signing config in `app/build.gradle.kts`

### 10.2 Release build

- [ ] `versionCode` / `versionName` scheme, incremented per release
- [ ] Enable R8 (`isMinifyEnabled = true` — currently `false`) with Compose-safe keep rules
- [ ] Verify the minified release build actually works on device — R8 breaking reflection-dependent
      paths is a classic late surprise
- [ ] Optional: baseline profile for faster first keyboard show
- [ ] Decide universal APK vs per-ABI splits (universal is simpler for sideloading) **⚠ decision**
      **Decision (recommended): universal APK** — simplest for a personal sideload; revisit size
      with per-ABI splits only if Phase 8.5's APK budget actually demands it.
- [ ] `./gradlew assembleRelease`, then `apksigner verify --print-certs` on the output

### 10.3 Publish

- [ ] Tag the release in git
- [ ] GitHub release with the APK, a changelog, and the APK's SHA-256 so installs are verifiable
- [ ] `README` install instructions: download → allow unknown sources → install → enable in
      *Languages & input* → select via the keyboard picker
- [ ] Document the update path: same keystore means installing over the top preserves settings and
      the learned dictionary. **A different keystore forces an uninstall and loses both**
- [ ] Note the backup/restore story for the user dictionary — `android:allowBackup` is currently
      `true`, so decide whether that's what you want for a keyboard's learned words **⚠ decision**
      **Decision (confirmed):** keep `android:allowBackup = true` (default) so the learned
      dictionary survives via Google backup across devices/reinstalls-with-keystore-loss.

**Gate:** a signed release APK installs on a clean device, works fully, and installs cleanly as an
update over the previous version without data loss.

---

## Phase 11 — Post-v1

Sequenced, not scheduled. Each is independently shippable.

### 11.1 Extension deck (design doc E1)

- [ ] **⚠ decision** First: are tiles real plugins, or built-in features behind flags? The doc
      does not say, and this is an architectural fork — a plugin API is a much larger commitment
      **Decision (recommended):** **built-in features behind flags** for v2 of the deck, with a
      clean tile interface so a plugin boundary could be introduced later without a rewrite. Revisit
      the plugin API path only if third-party tiles become real. (MANUAL — architectural fork worth a
      conscious call, but default is built-in.)
- [ ] Deck UI: 4-column grid of 76×76 tiles at 12dp radius, solid pastel fills, glyph over an 11sp
      label in white or deep ink depending on fill lightness
- [ ] Shelf above the grid: mounted extensions, **max 4 slots so the candidate strip never scrolls**
- [ ] Drag a tile up into the shelf to activate it
- [ ] The 12 tiles named in the doc: Highlights, 123 Row, Editor, Launcher, Hotkeys, Shortcuts,
      Rainbow, Invisible, Heart Pops, Color Pops, Clipboard, Add
- [ ] Note that *123 Row* and *Invisible* already exist as settings in v1 — reconcile the overlap
      rather than implementing them twice

### 11.2 Multi-language

- [ ] Layout abstraction supporting QWERTY, AZERTY, QWERTZ and Dvorak
- [ ] `method.xml` subtypes per language
- [ ] Spacebar horizontal swipe cycles active languages
- [ ] Per-language dictionaries, and a download mechanism (the doc shows "↓ Get" states) — **note
      this introduces the app's first network access**, which changes the privacy story
- [ ] Language-aware correction

### 11.3 Correction engine v2

- [ ] Bigram/context scoring
- [ ] Per-user spatial model adaptation (learn where *you* actually hit each key)
- [ ] Weight tuning passes against an expanded corpus

### 11.4 Other

- [ ] Clipboard history
- [ ] Long-press accent picker (if deferred at 9.2)
- [ ] Real icon set (if deferred at 9.2)

---

## Phase dependencies

```
0 ─┬─> 1 ──> 2 ──> 3 ──┬──> 4 ──> 5 ──> 6 ──> 7 ──> 8 ──> 9 ──> 10 ──> 11
   │                   │
   └─ 0.1/0.2 gate 1&3 └─ 0.3 gates 4
      0.4 gates 4
      0.5 gates 6
      0.6 gates 7
```

- Phases 2 and 3 can overlap once tokens exist.
- Phase 6 can start in parallel with 4/5 — it is a separate module with no UI dependency.
- Phase 8's per-suite work should be written **during** the phases it covers; Phase 8 is the sweep
  and the matrix, not the first time tests get written.

---

## Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| **IME crash blast radius** | A crash leaves the device with no keyboard, possibly unrecoverable without another IME installed | Phase 1.4 crash guard + fallback view; keep a second keyboard installed while dogfooding |
| **Compose latency on the keyboard surface** | Typing is the most latency-sensitive interaction on a phone; recomposition per keystroke can make it feel mushy | Phase 0.3 spike gates the whole approach; no ripple, per-key state hoisting, benchmarked in 8.5 |
| **Correction quality is the long pole** | The hardest part is invisible in mockups; a mediocre engine makes an otherwise beautiful keyboard unusable | Measure with a golden corpus (6.4) from day one; phase the engine rather than gating v1 on perfection |
| **Compose-in-IME plumbing** | Lifecycle owners and the `Popup` limitation are non-obvious and affect root layout | Phases 0.1 and 0.2 answer both before Phase 3 commits to a layout |
| **Accessibility in an IME window** | Behaves unlike a normal app; easy to ship silently broken | Phase 0.6 spike, explicit TalkBack pass in 7.2 |
| **Pastel contrast** | Dusty pastels are the whole visual identity and are exactly the palette that fails AA | Contrast audit in 7.2 across all six themes |
| **Keystore loss** | Loses the ability to ship updates over existing installs; users lose learned dictionary | Phase 10.1 backup requirement |
