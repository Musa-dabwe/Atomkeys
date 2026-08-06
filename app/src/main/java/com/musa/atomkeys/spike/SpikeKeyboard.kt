package com.musa.atomkeys.spike

/* THROWAWAY SPIKE CODE — Phase 0.2 (overlay without Popup) and 0.3 (latency + multitouch). */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** Sage Mist tokens, so the spike at least looks like the target. Not the real theme system. */
private val KbBg = Color(0xFFDDE3DA)
private val KeyBg = Color(0xFFF4F7F1)
private val KeyEdge = Color(0xFFC9D2C4)
private val KeyText = Color(0xFF333B33)
private val Accent = Color(0xFF7E9A80)

private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/**
 * Phase 0.2 answer, implemented: **reserved headroom**.
 *
 * The press bubble sits `bottom: h + 6` in the design — above the keycap, outside its bounds. Rather
 * than relying on `clipToBounds = false` overdraw (fragile in an IME window) we reserve a clear band
 * at the top of the input view and draw floating elements into it as an overlay `Box` layered over
 * the grid, inside this same composition. No Popup, no Dialog, no second window.
 */
private val HeadroomHeight = 56.dp

private const val KEY_HEIGHT_DP = 62 // design "L"

@Composable
fun SpikeKeyboard(
    metrics: SpikeMetrics,
    onCommit: (String) -> Unit,
) {
    val density = LocalDensity.current
    val keyBounds = remember { mutableStateMapOf<String, Pair<Offset, IntSize>>() }

    // Which key each active pointer id is currently resolved to. A SnapshotStateMap so only the
    // affected key composables read a changed value — this is what 0.3 is testing.
    val pressedByPointer = remember { mutableStateMapOf<Long, String>() }
    val pressedKeys: Set<String> = pressedByPointer.values.toSet()

    // Drive the 0.3 "time to first frame after press" measurement.
    var pressGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(pressGeneration) {
        if (pressGeneration > 0) {
            // Deliberately ignore withFrameNanos' frameTimeNanos parameter and read the clock
            // ourselves. frameTimeNanos is when the frame *started*, which can predate the pointer
            // event that triggered this effect — that produced negative latencies on the first run.
            withFrameNanos { metrics.onFrameAfterPress(System.nanoTime()) }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(KbBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            Column(Modifier.fillMaxWidth()) {
                // --- reserved headroom (0.2). Empty by design; the overlay draws into it. ---
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HeadroomHeight)
                ) {
                    Text(
                        text = metrics.lastSummary,
                        color = KeyText,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                // --- the grid ---
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            // Raw multitouch: every pointer tracked independently to its own key.
                            // No `clickable`, no Indication, no ripple — see 0.3.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    metrics.onPointersChanged(
                                        event.changes.count { it.pressed }
                                    )
                                    for (change in event.changes) {
                                        val id = change.id.value
                                        when {
                                            change.changedToDownIgnoreConsumed() -> {
                                                metrics.onPointerDown(System.nanoTime())
                                                pressGeneration++
                                                keyAt(change.position, keyBounds)?.let {
                                                    pressedByPointer[id] = it
                                                }
                                                change.consume()
                                            }

                                            change.changedToUpIgnoreConsumed() -> {
                                                val key = pressedByPointer.remove(id)
                                                if (key != null) onCommit(key)
                                                else metrics.onPointerLost()
                                                change.consume()
                                            }

                                            change.pressed -> {
                                                // Slide-off: re-resolve, so moving off cancels.
                                                val k = keyAt(change.position, keyBounds)
                                                if (k != null) pressedByPointer[id] = k
                                                else pressedByPointer.remove(id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Column(Modifier.fillMaxWidth().padding(6.dp)) {
                        ROWS.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { ch ->
                                    val key = ch.toString()
                                    SpikeKey(
                                        label = key,
                                        pressed = key in pressedKeys,
                                        metrics = metrics,
                                        modifier = Modifier
                                            .weight(1f)
                                            .onGloballyPositioned { coords ->
                                                keyBounds[key] =
                                                    coords.positionInRoot() to coords.size
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 0.2: floating press bubbles, drawn as an overlay in this composition ---
            pressedKeys.forEach { key ->
                val bounds = keyBounds[key]
                if (bounds != null) {
                    val (pos, size) = bounds
                    Box(
                        Modifier
                            .offsetPx(
                                x = pos.x + size.width / 2f - with(density) { 23.dp.toPx() },
                                y = pos.y - with(density) { 60.dp.toPx() },
                            )
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KeyBg)
                            .border(1.dp, KeyEdge, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = KeyText, fontSize = 30.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpikeKey(
    label: String,
    pressed: Boolean,
    metrics: SpikeMetrics,
    modifier: Modifier = Modifier,
) {
    // Counts every invocation of this composable. If pressing one key bumps this by ~1 rather than
    // by ~26, press state is correctly scoped and 0.3's recomposition requirement holds.
    SideEffect { metrics.keyRecompositions.incrementAndGet() }

    Box(
        modifier
            .padding(3.dp)
            .height(KEY_HEIGHT_DP.dp)
            .clip(RoundedCornerShape((KEY_HEIGHT_DP * 0.18f).dp))
            .background(if (pressed) Accent else KeyBg)
            .border(1.dp, KeyEdge, RoundedCornerShape((KEY_HEIGHT_DP * 0.18f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else KeyText,
            fontSize = (KEY_HEIGHT_DP * 0.4f).sp,
        )
    }
}

/** Hit-test a pointer position against the laid-out key bounds. */
private fun keyAt(
    position: Offset,
    bounds: SnapshotStateMap<String, Pair<Offset, IntSize>>,
): String? {
    for ((key, b) in bounds) {
        val (pos, size) = b
        // Bounds are in root coordinates; the pointer position is relative to the grid Box, which
        // starts below the headroom. Compare on the grid's own axis by offsetting y.
        if (position.x >= pos.x && position.x <= pos.x + size.width &&
            position.y + 0f >= pos.y - size.height && position.y <= pos.y + size.height
        ) {
            return key
        }
    }
    return null
}

private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(x.toInt(), y.toInt())
        }
    })
