package com.watchai.phone

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Встроенный ИИ: приложение запускает llama-server (сборка llama.cpp для Android, лежит в APK
 * как libllamaserver.so) как дочерний процесс на 127.0.0.1 и общается с ним по OpenAI-совместимому API.
 */
object LocalServer {
    const val PORT = 8080
    const val BASE = "http://127.0.0.1:$PORT/v1"

    @Volatile private var proc: Process? = null

    private fun healthy(): Boolean = try {
        val c = URL("http://127.0.0.1:$PORT/health").openConnection() as HttpURLConnection
        c.connectTimeout = 800
        c.readTimeout = 800
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    fun isRunning(): Boolean = proc?.isAlive == true

    /** Блокирующий вызов (до ~2 минут при первой загрузке модели). Возвращает текст ошибки или null. */
    @Synchronized
    fun ensureRunning(ctx: Context): String? {
        if (healthy()) return null

        stop()
        val model = ModelStore.modelFile(ctx)
        if (!ModelStore.hasModel(ctx)) {
            return "Модель не загружена. Открой приложение «Часы → ИИ» и нажми «Скачать модель»."
        }
        val libDir = ctx.applicationInfo.nativeLibraryDir
        val bin = File(libDir, "libllamaserver.so")
        if (!bin.exists()) return "В APK нет llama-server (файл ${bin.name})."

        val log = File(ctx.filesDir, "server.log")
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val pb = ProcessBuilder(
            bin.absolutePath,
            "-m", model.absolutePath,
            "--host", "127.0.0.1",
            "--port", PORT.toString(),
            "-c", "4096",
            "-t", threads.toString(),
            "--jinja"
        )
        pb.environment()["LD_LIBRARY_PATH"] = libDir
        pb.redirectErrorStream(true)
        pb.redirectOutput(log)

        val p = try {
            pb.start()
        } catch (e: Exception) {
            return "Не удалось запустить сервер: ${e.message}"
        }
        proc = p

        val deadline = System.currentTimeMillis() + 150_000
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive) {
                return "Сервер ИИ завершился: " + tail(log)
            }
            if (healthy()) return null
            Thread.sleep(500)
        }
        return "Сервер ИИ не успел загрузить модель за 2,5 минуты"
    }

    fun stop() {
        val p = proc ?: return
        proc = null
        try {
            p.destroy()
            Thread.sleep(300)
            if (p.isAlive) p.destroyForcibly()
        } catch (_: Exception) {
        }
    }

    private fun tail(f: File): String = try {
        f.readLines().takeLast(4).joinToString(" | ").take(300)
    } catch (_: Exception) {
        "(лога нет)"
    }
}
