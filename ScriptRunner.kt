package com.scriptwidgets

import android.content.Context as AContext
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import org.json.JSONObject
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.ScriptableObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.mozilla.javascript.Context as JsContext

class HttpResult(val status: Int, val body: String, val headersJson: String)

class ScriptHost {
    @JvmField
    var captured: ListWidget? = null

    fun setWidget(w: ListWidget?) {
        captured = w
    }

    fun complete() {}
}

class ConsoleBridge(private val sb: StringBuilder) {
    fun log(vararg a: Any?) {
        sb.append(a.joinToString(" ") { str(it) }).append('\n')
    }

    fun warn(vararg a: Any?) = log(*a)
    fun error(vararg a: Any?) = log(*a)

    private fun str(x: Any?): String = try {
        JsContext.toString(x)
    } catch (e: Exception) {
        x.toString()
    }
}

/** Native functions the JS prelude builds on. */
class Bridge(private val ctx: AContext) {

    private companion object {
        val cache = HashMap<String, Pair<Long, HttpResult>>()
    }

    /** In live mode the script re-runs every second, so identical GET requests are cached for 60s. */
    fun http(url: String, method: String?, headersJson: String?, body: String?, timeoutMs: Int): HttpResult {
        val cacheable = ScriptEnv.live && (method == null || method == "GET") && body.isNullOrEmpty()
        val key = url + "|" + (headersJson ?: "")
        if (cacheable) {
            synchronized(cache) {
                val hit = cache[key]
                if (hit != null && System.currentTimeMillis() - hit.first < 60_000) return hit.second
            }
        }
        val res = httpReal(url, method, headersJson, body, timeoutMs)
        if (cacheable) synchronized(cache) { cache[key] = Pair(System.currentTimeMillis(), res) }
        return res
    }

    private fun httpReal(url: String, method: String?, headersJson: String?, body: String?, timeoutMs: Int): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method ?: "GET"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        if (!headersJson.isNullOrEmpty()) {
            val h = JSONObject(headersJson)
            for (k in h.keys()) conn.setRequestProperty(k, h.optString(k))
        }
        if (!body.isNullOrEmpty()) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val hdrs = JSONObject()
        for ((k, v) in conn.headerFields) {
            if (k != null && v != null) hdrs.put(k, v.joinToString(", "))
        }
        conn.disconnect()
        return HttpResult(code, text, hdrs.toString())
    }

    fun image(url: String): ScriptImage? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.let { ScriptImage(it) }
        } finally {
            conn.disconnect()
        }
    }

    fun batteryLevel(): Double {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0.0
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) 0.0 else level.toDouble() / scale
    }

    fun isCharging(): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val s = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isDark(): Boolean = WidgetEngine.isDark(ctx)

    fun density(): Double = ctx.resources.displayMetrics.density.toDouble()
    fun screenW(): Double = ctx.resources.displayMetrics.let { it.widthPixels / it.density.toDouble() }
    fun screenH(): Double = ctx.resources.displayMetrics.let { it.heightPixels / it.density.toDouble() }
    fun sdk(): Int = Build.VERSION.SDK_INT
    fun model(): String = Build.MODEL

    private fun style(s: String?): Int = when (s) {
        "Short" -> DateFormat.SHORT
        "Medium" -> DateFormat.MEDIUM
        "Long" -> DateFormat.LONG
        "Full" -> DateFormat.FULL
        else -> -1
    }

    fun formatDate(ms: Double, pattern: String?, ds: String?, ts: String?, styled: Boolean): String {
        val d = Date(ms.toLong())
        val f: DateFormat = when {
            !pattern.isNullOrEmpty() -> SimpleDateFormat(pattern, Locale.getDefault())
            !styled -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            style(ds) < 0 && style(ts) < 0 -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            style(ds) < 0 -> DateFormat.getTimeInstance(style(ts))
            style(ts) < 0 -> DateFormat.getDateInstance(style(ds))
            else -> DateFormat.getDateTimeInstance(style(ds), style(ts))
        }
        return f.format(d)
    }

    fun parseDate(s: String, pattern: String?): Double = try {
        val p = if (pattern.isNullOrEmpty()) "yyyy-MM-dd'T'HH:mm:ssXXX" else pattern
        SimpleDateFormat(p, Locale.getDefault()).parse(s)?.time?.toDouble() ?: Double.NaN
    } catch (e: Exception) {
        Double.NaN
    }
}

object ScriptRunner {

    class Result(val widget: ListWidget?, val logs: String, val error: String?)

    private class ScriptTimeout : Error("Script timed out")

    private class Factory(private val deadline: Long) : ContextFactory() {
        override fun makeContext(): JsContext {
            val cx = super.makeContext()
            cx.optimizationLevel = -1 // interpreter mode: required on Android
            cx.languageVersion = JsContext.VERSION_ES6
            cx.instructionObserverThreshold = 20000
            return cx
        }

        override fun observeInstructionCount(cx: JsContext, instructionCount: Int) {
            if (System.currentTimeMillis() > deadline) throw ScriptTimeout()
        }
    }

    /**
     * Requests are blocking, so `await` is a no-op: we strip async/await keywords and let
     * every call run synchronously. Scriptable-style scripts keep working unchanged.
     */
    private fun transform(src: String): String = src
        .replace(Regex("\\bawait\\s+"), "")
        .replace(Regex("\\basync\\s+(?=function\\b|\\(|[A-Za-z_])"), "")

    fun run(app: AContext, source: String, family: String, param: String?, timeoutMs: Long): Result {
        val logs = StringBuilder()
        Capture.last = null
        ScriptEnv.app = app
        val factory = Factory(System.currentTimeMillis() + timeoutMs)
        val cx = factory.enterContext()
        var widget: ListWidget? = null
        var error: String? = null
        try {
            val scope = cx.initStandardObjects()
            val host = ScriptHost()

            fun expose(name: String, value: Any?) {
                ScriptableObject.putProperty(scope, name, JsContext.javaToJS(value, scope))
            }
            expose("Script", host)
            expose("console", ConsoleBridge(logs))
            expose("__b", Bridge(app))

            val classes = listOf<Class<*>>(
                Color::class.java, Font::class.java, Size::class.java, Point::class.java,
                LinearGradient::class.java, ListWidget::class.java,
                Rect::class.java, DrawContext::class.java, FileManager::class.java
            )
            for (c in classes) {
                ScriptableObject.putProperty(scope, c.simpleName, NativeJavaClass(scope, c))
            }

            val paramLit = if (param == null) "null" else JSONObject.quote(param)
            cx.evaluateString(
                scope,
                "var config={runsInWidget:true,runsInApp:false,runsWithSiri:false,runsFromHomeScreen:false,widgetFamily:'$family'};" +
                    "var args={widgetParameter:$paramLit,shortcutParameter:null,plainTexts:[],queryParameters:{},fileURLs:[],images:[],urls:[]};",
                "env", 1, null
            )
            val prelude = app.assets.open("prelude.js").bufferedReader().use { it.readText() }
            cx.evaluateString(scope, prelude, "prelude", 1, null)
            cx.evaluateString(scope, transform(source), "script", 1, null)
            widget = host.captured ?: Capture.last
        } catch (e: RhinoException) {
            val line = if (e.sourceName() == "script" && e.lineNumber() > 0) " (line ${e.lineNumber()})" else ""
            error = e.details() + line
        } catch (e: ScriptTimeout) {
            error = "Script timed out"
        } catch (e: Throwable) {
            error = e.message ?: e.toString()
        } finally {
            JsContext.exit()
        }
        return Result(widget, logs.toString(), error)
    }
}
