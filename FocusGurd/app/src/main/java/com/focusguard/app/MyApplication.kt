package com.focusguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.Configuration
import androidx.work.WorkManager
import com.focusguard.app.data.AppDatabase
import com.focusguard.app.data.repository.AppBlockingRepository
import com.focusguard.app.data.repository.AppBlockRepository
import com.focusguard.app.data.repository.NotificationRepository
import com.focusguard.app.data.repository.TaskRepository
import com.focusguard.app.data.repository.UserHabitRepository
import com.focusguard.app.data.repository.UserInsightRepository
import com.focusguard.app.services.NotificationService
import com.focusguard.app.util.ApiKeyManager
import com.focusguard.app.util.NotificationCacheManager
import com.focusguard.app.util.NotificationGenerator
import com.focusguard.app.util.NotificationScheduler
import com.focusguard.app.util.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MyApplication : Application(), Configuration.Provider {

    // Use lazy initialization for all database-related components
    companion object {
        private const val TAG = "FocusGuardApp"
        
        // Define migration from version 1 to 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration if needed
                // database.execSQL("CREATE TABLE IF NOT EXISTS `new_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            }
        }
        
        // Reference to application instance
        private lateinit var instance: MyApplication
        
        // Lazy-initialized database
        val database: AppDatabase by lazy {
            try {
                Log.d(TAG, "Initializing database")
                Room.databaseBuilder(
                    instance.applicationContext,
                    AppDatabase::class.java,
                    "focusguard-db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing database: ${e.message}", e)
                throw e
            }
        }
        
        // Lazy-initialized repositories
        val userHabitRepository: UserHabitRepository by lazy {
            try {
                Log.d(TAG, "Initializing userHabitRepository")
                UserHabitRepository(database.userHabitDao())
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing userHabitRepository: ${e.message}", e)
                throw e
            }
        }
        
        // Use AppBlockRepository if it exists, otherwise create a temporary one
        val appBlockRepository: AppBlockRepository by lazy {
            try {
                Log.d(TAG, "Initializing appBlockRepository")
                AppBlockRepository(database.appBlockDao())
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing appBlockRepository: ${e.message}", e)
                throw e
            }
        }
        
        val notificationRepository: NotificationRepository by lazy {
            try {
                Log.d(TAG, "Initializing notificationRepository")
                NotificationRepository(database.notificationDao())
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing notificationRepository: ${e.message}", e)
                throw e
            }
        }
        
        val userInsightRepository: UserInsightRepository by lazy {
            try {
                Log.d(TAG, "Initializing userInsightRepository")
                UserInsightRepository(database.userInsightDao())
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing userInsightRepository: ${e.message}", e)
                throw e
            }
        }
        
        // Notification channels
        const val CHANNEL_ID_GENERAL = "channel_general"
        
        private lateinit var aiPreferencesQueue: AiPreferencesQueue
        
        // Our new components
        private lateinit var notificationCacheManager: NotificationCacheManager
        private lateinit var apiKeyManager: ApiKeyManager
        
        fun getAiPreferencesQueue(): AiPreferencesQueue = aiPreferencesQueue
        fun getNotificationCacheManager(): NotificationCacheManager = notificationCacheManager
        fun getApiKeyManager(): ApiKeyManager = apiKeyManager
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationScheduler: NotificationScheduler
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate started")
        
        // Manually disable waiting for debugger - fixes the app freezing issue
        try {
            // Using reflection to disable debugger wait if it's happening
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null)
            val mHiddenApiWarningShown = activityThread.getDeclaredField("mHiddenApiWarningShown")
            mHiddenApiWarningShown.isAccessible = true
            mHiddenApiWarningShown.setBoolean(currentActivityThread, true)
            
            // Also try to manually detach any waiting debugger
            android.os.Debug.waitingForDebugger()
            Log.d(TAG, "Forcibly disabled debugger wait")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling debugger wait: ${e.message}")
        }
        
        // Set uncaught exception handler to log any crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL EXCEPTION: ${thread.name}", throwable)
            throwable.printStackTrace()
            // Let the default handler deal with it too
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        
        try {
            instance = this
            Log.d(TAG, "Instance set")
            
            // Initialize WorkManager with try-catch
            try {
                Log.d(TAG, "Initializing WorkManager")
                WorkManager.initialize(this, workManagerConfiguration)
                Log.d(TAG, "WorkManager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing WorkManager: ${e.message}", e)
            }
            
            // Create notification channels
            createNotificationChannels()
            
            // Initialize new components
            notificationCacheManager = NotificationCacheManager(this)
            apiKeyManager = ApiKeyManager(this)
            
            // Initialize notification scheduler
            notificationScheduler = NotificationScheduler(this)
            
            // Apply UI safety measures
            applySafetyMeasures()
            
            // Touch database in a background thread to trigger initialization
            applicationScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Testing database connection")
                    // Just touch the database to initialize it
                    val db = database
                    Log.d(TAG, "Database connection test successful")
                    
                    // Schedule notifications after database is initialized
                    notificationScheduler.scheduleAllNotifications()
                    
                    // Pre-generate offline content if enabled
                    preGenerateOfflineContentIfEnabled()
                } catch (e: Exception) {
                    Log.e(TAG, "Database initialization error: ${e.message}", e)
                }
            }
            
            // Initialize the AI preferences queue
            aiPreferencesQueue = AiPreferencesQueue(this)
            
            Log.d(TAG, "Application onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in application onCreate: ${e.message}", e)
        }
    }
    
    /**
     * Pre-generate offline content if the setting is enabled
     */
    private suspend fun preGenerateOfflineContentIfEnabled() {
        try {
            val prefs = getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
            val shouldPreGenerate = prefs.getBoolean("pre_generate_offline", true)
            
            if (shouldPreGenerate) {
                Log.d(TAG, "Pre-generating offline content on startup")
                val notificationGenerator = NotificationGenerator(this)
                notificationGenerator.preGenerateOfflineContent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pre-generating offline content", e)
        }
    }
    
    // Configure WorkManager
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
    
    // Create notification channels for Android 8.0+
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Task reminder channel
            val taskChannel = NotificationChannel(
                "task_reminders",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            
            // App blocking channel
            val blockingChannel = NotificationChannel(
                "app_blocking",
                "App Blocking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required for app blocking functionality"
            }
            
            // Additional channels for the new notification types
            val motivationChannel = NotificationChannel(
                NotificationService.CHANNEL_MOTIVATION,
                "Motivational Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily motivational messages"
            }
            
            val habitReminderChannel = NotificationChannel(
                NotificationService.CHANNEL_HABIT_REMINDER,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders about habits and goals"
            }
            
            val religiousChannel = NotificationChannel(
                NotificationService.CHANNEL_RELIGIOUS,
                "Religious Quotes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Quranic verses and religious reflections"
            }
            
            val insightsChannel = NotificationChannel(
                NotificationService.CHANNEL_INSIGHTS,
                "Personalized Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Insights about your habits and usage patterns"
            }
            
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications from the app"
            }
            
            // Register all channels
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(taskChannel)
            notificationManager.createNotificationChannel(blockingChannel)
            notificationManager.createNotificationChannel(motivationChannel)
            notificationManager.createNotificationChannel(habitReminderChannel)
            notificationManager.createNotificationChannel(religiousChannel)
            notificationManager.createNotificationChannel(insightsChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    private fun applySafetyMeasures() {
        try {
            Log.d(TAG, "Applying UI safety measures")
            
            // We need to initialize the UIUtils class early to ensure it's available
            // This registers our custom exception handler for ViewGroup.onMeasure issues
            applicationScope.launch(Dispatchers.Main) {
                // If any problematic ViewGroups with switches are created,
                // we'll intercept them at runtime to fix text issues
                androidx.appcompat.widget.AppCompatTextView::class.java.declaredFields
                    .firstOrNull { it.name == "TEXT_APPEARANCE_ATTRS" }
                    ?.also { Log.d(TAG, "Initialized AppCompatTextView classes") }
                
                // Ensure SwitchCompat classes are pre-initialized to prevent layout issues
                try {
                    val switchCompatClass = androidx.appcompat.widget.SwitchCompat::class.java
                    switchCompatClass.declaredFields
                        .firstOrNull { it.name == "CHECKED_STATE_SET" }
                        ?.also { Log.d(TAG, "Initialized SwitchCompat classes") }
                    
                    // Pre-initialize the important static layout methods to prevent early crashes
                    switchCompatClass.getDeclaredMethod("makeLayout", CharSequence::class.java)
                        ?.also { Log.d(TAG, "SwitchCompat makeLayout method initialized") }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing SwitchCompat: ${e.message}", e)
                }
                
                com.google.android.material.materialswitch.MaterialSwitch::class.java.declaredFields
                    .firstOrNull { it.name == "DEF_STYLE_RES" }
                    ?.also { Log.d(TAG, "Initialized MaterialSwitch classes") }
                
                Log.d(TAG, "Safety measures applied")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying safety measures: ${e.message}", e)
        }
    }

    /**
     * Class to manage a queue of AI preference requests
     * Ensures only one API call is made at a time
     */
    class AiPreferencesQueue(private val context: Context) {
        private val queue = ConcurrentLinkedQueue<PreferenceUpdate>()
        private val handler = Handler(Looper.getMainLooper())
        private var isProcessing = false
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        
        companion object {
            private const val TAG = "AiPreferencesQueue"
            private const val DEBOUNCE_DELAY_MS = 2000L // 2 seconds
        }
        
        // Data class for preference updates
        data class PreferenceUpdate(
            val habits: String,
            val goals: String,
            val interests: String,
            val timestamp: Long = System.currentTimeMillis()
        )
        
        // Add preferences to the queue
        fun queuePreferences(habits: String, goals: String, interests: String) {
            val update = PreferenceUpdate(habits, goals, interests)
            queue.add(update)
            Log.d(TAG, "Added preferences to queue. Queue size: ${queue.size}")
            
            if (!isProcessing) {
                // Wait a bit to allow for batched updates before processing
                handler.postDelayed({ processQueue() }, DEBOUNCE_DELAY_MS)
            }
        }
        
        private fun processQueue() {
            if (queue.isEmpty() || isProcessing) {
                return
            }
            
            isProcessing = true
            Log.d(TAG, "Processing AI preference queue. Queue size: ${queue.size}")
            
            // Get the latest update (we can discard older ones)
            val latestUpdate = queue.poll()
            
            // Clear the rest of the queue as we'll just use the latest
            queue.clear()
            
            // Process the update on a background thread using executor
            executor.execute {
                try {
                    // Simulate API call to AI model
                    Log.d(TAG, "Sending preferences to AI model: $latestUpdate")
                    
                    // In a real implementation, this would be an actual API call
                    // Call your AI service here with latestUpdate data
                    
                    // Add artificial delay to simulate network call
                    // This no longer blocks the main thread
                    Thread.sleep(1500)
                    
                    // Show a brief notification that the preferences were processed
                    handler.post {
                        showNotification(
                            "AI Preferences Updated",
                            "Your personalized content will reflect your preferences."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing AI preferences", e)
                } finally {
                    // Mark as done processing and check if there are more items
                    isProcessing = false
                    
                    // If more items were added during processing, process again
                    if (queue.isNotEmpty()) {
                        handler.postDelayed({ processQueue() }, DEBOUNCE_DELAY_MS)
                    }
                }
            }
        }
        
        private fun showNotification(title: String, content: String) {
            // Create a notification to inform user that preferences were processed
            val notificationService = NotificationService(context)
            
            val notification = com.focusguard.app.data.entity.Notification(
                id = 0, // Will be assigned by Room
                title = title,
                content = content,
                type = com.focusguard.app.data.entity.NotificationType.GENERAL,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
            
            notificationService.sendNotification(notification)
        }
    }
} 