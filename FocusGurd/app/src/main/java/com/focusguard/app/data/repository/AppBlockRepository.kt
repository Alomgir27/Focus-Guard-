package com.focusguard.app.data.repository

import android.util.Log
import com.focusguard.app.data.dao.AppBlockDao
import com.focusguard.app.data.entity.BlockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import androidx.lifecycle.LiveData

/**
 * Repository for managing blocked apps
 */
class AppBlockRepository(private val appBlockDao: AppBlockDao) {

    private val TAG = "AppBlockRepository"
    
    // Use a cache to reduce database calls
    private val blockedAppsCache = ConcurrentHashMap<String, BlockedAppEntity>()
    private val mutex = Mutex() // For thread safety
    
    val allBlockedApps: LiveData<List<BlockedAppEntity>> = appBlockDao.getAllBlockedAppsLiveData()
    
    // Initialize the repository
    init {
        Log.d(TAG, "Initializing AppBlockRepository")
    }
    
    /**
     * Refresh caches from database
     */
    suspend fun refreshCache() {
        mutex.withLock {
            try {
                Log.d(TAG, "Refreshing repository cache")
                
                val allBlockedApps = withContext(Dispatchers.IO) {
                    appBlockDao.getAllBlockedApps()
                }
                
                // Clear and update caches
                blockedAppsCache.clear()
                allBlockedApps.forEach { blockedAppsCache[it.packageName] = it }
                
                Log.d(TAG, "Cache refreshed: ${blockedAppsCache.size} blocked apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * BlockedAppEntity operations
     */
    suspend fun insertBlockedApp(blockedApp: BlockedAppEntity) {
        try {
            Log.d(TAG, "Inserting blocked app: ${blockedApp.packageName}, isActive=${blockedApp.isActive}")
            
            withContext(Dispatchers.IO) {
                appBlockDao.insertBlockedApp(blockedApp)
            }
            
            // Update cache
            blockedAppsCache[blockedApp.packageName] = blockedApp
            
            Log.d(TAG, "Successfully inserted blocked app: ${blockedApp.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting blocked app: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun insertBlockedApp(packageName: String, appName: String, isActive: Boolean) {
        try {
            Log.d(TAG, "Inserting blocked app: $packageName, isActive=$isActive")
            
            val blockedApp = BlockedAppEntity(packageName, appName, isActive)
            withContext(Dispatchers.IO) {
                appBlockDao.insertBlockedApp(blockedApp)
            }
            
            // Update cache
            blockedAppsCache[packageName] = blockedApp
            
            Log.d(TAG, "Successfully inserted blocked app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting blocked app: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun updateBlockedAppStatus(packageName: String, isActive: Boolean) {
        try {
            Log.d(TAG, "Updating blocked app status: $packageName, isActive=$isActive")
            
            withContext(Dispatchers.IO) {
                appBlockDao.updateBlockedAppStatus(packageName, isActive)
            }
            
            // Update cache if present
            blockedAppsCache[packageName]?.let {
                blockedAppsCache[packageName] = it.copy(isActive = isActive)
            }
            
            Log.d(TAG, "Successfully updated blocked app status: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocked app status: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update the schedule for a blocked app
     */
    suspend fun updateBlockedAppSchedule(
        packageName: String,
        startTime: String?,
        endTime: String?,
        blockAllDay: Boolean,
        enabledDays: Int,
        password: String?
    ) {
        try {
            Log.d(TAG, "Updating blocked app schedule: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockDao.updateBlockedAppSchedule(
                    packageName,
                    startTime,
                    endTime,
                    blockAllDay,
                    enabledDays,
                    password
                )
            }
            
            // Update cache if present
            blockedAppsCache[packageName]?.let { currentApp ->
                blockedAppsCache[packageName] = currentApp.copy(
                    startTime = startTime,
                    endTime = endTime,
                    blockAllDay = blockAllDay,
                    enabledDays = enabledDays,
                    password = password
                )
            }
            
            Log.d(TAG, "Successfully updated blocked app schedule: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocked app schedule: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update just the password for a blocked app
     */
    suspend fun updateBlockedAppPassword(packageName: String, password: String) {
        try {
            Log.d(TAG, "Updating blocked app password: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockDao.updateBlockedAppPassword(packageName, password)
            }
            
            // Update cache if present
            blockedAppsCache[packageName]?.let { currentApp ->
                blockedAppsCache[packageName] = currentApp.copy(password = password)
            }
            
            Log.d(TAG, "Successfully updated blocked app password: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocked app password: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * For backward compatibility with code expecting BlockedApp
     */
    suspend fun getBlockedApp(packageName: String): BlockedAppEntity? {
        try {
            // Check cache first
            blockedAppsCache[packageName]?.let {
                return it
            }
            
            // If not in cache, query database
            val app = withContext(Dispatchers.IO) {
                appBlockDao.getBlockedApp(packageName)
            }
            
            // Update cache with result
            app?.let {
                blockedAppsCache[packageName] = it
            }
            
            return app
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving blocked app: $packageName", e)
            return null
        }
    }
    
    suspend fun getAllBlockedApps(): List<BlockedAppEntity> {
        return try {
            withContext(Dispatchers.IO) {
                appBlockDao.getAllBlockedApps()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all blocked apps: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity> {
        return try {
            withContext(Dispatchers.IO) {
                appBlockDao.getActiveBlockedApps()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active blocked apps: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun deleteBlockedApp(packageName: String) {
        try {
            Log.d(TAG, "Deleting blocked app: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockDao.deleteBlockedApp(packageName)
            }
            
            // Remove from caches
            blockedAppsCache.remove(packageName)
            
            Log.d(TAG, "Successfully deleted blocked app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting blocked app: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Blocking logic
     */
    suspend fun shouldBlockAppNow(packageName: String): Boolean {
        try {
            // Check if app is in the blocked list and active
            val blockedApp = getBlockedApp(packageName)
            
            if (blockedApp == null) {
                Log.d(TAG, "App $packageName is not in blocked list")
                return false
            }
            
            if (!blockedApp.isActive) {
                Log.d(TAG, "App $packageName is in blocked list but not active")
                return false
            }
            
            // Check if it should be blocked based on schedule
            if (!isWithinBlockingSchedule(blockedApp)) {
                Log.d(TAG, "App $packageName is outside blocking schedule")
                return false
            }
            
            // App is in list, active, and within schedule, so block it
            Log.d(TAG, "App $packageName should be blocked based on schedule")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app should be blocked: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Check if current time is within the blocking schedule
     */
    private fun isWithinBlockingSchedule(blockedApp: BlockedAppEntity): Boolean {
        try {
            // Log entry for debugging
            Log.d(TAG, "Checking if ${blockedApp.packageName} should be blocked now")
            
            // If block all day is enabled, check only the days
            if (blockedApp.blockAllDay) {
                val isDayEnabled = isDayEnabled(blockedApp.enabledDays)
                Log.d(TAG, "Block all day is enabled. Current day enabled: $isDayEnabled")
                return isDayEnabled
            }
            
            // Return false if no schedule is set (for safety)
            if (blockedApp.startTime == null || blockedApp.endTime == null) {
                Log.d(TAG, "No schedule set for ${blockedApp.packageName}")
                return false
            }
            
            // Check if current day is enabled
            if (!isDayEnabled(blockedApp.enabledDays)) {
                Log.d(TAG, "Current day not enabled for ${blockedApp.packageName}")
                return false
            }
            
            // Parse start and end times
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val startTime = LocalTime.parse(blockedApp.startTime, formatter)
            val endTime = LocalTime.parse(blockedApp.endTime, formatter)
            val currentTime = LocalTime.now()
            
            // Log time comparison for debugging
            Log.d(TAG, "Checking time: current=$currentTime, start=$startTime, end=$endTime")
            
            // Check if current time is within the range
            val isWithinRange = if (startTime.isBefore(endTime)) {
                // Simple case: start time is before end time (e.g., 9:00 to 17:00)
                // Starting exactly at startTime and ending exactly at endTime (inclusive start, exclusive end)
                currentTime.compareTo(startTime) >= 0 && currentTime.compareTo(endTime) < 0
            } else {
                // Complex case: start time is after end time (e.g., 22:00 to 6:00)
                // For overnight blocks, include all times after startTime or before endTime
                currentTime.compareTo(startTime) >= 0 || currentTime.compareTo(endTime) < 0
            }
            
            Log.d(TAG, "Time check result for ${blockedApp.packageName}: $isWithinRange")
            return isWithinRange
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking blocking schedule: ${e.message}", e)
            return false // Default to not block if there's an error
        }
    }
    
    /**
     * Check if the current day is enabled in the days bit field
     */
    private fun isDayEnabled(enabledDays: Int): Boolean {
        // Get current day of week (1 = Sunday, 7 = Saturday)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Convert to 0-based index and check if the bit is set
        val dayBit = 1 shl (dayOfWeek - 1)
        val isDayEnabled = (enabledDays and dayBit) != 0
        
        // Log for debugging
        Log.d(TAG, "Checking day enabled: dayOfWeek=$dayOfWeek, enabledDays=0b${Integer.toBinaryString(enabledDays)}, isDayEnabled=$isDayEnabled")
        
        return isDayEnabled
    }
    
    /**
     * Generate a random 8-digit password
     */
    fun generateRandomPassword(): String {
        val chars = "0123456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
    
    /**
     * Verify if the provided password matches the stored password
     */
    suspend fun verifyPassword(packageName: String, password: String): Boolean {
        try {
            val blockedApp = getBlockedApp(packageName)
            
            if (blockedApp == null) {
                Log.e(TAG, "verifyPassword: App $packageName not found in database")
                return false
            }
            
            val storedPassword = blockedApp.password
            
            if (storedPassword.isNullOrEmpty()) {
                Log.e(TAG, "verifyPassword: No password set for $packageName")
                return false
            }
            
            val isMatch = storedPassword == password
            Log.d(TAG, "verifyPassword for $packageName: ${if (isMatch) "MATCH" else "NO MATCH"}")
            return isMatch
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying password for $packageName: ${e.message}", e)
            return false
        }
    }
} 