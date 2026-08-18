package com.lasse.speedometer.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.lasse.speedometer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** The languages the app ships strings for. */
enum class AppLanguage(@param:StringRes val labelRes: Int, val tag: String?) {
    /** Whatever the phone is set to, falling back to English. */
    SYSTEM(R.string.language_system, null),
    ENGLISH(R.string.language_english, "en"),
    GERMAN(R.string.language_german, "de"),
}

/**
 * In-app language switching.
 *
 * Kept in SharedPreferences rather than DataStore because the choice has to be
 * readable synchronously from `attachBaseContext`, which runs long before any
 * coroutine could deliver it.
 */
object AppLocale {

    private const val PREFS = "locale"
    private const val KEY_LANGUAGE = "language"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun initialise(context: Context) {
        _language.value = read(context)
    }

    /**
     * Stores the choice and returns true when the caller has to restart the
     * activity itself. From Android 13 the platform owns per-app language and
     * recreates the activity for us; doing it as well would be a second,
     * redundant restart.
     */
    fun set(context: Context, language: AppLanguage): Boolean {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .commit()
        _language.value = language
        return !applyToSystem(context, language)
    }

    fun read(context: Context): AppLanguage {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?: return AppLanguage.SYSTEM
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.SYSTEM)
    }

    /**
     * The locale the app is running in, or null when it follows the system.
     * Used where a locale has to be handed to something outside the resource
     * system — the speech engine, for one.
     */
    fun currentLocale(): Locale? = _language.value.tag?.let(Locale::forLanguageTag)

    /**
     * Wraps a context so resource lookups resolve in the chosen language.
     * Returns the context untouched when following the system.
     */
    fun wrap(context: Context): Context {
        val tag = read(context).tag ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    /**
     * Mirrors the choice into Android's own per-app language setting, so the
     * system Settings screen agrees with what the app is showing.
     */
    private fun applyToSystem(context: Context, language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
            ?: return false
        manager.applicationLocales = language.tag
            ?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
        return true
    }
}
