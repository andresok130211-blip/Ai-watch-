package com.watchai.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Служба переднего плана: не даёт системе убить приложение, пока ИИ думает.
 * Работает в двух режимах: на время одного запроса или постоянно (если включён мост в приложении).
 */
class BridgeService : Service() {

    companion object {
        const val EXTRA_NODE = "node"
        const val EXTRA_Q = "q"
        const val ACTION_STOP = "com.watchai.phone.STOP"
        private const val CHANNEL = "bridge"
        private const val NOTIF_ID = 1
    }

    private val pending = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Освобождаем память: встроенная модель выгружается вместе со службой.
        LocalServer.stop()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
        } catch (_: Throwable) {
            // Не страшно: запрос всё равно обработаем ниже.
        }

        val question = intent?.getStringExtra(EXTRA_Q)
        val node = intent?.getStringExtra(EXTRA_NODE)
        if (question != null && node != null) {
            pending.incrementAndGet()
            Thread {
                try {
                    Bridge.process(applicationContext, node, question)
                } finally {
                    if (pending.decrementAndGet() == 0 && !Settings(applicationContext).bridgeOn) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }.start()
        }

        return if (Settings(this).bridgeOn) START_STICKY else START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Мост часы → ИИ", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Часы → ИИ")
            .setContentText("Жду вопросы с часов")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }
}
