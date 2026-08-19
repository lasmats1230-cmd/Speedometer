package com.lasse.speedometer.data.io

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writing a file somewhere the user can find it afterwards.
 *
 * On Android 10 and later that means a MediaStore entry, created pending and
 * published once the write succeeds; below it, a plain file. Both the GPX
 * exporter and the backup writer need exactly this, and both used to carry
 * their own copy — including the same missing cleanup, which left an invisible
 * pending row behind whenever a write failed.
 */
object Downloads {

    /**
     * Writes [name] into the public Downloads folder.
     *
     * Throws if the folder is unavailable or the write fails; nothing is left
     * behind either way.
     */
    suspend fun write(
        context: Context,
        name: String,
        mime: String,
        body: suspend (Writer) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            directory.mkdirs()
            File(directory, name).bufferedWriter().use { body(it) }
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Downloads folder unavailable")
        try {
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { body(it) }
                ?: error("Could not open $uri")
        } catch (failure: Throwable) {
            // A row left pending is invisible in Downloads and nothing else
            // will ever clean it up.
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    /**
     * A timestamp for a file name.
     *
     * A new formatter each time on purpose: SimpleDateFormat is not safe to
     * share between threads, and every caller here is on a background one.
     */
    fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(millis))
}
