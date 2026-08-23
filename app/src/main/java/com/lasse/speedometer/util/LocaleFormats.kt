package com.lasse.speedometer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Date formatting that follows a language change.
 *
 * A `SimpleDateFormat` held in a top-level `val` captures the locale that was
 * current when its class loaded, so switching the app's language left every
 * date in the old one until the process restarted. These are keyed by locale
 * and rebuilt when it changes.
 *
 * Synchronised because `SimpleDateFormat` is not thread-safe and these are
 * shared: formatting a list of trips off the main thread while the live view
 * formats a clock is exactly the case that corrupts one of them.
 */
object LocaleFormats {

    private val cache = HashMap<Pair<String, Locale>, SimpleDateFormat>()

    @Synchronized
    fun format(pattern: String, millis: Long): String {
        val locale = Locale.getDefault()
        val formatter = cache.getOrPut(pattern to locale) { SimpleDateFormat(pattern, locale) }
        return formatter.format(Date(millis))
    }
}
