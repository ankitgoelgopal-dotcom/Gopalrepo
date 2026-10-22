package com.newlang.keyboard

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Google Gemini (generateContent) client. The key is sent in a header, never in the URL. */
object Gemini {
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    private const val ROOT = "https://generativelanguage.googleapis.com/"
    private const val BASE = ROOT + "v1beta/"

    class Result(val text: String?, val error: String?, val model: String, val api: String = "")

    private class Resp(val code: Int, val body: String)

    private fun http(url: String, key: String, body: String?): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 40000
            c.setRequestProperty("x-goog-api-key", key)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            } else {
                c.requestMethod = "GET"
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun generate(key: String, model: String, system: String, prompt: String, thinking: Boolean): Resp {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
        val gc = JSONObject().put("temperature", 0.2)
        if (thinking) gc.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
        body.put("generationConfig", gc)
        return http(BASE + "models/" + model + ":generateContent", key, body.toString())
    }

    // ------------------------------------------------ Interactions API (new format)

    private fun interactionsOnce(
        path: String, key: String, model: String, system: String, prompt: String, variant: Int
    ): Resp {
        val body = JSONObject()
            .put("model", model)
            .put("input", prompt)
            .put("system_instruction", system)
        if (variant <= 1) body.put("store", false)   // do not keep the user's text on Google's side
        if (variant == 0) {
            val gc = JSONObject().put("temperature", 0.2)
            if (model.startsWith("gemini-3")) gc.put("thinking_level", "low")
            body.put("generation_config", gc)
        }
        return http(ROOT + path + "/interactions", key, body.toString())
    }

    /** Tries the newest endpoint first; drops optional fields if the server rejects them (HTTP 400). */
    private fun interactions(key: String, model: String, system: String, prompt: String): Resp {
        var r = Resp(0, "")
        for (path in listOf("v1beta2", "v1beta")) {
            for (variant in 0..2) {
                r = interactionsOnce(path, key, model, system, prompt, variant)
                if (r.code != 400) break
            }
            if (r.code != 404) return r
        }
        return r
    }

    private fun extractInteraction(body: String): String? {
        return try {
            val root = JSONObject(body)
            val sb = StringBuilder()
            val steps = root.optJSONArray("steps")
            if (steps != null) {
                // the last model_output step holds the final answer
                for (i in steps.length() - 1 downTo 0) {
                    val st = steps.getJSONObject(i)
                    if (st.optString("type") != "model_output") continue
                    val content = st.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val c = content.getJSONObject(j)
                        if (c.optString("type", "text") == "text") sb.append(c.optString("text", ""))
                    }
                    break
                }
            }
            if (sb.isBlank()) {
                // older revision: "outputs" list
                val outs = root.optJSONArray("outputs")
                if (outs != null && outs.length() > 0) {
                    sb.append(outs.getJSONObject(outs.length() - 1).optString("text", ""))
                }
            }
            val t = sb.toString()
            if (t.isBlank()) null else t
        } catch (e: Exception) {
            null
        }
    }

    private fun extract(body: String): String? {
        return try {
            val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
            val t = sb.toString()
            if (t.isBlank()) null else t
        } catch (e: Exception) {
            null
        }
    }

    private fun errorText(r: Resp): String {
        val msg = try {
            JSONObject(r.body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            r.body
        }
        return "Error " + r.code + ": " + msg.replace('\n', ' ').take(140)
    }

    /** When the configured model does not exist, pick the newest stable "flash" model of this key. */
    private fun pickModel(key: String): String? {
        return try {
            val r = http(BASE + "models?pageSize=200", key, null)
            if (r.code !in 200..299) return null
            val arr = JSONObject(r.body).getJSONArray("models")
            val stable = Regex("^gemini-(\\d+(?:\\.\\d+)?)-flash$")
            var best: String? = null
            var bestV = -1.0
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.getString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) {
                    for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                }
                if (!ok) continue
                val mt = stable.find(name) ?: continue
                val v = mt.groupValues[1].toDoubleOrNull() ?: continue
                if (v > bestV) {
                    bestV = v
                    best = name
                }
            }
            best
        } catch (e: Exception) {
            null
        }
    }

    fun run(key: String, model0: String, system: String, prompt: String): Result {
        var model = model0.ifBlank { DEFAULT_MODEL }
        try {
            var api = "Interactions API"
            var r = interactions(key, model, system, prompt)
            if (r.code == 404) {
                // maybe the model does not exist for this key: pick the newest Flash model
                val alt = pickModel(key)
                if (alt != null && alt != model) {
                    model = alt
                    r = interactions(key, model, system, prompt)
                }
            }
            if (r.code == 404) {
                // last resort: the classic generateContent format
                api = "generateContent (fallback)"
                r = generate(key, model, system, prompt, model.startsWith("gemini-3"))
                if (r.code == 400 && model.startsWith("gemini-3")) {
                    r = generate(key, model, system, prompt, false)
                }
            }
            if (r.code in 200..299) {
                val t = if (api.startsWith("Interactions")) extractInteraction(r.body) else extract(r.body)
                return if (t != null) Result(t, null, model, api)
                else Result(null, "AI returned no text (empty or blocked)", model, api)
            }
            return Result(null, errorText(r), model, api)
        } catch (e: Exception) {
            return Result(null, "Network error: " + (e.message ?: e.javaClass.simpleName), model)
        }
    }

    // ---------------------------------------------------------------- prompts

    fun systemPrompt(lang: String, notes: String): String =
        "You are the writing assistant inside a phone keyboard for the language \"" + lang + "\". " +
            "Reply with ONLY the resulting text: no quotes, no explanations, no markdown, no labels. " +
            "Never add or remove information. Keep line breaks. " +
            (if (notes.isNotBlank()) "About the language: " + notes.trim() + " " else "") +
            "You may be given a list taken from the user's own dictionary (word = English meaning). " +
            "Trust it over your own guesses and use the same spelling and script as the dictionary."

    private fun hintsBlock(hints: String): String =
        if (hints.isBlank()) "" else "DICTIONARY HINTS (word = meaning):\n" + hints.trim() + "\n\n"

    fun fixPrompt(lang: String, hints: String, text: String): String =
        "Fix the spelling, grammar and punctuation of this " + lang + " text. " +
            "Keep the same meaning, language and script. If you are unsure about a word, leave it unchanged.\n\n" +
            hintsBlock(hints) + "TEXT:\n" + text

    fun translatePrompt(lang: String, hints: String, text: String): String =
        "If the text below is mostly English, translate it into " + lang +
            " (use the dictionary spelling and script). Otherwise translate it into English. " +
            "If you cannot translate a word, keep it as it is.\n\n" +
            hintsBlock(hints) + "TEXT:\n" + text
}
