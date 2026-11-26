package com.scriptwidgets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.max

/**
 * Keeps "live" widgets ticking: while the screen is on it re-runs each live widget's script
 * about once a second. When the screen turns off it pauses to save battery.
 */
class LiveService : Service() {

    companion object {
        private const val CHANNEL = "live"

        fun liveIds(ctx: Context): List<Int> {
            val mgr = AppWidgetManager.getInstance(ctx)
            return mgr.getAppWidgetIds(ComponentName(ctx, ScriptWidgetProvider::class.java))
                .filter { WidgetPrefs.script(ctx, it) != null && WidgetPrefs.live(ctx, it) }
        }

        fun startIfNeeded(ctx: Context) {
            try {
                if (liveIds(ctx).isEmpty()) return
                ctx.startForegroundService(Intent(ctx, LiveService::class.java))
            } catch (_: Throwable) {
                // Android may refuse to start a foreground service from the background.
            }
        }

        fun stopIfIdle(ctx: Context) {
            if (liveIds(ctx).isEmpty()) ctx.stopService(Intent(ctx, LiveService::class.java))
        }
    }

    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private var ticking = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> startTicking()
                Intent.ACTION_SCREEN_OFF -> stopTicking()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            val start = SystemClock.elapsedRealtime()
            val app = applicationContext
            val ids = liveIds(app)
            if (ids.isEmpty()) {
                stopSelf()
                return
            }
            val mgr = AppWidgetManager.getInstance(app)
            for (id in ids) {
                try {
                    WidgetUpdater.update(app, mgr, id, fast = true)
                } catch (_: Throwable) {
                }
            }
            val delay = max(50L, 1000L - (SystemClock.elapsedRealtime() - start))
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("live-widgets").also { it.start() }
        handler = Handler(worker.looper)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, f)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startTicking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live widgets", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Live widgets running")
            .setContentText("Updating every second while the screen is on")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        return START_STICKY
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        handler.post(tick)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        stopTicking()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Throwable) {
        }
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Restarts live updates after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) LiveService.startIfNeeded(context)
    }
}
