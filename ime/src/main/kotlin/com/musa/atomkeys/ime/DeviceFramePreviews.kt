package com.musa.atomkeys.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.design.AtomFonts
import com.musa.atomkeys.design.AtomTheme
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.engine.Candidate
import com.musa.atomkeys.engine.KeyboardConfig
import com.musa.atomkeys.engine.Layer
import com.musa.atomkeys.engine.ShiftState

/**
 * The three in-context device frames D1–D3 from `AtomKeyboard.dc.html` § In context.
 *
 * The doc wraps each in an `AndroidDevice` frame (bezel + status bar from `android-frame.jsx`, which
 * is not in this repo). What we reproduce is the **screen**: the app chrome above the keyboard — the
 * part that is ours to draw — sized to the doc's 412×892 canvas. This is Phase 3.4's gate: compare
 * these against the doc's own D1–D3. The bezel itself is out of scope; roadmap 8.2 generates goldens
 * from our own Compose frames, not from the `.dc.html`'s JSX runtime.
 */
@Preview(name = "D1 · Notes, composing (Sage)", widthDp = 412, heightDp = 892)
@Composable
private fun D1Notes() {
    DeviceScreen(app = { fill -> NotesChrome(fill) }) {
        AtomKeyboardPreview(
            config = KeyboardConfig(),
            theme = AtomThemes.SageMist,
            pressedKeyId = keyIdFor("n"),
        )
    }
}

@Preview(name = "D2 · Chat, shift (Claude Light)", widthDp = 412, heightDp = 892)
@Composable
private fun D2ChatShift() {
    DeviceScreen(app = { fill -> ChatChrome(AtomThemes.ClaudeLight, fill) }) {
        AtomKeyboardPreview(
            config = KeyboardConfig(shift = ShiftState.MANUAL),
            theme = AtomThemes.ClaudeLight,
            candidates = ShiftAttempts,
        )
    }
}

@Preview(name = "D3 · Emoji (Claude Dark)", widthDp = 412, heightDp = 892)
@Composable
private fun D3Emoji() {
    DeviceScreen(app = { fill -> ChatChrome(AtomThemes.ClaudeDark, fill) }) {
        AtomKeyboardPreview(
            config = KeyboardConfig(layer = Layer.EMOJI),
            theme = AtomThemes.ClaudeDark,
            candidates = EmojiAttempts,
        )
    }
}

/** The candidate strips the doc assigns to the D-frames (§ props). */
private val ShiftAttempts = listOf(
    Candidate("Yes", isActive = true, hasMore = true),
    Candidate("Yep"),
    Candidate("🎫", isEmoji = true),
)

private val EmojiAttempts = listOf(
    Candidate("Recent"),
    Candidate("😊", isEmoji = true),
    Candidate("🎉", isEmoji = true),
    Candidate("❤️", isEmoji = true),
)

/**
 * A full 412×892 device screen: a status bar on top, the app chrome filling the middle, then the
 * keyboard pinned to the bottom.
 */
@Composable
private fun DeviceScreen(
    app: @Composable (appModifier: Modifier) -> Unit,
    keyboard: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(STATUS_BAR_COLOR)) {
        StatusBar()
        app(Modifier.weight(1f).fillMaxWidth())
        keyboard()
    }
}

/** A simple status bar. The doc's device frame shows it; a neutral colour suits every theme. */
@Composable
private fun StatusBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(STATUS_BAR_COLOR)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("9:41", style = statusBarStyle)
        Spacer(Modifier.weight(1f))
        BasicText("▮▮▮", style = statusBarStyle)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(7.dp).background(Color.Black, RoundedCornerShape(2.dp)))
    }
}

/** The Notes app (D1): a header with the back chevron and title, then the note body with caret. */
@Composable
private fun NotesChrome(modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xFFF1F3EE)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("‹", style = backStyle.copy(color = Color(0xFF5A6357)))
            BasicText("Notes", style = titleStyle)
        }
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("Saturday", style = noteTitleStyle)
            Row {
                BasicText(
                    "walk to the market before it gets hot, then",
                    style = bodyStyle,
                )
                BasicText("▍", style = caretStyle)
            }
        }
    }
}

/** The Maya chat (D2/D3): a header, then the two bubbles pinned to the bottom of the app area. */
@Composable
private fun ChatChrome(theme: AtomTheme, modifier: Modifier = Modifier) {
    Column(modifier.background(ChatBgFor(theme))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (theme == AtomThemes.ClaudeDark) Color(0xFFB4B0A6) else Color(0xFF6B675E)
            BasicText("‹", style = backStyle.copy(color = ink))
            BasicText(
                "Maya",
                style = titleStyle.copy(
                    color = if (theme == AtomThemes.ClaudeDark) Color(0xFFF5F4EF) else Color(0xFF191919),
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Bubble(INCOMING_TEXT, outgoing = false, theme)
            Bubble(OUTGOING_TEXT, outgoing = true, theme)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One rounded chat bubble, mirroring the doc's asymmetric corner radii per side. */
@Composable
private fun Bubble(text: String, outgoing: Boolean, theme: AtomTheme) {
    val bubbleColor = when {
        outgoing -> ThemeCoralFor(theme)
        theme == AtomThemes.ClaudeDark -> Color(0xFF30302E)
        else -> Color(0xFFF0EEE6)
    }
    val textColor = when {
        outgoing -> ThemeCoralInkFor(theme)
        theme == AtomThemes.ClaudeDark -> Color(0xFFF5F4EF)
        else -> Color(0xFF191919)
    }
    val shape = if (outgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (outgoing) 60.dp else 0.dp,
                end = if (outgoing) 0.dp else 60.dp,
            ),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box(Modifier.background(bubbleColor, shape).padding(horizontal = 14.dp, vertical = 11.dp)) {
            BasicText(text, style = TextStyle(fontFamily = AtomFonts.DmSans, fontSize = 15.sp, color = textColor))
        }
    }
}

private val STATUS_BAR_COLOR = Color(0xFFF5F4EF)

private const val INCOMING_TEXT = "did you get the tickets?"
private const val OUTGOING_TEXT = "Yes"

// --- App-chrome typography (DM Sans, per the doc's screens) ------------------------------------

private val statusBarStyle = TextStyle(
    fontFamily = AtomFonts.DmSans, fontSize = 14.sp, color = Color.Black,
)
private val backStyle = TextStyle(
    fontFamily = AtomFonts.DmSans, fontSize = 20.sp,
)
private val titleStyle = TextStyle(
    fontFamily = AtomFonts.DmSans, fontSize = 16.sp, fontWeight = FontWeight.Medium,
)
private val noteTitleStyle = TextStyle(
    fontFamily = AtomFonts.DmSans, fontSize = 19.sp, fontWeight = FontWeight.Medium,
    color = Color(0xFF2B322B),
)
private val bodyStyle = TextStyle(
    fontFamily = AtomFonts.DmSans, fontSize = 15.sp, color = Color(0xFF4A5147), lineHeight = 24.sp,
)
private val caretStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF7E9A80))

/** The chat's paper background, per the doc's D2/D3. */
private fun ChatBgFor(theme: AtomTheme): Color = when (theme) {
    AtomThemes.ClaudeDark -> Color(0xFF262624)
    else -> Color(0xFFFAF9F5)
}

/** The outgoing bubble's coral fill (`#D97757`), and its ink, which flips in Claude Dark. */
private fun ThemeCoralFor(theme: AtomTheme): Color = Color(0xFFD97757)
private fun ThemeCoralInkFor(theme: AtomTheme): Color =
    if (theme == AtomThemes.ClaudeDark) Color(0xFF231F1D) else Color.White