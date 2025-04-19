package com.focusguard.app.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.workers.NotificationWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationScheduler"
        
        // Work names for different notification types
        private const val WORK_MOTIVATION = "motivation_notification"
        private const val WORK_HABIT_REMINDER = "habit_reminder_notification"
        private const val WORK_RELIGIOUS = "religious_notification"
        private const val WORK_INSIGHTS = "insights_notification"
        private const val WORK_GENERAL = "general_notification"
        
        // Default notification times (can be customized by user preferences)
        private val DEFAULT_MORNING_TIME = LocalTime.of(8, 0)
        private val DEFAULT_MIDDAY_TIME = LocalTime.of(12, 30)
        private val DEFAULT_EVENING_TIME = LocalTime.of(19, 0)
        
        // Notification frequencies
        private const val RELIGIOUS_QUOTE_FREQUENCY_DAYS = 1L  // Daily
        private const val MOTIVATION_FREQUENCY_DAYS = 1L       // Daily
        private const val INSIGHT_FREQUENCY_DAYS = 2L          // Every other day
        private const val HABIT_REMINDER_FREQUENCY_HOURS = 8L  // 3 times a day
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule all notification types according to a daily plan
     */
    fun scheduleAllNotifications() {
        Log.d(TAG, "Scheduling all notification types")
        
        // Cancel existing work
        workManager.cancelUniqueWork(WORK_MOTIVATION)
        workManager.cancelUniqueWork(WORK_HABIT_REMINDER)
        workManager.cancelUniqueWork(WORK_RELIGIOUS)
        workManager.cancelUniqueWork(WORK_INSIGHTS)
        workManager.cancelUniqueWork(WORK_GENERAL)
        
        // Schedule morning notifications
        scheduleNotificationAtTime(
            NotificationType.RELIGIOUS_QUOTE,
            DEFAULT_MORNING_TIME,
            WORK_RELIGIOUS
        )
        
        // Schedule midday notifications
        scheduleNotificationAtTime(
            NotificationType.MOTIVATION,
            DEFAULT_MIDDAY_TIME,
            WORK_MOTIVATION
        )
        
        // Schedule evening notifications
        scheduleNotificationAtTime(
            NotificationType.HABIT_REMINDER,
            DEFAULT_EVENING_TIME,
            WORK_HABIT_REMINDER
        )
        
        // Schedule insights on alternating days
        schedulePeriodicNotification(
            NotificationType.INSIGHT,
            INSIGHT_FREQUENCY_DAYS,
            TimeUnit.DAYS,
            WORK_INSIGHTS
        )
        
        Log.d(TAG, "All notifications scheduled")
    }
    
    /**
     * Schedule a one-time notification at a specific time today
     */
    fun scheduleNotificationAtTime(
        notificationType: NotificationType,
        time: LocalTime,
        workName: String
    ) {
        val now = LocalDateTime.now()
        val scheduledTime = LocalDateTime.of(now.toLocalDate(), time)
        
        // If the scheduled time is in the past, schedule for tomorrow
        var delay = ChronoUnit.MILLIS.between(now, scheduledTime)
        if (delay < 0) {
            delay = ChronoUnit.MILLIS.between(
                now, 
                scheduledTime.plusDays(1)
            )
        }
        
        Log.d(TAG, "Scheduling $notificationType notification in $delay ms")
        
        val notificationData = Data.Builder()
            .putString("notification_type", notificationType.name)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setConstraints(constraints)
            .setInputData(notificationData)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(workName)
            .build()
        
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            notificationWorkRequest
        )
    }
    
    /**
     * Schedule a recurring notification with specified frequency
     */
    fun schedulePeriodicNotification(
        notificationType: NotificationType,
        repeatInterval: Long,
        timeUnit: TimeUnit,
        workName: String
    ) {
        Log.d(TAG, "Scheduling periodic $notificationType notification every $repeatInterval ${timeUnit.name}")
        
        val notificationData = Data.Builder()
            .putString("notification_type", notificationType.name)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val notificationWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            repeatInterval, timeUnit
        )
            .setConstraints(constraints)
            .setInputData(notificationData)
            .addTag(workName)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            notificationWorkRequest
        )
    }
    
    /**
     * Schedule an immediate notification
     */
    fun scheduleImmediateNotification(notificationType: NotificationType) {
        Log.d(TAG, "Scheduling immediate $notificationType notification")
        
        val notificationData = Data.Builder()
            .putString("notification_type", notificationType.name)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setConstraints(constraints)
            .setInputData(notificationData)
            .build()
        
        workManager.enqueue(notificationWorkRequest)
    }

    /**
     * Set the notification frequency (how many notifications per day)
     */
    fun setNotificationFrequency(frequency: Int) {
        Log.d(TAG, "Setting notification frequency to $frequency")
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("notification_frequency", frequency).apply()
        
        // Reschedule notifications with new frequency
        scheduleAllNotifications()
    }

    /**
     * Set the morning notification time
     */
    fun setMorningTime(time: LocalTime) {
        Log.d(TAG, "Setting morning notification time to $time")
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("morning_hour", time.hour)
            .putInt("morning_minute", time.minute)
            .apply()
        
        // Reschedule the morning notifications
        scheduleAllNotifications()
    }

    /**
     * Set the evening notification time
     */
    fun setEveningTime(time: LocalTime) {
        Log.d(TAG, "Setting evening notification time to $time")
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("evening_hour", time.hour)
            .putInt("evening_minute", time.minute)
            .apply()
        
        // Reschedule the evening notifications
        scheduleAllNotifications()
    }

    /**
     * Enable or disable a specific notification type
     */
    fun setNotificationTypeEnabled(type: NotificationType, enabled: Boolean) {
        Log.d(TAG, "Setting notification type $type enabled=$enabled")
        
        val prefKey = when (type) {
            NotificationType.MOTIVATION -> "enable_motivational"
            NotificationType.HABIT_REMINDER -> "enable_habit_reminders"
            NotificationType.RELIGIOUS_QUOTE -> "enable_religious_quotes"
            NotificationType.INSIGHT -> "enable_insights"
            NotificationType.APP_USAGE_WARNING -> "enable_usage_warnings"
            NotificationType.GENERAL -> "enable_general"
        }
        
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(prefKey, enabled).apply()
        
        // Reschedule notifications with updated settings
        scheduleAllNotifications()
    }
} 