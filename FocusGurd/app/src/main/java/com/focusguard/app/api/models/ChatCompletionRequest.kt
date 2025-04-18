package com.focusguard.app.api.models

data class ChatCompletionRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)

data class Message(
    val role: String,
    val content: String
) 