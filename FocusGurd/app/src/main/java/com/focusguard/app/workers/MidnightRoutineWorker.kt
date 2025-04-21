package com.focusguard.app.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.MyApplication
import com.focusguard.app.data.repository.DailyRoutineRepository
import com.focusguard.app.services.NotificationService
import com.focusguard.app.util.RoutineGenerator
import com.focusguard.app.util.RoutineNotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Worker that runs at midnight to generate a new daily routine
 */
class MidnightRoutineWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    
    companion object {
        private const val TAG = "MidnightRoutineWorker"
        private const val PREF_NAME = "midnight_routine_worker_prefs"
        private const val LAST_GENERATION_DATE_KEY = "last_generation_date"
    }
    
    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    private val dailyRoutineRepository: DailyRoutineRepository by lazy {
        MyApplication.dailyRoutineRepository
    }
    
    private val routineGenerator: RoutineGenerator by lazy {
        MyApplication.routineGenerator
    }
    
    private val routineNotificationScheduler: RoutineNotificationScheduler by lazy {
        RoutineNotificationScheduler(applicationContext)
    }
    
    private val notificationService: NotificationService by lazy {
        NotificationService(applicationContext)
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Midnight routine worker started")
        
        try {
            // Check if we've already generated a routine today
            val today = LocalDate.now()
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val lastGenerationDate = prefs.getString(LAST_GENERATION_DATE_KEY, "")
            
            if (lastGenerationDate == todayStr) {
                Log.d(TAG, "Already generated routine at midnight for today: $todayStr")
                return@withContext Result.success()
            }
            
            // First, check if today's routine exists and schedule notifications for it
            val todayRoutine = dailyRoutineRepository.getRoutineForDate(today)
            if (todayRoutine != null) {
                Log.d(TAG, "Found existing routine for today with ${todayRoutine.items.size} items")
                
                // Schedule notifications for today's items
                routineNotificationScheduler.scheduleNotificationsForRoutine(todayRoutine)
                Log.d(TAG, "Scheduled notifications for today's routine")
                
                // Send a notification about today's routine
                sendTodayRoutineNotification(todayRoutine)
            } else {
                Log.d(TAG, "No routine found for today, generating one")
                
                // Generate today's routine if it doesn't exist
                val newTodayRoutine = routineGenerator.generateRoutine(today)
                
                // Schedule notifications for it
                routineNotificationScheduler.scheduleNotificationsForRoutine(newTodayRoutine)
                Log.d(TAG, "Generated and scheduled notifications for today's routine with ${newTodayRoutine.items.size} items")
                
                // Send a notification about the new routine
                sendTodayRoutineNotification(newTodayRoutine)
            }
            
            // Generate routine for tomorrow
            val tomorrow = today.plusDays(1)
            Log.d(TAG, "Generating routine for tomorrow: $tomorrow")
            
            // Generate the routine
            val tomorrowRoutine = routineGenerator.generateRoutine(tomorrow)
            Log.d(TAG, "Successfully generated routine for $tomorrow with ${tomorrowRoutine.items.size} items")
            
            // Record successful generation
            prefs.edit().putString(LAST_GENERATION_DATE_KEY, todayStr).apply()
            
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating routine: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
    
    /**
     * Send a notification to the user about today's routine
     */
    private fun sendTodayRoutineNotification(routine: com.focusguard.app.data.entity.DailyRoutine) {
        if (routine.items.isEmpty()) {
            return
        }
        
        val firstItem = routine.items.minByOrNull { it.startTime }
        val title = "Your day is planned!"
        val content = if (firstItem != null) {
            "Your day starts with \"${firstItem.title}\" at ${firstItem.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}. Check your routine for today."
        } else {
            "Your routine for today is ready. Check it out!"
        }
        
        val notification = com.focusguard.app.data.entity.Notification(
            id = 0,
            title = title,
            content = content,
            type = com.focusguard.app.data.entity.NotificationType.GENERAL,
            createdAt = java.time.LocalDateTime.now(),
            wasShown = false
        )
        
        notificationService.sendNotification(notification)
        Log.d(TAG, "Sent notification about today's routine")
    }
} 