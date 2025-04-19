package com.focusguard.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.focusguard.app.data.entity.NotificationType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A manager for caching AI-generated notification content
 * to reduce API calls and costs
 */
class NotificationCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationCacheManager"
        private const val PREF_NAME = "notification_cache"
        private const val CACHE_EXPIRY_HOURS = 24L // Cache expiry time in hours
        private const val MAX_CACHE_ENTRIES = 20 // Maximum number of entries per type
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Cache an AI-generated notification response
     */
    fun cacheResponse(notificationType: NotificationType, content: String) {
        try {
            val cacheKey = getCacheKey(notificationType)
            val currentCache = getCache(cacheKey)
            
            // Create new cache entry
            val entry = CacheEntry(
                content = content,
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
            // Add to the beginning of the list (most recent first)
            currentCache.add(0, entry)
            
            // Keep only the last MAX_CACHE_ENTRIES entries
            val trimmedCache = currentCache.take(MAX_CACHE_ENTRIES)
            
            // Save updated cache
            val json = gson.toJson(trimmedCache)
            preferences.edit().putString(cacheKey, json).apply()
            
            Log.d(TAG, "Cached content for $notificationType. Cache size: ${trimmedCache.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching response", e)
        }
    }
    
    /**
     * Get a cached notification content if available
     * Returns null if nothing is cached or cache is expired
     */
    fun getCachedContent(notificationType: NotificationType): String? {
        try {
            val cacheKey = getCacheKey(notificationType)
            val cache = getCache(cacheKey)
            
            if (cache.isEmpty()) {
                return null
            }
            
            // Get a random entry from the top 3 (if available) for variety
            val randomIndex = if (cache.size > 3) (0 until 3).random() else 0
            val entry = cache.getOrNull(randomIndex) ?: return null
            
            // Check if entry is expired
            val timestamp = LocalDateTime.parse(entry.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val now = LocalDateTime.now()
            val hoursDifference = ChronoUnit.HOURS.between(timestamp, now)
            
            if (hoursDifference > CACHE_EXPIRY_HOURS) {
                // Cache expired, return null
                Log.d(TAG, "Cache expired for $notificationType (${hoursDifference}h old)")
                return null
            }
            
            Log.d(TAG, "Using cached content for $notificationType (${hoursDifference}h old)")
            return entry.content
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached content", e)
            return null
        }
    }
    
    /**
     * Clear expired cache entries to save space
     */
    fun clearExpiredCache() {
        try {
            NotificationType.values().forEach { type ->
                val cacheKey = getCacheKey(type)
                val cache = getCache(cacheKey)
                
                if (cache.isEmpty()) {
                    return@forEach
                }
                
                // Filter out expired entries
                val now = LocalDateTime.now()
                val validEntries = cache.filter { entry ->
                    try {
                        val timestamp = LocalDateTime.parse(entry.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val hoursDifference = ChronoUnit.HOURS.between(timestamp, now)
                        hoursDifference <= CACHE_EXPIRY_HOURS
                    } catch (e: Exception) {
                        false
                    }
                }
                
                // Save filtered cache
                if (validEntries.size != cache.size) {
                    val json = gson.toJson(validEntries)
                    preferences.edit().putString(cacheKey, json).apply()
                    Log.d(TAG, "Cleared ${cache.size - validEntries.size} expired entries for $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing expired cache", e)
        }
    }
    
    /**
     * Pre-generate and cache content for offline use
     */
    fun preCacheForOfflineUse(notificationType: NotificationType, content: String) {
        // Tag this content as pre-cached for offline use
        val cacheKey = getCacheKey(notificationType) + "_offline"
        preferences.edit().putString(cacheKey, content).apply()
        Log.d(TAG, "Pre-cached content for offline use: $notificationType")
    }
    
    /**
     * Get pre-cached content for offline use
     */
    fun getOfflineContent(notificationType: NotificationType): String? {
        val cacheKey = getCacheKey(notificationType) + "_offline"
        return preferences.getString(cacheKey, null)
    }
    
    /**
     * Clear all cached content
     */
    fun clearAllCache() {
        preferences.edit().clear().apply()
        Log.d(TAG, "All notification cache cleared")
    }
    
    private fun getCacheKey(notificationType: NotificationType): String {
        return "cache_${notificationType.name.lowercase()}"
    }
    
    private fun getCache(cacheKey: String): MutableList<CacheEntry> {
        val json = preferences.getString(cacheKey, null)
        
        return if (json != null) {
            try {
                val type = object : TypeToken<List<CacheEntry>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cache", e)
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }
    
    /**
     * Cache entry data class
     */
    data class CacheEntry(
        val content: String,
        val timestamp: String
    )
} 