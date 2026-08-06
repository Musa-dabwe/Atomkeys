package com.musa.atomkeys.ime

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AtomKeys"

/**
 * Crash containment for the keyboard (roadmap 1.4).
 *
 * An IME crash is uniquely bad: the user is left with **no keyboard**, and may be unable to type their
 * way out of it — including into the Settings search box they would need to switch keyboards. So the
 * rule here is that no exception from keyboard logic or composition may reach the framework.
 *
 * Two layers:
 *  - [runGuarded] wraps individual key/gesture handling. A failure loses one keystroke, not the session.
 *  - [KeyboardCrashState] latches a hard failure so the service can swap to a plain fallback keyboard
 *    that has no dependency on the composition that just failed.
 *
 * Logs go **to a local file only**. The keyboard has no `INTERNET` permission and never will; a
 * keyboard that phones home about crashes is a keyboard that can leak what you typed.
 */
object CrashGuard {

    private const val LOG_FILE = "crash.log"
    private const val MAX_LOG_BYTES = 128 * 1024

    /**
     * Run [block], swallowing and recording any failure.
     * @return block's value, or [fallback] if it threw.
     */
    inline fun <T> runGuarded(context: Context, what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            report(context, what, t)
            fallback
        }

    /** Run [block], swallowing and recording any failure. */
    inline fun runGuarded(context: Context, what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            report(context, what, t)
        }
    }

    /**
     * Record a failure to logcat and to the on-disk log.
     *
     * Deliberately tolerant of its own failure: if writing the log throws (full disk, odd OEM
     * sandboxing) that must not become a second crash on top of the first.
     */
    fun report(context: Context, what: String, t: Throwable) {
        Log.e(TAG, "guarded failure in $what", t)
        try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.length() > MAX_LOG_BYTES) file.delete()
            val stack = StringWriter().also { t.printStackTrace(PrintWriter(it)) }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("=== $stamp — $what ===\n$stack\n")
        } catch (ignored: Throwable) {
            Log.e(TAG, "could not write crash log", ignored)
        }
    }

    /** The on-disk crash log, for the settings app to surface. Null when nothing has been recorded. */
    fun logFile(context: Context): File? =
        File(context.filesDir, LOG_FILE).takeIf { it.isFile && it.length() > 0 }

    /** Clear the crash log. Exposed through the settings app's reset path. */
    fun clearLog(context: Context) {
        runCatching { File(context.filesDir, LOG_FILE).delete() }
    }
}

/**
 * Latches whether the rich keyboard has failed hard.
 *
 * Once tripped, the service renders a minimal fallback keyboard for the rest of the process's life
 * rather than retrying a composition that is evidently broken — retrying in a loop would be worse than
 * degrading, because it would crash repeatedly and some OEMs then restrict the app (ColorOS marked us
 * "unstable" during the Phase 0 spike crash).
 */
class KeyboardCrashState {
    @Volatile
    var hasFailed: Boolean = false
        private set

    fun trip() {
        hasFailed = true
    }
}
