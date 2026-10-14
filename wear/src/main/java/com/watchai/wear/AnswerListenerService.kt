package com.watchai.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Получает ответ ИИ с телефона и сразу записывает его в нужный чат.
 * Если приложение открыто, обновляет экран, иначе показывает уведомление.
 */
class AnswerListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/answer") return
        val raw = String(event.data, Charsets.UTF_8)

        var chat = ""
        var text = raw
        try {
            val o = JSONObject(raw)
            chat = o.optString("chat")
            text = o.getString("a")
        } catch (_: Exception) {
            // Не JSON: показываем как есть
        }
        if (chat.isEmpty()) chat = "legacy"

        ChatStore.add(this, chat, "a", text)

        val callback = AnswerBus.onAnswer
        if (callback != null) {
            callback(chat, text)
            vibrate()
        } else {
            notifyAnswer(chat, text)
        }
    }

    private fun vibrate() {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun notifyAnswer(chat: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("answers", "Ответы ИИ", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            this,
            chat.hashCode(),
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CHAT, chat)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, "answers")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ответ ИИ")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(chat.hashCode(), notification)
    }
}
