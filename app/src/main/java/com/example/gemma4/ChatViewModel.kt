package com.example.gemma4

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = ChatUiState()
    private var inferenceModel: InferenceModel? = null
    var modelType: ModelType by mutableStateOf(ModelType.GEMMA4_E2B)
    var settings by mutableStateOf(ChatSettings.load(application))
    var isModelLoading by mutableStateOf(false)
    val isModelLoaded: Boolean get() = inferenceModel != null

    var attachedImageBytes: ByteArray? by mutableStateOf(null)
        private set
    var attachedAudioBytes: ByteArray? by mutableStateOf(null)
        private set

    fun attachImage(bytes: ByteArray) { attachedImageBytes = bytes }
    fun attachAudio(bytes: ByteArray) { attachedAudioBytes = bytes }
    fun clearImage() { attachedImageBytes = null }
    fun clearAudio() { attachedAudioBytes = null }

    private var generationJob: Job? = null
    private var tokenCountJob: Job? = null

    fun loadModel(type: ModelType) {
        modelType = type
        isModelLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prevModel = inferenceModel
                prevModel?.close()
                inferenceModel = null

                val app = getApplication<Application>()
                if (!ModelDownloader.modelExists(app, type)) {
                    uiState.addModelMessage(
                        text = "모델이 다운로드되지 않았습니다: ${type.displayName}",
                        isLoading = false
                    )
                    return@launch
                }

                inferenceModel = InferenceModel.getInstance(app, type.fileName)
                inferenceModel?.createConversation(settings.systemPrompt)
            } catch (e: Exception) {
                inferenceModel = null
                uiState.addModelMessage(
                    text = "모델 로드 실패: ${e.message}",
                    isLoading = false
                )
            } finally {
                isModelLoading = false
            }
        }
    }

    fun updateSettings(newSettings: ChatSettings, selectedModel: ModelType) {
        settings = newSettings
        if (selectedModel != modelType) {
            uiState.clear()
            uiState.isGenerating = false
            loadModel(selectedModel)
        } else {
            inferenceModel?.resetSession(newSettings.systemPrompt)
        }
    }

    fun onInputTextChanged(text: String) {
        tokenCountJob?.cancel()
        if (text.isBlank()) {
            uiState.inputTokenCount = 0
            return
        }
        tokenCountJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            // LiteRT-LM은 별도 토큰 카운트 API가 없으므로 근사값 사용
            uiState.inputTokenCount = text.length / 4
        }
    }

    fun sendMessage(userMessage: String) {
        val model = inferenceModel ?: return
        if (uiState.isGenerating) return

        val imgBytes = attachedImageBytes
        val audBytes = attachedAudioBytes
        attachedImageBytes = null
        attachedAudioBytes = null

        uiState.isGenerating = true

        val displayText = buildString {
            if (imgBytes != null) appendLine("[이미지 첨부]")
            if (audBytes != null) appendLine("[오디오 첨부]")
            append(userMessage)
        }
        uiState.addUserMessage(displayText)
        uiState.addModelMessage(text = "", isLoading = true)
        uiState.inputTokenCount = 0

        val fullResponse = StringBuilder()

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                model.generateResponse(
                    message = userMessage,
                    imageBytes = imgBytes,
                    audioBytes = audBytes,
                    onPartialResult = { partial ->
                        fullResponse.append(partial)
                        uiState.updateLastModelMessage(
                            text = fullResponse.toString(),
                            isLoading = true
                        )
                    }
                )
                uiState.updateLastModelMessage(
                    text = fullResponse.toString(),
                    isLoading = false
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                uiState.updateLastModelMessage(
                    text = fullResponse.toString().ifEmpty { "[생성 중단]" } + " ■",
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState.updateLastModelMessage(
                    text = "오류: ${e.message}",
                    isLoading = false
                )
            } finally {
                uiState.isGenerating = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun resetChat() {
        generationJob?.cancel()
        uiState.clear()
        uiState.isGenerating = false
        inferenceModel?.resetSession(settings.systemPrompt)
    }
}
