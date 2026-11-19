package com.scriptwidgets

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView

class MainActivity : Activity() {

    private lateinit var list: ListView
    private var names: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Script Widgets"
        ScriptStore.seed(this)
        list = ListView(this)
        setContentView(list)
        list.setOnItemClickListener { _, _, pos, _ -> openEditor(names[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(names[pos])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        names = ScriptStore.list(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun openEditor(name: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("name", name))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "New script").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "How to add a widget")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> newScript()
            2 -> AlertDialog.Builder(this)
                .setTitle("Add a widget")
                .setMessage(
                    "Long-press your home screen, choose Widgets, and drag \"Script Widgets\" onto it. " +
                        "Pick a script (and an optional parameter) when asked.\n\n" +
                        "Tap a widget to refresh it. Long-press a script here to delete it."
                )
                .setPositiveButton("OK", null)
                .show()
        }
        return true
    }

    private fun newScript() {
        val input = EditText(this).apply { hint = "Script name" }
        AlertDialog.Builder(this)
            .setTitle("New script")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = ScriptStore.sanitize(input.text.toString())
                if (!ScriptStore.exists(this, name)) ScriptStore.write(this, name, ScriptStore.TEMPLATE)
                openEditor(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"$name\"?")
            .setPositiveButton("Delete") { _, _ ->
                ScriptStore.delete(this, name)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
