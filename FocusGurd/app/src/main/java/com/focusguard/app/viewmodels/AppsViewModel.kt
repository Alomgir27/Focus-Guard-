package com.focusguard.app.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppBlockRepository
import com.focusguard.app.models.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AppsViewModel"
    private val context: Context = application.applicationContext
    private val repository: AppBlockRepository = MyApplication.appBlockRepository
    
    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps
    
    private val _filteredApps = MutableLiveData<List<AppInfo>>()
    val filteredApps: LiveData<List<AppInfo>> = _filteredApps
    
    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private var searchQuery: String = ""
    private var showSystemApps: Boolean = false
    private var filterMode: Int = FILTER_ALL
    
    companion object {
        const val FILTER_ALL = 0
        const val FILTER_BLOCKED = 1
        const val FILTER_NOT_BLOCKED = 2
        
        // Default categories of apps to consider "popular"
        private val POPULAR_CATEGORIES = setOf(
            "entertainment", "social", "communication", "dating",
            "lifestyle", "video", "shopping", "productivity"
        )
    }
    
    init {
        loadApps()
    }

    fun loadApps() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val installedApps = getInstalledApps()
                val blockedApps = repository.getAllBlockedApps()
                
                val appsList = installedApps.map { app ->
                    val blockedApp = blockedApps.find { it.packageName == app.packageName }
                    app.isBlocked = blockedApp?.isActive ?: false
                    app
                }
                
                _apps.value = appsList
                filterApps(searchQuery)
                _isLoading.value = false
                
                Log.d(TAG, "Loaded ${appsList.size} apps, found ${blockedApps.size} blocked apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or 
                PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES
            }
            
            // Get installed packages
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags)
            }
            
            // Create AppInfo objects
            installedPackages.mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                
                // For system apps, check if we should include them
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystemApp && !showSystemApps) {
                    return@mapNotNull null
                }
                
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    var icon: Drawable? = null
                    try {
                        icon = packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't load icon for ${appInfo.packageName}: ${e.message}")
                    }
                    
                    // Create an AppInfo object
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        isSystemApp = isSystemApp,
                        icon = icon,
                        isBlocked = false,
                        canBeBlocked = !isLauncherPackage(appInfo.packageName)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting info for app ${appInfo.packageName}: ${e.message}")
                    null
                }
            }.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}", e)
            emptyList()
        }
    }
    
    fun filterApps(query: String) {
        // Don't block UI thread with this operation
        viewModelScope.launch(Dispatchers.Default) {
            try {
                searchQuery = query.trim().lowercase()
                val allApps = _apps.value ?: emptyList()
                
                val filtered = allApps.filter { app ->
                    // First apply text search filter
                    val matchesSearch = app.appName.lowercase().contains(searchQuery) || 
                                      app.packageName.lowercase().contains(searchQuery)
                    
                    // Then apply block status filter
                    val matchesBlockFilter = when (filterMode) {
                        FILTER_BLOCKED -> app.isBlocked
                        FILTER_NOT_BLOCKED -> !app.isBlocked
                        else -> true
                    }
                    
                    matchesSearch && matchesBlockFilter
                }
                
                // Sort with blocked apps at the top, then alphabetically
                val sortedFiltered = filtered.sortedWith(
                    compareByDescending<AppInfo> { it.isBlocked }
                    .thenBy { it.appName.lowercase() }
                )
                
                // Switch back to main thread to update LiveData
                withContext(Dispatchers.Main) {
                    _filteredApps.value = sortedFiltered
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error filtering apps: ${e.message}", e)
            }
        }
    }
    
    fun setShowSystemApps(show: Boolean) {
        if (showSystemApps != show) {
            showSystemApps = show
            loadApps()
        }
    }
    
    fun setFilterMode(mode: Int) {
        if (filterMode != mode) {
            filterMode = mode
            filterApps(searchQuery)
        }
    }
    
    private fun isLauncherPackage(packageName: String): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(launcherIntent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    fun blockApp(app: AppInfo) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Blocking app: ${app.packageName}")
                
                // Update app in memory
                app.isBlocked = true
                
                // Add to blocked apps with isActive=true
                repository.insertBlockedApp(
                    BlockedAppEntity(
                        packageName = app.packageName,
                        appName = app.appName,
                        isActive = true
                    )
                )
                
                // Update the filtered list
                _apps.value = _apps.value?.map { 
                    if (it.packageName == app.packageName) app else it 
                }
                filterApps(searchQuery)
                
                Log.d(TAG, "Successfully blocked app: ${app.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking app: ${e.message}", e)
            }
        }
    }
    
    fun unblockApp(app: AppInfo) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Unblocking app: ${app.packageName}")
                
                // Update app in memory
                app.isBlocked = false
                
                // Update in database
                repository.updateBlockedAppStatus(app.packageName, false)
                
                // Update the filtered list
                _apps.value = _apps.value?.map { 
                    if (it.packageName == app.packageName) app else it 
                }
                filterApps(searchQuery)
                
                Log.d(TAG, "Successfully unblocked app: ${app.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error unblocking app: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update the blocked status of an app in the UI
     */
    fun updateBlockedAppStatus(packageName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            try {
                val currentApps = _apps.value ?: return@launch
                val updatedApps = currentApps.map { app ->
                    if (app.packageName == packageName) {
                        app.isBlocked = isBlocked
                    }
                    app
                }
                _apps.value = updatedApps
                filterApps(searchQuery)
                
                Log.d(TAG, "Updated blocked status for $packageName: $isBlocked")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating blocked status: ${e.message}", e)
            }
        }
    }
    
    private fun isPopularApp(packageName: String): Boolean {
        val popularPackages = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b", // WhatsApp Business
            "com.facebook.katana", // Facebook
            "com.facebook.orca", // Messenger
            "com.facebook.mlite", // Messenger Lite
            "com.facebook.lite", // Facebook Lite
            "com.facebook.android", // Alternative Facebook package
            "com.pinterest",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.spotify.music",
            "com.netflix.mediaclient",
            "com.amazon.mShop.android.shopping",
            "com.google.android.youtube",
            "com.google.android.gm", // Gmail
            "com.google.android.apps.photos",
            "com.linkedin.android",
            "com.discord",
            "org.telegram.messenger",
            "com.viber.voip",
            "com.skype.raider",
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill", // TikTok in some regions
            "com.reddit.frontpage",
            "com.tumblr"
        )
        
        return packageName in popularPackages
    }
} 