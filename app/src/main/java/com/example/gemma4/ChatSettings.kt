package com.example.gemma4

import android.content.Context

data class ChatSettings(
    val systemPrompt: String = "You are a helpful assistant.\nAlways respond in Korean.\nAnswer concisely and accurately.",
    val maxTokens: Int = 1024
) {
    companion object {
        private const val PREFS_NAME = "chat_settings"

        fun load(context: Context): ChatSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ChatSettings(
                systemPrompt = prefs.getString("system_prompt", null)
                    ?: ChatSettings().systemPrompt,
                maxTokens = prefs.getInt("max_tokens", 1024)
            )
        }

        fun save(context: Context, settings: ChatSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("system_prompt", settings.systemPrompt)
                .putInt("max_tokens", settings.maxTokens)
                .apply()
        }
    }
}
