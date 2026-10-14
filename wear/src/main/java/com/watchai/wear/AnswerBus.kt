package com.watchai.wear

/** Мост между службой, получающей ответ, и открытым экраном. */
object AnswerBus {
    /** Вызывается, когда пришёл ответ: (id чата, текст). */
    @Volatile
    var onAnswer: ((String, String) -> Unit)? = null
}
