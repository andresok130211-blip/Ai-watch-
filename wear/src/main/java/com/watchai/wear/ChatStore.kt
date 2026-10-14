package com.watchai.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Один пузырь переписки: role = "u" (вы) или "a" (ИИ). */
data class Msg(val role: String, val text: String)

data class Chat(val id: String, var title: String, val msgs: MutableList<Msg>, var updated: Long)

/** Чаты хранятся на самих часах (SharedPreferences, JSON). */
object ChatStore {
    private const val PREFS = "watchai"
    private const val KEY = "chats"
    private const val MAX_CHATS = 20
    private const val MAX_MSGS = 40

    @Synchronized
    fun list(ctx: Context): List<Chat> = load(ctx).sortedByDescending { it.updated }

    @Synchronized
    fun get(ctx: Context, id: String): Chat? = load(ctx).firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** Добавляет реплику; чат создаётся при первой реплике. */
    @Synchronized
    fun add(ctx: Context, chatId: String, role: String, text: String) {
        val chats = load(ctx)
        var chat = chats.firstOrNull { it.id == chatId }
        if (chat == null) {
            chat = Chat(chatId, text.take(40), mutableListOf(), System.currentTimeMillis())
            chats.add(chat)
        }
        chat.msgs.add(Msg(role, text))
        while (chat.msgs.size > MAX_MSGS) chat.msgs.removeAt(0)
        chat.updated = System.currentTimeMillis()
        // Лишние старые чаты удаляем
        val kept = chats.sortedByDescending { it.updated }.take(MAX_CHATS)
        save(ctx, kept)
    }

    @Synchronized
    fun delete(ctx: Context, chatId: String) {
        save(ctx, load(ctx).filter { it.id != chatId })
    }

    private fun load(ctx: Context): MutableList<Chat> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        val out = mutableListOf<Chat>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgs = mutableListOf<Msg>()
                val ma = o.getJSONArray("m")
                for (j in 0 until ma.length()) {
                    val m = ma.getJSONObject(j)
                    msgs.add(Msg(m.getString("r"), m.getString("t")))
                }
                out.add(Chat(o.getString("id"), o.getString("title"), msgs, o.getLong("u")))
            }
        } catch (_: Exception) {
            // Повреждённые данные: начинаем с пустого списка
        }
        return out
    }

    private fun save(ctx: Context, chats: List<Chat>) {
        val arr = JSONArray()
        for (c in chats) {
            val ma = JSONArray()
            for (m in c.msgs) ma.put(JSONObject().put("r", m.role).put("t", m.text))
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("u", c.updated).put("m", ma))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
