package com.focusguard.app.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.app.workers.DailyRoutineWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class RoutineScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "RoutineScheduler"
        
        // Work name for the routine generator worker
        private const val WORK_DAILY_ROUTINE = "daily_routine_generator"
        
        // The time at which to generate routines (3:00 AM)
        private val ROUTINE_GENERATION_TIME = LocalTime.of(3, 0)
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule the daily routine generator worker to run every day at midnight
     */
    fun scheduleDailyRoutineGeneration() {
        Log.d(TAG, "Scheduling daily routine generation")
        
        // Calculate the initial delay until the next 3:00 AM
        val now = LocalDateTime.now()
        val today3am = now.toLocalDate().atTime(ROUTINE_GENERATION_TIME)
        val next3am = if (now.isAfter(today3am)) {
            today3am.plusDays(1)
        } else {
            today3am
        }
        val initialDelay = Duration.between(now, next3am)
        
        // Create constraints - we require network to generate AI routines
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Create a periodic work request to run daily
        val routineWorkRequest = PeriodicWorkRequestBuilder<DailyRoutineWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_DAILY_ROUTINE)
            .build()
        
        // Enqueue the work request
        workManager.enqueueUniquePeriodicWork(
            WORK_DAILY_ROUTINE,
            ExistingPeriodicWorkPolicy.UPDATE,
            routineWorkRequest
        )
        
        Log.d(TAG, "Daily routine generation scheduled to start in ${initialDelay.toHours()} hours")
    }
    
    /**
     * Generate routine for tomorrow immediately
     */
    fun generateRoutineImmediately() {
        Log.d(TAG, "Triggering immediate routine generation")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val routineWorkRequest = OneTimeWorkRequestBuilder<DailyRoutineWorker>()
            .setConstraints(constraints)
            .addTag("${WORK_DAILY_ROUTINE}_immediate")
            .build()
        
        workManager.enqueue(routineWorkRequest)
    }
    
    /**
     * Cancel the scheduled routine generation
     */
    fun cancelScheduledGeneration() {
        workManager.cancelUniqueWork(WORK_DAILY_ROUTINE)
        Log.d(TAG, "Cancelled scheduled routine generation")
    }
} 