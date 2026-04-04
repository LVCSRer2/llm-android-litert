package com.example.gemma4

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ModelType(
    val fileName: String,
    val displayName: String,
    val sizeMb: String,
    val url: String,
    val needsAuth: Boolean = false
) {
    GEMMA4_E2B(
        fileName = "gemma-4-E2B-it.litertlm",
        displayName = "Gemma 4 E2B (LiteRT-LM)",
        sizeMb = "~2.6GB",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        needsAuth = true
    ),
    GEMMA4_E4B(
        fileName = "gemma-4-E4B-it.litertlm",
        displayName = "Gemma 4 E4B (LiteRT-LM)",
        sizeMb = "~3.7GB",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        needsAuth = true
    )
}

object ModelDownloader {
    private const val TAG = "ModelDownloader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 대용량 파일 다운로드
        .build()

    fun modelExists(context: Context, modelType: ModelType): Boolean {
        return File(context.filesDir, modelType.fileName).exists()
    }

    fun deleteModel(context: Context, modelType: ModelType) {
        val file = File(context.filesDir, modelType.fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun downloadModel(
        context: Context,
        modelType: ModelType,
        hfToken: String?,
        onProgress: (Int) -> Unit,
        onFinished: (Boolean, String?) -> Unit
    ) {
        val file = File(context.filesDir, modelType.fileName)
        if (file.exists()) {
            onFinished(true, null)
            return
        }

        withContext(Dispatchers.IO) {
            doDownload(file, modelType, hfToken, onProgress, onFinished)
        }
    }

    private fun doDownload(
        file: File,
        modelType: ModelType,
        hfToken: String?,
        onProgress: (Int) -> Unit,
        onFinished: (Boolean, String?) -> Unit
    ) {
        try {
            val requestBuilder = Request.Builder().url(modelType.url)
            if (modelType.needsAuth && !hfToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $hfToken")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        401 -> "Unauthorized: HF Token이 유효하지 않거나 누락되었습니다."
                        403 -> "Forbidden: Hugging Face 웹사이트에서 모델 라이선스를 수락해야 합니다."
                        404 -> "Not Found: 모델 파일을 찾을 수 없습니다."
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    onFinished(false, errorMsg)
                    return
                }

                val body = response.body ?: throw Exception("응답 본문이 비어 있습니다.")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
                onFinished(true, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "다운로드 실패", e)
            if (file.exists()) file.delete()
            onFinished(false, e.message)
        }
    }
}
