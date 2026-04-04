package com.example.gemma4

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: ChatSettings,
    currentModel: ModelType,
    onSave: (ChatSettings, ModelType) -> Unit,
    onBack: () -> Unit,
    onDownload: (ModelType) -> Unit = {}
) {
    val context = LocalContext.current
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var maxTokens by remember { mutableIntStateOf(currentSettings.maxTokens) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var deleteTarget by remember { mutableStateOf<ModelType?>(null) }

    deleteTarget?.let { modelType ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("모델 삭제") },
            text = { Text("${modelType.displayName}을 삭제합니다. 계속하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    ModelDownloader.deleteModel(context, modelType)
                    deleteTarget = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LiteRT-LM 설정") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("\u2190", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val newSettings = currentSettings.copy(
                            systemPrompt = systemPrompt,
                            maxTokens = maxTokens
                        )
                        ChatSettings.save(context, newSettings)
                        onSave(newSettings, selectedModel)
                    }) {
                        Text("저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "모델",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModelType.entries.forEach { modelType ->
                val isDownloaded = ModelDownloader.modelExists(context, modelType)
                val isSelected = modelType == selectedModel

                Card(
                    onClick = {
                        if (isDownloaded) selectedModel = modelType else onDownload(modelType)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(text = modelType.displayName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "크기: ${modelType.sizeMb}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isDownloaded) {
                                Row {
                                    Text(
                                        text = if (isSelected) "선택됨" else "준비됨",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (modelType != currentModel) {
                                        TextButton(onClick = { deleteTarget = modelType }) {
                                            Text(
                                                "삭제",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "다운로드",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "시스템 프롬프트",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "파라미터",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SliderSetting(
                label = "Max Tokens",
                value = maxTokens.toFloat(),
                range = 64f..2048f,
                format = { it.roundToInt().toString() }
            ) { maxTokens = it.roundToInt() }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = format(value),
            modifier = Modifier.weight(0.2f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
