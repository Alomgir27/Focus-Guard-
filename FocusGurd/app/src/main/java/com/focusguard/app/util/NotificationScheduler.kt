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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationScheduler"
        
        // Work name for general notifications
        private const val WORK_NOTIFICATION = "simple_notification"
        
        // Default notification times (can be customized by user preferences)
        private val DEFAULT_MORNING_TIME = LocalTime.of(8, 0)
        private val DEFAULT_EVENING_TIME = LocalTime.of(19, 0)
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule all notifications according to frequency settings
     */
    fun scheduleAllNotifications() {
        Log.d(TAG, "Scheduling notifications based on frequency")
        
        // Cancel existing scheduled notifications
        workManager.cancelUniqueWork(WORK_NOTIFICATION)
        
        // Get user preferences
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enable_notifications", true)
        
        if (!enabled) {
            Log.d(TAG, "Notifications are disabled, not scheduling")
            return
        }
        
        // Load the morning and evening times
        val morningHour = prefs.getInt("morning_hour", DEFAULT_MORNING_TIME.hour)
        val morningMinute = prefs.getInt("morning_minute", DEFAULT_MORNING_TIME.minute)
        val eveningHour = prefs.getInt("evening_hour", DEFAULT_EVENING_TIME.hour)
        val eveningMinute = prefs.getInt("evening_minute", DEFAULT_EVENING_TIME.minute)
        
        val morningTime = LocalTime.of(morningHour, morningMinute)
        val eveningTime = LocalTime.of(eveningHour, eveningMinute)
        
        // Get frequency setting
        val frequency = prefs.getInt("notification_frequency", 2)
        
        // Schedule based on frequency
        when (frequency) {
            1 -> { // Low: Morning only
                scheduleNotificationAtTime(morningTime, "morning_notification")
            }
            2 -> { // Medium: Morning and evening
                scheduleNotificationAtTime(morningTime, "morning_notification")
                scheduleNotificationAtTime(eveningTime, "evening_notification")
            }
            3 -> { // High: Morning, midday and evening
                scheduleNotificationAtTime(morningTime, "morning_notification")
                scheduleNotificationAtTime(LocalTime.of(13, 0), "midday_notification")
                scheduleNotificationAtTime(eveningTime, "evening_notification")
            }
            4 -> { // Very high: Four times per day
                scheduleNotificationAtTime(morningTime, "morning_notification")
                scheduleNotificationAtTime(LocalTime.of(11, 0), "mid_morning_notification")
                scheduleNotificationAtTime(LocalTime.of(15, 0), "afternoon_notification")
                scheduleNotificationAtTime(eveningTime, "evening_notification")
            }
        }
        
        Log.d(TAG, "Scheduled $frequency notifications per day")
    }
    
    /**
     * Schedule a one-time notification at a specific time
     */
    private fun scheduleNotificationAtTime(
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
        
        Log.d(TAG, "Scheduling notification at $time (in $delay ms)")
        
        // Randomly select a notification type - we'll let the worker decide what to show
        val notificationTypes = NotificationType.values()
        val randomType = notificationTypes[notificationTypes.indices.random()]
        
        val notificationData = Data.Builder()
            .putString("notification_type", randomType.name)
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
        
        // Reschedule the notifications
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
        
        // Reschedule the notifications
        scheduleAllNotifications()
    }
    
    /**
     * Cancel all scheduled notifications
     */
    fun cancelAllNotifications() {
        Log.d(TAG, "Cancelling all scheduled notifications")
        workManager.cancelAllWorkByTag(WORK_NOTIFICATION)
        workManager.cancelUniqueWork("morning_notification")
        workManager.cancelUniqueWork("mid_morning_notification")
        workManager.cancelUniqueWork("midday_notification")
        workManager.cancelUniqueWork("afternoon_notification")
        workManager.cancelUniqueWork("evening_notification")
    }
} 