package com.musa.atomkeys.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.engine.KeySize
import com.musa.atomkeys.engine.LabelScale

/**
 * Phase 2's gate: a keycap rendered in all six themes at all four heights, to be held next to
 * `AtomKeyboard.dc.html` and compared.
 *
 * These previews are the only consumer of `:design` until Phase 3 builds the real grid, so they are
 * also the module's smoke test — if the tokens or the sizing wrappers are wrong, it shows here.
 * Phase 8.4 screenshots the real keyboard rather than these.
 */

@Preview(name = "Gate — 6 themes × 4 heights", widthDp = 560, heightDp = 900)
@Composable
private fun KeycapMatrixPreview() {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F4EF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (theme in AtomThemes.all) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(theme.name, style = captionStyle.copy(fontWeight = FontWeight.Medium))
                    if (theme === AtomThemes.Oat) {
                        BasicText("  · internal, not in the picker", style = captionStyle)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.kbBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (size in KeySize.entries) {
                        AtomKeyboardTheme(theme = theme, keySize = size) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Keycap(
                                    label = "q",
                                    modifier = Modifier.size(size.height),
                                    hint = "1",
                                )
                                BasicText(
                                    "${size.name} · ${size.heightDp}",
                                    style = captionStyle.copy(color = theme.hint),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The `keyStyle(kind)` switch, every branch of it, so the raised look and its variants can be
 * checked against the source in one glance.
 */
@Preview(name = "Keycap kinds & states", widthDp = 420, heightDp = 260)
@Composable
private fun KeycapKindsPreview() {
    val theme = AtomThemes.SageMist
    AtomKeyboardTheme(theme = theme, keySize = KeySize.L) {
        val size = KeySize.L
        Column(
            modifier = Modifier
                .background(theme.kbBg)
                .padding(size.gap),
            verticalArrangement = Arrangement.spacedBy(size.gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(size.gap)) {
                LabelledKey("key", size) { Keycap("a", it) }
                LabelledKey("fn", size) {
                    Keycap("123", it, kind = KeyKind.FN, labelScale = LabelScale.LAYER_TOGGLE)
                }
                LabelledKey("accent", size) {
                    Keycap("✓", it, kind = KeyKind.ACCENT)
                }
                LabelledKey("emoji", size) {
                    Keycap("😊", it, kind = KeyKind.EMOJI, labelScale = LabelScale.EMOJI)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(size.gap)) {
                LabelledKey("pressed", size) { Keycap("a", it, pressed = true) }
                LabelledKey("invisible", size) { Keycap("a", it, invisible = true) }
                LabelledKey("hint", size) { Keycap("q", it, hint = "1") }
                LabelledKey("shift", size) {
                    Keycap("⇧", it, kind = KeyKind.FN, labelScale = LabelScale.SHIFT)
                }
            }
        }
    }
}

/** The doc's theme swatch row: `[kbBg, key, fnKey, keyText, accent, hint]` per theme. */
@Preview(name = "Theme swatches", widthDp = 420, heightDp = 320)
@Composable
private fun ThemeSwatchesPreview() {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F4EF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (theme in AtomThemes.all) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    theme.name,
                    modifier = Modifier.width(110.dp),
                    style = captionStyle,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val swatches = listOf(
                        theme.kbBg, theme.key, theme.fnKey,
                        theme.keyText, theme.accent, theme.hint,
                    )
                    for (color in swatches) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(theme.kbBg)   // chip tokens are translucent — show the
                                .background(color),       // composite over kbBg, as on device
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelledKey(
    caption: String,
    size: KeySize,
    key: @Composable (Modifier) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(size.height).width(size.height * 1.4f)) {
            key(Modifier.fillMaxWidth().height(size.height))
        }
        BasicText(caption, style = captionStyle.copy(color = AtomKeyboard.theme.hint))
    }
}

private val captionStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF6B6559))
