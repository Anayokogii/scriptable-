package com.scriptwidgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap

/** Runs a script and renders its widget to a bitmap. Shared by the editor preview and real widgets. */
object WidgetEngine {

    class Output(
        val bitmap: Bitmap,
        val logs: String,
        val error: String?,
        val url: String?,
        val refreshAt: Long?
    )

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Guess the iOS-style family from the widget's size in dp. */
    fun familyFor(dpW: Double, dpH: Double): String = when {
        dpW < 200 -> "small"
        dpW / dpH > 1.7 -> "medium"
        else -> "large"
    }

    /** Width in iOS points for each family; height follows the real widget's aspect ratio. */
    fun virtualWidth(family: String): Double = if (family == "small") 155.0 else 329.0

    fun run(
        ctx: Context,
        source: String,
        param: String?,
        pxW: Int,
        pxH: Int,
        family: String,
        timeoutMs: Long
    ): Output {
        val dark = isDark(ctx)
        val vw = virtualWidth(family)
        val vh = vw * pxH / pxW
        val res = ScriptRunner.run(ctx, source, family, param, timeoutMs)
        val widget: ListWidget = when {
            res.error != null -> messageWidget("Script error", res.error, true)
            res.widget == null -> messageWidget(
                "Nothing to show",
                "The script did not call Script.setWidget(widget).",
                false
            )
            else -> res.widget
        }
        val bmp = Renderer(dark).render(widget, vw, vh, pxW, pxH)
        return Output(bmp, res.logs, res.error, widget.url, widget.refreshAfterMillis)
    }

    fun message(ctx: Context, title: String, body: String, pxW: Int, pxH: Int, family: String): Bitmap {
        val vw = virtualWidth(family)
        val vh = vw * pxH / pxW
        return Renderer(isDark(ctx)).render(messageWidget(title, body, false), vw, vh, pxW, pxH)
    }

    private fun messageWidget(title: String, body: String, isError: Boolean): ListWidget {
        val w = ListWidget()
        w.backgroundColor = Color("1C1C1E")
        val t = w.addText(title)
        t.font = Font.boldSystemFont(14.0)
        t.textColor = if (isError) Color.red() else Color.white()
        w.addSpacer(4.0)
        val b = w.addText(body)
        b.font = Font.regularMonospacedSystemFont(10.0)
        b.textColor = Color.white()
        return w
    }
}
