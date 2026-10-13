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

/** Получает ответ ИИ с телефона: показывает на экране, если приложение открыто, иначе уведомлением. */
class AnswerListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/answer") return
        val text = String(event.data, Charsets.UTF_8)

        getSharedPreferences("watchai", MODE_PRIVATE).edit().putString("last", text).apply()

        val callback = AnswerBus.onAnswer
        if (callback != null) {
            callback(text)
            vibrate()
        } else {
            notifyAnswer(text)
        }
    }

    private fun vibrate() {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun notifyAnswer(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("answers", "Ответы ИИ", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
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
        nm.notify(1, notification)
    }
}
