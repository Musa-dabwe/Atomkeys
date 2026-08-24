package com.musa.atomkeys.ime

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musa.atomkeys.design.AtomKeyboardTheme
import com.musa.atomkeys.design.AtomSizes
import com.musa.atomkeys.design.AtomTheme
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.design.AtomType
import com.musa.atomkeys.design.Keycap
import com.musa.atomkeys.design.KeycapFont
import com.musa.atomkeys.design.cornerRadius
import com.musa.atomkeys.design.gap
import com.musa.atomkeys.design.height
import com.musa.atomkeys.design.middleRuleInset
import com.musa.atomkeys.design.wrapPaddingBottom
import com.musa.atomkeys.design.wrapPaddingHorizontal
import com.musa.atomkeys.design.wrapPaddingTop
import com.musa.atomkeys.engine.AtomShimmer
import com.musa.atomkeys.engine.Candidate
import com.musa.atomkeys.engine.Gesture
import com.musa.atomkeys.engine.KeyAction
import com.musa.atomkeys.engine.KeySize
import com.musa.atomkeys.engine.KeyboardConfig
import com.musa.atomkeys.engine.KeyboardGeometry
import com.musa.atomkeys.engine.KeyboardLayout
import com.musa.atomkeys.engine.KeyPressTracker
import com.musa.atomkeys.engine.Row as LayoutRow
import com.musa.atomkeys.engine.RowItem

/**
 * The keyboard surface.
 *
 * Phase 3 renders; it does not interpret. Every piece of state — which layer, which shift state,
 * which key is pressed — arrives as a parameter, exactly as the mockup's props do. Touch handling
 * and the commit path are Phase 4, and [onAction] is the seam they will attach to.
 *
 * Structure follows the source's `wrap`: an optional candidate strip, then the rows from
 * [KeyboardLayout], on a `kbBg` background padded `8dp / gap / gap + 4`.
 *
 * @param reserveOverlayHeadroom the transparent band above the keyboard that spike 0.2 established
 *   as the answer to "floating elements without `Popup`". The press bubble on a **top-row** key
 *   sits outside the keyboard's own bounds; middle- and bottom-row bubbles simply overdraw the row
 *   above and need no band. Callers embedding the keyboard in ordinary UI — the settings live
 *   preview (5.2), screenshot tests (8.4) — pass `false`.
 * @param pressedKeyId the [RowItem.Key.id] currently held down, or `null`. A parameter in Phase 3;
 *   Phase 4.2 supersedes it for real touches — previews still use it to pin a pressed key.
 * @param invisible frame L7. Deliberately **not** part of [KeyboardConfig]: it changes nothing
 *   about which keys exist, and `KeyboardLayout.rows()` never reads it.
 * @param gesture the G1–G5 affordance to draw over the grid (a gradient trail plus the accent pill
 *   that labels it), or `null`. **Debug-only per the Phase 3.3 decision** — the frames call it
 *   "trail + pill shown at M swipe length" and call it an *annotation*, and showing it on every
 *   keystroke would be noise. The service never passes one; only previews and the dogfood flag do.
 * @param onAction receives the [KeyAction] resolved from a completed press. Phase 4.2 drives this
 *   from raw pointer events; the service interprets it against the `Editor`.
 */
@Composable
fun AtomKeyboard(
    config: KeyboardConfig,
    modifier: Modifier = Modifier,
    theme: AtomTheme = AtomThemes.Default,
    keySize: KeySize = KeySize.Default,
    keycapFont: KeycapFont = KeycapFont.ATOM,
    candidates: List<Candidate> = Candidate.Placeholder,
    showStrip: Boolean = true,
    invisible: Boolean = false,
    reserveOverlayHeadroom: Boolean = true,
    pressedKeyId: String? = null,
    gesture: Gesture? = null,
    onAction: (KeyAction) -> Unit = {},
) {
    AtomKeyboardTheme(theme = theme, keySize = keySize, keycapFont = keycapFont) {
        val rows = remember(config) { KeyboardLayout.rows(config) }

        Column(modifier.fillMaxWidth()) {
            if (reserveOverlayHeadroom) Spacer(Modifier.height(AtomSizes.overlayHeadroom))

            // `position: relative` — the gesture trail/pill anchor here, exactly as the source's
            // `wrap { position: relative }` does.
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.kbBg)
                        .padding(
                            PaddingValues(
                                top = keySize.wrapPaddingTop,
                                start = keySize.wrapPaddingHorizontal,
                                end = keySize.wrapPaddingHorizontal,
                                bottom = keySize.wrapPaddingBottom,
                            ),
                        ),
                    verticalArrangement = Arrangement.spacedBy(keySize.gap),
                ) {
                    if (showStrip) CandidateStrip(candidates, theme)

                    Grid(
                        rows = rows,
                        theme = theme,
                        keySize = keySize,
                        invisible = invisible,
                        previewPressedKeyId = pressedKeyId,
                        onAction = onAction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (gesture != null) GestureAffordance(gesture, theme, keySize, Modifier.matchParentSize())
            }
        }
    }
}

/**
 * The keyboard grid: the rows from [KeyboardLayout] plus the touch handling that resolves presses
 * to key actions.
 *
 * The grid is its own composable so the [pointerInput] modifier's coordinate space **is the grid's
 * coordinate space**. [KeyboardGeometry] resolves key rects arithmetically from the same weights
 * the renderer lays out, in one coordinate space (origin = top-left of the grid) — exactly what the
 * spike-0.3 failure demanded. A [pointerInput] here reports positions in that same space, so no
 * offset math or bounds scanning ever enters the picture.
 *
 * Press state (which keys are visually down) is held in snapshot state so only the touched keys
 * recompose — spike 0.3 measured that as +1 per press, not +26.
 *
 * @param previewPressedKeyId the pinned pressed key from a Phase 3 caller (previews), or null.
 *   Live touches take precedence; when no pointer is down, a pinned key is still shown pressed so
 *   the L1 press-bubble preview keeps working.
 */
@Composable
private fun Grid(
    rows: List<LayoutRow>,
    theme: AtomTheme,
    keySize: KeySize,
    invisible: Boolean,
    previewPressedKeyId: String?,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var livePressed by remember { mutableStateOf(emptySet<String>()) }
    val density = LocalDensity.current

    Column(
        modifier
            .pointerInput(rows) {
                val geometry = KeyboardGeometry.resolve(
                    rows = rows,
                    keySize = keySize,
                    widthPx = size.width.toFloat(),
                    densityPxPerDp = density.density,
                )
                val tracker = KeyPressTracker(
                    resolveKey = { x, y -> geometry.keyAt(x, y) ?: geometry.nearestKeyTo(x, y) },
                    densityPxPerDp = density.density,
                )
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val timeMillis = event.uptimeMillis
                        event.changes.forEach { change ->
                            val x = change.position.x
                            val y = change.position.y
                            when {
                                change.changedToDownIgnoreConsumed() ->
                                    tracker.onDown(change.id, x, y, timeMillis)

                                change.changedToUpIgnoreConsumed() ->
                                    tracker.onUp(change.id, x, y, timeMillis)?.let(onAction)

                                change.pressed ->
                                    tracker.onMove(change.id, x, y)
                            }
                        }
                        livePressed = tracker.pressedKeyIds
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(keySize.gap),
    ) {
        rows.forEach { row ->
            when (row) {
                is LayoutRow.MiddleRule -> MiddleRule(theme, keySize)
                is LayoutRow.Keys -> KeyRow(
                    row = row,
                    theme = theme,
                    keySize = keySize,
                    invisible = invisible,
                    pressedKeyIds = livePressed.ifEmpty {
                        previewPressedKeyId?.let { setOf(it) } ?: emptySet()
                    },
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * The G1–G5 annotation: a gradient trail in the swipe direction plus an accent pill with the
 * gesture's label, exactly as `AtomKeys.dc.html` draws them over the grid.
 *
 * All geometry is percentage-based, matching the source's CSS `left`/`top`/`width`/`height`. CSS
 * percentages resolve against the wrap's content box; Compose resolves them against `maxWidth` /
 * `maxHeight` here, which is the same box because the overlay fills the wrap exactly.
 */
@Composable
private fun BoxScope.GestureAffordance(
    gesture: Gesture,
    theme: AtomTheme,
    keySize: KeySize,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val accent = theme.accent

        val trail = when (gesture) {
            // { left: '22%', top: '46%', width: '46%', height: 3, background: 'linear-gradient(90deg, accent00, accent)' }
            Gesture.RIGHT -> TrailSpec(
                left = w * 0.22f,
                top = h * 0.46f,
                width = w * 0.46f,
                height = GESTURE_TRAIL_THICKNESS,
            )
            // { left: '26%', top: '46%', width: '46%', height: 3, background: 'linear-gradient(270deg, ...)' }
            Gesture.LEFT -> TrailSpec(
                left = w * 0.26f,
                top = h * 0.46f,
                width = w * 0.46f,
                height = GESTURE_TRAIL_THICKNESS,
            )
            // { left: '46%', top: '24%', width: 3, height: '44%', background: 'linear-gradient(180deg, ...)' }
            Gesture.DOWN -> TrailSpec(
                left = w * 0.46f,
                top = h * 0.24f,
                width = GESTURE_TRAIL_THICKNESS,
                height = h * 0.44f,
            )
            // { left: '46%', top: '26%', width: 3, height: '44%', background: 'linear-gradient(0deg, ...)' }
            Gesture.UP -> TrailSpec(
                left = w * 0.46f,
                top = h * 0.26f,
                width = GESTURE_TRAIL_THICKNESS,
                height = h * 0.44f,
            )
            // { left: '28%', bottom: '9%', width: '44%', height: h, background: accent26, borderRadius: 10 }
            // `bottom: 9%` with a height of `h` — the spacebar highlight sits over the spacebar.
            Gesture.SPACE -> TrailSpec(
                left = w * 0.28f,
                top = h - (h * 0.09f) - keySize.height,
                width = w * 0.44f,
                height = keySize.height,
            )
        }

        // The trail is a rounded line fading from transparent accent to solid accent along the swipe
        // direction. The `space` variant is a flat ~15% accent wash over the spacebar, not a line.
        val brush: Brush? = when (gesture) {
            Gesture.RIGHT -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent))
            Gesture.LEFT -> Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0f)))
            Gesture.DOWN -> Brush.verticalGradient(listOf(accent.copy(alpha = 0f), accent))
            Gesture.UP -> Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0f)))
            Gesture.SPACE -> null
        }
        val trailShape = RoundedCornerShape(
            if (gesture == Gesture.SPACE) GESTURE_SPACE_RADIUS else GESTURE_TRAIL_RADIUS,
        )
        Box(
            Modifier
                .offset(x = trail.left, y = trail.top)
                .size(trail.width, trail.height)
                .then(
                    if (gesture == Gesture.SPACE) {
                        Modifier.background(accent.copy(alpha = SPACE_HIGHLIGHT_ALPHA), trailShape)
                    } else {
                        Modifier.background(brush ?: Brush.linearGradient(listOf(accent, accent)), trailShape)
                    },
                ),
        )

        // The pill is centred on the grid for directional gestures, and at the top for `space`,
        // matching `pillStyle: { top: gesture === 'space' ? 12 : '50%', ... }`.
        val pillAlign = if (gesture == Gesture.SPACE) {
            Modifier.align(Alignment.TopCenter).offset(y = GESTURE_PILL_TOP)
        } else {
            Modifier.align(Alignment.Center)
        }
        Box(
            pillAlign
                .shadow(
                    elevation = GESTURE_PILL_ELEVATION,
                    shape = RoundedCornerShape(GESTURE_PILL_RADIUS),
                )
                .clip(RoundedCornerShape(GESTURE_PILL_RADIUS))
                .background(theme.accent)
                .padding(horizontal = GESTURE_PILL_PAD_X, vertical = GESTURE_PILL_PAD_Y),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(gesture.label, style = AtomType.gesturePill())
        }
    }
}

/** Percentage geometry for one gesture's trail. */
private data class TrailSpec(val left: Dp, val top: Dp, val width: Dp, val height: Dp)

@Composable
private fun KeyRow(
    row: LayoutRow.Keys,
    theme: AtomTheme,
    keySize: KeySize,
    invisible: Boolean,
    pressedKeyIds: Set<String>,
    onAction: (KeyAction) -> Unit,
) {
    Row(
        // No `clipToBounds`: a keycap's 1.5dp hard shadow paints below its own bounds, and a
        // top-row press bubble paints far above them. Clipping here would sever both.
        modifier = Modifier.fillMaxWidth().height(keySize.height),
        horizontalArrangement = Arrangement.spacedBy(keySize.gap),
    ) {
        row.items.forEach { item ->
            when (item) {
                is RowItem.Spacer -> Spacer(Modifier.weight(item.weight))
                is RowItem.Key -> KeySlot(
                    key = item,
                    theme = theme,
                    invisible = invisible,
                    pressed = item.id in pressedKeyIds,
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * One key, plus the press bubble it owns when held.
 *
 * The bubble lives inside the key's own box rather than in a shared overlay layer, mirroring the
 * source — where `popStyle` is `position: absolute` inside the key div. That keeps it anchored to
 * the key with no geometry calculation at all, so it cannot drift from what was laid out.
 */
@Composable
private fun RowScope.KeySlot(
    key: RowItem.Key,
    theme: AtomTheme,
    invisible: Boolean,
    pressed: Boolean,
    onAction: (KeyAction) -> Unit,
) {
    Box(Modifier.weight(key.weight).fillMaxSize()) {
        Keycap(
            label = key.label,
            modifier = Modifier.fillMaxSize(),
            kind = key.kind,
            labelScale = key.labelScale,
            hint = key.hint,
            pressed = pressed,
            invisible = invisible,
        )
        if (pressed && !invisible) PressBubble(key.label, theme)
    }
}

/**
 * The 46×54 bubble that floats above a held key — `bottom: h + 6` in the source, i.e. its lower
 * edge sits 6dp clear of the keycap's top edge.
 *
 * Spike 0.2 established that Compose `Popup` is the wrong tool inside an IME window (it allocates
 * its own window and misbehaves on positioning and focus), so this is an ordinary child drawn
 * outside its parent's bounds. Nothing in the ancestor chain clips.
 */
@Composable
private fun BoxScope.PressBubble(label: String, theme: AtomTheme) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            // `offset` shifts at placement without changing measured bounds, so the bubble draws
            // clear above the keycap without claiming any row height.
            .offset(y = -(AtomSizes.pressBubbleHeight + PRESS_BUBBLE_CLEARANCE))
            .size(AtomSizes.pressBubbleWidth, AtomSizes.pressBubbleHeight)
            .shadow(
                elevation = BUBBLE_SHADOW_ELEVATION,
                shape = RoundedCornerShape(AtomSizes.pressBubbleRadius),
            )
            .clip(RoundedCornerShape(AtomSizes.pressBubbleRadius))
            .background(theme.key)
            .border(
                width = AtomSizes.keyBorderWidth,
                color = theme.keyEdge,
                shape = RoundedCornerShape(AtomSizes.pressBubbleRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = AtomType.pressBubble())
    }
}

/**
 * The shimmering rule between letters rows 2 and 3.
 *
 * `atomShimmer` is `0%,100% { opacity: .14 } 50% { opacity: .55 }` over 2.6s ease-in-out infinite —
 * so a full cycle is 2.6s and each direction takes 1.3s. CSS `ease-in-out` is
 * `cubic-bezier(0.42, 0, 0.58, 1)`, which is *not* Compose's `FastOutSlowInEasing` (0.4, 0, 0.2, 1),
 * so the curve is spelled out rather than approximated.
 *
 * The animation stops on its own when the keyboard hides, because spike 0.1's answer recreates the
 * composition on each show — which is what roadmap 8.5's battery requirement asks for.
 */
@Composable
internal fun MiddleRule(theme: AtomTheme, keySize: KeySize) {
    val transition = rememberInfiniteTransition(label = "atomShimmer")
    val opacity by transition.animateFloat(
        initialValue = AtomShimmer.MIN_OPACITY,
        targetValue = AtomShimmer.MAX_OPACITY,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AtomShimmer.DURATION_MS / 2,
                easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "opacity",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = keySize.middleRuleInset,
                vertical = AtomSizes.middleRuleVerticalMargin,
            )
            .height(AtomSizes.middleRuleHeight)
            .alpha(opacity)
            .clip(RoundedCornerShape(AtomSizes.middleRuleRadius))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, theme.accent, Color.Transparent),
                ),
            ),
    )
}

/**
 * The 48dp candidate strip: a `⋯` extension button, a scrolling chip row, and a mic button.
 *
 * `⋯` and `◉` are the design doc's own stand-in glyphs. It says so explicitly, and roadmap 9.2
 * carries the decision to replace them with a real icon set before v1 — shipping them is a choice
 * to be made deliberately, not by default.
 */
@Composable
private fun CandidateStrip(candidates: List<Candidate>, theme: AtomTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AtomSizes.stripHeight)
            .padding(bottom = AtomSizes.stripBottomMargin),
        horizontalArrangement = Arrangement.spacedBy(AtomSizes.stripItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(AtomSizes.stripButtonSize)
                .clip(RoundedCornerShape(AtomSizes.stripButtonRadius))
                .background(theme.chip),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("⋯", style = AtomType.stripButton())
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AtomSizes.stripItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            candidates.forEach { CandidateChip(it, theme) }
        }

        Box(
            Modifier.size(AtomSizes.stripButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("◉", style = AtomType.micButton())
        }
    }
}

/**
 * One candidate chip, and the `·`·`·` sub-dots that hang below it when more candidates exist.
 *
 * Nested boxes rather than one: the source positions the dots at `bottom: -9` relative to the
 * chip's **outer** edge, so aligning them inside the padded content box would place them 5dp off.
 * The dots are offset at placement, so they draw below the chip without enlarging it.
 */
@Composable
private fun CandidateChip(candidate: Candidate, theme: AtomTheme) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AtomSizes.chipRadius))
                .background(if (candidate.isActive) theme.chipActive else theme.chip)
                .padding(
                    horizontal = AtomSizes.chipPaddingHorizontal,
                    vertical = AtomSizes.chipPaddingVertical,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(candidate.text, style = AtomType.chip(candidate.isActive))
        }
        if (candidate.hasMore) {
            BasicText(
                text = "···",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = AtomSizes.subDotsOffset),
                style = AtomType.subDots(),
            )
        }
    }
}

/** `bottom: h + 6` — the clearance between a keycap's top edge and the bubble's lower edge. */
private val PRESS_BUBBLE_CLEARANCE = 6.dp

/**
 * `box-shadow: 0 6px 18px rgba(0,0,0,0.16)` — a genuinely soft shadow, unlike the keycaps'. This is
 * the one place Material elevation is the right tool: it is a blurred drop shadow on a single
 * element, not a hard offset repeated 26 times.
 */
private val BUBBLE_SHADOW_ELEVATION = 6.dp

// --- Gesture affordance (G1–G5) geometry, from `AtomKeys.dc.html`'s `trails` / `pillStyle` ------

/** The line thickness of the directional trails (`height: 3` / `width: 3`). */
private val GESTURE_TRAIL_THICKNESS = 3.dp

/** `borderRadius: 3` on the directional trails. */
private val GESTURE_TRAIL_RADIUS = 3.dp

/** The `space` trail's `borderRadius: 10` — a soft-ended highlight over the spacebar. */
private val GESTURE_SPACE_RADIUS = 10.dp

/** `accent26` — the spacebar highlight sits at ~15% accent. */
private const val SPACE_HIGHLIGHT_ALPHA = 0.15f

/** `pillStyle: { padding: '6px 12px', borderRadius: 999 }` and its soft shadow. */
private val GESTURE_PILL_PAD_X = 12.dp
private val GESTURE_PILL_PAD_Y = 6.dp
private val GESTURE_PILL_RADIUS = 999
private val GESTURE_PILL_ELEVATION = 4.dp

/** `pillStyle: { top: gesture === 'space' ? 12 : '50%' }`. */
private val GESTURE_PILL_TOP = 12.dp
