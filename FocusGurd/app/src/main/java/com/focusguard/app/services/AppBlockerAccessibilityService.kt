package com.focusguard.app.services

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.focusguard.app.MyApplication
import com.focusguard.app.MainActivity
import com.focusguard.app.R
import com.focusguard.app.data.repository.AppBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Accessibility service that monitors and blocks apps
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val TAG = "AppBlockerService"
    private lateinit var windowManager: WindowManager
    private lateinit var packageManager: PackageManager
    private lateinit var appBlockRepository: AppBlockRepository
    private lateinit var socialMediaPrefs: SharedPreferences
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var currentForegroundApp: String? = null
    private var blockedApps: Set<String> = setOf()
    private var blockingActive = false
    private var blockingView: View? = null
    private var temporarilyUnblockedApp: String? = null
    private var lastBlockedApp: String? = null
    private var lastBlockCheckTime: Long = 0
    private var lastHideTime = 0L
    private var lastBlockEvent: Long = 0
    private val isProcessingEvent = AtomicBoolean(false)
    private var currentBlockedPackage: String? = null
    private var blackScreenView: android.view.View? = null
    private val KNOWN_LAUNCHERS = listOf(
        "com.motorola.launcher3",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.microsoft.launcher",
        "com.teslacoilsw.launcher", // Nova Launcher
        "com.lge.launcher2",
        "com.huawei.android.launcher"
    )

    private var isServiceActive = false
    private var persistentCheckJob: Job? = null
    private var serviceHeartbeatJob: Job? = null

    // Add these variables to track state better
    private var isUiManipulationInProgress = AtomicBoolean(false)
    private val MIN_TIME_BETWEEN_BLOCKS = 1500L // 1.5 seconds
    
    // Cache for checking launcher packages
    private val launcherPackagesCache = mutableSetOf<String>()

    // Broadcast receiver for force unblock actions
    private val forceUnblockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra("package_name")
            val duration = intent.getLongExtra("duration_ms", 30000)
            
            if (packageName != null) {
                temporarilyUnblockApp(packageName, duration)
            }
        }
    }
    
    // Broadcast receiver for reloading blocked apps list
    private val reloadBlockedAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast to reload blocked apps")
            serviceScope.launch {
                updateBlockedAppsList(immediate = true)
                // Check if current app should be blocked after reload
                currentForegroundApp?.let { packageName ->
                    if (shouldBlockApp(packageName)) {
                        Log.d(TAG, "Blocking current app after reload: $packageName")
                        withContext(Dispatchers.Main) {
                            showBlockingScreen(packageName)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            packageManager = applicationContext.packageManager
            appBlockRepository = MyApplication.appBlockRepository
            socialMediaPrefs = applicationContext.getSharedPreferences("social_media_settings", Context.MODE_PRIVATE)
            
            // Initialize wake lock to keep service running
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyApplication:AppBlockerWakeLock"
            )
            
            // Register broadcast receiver for force unblock
            registerForceUnblockReceiver()
            
            // Start foreground service with minimal notification visibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use the new API for Android 12+
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                // Use the old API for Android 11 and below
                startForeground(NOTIFICATION_ID, createNotification())
            }
            
            // Observe blocked apps list changes
            observeBlockedApps()
            
            // Initialize launcher packages cache
            initLauncherPackagesCache()
            
            // Start service heartbeat to prevent being killed
            startServiceHeartbeat()
            
            // Create work directory for logs
            createWorkDirectory()
            
            // Write diagnostic log that service started
            writeLog("Service onCreate called")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            writeLog("ERROR in onCreate: ${e.message}")
        }
    }

    private fun createWorkDirectory() {
        try {
            val workDir = File(applicationContext.filesDir, "app_blocker_logs")
            if (!workDir.exists()) {
                workDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating work directory: ${e.message}", e)
        }
    }
    
    private fun writeLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val logEntry = "$timestamp: $message\n"
            
            val logDir = File(applicationContext.filesDir, "app_blocker_logs")
            val logFile = File(logDir, "app_blocker_log.txt")
            
            // Create parent directories if they don't exist
            logDir.mkdirs()
            
            // Append to log file
            FileOutputStream(logFile, true).use { output ->
                output.write(logEntry.toByteArray())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to log file: ${e.message}", e)
        }
    }
    
    private fun startServiceHeartbeat() {
        if (serviceHeartbeatJob != null) return
        
        serviceHeartbeatJob = serviceScope.launch {
            while (isServiceActive) {
                try {
                    // Acquire wake lock for short period
                    if (!wakeLock.isHeld) {
                        wakeLock.acquire(30000) // 30 seconds max
                    }
                    
                    // Update the notification silently - keep it minimal
                    val updatedNotification = createNotification()
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    
                    // Check if we should update our blocked apps list
                    if (System.currentTimeMillis() - lastBlockCheckTime > 30000) {
                        // Update blocked apps list without updating notification visibility
                        updateBlockedAppsList(immediate = true)
                        lastBlockCheckTime = System.currentTimeMillis()
                    }
                    
                    // Release wake lock if held
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in service heartbeat: ${e.message}", e)
                    writeLog("ERROR in service heartbeat: ${e.message}")
                }
                
                delay(30000) // Run every 30 seconds
            }
        }
    }

    private fun initLauncherPackagesCache() {
        try {
            // Add known launchers
            launcherPackagesCache.addAll(KNOWN_LAUNCHERS)
            
            // Find default launcher
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName?.let {
                launcherPackagesCache.add(it)
                Log.d(TAG, "Added default launcher to cache: $it")
            }
            
            // Find all apps that can handle home intent
            val launcherIntents = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in launcherIntents) {
                info.activityInfo.packageName?.let {
                    launcherPackagesCache.add(it)
                    Log.d(TAG, "Added launcher to cache: $it")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing launcher packages cache: ${e.message}", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        writeLog("Service connected")
        
        try {
            // Always set service as active
            isServiceActive = true
            
            // Always keep blockingActive true to ensure direct blocking works
            blockingActive = true
            
            // Refresh the blocked apps list immediately
            serviceScope.launch {
                // Update the blocked apps list
                updateBlockedAppsList(immediate = true)
                
                // Check if current app should be blocked
                currentForegroundApp?.let { packageName ->
                    if (shouldBlockApp(packageName)) {
                        Log.d(TAG, "Blocking current app on service start: $packageName")
                        writeLog("Blocking current app on service start: $packageName")
                        showBlockingScreen(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
            writeLog("ERROR in onServiceConnected: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Capture the event time as early as possible
        val eventTime = System.currentTimeMillis()
        
        // Only process if we're not already processing an event to prevent race conditions
        if (isProcessingEvent.getAndSet(true)) {
            isProcessingEvent.set(false) // Release lock immediately if skipped
            return
        }

        try {
            val eventType = event.eventType
            val sourceNode = event.source // Use event source if available for content changes

            // --- Handle Window State Changes (App Switches) ---
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = event.packageName?.toString()
                val className = event.className?.toString() ?: ""
                
                // Skip processing for certain system UI elements, overlays, or our own app
                if (packageName == null ||
                    (packageName.startsWith("android") && (className.contains("Toast") || className.contains("PopupWindow"))) ||
                    packageName == "com.android.systemui" ||
                    className.contains("BlockingActivity") ||
                    packageName == applicationContext.packageName) {
                    isProcessingEvent.set(false)
                    return
                }
                
                // --- App Switch Detected ---
                if (packageName != currentForegroundApp) {
                    Log.d(TAG, "WINDOW_STATE_CHANGED: New foreground app: $packageName (Class: $className)")
                    writeLog("App switch: $packageName ($className)")
                    
                    val previousApp = currentForegroundApp
                    currentForegroundApp = packageName
                    
                    // --- Immediate Blocking Check ---
                    // If the new app is explicitly in the blocked list, block it without delay.
                    if (blockedApps.contains(packageName)) {
                        Log.i(TAG, "DIRECT BLOCK (State Change): App $packageName is in blocked list. Checking schedule...")
                        // Before blocking, verify it should be blocked right now based on schedule
                        val shouldBlock = runBlocking {
                            appBlockRepository.shouldBlockAppNow(packageName)
                        }
                        
                        if (shouldBlock) {
                            Log.i(TAG, "CONFIRMED BLOCK: App $packageName is in schedule and should be blocked now")
                            writeLog("BLOCK: Direct block for $packageName (in blocked list and in schedule)")
                            applyImmediateForceBlock(packageName)
                        } else {
                            Log.d(TAG, "NOT BLOCKING: App $packageName is in blocked list but outside schedule")
                            writeLog("NO BLOCK: App $packageName is outside blocking schedule")
                            // If blocking screen exists but we're outside schedule, hide it
                            if (blockingView != null && currentBlockedPackage == packageName) {
                                hideBlockingScreen()
                            }
                        }
                        
                        // Don't proceed further for this app
                        isProcessingEvent.set(false)
                        return 
                    }
                    
                    // --- General Blocking Check ---
                    // Check if the new app should be blocked
                    if (shouldBlockApp(packageName)) {
                        Log.i(TAG, "BLOCK (State Change): App $packageName should be blocked. Applying force block.")
                        writeLog("BLOCK: App $packageName should be blocked")
                        applyImmediateForceBlock(packageName)
                    } else {
                        // --- Unblocking / Hiding Screen ---
                        Log.d(TAG, "NO BLOCK (State Change): App $packageName should not be blocked.")
                        // Hide blocking screen only if it was shown for the *previous* app,
                        // and the new app is definitely not blocked.
                        if (blockingView != null && previousApp != null && blockedApps.contains(previousApp)) {
                           Log.d(TAG, "Hiding blocking screen as we moved from blocked app $previousApp to unblocked $packageName")
                           writeLog("UNBLOCK: Hiding blocking screen (moved from $previousApp to $packageName)")
                           hideBlockingScreen()
                        } else {
                            // Check if we should hide existing blocking screen
                            val shouldHideBlockingScreen = blockingView != null && currentBlockedPackage == packageName
                            if (shouldHideBlockingScreen) {
                                Log.d(TAG, "Current app $packageName is no longer blocked, hiding screen")
                                // Run on main thread through Handler instead of using withContext
                                Handler(Looper.getMainLooper()).post {
                                    hideBlockingScreen()
                                }
                            }
                        }
                    }
                    
                    // Update list asynchronously
                    serviceScope.launch { updateBlockedAppsList(immediate = false) }
                } else {
                     // App didn't change, but window state did. Might still need to check features.
                     Log.d(TAG, "WINDOW_STATE_CHANGED: Same app ($packageName), window state changed.")
                     
                     // Even if it's the same app, check if it should be blocked
                     // This handles cases where the app was added to the block list while running
                     if (blockedApps.contains(packageName) && blockingView == null) {
                         Log.i(TAG, "BLOCK (Window Change): App $packageName found in blocked list during window change.")
                         writeLog("BLOCK: Window change triggered block for $packageName")
                         applyImmediateForceBlock(packageName)
                     }
                }
            }
            // --- Handle Content Changes (Within an App) ---
            else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                 val packageName = event.packageName?.toString()

                 // Process content changes for all apps, not just social media
                 // This helps catch when a blocked app manages to appear
                 if (packageName != null) {
                    // Check if this app should be blocked
                    if (blockedApps.contains(packageName) && blockingView == null) {
                        Log.i(TAG, "BLOCK (Content Change): App $packageName found in blocked list during content change.")
                        writeLog("BLOCK: Content change triggered block for $packageName")
                        applyImmediateForceBlock(packageName)
                    }
                 }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent (Type: ${event.eventType}): ${e.message}", e)
            writeLog("ERROR in onAccessibilityEvent: ${e.message}")
        } finally {
            // Always reset the processing flag
            isProcessingEvent.set(false)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        writeLog("Service interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        writeLog("Service onDestroy - attempting restart")
        
        try {
            // Unregister the force unblock receiver
            try {
                unregisterReceiver(forceUnblockReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering force unblock receiver: ${e.message}")
            }
            
            // Cancel jobs
            persistentCheckJob?.cancel()
            serviceHeartbeatJob?.cancel()
            
            // Release wake lock if held
            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
            }
            
            hideBlockingScreen()
            
            // Immediately try to restart the service via a broadcast
            val intent = Intent("com.focusguard.app.RESTART_APP_BLOCKER")
            applicationContext.sendBroadcast(intent)
            
            // Set flags to allow service to be restarted
            blockingActive = false
            isServiceActive = false
            
            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
            writeLog("ERROR in onDestroy: ${e.message}")
            super.onDestroy()
        }
    }

    private fun observeBlockedApps() {
        serviceScope.launch {
            try {
                updateBlockedAppsList(immediate = false)
                
                // Poll for updates more frequently
                delay(30000) // 30 seconds
                observeBlockedApps()
            } catch (e: Exception) {
                Log.e(TAG, "Error observing blocked apps: ${e.message}", e)
                writeLog("ERROR observing blocked apps: ${e.message}")
                // Try again after a short delay if there was an error
                delay(5000)
                observeBlockedApps()
            }
        }
    }

    private suspend fun updateBlockedAppsList(immediate: Boolean) {
        try {
            // Track if there are changes
            var hasChanges = false
            
            // Force refresh the repository cache
            appBlockRepository.refreshCache()
            
            // Now get the active apps
            val activeBlockedApps = withContext(Dispatchers.IO) {
                appBlockRepository.getActiveBlockedApps().map { it.packageName }.toSet()
            }
            
            // Check for differences
            if (activeBlockedApps != blockedApps) {
                Log.d(TAG, "Blocked apps list changed. Old: ${blockedApps.size}, New: ${activeBlockedApps.size}")
                writeLog("Blocked apps list changed. Old: ${blockedApps.size}, New: ${activeBlockedApps.size}")
                
                // Calculate added and removed apps for better logging
                val added = activeBlockedApps.minus(blockedApps)
                val removed = blockedApps.minus(activeBlockedApps)
                
                if (added.isNotEmpty()) {
                    Log.d(TAG, "Added apps: $added")
                    writeLog("Added to blocked list: $added")
                }
                
                if (removed.isNotEmpty()) {
                    Log.d(TAG, "Removed apps: $removed")
                    writeLog("Removed from blocked list: $removed")
                }
                
                // Update the set
                blockedApps = activeBlockedApps
                hasChanges = true
            }
            
            // Process changes if any, or if immediate update requested
            if (hasChanges || immediate) {
                Log.d(TAG, "Blocked apps list changed or immediate update requested, checking current app")
                
                // If the current app is now in the blocked list, show blocking screen
                currentForegroundApp?.let { appPackage ->
                    if (blockedApps.contains(appPackage)) {
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "Current app $appPackage is in blocked list, showing block screen")
                            // Don't show immediately if we recently hid
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastHideTime > MIN_TIME_BETWEEN_BLOCKS) {
                                applyImmediateForceBlock(appPackage)
                            } else {
                                Log.d(TAG, "Skipping immediate blocking due to recent hide")
                            }
                        }
                    } else {
                        // Check if we should hide existing blocking screen
                        val shouldHideBlockingScreen = blockingView != null && currentBlockedPackage == appPackage
                        if (shouldHideBlockingScreen) {
                            Log.d(TAG, "Current app $appPackage is no longer blocked, hiding screen")
                            // Run on main thread through Handler instead of using withContext
                            Handler(Looper.getMainLooper()).post {
                                hideBlockingScreen()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocked apps list: ${e.message}", e)
            writeLog("ERROR updating blocked apps list: ${e.message}")
        }
    }

    private fun shouldBlockApp(packageName: String): Boolean {
        // Skip checks for null package or empty package
        if (packageName.isEmpty()) return false
        
        // Don't block our own app
        if (packageName == applicationContext.packageName) return false
        
        // Don't block launchers - use cache for faster checking
        if (launcherPackagesCache.contains(packageName) || isLauncherPackage(packageName)) {
            return false
        }
        
        // First, check if the app is in our blocked list
        if (!blockedApps.contains(packageName)) {
            Log.d(TAG, "shouldBlockApp: NO - $packageName is not in blocked list")
            return false
        }
        
        // If this app is temporarily unblocked, don't block it
        if (temporarilyUnblockedApp == packageName) {
            Log.d(TAG, "shouldBlockApp: NO - $packageName is temporarily unblocked")
            return false
        }
        
        // If it's in the list, verify it should be blocked right now
        try {
            // Do this check synchronously to avoid race conditions
            val blocked = runBlocking {
                appBlockRepository.shouldBlockAppNow(packageName)
            }
            
            if (!blocked) {
                Log.d(TAG, "shouldBlockApp: NO - $packageName is in blocked list but outside schedule")
                return false
            }
            
            Log.d(TAG, "shouldBlockApp: YES - $packageName is in blocked list and active")
            return true
        } catch (e: Exception) {
            // If there's an error checking, log it and don't block
            Log.e(TAG, "Error checking if app should be blocked: ${e.message}")
            return false
        }
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        // Quick check against known launcher packages
        if (launcherPackagesCache.contains(packageName)) {
            return true
        }
        
        // More thorough check using package manager
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (resolveInfo?.activityInfo?.packageName == packageName) {
                Log.d(TAG, "Detected launcher package: $packageName")
                // Add to cache for future checks
                launcherPackagesCache.add(packageName)
                return true
            }
            
            // Additional check for system packages
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                // Check if this system app can handle home intent
                val launcherIntents = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (info in launcherIntents) {
                    if (info.activityInfo.packageName == packageName) {
                        Log.d(TAG, "Detected system launcher package: $packageName")
                        // Add to cache for future checks
                        launcherPackagesCache.add(packageName)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if $packageName is a launcher: ${e.message}", e)
        }
        
        return false
    }
    
    private fun logNodeDetails(node: AccessibilityNodeInfo?, packageName: String = "") {
        // Implementation of the function with correct parameter signature
        // Simplified to reduce overhead
        if (node == null) return
        
        try {
            // Just log the basic info to save performance
            Log.v(TAG, "Node for $packageName: class=${node.className}, text=${node.text}, viewId=${node.viewIdResourceName}")
        } catch (e: Exception) {
            // Ignore errors, this is just logging
        }
    }
    
    // Implement detectBlockedSocialMediaFeature function with the logic from the code you reviewed
    private fun detectBlockedSocialMediaFeature(packageName: String, rootNode: AccessibilityNodeInfo): String? {
        // Get this from the existing implementation
        return null // Placeholder, replace with actual implementation
    }
    
    // Helper function for feature detection
    private fun checkNodeTreeForIdsOrText(
        rootNode: AccessibilityNodeInfo?, 
        ids: List<String>, 
        textPatterns: List<String>, 
        excludePatterns: List<String>,
        logDetails: Boolean
    ): Boolean {
        // Get this from the existing implementation
        return false // Placeholder, replace with actual implementation
    }

    private fun registerForceUnblockReceiver() {
        try {
            val filter = IntentFilter("com.focusguard.app.ACTION_FORCE_UNBLOCK")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(forceUnblockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(forceUnblockReceiver, filter)
            }
            Log.d(TAG, "Registered force unblock receiver")
            
            // Register the reload blocked apps receiver
            val reloadFilter = IntentFilter("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(reloadBlockedAppsReceiver, reloadFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(reloadBlockedAppsReceiver, reloadFilter)
            }
            Log.d(TAG, "Registered reload blocked apps receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receivers: ${e.message}", e)
        }
    }

    private fun temporarilyUnblockApp(packageName: String, durationMs: Long = 30000) {
        Log.d(TAG, "Temporarily unblocking $packageName for ${durationMs}ms")
        writeLog("Temporarily unblocking $packageName for ${durationMs}ms")
        
        // If we're currently blocking this app, hide the blocking screen
        if (packageName == currentBlockedPackage && blockingView != null) {
            hideBlockingScreen()
        }
        
        // Mark it as temporarily unblocked
        temporarilyUnblockedApp = packageName
        
        // Schedule re-block after duration
        Handler(Looper.getMainLooper()).postDelayed({
            temporarilyUnblockedApp = null
            Log.d(TAG, "Temporary unblock period ended for $packageName")
            writeLog("Temporary unblock period ended for $packageName")
            
            // Check if app needs to be re-blocked if it's still in foreground
            if (currentForegroundApp == packageName && shouldBlockApp(packageName)) {
                applyImmediateForceBlock(packageName)
            }
        }, durationMs)
    }

    private fun showBlockingScreen(packageName: String) {
        // If blocking view already exists, no need to create another one
        if (blockingView != null) {
            Log.d(TAG, "Blocking view already exists, not creating a new one")
            
            // Ensure the existing view is on top by updating its layout
            try {
                windowManager.updateViewLayout(blockingView, blockingView!!.layoutParams)
                Log.d(TAG, "Updated existing blocking view to ensure it's on top")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating existing blocking view", e)
                writeLog("ERROR updating existing blocking view: ${e.message}")
            }
            return
        }

        // Set flag to prevent race conditions
        if (isProcessingEvent.getAndSet(true)) {
            Log.d(TAG, "Already processing an event, not showing blocking screen")
            isProcessingEvent.set(false)
            return
        }

        try {
            // Get app name
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting app name: ${e.message}")
                packageName
            }
            
            // Set the current blocked package
            currentBlockedPackage = packageName
            lastBlockedApp = packageName
            blockingActive = true
            
            Log.d(TAG, "Showing blocking screen for $appName ($packageName)")
            writeLog("Showing blocking screen for $appName ($packageName)")
            
            // Create the blocking view
            val inflater = LayoutInflater.from(this)
            blockingView = inflater.inflate(R.layout.view_app_blocked, null)
            
            // Setup blocking view elements
            setupBlockingView(blockingView!!, appName, packageName)
            
            // Apply window parameters with improved flags for better blocking
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.CENTER
            
            // Add the view with improved parameters
            try {
                windowManager.addView(blockingView, params)
                Log.d(TAG, "Added blocking view for $packageName")
                
                // Don't automatically force to home screen - let user click button
                // Removed: performGlobalAction(GLOBAL_ACTION_HOME)
                
                // Start persistent blocking check for this app
                setupPersistentBlockingCheck(packageName)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding blocking view: ${e.message}", e)
                writeLog("ERROR adding blocking view: ${e.message}")
                blockingView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing blocking screen: ${e.message}", e)
            writeLog("ERROR showing blocking screen: ${e.message}")
        } finally {
            isProcessingEvent.set(false)
        }
    }
    
    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
    }
    
    private fun setupBlockingView(view: View, appName: String, packageName: String) {
        try {
            // Find the title, description and button views
            val titleText = view.findViewById<TextView>(R.id.blockingTitle)
            val descriptionText = view.findViewById<TextView>(R.id.blockingDescription)
            val timeRemainingText = view.findViewById<TextView>(R.id.timeRemaining)
            val closeButton = view.findViewById<Button>(R.id.btnClose)
            
            if (titleText == null || descriptionText == null || timeRemainingText == null || closeButton == null) {
                Log.e(TAG, "One or more views not found in blocking layout")
                writeLog("ERROR: Missing views in blocking layout")
                return
            }
            
            // Set text for app block
            titleText.text = "$appName Blocked"
            descriptionText.text = "This app is currently blocked according to your settings."
            
            // Show the schedule information instead of hiding it
            timeRemainingText.visibility = View.VISIBLE
            
            // Launch coroutine to fetch and display schedule info
            serviceScope.launch {
                try {
                    val blockedApp = appBlockRepository.getBlockedApp(packageName)
                    
                    withContext(Dispatchers.Main) {
                        if (blockedApp != null) {
                            if (blockedApp.blockAllDay) {
                                timeRemainingText.text = "Blocked all day"
                            } else if (blockedApp.startTime != null && blockedApp.endTime != null) {
                                // Format the times
                                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                val outputFormatter = DateTimeFormatter.ofPattern("h:mma")
                                
                                try {
                                    val start = LocalTime.parse(blockedApp.startTime, formatter)
                                    val end = LocalTime.parse(blockedApp.endTime, formatter)
                                    
                                    val formattedStart = start.format(outputFormatter).toLowerCase()
                                    val formattedEnd = end.format(outputFormatter).toLowerCase()
                                    
                                    // Show the schedule
                                    timeRemainingText.text = "Blocked from $formattedStart to $formattedEnd"
                                } catch (e: Exception) {
                                    // Fallback if parsing fails
                                    timeRemainingText.text = "Blocked from ${blockedApp.startTime} to ${blockedApp.endTime}"
                                }
                            } else {
                                timeRemainingText.visibility = View.GONE
                            }
                        } else {
                            timeRemainingText.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading block schedule: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        timeRemainingText.visibility = View.GONE
                    }
                }
            }
            
            // Set up close button - only one action when clicked
            closeButton.setOnClickListener {
                // Go to home screen and hide the blocking screen when button is clicked
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideBlockingScreen()
                
                // Show confirmation toast
                Toast.makeText(
                    applicationContext,
                    "App blocked. Returned to home screen.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up blocking view: ${e.message}", e)
            writeLog("ERROR setting up blocking view: ${e.message}")
        }
    }
    
    private fun hideBlockingScreen() {
        // Keep track of when we last hid a blocking screen
        lastHideTime = System.currentTimeMillis()
        
        if (blockingView != null) {
            try {
                windowManager.removeView(blockingView)
                Log.d(TAG, "Removed blocking view")
                writeLog("Removed blocking view")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing blocking view: ${e.message}", e)
                writeLog("ERROR removing blocking view: ${e.message}")
            } finally {
                blockingView = null
                currentBlockedPackage = null
            }
        }
    }
    
    private fun hideBlockingScreenAfterDelay(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            hideBlockingScreen()
        }, delayMs)
    }

    private fun setupPersistentBlockingCheck(packageName: String) {
        // Cancel existing job if any
        persistentCheckJob?.cancel()
        
        // Start a new job
        persistentCheckJob = serviceScope.launch {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Run a persistent check every 300ms to make sure the blocked app stays blocked
            while (blockingActive && currentBlockedPackage == packageName) {
                try {
                    // IMPORTANT: First check if the app should still be blocked according to time schedule
                    val shouldStillBlock = runBlocking {
                        appBlockRepository.shouldBlockAppNow(packageName)
                    }
                    
                    if (!shouldStillBlock) {
                        Log.d(TAG, "Schedule check: App $packageName no longer needs to be blocked - outside schedule")
                        // We're outside the blocking schedule now, so hide the blocking screen
                        withContext(Dispatchers.Main) {
                            hideBlockingScreen()
                        }
                        break // Exit the loop since we no longer need to block
                    }
                    
                    // Double-check current foreground app
                    val currentForegroundPackage = currentForegroundApp
                    
                    // If our current foreground app matches what we're trying to block, take action
                    if (currentForegroundPackage == packageName) {
                        Log.d(TAG, "Persistent check: Blocked app $packageName is in foreground")
                        // Don't automatically go to HOME - let user click button
                        // Removed: performGlobalAction(GLOBAL_ACTION_HOME)
                        
                        // Ensure blocking view is visible
                        if (blockingView == null) {
                            showBlockingScreen(packageName)
                        }
                        
                        // Try to force close the app if it somehow got into the foreground
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            try {
                                activityManager.killBackgroundProcesses(packageName)
                                Log.d(TAG, "Killed background processes for $packageName")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to kill processes: ${e.message}", e)
                            }
                        }
                    }
                    
                    delay(300) // Check more frequently
                } catch (e: Exception) {
                    Log.e(TAG, "Error in persistent blocking check: ${e.message}", e)
                    writeLog("ERROR in persistent blocking check: ${e.message}")
                    delay(1000) // Longer delay on error
                }
            }
        }
    }

    private fun createNotification(): Notification {
        // Create a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "",  // Empty name to minimize visibility
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = ""  // Empty description
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create a basic notification for the foreground service
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")  // Empty title
            .setContentText("")  // Empty text
            .setSmallIcon(android.R.drawable.ic_lock_lock)  // Use a system icon instead
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // Hide from lock screen
            .setShowWhen(false)  // Don't show timestamp
            .setSilent(true)  // Silent notification
            .setOngoing(true)  // Required for foreground service
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun applyImmediateForceBlock(packageName: String) {
        Log.d(TAG, "Applying immediate force block for $packageName")
        
        // Check if enough time has passed since the last block
        val now = System.currentTimeMillis()
        if (now - lastBlockEvent < MIN_TIME_BETWEEN_BLOCKS) {
            Log.d(TAG, "Skipping immediate block for $packageName - too soon since last block")
            return
        }
        
        // CRITICAL: First verify if the app should be blocked at the current time
        serviceScope.launch {
            // Check if the app should be blocked now according to schedule
            val shouldBlock = runBlocking {
                appBlockRepository.shouldBlockAppNow(packageName)
            }
            
            if (!shouldBlock) {
                Log.d(TAG, "Skipping block for $packageName - not within blocking schedule")
                // Hide blocking screen if it exists for this app
                if (blockingView != null && packageName == currentBlockedPackage) {
                    withContext(Dispatchers.Main) {
                        hideBlockingScreen()
                    }
                }
                return@launch
            }
            
            Log.d(TAG, "Confirmed $packageName should be blocked now based on schedule")
            
            // Proceed with blocking since it's within scheduled time
            withContext(Dispatchers.Main) {
                // Ensure we're actively blocking
                blockingActive = true
                
                // Only block if not already showing blocking screen for this app
                if (blockingView != null && packageName == currentBlockedPackage) {
                    Log.d(TAG, "Blocking screen already showing for $packageName")
                    return@withContext
                }
                
                // Show the blocking screen for this app
                try {
                    // Show the blocking screen
                    showBlockingScreen(packageName)
                    lastBlockEvent = now
                    
                    // Make sure persistent check is running to continuously block
                    setupPersistentBlockingCheck(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing blocking screen for $packageName: ${e.message}", e)
                    writeLog("ERROR showing blocking screen for $packageName: ${e.message}")
                }
            }
        }
    }
    
    private fun setStartupCooldown(packageName: String) {
        // This function is removed as per the instructions
    }

    companion object {
        private const val NOTIFICATION_ID = 923457
        private const val CHANNEL_ID = "app_blocker_channel"
        private const val CHECK_INTERVAL_MS = 60000L // 1 minute
    }
} 