package com.focusguard.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.focusguard.app.BuildConfig
import com.focusguard.app.api.OpenAIClient
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.data.entity.UserInsight
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NotificationGenerator(private val context: Context) {
    
    // Initialize our new components
    private val cacheManager = NotificationCacheManager(context)
    private val apiKeyManager = ApiKeyManager(context)
    
    companion object {
        private const val TAG = "NotificationGenerator"
        
        private val religiousVerses = listOf(
            "Verily, with hardship comes ease. (Quran 94:5)",
            "Allah does not burden a soul beyond that it can bear. (Quran 2:286)",
            "Indeed, Allah is with the patient. (Quran 2:153)",
            "And whoever puts their trust in Allah, then He will suffice him. (Quran 65:3)",
            "So remember Me; I will remember you. (Quran 2:152)"
        )
        
        private val motivationalQuotes = listOf(
            "The journey of a thousand miles begins with a single step.",
            "You are never too old to set another goal or to dream a new dream.",
            "Believe you can and you're halfway there.",
            "The only way to do great work is to love what you do.",
            "Your time is limited, so don't waste it living someone else's life."
        )
    }
    
    /**
     * Generates a notification based on the user's habits and preferences using AI
     */
    suspend fun generateAINotification(
        habits: List<UserHabit>,
        previousSuggestions: List<UserInsight>,
        appUsage: String,
        notificationType: NotificationType
    ): Result<Notification> {
        Log.d(TAG, "Generating AI notification of type: $notificationType")
        
        // Check for cached content first if enabled
        if (shouldUseCache(notificationType)) {
            val cachedContent = cacheManager.getCachedContent(notificationType)
            if (cachedContent != null) {
                Log.d(TAG, "Using cached content for $notificationType")
                return Result.success(createNotificationFromContent(cachedContent, notificationType))
            }
        }
        
        // Check for offline mode
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Network unavailable, using offline content")
            val offlineContent = cacheManager.getOfflineContent(notificationType)
            if (offlineContent != null) {
                return Result.success(createNotificationFromContent(offlineContent, notificationType))
            }
            return Result.success(generateFallbackNotification(notificationType))
        }
        
        // Get the current API key from the manager
        val apiKey = apiKeyManager.getCurrentApiKey()
        
        // Build prompt and generate content
        val prompt = buildPrompt(habits, previousSuggestions, appUsage, notificationType)
        val systemPrompt = getSystemPrompt(notificationType)
        
        return try {
            val result = OpenAIClient.getInstance().generateContent(prompt, apiKey, systemPrompt)
            
            if (result.isSuccess) {
                // Record API usage for rotation tracking
                apiKeyManager.recordUsage()
                
                val content = result.getOrNull() ?: "Error generating content"
                Log.d(TAG, "Generated content: $content")
                
                // Cache the successful response
                cacheManager.cacheResponse(notificationType, content)
                
                // Pre-cache for offline use if this is the first successful generation
                if (cacheManager.getOfflineContent(notificationType) == null) {
                    cacheManager.preCacheForOfflineUse(notificationType, content)
                }
                
                val notification = createNotificationFromContent(content, notificationType)
                Result.success(notification)
            } else {
                Log.e(TAG, "Error generating AI content: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception generating AI notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a notification object from content and type
     */
    private fun createNotificationFromContent(content: String, notificationType: NotificationType): Notification {
        val title = when (notificationType) {
            NotificationType.HABIT_REMINDER -> "Habit Reminder"
            NotificationType.MOTIVATION -> "Daily Motivation"
            NotificationType.INSIGHT -> "Personal Insight"
            NotificationType.RELIGIOUS_QUOTE -> "Spiritual Reflection"
            NotificationType.APP_USAGE_WARNING -> "Usage Alert"
            NotificationType.GENERAL -> "Thought for the Day"
        }
        
        return Notification(
            title = title,
            content = content,
            type = notificationType,
            createdAt = LocalDateTime.now()
        )
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Determine if we should try to use cached content
     */
    private fun shouldUseCache(notificationType: NotificationType): Boolean {
        // Get from shared preferences
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("use_cache_${notificationType.name.lowercase()}", true)
    }
    
    /**
     * Wrapper that simplifies AI notification generation by handling the Result directly
     */
    suspend fun generateAINotificationDirectly(
        habits: List<UserHabit>,
        previousSuggestions: List<UserInsight>,
        appUsage: String,
        notificationType: NotificationType
    ): Notification {
        return try {
            val result = generateAINotification(
                habits, 
                previousSuggestions, 
                appUsage, 
                notificationType
            )
            
            if (result.isSuccess) {
                result.getOrNull() ?: generateFallbackNotification(notificationType)
            } else {
                generateFallbackNotification(notificationType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI notification", e)
            generateFallbackNotification(notificationType)
        }
    }
    
    /**
     * Generates a fallback notification when AI is not available
     */
    fun generateFallbackNotification(notificationType: NotificationType): Notification {
        Log.d(TAG, "Generating fallback notification of type: $notificationType")
        
        val (title, content) = when (notificationType) {
            NotificationType.RELIGIOUS_QUOTE -> {
                "Spiritual Reflection" to religiousVerses.random()
            }
            NotificationType.MOTIVATION -> {
                "Daily Motivation" to motivationalQuotes.random()
            }
            NotificationType.HABIT_REMINDER -> {
                "Habit Reminder" to "Take a moment to reflect on your habits and make positive choices today."
            }
            NotificationType.INSIGHT -> {
                "Personal Insight" to "Small changes in daily routines can lead to significant improvements over time."
            }
            NotificationType.APP_USAGE_WARNING -> {
                "Usage Alert" to "Remember to take breaks from screen time for your well-being."
            }
            NotificationType.GENERAL -> {
                "Thought for the Day" to "Focus on progress, not perfection."
            }
        }
        
        return Notification(
            title = title,
            content = content,
            type = notificationType,
            createdAt = LocalDateTime.now()
        )
    }
    
    /**
     * Pre-generate content for offline use
     */
    suspend fun preGenerateOfflineContent() {
        // Only do this if we have network
        if (!isNetworkAvailable()) {
            return
        }
        
        Log.d(TAG, "Pre-generating content for offline use")
        
        // Generate one of each type
        NotificationType.values().forEach { type ->
            try {
                // Skip if we already have offline content for this type
                if (cacheManager.getOfflineContent(type) != null) {
                    return@forEach
                }
                
                // Generate minimal content to save API calls
                val result = OpenAIClient.getInstance().generateContent(
                    "Generate a brief but inspiring ${type.name.replace('_', ' ').lowercase()} message.",
                    apiKeyManager.getCurrentApiKey()
                )
                
                if (result.isSuccess) {
                    val content = result.getOrNull() ?: return@forEach
                    cacheManager.preCacheForOfflineUse(type, content)
                    Log.d(TAG, "Pre-generated offline content for $type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-generating content for $type", e)
            }
        }
    }
    
    private fun buildPrompt(
        habits: List<UserHabit>,
        previousSuggestions: List<UserInsight>,
        appUsage: String,
        notificationType: NotificationType
    ): String {
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val habitText = if (habits.isNotEmpty()) {
            "User's habits: " + habits.joinToString(", ") { 
                "${it.habitName}${if (it.isDistracting) " (distracting)" else ""}" 
            }
        } else {
            "User hasn't specified any habits yet."
        }
        
        val previousSuggestionsText = if (previousSuggestions.isNotEmpty()) {
            "Previous suggestions: " + previousSuggestions.take(5).joinToString(". ") { it.content }
        } else {
            "No previous suggestions available."
        }
        
        val promptBase = when (notificationType) {
            NotificationType.RELIGIOUS_QUOTE -> 
                "Generate an inspiring Islamic quote or Quranic verse with brief explanation."
            
            NotificationType.MOTIVATION -> 
                "Generate a short motivational message to inspire the user."
            
            NotificationType.HABIT_REMINDER -> 
                "Generate a gentle reminder about forming better habits, specifically addressing: $habitText"
            
            NotificationType.INSIGHT -> 
                "Based on the user's app usage patterns ($appUsage) and habits ($habitText), provide an insightful observation or suggestion for improvement."
            
            NotificationType.APP_USAGE_WARNING -> 
                "Based on app usage data ($appUsage), provide a gentle reminder about digital wellbeing."
            
            NotificationType.GENERAL -> 
                "Provide a thoughtful reflection or tip for the day that could help with productivity or wellbeing."
        }
        
        return """
            Today's date: $date
            $habitText
            $previousSuggestionsText
            App usage information: $appUsage
            
            Request: $promptBase
            
            Important: Keep the response concise (maximum 2-3 sentences) and focused on a single point.
        """.trimIndent()
    }
    
    private fun getSystemPrompt(notificationType: NotificationType): String {
        return when (notificationType) {
            NotificationType.RELIGIOUS_QUOTE -> """
                You are an assistant specializing in Islamic knowledge. Provide brief, meaningful Quranic verses or Hadith 
                with simple explanations that can inspire and guide users.
            """.trimIndent()
            
            NotificationType.MOTIVATION -> """
                You are a motivational coach. Provide brief, powerful messages that inspire action and positive thinking.
            """.trimIndent()
            
            NotificationType.HABIT_REMINDER -> """
                You are a habit coach. Provide gentle, encouraging reminders about healthy habits and breaking unhealthy ones.
            """.trimIndent()
            
            NotificationType.INSIGHT -> """
                You are an insight specialist. Analyze patterns and provide thoughtful observations that help users 
                understand themselves better.
            """.trimIndent()
            
            NotificationType.APP_USAGE_WARNING -> """
                You are a digital wellbeing advisor. Provide constructive reminders about healthy technology use 
                without being judgmental.
            """.trimIndent()
            
            NotificationType.GENERAL -> """
                You are a wellbeing assistant. Provide brief, thoughtful reflections that encourage mindfulness 
                and personal growth.
            """.trimIndent()
        }
    }
} 