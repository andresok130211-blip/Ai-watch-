package com.example.watchai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageClient = Wearable.getMessageClient(this)

        setContent {
            WatchScreen(messageClient)
        }
    }
}

@Composable
fun WatchScreen(messageClient: MessageClient) {
    var answer by remember { mutableStateOf("Нажми кнопку,\nчтобы спросить") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messageClient.addListener { event: MessageEvent ->
            if (event.path == "/ai/response") {
                answer = String(event.data, Charsets.UTF_8)
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = answer,
            fontSize = 14.sp,
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                answer = "Отправляю..."

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val nodes = Wearable.getNodeClient(messageClient.applicationContext)
                            .connectedNodes.await()

                        if (nodes.isEmpty()) {
                            answer = "Часы не подключены\nк телефону"
                            isLoading = false
                            return@launch
                        }

                        val question = "Привет! Это тестовый вопрос с часов"
                        messageClient.sendMessage(
                            nodes[0].id,
                            "/ai/question",
                            question.toByteArray(Charsets.UTF_8)
                        ).await()
                    } catch (e: Exception) {
                        answer = "Ошибка: ${e.message}"
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
