package com.lasse.speedometer.tracking

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.util.AppLocale
import com.lasse.speedometer.util.Formatters
import java.util.Locale
import kotlin.math.floor

/**
 * Says how far you have got, so the phone can stay in a pocket.
 *
 * Announcements are attached to distance rather than time: "five kilometres,
 * twenty-two minutes" is the sentence a rider is waiting for, and it arrives
 * at the same points on every ride whatever the traffic.
 *
 * Lives in the tracking layer rather than the UI because the screen is
 * usually off when this matters — the service is the only part still running.
 */
class VoiceCoach(private val context: Context) {

    private var engine: TextToSpeech? = null

    /**
     * Written by the engine's init callback, which arrives on whichever thread
     * the speech service hands it back on, and read from the recording loop.
     */
    @Volatile
    private var ready = false

    /** The last milestone spoken, so a stop-start ride is not announced twice. */
    private var lastMilestone = 0.0

    /** Whatever could not be said while the engine was still starting. */
    @Volatile
    private var pending: String? = null

    private fun ensureEngine() {
        if (engine != null) return
        engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.setAudioAttributes(
                    AudioAttributes.Builder()
                        // Navigation guidance ducks music rather than stopping
                        // it, which is what a two-second announcement wants.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine?.chooseLanguage()
                pending?.let { speakNow(it) }
            }
            pending = null
        }
    }

    /**
     * Prefers the app's own language over the system's: someone running the
     * interface in German expects German announcements, and falls back to
     * whatever the engine does have rather than staying silent.
     */
    private fun TextToSpeech.chooseLanguage() {
        val preferred = AppLocale.currentLocale() ?: Locale.getDefault()
        val result = setLanguage(preferred)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            setLanguage(Locale.getDefault())
        }
    }

    fun reset() {
        lastMilestone = 0.0
    }

    fun say(text: String) {
        ensureEngine()
        if (ready) speakNow(text) else pending = text
    }

    fun sayStarted() = say(context.getString(R.string.voice_started))

    fun sayPaused() = say(context.getString(R.string.voice_paused))

    fun sayResumed() = say(context.getString(R.string.voice_resumed))

    fun sayFinished() = say(context.getString(R.string.voice_finished))

    /**
     * Speaks when the trip crosses the next multiple of [intervalM].
     *
     * Only ever announces the milestone just passed, so a phone that wakes up
     * to a five-kilometre jump does not read out every marker it missed.
     */
    fun onProgress(
        distanceM: Double,
        elapsedMs: Long,
        avgSpeedMps: Double,
        intervalM: Double,
        units: UnitSystem,
        prefersPace: Boolean,
    ) {
        if (intervalM <= 0.0 || distanceM <= 0.0) return
        val reached = floor(distanceM / intervalM) * intervalM
        if (reached <= lastMilestone) return
        lastMilestone = reached

        say(
            context.getString(
                R.string.voice_update,
                Formatters.distance(reached, units),
                spokenDuration(elapsedMs),
                if (prefersPace) {
                    Formatters.pace(avgSpeedMps, units)
                } else {
                    Formatters.speed(avgSpeedMps, units)
                },
            )
        )
    }

    /** "1 hour 12 minutes" — a clock face read aloud is unintelligible. */
    fun spokenDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000).coerceAtLeast(0)
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        val parts = buildList {
            if (hours > 0) {
                add(context.resources.getQuantityString(R.plurals.hours_count, hours, hours))
            }
            if (minutes > 0 || hours == 0) {
                add(context.resources.getQuantityString(R.plurals.minutes_count, minutes, minutes))
            }
        }
        return parts.joinToString(" ")
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pending = null
    }

    private fun speakNow(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
    }

    private companion object {
        const val UTTERANCE_ID = "speedometer-voice"
    }
}
