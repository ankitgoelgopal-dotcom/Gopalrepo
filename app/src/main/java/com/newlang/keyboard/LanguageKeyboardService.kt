package com.newlang.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class LanguageKeyboardService : InputMethodService() {

    private val BG = Color.parseColor("#1E1F24")
    private val KEY = Color.parseColor("#3A3C44")
    private val SPECIAL = Color.parseColor("#2A2C32")
    private val ACCENT = Color.parseColor("#5B4BDB")
    private val STRIP_BG = Color.parseColor("#17181C")

    private val composing = StringBuilder()
    private var shift = 0            // 0 = off, 1 = once, 2 = caps lock
    private var symbols = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var keysContainer: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var stripScroll: HorizontalScrollView

    private val repeatDelete = object : Runnable {
        override fun run() {
            doBackspace()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Thread { Dictionary.load(applicationContext) }.start()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        stripScroll = HorizontalScrollView(this)
        stripScroll.isHorizontalScrollBarEnabled = false
        stripScroll.setBackgroundColor(STRIP_BG)
        strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.gravity = Gravity.CENTER_VERTICAL
        stripScroll.addView(
            strip,
            HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, dp(44)
            )
        )
        root.addView(
            stripScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        keysContainer = LinearLayout(this)
        keysContainer.orientation = LinearLayout.VERTICAL
        keysContainer.setPadding(dp(3), dp(2), dp(3), dp(6))
        root.addView(
            keysContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        renderKeys()
        updateSuggestions()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.clear()
        symbols = false
        if (::keysContainer.isInitialized) {
            renderKeys()
            updateSuggestions()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (composing.isNotEmpty() && (candidatesEnd == -1 || newSelStart != candidatesEnd)) {
            composing.clear()
            if (::strip.isInitialized) updateSuggestions()
        }
    }

    // ---------------------------------------------------------------- layout

    private fun renderKeys() {
        keysContainer.removeAllViews()

        // Digits are always visible: every word in the language ends with a number.
        keysContainer.addView(row("1234567890".map { charKey(it) }))

        if (!symbols) {
            keysContainer.addView(row("qwertyuiop".map { charKey(it) }))
            val r2 = ArrayList<View>()
            r2.add(spacer(0.5f))
            "asdfghjkl".forEach { r2.add(charKey(it)) }
            r2.add(spacer(0.5f))
            keysContainer.addView(row(r2))

            val r3 = ArrayList<View>()
            val shiftLabel = when (shift) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
            r3.add(key(shiftLabel, 1.5f, if (shift != 0) ACCENT else SPECIAL) {
                shift = (shift + 1) % 3
                renderKeys()
            })
            "zxcvbnm".forEach { r3.add(charKey(it)) }
            r3.add(backspaceKey())
            keysContainer.addView(row(r3))
        } else {
            keysContainer.addView(row("@#$%&-+()/".map { charKey(it) }))
            keysContainer.addView(row("*\"':;!?_=~".map { charKey(it) }))
            val r3 = ArrayList<View>()
            r3.add(spacer(0.75f))
            "[]{}<>^|\\".forEach { r3.add(charKey(it)) }
            r3.add(spacer(0.25f))
            r3.add(backspaceKey())
            keysContainer.addView(row(r3))
        }

        val bottom = ArrayList<View>()
        bottom.add(key(if (symbols) "ABC" else "?123", 1.3f, SPECIAL) {
            symbols = !symbols
            renderKeys()
        })
        bottom.add(key("\uD83C\uDF10", 1.0f, SPECIAL) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        bottom.add(key("Vi", 1.1f, SPECIAL) { insertVi() })   // plural marker
        bottom.add(key("space", 3.6f, KEY) { onSpace() })
        bottom.add(charKey(',', 0.9f))
        bottom.add(charKey('.', 0.9f))
        bottom.add(key("⏎", 1.4f, ACCENT) { onEnter() })
        keysContainer.addView(row(bottom))
    }

    private fun row(views: List<View>): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) r.addView(v)
        return r
    }

    private fun spacer(weight: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, 1, weight)
        return v
    }

    private fun charKey(c: Char, weight: Float = 1f): TextView {
        val label = if (c.isLetter() && shift != 0 && !symbols) c.uppercaseChar().toString() else c.toString()
        return key(label, weight, KEY) {
            val out = if (c.isLetter() && shift != 0 && !symbols) c.uppercaseChar() else c
            typeChar(out)
            if (shift == 1) {
                shift = 0
                renderKeys()
            }
        }
    }

    private fun key(label: String, weight: Float, color: Int, onTap: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.WHITE)
        tv.textSize = if (label.length > 1) 14f else 19f
        tv.background = bgDrawable(color)
        val lp = LinearLayout.LayoutParams(0, dp(46), weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        tv.layoutParams = lp
        tv.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onTap()
        }
        return tv
    }

    private fun backspaceKey(): TextView {
        val k = key("⌫", 1.5f, SPECIAL) {}
        k.setOnClickListener(null)
        k.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    doBackspace()
                    handler.postDelayed(repeatDelete, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                }
            }
            true
        }
        return k
    }

    private fun round(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(7).toFloat()
        g.setColor(color)
        return g
    }

    private fun bgDrawable(color: Int): StateListDrawable {
        val s = StateListDrawable()
        val r = minOf(255, Color.red(color) + 45)
        val g = minOf(255, Color.green(color) + 45)
        val b = minOf(255, Color.blue(color) + 45)
        s.addState(intArrayOf(android.R.attr.state_pressed), round(Color.rgb(r, g, b)))
        s.addState(intArrayOf(), round(color))
        return s
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- typing

    private fun typeChar(ch: Char) {
        val ic = currentInputConnection ?: return
        if (ch.isLetterOrDigit()) {
            composing.append(ch)
            ic.setComposingText(composing.toString(), 1)
        } else {
            commitComposing()
            ic.commitText(ch.toString(), 1)
        }
        updateSuggestions()
    }

    private fun commitComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing.toString(), 1)
            composing.clear()
        }
    }

    private fun insertVi() {
        val ic = currentInputConnection ?: return
        commitComposing()
        ic.commitText("Vi ", 1)
        updateSuggestions()
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.commitText(composing.toString() + " ", 1)
            composing.clear()
        } else {
            ic.commitText(" ", 1)
        }
        updateSuggestions()
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        commitComposing()
        val ei = currentInputEditorInfo
        val action = if (ei != null) ei.imeOptions and EditorInfo.IME_MASK_ACTION else 0
        val noEnterAction =
            ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateSuggestions()
    }

    private fun doBackspace() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            ic.setComposingText(composing.toString(), 1)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateSuggestions()
    }

    private fun pick(e: Entry) {
        val ic = currentInputConnection ?: return
        ic.commitText(e.word + " ", 1)
        composing.clear()
        updateSuggestions()
    }

    // ----------------------------------------------------------- suggestions

    private fun updateSuggestions() {
        if (!::strip.isInitialized) return
        strip.removeAllViews()
        if (composing.isEmpty()) {
            addChip("Type a word or its English meaning…", null)
            stripScroll.scrollTo(0, 0)
            return
        }
        if (!Dictionary.ready) {
            addChip("Loading dictionary…", null)
            return
        }
        val s = composing.toString()
        val byWord = Dictionary.byWord(s, 10)
        val byMeaning =
            if (s.none { it.isDigit() }) Dictionary.byMeaning(s, 10) else emptyList()
        val all = byWord + byMeaning.filter { m -> byWord.none { it.word == m.word } }
        if (all.isEmpty()) {
            addChip("No match", null)
        } else {
            for (e in all) addChip("${e.word}  ·  ${e.meaning}", e)
        }
        stripScroll.scrollTo(0, 0)
    }

    private fun addChip(text: String, entry: Entry?) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        if (entry != null) {
            tv.setTextColor(Color.parseColor("#9FD3FF"))
            tv.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                pick(entry)
            }
        } else {
            tv.setTextColor(Color.parseColor("#80848F"))
        }
        strip.addView(tv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }
}
