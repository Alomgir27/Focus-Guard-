package com.focusguard.app.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.MyApplication
import com.focusguard.app.data.repository.DailyRoutineRepository
import com.focusguard.app.util.RoutineGenerator
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
            
            // Generate routine for tomorrow
            val tomorrow = today.plusDays(1)
            Log.d(TAG, "Generating routine for tomorrow: $tomorrow")
            
            // Generate the routine
            routineGenerator.generateRoutine(tomorrow)
            
            // Record successful generation
            prefs.edit().putString(LAST_GENERATION_DATE_KEY, todayStr).apply()
            
            Log.d(TAG, "Successfully generated routine for $tomorrow")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating routine: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
} 