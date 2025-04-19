package com.focusguard.app.services

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.focusguard.app.MainActivity
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * DEPRECATED: This class is no longer used and has been replaced by AppBlockerAccessibilityService.
 * Keeping this class as a placeholder to avoid breaking any references,
 * but it is disabled and not registered in the manifest.
 */
@Deprecated("This service is deprecated and replaced by AppBlockerAccessibilityService")
class AppBlockerService : AccessibilityService() {

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "app_blocker_channel"
        private const val CHECK_INTERVAL_MS = 15000L // 15 seconds
        private const val TAG = "AppBlockerService"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var packageManager: PackageManager
    private lateinit var appBlockRepository: AppBlockRepository
    private lateinit var wakeLock: PowerManager.WakeLock
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var currentForegroundApp: String? = null
    private var blockedApps: Set<String> = setOf()
    private var blockingView: View? = null
    private var currentBlockedPackage: String? = null
    
    // For minimizing repeated blocking operations
    private var lastBlockCheckTime: Long = 0
    private val isProcessingEvent = AtomicBoolean(false)
    
    // For temp unblocking
    private var temporarilyUnblockedApp: String? = null
    
    // Variables for blocking
    private var lastBlockedApp: String? = null
    private var blockingActive = false
    
    // Launcher packages to avoid blocking
    private val launcherPackages = mutableSetOf<String>()
    
    // Service state
    private var isServiceActive = false
    
    // Broadcast receiver for reload actions
    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received reload broadcast")
            serviceScope.launch {
                updateBlockedAppsList(true)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.w(TAG, "WARNING: Deprecated service onCreate called. This service should not be used.")
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            packageManager = applicationContext.packageManager
            appBlockRepository = MyApplication.appBlockRepository
            
            // Initialize wake lock to keep service running
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyApplication:AppBlockerWakeLock"
            )
            
            // Register broadcast receiver
            registerReceiver(reloadReceiver, IntentFilter("com.focusguard.app.ACTION_RELOAD_BLOCKS"))
            
            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Initialize launcher packages
            initLauncherPackages()
            
            // Mark service as active
            isServiceActive = true
            
            // Start periodic checks
            startPeriodicChecks()
            
            writeLog("Service created")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            writeLog("ERROR in onCreate: ${e.message}")
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.w(TAG, "WARNING: Deprecated service onServiceConnected called. This service should not be used.")
        
        serviceScope.launch {
            try {
                // Update the blocked apps list
                updateBlockedAppsList(immediate = true)
                
                // Check if current app should be blocked
                currentForegroundApp?.let { packageName ->
                    if (shouldBlockApp(packageName)) {
                        Log.d(TAG, "Blocking current app on service start: $packageName")
                        withContext(Dispatchers.Main) {
                            showBlockingScreen(packageName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Disabled - do nothing
    }
    
    override fun onInterrupt() {
        // Disabled - do nothing
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy called")
        
        try {
            // Clean up resources
            hideBlockingScreen()
            
            // Unregister receiver
            try {
                unregisterReceiver(reloadReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
            
            // Release wake lock if held
            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
            }
            
            // Mark service as inactive
            isServiceActive = false
            
            writeLog("Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }
    
    /**
     * Update the list of blocked apps from the repository
     */
    private suspend fun updateBlockedAppsList(immediate: Boolean = false) {
        try {
            // Limit how often we refresh the list
            val now = System.currentTimeMillis()
            if (!immediate && now - lastBlockCheckTime < CHECK_INTERVAL_MS) {
                return
            }
            
            lastBlockCheckTime = now
            
            // Refresh repository cache
            appBlockRepository.refreshCache()
            
            // Get active blocked apps
            val activeApps = appBlockRepository.getActiveBlockedApps()
            
            // Update our blocked apps set
            blockedApps = activeApps.map { it.packageName }.toSet()
            
            Log.d(TAG, "Updated blocked apps list: ${blockedApps.size} apps")
            
            // Update notification to show current status
            val updatedNotification = createNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocked apps list: ${e.message}", e)
        }
    }
    
    /**
     * Determine if an app should be blocked
     */
    private fun shouldBlockApp(packageName: String): Boolean {
        // Skip checks for empty package
        if (packageName.isEmpty()) return false
        
        // Don't block our own app
        if (packageName == applicationContext.packageName) return false
        
        // Don't block launchers
        if (launcherPackages.contains(packageName)) return false
        
        // Check for temporary unblock
        if (packageName == temporarilyUnblockedApp) {
            Log.d(TAG, "App $packageName is temporarily unblocked")
            return false
        }
        
        // Check if in blocked list
        if (!blockedApps.contains(packageName)) {
            return false
        }
        
        // Check scheduling - run blocking to avoid async issues
        return runBlocking {
            appBlockRepository.shouldBlockAppNow(packageName)
        }
    }
    
    /**
     * Display blocking overlay
     */
    private fun showBlockingScreen(packageName: String) {
        try {
            // If we already have a blocking view for this package, just make sure it's working
            if (blockingView != null && currentBlockedPackage == packageName) {
                // Force return to home screen to prevent app access
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            
            // Clean up any existing blocking view
            hideBlockingScreen()
            
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
            
            // Apply window parameters with improved flags for EFFECTIVE blocking
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE,
                PixelFormat.OPAQUE
            )
            params.alpha = 1.0f
            params.screenBrightness = 1.0f
            
            // Add view to window
            windowManager.addView(blockingView, params)
            
            // Force return to home screen to prevent using the blocked app
            performGlobalAction(GLOBAL_ACTION_HOME)
            performGlobalAction(GLOBAL_ACTION_BACK)
            
            // Start a more aggressive monitoring to ensure app remains blocked
            startStrictBlockingMonitor(packageName)
            
            Log.d(TAG, "Blocking screen shown for $packageName")
            writeLog("Blocking screen shown for $packageName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing blocking screen: ${e.message}", e)
            writeLog("ERROR showing blocking screen: ${e.message}")
        }
    }
    
    /**
     * Setup the blocking view elements
     */
    private fun setupBlockingView(view: View, appName: String, packageName: String) {
        try {
            // Set app name in the view
            // view.findViewById<TextView>(R.id.blocked_app_name).text = appName
            
            // Get app info
            val appInfo = getBlockedAppInfo(packageName)
            
            // Show time remaining if applicable
            /*
            val timeInfoText = view.findViewById<TextView>(R.id.time_info)
            if (appInfo.endTime != null && !appInfo.blockAllDay) {
                timeInfoText.visibility = View.VISIBLE
                timeInfoText.text = "Blocked until ${appInfo.endTime}"
            } else {
                timeInfoText.visibility = View.GONE
            }
            */
            
            // Set up temporary unblock button
            // view.findViewById<Button>(R.id.btn_unblock_temp).setOnClickListener {
            //     temporarilyUnblockApp(packageName)
            // }
            
            // Set up enter password button
            // view.findViewById<Button>(R.id.btn_enter_password).setOnClickListener {
            //     showPasswordDialog(packageName)
            // }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up blocking view: ${e.message}", e)
        }
    }
    
    /**
     * Get the current foreground app
     */
    private fun getCurrentForegroundApp(): String? {
        return currentForegroundApp
    }
    
    /**
     * Get information about a blocked app
     */
    private data class BlockedAppInfo(
        val packageName: String,
        val appName: String,
        val startTime: String?,
        val endTime: String?,
        val blockAllDay: Boolean,
        val password: String?
    )
    
    private fun getBlockedAppInfo(packageName: String): BlockedAppInfo {
        return runBlocking {
            try {
                val blockedApp = appBlockRepository.getBlockedApp(packageName)
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                
                if (blockedApp != null && blockedApp.isActive) {
                    BlockedAppInfo(
                        packageName = blockedApp.packageName,
                        appName = appName,
                        startTime = blockedApp.startTime,
                        endTime = blockedApp.endTime,
                        blockAllDay = blockedApp.blockAllDay,
                        password = blockedApp.password
                    )
                } else {
                    BlockedAppInfo(
                        packageName = packageName,
                        appName = appName,
                        startTime = null,
                        endTime = null,
                        blockAllDay = false,
                        password = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting blocked app info: ${e.message}", e)
                BlockedAppInfo(
                    packageName = packageName,
                    appName = packageName,
                    startTime = null,
                    endTime = null,
                    blockAllDay = false,
                    password = null
                )
            }
        }
    }
    
    /**
     * Show dialog to enter password for unblocking
     */
    private fun showPasswordDialog(packageName: String) {
        val appInfo = getBlockedAppInfo(packageName)
        
        // Create the password dialog view
        val view = LayoutInflater.from(applicationContext).inflate(R.layout.password_entry_dialog, null)
        val passwordEditText = view.findViewById<EditText>(R.id.password_input)
        val titleText = view.findViewById<TextView>(R.id.password_title)
        val submitButton = view.findViewById<android.widget.Button>(R.id.submit_button)
        val cancelButton = view.findViewById<android.widget.Button>(R.id.cancel_button)
        
        titleText.text = "Enter password to unblock ${appInfo.appName}"
        
        // Create window parameters to allow input focus
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        
        // Add the view to the window manager
        windowManager.addView(view, params)
        
        // Request focus for the edit text and show keyboard
        passwordEditText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(passwordEditText, InputMethodManager.SHOW_IMPLICIT)
        
        submitButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            if (password.isNotEmpty()) {
                serviceScope.launch {
                    try {
                        val isPasswordCorrect = appBlockRepository.verifyPassword(packageName, password)
                        
                        withContext(Dispatchers.Main) {
                            // First close dialog
                            try {
                                windowManager.removeView(view)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error removing password dialog: ${e.message}")
                            }
                            
                            if (isPasswordCorrect) {
                                // Password correct, unblock the app
                                Toast.makeText(applicationContext, "Password correct! Unblocking app.", Toast.LENGTH_SHORT).show()
                                
                                // Update the app's status
                                serviceScope.launch {
                                    try {
                                        // Get current app info
                                        val currentApp = appBlockRepository.getBlockedApp(packageName)
                                        
                                        // Create a BlockedApp object with isActive = false
                                        val updatedApp = BlockedAppEntity(
                                            packageName = packageName,
                                            appName = appInfo.appName,
                                            isActive = false,
                                            startTime = currentApp?.startTime,
                                            endTime = currentApp?.endTime,
                                            blockAllDay = currentApp?.blockAllDay ?: false,
                                            enabledDays = currentApp?.enabledDays ?: 127,
                                            password = currentApp?.password
                                        )
                                        
                                        // Update the app's status in the database
                                        appBlockRepository.insertBlockedApp(updatedApp)
                                        
                                        // Broadcast the change
                                        val intent = Intent("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
                                        applicationContext.sendBroadcast(intent)
                                        updateBlockedAppsList(immediate = true)
                                        
                                        // Hide the blocking screen
                                        hideBlockingScreen()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error updating app status: ${e.message}")
                                    }
                                }
                            } else {
                                // Password incorrect
                                Toast.makeText(applicationContext, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show()
                                
                                // Force return to home screen
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                
                                // Then re-show blocking screen
                                showBlockingScreen(packageName)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error verifying password: ${e.message}", e)
                        Toast.makeText(applicationContext, "Error verifying password: ${e.message}", Toast.LENGTH_SHORT).show()
                        
                        // Remove view and return to home on error
                        try {
                            windowManager.removeView(view)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error removing password dialog: ${ex.message}")
                        }
                        
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        showBlockingScreen(packageName)
                    }
                }
            } else {
                Toast.makeText(applicationContext, "Please enter a password", Toast.LENGTH_SHORT).show()
            }
        }
        
        cancelButton.setOnClickListener {
            try {
                // Remove the dialog
                windowManager.removeView(view)
                
                // Force return to home screen
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // Then show blocking screen again
                showBlockingScreen(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing password dialog: ${e.message}")
                
                // Force return to home as fallback
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }
    
    /**
     * Hide blocking overlay
     */
    private fun hideBlockingScreen() {
        try {
            if (blockingView != null) {
                Log.d(TAG, "Hiding blocking screen for ${currentBlockedPackage ?: "unknown"}")
                windowManager.removeView(blockingView)
                blockingView = null
                currentBlockedPackage = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding blocking screen: ${e.message}", e)
            writeLog("ERROR hiding blocking screen: ${e.message}")
            blockingView = null
            currentBlockedPackage = null
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        // Create a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required for app blocking functionality"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create a basic notification for the foreground service
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create a "View" button intent for the notification
        val viewButtonIntent = PendingIntent.getActivity(
            this, 1, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create an informative notification
        val blockedAppsCount = blockedApps.size
        val currentlyBlocking = currentBlockedPackage?.let {
            try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(it, 0)
                ).toString()
            } catch (e: Exception) {
                it
            }
        }
        
        val contentText = when {
            currentlyBlocking != null -> "Currently blocking: $currentlyBlocking"
            blockedAppsCount > 0 -> "Monitoring $blockedAppsCount apps"
            else -> "Service is active"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(null)
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_lock_lock)  // Use a system icon instead
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
            .setShowWhen(false) // Don""t show timestamp
            .setContentIntent(pendingIntent)
            // Add a View button to the notification
            .addAction(android.R.drawable.ic_menu_view, "View", viewButtonIntent)
            .build()
    }
    
    /**
     * Initialize list of launcher packages to avoid blocking
     */
    private fun initLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                info.activityInfo.packageName?.let {
                    launcherPackages.add(it)
                    Log.d(TAG, "Added launcher package: $it")
                }
            }
            
            // Add some known launchers as well
            launcherPackages.addAll(listOf(
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
            ))
            
            Log.d(TAG, "Initialized ${launcherPackages.size} launcher packages")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing launcher packages: ${e.message}", e)
        }
    }
    
    /**
     * Temporarily unblock an app for a short period
     */
    private fun temporarilyUnblockApp(packageName: String, durationMs: Long = 30000) {
        Log.d(TAG, "Temporarily unblocking $packageName for ${durationMs}ms")
        writeLog("Temporarily unblocking $packageName for ${durationMs}ms")
        
        // Hide the blocking screen
        hideBlockingScreen()
        
        // Mark it as temporarily unblocked
        temporarilyUnblockedApp = packageName
        
        // Schedule re-block after duration
        Handler(Looper.getMainLooper()).postDelayed({
            temporarilyUnblockedApp = null
            Log.d(TAG, "Temporary unblock period ended for $packageName")
            writeLog("Temporary unblock period ended for $packageName")
            
            // Check if app needs to be re-blocked if it's still in foreground
            if (currentForegroundApp == packageName && shouldBlockApp(packageName)) {
                showBlockingScreen(packageName)
            }
        }, durationMs)
    }
    
    /**
     * Start periodic checks for schedule changes
     */
    private fun startPeriodicChecks() {
        serviceScope.launch {
            while (isServiceActive) {
                try {
                    Log.d(TAG, "Running periodic check")
                    updateBlockedAppsList(true)
                    
                    // Broadcast to trigger TimeScheduleReceiver
                    val intent = Intent("com.focusguard.app.ACTION_CHECK_SCHEDULES")
                    applicationContext.sendBroadcast(intent)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic check: ${e.message}", e)
                }
                
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Write a log entry to a file for debugging
     */
    private fun writeLog(message: String) {
        try {
            val now = LocalDateTime.now()
            val timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val logMessage = "[$timestamp] $message\n"
            
            // Create logs directory if it doesn't exist
            val logsDir = File(applicationContext.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            // Create or append to log file
            val logFile = File(logsDir, "app_blocker.log")
            FileWriter(logFile, true).use { writer ->
                writer.append(logMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file: ${e.message}", e)
        }
    }

    private fun isAppBlocked(packageName: String): Boolean {
        val blockedApps = runBlocking {
            appBlockRepository.getActiveBlockedApps()
        }
        
        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)
        val currentDay = currentTime.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-6 range

        return blockedApps.any { blockedApp ->
            blockedApp.packageName == packageName &&
                    blockedApp.isActive &&
                    (blockedApp.blockAllDay || (
                            isTimeInRange(
                                currentHour,
                                currentMinute,
                                blockedApp.startTime,
                                blockedApp.endTime
                            ) && (blockedApp.enabledDays and (1 shl currentDay)) != 0
                            ))
        }
    }
    
    private fun isTimeInRange(
        currentHour: Int,
        currentMinute: Int,
        startTime: String?,
        endTime: String?
    ): Boolean {
        if (startTime == null || endTime == null) return false
        
        val startParts = startTime.split(":")
        val endParts = endTime.split(":")
        
        if (startParts.size != 2 || endParts.size != 2) return false
        
        val startHour = startParts[0].toIntOrNull() ?: return false
        val startMinute = startParts[1].toIntOrNull() ?: return false
        val endHour = endParts[0].toIntOrNull() ?: return false
        val endMinute = endParts[1].toIntOrNull() ?: return false
        
        val currentTimeMinutes = currentHour * 60 + currentMinute
        val startTimeMinutes = startHour * 60 + startMinute
        val endTimeMinutes = endHour * 60 + endMinute
        
        return if (startTimeMinutes <= endTimeMinutes) {
            // Normal time range (e.g., 9:00 to 17:00)
            currentTimeMinutes in startTimeMinutes..endTimeMinutes
        } else {
            // Time range crosses midnight (e.g., 22:00 to 6:00)
            currentTimeMinutes >= startTimeMinutes || currentTimeMinutes <= endTimeMinutes
        }
    }

    /**
     * Start a strict monitoring job to ensure app remains blocked
     */
    private fun startStrictBlockingMonitor(packageName: String) {
        serviceScope.launch {
            try {
                var consecutiveChecks = 0
                while (blockingActive && currentBlockedPackage == packageName) {
                    delay(250) // Check every 250ms for tighter control
                    
                    val currentApp = currentForegroundApp
                    if (currentApp == packageName) {
                        consecutiveChecks++
                        
                        // Force return to home if app is detected in foreground
                        withContext(Dispatchers.Main) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            
                            // If we've had to block multiple times, refresh the blocking overlay
                            if (consecutiveChecks > 2) {
                                // App is persistently trying to show - recreate the blocking overlay
                                try {
                                    if (blockingView != null) {
                                        windowManager.removeView(blockingView)
                                    }
                                    
                                    // Create a fresh blocking view with highest priority
                                    val inflater = LayoutInflater.from(applicationContext)
                                    blockingView = inflater.inflate(R.layout.view_app_blocked, null)
                                    
                                    // Setup blocking view elements
                                    setupBlockingView(blockingView!!, packageName, packageName)
                                    
                                    // Apply strongest possible blocking parameters
                                    val params = WindowManager.LayoutParams(
                                        WindowManager.LayoutParams.MATCH_PARENT,
                                        WindowManager.LayoutParams.MATCH_PARENT,
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                        PixelFormat.OPAQUE
                                    )
                                    params.alpha = 1.0f
                                    params.screenBrightness = 1.0f
                                    
                                    // Add view to window with highest priority
                                    windowManager.addView(blockingView, params)
                                    
                                    consecutiveChecks = 0
                                    Log.d(TAG, "Reinforced blocking for persistent app: $packageName")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error reinforcing block: ${e.message}")
                                }
                            }
                        }
                    } else {
                        // Reset counter when app is not in foreground
                        consecutiveChecks = 0
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in blocking monitor: ${e.message}")
            }
        }
    }
} 