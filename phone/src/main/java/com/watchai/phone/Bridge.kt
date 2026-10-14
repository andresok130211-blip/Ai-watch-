package com.watchai.phone

import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Общая логика: получить вопрос с часов, спросить ИИ, отправить ответ обратно на часы.
 *
 * Формат сообщений (JSON, UTF-8):
 *   часы -> телефон  /ask:    {"chat":"<id чата>","q":"вопрос","h":[{"r":"u"|"a","t":"текст"}, ...]}
 *   телефон -> часы  /answer: {"chat":"<id чата>","a":"ответ"}
 * "h" - предыдущие реплики этого чата, чтобы ИИ помнил разговор.
 */
object Bridge {
    const val PATH_ASK = "/ask"
    const val PATH_ANSWER = "/answer"

    /** Вызывается при входящем вопросе. Пытается запустить службу переднего плана, иначе работает в потоке. */
    fun handle(ctx: Context, nodeId: String, payload: String) {
        val intent = Intent(ctx, BridgeService::class.java)
            .putExtra(BridgeService.EXTRA_NODE, nodeId)
            .putExtra(BridgeService.EXTRA_Q, payload)
        try {
            ctx.startForegroundService(intent)
        } catch (t: Throwable) {
            Thread { process(ctx, nodeId, payload) }.start()
        }
    }

    /** Блокирующий вызов: нужно запускать не в главном потоке. */
    fun process(ctx: Context, nodeId: String, payload: String) {
        val s = Settings(ctx)
        LlmClient.appContext = ctx.applicationContext

        var chat = ""
        var question = payload
        val history = ArrayList<LlmClient.Turn>()
        try {
            val req = JSONObject(payload)
            chat = req.optString("chat")
            question = req.getString("q")
            val h: JSONArray? = req.optJSONArray("h")
            if (h != null) {
                for (i in 0 until h.length()) {
                    val o = h.getJSONObject(i)
                    val role = if (o.optString("r") == "a") "assistant" else "user"
                    history.add(LlmClient.Turn(role, o.optString("t")))
                }
            }
        } catch (_: Exception) {
            // Не JSON: считаем, что пришёл просто текст вопроса
        }

        val answer = try {
            LlmClient.ask(s, question, history).ifBlank { "(пустой ответ)" }
        } catch (e: Exception) {
            "Ошибка ИИ: ${e.message ?: e.javaClass.simpleName}"
        }
        val out = answer.take(3000)
        val reply = JSONObject().put("chat", chat).put("a", out).toString()
        Wearable.getMessageClient(ctx.applicationContext)
            .sendMessage(nodeId, PATH_ANSWER, reply.toByteArray(Charsets.UTF_8))
        s.log = "Вопрос: $question\n\nОтвет: $out"
    }
}
