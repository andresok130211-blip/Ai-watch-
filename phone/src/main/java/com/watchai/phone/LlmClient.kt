package com.watchai.phone

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Клиент OpenAI-совместимого эндпоинта /chat/completions. */
object LlmClient {

    fun ask(s: Settings, question: String): String {
        val base = s.url.trim().trimEnd('/')
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messages = JSONArray()
        if (s.system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", s.system))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val body = JSONObject()
            .put("messages", messages)
            .put("stream", false)
            .put("max_tokens", 400)
        if (s.model.isNotBlank()) body.put("model", s.model.trim())

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
