package com.lasse.speedometer.data.repo

/**
 * The handful of points a list thumbnail is drawn from, encoded for storage.
 *
 * History used to build its sketches by reading every fourth point of every
 * trip out of the database — a hundred thousand rows for a few hundred rides,
 * re-read whenever anything in history changed, to draw pictures a hundred
 * pixels wide. The shape of a route at that size needs a few dozen points, so
 * they are worked out once when the trip is saved and kept on the trip.
 *
 * Coordinates are rounded to five decimal places, which is about a metre —
 * far finer than a thumbnail can show, and it halves the stored text.
 */
object TrackSketch {

    /** Enough to keep every bend a thumbnail can render. */
    const val MAX_POINTS = 48

    private const val SEPARATOR = ';'
    private const val PAIR = ','

    fun encode(points: List<Pair<Double, Double>>): String =
        decimate(points, MAX_POINTS).joinToString(SEPARATOR.toString()) { (latitude, longitude) ->
            "${round(latitude)}$PAIR${round(longitude)}"
        }

    fun decode(encoded: String): List<Pair<Double, Double>> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(SEPARATOR).mapNotNull { chunk ->
            val parts = chunk.split(PAIR)
            if (parts.size != 2) return@mapNotNull null
            val latitude = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val longitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            latitude to longitude
        }
    }

    /**
     * Thins a track by taking evenly spaced points, keeping the first and the
     * last so the sketch still starts and ends where the ride did.
     */
    fun decimate(
        points: List<Pair<Double, Double>>,
        limit: Int = MAX_POINTS,
    ): List<Pair<Double, Double>> {
        if (points.size <= limit) return points
        val step = (points.size - 1).toFloat() / (limit - 1)
        return List(limit) { index -> points[(index * step).toInt().coerceIn(points.indices)] }
    }

    private fun round(value: Double): String =
        String.format(java.util.Locale.US, "%.5f", value)
}
