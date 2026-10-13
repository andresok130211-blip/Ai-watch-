package com.watchai.phone

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Хранение модели (GGUF) во внутренней папке приложения: скачивание и импорт из файла. */
object ModelStore {
    const val MODEL_URL =
        "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"

    @Volatile var busy = false
        private set
    @Volatile var status = ""
        private set

    fun modelFile(ctx: Context): File {
        val dir = ctx.getExternalFilesDir("models") ?: File(ctx.filesDir, "models")
        dir.mkdirs()
        return File(dir, "model.gguf")
    }

    fun hasModel(ctx: Context): Boolean = modelFile(ctx).let { it.exists() && it.length() > 1_000_000 }

    fun download(ctx: Context) {
        if (busy) return
        busy = true
        status = "Подключаюсь к Hugging Face…"
        Thread {
            val target = modelFile(ctx)
            val part = File(target.parentFile, "model.gguf.part")
            try {
                val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                var done = 0L
                var lastUi = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 500) {
                                lastUi = now
                                status = "Скачано ${done / 1_000_000} из ${if (total > 0) total / 1_000_000 else "?"} МБ"
                            }
                        }
                    }
                }
                if (done < 1_000_000) error("файл слишком маленький")
                target.delete()
                if (!part.renameTo(target)) error("не удалось сохранить файл")
                status = "Модель готова (${target.length() / 1_000_000} МБ)"
            } catch (e: Exception) {
                part.delete()
                status = "Ошибка загрузки: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }.start()
    }

    /** Копирует выбранный пользователем .gguf в папку приложения. */
    fun import(ctx: Context, uri: Uri) {
        if (busy) return
        busy = true
        status = "Копирую файл…"
        Thread {
            val target = modelFile(ctx)
            val part = File(target.parentFile, "model.gguf.part")
            try {
                var done = 0L
                var lastUi = 0L
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 500) {
                                lastUi = now
                                status = "Скопировано ${done / 1_000_000} МБ"
                            }
                        }
                    }
                }
                target.delete()
                if (!part.renameTo(target)) error("не удалось сохранить файл")
                status = "Модель готова (${target.length() / 1_000_000} МБ)"
            } catch (e: Exception) {
                part.delete()
                status = "Ошибка копирования: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }.start()
    }

    fun delete(ctx: Context) {
        LocalServer.stop()
        modelFile(ctx).delete()
        status = "Модель удалена"
    }
}
