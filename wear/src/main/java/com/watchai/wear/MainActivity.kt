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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {

    private companion object {
        const val REQ_SPEECH = 100
        const val PATH_ASK = "/ask"
    }

    private lateinit var statusTv: TextView
    private lateinit var answerTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Круглый экран: отступы побольше, чтобы текст не резался по краям.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(34), dp(22), dp(40))
        }

        root.addView(Button(this).apply {
            text = "🎤 Спросить"
            setOnClickListener { startVoice() }
        })
        root.addView(Button(this).apply {
            text = "⌨ Текстом"
            setOnClickListener { askTyped() }
        })

        statusTv = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(statusTv)

        answerTv = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            text = getSharedPreferences("watchai", MODE_PRIVATE).getString("last", "") ?: ""
        }
        root.addView(answerTv)

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        AnswerBus.onAnswer = { text ->
            runOnUiThread {
                statusTv.text = "Ответ ИИ"
                answerTv.text = text
            }
        }
    }

    override fun onPause() {
        AnswerBus.onAnswer = null
        super.onPause()
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
        }
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (e: ActivityNotFoundException) {
            statusTv.text = "Нет голосового ввода, ввожу текстом"
            askTyped()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) send(text)
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

    private fun send(question: String) {
        statusTv.text = "Отправляю…"
        answerTv.text = "Вы: $question"
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    statusTv.text = "Телефон не подключён"
                    return@addOnSuccessListener
                }
                val client = Wearable.getMessageClient(this)
                nodes.forEach { node ->
                    client.sendMessage(node.id, PATH_ASK, question.toByteArray(Charsets.UTF_8))
                }
                statusTv.text = "Жду ответ ИИ…"
            }
            .addOnFailureListener {
                statusTv.text = "Ошибка связи: ${it.message}"
            }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
