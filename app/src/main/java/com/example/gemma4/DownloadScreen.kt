package com.example.gemma4

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    modelType: ModelType,
    onDownloadComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hfToken by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var downloadedMb by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "모델 다운로드",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = modelType.displayName,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "크기: ${modelType.sizeMb}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hugging Face에서 다운로드합니다.\n모델 라이선스 동의가 필요합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (modelType.needsAuth) {
            OutlinedTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                label = { Text("Hugging Face Token") },
                placeholder = { Text("hf_...") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDownloading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "다운로드 중... $progress%")
        } else {
            Button(
                onClick = {
                    isDownloading = true
                    errorMessage = null
                    scope.launch {
                        ModelDownloader.downloadModel(
                            context,
                            modelType,
                            hfToken,
                            onProgress = { p ->
                                scope.launch { progress = p }
                            },
                            onFinished = { success, error ->
                                scope.launch {
                                    isDownloading = false
                                    if (success) onDownloadComplete() else errorMessage = error
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !modelType.needsAuth || hfToken.isNotBlank()
            ) {
                Text("다운로드 시작")
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
