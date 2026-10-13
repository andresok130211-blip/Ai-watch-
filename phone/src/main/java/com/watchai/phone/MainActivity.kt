package com.watchai.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {

    private companion object {
        const val REQ_PICK_MODEL = 200
    }

    private lateinit var settings: Settings
    private lateinit var builtinSw: Switch
    private lateinit var modelBox: LinearLayout
    private lateinit var externalBox: LinearLayout
    private lateinit var modelTv: TextView
    private lateinit var downloadBtn: Button
    private lateinit var pickBtn: Button
    private lateinit var urlEt: EditText
    private lateinit var modelEt: EditText
    private lateinit var keyEt: EditText
    private lateinit var systemEt: EditText
    private lateinit var bridgeBtn: Button
    private lateinit var watchTv: TextView
    private lateinit var logTv: TextView

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshModelStatus()
            ui.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        LlmClient.appContext = applicationContext

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

        builtinSw = Switch(this).apply {
            text = "Встроенный ИИ (Qwen3 1.7B на этом телефоне)"
            isChecked = settings.builtin
            setPadding(0, dp(14), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                settings.builtin = checked
                applyMode()
            }
        }
        root.addView(builtinSw)

        // --- Встроенная модель ---
        modelBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        modelTv = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(4))
        }
        modelBox.addView(modelTv)
        downloadBtn = Button(this).apply {
            text = "Скачать модель (1,3 ГБ, нужен Wi-Fi)"
            setOnClickListener {
                ModelStore.download(applicationContext)
                refreshModelStatus()
            }
        }
        modelBox.addView(downloadBtn)
        pickBtn = Button(this).apply {
            text = "Выбрать свой .gguf файл"
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(i, REQ_PICK_MODEL)
            }
        }
        modelBox.addView(pickBtn)
        modelBox.addView(Button(this).apply {
            text = "Удалить модель"
            setOnClickListener {
                ModelStore.delete(applicationContext)
                refreshModelStatus()
            }
        })
        root.addView(modelBox)

        // --- Внешний ИИ ---
        externalBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        externalBox.addView(label("Адрес ИИ (OpenAI-совместимый API). Ollama: http://IP:11434/v1, LM Studio: http://IP:1234/v1"))
        urlEt = EditText(this).apply {
            setText(settings.url)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        externalBox.addView(urlEt)
        externalBox.addView(label("Модель (например qwen2.5:7b). Можно оставить пустой, если сервер сам выбирает"))
        modelEt = EditText(this).apply {
            setText(settings.model)
            setSingleLine()
        }
        externalBox.addView(modelEt)
        externalBox.addView(label("API-ключ (если нужен)"))
        keyEt = EditText(this).apply {
            setText(settings.apiKey)
            setSingleLine()
        }
        externalBox.addView(keyEt)
        root.addView(externalBox)

        root.addView(label("Системная инструкция для ИИ"))
        systemEt = EditText(this).apply {
            setText(settings.system)
            minLines = 2
        }
        root.addView(systemEt)

        root.addView(Button(this).apply {
            text = "Проверить связь с ИИ"
            setOnClickListener { testAi() }
        })

        bridgeBtn = Button(this).apply {
            setOnClickListener { toggleBridge() }
        }
        root.addView(bridgeBtn)

        root.addView(label("Последний диалог"))
        logTv = TextView(this).apply { textSize = 14f }
        root.addView(logTv)

        setContentView(ScrollView(this).apply { addView(root) })
        applyMode()

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
        ui.post(tick)
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
        ui.removeCallbacks(tick)
        saveSettings()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_MODEL && resultCode == RESULT_OK) {
            data?.data?.let { ModelStore.import(applicationContext, it) }
        }
    }

    private fun applyMode() {
        val b = settings.builtin
        modelBox.visibility = if (b) View.VISIBLE else View.GONE
        externalBox.visibility = if (b) View.GONE else View.VISIBLE
        refreshModelStatus()
    }

    private fun refreshModelStatus() {
        val has = ModelStore.hasModel(applicationContext)
        val busy = ModelStore.busy
        modelTv.text = when {
            ModelStore.status.isNotBlank() && (busy || !has) -> ModelStore.status
            has -> "Модель готова (${ModelStore.modelFile(applicationContext).length() / 1_000_000} МБ). " +
                "Сервер ИИ запустится при первом вопросе."
            else -> "Модель не загружена"
        }
        downloadBtn.isEnabled = !busy
        pickBtn.isEnabled = !busy
    }

    private fun saveSettings() {
        settings.url = urlEt.text.toString().trim()
        settings.model = modelEt.text.toString().trim()
        settings.apiKey = keyEt.text.toString().trim()
        settings.system = systemEt.text.toString().trim()
    }

    private fun testAi() {
        saveSettings()
        logTv.text = if (settings.builtin) "Запускаю встроенный ИИ, первый раз это до минуты…" else "Спрашиваю ИИ…"
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
