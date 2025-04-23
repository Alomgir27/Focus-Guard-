package com.focusguard.app.api.models

data class ChatCompletionRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1200
)

data class Message(
    val role: String,
    val content: String
) 