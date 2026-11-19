package com.scriptwidgets

import android.content.Context
import java.io.File

/** Scripts live as plain .js files in the app's private storage. */
object ScriptStore {

    private fun dir(ctx: Context): File = File(ctx.filesDir, "scripts").apply { mkdirs() }

    fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "Untitled" }

    private fun file(ctx: Context, name: String) = File(dir(ctx), sanitize(name) + ".js")

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.extension == "js" }
            ?.map { it.nameWithoutExtension }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

    fun exists(ctx: Context, name: String) = file(ctx, name).exists()

    fun read(ctx: Context, name: String): String =
        file(ctx, name).takeIf { it.exists() }?.readText() ?: ""

    fun write(ctx: Context, name: String, text: String) {
        file(ctx, name).writeText(text)
    }

    fun delete(ctx: Context, name: String) {
        file(ctx, name).delete()
    }

    const val TEMPLATE = """let widget = new ListWidget()
widget.backgroundColor = new Color("#1c1c1e")

let title = widget.addText("Hello, Android")
title.font = Font.boldSystemFont(20)
title.textColor = Color.white()

Script.setWidget(widget)
Script.complete()
"""

    fun seed(ctx: Context) {
        if (list(ctx).isNotEmpty()) return
        write(ctx, "Hello Widget", HELLO)
        write(ctx, "Battery and Time", BATTERY)
        write(ctx, "GitHub Zen", ZEN)
    }

    private const val HELLO = """// Hello Widget
let w = new ListWidget()
let g = new LinearGradient()
g.colors = [new Color("#4facfe"), new Color("#00f2fe")]
g.locations = [0, 1]
g.startPoint = new Point(0, 0)
g.endPoint = new Point(1, 1)
w.backgroundGradient = g

let t = w.addText("Hello, Android")
t.font = Font.boldSystemFont(22)
t.textColor = Color.white()
w.addSpacer(4)

let d = w.addDate(new Date())
d.applyDateStyle()
d.font = Font.mediumSystemFont(14)
d.textColor = new Color("#ffffff", 0.85)
w.addSpacer()

let arg = args.widgetParameter
let p = w.addText(arg ? "Parameter: " + arg : "Set a widget parameter")
p.font = Font.regularSystemFont(11)
p.textColor = new Color("#ffffff", 0.7)

Script.setWidget(w)
Script.complete()
"""

    private const val BATTERY = """// Battery and Time
let w = new ListWidget()
w.backgroundColor = new Color("#111827")
w.refreshAfterDate = new Date(Date.now() + 15 * 60 * 1000)

let df = new DateFormatter()
df.dateFormat = "HH:mm"
let time = w.addText(df.string(new Date()))
time.font = Font.boldSystemFont(40)
time.textColor = Color.white()

df.dateFormat = "EEEE, MMM d"
let day = w.addText(df.string(new Date()))
day.font = Font.mediumSystemFont(14)
day.textColor = new Color("#9ca3af")
w.addSpacer()

let level = Math.round(Device.batteryLevel() * 100)
let row = w.addStack()
row.centerAlignContent()
let label = row.addText("Battery")
label.font = Font.mediumSystemFont(13)
label.textColor = new Color("#9ca3af")
row.addSpacer()
let pct = row.addText(level + "%" + (Device.isCharging() ? " +" : ""))
pct.font = Font.boldSystemFont(15)
pct.textColor = level > 20 ? Color.green() : Color.red()

Script.setWidget(w)
Script.complete()
"""

    private const val ZEN = """// GitHub Zen - fetches a line over the network
let w = new ListWidget()
w.backgroundColor = new Color("#0d1117")
w.setPadding(14, 14, 14, 14)

let quote
try {
  let req = new Request("https://api.github.com/zen")
  quote = await req.loadString()
} catch (e) {
  quote = "Could not load: " + e
}

let head = w.addText("GITHUB ZEN")
head.font = Font.boldSystemFont(10)
head.textColor = new Color("#8b949e")
w.addSpacer(6)
let q = w.addText(quote)
q.font = Font.mediumSystemFont(16)
q.textColor = Color.white()
q.minimumScaleFactor = 0.7

Script.setWidget(w)
Script.complete()
"""
}
