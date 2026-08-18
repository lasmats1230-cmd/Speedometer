package com.lasse.speedometer.data.io

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.lasse.speedometer.data.db.TrackPointEntity
import kotlin.math.cos

/** The words on a shared card, resolved by the caller so this stays free of resources. */
data class TripCardText(
    val headline: String,
    val subtitle: String,
    val stats: List<Pair<String, String>>,
    val footer: String,
)

/**
 * Draws a trip as a picture worth sending to someone.
 *
 * A GPX file is for another app; a screenshot is what actually gets sent to a
 * friend, and screenshots of a phone are cropped, dimmed and full of status
 * bar. This renders the same information at a size made for a chat window,
 * with the route drawn from the recorded track rather than a map tile — no
 * network, no attribution question, and it works on a trip recorded in a
 * tunnel with no basemap loaded.
 */
object TripCardRenderer {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 72f

    private const val BACKGROUND = 0xFF0B0F0D.toInt()
    private const val SURFACE = 0xFF151A17.toInt()
    private const val TRACK = 0xFF19E68C.toInt()
    private const val TEXT = 0xFFF2F5F3.toInt()
    private const val MUTED = 0xFF93A09A.toInt()

    fun render(points: List<TrackPointEntity>, text: TripCardText): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val mapTop = MARGIN + 40f
        val mapHeight = 620f
        drawMapPanel(canvas, points, mapTop, mapHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = TEXT
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textSize = 112f
        canvas.drawText(text.headline, MARGIN, mapTop + mapHeight + 150f, paint)

        paint.color = MUTED
        paint.textSize = 40f
        canvas.drawText(text.subtitle, MARGIN, mapTop + mapHeight + 210f, paint)

        // Statistics in two columns, which is as many as stay readable at the
        // size a chat window shows a picture.
        val columnWidth = (WIDTH - 2 * MARGIN) / 2f
        text.stats.forEachIndexed { index, (label, value) ->
            val column = index % 2
            val row = index / 2
            val x = MARGIN + column * columnWidth
            val y = mapTop + mapHeight + 320f + row * 130f

            paint.color = MUTED
            paint.textSize = 32f
            canvas.drawText(label.uppercase(), x, y, paint)

            paint.color = TEXT
            paint.textSize = 58f
            canvas.drawText(value, x, y + 66f, paint)
        }

        paint.color = MUTED
        paint.textSize = 32f
        canvas.drawText(text.footer, MARGIN, HEIGHT - MARGIN, paint)

        return bitmap
    }

    /** The route, fitted into a rounded panel with a little air around it. */
    private fun drawMapPanel(
        canvas: Canvas,
        points: List<TrackPointEntity>,
        top: Float,
        height: Float,
    ) {
        val left = MARGIN
        val right = WIDTH - MARGIN
        val bottom = top + height

        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE }
        canvas.drawRoundRect(left, top, right, bottom, 48f, 48f, panel)

        if (points.size < 2) return

        val latitudes = points.map { it.latitude }
        val longitudes = points.map { it.longitude }
        val minLat = latitudes.min()
        val maxLat = latitudes.max()
        val minLon = longitudes.min()
        val maxLon = longitudes.max()

        // Longitude degrees shrink towards the poles; without this correction a
        // north-south ride comes out stretched sideways.
        val midLat = Math.toRadians((minLat + maxLat) / 2)
        val spanX = ((maxLon - minLon) * cos(midLat)).coerceAtLeast(1e-9)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-9)

        val inset = 64f
        val availableWidth = (right - left) - 2 * inset
        val availableHeight = height - 2 * inset
        val scale = minOf(availableWidth / spanX, availableHeight / spanY)
        val drawnWidth = spanX * scale
        val drawnHeight = spanY * scale
        val offsetX = left + inset + (availableWidth - drawnWidth) / 2
        val offsetY = top + inset + (availableHeight - drawnHeight) / 2

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = (offsetX + ((point.longitude - minLon) * cos(midLat)) * scale).toFloat()
            // Screen y grows downwards, latitude grows upwards.
            val y = (offsetY + drawnHeight - (point.latitude - minLat) * scale).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(
                left,
                top,
                right,
                bottom,
                intArrayOf(TRACK, 0xFF12B4E6.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, stroke)

        // Start and finish, so the direction of travel is readable.
        val markers = Paint(Paint.ANTI_ALIAS_FLAG)
        val first = points.first()
        val last = points.last()
        fun project(point: TrackPointEntity): Pair<Float, Float> {
            val x = (offsetX + ((point.longitude - minLon) * cos(midLat)) * scale).toFloat()
            val y = (offsetY + drawnHeight - (point.latitude - minLat) * scale).toFloat()
            return x to y
        }
        val (startX, startY) = project(first)
        val (endX, endY) = project(last)
        markers.color = Color.WHITE
        canvas.drawCircle(startX, startY, 18f, markers)
        markers.color = BACKGROUND
        canvas.drawCircle(startX, startY, 10f, markers)
        markers.color = TRACK
        canvas.drawCircle(endX, endY, 18f, markers)
    }
}
