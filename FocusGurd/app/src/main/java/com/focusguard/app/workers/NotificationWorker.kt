package com.focusguard.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.data.entity.UserInsight
import com.focusguard.app.data.repository.NotificationRepository
import com.focusguard.app.data.repository.UserHabitRepository
import com.focusguard.app.data.repository.UserInsightRepository
import com.focusguard.app.services.NotificationService
import com.focusguard.app.util.NotificationGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.time.format.DateTimeFormatter
import com.focusguard.app.data.entity.Notification
import java.util.Locale
import android.telephony.TelephonyManager
import android.os.Build

class NotificationWorker(
    context: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "NotificationWorker"
        private const val MAX_AI_API_CALLS_PER_DAY = 5
        
        // Queue for storing notifications for the day
        private val notificationQueue = mutableListOf<QueuedNotification>()
        
        // Helper class for queued notifications
        data class QueuedNotification(
            val type: NotificationType,
            val scheduledTime: LocalDateTime,
            val priority: Int = 1 // Higher number = higher priority
        )
        
        // Function to plan notifications for the next day
        fun planNotificationsForNextDay(context: Context) {
            Log.d(TAG, "Planning notifications for next day")
            
            // Clear previous queue
            notificationQueue.clear()
            
            // Create a plan for tomorrow
            val tomorrow = LocalDateTime.now().plusDays(1)
            val morningTime = tomorrow.with(LocalTime.of(8, 0))
            val noonTime = tomorrow.with(LocalTime.of(12, 0))
            val afternoonTime = tomorrow.with(LocalTime.of(15, 0))
            val eveningTime = tomorrow.with(LocalTime.of(19, 0))
            
            // Queue notifications with different types spread throughout the day
            notificationQueue.add(QueuedNotification(NotificationType.MOTIVATION, morningTime, 2))
            notificationQueue.add(QueuedNotification(NotificationType.HABIT_REMINDER, noonTime, 1))
            notificationQueue.add(QueuedNotification(NotificationType.INSIGHT, afternoonTime, 3))
            notificationQueue.add(QueuedNotification(NotificationType.RELIGIOUS_QUOTE, eveningTime, 1))
            
            // Sort by scheduled time
            notificationQueue.sortBy { it.scheduledTime }
            
            Log.d(TAG, "Planned ${notificationQueue.size} notifications for tomorrow")
            
            // TODO: Save this plan to shared preferences or database for persistence
        }
        
        // Get the next notification to process
        fun getNextNotification(): QueuedNotification? {
            if (notificationQueue.isEmpty()) {
                return null
            }
            
            // Find notification with nearest time and remove it from queue
            val now = LocalDateTime.now()
            val nextNotification = notificationQueue.firstOrNull { it.scheduledTime.isBefore(now) }
                ?: notificationQueue.minByOrNull { it.scheduledTime }
                
            if (nextNotification != null) {
                notificationQueue.remove(nextNotification)
            }
            
            return nextNotification
        }
    }
    
    private val notificationService = NotificationService(context)
    private val notificationRepository = MyApplication.notificationRepository
    private val userHabitRepository = MyApplication.userHabitRepository
    private val userInsightRepository = MyApplication.userInsightRepository
    
    // Get the user's locale for localized content
    private val userLocale: Locale by lazy {
        determineUserLocale(applicationContext)
    }
    
    // Get the user's country code for region-specific content
    private val userCountry: String by lazy {
        determineUserCountry(applicationContext)
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting notification worker")
        
        return try {
            // Check if we have a specific notification type from input data
            val specificType = inputData.getString("notification_type")?.let {
                NotificationType.valueOf(it)
            }
            
            if (specificType != null) {
                // Use the specified type
                generateAndSendNotification(specificType)
            } else {
                // Use the next queued notification or generate a default one
                val nextNotification = getNextNotification()
                
                if (nextNotification != null) {
                    Log.d(TAG, "Processing queued notification: ${nextNotification.type} scheduled for ${nextNotification.scheduledTime}")
                    generateAndSendNotification(nextNotification.type)
                } else {
                    // Default to general if queue is empty
                    Log.d(TAG, "No queued notifications, using general type")
                    generateAndSendNotification(NotificationType.GENERAL)
                    
                    // Plan for next day if queue is empty
                    planNotificationsForNextDay(applicationContext)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification worker", e)
            Result.failure()
        }
    }
    
    /**
     * Determine the user's locale from system settings or SIM information
     */
    private fun determineUserLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString("preferred_language", null)
        
        // If user has specifically selected a language, use that
        if (!savedLanguage.isNullOrEmpty()) {
            try {
                val localeParts = savedLanguage.split("_")
                return if (localeParts.size > 1) {
                    Locale(localeParts[0], localeParts[1])
                } else {
                    Locale(localeParts[0])
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved locale: $savedLanguage", e)
            }
        }
        
        // Try to get locale from telephony manager (more accurate for region)
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val countryIso = telephonyManager.networkCountryIso
            
            if (!countryIso.isNullOrEmpty()) {
                if (countryIso.equals("bd", ignoreCase = true)) {
                    return Locale("bn", "BD") // Bengali/Bangladesh
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting country from telephony", e)
        }
        
        // Default to system locale
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    /**
     * Determine the user's country for region-specific content
     */
    private fun determineUserCountry(context: Context): String {
        // Try telephony manager first
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val countryIso = telephonyManager.networkCountryIso
            
            if (!countryIso.isNullOrEmpty()) {
                return countryIso.uppercase(Locale.US)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting country from telephony", e)
        }
        
        // Fall back to locale
        return userLocale.country
    }
    
    private suspend fun generateAndSendNotification(type: NotificationType) {
        Log.d(TAG, "Generating notification for type: $type")
        
        try {
            // Check if we've hit the daily API call limit
            if (type != NotificationType.GENERAL && hasReachedDailyApiLimit()) {
                Log.d(TAG, "Reached daily API call limit, using pre-generated content instead")
                sendPreGeneratedNotification(type)
                return
            }
            
            // Generate notification content based on type
            val notification = when (type) {
                NotificationType.MOTIVATION -> generateMotivationalNotification()
                NotificationType.HABIT_REMINDER -> generateHabitReminderNotification()
                NotificationType.RELIGIOUS_QUOTE -> generateReligiousNotification()
                NotificationType.INSIGHT -> generateInsightNotification()
                else -> generateGeneralNotification()
            }
            
            // Store in database
            val notificationId = notificationRepository.addNotification(notification)
            Log.d(TAG, "Stored notification in database with ID: $notificationId")
            
            // Queue the notification to be sent with proper timing
            notificationService.queueNotification(notification.copy(id = notificationId))
            
            Log.d(TAG, "Notification queued for delivery")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating notification", e)
            
            // Fallback to a simple notification in case of error
            val fallbackNotification = Notification(
                id = 0,
                title = "Focus Guard",
                content = "Stay focused on what matters most today.",
                type = NotificationType.GENERAL,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
            
            val notificationId = notificationRepository.addNotification(fallbackNotification)
            notificationService.queueNotification(fallbackNotification.copy(id = notificationId))
        }
    }
    
    private suspend fun sendPreGeneratedNotification(type: NotificationType) {
        // Get a pre-generated notification message based on type
        val (title, content) = getPreGeneratedContent(type)
        
        val notification = Notification(
            id = 0,
            title = title,
            content = content,
            type = type,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
        
        val notificationId = notificationRepository.addNotification(notification)
        notificationService.queueNotification(notification.copy(id = notificationId))
    }
    
    /**
     * Determines if we should use AI for this notification based on daily limits
     */
    private suspend fun shouldUseAIForNotification(): Boolean {
        val today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0)
        val notificationsToday = withContext(Dispatchers.IO) {
            notificationRepository.getAllNotifications().first().count { 
                it.createdAt.isAfter(today) && it.content.length > 100 // Assume AI content is longer
            }
        }
        
        return notificationsToday < MAX_AI_API_CALLS_PER_DAY
    }
    
    /**
     * Records API usage to track limits
     */
    private fun recordAIUsage() {
        // In a real app, you would persist this information to track API usage
        Log.d(TAG, "Recording AI API usage")
    }
    
    /**
     * Creates an insight based on the notification content
     */
    private suspend fun createInsightFromNotification(notification: com.focusguard.app.data.entity.Notification) {
        val insight = UserInsight(
            content = notification.content,
            source = when (notification.type) {
                NotificationType.INSIGHT -> com.focusguard.app.data.entity.InsightSource.AI_SUGGESTION
                NotificationType.APP_USAGE_WARNING -> com.focusguard.app.data.entity.InsightSource.APP_USAGE_ANALYSIS
                else -> com.focusguard.app.data.entity.InsightSource.SYSTEM_GENERATED
            }
        )
        
        userInsightRepository.addInsight(insight)
    }
    
    private suspend fun <T> withTimeout(timeoutMs: Long, block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                block()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Operation timed out after $timeoutMs ms")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error in withTimeout", e)
            null
        }
    }
    
    /**
     * Check if we've reached the daily limit for AI API calls
     */
    private fun hasReachedDailyApiLimit(): Boolean {
        val prefs = applicationContext.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)
        val today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentCount = prefs.getInt(today, 0)
        
        return currentCount >= MAX_AI_API_CALLS_PER_DAY
    }
    
    /**
     * Get pre-generated content for when API limit is reached, with localization support
     */
    private fun getPreGeneratedContent(type: NotificationType): Pair<String, String> {
        // Check if we should use Bengali content
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            return getPreGeneratedContentBengali(type)
        }
        
        return when (type) {
            NotificationType.MOTIVATION -> Pair(
                "Stay Motivated",
                "Every step you take brings you closer to your goals. Keep moving forward!"
            )
            NotificationType.HABIT_REMINDER -> Pair(
                "Habit Reminder",
                "Take a moment to focus on building your positive habits today."
            )
            NotificationType.RELIGIOUS_QUOTE -> Pair(
                "Reflection",
                "Remember that patience is a virtue and persistence leads to success."
            )
            NotificationType.INSIGHT -> Pair(
                "Daily Insight",
                "Take time to reflect on your progress and adjust your path if needed."
            )
            else -> Pair(
                "Focus Guard",
                "Stay present in the moment and focus on what truly matters."
            )
        }
    }
    
    /**
     * Get pre-generated Bengali content
     */
    private fun getPreGeneratedContentBengali(type: NotificationType): Pair<String, String> {
        return when (type) {
            NotificationType.MOTIVATION -> Pair(
                "অনুপ্রেরণা",
                "প্রতিটি পদক্ষেপ আপনাকে আপনার লক্ষ্যের দিকে এগিয়ে নিয়ে যায়। সামনে এগিয়ে যান!"
            )
            NotificationType.HABIT_REMINDER -> Pair(
                "অভ্যাস স্মরণিকা",
                "আজ আপনার ইতিবাচক অভ্যাসগুলি গড়ে তোলার দিকে মনোযোগ দিতে একটু সময় নিন।"
            )
            NotificationType.RELIGIOUS_QUOTE -> Pair(
                "ধর্মীয় চিন্তা",
                "ধৈর্য একটি গুণ এবং দৃঢ়তা সাফল্যের দিকে নিয়ে যায়।"
            )
            NotificationType.INSIGHT -> Pair(
                "দৈনিক অন্তর্দৃষ্টি",
                "আপনার অগ্রগতি প্রতিফলিত করতে এবং প্রয়োজনে আপনার পথ সমন্বয় করতে সময় নিন।"
            )
            else -> Pair(
                "ফোকাস গার্ড",
                "বর্তমান মুহূর্তে থাকুন এবং যা সত্যিই গুরুত্বপূর্ণ তার উপর মনোযোগ দিন।"
            )
        }
    }
    
    /**
     * Generate a motivational notification
     */
    private suspend fun generateMotivationalNotification(): Notification {
        // Check if we should generate in Bengali
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            val title = "দৈনিক অনুপ্রেরণা"
            val content = "সাফল্য চূড়ান্ত নয়, ব্যর্থতা মারাত্মক নয়: এটি এগিয়ে যাওয়ার সাহস যা গুরুত্বপূর্ণ।"
            
            return Notification(
                id = 0,
                title = title,
                content = content,
                type = NotificationType.MOTIVATION,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
        }
        
        // Default to English
        val title = "Daily Motivation"
        val content = "Success is not final, failure is not fatal: It is the courage to continue that counts."
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = NotificationType.MOTIVATION,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
    
    /**
     * Generate a habit reminder notification
     */
    private suspend fun generateHabitReminderNotification(): Notification {
        // Check if we should generate in Bengali
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            val title = "অভ্যাস চেক-ইন"
            val content = "আপনি কি আজ আপনার গুরুত্বপূর্ণ অভ্যাসগুলির জন্য সময় নিয়েছেন? ছোট সামঞ্জস্যপূর্ণ পদক্ষেপগুলি বড় ফলাফল এনে দেয়।"
            
            return Notification(
                id = 0,
                title = title,
                content = content,
                type = NotificationType.HABIT_REMINDER,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
        }
        
        // Default to English
        val title = "Habit Check-in"
        val content = "Have you taken time for your important habits today? Small consistent actions lead to big results."
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = NotificationType.HABIT_REMINDER,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
    
    /**
     * Generate a religious notification
     */
    private suspend fun generateReligiousNotification(): Notification {
        // Check if we should generate in Bengali
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            val title = "দৈনিক চিন্তা"
            val content = "ধৈর্য এবং অধ্যবসায়ের একটি জাদুকরী প্রভাব রয়েছে যার সামনে অসুবিধাগুলি অদৃশ্য হয়ে যায় এবং বাধাগুলি মিলিয়ে যায়।"
            
            return Notification(
                id = 0,
                title = title,
                content = content,
                type = NotificationType.RELIGIOUS_QUOTE,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
        }
        
        // Default to English
        val title = "Daily Reflection"
        val content = "Patience and perseverance have a magical effect before which difficulties disappear and obstacles vanish."
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = NotificationType.RELIGIOUS_QUOTE,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
    
    /**
     * Generate an insight notification
     */
    private suspend fun generateInsightNotification(): Notification {
        // Check if we should generate in Bengali
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            val title = "ফোকাস অন্তর্দৃষ্টি"
            val content = "আপনি আপনার লক্ষ্যগুলিতে ধীরে ধীরে অগ্রগতি করছেন। মনে রাখবেন বিরতি নিতে এবং পথে ছোট সাফল্যগুলি উদযাপন করতে!"
            
            return Notification(
                id = 0,
                title = title,
                content = content,
                type = NotificationType.INSIGHT,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
        }
        
        // Default to English
        val title = "Focus Insight"
        val content = "You've been making steady progress on your goals. Remember to take breaks and celebrate small wins along the way!"
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = NotificationType.INSIGHT,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
    
    /**
     * Generate a general notification
     */
    private suspend fun generateGeneralNotification(): Notification {
        // Check if we should generate in Bengali
        if (userLocale.language == "bn" || userCountry.equals("BD", ignoreCase = true)) {
            val title = "ফোকাস গার্ড"
            val content = "আজ আপনি কীভাবে সময় ব্যয় করছেন তা সম্পর্কে সচেতন থাকুন। প্রতিটি মুহূর্ত একটি সুযোগ।"
            
            return Notification(
                id = 0,
                title = title,
                content = content,
                type = NotificationType.GENERAL,
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
        }
        
        // Default to English
        val title = "Focus Guard"
        val content = "Stay mindful of how you spend your time today. Every moment is an opportunity."
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = NotificationType.GENERAL,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
} 