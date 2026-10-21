package com.newlang.keyboard

import android.content.SharedPreferences

class KbLayout(
    val name: String,
    val short: String,
    val latin: Boolean,          // true = dictionary suggestions + composing text
    val cased: Boolean,          // true = auto-capitalisation makes sense
    val normal: List<List<String>>,
    val shifted: List<List<String>>
)

object Layouts {
    private fun rows(vararg s: String) = s.map { r -> r.map { it.toString() } }
    private fun upper(r: List<List<String>>) = r.map { row -> row.map { it.uppercase() } }

    private fun latin(name: String, short: String, vararg s: String): KbLayout {
        val n = rows(*s)
        return KbLayout(name, short, true, true, n, upper(n))
    }

    val builtins: List<KbLayout> = listOf(
        latin("English / your language (QWERTY)", "EN", "qwertyuiop", "asdfghjkl", "zxcvbnm"),
        latin("AZERTY", "FR", "azertyuiop", "qsdfghjklm", "wxcvbn"),
        latin("QWERTZ", "DE", "qwertzuiop", "asdfghjkl", "yxcvbnm"),
        KbLayout(
            "Hindi (Devanagari)", "\u0939\u093F", false, false,
            rows("\u094C\u0948\u093E\u0940\u0942\u092C\u0939\u0917\u0926\u091C\u0921", "\u094B\u0947\u094D\u093F\u0941\u092A\u0930\u0915\u0924\u091A\u091F", "\u0902\u092E\u0928\u0935\u0932\u0938\u092F"),
            rows("\u0914\u0910\u0906\u0908\u090A\u092D\u0919\u0918\u0927\u091D\u0922", "\u0913\u090F\u0905\u0907\u0909\u092B\u0931\u0916\u0925\u091B\u0920", "\u0901\u0923\u0929\u0934\u0933\u0936\u0937")
        ),
        run {
            val n = rows("\u0439\u0446\u0443\u043A\u0435\u043D\u0433\u0448\u0449\u0437\u0445", "\u0444\u044B\u0432\u0430\u043F\u0440\u043E\u043B\u0434\u0436\u044D", "\u044F\u0447\u0441\u043C\u0438\u0442\u044C\u0431\u044E")
            KbLayout("Russian (Cyrillic)", "RU", false, true, n, upper(n))
        },
        latin("Spanish (QWERTY + \u00F1)", "ES", "qwertyuiop", "asdfghjkl\u00F1", "zxcvbnm"),
        run {
            val n = rows("\u03C2\u03B5\u03C1\u03C4\u03C5\u03B8\u03B9\u03BF\u03C0", "\u03B1\u03C3\u03B4\u03C6\u03B3\u03B7\u03BE\u03BA\u03BB", "\u03B6\u03C7\u03C8\u03C9\u03B2\u03BD\u03BC")
            KbLayout("Greek", "\u0395\u039B", false, true, n, upper(n))
        },
        run {
            val n = rows("\u0636\u0635\u062B\u0642\u0641\u063A\u0639\u0647\u062E\u062D\u062C\u062F", "\u0634\u0633\u064A\u0628\u0644\u0627\u062A\u0646\u0645\u0643\u0637", "\u0626\u0621\u0624\u0631\u0649\u0629\u0648\u0632\u0638")
            KbLayout("Arabic", "\u0639", false, false, n, n)
        }
    )

    // ---- user-defined layout (edited in the app's settings screen) ----
    const val DEFAULT_CUSTOM = "qwertyuiop\nasdfghjkl\nzxcvbnm"

    @Volatile
    var custom: KbLayout = buildCustom(DEFAULT_CUSTOM, "", true)
        private set

    /** Builtin layouts followed by the custom one (custom is always the last index). */
    var all: List<KbLayout> = builtins + custom
        private set

    private fun tokens(line: String): List<String> {
        val t = line.trim()
        if (t.isEmpty()) return emptyList()
        if (t.any { it.isWhitespace() }) return t.split(Regex("\\s+"))
        val out = ArrayList<String>()
        var i = 0
        while (i < t.length) {
            val n = Character.charCount(t.codePointAt(i))
            out.add(t.substring(i, i + n))
            i += n
        }
        return out
    }

    private fun parse(text: String): List<List<String>> =
        text.lines().map { tokens(it) }.filter { it.isNotEmpty() }.take(3)

    private fun pad3(r: List<List<String>>): List<List<String>> {
        val out = ArrayList<List<String>>(r)
        while (out.size < 3) out.add(emptyList())
        return out
    }

    private fun buildCustom(normalText: String, shiftedText: String, latin: Boolean): KbLayout {
        var n = parse(normalText)
        if (n.isEmpty()) n = parse(DEFAULT_CUSTOM)
        val s0 = parse(shiftedText)
        val s = if (s0.size >= n.size) s0 else upper(n)
        return KbLayout("Custom (your own keys)", "CU", latin, latin, pad3(n), pad3(s))
    }

    fun loadCustom(p: SharedPreferences) {
        custom = buildCustom(
            p.getString("customRows", DEFAULT_CUSTOM) ?: DEFAULT_CUSTOM,
            p.getString("customShifted", "") ?: "",
            p.getBoolean("customLatin", true)
        )
        all = builtins + custom
    }
}

/** Long-press popup characters. */
object Alts {
    private val map: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "æ", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "d" to listOf("ð"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ę", "ė"),
        "g" to listOf("ğ"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ", "ō"),
        "s" to listOf("ß", "ś", "š", "ş"),
        "t" to listOf("þ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "1" to listOf("¹", "१"), "2" to listOf("²", "२"), "3" to listOf("³", "३"),
        "4" to listOf("⁴", "४"), "5" to listOf("⁵", "५"), "6" to listOf("⁶", "६"),
        "7" to listOf("⁷", "७"), "8" to listOf("⁸", "८"), "9" to listOf("⁹", "९"),
        "0" to listOf("⁰", "०", "°"),
        "." to listOf("…", "।", "॥", ";", "!", "?"),
        "," to listOf(";", "'", "\""),
        "-" to listOf("–", "—", "•"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "\$" to listOf("₹", "€", "£", "¥", "¢"),
        "'" to listOf("‘", "’"),
        "\"" to listOf("“", "”"),
        "=" to listOf("≠", "≈"),
        "+" to listOf("±"),
        // Hindi
        "ज" to listOf("ज्ञ", "ज़"), "क" to listOf("क्ष", "क़"), "त" to listOf("त्र"),
        "श" to listOf("श्र"), "ं" to listOf("ः"), "र" to listOf("ऋ", "ॠ", "ृ"),
        "ड" to listOf("ड़", "ढ़"), "फ" to listOf("फ़"), "ख" to listOf("ख़"),
        "ग" to listOf("ग़"), "झ" to listOf("ञ"), "ओ" to listOf("ॐ"), "ा" to listOf("ॉ"),
        "म" to listOf("़"),
        // Arabic
        "\u0627" to listOf("\u0623", "\u0625", "\u0622", "\u0621"), "\u062F" to listOf("\u0630"),
        // Russian
        "е" to listOf("ё"), "ь" to listOf("ъ"), "г" to listOf("ґ")
    )

    fun get(k: String): List<String> {
        map[k]?.let { return it }
        if (k.length == 1) {
            val lo = k.lowercase()
            if (lo != k) map[lo]?.let { l -> return l.map { it.uppercase() } }
        }
        return emptyList()
    }
}
