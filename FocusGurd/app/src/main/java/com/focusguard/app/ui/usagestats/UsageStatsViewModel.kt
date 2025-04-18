package com.focusguard.app.ui.usagestats

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "UsageStatsViewModel"
    
    enum class TimeRange {
        DAY, WEEK, MONTH
    }
    
    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val timeInForeground: Long,
        val appIcon: Drawable?,
        val lastTimeUsed: Long = 0L,
        val launchCount: Int = 0,
        val dailyUsage: Map<Date, Long> = emptyMap()
    )
    
    private val _usageStatsList = MutableLiveData<List<AppUsageInfo>>()
    val usageStatsList: LiveData<List<AppUsageInfo>> = _usageStatsList
    
    private val _totalUsageTime = MutableLiveData<Long>()
    val totalUsageTime: LiveData<Long> = _totalUsageTime
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    fun loadUsageStatistics(timeRange: TimeRange) {
        _isLoading.value = true
        _errorMessage.value = ""
        
        viewModelScope.launch {
            try {
                val usageStatsList = getUsageStatistics(timeRange)
                
                // Calculate total usage time
                val totalTime = usageStatsList.sumOf { it.timeInForeground }
                
                withContext(Dispatchers.Main) {
                    _usageStatsList.value = usageStatsList
                    _totalUsageTime.value = totalTime
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading usage stats", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error: ${e.message}"
                    _isLoading.value = false
                }
            }
        }
    }
    
    private suspend fun getUsageStatistics(timeRange: TimeRange): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        
        // Calculate time range
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        when (timeRange) {
            TimeRange.DAY -> calendar.add(Calendar.DAY_OF_YEAR, -1)
            TimeRange.WEEK -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            TimeRange.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        
        val startTime = calendar.timeInMillis
        
        // Map to store app usage info
        val usageMap = mutableMapOf<String, AppUsageInfo>()
        
        // Also track daily usage
        val dailyUsageMap = mutableMapOf<String, MutableMap<Date, Long>>()
        
        // Use UsageEvents for more accurate data
        try {
            // Get usage events
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            
            // Map to track app launch counts
            val launchCountMap = mutableMapOf<String, Int>()
            
            // Map to track last foreground time for each app
            val lastForegroundTimeMap = mutableMapOf<String, Long>()
            
            // Process all events
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                
                val packageName = event.packageName
                
                // Skip system packages
                if (isSystemPackage(packageName, packageManager)) {
                    continue
                }
                
                // Track foreground time
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundTimeMap[packageName] = event.timeStamp
                    
                    // Increment launch count
                    launchCountMap[packageName] = (launchCountMap[packageName] ?: 0) + 1
                } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    val lastForegroundTime = lastForegroundTimeMap[packageName] ?: continue
                    val foregroundTime = event.timeStamp - lastForegroundTime
                    
                    // Initialize app info if not exists
                    if (!usageMap.containsKey(packageName)) {
                        val appInfo = try {
                            packageManager.getApplicationInfo(packageName, 0)
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                        
                        val appName = appInfo?.let { 
                            packageManager.getApplicationLabel(it).toString() 
                        } ?: packageName
                        
                        val appIcon = try {
                            packageManager.getApplicationIcon(packageName)
                        } catch (e: Exception) {
                            null
                        }
                        
                        usageMap[packageName] = AppUsageInfo(
                            packageName = packageName,
                            appName = appName,
                            timeInForeground = 0,
                            appIcon = appIcon,
                            lastTimeUsed = 0,
                            launchCount = 0,
                            dailyUsage = emptyMap()
                        )
                        
                        // Initialize daily usage map
                        dailyUsageMap[packageName] = mutableMapOf()
                    }
                    
                    // Update foreground time
                    val currentInfo = usageMap[packageName]!!
                    usageMap[packageName] = currentInfo.copy(
                        timeInForeground = currentInfo.timeInForeground + foregroundTime
                    )
                    
                    // Update daily usage
                    val date = Calendar.getInstance().apply {
                        timeInMillis = event.timeStamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    
                    val dailyUsage = dailyUsageMap[packageName]!!
                    val currentDailyTime = dailyUsage[date] ?: 0L
                    dailyUsage[date] = currentDailyTime + foregroundTime
                }
            }
            
            // Update last time used and launch count
            usageMap.forEach { (packageName, info) ->
                val lastUsedTime = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                ).find { it.packageName == packageName }?.lastTimeUsed ?: 0L
                
                val launchCount = launchCountMap[packageName] ?: 0
                
                // Create daily usage map from our tracking
                val dailyUsage = dailyUsageMap[packageName]?.toMap() ?: emptyMap()
                
                usageMap[packageName] = info.copy(
                    lastTimeUsed = lastUsedTime,
                    launchCount = launchCount,
                    dailyUsage = dailyUsage
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing usage events", e)
            
            // Fallback to UsageStats
            val usageStats = usageStatsManager.queryUsageStats(
                when (timeRange) {
                    TimeRange.DAY -> UsageStatsManager.INTERVAL_DAILY
                    TimeRange.WEEK -> UsageStatsManager.INTERVAL_WEEKLY
                    TimeRange.MONTH -> UsageStatsManager.INTERVAL_MONTHLY
                },
                startTime,
                endTime
            )
            
            for (stat in usageStats) {
                val packageName = stat.packageName
                
                // Skip system packages
                if (isSystemPackage(packageName, packageManager)) {
                    continue
                }
                
                // Skip entries with no usage time
                if (stat.totalTimeInForeground == 0L) {
                    continue
                }
                
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val appIcon = packageManager.getApplicationIcon(packageName)
                    
                    usageMap[packageName] = AppUsageInfo(
                        packageName = packageName,
                        appName = appName,
                        timeInForeground = stat.totalTimeInForeground,
                        appIcon = appIcon,
                        lastTimeUsed = stat.lastTimeUsed,
                        launchCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            stat.firstTimeStamp.toInt()  // We don't have real launch count in fallback
                        } else {
                            0
                        },
                        dailyUsage = emptyMap()  // We don't have daily breakdown in fallback
                    )
                } catch (e: Exception) {
                    // Skip package if info can't be retrieved
                    Log.e(TAG, "Error getting app info for $packageName", e)
                }
            }
        }
        
        // Convert map to sorted list (by usage time, descending)
        return@withContext usageMap.values.toList()
            .filter { it.timeInForeground > 0 }
            .sortedByDescending { it.timeInForeground }
    }
    
    private fun isSystemPackage(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    fun formatUsageTime(timeInMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % 60
        
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }
} 