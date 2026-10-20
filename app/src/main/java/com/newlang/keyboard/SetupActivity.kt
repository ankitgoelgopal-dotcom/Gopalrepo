package com.newlang.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(48), dp(24), dp(24))

        val title = TextView(this)
        title.text = getString(R.string.app_name)
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#5B4BDB"))
        col.addView(title)

        val info = TextView(this)
        info.textSize = 15f
        info.setPadding(0, dp(8), 0, dp(16))
        info.text = "Set the keyboard up in 2 steps. After that it works in every app."
        col.addView(info)

        val b1 = Button(this)
        b1.text = "1. Enable keyboard"
        b1.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        col.addView(b1)

        val b2 = Button(this)
        b2.text = "2. Choose keyboard"
        b2.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        col.addView(b2)

        val test = EditText(this)
        test.hint = "Tap here to test the keyboard"
        test.gravity = Gravity.TOP
        test.minLines = 4
        col.addView(
            test,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(24) }
        )

        val stats = TextView(this)
        stats.setPadding(0, dp(16), 0, 0)
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
}
