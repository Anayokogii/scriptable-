package com.scriptwidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

object WidgetPrefs {
    private fun p(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun save(c: Context, id: Int, script: String, param: String?) {
        val e = p(c).edit()
        e.putString("s$id", script)
        if (param == null) e.remove("p$id") else e.putString("p$id", param)
        e.apply()
    }

    fun script(c: Context, id: Int): String? = p(c).getString("s$id", null)
    fun param(c: Context, id: Int): String? = p(c).getString("p$id", null)

    fun remove(c: Context, id: Int) {
        p(c).edit().remove("s$id").remove("p$id").apply()
    }
}

object WidgetUpdater {

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val dm = ctx.resources.displayMetrics
        val opts = mgr.getAppWidgetOptions(id)
        var dpW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toDouble()
        var dpH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toDouble()
        if (dpW <= 0) dpW = 160.0
        if (dpH <= 0) dpH = 160.0

        var pxW = dpW * dm.density
        var pxH = dpH * dm.density
        // RemoteViews bitmaps are size-limited; stay well below the cap.
        val maxPx = dm.widthPixels.toDouble() * dm.heightPixels * 0.6
        if (pxW * pxH > maxPx) {
            val k = sqrt(maxPx / (pxW * pxH))
            pxW *= k
            pxH *= k
        }
        val w = max(1, pxW.toInt())
        val h = max(1, pxH.toInt())
        val family = WidgetEngine.familyFor(dpW, dpH)

        val script = WidgetPrefs.script(ctx, id)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_layout)
        var url: String? = null
        var refreshAt: Long? = null

        val bitmap = if (script == null || !ScriptStore.exists(ctx, script)) {
            WidgetEngine.message(
                ctx, "No script",
                "Remove this widget and add it again to pick a script.", w, h, family
            )
        } else {
            val out = WidgetEngine.run(
                ctx, ScriptStore.read(ctx, script), WidgetPrefs.param(ctx, id), w, h, family, 20_000
            )
            url = out.url
            refreshAt = out.refreshAt
            out.bitmap
        }
        rv.setImageViewBitmap(R.id.widget_image, bitmap)

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val tap = if (!url.isNullOrBlank()) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(ctx, id, i, flags)
        } else {
            PendingIntent.getBroadcast(ctx, id, refreshIntent(ctx, id), flags)
        }
        rv.setOnClickPendingIntent(R.id.widget_image, tap)
        mgr.updateAppWidget(id, rv)

        scheduleRefresh(ctx, id, refreshAt)
    }

    private fun refreshIntent(ctx: Context, id: Int): Intent =
        Intent(ctx, ScriptWidgetProvider::class.java)
            .setAction(ScriptWidgetProvider.ACTION_REFRESH)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    /** Honour refreshAfterDate with an inexact alarm (never earlier than 5 minutes from now). */
    private fun scheduleRefresh(ctx: Context, id: Int, at: Long?) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 1_000_000 + id, refreshIntent(ctx, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (at == null) {
            am.cancel(pi)
        } else {
            val whenMs = max(at, System.currentTimeMillis() + 5 * 60_000L)
            am.set(AlarmManager.RTC, whenMs, pi)
        }
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ScriptWidgetProvider::class.java))
        for (id in ids) update(ctx, mgr, id)
    }
}

class ScriptWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.scriptwidgets.REFRESH"
    }

    private fun async(ctx: Context, block: () -> Unit) {
        val pending = goAsync()
        thread {
            try {
                block()
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        async(context) { ids.forEach { WidgetUpdater.update(app, mgr, it) } }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        val app = context.applicationContext
        async(context) { WidgetUpdater.update(app, mgr, id) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val app = context.applicationContext
                val mgr = AppWidgetManager.getInstance(app)
                async(context) { WidgetUpdater.update(app, mgr, id) }
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}
