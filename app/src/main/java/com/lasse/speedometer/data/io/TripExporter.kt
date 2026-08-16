package com.lasse.speedometer.data.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Puts a recorded trip on disk as GPX, and hands it to another app. */
class TripExporter(private val context: Context) {

    private val fileStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

    fun fileNameFor(trip: TripEntity): String {
        val base = trip.title?.takeIf { it.isNotBlank() }?.replace(Regex("[^\\w\\- ]"), "")
            ?: "Trip_${fileStamp.format(Date(trip.startedAt))}"
        return "$base.gpx"
    }

    /**
     * Saves into the public Downloads folder, which is what "Download GPX"
     * means to anyone who then goes looking for the file.
     */
    suspend fun saveToDownloads(
        trip: TripEntity,
        points: List<TrackPointEntity>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = fileNameFor(trip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, MIME_GPX)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Downloads folder unavailable")
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    GpxWriter.write(writer, trip, points, name.removeSuffix(".gpx"))
                } ?: error("Could not open $uri")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                dir.mkdirs()
                File(dir, name).bufferedWriter().use { writer ->
                    GpxWriter.write(writer, trip, points, name.removeSuffix(".gpx"))
                }
            }
            name
        }
    }

    /** Writes to the cache and returns a share intent for it. */
    suspend fun shareIntent(
        trip: TripEntity,
        points: List<TrackPointEntity>,
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val name = fileNameFor(trip)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name)
            file.bufferedWriter().use { writer ->
                GpxWriter.write(writer, trip, points, name.removeSuffix(".gpx"))
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_GPX
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private companion object {
        const val MIME_GPX = "application/gpx+xml"
    }
}
