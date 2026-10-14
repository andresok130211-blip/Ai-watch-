package com.watchai.phone

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Клиент OpenAI-совместимого эндпоинта /chat/completions. */
object LlmClient {

    @Volatile var appContext: android.content.Context? = null

    /** Реплика прошлой переписки: role = "user" или "assistant". */
    data class Turn(val role: String, val text: String)

    /** Берём самые свежие реплики, пока они помещаются в лимит символов (контекст модели ограничен). */
    private fun trimHistory(history: List<Turn>, maxChars: Int = 3500): List<Turn> {
        val out = ArrayDeque<Turn>()
        var total = 0
        for (t in history.asReversed()) {
            if (total + t.text.length > maxChars) break
            out.addFirst(t)
            total += t.text.length
        }
        // Диалог должен начинаться с реплики пользователя
        while (out.isNotEmpty() && out.first().role != "user") out.removeFirst()
        return out.toList()
    }

    fun ask(s: Settings, question: String, history: List<Turn> = emptyList()): String {
        if (s.builtin) {
            // Первый запрос запускает сервер и грузит модель; ошибка вернётся текстом.
            LocalServer.ensureRunning(appContext ?: error("нет контекста"))?.let { throw IOException(it) }
        }
        val base = s.effectiveUrl.trim().trimEnd('/')
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messages = JSONArray()
        if (s.system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", s.system))
        }
        for (t in trimHistory(history)) {
            messages.put(JSONObject().put("role", t.role).put("content", t.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val body = JSONObject()
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 400)
        if (s.builtin) {
            // Qwen3: без режима рассуждений, иначе на часах будет долгая пауза.
            body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            body.put("temperature", 0.7)
        } else if (s.model.isNotBlank()) {
            body.put("model", s.model.trim())
        }

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (s.apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer ${s.apiKey.trim()}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code ${text.take(200)}")
            }

            val content = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Некоторые модели выводят рассуждения в <think>…</think>: на часах они не нужны.
            return content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        } finally {
            conn.disconnect()
        }
    }
}
