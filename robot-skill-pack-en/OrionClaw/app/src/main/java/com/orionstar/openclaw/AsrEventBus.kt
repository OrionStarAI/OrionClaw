package com.orionstar.openclaw

import android.util.Log

/**
 * Small indirection layer so MainApplication (ASR listener) can report ASR final
 * texts via the currently connected RobotWsClient (owned by MainActivity).
 */
object AsrEventBus {
  private const val TAG = "AsrEventBus"

  // keep a tiny in-memory backlog so ASR finals produced before WS is connected are not lost.
  private val backlog = java.util.ArrayDeque<String>(8)

  @Volatile private var reporter: ((text: String) -> Unit)? = null

  @Synchronized
  fun setReporter(r: ((text: String) -> Unit)?) {
    reporter = r
    if (r == null) return

    // flush backlog
    while (backlog.isNotEmpty()) {
      val t = backlog.removeFirst()
      try {
        Log.i(TAG, "flush ASR final -> reporter: $t")
        r.invoke(t)
      } catch (e: Throwable) {
        Log.e(TAG, "flush reporter failed", e)
        break
      }
    }
  }

  @Synchronized
  fun onAsrFinal(text: String) {
    val t = text.trim()
    if (t.isBlank()) return
    val r = reporter
    if (r == null) {
      // buffer and return
      if (backlog.size >= 8) backlog.removeFirst()
      backlog.addLast(t)
      Log.w(TAG, "ASR final queued (no reporter yet). text=$t")
      return
    }
    try {
      Log.i(TAG, "ASR final -> reporter: $t")
      r.invoke(t)
    } catch (e: Throwable) {
      Log.e(TAG, "reporter failed", e)
    }
  }
}
