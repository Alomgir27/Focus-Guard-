package com.focusguard.app.api

import android.util.Log
import com.focusguard.app.api.models.ChatCompletionRequest
import com.focusguard.app.api.models.Message
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class OpenAIClient {
    companion object {
        private const val TAG = "OpenAIClient"
        private const val BASE_URL = "https://api.openai.com/"
        private var instance: OpenAIClient? = null
        
        fun getInstance(): OpenAIClient {
            if (instance == null) {
                instance = OpenAIClient()
            }
            return instance!!
        }
    }
    
    private val openAIService: OpenAIService
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        openAIService = retrofit.create(OpenAIService::class.java)
    }
    
    suspend fun generateContent(
        prompt: String,
        apiKey: String,
        systemPrompt: String = "You are a helpful assistant."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating content with prompt: $prompt")
            
            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", prompt)
            )
            
            val request = ChatCompletionRequest(
                messages = messages
            )
            
            val response = openAIService.createChatCompletion("Bearer $apiKey", request)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.choices.isNotEmpty()) {
                    val generatedText = responseBody.choices[0].message.content.trim()
                    Log.d(TAG, "Generated content successfully")
                    Result.success(generatedText)
                } else {
                    Log.e(TAG, "Empty response from OpenAI")
                    Result.failure(Exception("Empty response from OpenAI"))
                }
            } else {
                val errorMessage = "Error: ${response.code()} - ${response.errorBody()?.string()}"
                Log.e(TAG, errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            Result.failure(e)
        }
    }
} 