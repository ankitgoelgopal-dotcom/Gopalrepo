package com.newlang.keyboard

import android.content.Context

class Entry(val word: String, val meaning: String)

/** Loads assets/dictionary.tsv (word <TAB> meaning) and answers prefix queries. */
object Dictionary {
    @Volatile
    var ready = false
        private set

    private var words: Array<Entry> = emptyArray()      // in priority order
    private var lowWords: Array<String> = emptyArray()
    private var lowMeanings: Array<String> = emptyArray()
    private var sorted: IntArray = IntArray(0)          // indices sorted by word

    val size: Int get() = words.size

    fun load(ctx: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val list = ArrayList<Entry>(90000)
            ctx.assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (l in lines) {
                    val t = l.indexOf('\t')
                    if (t > 0) list.add(Entry(l.substring(0, t), l.substring(t + 1)))
                }
            }
            words = list.toTypedArray()
            lowWords = Array(words.size) { words[it].word.lowercase() }
            lowMeanings = Array(words.size) { words[it].meaning.lowercase() }
            sorted = (0 until words.size).sortedBy { lowWords[it] }.toIntArray()
            ready = true
        }
    }

    /** Words that start with [prefix]; exact match first, then by priority. */
    fun byWord(prefix: String, limit: Int): List<Entry> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lowWords[sorted[mid]] < p) lo = mid + 1 else hi = mid
        }
        val best = ArrayList<Int>()
        var exact = -1
        var i = lo
        while (i < sorted.size && lowWords[sorted[i]].startsWith(p)) {
            val r = sorted[i]
            if (lowWords[r] == p) exact = r
            if (best.size < limit || r < best[best.size - 1]) {
                val pos = best.binarySearch(r)
                best.add(-pos - 1, r)
                if (best.size > limit) best.removeAt(best.size - 1)
            }
            i++
        }
        val out = ArrayList<Entry>()
        if (exact >= 0) out.add(words[exact])
        for (r in best) if (r != exact) out.add(words[r])
        return out
    }

    /** English meaning lookup: type "food" and get the language word. */
    fun byMeaning(query: String, limit: Int): List<Entry> {
        if (!ready || query.isEmpty()) return emptyList()
        val q = query.lowercase()
        val exact = ArrayList<Entry>()
        val starts = ArrayList<Entry>()
        for (i in words.indices) {
            val m = lowMeanings[i]
            if (m == q) {
                if (exact.size < limit) exact.add(words[i])
            } else if (starts.size < limit && m.startsWith(q)) {
                starts.add(words[i])
            }
            if (exact.size >= limit && starts.size >= limit) break
        }
        return (exact + starts).take(limit)
    }
}
