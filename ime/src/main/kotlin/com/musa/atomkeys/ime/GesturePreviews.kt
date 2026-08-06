package com.musa.atomkeys.ime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.musa.atomkeys.design.AtomThemes
import com.musa.atomkeys.engine.Candidate
import com.musa.atomkeys.engine.Gesture

/**
 * The five gesture frames G1–G5 from `AtomKeyboard.dc.html` § Gestures.
 *
 * The doc draws these on the Mauve theme at size L with the trail + pill labelled "shown at M swipe
 * length (40dp)" — the pill is an **annotation**, so these previews are how it gets dogfooded rather
 * than a claim that it ships. G3 and G4 pass candidate lists the doc gives them.
 */
@Preview(name = "G1 · Swipe right", widthDp = 412, heightDp = 300)
@Composable
private fun G1SwipeRight() = GestureFrame(Gesture.RIGHT)

@Preview(name = "G2 · Swipe left", widthDp = 412, heightDp = 300)
@Composable
private fun G2SwipeLeft() = GestureFrame(Gesture.LEFT)

@Preview(name = "G3 · Swipe down", widthDp = 412, heightDp = 300)
@Composable
private fun G3SwipeDown() = GestureFrame(
    Gesture.DOWN,
    candidates = listOf(Candidate("hello", isActive = true, hasMore = true), Candidate("jello"), Candidate("hallo")),
)

/** G4 labels the raw word it will revert to, so the strip shows the un-corrected candidates. */
@Preview(name = "G4 · Swipe up", widthDp = 412, heightDp = 300)
@Composable
private fun G4SwipeUp() = GestureFrame(
    Gesture.UP,
    candidates = listOf(Candidate("teh", isActive = true, hasMore = true), Candidate("the"), Candidate("ten")),
)

@Preview(name = "G5 · Hold space", widthDp = 412, heightDp = 300)
@Composable
private fun G5HoldSpace() = GestureFrame(Gesture.SPACE)

@Composable
private fun GestureFrame(gesture: Gesture, candidates: List<Candidate> = Candidate.Placeholder) {
    AtomKeyboardPreview(
        theme = AtomThemes.Mauve,
        candidates = candidates,
        gesture = gesture,
    )
}