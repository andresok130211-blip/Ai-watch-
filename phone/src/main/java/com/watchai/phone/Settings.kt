package com.watchai.phone

import android.content.Context

/** Настройки подключения к локальному ИИ (OpenAI-совместимый API: Ollama, LM Studio, llama.cpp server и т.д.). */
class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("watchai", Context.MODE_PRIVATE)

    /** Встроенный ИИ (llama-server внутри приложения) вместо внешнего адреса. */
    var builtin: Boolean
        get() = p.getBoolean("builtin", true)
        set(v) = p.edit().putBoolean("builtin", v).apply()

    var url: String
        get() = p.getString("url", "http://127.0.0.1:11434/v1") ?: ""
        set(v) = p.edit().putString("url", v).apply()

    /** Адрес, по которому реально идёт запрос. */
    val effectiveUrl: String
        get() = if (builtin) LocalServer.BASE else url

    var model: String
        get() = p.getString("model", "") ?: ""
        set(v) = p.edit().putString("model", v).apply()

    var apiKey: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v).apply()

    var system: String
        get() = p.getString(
            "system",
            "Ты ассистент на умных часах. Отвечай по-русски, коротко: 1–3 предложения, без markdown, списков и эмодзи."
        ) ?: ""
        set(v) = p.edit().putString("system", v).apply()

    /** Включён ли постоянный режим моста (фоновая служба). */
    var bridgeOn: Boolean
        get() = p.getBoolean("bridge_on", false)
        set(v) = p.edit().putBoolean("bridge_on", v).apply()

    /** Последний вопрос и ответ, показывается на главном экране. */
    var log: String
        get() = p.getString("log", "") ?: ""
        set(v) = p.edit().putString("log", v).apply()
}
