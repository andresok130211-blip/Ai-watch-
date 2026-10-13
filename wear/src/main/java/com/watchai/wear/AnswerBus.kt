package com.watchai.wear

/** Мост между службой, получающей ответ, и открытым экраном. */
object AnswerBus {
    @Volatile
    var onAnswer: ((String) -> Unit)? = null
}
