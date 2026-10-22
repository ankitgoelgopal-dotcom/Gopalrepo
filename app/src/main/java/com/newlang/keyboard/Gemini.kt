package com.newlang.keyboard

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Google Gemini client using the new Interactions API. The key is sent in a header, never in the URL. */
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

    /** One call to the Interactions API (POST /v1beta/interactions). store=false: Google keeps no copy. */
    private fun generate(key: String, model: String, system: String, prompt: String, thinking: Boolean): Resp {
        val gc = JSONObject()
        if (!model.startsWith("gemini-3")) gc.put("temperature", 0.2)
        if (thinking) gc.put("thinking_level", "low")
        val body = JSONObject()
            .put("model", model)
            .put("input", prompt)
            .put("system_instruction", system)
            .put("store", false)
        if (gc.length() > 0) body.put("generation_config", gc)
        return http(BASE + "interactions", key, body.toString())
    }

    /** Reads the answer from the new "steps" format: steps[type=model_output].content[type=text].text */
    private fun extract(body: String): String? {
        return try {
            val steps = JSONObject(body).optJSONArray("steps") ?: return null
            val sb = StringBuilder()
            for (i in 0 until steps.length()) {
                val st = steps.getJSONObject(i)
                if (st.optString("type") != "model_output") continue
                val content = st.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val c = content.getJSONObject(j)
                    if (c.optString("type") == "text") sb.append(c.optString("text", ""))
                }
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

    private fun attempt(key: String, model: String, system: String, prompt: String): Resp {
        val think = model.startsWith("gemini-3")
        var r = generate(key, model, system, prompt, think)
        if (r.code == 400 && think) r = generate(key, model, system, prompt, false)
        return r
    }

    fun run(key: String, model0: String, system: String, prompt: String): Result {
        var model = model0.ifBlank { DEFAULT_MODEL }
        try {
            var r = attempt(key, model, system, prompt)
            if (r.code == 404 || (r.code == 400 && r.body.lowercase().contains("model"))) {
                val alt = pickModel(key)
                if (alt != null && alt != model) {
                    model = alt
                    r = attempt(key, model, system, prompt)
                }
            }
            if (r.code in 200..299) {
                val t = extract(r.body)
                return if (t != null) Result(t, null, model)
                else Result(null, "AI returned no text (empty or blocked)", model)
            }
            return Result(null, errorText(r), model)
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
