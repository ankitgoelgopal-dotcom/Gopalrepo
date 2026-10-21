package com.newlang.keyboard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Setup + settings screen. Every change is saved to SharedPreferences and the
 * running keyboard picks it up immediately (it listens for preference changes).
 */
class SetupActivity : Activity() {

    private lateinit var sp: SharedPreferences
    private lateinit var col: LinearLayout
    private lateinit var titleView: TextView
    private val purple = Color.parseColor("#5B4BDB")

    private val iconAliases = listOf("IconPurple", "IconBlue", "IconGreen", "IconOrange")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun displayName(): String {
        val n = sp.getString("spaceLabel", "") ?: ""
        return if (n.isBlank()) getString(R.string.app_name) else n
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Prefs.sp(this)
        Layouts.loadCustom(sp)

        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(40), dp(20), dp(40))

        titleView = TextView(this)
        titleView.text = displayName()
        titleView.textSize = 26f
        titleView.setTextColor(purple)
        col.addView(titleView)

        note("Set the keyboard up in 2 steps. After that it works in every app. Changes below apply instantly.")

        button("1. Enable keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button("2. Choose keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        val test = EditText(this)
        test.hint = "Tap here to test the keyboard"
        test.gravity = Gravity.TOP
        test.minLines = 3
        col.addView(
            test,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(12) }
        )

        // ------------------------------------------------ keys & layout
        header("Keys & layout")
        toggle("Number row on the main page", "numberRow", true)
        note("Active layouts (tap the layout key next to the space bar to switch between them):")
        layoutPicker()
        header("Custom layout (add, remove or rearrange keys)")
        customEditor()

        // ------------------------------------------------ suggestions
        header("Suggestions & dictionary")
        toggle("Show word suggestions", "suggestions", true)
        note("Add your own words. One per line, written as  word = meaning . They get the highest priority in the suggestion bar.")
        dictionaryEditor()

        // ------------------------------------------------ look
        header("Look")
        choice("Theme", "theme", Prefs.themeNames, 0)
        choice("Accent colour (shift, enter, selected popup key)", "accent", Prefs.accentNames, 0)
        choice("Key height", "height", Prefs.heightNames, 1)
        choice("Key shape", "shape", Prefs.shapeNames, 0)

        // ------------------------------------------------ behavior
        header("Behavior")
        choice("Vibration on key press", "vibration", Prefs.vibNames, 1)
        toggle("Long-press popups (accents and extra characters)", "longPress", true)
        toggle("Auto-capitalise at the start of sentences", "autoCap", true)

        // ------------------------------------------------ app
        header("App")
        nameEditor()
        choice("App icon colour", "icon", Prefs.iconNames, 0) { applyIcon(it) }
        note("The icon can take a few seconds to change on your home screen. The name in Android's keyboard list is set at build time in strings.xml.")

        val stats = TextView(this)
        stats.setPadding(0, dp(24), 0, 0)
        stats.setTextColor(Color.parseColor("#666666"))
        col.addView(stats)
        Thread {
            Dictionary.load(applicationContext)
            runOnUiThread { stats.text = "Dictionary loaded: ${Dictionary.size} words" }
        }.start()

        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)
    }

    // ------------------------------------------------------------- UI helpers

    private fun header(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 18f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(purple)
        tv.setPadding(0, dp(28), 0, dp(6))
        col.addView(tv)
    }

    private fun note(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 13f
        tv.setTextColor(Color.parseColor("#666666"))
        tv.setPadding(0, dp(4), 0, dp(6))
        col.addView(tv)
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        col.addView(b)
        return b
    }

    private fun editor(hint: String, text: String, lines: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(text)
        e.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.minLines = lines
        e.gravity = Gravity.TOP
        col.addView(
            e,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return e
    }

    private fun toggle(t: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = t
        sw.textSize = 15f
        sw.isChecked = sp.getBoolean(key, def)
        sw.setPadding(0, dp(8), 0, dp(8))
        sw.setOnCheckedChangeListener { _, v -> sp.edit().putBoolean(key, v).apply() }
        col.addView(sw)
    }

    private fun choice(
        t: String, key: String, names: List<String>, def: Int,
        onPick: ((Int) -> Unit)? = null
    ) {
        val lbl = TextView(this)
        lbl.text = t
        lbl.textSize = 15f
        lbl.setPadding(0, dp(10), 0, 0)
        col.addView(lbl)

        val spin = Spinner(this)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spin.setSelection(sp.getInt(key, def).coerceIn(0, names.size - 1))
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != sp.getInt(key, def)) {
                    sp.edit().putInt(key, pos).apply()
                    onPick?.invoke(pos)
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(spin)
    }

    // ---------------------------------------------------------------- sections

    private fun layoutPicker() {
        val selected = Prefs.parseLayouts(sp.getString("layouts", null))
        val boxes = ArrayList<CheckBox>()
        Layouts.all.forEachIndexed { i, l ->
            val cb = CheckBox(this)
            cb.text = l.name + "  [" + l.short + "]"
            cb.textSize = 15f
            cb.isChecked = i in selected
            cb.setOnCheckedChangeListener { b, _ ->
                val chosen = boxes.indices.filter { boxes[it].isChecked }
                if (chosen.isEmpty()) {
                    b.isChecked = true          // always keep at least one layout
                } else {
                    sp.edit().putString("layouts", chosen.joinToString(",")).apply()
                }
            }
            boxes.add(cb)
            col.addView(cb)
        }
    }

    private fun customEditor() {
        note(
            "Type your own keys: one row per line, up to 3 rows. Letters can be written together " +
                "(qwertyuiop) or separated by spaces when a key has several characters " +
                "(क्ष त्र ज्ञ). Then tick \"Custom\" in the list above."
        )
        val normal = editor(
            "Row 1\nRow 2\nRow 3",
            sp.getString("customRows", null) ?: Layouts.DEFAULT_CUSTOM,
            3
        )
        note("Shifted / capital keys (optional, same shape; leave empty to auto-capitalise):")
        val shifted = editor("(optional)", sp.getString("customShifted", "") ?: "", 3)
        toggle("Word suggestions on the custom layout (Latin letters)", "customLatin", true)

        button("Save custom layout") {
            sp.edit()
                .putString("customRows", normal.text.toString())
                .putString("customShifted", shifted.text.toString())
                .apply()
            toast("Custom layout saved")
        }
        button("Reset custom layout to QWERTY") {
            normal.setText(Layouts.DEFAULT_CUSTOM)
            shifted.setText("")
            sp.edit()
                .putString("customRows", Layouts.DEFAULT_CUSTOM)
                .putString("customShifted", "")
                .apply()
            toast("Custom layout reset")
        }
    }

    private fun dictionaryEditor() {
        val words = editor(
            "kora = house\nmira = mother",
            Dictionary.readCustom(this).joinToString("\n") { it.first + " = " + it.second },
            5
        )
        button("Save my words") {
            val list = words.text.toString().lines().mapNotNull { Dictionary.parseLine(it) }
            Dictionary.writeCustom(this, list)
            // bump a preference so the running keyboard reloads the dictionary right away
            sp.edit().putLong("dictRev", System.currentTimeMillis()).apply()
            toast("${list.size} words saved")
        }
    }

    private fun nameEditor() {
        note("Keyboard name (shown on the space bar and at the top of this screen):")
        val box = EditText(this)
        box.hint = getString(R.string.app_name)
        box.setText(sp.getString("spaceLabel", "") ?: "")
        box.setSingleLine(true)
        col.addView(
            box,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        button("Save name") {
            sp.edit().putString("spaceLabel", box.text.toString().trim()).apply()
            titleView.text = displayName()
            toast("Name saved")
        }
    }

    /** Enables the chosen launcher-icon alias and disables the others. */
    private fun applyIcon(sel: Int) {
        val pm = packageManager
        val pkg = packageName
        // enable the new one first so there is always a launcher entry
        iconAliases.forEachIndexed { i, n ->
            if (i == sel) {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "com.newlang.keyboard.$n"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        iconAliases.forEachIndexed { i, n ->
            if (i != sel) {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "com.newlang.keyboard.$n"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        toast("Icon changed")
    }
}
