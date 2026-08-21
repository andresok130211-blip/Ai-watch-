package com.example.watchai

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class MainActivity : ComponentActivity() {

    // Тот же UUID, что и в BluetoothServerService.kt на телефоне
    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private var onPermissionResult: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionResult?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WatchScreen(
                onAsk = { question, onResult -> askPhone(question, onResult) },
                onRequestPermission = { callback -> requestBtPermission(callback) }
            )
        }
    }

    private fun requestBtPermission(callback: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                onPermissionResult = callback
                permissionLauncher.launch(needed.toTypedArray())
                return
            }
        }
        callback()
    }

    @Suppress("MissingPermission")
    private fun askPhone(question: String, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    onResult("Bluetooth недоступен")
                    return@launch
                }

                val bonded: Set<BluetoothDevice> = adapter.bondedDevices
                if (bonded.isEmpty()) {
                    onResult("Нет сопряжённых устройств.\nСначала сопрягите телефон\nв настройках Bluetooth часов")
                    return@launch
                }

                // Берём первое сопряжённое устройство (обычно это телефон)
                val phoneDevice = bonded.first()

                socket = phoneDevice.createInsecureRfcommSocketToServiceRecord(appUuid)
                adapter.cancelDiscovery()
                socket.connect()

                val output = socket.outputStream
                output.write((question + "\n").toByteArray(Charsets.UTF_8))
                output.flush()

                val input = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val answer = input.readLine() ?: "Пустой ответ"

                onResult(answer)
            } catch (e: Exception) {
                onResult("Ошибка: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }
}

@Composable
fun WatchScreen(
    onAsk: (String, (String) -> Unit) -> Unit,
    onRequestPermission: (() -> Unit) -> Unit
) {
    var answer by remember { mutableStateOf("Нажми кнопку,\nчтобы спросить") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = answer,
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                answer = "Подключаюсь..."
                onRequestPermission {
                    val question = "Привет! Это тестовый вопрос с часов"
                    onAsk(question) { result ->
                        answer = result
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Жду..." else "Спросить")
        }
    }
}
