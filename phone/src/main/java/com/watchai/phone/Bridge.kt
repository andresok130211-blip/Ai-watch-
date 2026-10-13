package com.watchai.phone

import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.Wearable

/** Общая логика: получить вопрос с часов, спросить ИИ, отправить ответ обратно на часы. */
object Bridge {
    const val PATH_ASK = "/ask"
    const val PATH_ANSWER = "/answer"

    /** Вызывается при входящем вопросе. Пытается запустить службу переднего плана, иначе работает в потоке. */
    fun handle(ctx: Context, nodeId: String, question: String) {
        val intent = Intent(ctx, BridgeService::class.java)
            .putExtra(BridgeService.EXTRA_NODE, nodeId)
            .putExtra(BridgeService.EXTRA_Q, question)
        try {
            ctx.startForegroundService(intent)
        } catch (t: Throwable) {
            Thread { process(ctx, nodeId, question) }.start()
        }
    }

    /** Блокирующий вызов: нужно запускать не в главном потоке. */
    fun process(ctx: Context, nodeId: String, question: String) {
        val s = Settings(ctx)
        LlmClient.appContext = ctx.applicationContext
        val answer = try {
            LlmClient.ask(s, question).ifBlank { "(пустой ответ)" }
        } catch (e: Exception) {
            "Ошибка ИИ: ${e.message ?: e.javaClass.simpleName}"
        }
        val out = answer.take(3000)
        Wearable.getMessageClient(ctx.applicationContext)
            .sendMessage(nodeId, PATH_ANSWER, out.toByteArray(Charsets.UTF_8))
        s.log = "Вопрос: $question\n\nОтвет: $out"
    }
}
