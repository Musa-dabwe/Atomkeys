package com.musa.atomkeys.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.musa.atomkeys.engine.AtomDimens
import com.musa.atomkeys.engine.LabelScale

/**
 * A keycap's visual family, from the design doc's `keyStyle(kind)` switch.
 *
 * This is *appearance*, not behaviour: `123`, shift and backspace are all [FN] even though they do
 * unrelated things. What a key does belongs to the layout model in Phase 3.
 */
@Immutable
enum class KeyKind {
    /** Letters, symbols, the spacebar and `.` — [AtomTheme.key] fill, [AtomTheme.keyEdge] border. */
    KEY,

    /** Shift, backspace, `123`/`ABC`, `#+=`, the special key, `AC` — [AtomTheme.fnKey] fill. */
    FN,

    /** The ✓ key, and shift when locked — [AtomTheme.accent] fill, no separate border colour. */
    ACCENT,

    /** Emoji-layer keys — fully transparent, no border, no shadow. */
    EMOJI,
}

/**
 * One keycap: the design's raised slab, drawn rather than elevated.
 *
 * The raised look is `1px solid keyEdge` over `box-shadow: 0 1.5px 0 keyShadow` — a *hard* shadow
 * offset straight down with no blur and no spread. Material elevation is deliberately not used:
 * it renders a soft blurred shadow, which is the wrong shape, and it allocates a render layer per
 * key. Fill, border and shadow are drawn in a single [drawBehind] pass instead.
 *
 * The shadow spills 1.5dp below the keycap's own bounds, exactly as CSS `box-shadow` does — it is
 * paint, not layout. Row containers must therefore not clip.
 *
 * @param label what the key shows. Case is the caller's business: the doc's shift-on frames use
 *   uppercase labels, so the layer model uppercases before it gets here.
 * @param labelScale one of the [LabelScale] factors. The label size is `round(h * scale)`.
 * @param hint the numeric hint on letters row 1, or `null`. Suppressed when the numbers row is on.
 * @param pressed replaces the whole fill/border treatment per the source's `pressed` kind — a
 *   [AtomTheme.fnKey] fill inside an [AtomTheme.accent] border, scaled to 0.95, shadow removed.
 *   The *label* keeps the colour of its original [kind], which is what the mockup does.
 * @param invisible frame L7 — transparent fill, dashed edge, transparent label and hint.
 */
@Composable
fun Keycap(
    label: String,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.KEY,
    labelScale: Double = LabelScale.LETTER,
    hint: String? = null,
    pressed: Boolean = false,
    invisible: Boolean = false,
) {
    val theme = AtomKeyboard.theme
    // Named `keySize`, not `size` — inside `drawBehind` a local `size` would shadow the
    // DrawScope's own `size`, which is what the rects below are measured against.
    val cornerRadius = AtomKeyboard.keySize.cornerRadius

    Box(
        modifier = modifier
            // `transform: scale(0.95)` on press. Applied only *while* pressed, so a resting grid
            // allocates zero render layers — spike 0.3 confirmed press feedback needs no drawing
            // machinery beyond parameter-level skipping, and 26 permanent RenderNodes would be
            // cost the measurement says we do not have to pay. At most one key wears this at a
            // time per pointer.
            .then(
                if (pressed && !invisible) {
                    Modifier.graphicsLayer {
                        scaleX = AtomDimens.PRESSED_SCALE
                        scaleY = AtomDimens.PRESSED_SCALE
                    }
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val radius = CornerRadius(cornerRadius.toPx())
                val stroke = AtomDimens.KEY_BORDER_WIDTH_DP.dp.toPx()

                if (invisible) {
                    // L7: nothing but a dashed outline. CSS does not specify `dashed`'s period, so
                    // 3dp on / 3dp off is a deliberate choice, not a transcription.
                    drawRoundRect(
                        color = theme.keyEdge,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = radius,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(DASH_ON_DP.dp.toPx(), DASH_OFF_DP.dp.toPx()),
                            ),
                        ),
                    )
                    return@drawBehind
                }

                if (kind == KeyKind.EMOJI) return@drawBehind

                val fill = when {
                    pressed -> theme.fnKey
                    kind == KeyKind.ACCENT -> theme.accent
                    kind == KeyKind.FN -> theme.fnKey
                    else -> theme.key
                }
                val edge = when {
                    pressed -> theme.accent
                    kind == KeyKind.ACCENT -> theme.accent
                    else -> theme.keyEdge
                }

                // `box-shadow: 0 1.5px 0` — the same rounded rect, offset straight down, drawn
                // first so the keycap covers all but the exposed sliver. A pressed key drops it.
                if (!pressed) {
                    drawRoundRect(
                        color = theme.keyShadow,
                        topLeft = Offset(0f, AtomDimens.KEY_SHADOW_HEIGHT_DP.dp.toPx()),
                        size = size,
                        cornerRadius = radius,
                    )
                }

                drawRoundRect(color = fill, size = size, cornerRadius = radius)
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = radius,
                    style = Stroke(width = stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = keycapLabelStyle(kind, labelScale, invisible),
        )
        if (hint != null) KeyHint(hint, invisible)
    }
}

/**
 * The numeric hint on letters row 1 — absolutely positioned at `top h*0.1, right h*0.11` and drawn
 * at 0.55 opacity in [AtomTheme.hint].
 *
 * Frame L5 turns these off wholesale when the permanent numbers row is enabled, so the caller
 * passes `hint = null` rather than this composable deciding.
 */
@Composable
private fun BoxScope.KeyHint(hint: String, invisible: Boolean) {
    val theme = AtomKeyboard.theme
    val size = AtomKeyboard.keySize
    BasicText(
        text = hint,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = size.hintTop, end = size.hintRight),
        style = TextStyle(
            fontFamily = AtomKeyboard.keycapFont.family,
            fontSize = size.hintSize,
            color = if (invisible) Color.Transparent else theme.hint.copy(alpha = AtomDimens.HINT_OPACITY),
            lineHeight = size.hintSize,
            lineHeightStyle = TightLineHeight,
            platformStyle = NoFontPadding,
        ),
    )
}

/**
 * `labelStyle(kind, scale)` from the source.
 *
 * `lineHeight: 1` in CSS means the glyph box is exactly the font size, which in Compose needs both
 * `includeFontPadding = false` and a trimmed line-height style — otherwise the label sits a couple
 * of dp low inside the keycap and the whole grid reads as subtly misaligned.
 */
@Composable
private fun keycapLabelStyle(kind: KeyKind, scale: Double, invisible: Boolean): TextStyle {
    val theme = AtomKeyboard.theme
    val size = AtomKeyboard.keySize
    val fontSize = size.labelSize(scale)
    return TextStyle(
        // Emoji render in the system font: the keycap face has no emoji coverage, and the design
        // doc asks for `system-ui` here for exactly that reason.
        fontFamily = if (kind == KeyKind.EMOJI) FontFamily.Default else AtomKeyboard.keycapFont.family,
        fontSize = fontSize,
        fontWeight = if (kind == KeyKind.EMOJI) FontWeight.Normal else FontWeight.Medium,
        letterSpacing = if (kind == KeyKind.FN) AtomDimens.FN_LETTER_SPACING_EM.em else 0.em,
        color = when {
            invisible -> Color.Transparent
            kind == KeyKind.ACCENT -> theme.onAccent
            kind == KeyKind.FN -> theme.fnText
            else -> theme.keyText
        },
        lineHeight = fontSize,
        lineHeightStyle = TightLineHeight,
        platformStyle = NoFontPadding,
        textAlign = TextAlign.Center,
    )
}

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Suppress("DEPRECATION")
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private const val DASH_ON_DP = 3f
private const val DASH_OFF_DP = 3f
