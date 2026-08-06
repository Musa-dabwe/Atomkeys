package com.musa.atomkeys.spike

/* THROWAWAY SPIKE CODE — Phase 0.3 measurement harness. */

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "AtomSpike"

/**
 * Collects the Phase 0.3 numbers. Deliberately allocation-light on the hot path: the whole point is
 * measuring keypress latency, so the harness must not perturb what it measures.
 */
class SpikeMetrics {

    /** Nanos from pointer-down to the start of the next rendered frame. */
    private val feedbackSamples = ArrayDeque<Long>()

    /** Nanos from pointer-down to commitText() returning. */
    private val commitSamples = ArrayDeque<Long>()

    /** Total composable invocations of the key composable, all keys. Proves scoping. */
    val keyRecompositions = AtomicInteger(0)

    /** Peak simultaneous pointers observed. Proves multitouch survives. */
    var peakPointers by mutableIntStateOf(0)
        private set

    /** Keys that were resolved from a pointer-down but never got a matching up. */
    var lostPointers by mutableIntStateOf(0)
        private set

    var lastSummary by mutableStateOf("no samples yet")
        private set

    private var pendingDownNanos: Long = 0L
    private var showCount = 0

    fun onPointerDown(nanos: Long) {
        pendingDownNanos = nanos
    }

    fun onFrameAfterPress(frameNanos: Long) {
        if (pendingDownNanos == 0L) return
        record(feedbackSamples, frameNanos - pendingDownNanos)
        refreshSummary()
    }

    fun onCommitted() {
        if (pendingDownNanos == 0L) return
        record(commitSamples, System.nanoTime() - pendingDownNanos)
        pendingDownNanos = 0L
        refreshSummary()
    }

    fun onPointersChanged(active: Int) {
        if (active > peakPointers) peakPointers = active
    }

    fun onPointerLost() {
        lostPointers++
    }

    fun onStartInputView(restarting: Boolean) {
        showCount++
        Log.i(TAG, "onStartInputView #$showCount restarting=$restarting")
    }

    private fun record(into: ArrayDeque<Long>, sample: Long) {
        into.addLast(sample)
        if (into.size > SAMPLE_WINDOW) into.removeFirst()
    }

    private fun refreshSummary() {
        val fb = feedbackSamples.stats()
        val cm = commitSamples.stats()
        lastSummary = buildString {
            append("feedback p50 ").append(fb.first).append("ms  p95 ").append(fb.second)
            append("\ncommit   p50 ").append(cm.first).append("ms  p95 ").append(cm.second)
            append("\nrecomps ").append(keyRecompositions.get())
            append("  peakPtrs ").append(peakPointers)
            append("  lost ").append(lostPointers)
        }
        Log.i(TAG, lastSummary.replace('\n', ' '))
    }

    /** @return (p50, p95) in whole milliseconds, as display strings. */
    private fun ArrayDeque<Long>.stats(): Pair<String, String> {
        if (isEmpty()) return "-" to "-"
        val sorted = sorted()
        fun pct(p: Double): String {
            val idx = ((sorted.size - 1) * p).toInt()
            return "%.1f".format(sorted[idx] / 1_000_000.0)
        }
        return pct(0.50) to pct(0.95)
    }

    private companion object {
        const val SAMPLE_WINDOW = 200
    }
}
