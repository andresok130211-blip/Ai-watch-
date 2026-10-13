package com.watchai.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var urlEt: EditText
    private lateinit var modelEt: EditText
    private lateinit var keyEt: EditText
    private lateinit var systemEt: EditText
    private lateinit var bridgeBtn: Button
    private lateinit var watchTv: TextView
    private lateinit var logTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, dp(14), 0, 0)
        }

        root.addView(TextView(this).apply {
            text = "Вопрос на часах → ИИ на телефоне → ответ на часы"
            textSize = 17f
        })

        watchTv = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(watchTv)

        root.addView(label("Адрес ИИ (OpenAI-совместимый API). Ollama: http://IP:11434/v1, LM Studio: http://IP:1234/v1"))
        urlEt = EditText(this).apply {
            setText(settings.url)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(urlEt)

        root.addView(label("Модель (например qwen2.5:7b). Можно оставить пустой, если сервер сам выбирает"))
        modelEt = EditText(this).apply {
            setText(settings.model)
            setSingleLine()
        }
        root.addView(modelEt)

        root.addView(label("API-ключ (если нужен)"))
        keyEt = EditText(this).apply {
            setText(settings.apiKey)
            setSingleLine()
        }
        root.addView(keyEt)

        root.addView(label("Системная инструкция для ИИ"))
        systemEt = EditText(this).apply {
            setText(settings.system)
            minLines = 2
        }
        root.addView(systemEt)

        val testBtn = Button(this).apply {
            text = "Проверить связь с ИИ"
            setOnClickListener { testAi() }
        }
        root.addView(testBtn)

        bridgeBtn = Button(this).apply {
            setOnClickListener { toggleBridge() }
        }
        root.addView(bridgeBtn)

        root.addView(label("Последний диалог"))
        logTv = TextView(this).apply { textSize = 14f }
        root.addView(logTv)

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBridgeButton()
        logTv.text = settings.log.ifBlank { "Пока пусто" }
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                watchTv.text = if (nodes.isEmpty()) {
                    "Часы: не подключены"
                } else {
                    "Часы: " + nodes.joinToString { it.displayName }
                }
            }
            .addOnFailureListener {
                watchTv.text = "Часы: нет связи (${it.message})"
            }
    }

    override fun onPause() {
        saveSettings()
        super.onPause()
    }

    private fun saveSettings() {
        settings.url = urlEt.text.toString().trim()
        settings.model = modelEt.text.toString().trim()
        settings.apiKey = keyEt.text.toString().trim()
        settings.system = systemEt.text.toString().trim()
    }

    private fun testAi() {
        saveSettings()
        logTv.text = "Спрашиваю ИИ…"
        Thread {
            val result = try {
                "Ответ ИИ: " + LlmClient.ask(settings, "Скажи «привет» одним словом")
            } catch (e: Exception) {
                "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { logTv.text = result }
        }.start()
    }

    private fun toggleBridge() {
        saveSettings()
        if (settings.bridgeOn) {
            settings.bridgeOn = false
            startService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_STOP))
        } else {
            settings.bridgeOn = true
            startForegroundService(Intent(this, BridgeService::class.java))
        }
        refreshBridgeButton()
    }

    private fun refreshBridgeButton() {
        bridgeBtn.text = if (settings.bridgeOn) {
            "Постоянный режим включён (нажми, чтобы выключить)"
        } else {
            "Включить постоянный режим (надёжнее)"
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
