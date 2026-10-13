package com.watchai.phone

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Система будит это приложение, когда с часов приходит сообщение /ask. */
class AskListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Bridge.PATH_ASK) return
        val question = String(event.data, Charsets.UTF_8).trim()
        if (question.isEmpty()) return
        Bridge.handle(applicationContext, event.sourceNodeId, question)
    }
}
