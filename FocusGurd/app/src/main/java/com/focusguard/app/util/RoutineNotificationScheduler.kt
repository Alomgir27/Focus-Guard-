package com.focusguard.app.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.RoutineItem
import com.focusguard.app.workers.RoutineItemNotificationWorker
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Schedules notifications for routine items
 */
class RoutineNotificationScheduler(private val context: Context) {

    companion object {
        private const val TAG = "RoutineNotificationSch"
        
        // Notification timing
        private const val ADVANCE_NOTIFICATION_MINUTES = 15 // Notify 15 min before start
        private const val SECONDARY_NOTIFICATION_MINUTES = 5 // Additional notification 5 min before
        private const val WORK_NAME_PREFIX = "routine_item_notification_"
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule notifications for all items in a routine
     */
    fun scheduleNotificationsForRoutine(routine: DailyRoutine) {
        Log.d(TAG, "Scheduling notifications for routine with ID: ${routine.id}, with ${routine.items.size} items")
        
        // Cancel existing notifications for this routine
        cancelExistingNotifications(routine.id)
        
        if (routine.items.isEmpty()) {
            Log.d(TAG, "No items in routine, skipping notification scheduling")
            return
        }
        
        val now = LocalDateTime.now()
        routine.items.forEach { item ->
            // Skip items that have already ended
            if (item.endTime.isBefore(now)) {
                Log.d(TAG, "Item '${item.title}' has already ended, skipping notification")
                return@forEach
            }
            
            // For items that are already in progress
            if (item.startTime.isBefore(now) && item.endTime.isAfter(now)) {
                // Schedule an "in progress" notification immediately
                scheduleRoutineItemNotification(item, 0, routine.id)
                Log.d(TAG, "Scheduled immediate notification for in-progress item: ${item.title}")
                return@forEach
            }
            
            // For upcoming items, schedule notifications in advance
            val minutesToStart = ChronoUnit.MINUTES.between(now, item.startTime)
            
            // Main advance notification (e.g., 15 minutes before)
            if (minutesToStart > ADVANCE_NOTIFICATION_MINUTES) {
                scheduleRoutineItemNotification(
                    item,
                    minutesToStart - ADVANCE_NOTIFICATION_MINUTES,
                    routine.id,
                    "_advance"
                )
                Log.d(TAG, "Scheduled advance notification for '${item.title}' (${minutesToStart - ADVANCE_NOTIFICATION_MINUTES} min from now)")
            }
            
            // Secondary notification (e.g., 5 minutes before)
            if (minutesToStart > SECONDARY_NOTIFICATION_MINUTES) {
                scheduleRoutineItemNotification(
                    item,
                    minutesToStart - SECONDARY_NOTIFICATION_MINUTES,
                    routine.id,
                    "_secondary"
                )
                Log.d(TAG, "Scheduled secondary notification for '${item.title}' (${minutesToStart - SECONDARY_NOTIFICATION_MINUTES} min from now)")
            }
            
            // Start notification (at the exact start time)
            if (minutesToStart > 0) {
                scheduleRoutineItemNotification(item, minutesToStart, routine.id, "_start")
                Log.d(TAG, "Scheduled start notification for '${item.title}' (${minutesToStart} min from now)")
            }
        }
    }
    
    /**
     * Schedule a notification for a single routine item
     */
    private fun scheduleRoutineItemNotification(
        item: RoutineItem,
        delayMinutes: Long,
        routineId: Int,
        suffix: String = ""
    ) {
        val inputData = Data.Builder().apply {
            putString(RoutineItemNotificationWorker.KEY_ITEM_ID, item.id)
            putString(RoutineItemNotificationWorker.KEY_ITEM_TITLE, item.title)
            putString(RoutineItemNotificationWorker.KEY_ITEM_DESCRIPTION, item.description ?: "")
            putString(RoutineItemNotificationWorker.KEY_ITEM_START_TIME, item.startTime.toString())
            putString(RoutineItemNotificationWorker.KEY_ITEM_END_TIME, item.endTime.toString())
            putBoolean(RoutineItemNotificationWorker.KEY_ITEM_IS_FOCUS, item.isFocusTime)
            putInt(RoutineItemNotificationWorker.KEY_ITEM_PRIORITY, item.priority)
        }.build()
        
        val notificationWork = OneTimeWorkRequestBuilder<RoutineItemNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag("routine_item_${item.id}")
            .addTag("routine_id_${routineId}")
            .build()
        
        // Use a unique work name for each notification to allow multiple notifications per item
        val workName = "${WORK_NAME_PREFIX}${item.id}${suffix}"
        
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            notificationWork
        )
    }
    
    /**
     * Cancel existing notifications for a routine
     */
    fun cancelExistingNotifications(routineId: Int) {
        workManager.cancelAllWorkByTag("routine_id_${routineId}")
        Log.d(TAG, "Cancelled existing notifications for routine ID: $routineId")
    }
    
    /**
     * Cancel all routine notifications
     */
    fun cancelAllRoutineNotifications() {
        workManager.cancelAllWorkByTag("routine_item_")
        Log.d(TAG, "Cancelled all routine notifications")
    }
} 