package com.scriptwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import kotlin.concurrent.thread

/** Shown when a widget is added (or reconfigured): choose the script and optional parameter. */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        title = "Choose script"
        ScriptStore.seed(this)
        val names = ScriptStore.list(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        root.addView(TextView(this).apply { text = "Script"; textSize = 14f })
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ConfigActivity, android.R.layout.simple_spinner_dropdown_item, names
            )
            val current = WidgetPrefs.script(this@ConfigActivity, widgetId)
            setSelection(names.indexOf(current).coerceAtLeast(0))
        }
        root.addView(spinner)

        root.addView(TextView(this).apply {
            text = "Parameter (args.widgetParameter)"
            textSize = 14f
            setPadding(0, dp(16), 0, 0)
        })
        val param = EditText(this).apply {
            setSingleLine()
            setText(WidgetPrefs.param(this@ConfigActivity, widgetId) ?: "")
        }
        root.addView(param)

        val live = CheckBox(this).apply {
            text = "Live mode (updates every second while the screen is on)"
            isChecked = WidgetPrefs.live(this@ConfigActivity, widgetId)
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(live)

        val ok = Button(this).apply {
            text = "Add widget"
            setOnClickListener {
                if (names.isEmpty()) return@setOnClickListener
                WidgetPrefs.save(
                    this@ConfigActivity, widgetId,
                    names[spinner.selectedItemPosition],
                    param.text.toString().ifBlank { null },
                    live.isChecked
                )
                if (live.isChecked) LiveService.startIfNeeded(this@ConfigActivity) else LiveService.stopIfIdle(this@ConfigActivity)
                val app = applicationContext
                val id = widgetId
                thread { WidgetUpdater.update(app, AppWidgetManager.getInstance(app), id) }
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        }
        root.addView(ok)
        setContentView(root)
    }
}
