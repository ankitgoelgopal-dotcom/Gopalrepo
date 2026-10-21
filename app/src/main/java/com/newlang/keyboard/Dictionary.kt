package com.newlang.keyboard

import android.content.Context
import java.io.File

class Entry(val word: String, val meaning: String)

/**
 * Loads assets/dictionary.tsv plus the user's custom words (filesDir/custom_words.tsv).
 * Custom words come first, so they get the highest suggestion priority.
 */
object Dictionary {
    private class Snap(
        val words: Array<Entry>,
        val lowWords: Array<String>,
        val lowMeanings: Array<String>,
        val sorted: IntArray,
        val sortedM: IntArray,
        val stamp: Long
    )

    @Volatile
    private var snap: Snap? = null

    val ready: Boolean get() = snap != null
    val size: Int get() = snap?.words?.size ?: 0

    private fun customFile(ctx: Context) = File(ctx.filesDir, "custom_words.tsv")

    fun parseLine(line: String): Pair<String, String>? {
        for (sep in charArrayOf('\t', '=', '|')) {
            val i = line.indexOf(sep)
            if (i > 0) {
                val w = line.substring(0, i).trim()
                val m = line.substring(i + 1).trim()
                if (w.isNotEmpty() && m.isNotEmpty()) return Pair(w, m)
            }
        }
        return null
    }

    fun readCustom(ctx: Context): List<Pair<String, String>> {
        val f = customFile(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines(Charsets.UTF_8).mapNotNull { parseLine(it) }
    }

    fun writeCustom(ctx: Context, list: List<Pair<String, String>>) {
        val f = customFile(ctx)
        if (list.isEmpty()) {
            f.delete()
        } else {
            f.writeText(list.joinToString("\n") { it.first + "\t" + it.second }, Charsets.UTF_8)
        }
    }

    fun load(ctx: Context) {
        val stamp = customFile(ctx).lastModified()
        val cur0 = snap
        if (cur0 != null && cur0.stamp == stamp) return
        synchronized(this) {
            val cur = snap
            if (cur != null && cur.stamp == stamp) return
            val list = ArrayList<Entry>(90000)
            for ((w, m) in readCustom(ctx)) list.add(Entry(w, m))
            ctx.assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (l in lines) {
                    val t = l.indexOf('\t')
                    if (t > 0) list.add(Entry(l.substring(0, t), l.substring(t + 1)))
                }
            }
            val words = list.toTypedArray()
            val lw = Array(words.size) { words[it].word.lowercase() }
            val lm = Array(words.size) { words[it].meaning.lowercase() }
            val sorted = (0 until words.size).sortedBy { lw[it] }.toIntArray()
            val sortedM = (0 until words.size).sortedBy { lm[it] }.toIntArray()
            snap = Snap(words, lw, lm, sorted, sortedM, stamp)
        }
    }

    /** Words that start with [prefix]; exact match first, then by priority. */
    fun byWord(prefix: String, limit: Int): List<Entry> {
        val s = snap ?: return emptyList()
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        var lo = 0
        var hi = s.sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (s.lowWords[s.sorted[mid]] < p) lo = mid + 1 else hi = mid
        }
        val best = ArrayList<Int>()
        var exact = -1
        var i = lo
        while (i < s.sorted.size && s.lowWords[s.sorted[i]].startsWith(p)) {
            val r = s.sorted[i]
            if (s.lowWords[r] == p && exact < 0) exact = r
            if (best.size < limit || r < best[best.size - 1]) {
                val pos = best.binarySearch(r)
                best.add(-pos - 1, r)
                if (best.size > limit) best.removeAt(best.size - 1)
            }
            i++
        }
        val out = ArrayList<Entry>()
        if (exact >= 0) out.add(s.words[exact])
        for (r in best) if (r != exact) out.add(s.words[r])
        return out
    }

    /** English meaning lookup (binary search on a sorted index, so it stays fast). */
    fun byMeaning(query: String, limit: Int): List<Entry> {
        val s = snap ?: return emptyList()
        if (query.isEmpty()) return emptyList()
        val q = query.lowercase()
        var lo = 0
        var hi = s.sortedM.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (s.lowMeanings[s.sortedM[mid]] < q) lo = mid + 1 else hi = mid
        }
        val exact = ArrayList<Int>()
        val best = ArrayList<Int>()
        var i = lo
        while (i < s.sortedM.size) {
            val r = s.sortedM[i]
            val m = s.lowMeanings[r]
            if (!m.startsWith(q)) break
            if (m == q) {
                if (exact.size < limit) exact.add(r)
            } else if (best.size < limit || r < best[best.size - 1]) {
                val pos = best.binarySearch(r)
                best.add(-pos - 1, r)
                if (best.size > limit) best.removeAt(best.size - 1)
            }
            i++
        }
        return (exact + best).take(limit).map { s.words[it] }
    }
}
