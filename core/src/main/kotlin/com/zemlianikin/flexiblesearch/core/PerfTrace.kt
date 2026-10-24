package com.zemlianikin.flexiblesearch.core

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log

/**
 * Метки старта и открытия окна: в logcat — мс от запуска процесса (`adb
 * logcat -s PerfTrace`), в Perfetto — секции [section].
 */
object PerfTrace {
    const val TAG = "PerfTrace"

    fun mark(what: String) {
        Log.i(TAG, "${sinceStart()} $what")
    }

    /** [block] секцией в Perfetto и с длительностью в logcat. */
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        val start = SystemClock.uptimeMillis()
        try {
            return block()
        } finally {
            Trace.endSection()
            val took = SystemClock.uptimeMillis() - start
            Log.i(TAG, "${sinceStart()} $name: $took ms")
        }
    }

    @PublishedApi
    internal fun sinceStart(): Long =
        SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
}
