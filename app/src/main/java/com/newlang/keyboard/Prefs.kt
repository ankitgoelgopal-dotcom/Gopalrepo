package com.newlang.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Theme(
    val bg: Int, val key: Int, val special: Int, val strip: Int,
    val text: Int, val chip: Int, val hint: Int
)

object Prefs {
    const val FILE = "keyboard_prefs"

    val themeNames = listOf("Dark", "Light", "AMOLED black", "Ocean")
    val themes = listOf(
        Theme(c("#1E1F24"), c("#3A3C44"), c("#2A2C32"), c("#17181C"), Color.WHITE, c("#9FD3FF"), c("#80848F")),
        Theme(c("#D9DCE1"), c("#FFFFFF"), c("#B8BDC7"), c("#C9CDD4"), c("#111111"), c("#1A5FB4"), c("#666B75")),
        Theme(c("#000000"), c("#1C1C1E"), c("#0E0E10"), c("#000000"), Color.WHITE, c("#9FD3FF"), c("#80848F")),
        Theme(c("#0F1B2D"), c("#22344F"), c("#172840"), c("#0B1422"), Color.WHITE, c("#8FE0FF"), c("#7C8DA6"))
    )

    val accentNames = listOf("Purple", "Blue", "Green", "Orange", "Pink", "Red")
    val accents = intArrayOf(c("#5B4BDB"), c("#1E88E5"), c("#2E9E5B"), c("#F57C00"), c("#E91E8C"), c("#E53935"))

    val heightNames = listOf("Small", "Medium", "Large", "Extra large")
    val heightDp = intArrayOf(40, 46, 54, 62)

    val shapeNames = listOf("Rounded", "Square", "Pill")

    val vibNames = listOf("Off", "Light", "Medium", "Strong")
    val vibMs = longArrayOf(0L, 8L, 18L, 35L)

    val iconNames = listOf("Purple", "Blue", "Green", "Orange")

    private fun c(s: String) = Color.parseColor(s)

    fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun parseLayouts(s: String?): List<Int> {
        val out = (s ?: "0,3").split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until Layouts.all.size }.distinct()
        return if (out.isEmpty()) listOf(0) else out
    }
}

/** A snapshot of all settings; the keyboard re-creates it whenever a setting changes. */
class Config(ctx: Context) {
    private val p = Prefs.sp(ctx)
    init { Layouts.loadCustom(p) }
    val numberRow = p.getBoolean("numberRow", true)
    val layouts: List<Int> = Prefs.parseLayouts(p.getString("layouts", null))
    val theme = p.getInt("theme", 0).coerceIn(0, Prefs.themes.size - 1)
    val accent = p.getInt("accent", 0).coerceIn(0, Prefs.accents.size - 1)
    val keyHeight = Prefs.heightDp[p.getInt("height", 1).coerceIn(0, Prefs.heightDp.size - 1)]
    val shape = p.getInt("shape", 0).coerceIn(0, 2)
    val vibration = p.getInt("vibration", 1).coerceIn(0, Prefs.vibMs.size - 1)
    val longPress = p.getBoolean("longPress", true)
    val autoCap = p.getBoolean("autoCap", true)
    val suggestions = p.getBoolean("suggestions", true)
    val spaceLabel = p.getString("spaceLabel", "") ?: ""
}
