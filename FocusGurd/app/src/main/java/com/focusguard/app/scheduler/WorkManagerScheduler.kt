package com.focusguard.app.scheduler

import android.util.Log
import androidx.work.*
import com.focusguard.app.workers.MidnightRoutineWorker
import java.time.*
import java.util.concurrent.TimeUnit

class WorkManagerScheduler(private val workManager: WorkManager) {

    fun schedule() {
        scheduleAllWork()
    }

    private fun scheduleAllWork() {
        Log.d(TAG, "Scheduling all work")
        
        // Schedule app usage sync worker
        scheduleAppUsageSync()
        
        // Schedule notification workers
        scheduleMotivationalNotifications()
        scheduleHabitReminderNotifications()
        scheduleInsightNotifications()
        
        // Schedule daily routine workers
        scheduleDailyRoutineGeneration()
        scheduleMidnightRoutineGeneration()
        
        // Schedule cleaning/maintenance workers
        scheduleMaintenanceWork()
    }

    /**
     * Schedule the midnight routine generation worker
     */
    private fun scheduleMidnightRoutineGeneration() {
        Log.d(TAG, "Scheduling midnight routine generation")
        
        // Create midnight time constraints (run at midnight every day)
        val midnightTime = LocalTime.MIDNIGHT
        val currentTime = LocalTime.now()
        
        // Calculate time until next midnight
        val nowDateTime = LocalDateTime.now()
        val todayMidnight = LocalDateTime.of(LocalDate.now(), midnightTime)
        val tomorrowMidnight = LocalDateTime.of(LocalDate.now().plusDays(1), midnightTime)
        
        val targetMidnight = if (nowDateTime.isAfter(todayMidnight)) tomorrowMidnight else todayMidnight
        val initialDelay = Duration.between(nowDateTime, targetMidnight)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val midnightRoutineRequest = PeriodicWorkRequestBuilder<MidnightRoutineWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            MIDNIGHT_ROUTINE_WORKER_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            midnightRoutineRequest
        )
        
        Log.d(TAG, "Midnight routine worker scheduled to run in ${initialDelay.toHours()} hours and ${initialDelay.toMinutes() % 60} minutes")
    }
    
    // Placeholder methods for other scheduling functions
    private fun scheduleAppUsageSync() {
        // Implementation
    }
    
    private fun scheduleMotivationalNotifications() {
        // Implementation
    }
    
    private fun scheduleHabitReminderNotifications() {
        // Implementation
    }
    
    private fun scheduleInsightNotifications() {
        // Implementation
    }
    
    private fun scheduleDailyRoutineGeneration() {
        // Implementation
    }
    
    private fun scheduleMaintenanceWork() {
        // Implementation
    }

    companion object {
        private const val TAG = "WorkManagerScheduler"
        
        // Worker Names
        private const val APP_USAGE_SYNC_WORKER_NAME = "app_usage_sync_worker"
        private const val MOTIVATIONAL_NOTIFICATION_WORKER_NAME = "motivational_notification_worker"
        private const val HABIT_REMINDER_NOTIFICATION_WORKER_NAME = "habit_reminder_notification_worker"
        private const val INSIGHT_NOTIFICATION_WORKER_NAME = "insight_notification_worker"
        private const val DAILY_ROUTINE_GENERATION_WORKER_NAME = "daily_routine_generation_worker"
        private const val MIDNIGHT_ROUTINE_WORKER_NAME = "midnight_routine_worker"
        private const val MAINTENANCE_WORKER_NAME = "maintenance_worker"
    }
} 