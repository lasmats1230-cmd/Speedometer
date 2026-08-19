package com.lasse.speedometer.data.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Puts a recorded trip on disk as GPX, and hands it to another app. */
class TripExporter(private val context: Context) {

    fun fileNameFor(trip: TripEntity): String {
        // A title of nothing but punctuation or emoji sanitises down to an
        // empty string, and a file called ".gpx" is a hidden file nobody will
        // find again.
        val base = trip.title
            ?.replace(Regex("[^\\w\\- ]"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Trip_${Downloads.stamp(trip.startedAt)}"
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
            Downloads.write(context, name, MIME_GPX) { writer ->
                GpxWriter.write(writer, trip, points, name.removeSuffix(".gpx"))
            }
            name
        }
    }

    /** Writes the whole history into Downloads as one spreadsheet. */
    suspend fun saveCsvToDownloads(
        trips: List<TripEntity>,
        units: UnitSystem,
        headings: CsvHeadings,
        activityName: (TripEntity) -> String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = "Speedometer_trips_${Downloads.stamp(System.currentTimeMillis())}.csv"
            Downloads.write(context, name, MIME_CSV) { writer ->
                CsvWriter.write(writer, trips, units, headings, activityName)
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

    /**
     * Writes a rendered card into the cache and returns an intent that sends
     * the picture rather than the file: a chat window shows one and offers to
     * download the other.
     */
    suspend fun shareCard(
        trip: TripEntity,
        points: List<TrackPointEntity>,
        text: TripCardText,
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = TripCardRenderer.render(points, text)
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(directory, fileNameFor(trip).removeSuffix(".gpx") + ".png")
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_PNG
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private companion object {
        const val MIME_GPX = "application/gpx+xml"
        const val MIME_CSV = "text/csv"
        const val MIME_PNG = "image/png"
    }
}
