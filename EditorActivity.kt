package com.scriptwidgets

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import kotlin.concurrent.thread

class EditorActivity : Activity() {

    private lateinit var name: String
    private lateinit var edit: EditText
    private lateinit var param: EditText
    private lateinit var preview: ImageView
    private lateinit var logView: TextView
    private lateinit var spinner: Spinner
    private var lastSaved = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        name = intent.getStringExtra("name") ?: run { finish(); return }
        title = name

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@EditorActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Small", "Medium", "Large")
            )
            setSelection(1)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (::edit.isInitialized) runScript()
                }

                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        val run = Button(this).apply {
            text = "Run"
            setOnClickListener { runScript() }
        }
        row.addView(spinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(run)
        root.addView(row)

        param = EditText(this).apply {
            hint = "Widget parameter (optional)"
            setSingleLine()
            textSize = 13f
        }
        root.addView(param)

        preview = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = dp(220)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(
            preview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6); bottomMargin = dp(6) }
        )

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            maxLines = 4
        }
        root.addView(logView)

        edit = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD // disables autocorrect/smart quotes
            setHorizontallyScrolling(false)
        }
        root.addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        lastSaved = ScriptStore.read(this, name)
        edit.setText(lastSaved)
        runScript()
    }

    private fun runScript() {
        val src = edit.text.toString()
        ScriptStore.write(this, name, src)
        val family = listOf("small", "medium", "large")[spinner.selectedItemPosition]
        val (w, h) = when (family) {
            "small" -> 465 to 465
            "medium" -> 987 to 465
            else -> 987 to 1035
        }
        val p = param.text.toString().ifBlank { null }
        val app = applicationContext
        logView.text = "Running…"
        thread {
            val out = try {
                WidgetEngine.run(app, src, p, w, h, family, 20_000)
            } catch (t: Throwable) {
                null
            }
            runOnUiThread {
                if (out != null) {
                    preview.setImageBitmap(out.bitmap)
                    logView.text = (out.error?.let { "Error: $it\n" } ?: "") + out.logs
                } else {
                    logView.text = "Render failed"
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Run").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) runScript()
        return true
    }

    override fun onPause() {
        super.onPause()
        if (!::edit.isInitialized) return
        val src = edit.text.toString()
        ScriptStore.write(this, name, src)
        if (src != lastSaved) {
            lastSaved = src
            val app = applicationContext
            thread { WidgetUpdater.updateAll(app) } // refresh any widgets using the edited script
        }
    }
}
