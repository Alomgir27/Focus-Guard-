package com.focusguard.app.data.repository

import android.util.Log
import com.focusguard.app.data.dao.AppBlockingDao
import com.focusguard.app.data.entity.BlockedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing blocked apps
 */
class AppBlockingRepository(private val appBlockingDao: AppBlockingDao) {

    private val TAG = "AppBlockingRepository"
    
    // Use a cache to reduce database calls
    private val blockedAppsCache = ConcurrentHashMap<String, BlockedApp>()
    private val mutex = Mutex() // For thread safety
    
    // Initialize cache immediately when repository is created
    init {
        Log.d(TAG, "Initializing AppBlockingRepository")
    }
    
    /**
     * Refresh caches from database
     */
    suspend fun refreshCache() {
        try {
            mutex.withLock {
                Log.d(TAG, "Refreshing app blocking cache")
                
                // Clear existing caches
                blockedAppsCache.clear()
                
                // Reload blocked apps from database
                withContext(Dispatchers.IO) {
                    val blockedApps = appBlockingDao.getAllBlockedApps()
                    for (app in blockedApps) {
                        blockedAppsCache[app.packageName] = app
                    }
                }
                
                Log.d(TAG, "Cache refresh complete. Cached ${blockedAppsCache.size} apps")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing cache: ${e.message}", e)
        }
    }
    
    // Blocked Apps methods
    suspend fun insertBlockedApp(app: BlockedApp) {
        try {
            Log.d(TAG, "Inserting blocked app: ${app.packageName}, name: ${app.appName}, active: ${app.isActive}")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.insertBlockedApp(app)
                // Update cache
                blockedAppsCache[app.packageName] = app
            }
            
            Log.d(TAG, "Successfully inserted blocked app: ${app.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting blocked app: ${app.packageName}", e)
            throw e
        }
    }
    
    suspend fun activateBlockedApp(packageName: String) {
        try {
            Log.d(TAG, "Activating blocked app: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.updateBlockedAppStatus(packageName, true)
                
                // Update cache if app exists
                blockedAppsCache[packageName]?.let { app ->
                    blockedAppsCache[packageName] = app.copy(isActive = true)
                }
            }
            
            // Force refresh cache to ensure consistency
            refreshCache()
            
            Log.d(TAG, "Successfully activated app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error activating app: $packageName", e)
            throw e
        }
    }
    
    suspend fun deactivateBlockedApp(packageName: String) {
        try {
            Log.d(TAG, "Deactivating blocked app: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.updateBlockedAppStatus(packageName, false)
                
                // Update cache if app exists
                blockedAppsCache[packageName]?.let { app ->
                    blockedAppsCache[packageName] = app.copy(isActive = false)
                }
            }
            
            // Force refresh cache to ensure consistency
            refreshCache()
            
            Log.d(TAG, "Successfully deactivated app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating app: $packageName", e)
            throw e
        }
    }
    
    suspend fun updateAppSchedule(packageName: String, startTime: String?, endTime: String?, blockAllDay: Boolean) {
        try {
            Log.d(TAG, "Updating schedule for app: $packageName, start: $startTime, end: $endTime, allDay: $blockAllDay")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.updateBlockedAppSchedule(packageName, startTime, endTime, blockAllDay)
                
                // Update cache if app exists
                blockedAppsCache[packageName]?.let { app ->
                    blockedAppsCache[packageName] = app.copy(
                        startTime = startTime,
                        endTime = endTime,
                        blockAllDay = blockAllDay
                    )
                }
            }
            
            // Force refresh cache to ensure consistency
            refreshCache()
            
            Log.d(TAG, "Successfully updated schedule for app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating schedule for app: $packageName", e)
            throw e
        }
    }
    
    suspend fun updateEnabledDays(packageName: String, enabledDays: Int) {
        try {
            Log.d(TAG, "Updating enabled days for app: $packageName, days: $enabledDays")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.updateBlockedAppDays(packageName, enabledDays)
                
                // Update cache if app exists
                blockedAppsCache[packageName]?.let { app ->
                    blockedAppsCache[packageName] = app.copy(enabledDays = enabledDays)
                }
            }
            
            // Force refresh cache to ensure consistency
            refreshCache()
            
            Log.d(TAG, "Successfully updated enabled days for app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating enabled days for app: $packageName", e)
            throw e
        }
    }
    
    suspend fun getAllBlockedApps(): List<BlockedApp> {
        try {
            // If cache is empty, load from database
            if (blockedAppsCache.isEmpty()) {
                refreshCache()
            }
            
            // Return list from cache
            val apps = blockedAppsCache.values.toList()
            Log.d(TAG, "Retrieved ${apps.size} blocked apps")
            return apps
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving all blocked apps", e)
            
            // Fallback to database if cache fails
            return withContext(Dispatchers.IO) {
                appBlockingDao.getAllBlockedApps()
            }
        }
    }
    
    suspend fun getActiveBlockedApps(): List<BlockedApp> {
        try {
            // If cache is empty, refresh it
            if (blockedAppsCache.isEmpty()) {
                refreshCache()
            }
            
            // Filter active apps from cache that should be blocked right now
            val now = LocalDateTime.now()
            val activeApps = blockedAppsCache.values
                .filter { it.isActive && isAppBlockedAtTime(it, now) }
            
            Log.d(TAG, "Retrieved ${activeApps.size} active blocked apps from cache")
            return activeApps
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving active blocked apps", e)
            
            // Fallback to database and filter
            return withContext(Dispatchers.IO) {
                val apps = appBlockingDao.getActiveBlockedApps()
                val now = LocalDateTime.now()
                apps.filter { isAppBlockedAtTime(it, now) }
            }
        }
    }
    
    suspend fun getBlockedApp(packageName: String): BlockedApp? {
        try {
            // Check cache first
            blockedAppsCache[packageName]?.let {
                Log.d(TAG, "Retrieved blocked app for $packageName from cache: ${it.isActive}")
                return it
            }
            
            // If not in cache, query database
            val app = withContext(Dispatchers.IO) {
                appBlockingDao.getBlockedApp(packageName)
            }
            
            // Update cache if found
            app?.let {
                blockedAppsCache[packageName] = it
            }
            
            Log.d(TAG, "Retrieved blocked app for $packageName from database: ${app != null}")
            return app
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving blocked app: $packageName", e)
            return null
        }
    }
    
    suspend fun deleteBlockedApp(packageName: String) {
        try {
            Log.d(TAG, "Deleting blocked app: $packageName")
            
            withContext(Dispatchers.IO) {
                appBlockingDao.deleteBlockedApp(packageName)
                
                // Remove from cache
                blockedAppsCache.remove(packageName)
            }
            
            Log.d(TAG, "Successfully deleted blocked app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting blocked app: $packageName", e)
            throw e
        }
    }
    
    /**
     * Check if a specific app is actively blocked
     */
    suspend fun isAppBlocked(packageName: String): Boolean {
        try {
            // Check if app should be blocked right now based on time and schedule
            return shouldBlockApp(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is blocked: $packageName", e)
            return false
        }
    }
    
    /**
     * Determine if an app should be blocked based on its schedule and current time
     */
    private fun isAppBlockedAtTime(app: BlockedApp, dateTime: LocalDateTime): Boolean {
        // Not an active block
        if (!app.isActive) {
            return false
        }
        
        // Check if today is an enabled day
        val dayOfWeek = dateTime.dayOfWeek.value % 7 // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
        val dayBit = 1 shl dayOfWeek
        if ((app.enabledDays and dayBit) == 0) {
            // Day not enabled
            return false
        }
        
        // If block all day is enabled, always block
        if (app.blockAllDay) {
            return true
        }
        
        // Check if current time is within block time
        if (app.startTime != null && app.endTime != null) {
            try {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val startTime = LocalTime.parse(app.startTime, formatter)
                val endTime = LocalTime.parse(app.endTime, formatter)
                val currentTime = dateTime.toLocalTime()
                
                // Handle cases where end time is before start time (overnight)
                return if (endTime.isBefore(startTime)) {
                    // Overnight case: 22:00 - 06:00 - block if current time is after start or before end
                    currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
                } else {
                    // Normal case: 08:00 - 17:00 - block if current time is between start and end
                    currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing time schedule for ${app.packageName}: ${e.message}")
                // If can't parse time, default to block
                return true
            }
        }
        
        // No schedule defined, but it's an active block, so block it
        return true
    }
    
    /**
     * Check if a specific app should be blocked based on blocking settings
     */
    suspend fun shouldBlockApp(packageName: String): Boolean {
        try {
            // Get current date time for logging
            val now = LocalDateTime.now()
            val timeStr = now.toString()
            
            // Check if app is in the blocked list first
            val app = getBlockedApp(packageName)
            if (app == null) {
                Log.d(TAG, "shouldBlockApp ($timeStr): NO - $packageName is not in blocked list")
                return false
            }
            
            // If app is in list but not active, don't block
            if (!app.isActive) {
                Log.d(TAG, "shouldBlockApp ($timeStr): NO - $packageName is in blocked list but inactive")
                return false
            }
            
            // Check if app should be blocked based on schedule
            val shouldBlock = isAppBlockedAtTime(app, now)
            
            if (shouldBlock) {
                Log.d(TAG, "shouldBlockApp ($timeStr): YES - $packageName is in blocked list and schedule applies")
            } else {
                Log.d(TAG, "shouldBlockApp ($timeStr): NO - $packageName is in blocked list but outside schedule")
            }
            
            return shouldBlock
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app should be blocked: $packageName", e)
            return false
        }
    }
    
    /**
     * Update the password for a blocked app
     */
    suspend fun updatePassword(packageName: String, password: String?) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating password for app: $packageName")
                
                // Get current app data
                val app = getBlockedApp(packageName)
                if (app == null) {
                    Log.e(TAG, "Cannot update password - app not found: $packageName")
                    return@withContext
                }
                
                // Update with new password
                val updatedApp = app.copy(password = password)
                
                // Insert the updated app
                insertBlockedApp(updatedApp)
                
                Log.d(TAG, "Successfully updated password for: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating password for $packageName: ${e.message}", e)
                throw e
            }
        }
    }
    
    // Helper method for quick adding an app to blocked list
    suspend fun addBlockedApp(packageName: String, appName: String) {
        try {
            Log.d(TAG, "Adding basic blocked app: $packageName, $appName")
            
            // Create a default app entry
            val app = BlockedApp(
                packageName = packageName,
                appName = appName,
                isActive = false,
                startTime = "08:00",
                endTime = "17:00",
                blockAllDay = false,
                enabledDays = 127, // All days enabled by default
                password = null
            )
            
            // Insert into database
            insertBlockedApp(app)
            
            Log.d(TAG, "Successfully added blocked app: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding blocked app: $packageName", e)
            throw e
        }
    }
} 