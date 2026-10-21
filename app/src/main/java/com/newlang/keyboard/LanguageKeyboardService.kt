package com.newlang.keyboard

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import java.util.concurrent.Executors

class LanguageKeyboardService : InputMethodService() {

    private lateinit var cfg: Config
    private lateinit var th: Theme
    private var accent = Color.parseColor("#5B4BDB")
    private var layoutPos = 0

    private val composing = StringBuilder()
    private var shift = 0            // 0 = off, 1 = once, 2 = caps lock
    private var symbols = false
    private val handler = Handler(Looper.getMainLooper())

    // --- speed: suggestions are computed off the main thread, vibration too ---
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var suggestSeq = 0
    private var chipKey: String? = null
    private val vibThread = HandlerThread("kb-vibrate").also { it.start() }
    private val vibHandler = Handler(vibThread.looper)
    private val vibrator: Vibrator? by lazy { getSystemService(VIBRATOR_SERVICE) as? Vibrator }
    private val effects = HashMap<Long, VibrationEffect>()

    // --- speed: letter keys are updated in place instead of rebuilding the keyboard ---
    private class LetterKey(val tv: TextView, val row: Int, val col: Int)
    private val letterKeys = ArrayList<LetterKey>()
    private var shiftKey: TextView? = null

    private var rootView: LinearLayout? = null
    private lateinit var keysContainer: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var stripScroll: HorizontalScrollView

    // long-press popup state
    private var popup: PopupWindow? = null
    private var popItems: List<TextView> = emptyList()
    private var popAlts: List<String> = emptyList()
    private var popLeft = 0
    private var popItemW = 0
    private var popSel = 0

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            applyConfig()
            Thread { Dictionary.load(applicationContext) }.start()
        }
    }

    private val repeatDelete = object : Runnable {
        override fun run() {
            doBackspace()
            buzz()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadConfig()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        Thread {
            Dictionary.load(applicationContext)
            handler.post { updateSuggestions() }
        }.start()
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        worker.shutdownNow()
        vibThread.quitSafely()
        super.onDestroy()
    }

    private fun loadConfig() {
        cfg = Config(this)
        th = Prefs.themes[cfg.theme]
        accent = Prefs.accents[cfg.accent]
        if (layoutPos >= cfg.layouts.size) layoutPos = 0
    }

    private fun applyConfig() {
        loadConfig()
        rootView?.setBackgroundColor(th.bg)
        if (::stripScroll.isInitialized) stripScroll.setBackgroundColor(th.strip)
        chipKey = null
        if (::keysContainer.isInitialized) {
            renderKeys()
            updateSuggestions()
        }
    }

    private fun curLayout(): KbLayout = Layouts.all[cfg.layouts[layoutPos % cfg.layouts.size]]

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)
        rootView = root

        stripScroll = HorizontalScrollView(this)
        stripScroll.isHorizontalScrollBarEnabled = false
        stripScroll.setBackgroundColor(th.strip)
        strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.gravity = Gravity.CENTER_VERTICAL
        stripScroll.addView(
            strip,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(44))
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
        val wasSymbols = symbols
        symbols = false
        if (!Dictionary.ready) Thread { Dictionary.load(applicationContext) }.start()
        if (::keysContainer.isInitialized) {
            if (wasSymbols) {
                shift = 0
                renderKeys()
            } else {
                setShift(0)
            }
            autoCap()
            updateSuggestions()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
        dismissPopup()
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
        letterKeys.clear()
        shiftKey = null
        val lay = curLayout()
        stripScroll.visibility = if (lay.latin && cfg.suggestions) View.VISIBLE else View.GONE

        // Number row: always on the main page when enabled, otherwise on the symbols page.
        if (cfg.numberRow || symbols) {
            keysContainer.addView(row("1234567890".map { charKey(it.toString()) }))
        }

        if (!symbols) {
            val rows = if (shift != 0) lay.shifted else lay.normal
            keysContainer.addView(row(rows[0].mapIndexed { c, t -> letterKey(0, c, t) }))

            val r2 = ArrayList<View>()
            val pad2 = rows[1].size <= 9
            if (pad2) r2.add(spacer(0.5f))
            rows[1].forEachIndexed { c, t -> r2.add(letterKey(1, c, t)) }
            if (pad2) r2.add(spacer(0.5f))
            keysContainer.addView(row(r2))

            val r3 = ArrayList<View>()
            val shiftLabel = when (shift) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
            val sk = key(shiftLabel, 1.5f, if (shift != 0) accent else th.special,
                textColor = if (shift != 0) Color.WHITE else th.text) {
                setShift((shift + 1) % 3)
            }
            shiftKey = sk
            r3.add(sk)
            val missing = 7 - rows[2].size
            if (missing > 0) r3.add(spacer(missing / 2f))
            rows[2].forEachIndexed { c, t -> r3.add(letterKey(2, c, t)) }
            if (missing > 0) r3.add(spacer(missing / 2f))
            r3.add(backspaceKey())
            keysContainer.addView(row(r3))
        } else {
            keysContainer.addView(row("@#\$%&-+()/".map { charKey(it.toString()) }))
            keysContainer.addView(row("*\"':;!?_=~".map { charKey(it.toString()) }))
            val r3 = ArrayList<View>()
            r3.add(spacer(0.75f))
            "[]{}<>^|\\".forEach { r3.add(charKey(it.toString())) }
            r3.add(spacer(0.25f))
            r3.add(backspaceKey())
            keysContainer.addView(row(r3))
        }

        val bottom = ArrayList<View>()
        bottom.add(key(if (symbols) "ABC" else "?123", 1.2f, th.special) {
            symbols = !symbols
            renderKeys()
        })
        bottom.add(key("\uD83C\uDF10", 0.9f, th.special) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        if (cfg.layouts.size > 1) {
            bottom.add(key(lay.short, 0.9f, th.special) {
                commitComposing()
                layoutPos = (layoutPos + 1) % cfg.layouts.size
                shift = 0
                symbols = false
                renderKeys()
                updateSuggestions()
                autoCap()
            })
        }
        if (lay.latin) {
            bottom.add(key("Vi", 0.9f, th.special) { insertVi() })   // plural marker
        }
        val spaceText = if (cfg.spaceLabel.isBlank()) "space" else cfg.spaceLabel
        bottom.add(key(spaceText, 3.2f, th.key) { onSpace() })
        bottom.add(charKey(",", 0.8f))
        bottom.add(charKey(".", 0.8f))
        bottom.add(key("⏎", 1.3f, accent, textColor = Color.WHITE) { onEnter() })
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

    private fun afterChar() {
        if (shift == 1 && !symbols) setShift(0)
    }

    private fun charKey(s: String, weight: Float = 1f): TextView =
        key(s, weight, th.key, alts = Alts.get(s), fireOnDown = true) {
            typeText(s)
            afterChar()
        }

    /** A letter of the main page; its text changes in place when shift changes. */
    private fun letterKey(r: Int, c: Int, label: String): TextView {
        val holder = arrayOfNulls<TextView>(1)
        val tv = key(label, 1f, th.key,
            altsFn = { Alts.get(holder[0]?.text?.toString() ?: label) },
            fireOnDown = true) {
            typeText(holder[0]?.text?.toString() ?: label)
            afterChar()
        }
        holder[0] = tv
        letterKeys.add(LetterKey(tv, r, c))
        return tv
    }

    private fun setShift(n: Int) {
        if (n == shift) return
        val lettersChange = (n != 0) != (shift != 0)
        shift = n
        if (symbols) return
        if (lettersChange) refreshLetters()
        refreshShiftKey()
    }

    private fun refreshLetters() {
        val lay = curLayout()
        val rows = if (shift != 0) lay.shifted else lay.normal
        for (k in letterKeys) {
            val t = rows.getOrNull(k.row)?.getOrNull(k.col) ?: continue
            k.tv.text = t
            k.tv.textSize = if (t.length > 1) 14f else 19f
        }
    }

    private fun refreshShiftKey() {
        val sk = shiftKey ?: return
        sk.text = when (shift) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
        sk.background = bgDrawable(if (shift != 0) accent else th.special)
        sk.setTextColor(if (shift != 0) Color.WHITE else th.text)
    }

    private fun key(
        label: String,
        weight: Float,
        color: Int,
        alts: List<String> = emptyList(),
        altsFn: (() -> List<String>)? = null,
        fireOnDown: Boolean = false,
        textColor: Int = th.text,
        onTap: () -> Unit
    ): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.setTextColor(textColor)
        tv.textSize = if (label.length > 1) 14f else 19f
        tv.background = bgDrawable(color)
        val lp = LinearLayout.LayoutParams(0, dp(cfg.keyHeight), weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        tv.layoutParams = lp

        var longFired = false
        var firedDown = false
        val longRunnable = Runnable {
            val a = altsFn?.invoke() ?: alts
            if (a.isNotEmpty()) {
                longFired = true
                buzz()
                showPopup(tv, a)
            }
        }
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false
                    firedDown = false
                    v.isPressed = true
                    buzz()
                    val a = altsFn?.invoke() ?: alts
                    if (cfg.longPress && a.isNotEmpty()) {
                        handler.postDelayed(longRunnable, 350)
                    } else if (fireOnDown) {
                        // no long-press possible: type immediately on touch-down (fastest)
                        firedDown = true
                        onTap()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longFired) updatePopup(e.rawX)
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    handler.removeCallbacks(longRunnable)
                    if (longFired) {
                        val chosen = dismissPopup()
                        if (chosen != null) commitAlt(chosen)
                    } else if (!firedDown) {
                        val slop = dp(10).toFloat()
                        if (e.x >= -slop && e.x <= v.width + slop && e.y >= -slop && e.y <= v.height + slop) {
                            onTap()
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(longRunnable)
                    dismissPopup()
                }
            }
            true
        }
        return tv
    }

    private fun backspaceKey(): TextView {
        val k = key("⌫", 1.5f, th.special) {}
        k.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    buzz()
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

    // ------------------------------------------------------------- look & feel

    private fun radius(): Float = when (cfg.shape) {
        1 -> dp(2).toFloat()
        2 -> dp(100).toFloat()
        else -> dp(7).toFloat()
    }

    private fun shape(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = radius()
        g.setColor(color)
        return g
    }

    private fun bgDrawable(color: Int): StateListDrawable {
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        val pressed = if (lum > 0.6) {
            Color.rgb(
                (Color.red(color) * 0.82).toInt(),
                (Color.green(color) * 0.82).toInt(),
                (Color.blue(color) * 0.82).toInt()
            )
        } else {
            Color.rgb(
                minOf(255, Color.red(color) + 45),
                minOf(255, Color.green(color) + 45),
                minOf(255, Color.blue(color) + 45)
            )
        }
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
        s.addState(intArrayOf(), shape(color))
        return s
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun buzz() {
        val ms = Prefs.vibMs[cfg.vibration]
        if (ms <= 0L) return
        vibHandler.post {
            val vib = vibrator ?: return@post
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val fx = effects.getOrPut(ms) {
                        VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    vib.vibrate(fx)
                } else {
                    vib.vibrate(ms)
                }
            } catch (e: Exception) {
            }
        }
    }

    // ------------------------------------------------------------ long-press popup

    private fun showPopup(anchor: View, alts: List<String>) {
        dismissPopup()
        val itemW = dp(40)
        val itemH = dp(48)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.setPadding(dp(4), dp(4), dp(4), dp(4))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(10).toFloat()
        bg.setColor(th.special)
        bg.setStroke(dp(1), th.hint)
        box.background = bg

        val items = ArrayList<TextView>()
        for (a in alts) {
            val t = TextView(this)
            t.text = a
            t.gravity = Gravity.CENTER
            t.textSize = 20f
            t.setTextColor(th.text)
            box.addView(t, LinearLayout.LayoutParams(itemW, itemH))
            items.add(t)
        }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenW = resources.displayMetrics.widthPixels
        val totalW = itemW * alts.size + dp(8)
        var left = loc[0] + anchor.width / 2 - totalW / 2
        left = left.coerceIn(0, maxOf(0, screenW - totalW))
        val top = loc[1] - itemH - dp(16)

        val pw = PopupWindow(box, totalW, itemH + dp(8), false)
        pw.isTouchable = false
        pw.isClippingEnabled = false
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, left, top)

        popup = pw
        popItems = items
        popAlts = alts
        popLeft = left + dp(4)
        popItemW = itemW
        popSel = -1
        updatePopup((loc[0] + anchor.width / 2).toFloat())
    }

    private fun updatePopup(rawX: Float) {
        if (popItems.isEmpty()) return
        val idx = ((rawX - popLeft) / popItemW).toInt().coerceIn(0, popItems.size - 1)
        if (idx == popSel) return
        popSel = idx
        for ((i, t) in popItems.withIndex()) {
            if (i == idx) {
                t.background = shape(accent)
                t.setTextColor(Color.WHITE)
            } else {
                t.background = null
                t.setTextColor(th.text)
            }
        }
    }

    /** Closes the popup and returns the highlighted character (or null). */
    private fun dismissPopup(): String? {
        val result = if (popSel in popAlts.indices) popAlts[popSel] else null
        popup?.dismiss()
        popup = null
        popItems = emptyList()
        popAlts = emptyList()
        popSel = -1
        return result
    }

    private fun commitAlt(s: String) {
        typeText(s)
        afterChar()
    }

    // ---------------------------------------------------------------- typing

    private fun typeText(s: String) {
        val ic = currentInputConnection ?: return
        if (curLayout().latin && s.length == 1 && s[0].isLetterOrDigit()) {
            composing.append(s)
            ic.setComposingText(composing.toString(), 1)
        } else {
            commitComposing()
            ic.commitText(s, 1)
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
        autoCap()
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
        autoCap()
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
        if (composing.isEmpty()) autoCap()
    }

    private fun pick(e: Entry) {
        val ic = currentInputConnection ?: return
        ic.commitText(e.word + " ", 1)
        composing.clear()
        updateSuggestions()
        autoCap()
    }

    /** Turns shift on at the start of a sentence (unless the field is a password / e-mail / URL). */
    private fun autoCap() {
        if (!::cfg.isInitialized || !::keysContainer.isInitialized) return
        if (!cfg.autoCap || !curLayout().cased || shift == 2 || symbols) return
        var want = false
        val ei = currentInputEditorInfo
        if (ei != null && (ei.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            val v = ei.inputType and InputType.TYPE_MASK_VARIATION
            val skip = v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                v == InputType.TYPE_TEXT_VARIATION_URI
            if (!skip) {
                val before = currentInputConnection?.getTextBeforeCursor(4, 0)?.toString() ?: ""
                val trimmed = before.trimEnd(' ')
                want = before.isBlank() || before.endsWith("\n") ||
                    (before.endsWith(" ") &&
                        (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")))
            }
        }
        setShift(if (want) 1 else 0)
    }

    // ----------------------------------------------------------- suggestions

    private fun showChips(items: List<Pair<String, Entry?>>) {
        val key = items.joinToString("\u0001") { it.first }
        if (key == chipKey) return
        chipKey = key
        strip.removeAllViews()
        for ((t, e) in items) addChip(t, e)
        stripScroll.scrollTo(0, 0)
    }

    private fun updateSuggestions() {
        if (!::strip.isInitialized) return
        if (!curLayout().latin || !cfg.suggestions) {
            suggestSeq++
            showChips(emptyList())
            return
        }
        if (composing.isEmpty()) {
            suggestSeq++
            showChips(listOf<Pair<String, Entry?>>(Pair("Type a word or its English meaning…", null)))
            return
        }
        if (!Dictionary.ready) {
            suggestSeq++
            showChips(listOf<Pair<String, Entry?>>(Pair("Loading dictionary…", null)))
            return
        }
        val s = composing.toString()
        val seq = ++suggestSeq
        try {
            worker.execute {
                try {
                    if (seq != suggestSeq) return@execute
                    val byWord = Dictionary.byWord(s, 8)
                    val byMeaning =
                        if (s.none { it.isDigit() }) Dictionary.byMeaning(s, 6) else emptyList()
                    val all = byWord + byMeaning.filter { m -> byWord.none { it.word == m.word } }
                    if (seq != suggestSeq) return@execute
                    val items: List<Pair<String, Entry?>> =
                        if (all.isEmpty()) {
                            listOf<Pair<String, Entry?>>(Pair("No match", null))
                        } else {
                            all.map { Pair<String, Entry?>(it.word + "  ·  " + it.meaning, it) }
                        }
                    handler.post { if (seq == suggestSeq) showChips(items) }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun addChip(text: String, entry: Entry?) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        if (entry != null) {
            tv.setTextColor(th.chip)
            tv.setOnClickListener {
                buzz()
                pick(entry)
            }
        } else {
            tv.setTextColor(th.hint)
        }
        strip.addView(tv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }
}
