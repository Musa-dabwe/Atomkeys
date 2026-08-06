package com.musa.atomkeys.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.design.AtomFonts

/**
 * **Phase 1 keyboard — intentionally minimal.**
 *
 * Phase 1's gate is only that the IME can be enabled and commits characters into a real field. The
 * real layout — row/weight model, layers, shift states, candidate strip, press bubble — is Phase 3,
 * and the token system it should draw from is Phase 2. Nothing here is the design; it exists so the
 * plumbing can be verified end to end.
 *
 * Two deliberate shortcuts, both to be undone:
 *  - Colours are inlined Sage Mist values, so it is not unpleasant to look at. Phase 2.1 replaces them.
 *  - Keys use `clickable` rather than raw `pointerInput`. Phase 0.3 established that `clickable` is
 *    wrong for the real keyboard (no ripple, per-pointer tracking), but Phase 1 is verifying the
 *    commit path, not touch handling. Phase 4.2 replaces this.
 */
private val KbBg = Color(0xFFDDE3DA)
private val KeyBg = Color(0xFFF4F7F1)
private val KeyEdge = Color(0xFFC9D2C4)
private val KeyText = Color(0xFF333B33)
private val FnKey = Color(0xFFCCD5C7)
private val FnText = Color(0xFF4E574C)
private val Accent = Color(0xFF7E9A80)

private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/** Design "L" height. Phase 2.2 turns this into the real S/M/L/XL scale. */
private const val KEY_H = 62

private enum class KeyKind { KEY, FN, ACCENT }

@Composable
fun PhaseOneKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
) {
    val gap = (KEY_H * 0.1f).dp
    val radius = (KEY_H * 0.18f).dp

    Column(
        Modifier
            .fillMaxWidth()
            .background(KbBg)
            .padding(horizontal = gap, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        ROWS.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Row insets follow the design doc's spacer weights.
                if (index == 1) Gap(0.5f)
                if (index == 2) {
                    Key("ABC", 1.5f, KeyKind.FN, radius) {}
                    Gap(0.15f)
                }
                row.forEach { ch ->
                    Key(ch.toString(), 1f, KeyKind.KEY, radius) { onKey(ch) }
                }
                if (index == 1) Gap(0.5f)
                if (index == 2) {
                    Gap(0.15f)
                    Key("⌫", 1.5f, KeyKind.FN, radius) { onBackspace() }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Key("123", 1.3f, KeyKind.FN, radius) {}
            Key("☺", 1f, KeyKind.FN, radius) {}
            Key("atom", 4.2f, KeyKind.KEY, radius) { onSpace() }
            Key(".", 1f, KeyKind.KEY, radius) { onKey('.') }
            Key("✓", 1.5f, KeyKind.ACCENT, radius) { onEnter() }
        }
    }
}

@Composable
private fun RowScope.Gap(weight: Float) {
    Box(Modifier.weight(weight))
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float,
    kind: KeyKind,
    radius: Dp,
    onClick: () -> Unit,
) {
    val bg = when (kind) {
        KeyKind.KEY -> KeyBg
        KeyKind.FN -> FnKey
        KeyKind.ACCENT -> Accent
    }
    val fg = when (kind) {
        KeyKind.KEY -> KeyText
        KeyKind.FN -> FnText
        KeyKind.ACCENT -> Color.White
    }
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .weight(weight)
            .height(KEY_H.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (kind == KeyKind.ACCENT) Accent else KeyEdge, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = AtomFonts.BarlowSemiCondensed,
            fontWeight = FontWeight.Medium,
            fontSize = (KEY_H * if (label.length > 1) 0.26f else 0.4f).sp,
        )
    }
}

/**
 * The keyboard [CrashGuard] degrades to when the rich one has failed hard.
 *
 * Deliberately the simplest thing that still types: no design system, no gestures, no state beyond
 * the callbacks. If this cannot render, nothing can — which is the point, since the user may need it
 * to type their way into Settings and switch keyboards.
 */
@Composable
fun FallbackKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFECECEC))
            .padding(4.dp),
    ) {
        Text(
            text = "AtomKeys hit an error — basic keyboard",
            color = Color(0xFF7A1F1F),
            fontSize = 11.sp,
            modifier = Modifier.padding(4.dp),
        )
        ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { ch -> PlainKey(ch.toString(), 1f) { onKey(ch) } }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            PlainKey("space", 4f, onSpace)
            PlainKey("⌫", 1f, onBackspace)
        }
    }
}

@Composable
private fun RowScope.PlainKey(label: String, weight: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .weight(weight)
            .height(48.dp)
            .padding(2.dp)
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.Black, fontSize = if (label.length > 1) 14.sp else 18.sp)
    }
}
