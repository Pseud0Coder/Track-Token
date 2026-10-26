package com.tracktoken.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tracktoken.R
import com.tracktoken.data.Store
import com.tracktoken.ui.MainActivity
import java.util.Locale
import kotlin.math.roundToInt

enum class WidgetStyle(
    val layoutRes: Int,
    val providerClass: Class<out AppWidgetProvider>
) {
    HUD(R.layout.widget_hud, HudWidgetProvider::class.java),
    TERMINAL(R.layout.widget_terminal, TerminalWidgetProvider::class.java),
    PULSE(R.layout.widget_pulse, PulseWidgetProvider::class.java),
    MATRIX(R.layout.widget_matrix, MatrixWidgetProvider::class.java),
    LED(R.layout.widget_led, LedWidgetProvider::class.java)
}

abstract class BaseWidgetProvider : AppWidgetProvider() {
    abstract val style: WidgetStyle

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = WidgetRenderer.render(context, style)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }
}

class HudWidgetProvider : BaseWidgetProvider() {
    override val style: WidgetStyle = WidgetStyle.HUD
}

class TerminalWidgetProvider : BaseWidgetProvider() {
    override val style: WidgetStyle = WidgetStyle.TERMINAL
}

class PulseWidgetProvider : BaseWidgetProvider() {
    override val style: WidgetStyle = WidgetStyle.PULSE
}

class MatrixWidgetProvider : BaseWidgetProvider() {
    override val style: WidgetStyle = WidgetStyle.MATRIX
}

class LedWidgetProvider : BaseWidgetProvider() {
    override val style: WidgetStyle = WidgetStyle.LED
}

/** Re-renders every widget style from the locally cached snapshot. */
object WidgetPush {

    fun pushAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return

        WidgetStyle.entries.forEach { style ->
            val component = ComponentName(app, style.providerClass)
            val ids = manager.getAppWidgetIds(component) ?: return@forEach
            if (ids.isEmpty()) return@forEach
            runCatching { manager.updateAppWidget(ids, WidgetRenderer.render(app, style)) }
        }
    }
}

object WidgetRenderer {

    private const val SEGMENTS = 8

    private val segIds = intArrayOf(
        R.id.seg_0, R.id.seg_1, R.id.seg_2, R.id.seg_3,
        R.id.seg_4, R.id.seg_5, R.id.seg_6, R.id.seg_7
    )

    fun render(context: Context, style: WidgetStyle): RemoteViews {
        val snapshot = SnapshotFactory.build(
            Store.loadKeys(context),
            Store.loadStatuses(context)
        )

        val views = RemoteViews(context.packageName, style.layoutRes)
        when (style) {
            WidgetStyle.HUD -> renderHud(views, snapshot)
            WidgetStyle.TERMINAL -> renderTerminal(views, snapshot)
            WidgetStyle.PULSE -> renderPulse(views, snapshot)
            WidgetStyle.MATRIX -> renderMatrix(views, snapshot)
            WidgetStyle.LED -> renderLed(views, snapshot)
        }

        views.setOnClickPendingIntent(R.id.wgt_root, openApp(context))
        return views
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ------------------------------------------------------------------ HUD

    private fun renderHud(views: RemoteViews, snapshot: Snapshot) {
        val rows = listOf(
            Triple(R.id.hud_row_1, R.id.hud_name_1, R.id.hud_val_1),
            Triple(R.id.hud_row_2, R.id.hud_name_2, R.id.hud_val_2),
            Triple(R.id.hud_row_3, R.id.hud_name_3, R.id.hud_val_3)
        )
        val bars = intArrayOf(R.id.hud_bar_1, R.id.hud_bar_2, R.id.hud_bar_3)

        views.setViewVisibility(
            R.id.hud_status,
            View.VISIBLE
        )
        views.setTextViewText(
            R.id.hud_status,
            if (snapshot.isEmpty) "NO KEYS" else "SYNC " + snapshot.updated
        )

        rows.forEachIndexed { index, ids ->
            val row = snapshot.rows.getOrNull(index)
            if (row == null) {
                views.setViewVisibility(ids.first, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(ids.first, View.VISIBLE)
            views.setTextViewText(ids.second, row.name)
            views.setTextViewText(ids.third, row.value)
            views.setProgressBar(bars[index], 100, row.fill ?: 0, false)
        }

        val overflow = if (snapshot.rows.size > 3) "  +${snapshot.rows.size - 3} MORE" else ""
        views.setTextViewText(
            R.id.hud_foot,
            if (snapshot.isEmpty) {
                "NO API KEYS LINKED"
            } else {
                "${snapshot.keyCount} KEYS$overflow · ${snapshot.errorCount} ALERT" + snapshot.tokenLine
            }
        )
    }

    // ------------------------------------------------------------ TERMINAL

    private fun renderTerminal(views: RemoteViews, snapshot: Snapshot) {
        val lineIds = intArrayOf(R.id.term_line_1, R.id.term_line_2, R.id.term_line_3)

        val lines: List<String> = if (snapshot.isEmpty) {
            listOf("> no api keys linked", "> open tracktoken to add one", "")
        } else {
            snapshot.rows.take(3).map { row ->
                String.format(
                    Locale.US,
                    "%-11s %9s  %s",
                    row.name.take(11),
                    row.value,
                    if (row.ok) "OK" else "ERR"
                )
            }
        }

        val filled = lines.toMutableList()
        while (filled.size < 3) filled.add("")
        val last = filled.indexOfLast { it.isNotBlank() }
        if (last >= 0) filled[last] = filled[last] + " ▌"

        lineIds.forEachIndexed { index, id -> views.setTextViewText(id, filled[index]) }

        views.setTextViewText(
            R.id.term_foot,
            if (snapshot.isEmpty) {
                "sync --:-- · waiting for keys"
            } else {
                "sync ${snapshot.updated} · ${snapshot.keyCount} keys · ${snapshot.errorCount} err" +
                    snapshot.tokenLine
            }
        )
    }

    // --------------------------------------------------------------- PULSE

    private fun renderPulse(views: RemoteViews, snapshot: Snapshot) {
        val percent = if (snapshot.isEmpty) null else snapshot.fill

        views.setTextViewText(
            R.id.pulse_value,
            when {
                snapshot.isEmpty -> "--"
                percent == null -> "??"
                else -> "$percent%"
            }
        )

        views.setTextViewText(
            R.id.pulse_sub,
            if (snapshot.isEmpty) {
                "NO API KEYS LINKED"
            } else {
                snapshot.total + " · " + snapshot.keyCount + " KEYS" + snapshot.tokenLine
            }
        )

        val lit = if (percent == null) 0
        else ((percent / 100.0) * SEGMENTS).roundToInt().coerceIn(0, SEGMENTS)

        segIds.forEachIndexed { index, id ->
            views.setViewVisibility(id, if (index < lit) View.VISIBLE else View.GONE)
        }

        views.setTextViewText(
            R.id.pulse_foot,
            if (snapshot.isEmpty) "add a key in the app"
            else "SYNC ${snapshot.updated} · ${snapshot.errorCount} ALERT"
        )
    }

    // -------------------------------------------------------------- MATRIX

    private fun renderMatrix(views: RemoteViews, snapshot: Snapshot) {
        val rows = listOf(
            Triple(R.id.mx_row_1, R.id.mx_name_1, R.id.mx_val_1),
            Triple(R.id.mx_row_2, R.id.mx_name_2, R.id.mx_val_2),
            Triple(R.id.mx_row_3, R.id.mx_name_3, R.id.mx_val_3),
            Triple(R.id.mx_row_4, R.id.mx_name_4, R.id.mx_val_4)
        )
        val bars = intArrayOf(R.id.mx_bar_1, R.id.mx_bar_2, R.id.mx_bar_3, R.id.mx_bar_4)

        rows.forEachIndexed { index, ids ->
            val row = snapshot.rows.getOrNull(index)
            if (row == null) {
                views.setViewVisibility(ids.first, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(ids.first, View.VISIBLE)
            views.setTextViewText(ids.second, row.name)
            views.setTextViewText(ids.third, row.value)
            views.setProgressBar(bars[index], 100, row.fill ?: 0, false)
        }

        views.setTextViewText(
            R.id.matrix_status,
            if (snapshot.isEmpty) "NO KEYS" else "SYNC " + snapshot.updated
        )

        views.setTextViewText(
            R.id.matrix_foot,
            if (snapshot.isEmpty) {
                "NO API KEYS LINKED"
            } else {
                "${snapshot.keyCount} KEYS · ${snapshot.errorCount} ALERT" + snapshot.tokenLine
            }
        )
    }

    // ------------------------------------------------------------------ LED

    private fun renderLed(views: RemoteViews, snapshot: Snapshot) {
        views.setTextViewText(
            R.id.led_value,
            if (snapshot.isEmpty) "--" else snapshot.total
        )

        views.setProgressBar(R.id.led_bar, 100, snapshot.fill, false)

        views.setTextViewText(
            R.id.led_sub,
            if (snapshot.isEmpty) {
                "NO API KEYS LINKED"
            } else {
                buildString {
                    append(snapshot.keyCount)
                    append(" KEYS")
                    if (snapshot.totalCurrency.isNotEmpty()) {
                        append(" · ")
                        append(snapshot.totalCurrency)
                    }
                    append(" · SYNC ")
                    append(snapshot.updated)
                    append(snapshot.tokenLine)
                }
            }
        )
    }
}
