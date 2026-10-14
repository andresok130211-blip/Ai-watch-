package com.watchai.phone

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Система будит это приложение, когда с часов приходит сообщение /ask. */
class AskListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Bridge.PATH_ASK) return
        val payload = String(event.data, Charsets.UTF_8).trim()
        if (payload.isEmpty()) return
        Bridge.handle(applicationContext, event.sourceNodeId, payload)
    }
}
