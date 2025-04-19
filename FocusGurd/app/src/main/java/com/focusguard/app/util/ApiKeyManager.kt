package com.focusguard.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.focusguard.app.BuildConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Manages API key usage and rotation
 */
class ApiKeyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiKeyManager"
        private const val PREF_NAME = "api_key_manager"
        private const val CURRENT_API_KEY = "current_api_key"
        private const val KEY_LAST_ROTATION = "last_rotation_date"
        private const val KEY_USAGE_COUNT = "usage_count"
        private const val KEY_USAGE_LIMIT = "usage_limit"
        private const val KEY_ROTATION_INTERVAL_DAYS = "rotation_interval_days"
        
        // Default values
        private const val DEFAULT_USAGE_LIMIT = 100
        private const val DEFAULT_ROTATION_INTERVAL_DAYS = 30L
        
        // Flag to determine if using production or fallback key
        private const val KEY_USING_MAIN_KEY = "using_main_key"
        
        // Optional fallback/secondary keys
        private const val KEY_SECONDARY_API_KEY = "secondary_api_key"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get the current API key to use
     */
    fun getCurrentApiKey(): String {
        // Initialize if needed
        initialize()
        
        // Get from preferences or fallback to BuildConfig
        val savedKey = preferences.getString(CURRENT_API_KEY, null)
        val isUsingMainKey = preferences.getBoolean(KEY_USING_MAIN_KEY, true)
        
        // If we have a saved key and are still using the main key, return it
        if (!savedKey.isNullOrBlank() && isUsingMainKey) {
            return savedKey
        }
        
        // If we're using the secondary key, try to get it
        if (!isUsingMainKey) {
            val secondaryKey = preferences.getString(KEY_SECONDARY_API_KEY, null)
            if (!secondaryKey.isNullOrBlank()) {
                return secondaryKey
            }
        }
        
        // Fallback to BuildConfig key
        return BuildConfig.OPENAI_API_KEY
    }
    
    /**
     * Record API key usage and rotate if needed
     */
    fun recordUsage(): Boolean {
        // Increment usage count
        val currentCount = preferences.getInt(KEY_USAGE_COUNT, 0)
        val newCount = currentCount + 1
        preferences.edit().putInt(KEY_USAGE_COUNT, newCount).apply()
        
        // Check if we should rotate based on usage
        val usageLimit = preferences.getInt(KEY_USAGE_LIMIT, DEFAULT_USAGE_LIMIT)
        if (newCount >= usageLimit) {
            return rotateApiKey()
        }
        
        // Check if we should rotate based on time
        val lastRotationStr = preferences.getString(KEY_LAST_ROTATION, null)
        if (!lastRotationStr.isNullOrBlank()) {
            try {
                val lastRotation = LocalDateTime.parse(lastRotationStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val now = LocalDateTime.now()
                val daysSinceRotation = ChronoUnit.DAYS.between(lastRotation, now)
                val rotationInterval = preferences.getLong(KEY_ROTATION_INTERVAL_DAYS, DEFAULT_ROTATION_INTERVAL_DAYS)
                
                if (daysSinceRotation >= rotationInterval) {
                    return rotateApiKey()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing last rotation date", e)
            }
        }
        
        return true
    }
    
    /**
     * Set a secondary/backup API key
     */
    fun setSecondaryApiKey(apiKey: String) {
        preferences.edit().putString(KEY_SECONDARY_API_KEY, apiKey).apply()
        Log.d(TAG, "Secondary API key saved")
    }
    
    /**
     * Switch to the secondary API key
     */
    fun switchToSecondaryKey() {
        preferences.edit().putBoolean(KEY_USING_MAIN_KEY, false).apply()
        Log.d(TAG, "Switched to secondary API key")
    }
    
    /**
     * Switch back to the primary API key
     */
    fun switchToPrimaryKey() {
        preferences.edit().putBoolean(KEY_USING_MAIN_KEY, true).apply()
        Log.d(TAG, "Switched to primary API key")
    }
    
    /**
     * Rotate the API key (this simulates rotation, in a real app you would call your backend to get a new key)
     */
    private fun rotateApiKey(): Boolean {
        try {
            // In a real app, this would make a secure API call to your backend
            // to request a new API key. Here we're just simulating rotation.
            
            // Generate a fake new key for simulation purposes
            // val newKey = "sk-" + UUID.randomUUID().toString().replace("-", "")
            
            // In a real app, you would get the new key from your backend
            val newKey = BuildConfig.OPENAI_API_KEY
            
            // Reset usage count
            preferences.edit()
                .putString(CURRENT_API_KEY, newKey)
                .putInt(KEY_USAGE_COUNT, 0)
                .putString(KEY_LAST_ROTATION, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .apply()
            
            Log.d(TAG, "API key rotated successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating API key", e)
            return false
        }
    }
    
    /**
     * Initialize the API key manager if it's the first run
     */
    private fun initialize() {
        // Check if already initialized
        if (preferences.contains(CURRENT_API_KEY)) {
            return
        }
        
        Log.d(TAG, "Initializing API key manager")
        
        // Set initial values
        preferences.edit()
            .putString(CURRENT_API_KEY, BuildConfig.OPENAI_API_KEY)
            .putInt(KEY_USAGE_COUNT, 0)
            .putInt(KEY_USAGE_LIMIT, DEFAULT_USAGE_LIMIT)
            .putLong(KEY_ROTATION_INTERVAL_DAYS, DEFAULT_ROTATION_INTERVAL_DAYS)
            .putString(KEY_LAST_ROTATION, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .putBoolean(KEY_USING_MAIN_KEY, true)
            .apply()
    }
    
    /**
     * Configure usage limits and rotation interval
     */
    fun configure(usageLimit: Int, rotationIntervalDays: Long) {
        preferences.edit()
            .putInt(KEY_USAGE_LIMIT, usageLimit)
            .putLong(KEY_ROTATION_INTERVAL_DAYS, rotationIntervalDays)
            .apply()
        
        Log.d(TAG, "API key manager configured: limit=$usageLimit, interval=$rotationIntervalDays days")
    }
} 