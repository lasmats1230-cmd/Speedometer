package com.lasse.speedometer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.lasse.speedometer.MainActivity
import com.lasse.speedometer.R
import com.lasse.speedometer.app
import com.lasse.speedometer.data.repo.StatsCalculator
import com.lasse.speedometer.data.repo.StatsPeriod
import com.lasse.speedometer.util.AppLocale
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * A home-screen tile of this week and all time.
 *
 * The point of a widget is the glance that never becomes an app launch: how
 * far this week, how far ever, and a tap to start the next one. It refreshes
 * when a trip is saved rather than on a timer, so it costs nothing while the
 * phone sits in a pocket.
 */
class SpeedometerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // The provider is a broadcast receiver, so the database read has to
        // outlive onUpdate; goAsync would need the result inside 10 seconds,
        // and a scope tied to the application is the calmer way to do it.
        val pending = goAsync()
        scope.launch {
            runCatching {
                val localised = AppLocale.wrap(context)
                val application = context.app
                val settings = application.settingsRepository.settings.first()
                val trips = application.tripRepository.trips.first()
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()

                val week = StatsCalculator.totals(
                    StatsCalculator.tripsIn(trips, StatsPeriod.WEEK, now, zone),
                    zone,
                )
                val lifetime = StatsCalculator.totals(trips, zone)
                val goalM = settings.weeklyGoalM
                val streak = StatsCalculator.currentStreak(trips, now, zone)

                // "of 100 km · 3 days in a row" — whichever halves apply. One
                // day is not a streak, it is today.
                val caption = listOfNotNull(
                    goalM.takeIf { it > 0 }?.let {
                        localised.getString(
                            R.string.widget_goal,
                            Formatters.distance(it, settings.units),
                        )
                    },
                    streak.takeIf { it > 1 }?.let {
                        localised.resources.getQuantityString(R.plurals.today_streak, it, it)
                    },
                ).joinToString(" · ")

                val views = RemoteViews(context.packageName, R.layout.widget_speedometer).apply {
                    if (goalM > 0) {
                        setProgressBar(
                            R.id.widget_goal_bar,
                            GOAL_STEPS,
                            ((week.distanceM / goalM) * GOAL_STEPS)
                                .toInt()
                                .coerceIn(0, GOAL_STEPS),
                            false,
                        )
                        setViewVisibility(R.id.widget_goal_bar, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.widget_goal_bar, View.GONE)
                    }
                    setTextViewText(R.id.widget_goal_text, caption)
                    setViewVisibility(
                        R.id.widget_goal_text,
                        if (caption.isEmpty()) View.GONE else View.VISIBLE,
                    )
                    setTextViewText(
                        R.id.widget_week_value,
                        Formatters.distance(week.distanceM, settings.units),
                    )
                    setTextViewText(
                        R.id.widget_week_label,
                        localised.getString(R.string.widget_this_week),
                    )
                    setTextViewText(
                        R.id.widget_total_value,
                        Formatters.distance(lifetime.distanceM, settings.units),
                    )
                    setTextViewText(
                        R.id.widget_total_label,
                        localised.getString(R.string.widget_lifetime),
                    )
                    setTextViewText(
                        R.id.widget_action,
                        localised.getString(R.string.widget_open),
                    )
                    setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                }
                manager.updateAppWidget(widgetId, views)
            }
            pending.finish()
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** A progress bar wants whole steps; a hundred is finer than the pixels. */
        private const val GOAL_STEPS = 100

        /** Redraws every placed widget — called when a trip lands. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SpeedometerWidget::class.java)
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, SpeedometerWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
