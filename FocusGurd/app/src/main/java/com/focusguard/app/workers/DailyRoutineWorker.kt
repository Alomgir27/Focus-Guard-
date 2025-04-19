package com.focusguard.app.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.MyApplication
import com.focusguard.app.data.repository.DailyRoutineRepository
import com.focusguard.app.util.RoutineGenerator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailyRoutineWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyRoutineWorker"
        private const val LAST_GENERATION_DATE_KEY = "last_routine_generation_date"
        private const val PREF_NAME = "daily_routine_prefs"
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
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting DailyRoutineWorker")
        
        try {
            // Check if we've already generated a routine today
            val today = LocalDate.now()
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val lastGenerationDate = prefs.getString(LAST_GENERATION_DATE_KEY, "")
            
            if (lastGenerationDate == todayStr) {
                Log.d(TAG, "Already generated routine for today: $todayStr")
                return Result.success()
            }
            
            // Generate routine for tomorrow
            val tomorrow = today.plusDays(1)
            Log.d(TAG, "Generating routine for tomorrow: $tomorrow")
            
            // Get user preferences from SharedPreferences or DB
            val userPreferences = getUserPreferences()
            
            // Generate the routine
            val routine = routineGenerator.generateRoutine(tomorrow, userPreferences)
            
            // Record successful generation
            prefs.edit().putString(LAST_GENERATION_DATE_KEY, todayStr).apply()
            
            Log.d(TAG, "Successfully generated routine for tomorrow with ${routine.items.size} items")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily routine", e)
            return Result.retry()
        }
    }
    
    private fun getUserPreferences(): Map<String, Any> {
        // In a real implementation, you would get user preferences from SharedPreferences or database
        // For now, we'll return default values
        val wakeupHour = prefs.getInt("wakeup_hour", 6)
        val wakeupMinute = prefs.getInt("wakeup_minute", 0)
        val sleepHour = prefs.getInt("sleep_hour", 22)
        val sleepMinute = prefs.getInt("sleep_minute", 0)
        val focusHours = prefs.getInt("focus_hours", 4)
        
        val wakeupTime = java.time.LocalTime.of(wakeupHour, wakeupMinute)
        val sleepTime = java.time.LocalTime.of(sleepHour, sleepMinute)
        
        return mapOf(
            "wakeup_time" to wakeupTime,
            "sleep_time" to sleepTime,
            "focus_hours" to focusHours
        )
    }
} 