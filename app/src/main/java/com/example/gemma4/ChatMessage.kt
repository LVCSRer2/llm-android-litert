package com.example.gemma4

enum class MessageAuthor {
    USER, MODEL
}

data class ChatMessage(
    val text: String,
    val author: MessageAuthor,
    val isLoading: Boolean = false
)
