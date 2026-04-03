package com.example.gemma4

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import java.io.File

class InferenceModel private constructor(context: Context, modelFile: String) {
    private val TAG = "InferenceModel"
    private val engine: Engine
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null

    companion object {
        @Volatile
        private var instance: InferenceModel? = null
        private var currentModelFile: String? = null

        fun getInstance(context: Context, modelFile: String): InferenceModel {
            val existing = instance
            if (existing != null && currentModelFile == modelFile) {
                return existing
            }
            synchronized(this) {
                val existing2 = instance
                if (existing2 != null && currentModelFile == modelFile) {
                    return existing2
                }
                existing2?.close()
                instance = null
                currentModelFile = null

                val newInstance = InferenceModel(context.applicationContext, modelFile)
                instance = newInstance
                currentModelFile = modelFile
                return newInstance
            }
        }
    }

    init {
        val modelPath = File(context.filesDir, modelFile).absolutePath
        Log.i(TAG, "LiteRT-LM 모델 로딩: $modelPath")

        engine = try {
            val gpuConfig = EngineConfig(modelPath = modelPath, backend = Backend.GPU())
            Engine(gpuConfig).also { it.initialize() }
        } catch (e: Throwable) {
            Log.w(TAG, "GPU 초기화 실패, CPU로 전환: ${e.message}")
            val cpuConfig = EngineConfig(modelPath = modelPath, backend = Backend.CPU())
            Engine(cpuConfig).also { it.initialize() }
        }

        Log.i(TAG, "LiteRT-LM 엔진 초기화 완료")
        createConversation("You are a helpful assistant.\nAlways respond in Korean.")
    }

    fun createConversation(systemPrompt: String) {
        conversation?.close()
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt)
        )
        conversation = engine.createConversation(config)
        Log.i(TAG, "새 대화 세션 생성")
    }

    suspend fun generateResponse(
        message: String,
        onPartialResult: (String) -> Unit
    ) {
        val conv = conversation ?: throw IllegalStateException("대화가 초기화되지 않았습니다.")

        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L
        var tokenCount = 0

        conv.sendMessageAsync(message)
            .catch { e ->
                Log.e(TAG, "생성 오류: ${e.message}")
                throw e
            }
            .collect { chunk ->
                val text = "$chunk"
                if (tokenCount == 0) {
                    firstTokenTime = System.currentTimeMillis()
                }
                tokenCount++
                if (text.isNotEmpty()) {
                    onPartialResult(text)
                }
            }

        val endTime = System.currentTimeMillis()
        val prefillMs = if (firstTokenTime > 0) firstTokenTime - startTime else 0
        val decodeMs = if (firstTokenTime > 0) endTime - firstTokenTime else endTime - startTime
        val decTps = if (decodeMs > 0) tokenCount * 1000.0 / decodeMs else 0.0

        Log.i(TAG, "생성 완료: prefill=${prefillMs}ms, decode=${decodeMs}ms, ${decTps.toInt()} t/s")

        val perfInfo = "\n\n[LiteRT-LM]\n" +
                "- Prefill: %.2fs\n".format(prefillMs / 1000.0) +
                "- Decode: %.1f t/s (%.2fs)".format(decTps, decodeMs / 1000.0)
        onPartialResult(perfInfo)
    }

    fun resetSession(systemPrompt: String) {
        createConversation(systemPrompt)
    }

    fun close() {
        Log.i(TAG, "InferenceModel 종료")
        try {
            conversation?.close()
            engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "종료 중 오류: ${e.message}")
        }
        synchronized(Companion) {
            if (instance === this) {
                instance = null
                currentModelFile = null
            }
        }
    }
}
