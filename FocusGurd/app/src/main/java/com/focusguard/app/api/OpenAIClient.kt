package com.focusguard.app.api

import android.util.Log
import com.focusguard.app.api.models.ChatCompletionRequest
import com.focusguard.app.api.models.Message
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class OpenAIClient {
    companion object {
        private const val TAG = "OpenAIClient"
        private const val BASE_URL = "https://api.openai.com/"
        private var instance: OpenAIClient? = null
        
        // Retry constants
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 10000L
        
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
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < MAX_RETRIES) {
            try {
                if (currentRetry > 0) {
                    Log.d(TAG, "Retry attempt $currentRetry for OpenAI API call")
                    
                    // Calculate exponential backoff time with jitter
                    val backoffTime = (INITIAL_BACKOFF_MS * 2.0.pow(currentRetry - 1)).toLong()
                    val jitteredBackoff = (backoffTime * (0.5 + Math.random() * 0.5)).toLong()
                    val delayTime = jitteredBackoff.coerceAtMost(MAX_BACKOFF_MS)
                    
                    Log.d(TAG, "Waiting for $delayTime ms before retry")
                    delay(delayTime)
                }
                
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
                        return@withContext Result.success(generatedText)
                    } else {
                        Log.e(TAG, "Empty response from OpenAI")
                        lastException = Exception("Empty response from OpenAI")
                    }
                } else {
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMessage = "Error: $errorCode - $errorBody"
                    Log.e(TAG, errorMessage)
                    
                    // Only retry on specific error codes that are likely to be temporary
                    if (errorCode == 429 || errorCode >= 500) {
                        lastException = Exception(errorMessage)
                        currentRetry++
                        continue
                    } else {
                        // Don't retry on client errors (except rate limits)
                        return@withContext Result.failure(Exception(errorMessage))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating content", e)
                lastException = e
            }
            
            currentRetry++
        }
        
        // If we've exhausted retries, return the last exception
        Result.failure(lastException ?: Exception("Failed after $MAX_RETRIES retries"))
    }
    
    /**
     * Create a chat completion with a custom request object
     */
    suspend fun createChatCompletion(
        apiKey: String,
        request: ChatCompletionRequest
    ): Result<String> = withContext(Dispatchers.IO) {
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < MAX_RETRIES) {
            try {
                if (currentRetry > 0) {
                    Log.d(TAG, "Retry attempt $currentRetry for OpenAI API call")
                    
                    // Calculate exponential backoff time with jitter
                    val backoffTime = (INITIAL_BACKOFF_MS * 2.0.pow(currentRetry - 1)).toLong()
                    val jitteredBackoff = (backoffTime * (0.5 + Math.random() * 0.5)).toLong()
                    val delayTime = jitteredBackoff.coerceAtMost(MAX_BACKOFF_MS)
                    
                    Log.d(TAG, "Waiting for $delayTime ms before retry")
                    delay(delayTime)
                }
                
                Log.d(TAG, "Creating chat completion with custom request")
                
                val response = openAIService.createChatCompletion("Bearer $apiKey", request)
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null && responseBody.choices.isNotEmpty()) {
                        val generatedText = responseBody.choices[0].message.content.trim()
                        Log.d(TAG, "Generated content successfully")
                        return@withContext Result.success(generatedText)
                    } else {
                        Log.e(TAG, "Empty response from OpenAI")
                        lastException = Exception("Empty response from OpenAI")
                    }
                } else {
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMessage = "Error: $errorCode - $errorBody"
                    Log.e(TAG, errorMessage)
                    
                    // Only retry on specific error codes that are likely to be temporary
                    if (errorCode == 429 || errorCode >= 500) {
                        lastException = Exception(errorMessage)
                        currentRetry++
                        continue
                    } else {
                        // Don't retry on client errors (except rate limits)
                        return@withContext Result.failure(Exception(errorMessage))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating content", e)
                lastException = e
            }
            
            currentRetry++
        }
        
        // If we've exhausted retries, return the last exception
        Result.failure(lastException ?: Exception("Failed after $MAX_RETRIES retries"))
    }
} 