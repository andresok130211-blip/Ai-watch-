package com.watchai.wear

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Два экрана в одной активности:
 *  - список чатов: «Новый чат» и старые чаты (нажми, чтобы продолжить; долгое нажатие удаляет);
 *  - сам чат: переписка, кнопки «Спросить» (голос) и «Текстом».
 */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_CHAT = "chat"
        private const val REQ_SPEECH = 100
        private const val PATH_ASK = "/ask"
        private const val STATE_CHAT = "chat"
        private const val HISTORY_MSGS = 10
    }

    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout

    /** Открытый чат, null = показан список. */
    private var chatId: String? = null
    private var status = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Круглый экран: отступы побольше, чтобы текст не резался по краям.
            setPadding(dp(22), dp(34), dp(22), dp(40))
        }
        scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        chatId = savedInstanceState?.getString(STATE_CHAT) ?: intent.getStringExtra(EXTRA_CHAT)
        render(scrollToEnd = chatId != null)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Нажали на уведомление с ответом: открываем нужный чат
        intent.getStringExtra(EXTRA_CHAT)?.let {
            chatId = it
            render(scrollToEnd = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        chatId?.let { outState.putString(STATE_CHAT, it) }
    }

    override fun onResume() {
        super.onResume()
        AnswerBus.onAnswer = { chat, _ ->
            runOnUiThread {
                if (chat == chatId) status = "" // ответ уже записан в чат, перерисовываем
                render(scrollToEnd = true)
            }
        }
        // Мог прийти ответ, пока экран был закрыт
        render(scrollToEnd = chatId != null)
    }

    override fun onPause() {
        AnswerBus.onAnswer = null
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (chatId != null) {
            chatId = null
            status = ""
            render(scrollToEnd = false)
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ экраны

    private fun render(scrollToEnd: Boolean) {
        content.removeAllViews()
        if (chatId == null) renderList() else renderChat(chatId!!)
        scroll.post { if (scrollToEnd) scroll.fullScroll(View.FOCUS_DOWN) else scroll.scrollTo(0, 0) }
    }

    private fun renderList() {
        content.addView(small("Часы → ИИ"))

        content.addView(Button(this).apply {
            text = "➕ Новый чат"
            setOnClickListener {
                chatId = ChatStore.newId()
                status = ""
                render(scrollToEnd = true)
                startVoice()
            }
        })

        val chats = ChatStore.list(this)
        if (chats.isEmpty()) {
            content.addView(small("Чатов пока нет"))
            return
        }
        content.addView(small("Продолжить:"))
        for (c in chats) {
            content.addView(Button(this).apply {
                text = "${c.title}  (${c.msgs.count { it.role == "u" }})"
                maxLines = 2
                setOnClickListener {
                    chatId = c.id
                    status = ""
                    render(scrollToEnd = true)
                }
                setOnLongClickListener {
                    confirmDelete(c)
                    true
                }
            })
        }
        content.addView(small("Долгое нажатие на чат: удалить"))
    }

    private fun renderChat(id: String) {
        val chat = ChatStore.get(this, id)

        content.addView(small(chat?.title ?: "Новый чат"))

        chat?.msgs?.forEach { m ->
            content.addView(TextView(this).apply {
                textSize = 15f
                setPadding(0, dp(6), 0, dp(6))
                if (m.role == "u") {
                    text = "Вы: ${m.text}"
                    setTextColor(Color.parseColor("#8AB4F8"))
                } else {
                    text = m.text
                    setTextColor(Color.WHITE)
                }
            })
        }

        content.addView(small(status))

        content.addView(Button(this).apply {
            text = "🎤 Спросить"
            setOnClickListener { startVoice() }
        })
        content.addView(Button(this).apply {
            text = "⌨ Текстом"
            setOnClickListener { askTyped() }
        })
        content.addView(Button(this).apply {
            text = "← Все чаты"
            setOnClickListener { onBackPressed() }
        })
    }

    private fun confirmDelete(c: Chat) {
        AlertDialog.Builder(this)
            .setTitle("Удалить чат?")
            .setMessage(c.title)
            .setPositiveButton("Удалить") { _, _ ->
                ChatStore.delete(this, c.id)
                render(scrollToEnd = false)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ------------------------------------------------------------------ ввод

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (e: ActivityNotFoundException) {
            status = "Нет голосового ввода, ввожу текстом"
            askTyped()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) send(text)
        } else if (requestCode == REQ_SPEECH && chatId != null && ChatStore.get(this, chatId!!) == null) {
            // Новый чат, а вопрос так и не задали: возвращаемся к списку
            chatId = null
            render(scrollToEnd = false)
        }
    }

    private fun askTyped() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Вопрос")
            .setView(input)
            .setPositiveButton("Отправить") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) send(text)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ------------------------------------------------------------------ отправка

    private fun send(question: String) {
        val id = chatId ?: ChatStore.newId().also { chatId = it }

        // История: прошлые реплики этого чата (до нового вопроса), без сообщений об ошибках
        val history = JSONArray()
        ChatStore.get(this, id)?.msgs
            ?.filterNot { it.role == "a" && it.text.startsWith("Ошибка ИИ") }
            ?.takeLast(HISTORY_MSGS)
            ?.forEach { history.put(JSONObject().put("r", it.role).put("t", it.text.take(800))) }

        ChatStore.add(this, id, "u", question)
        status = "Отправляю…"
        render(scrollToEnd = true)

        val payload = JSONObject().put("chat", id).put("q", question).put("h", history).toString()

        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    setStatus("Телефон не подключён")
                    return@addOnSuccessListener
                }
                val client = Wearable.getMessageClient(this)
                nodes.forEach { node ->
                    client.sendMessage(node.id, PATH_ASK, payload.toByteArray(Charsets.UTF_8))
                }
                setStatus("Жду ответ ИИ…")
            }
            .addOnFailureListener {
                setStatus("Ошибка связи: ${it.message}")
            }
    }

    private fun setStatus(text: String) {
        status = text
        render(scrollToEnd = true)
    }

    // ------------------------------------------------------------------ мелочи

    private fun small(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#9E9E9E"))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
