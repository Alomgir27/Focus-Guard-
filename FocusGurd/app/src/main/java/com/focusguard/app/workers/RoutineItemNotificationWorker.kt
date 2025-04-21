package com.focusguard.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.focusguard.app.MainActivity
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.data.entity.RoutineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Worker to send notifications for upcoming routine items
 */
class RoutineItemNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RoutineItemNotificationWorker"
        
        // Extra data keys
        const val KEY_ITEM_ID = "item_id"
        const val KEY_ITEM_TITLE = "item_title"
        const val KEY_ITEM_DESCRIPTION = "item_description"
        const val KEY_ITEM_START_TIME = "item_start_time"
        const val KEY_ITEM_END_TIME = "item_end_time"
        const val KEY_ITEM_IS_FOCUS = "item_is_focus"
        const val KEY_ITEM_PRIORITY = "item_priority"
        
        // Notification IDs
        private const val NOTIFICATION_ID_BASE = 9000 // Different range from other notifications
        
        // Formatting
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting routine item notification worker")
            
            // Extract data about the routine item
            val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
            val title = inputData.getString(KEY_ITEM_TITLE) ?: "Routine Item"
            val description = inputData.getString(KEY_ITEM_DESCRIPTION) ?: ""
            val startTimeStr = inputData.getString(KEY_ITEM_START_TIME) ?: return@withContext Result.failure()
            val endTimeStr = inputData.getString(KEY_ITEM_END_TIME) ?: return@withContext Result.failure()
            val isFocus = inputData.getBoolean(KEY_ITEM_IS_FOCUS, false)
            val priority = inputData.getInt(KEY_ITEM_PRIORITY, 1)
            
            // Parse times
            val startTime = LocalDateTime.parse(startTimeStr)
            val endTime = LocalDateTime.parse(endTimeStr)
            val now = LocalDateTime.now()
            
            // Check if this routine item is still relevant
            if (endTime.isBefore(now)) {
                Log.d(TAG, "Routine item $itemId has already ended, not sending notification")
                return@withContext Result.success()
            }
            
            // Calculate notification ID based on item ID to avoid duplicates
            val notificationId = NOTIFICATION_ID_BASE + itemId.hashCode() % 1000
            
            // Create and show the notification
            showRoutineItemNotification(
                notificationId,
                title,
                description,
                startTime,
                endTime,
                isFocus,
                priority
            )
            
            Log.d(TAG, "Successfully showed notification for routine item: $title")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing routine item notification: ${e.message}", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Show a notification for a routine item
     */
    private fun showRoutineItemNotification(
        notificationId: Int,
        title: String,
        description: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        isFocus: Boolean,
        priority: Int
    ) {
        val now = LocalDateTime.now()
        
        // Determine if this is a "coming up" or "started" notification
        val isUpcoming = startTime.isAfter(now)
        val minutesToStart = if (isUpcoming) ChronoUnit.MINUTES.between(now, startTime) else 0
        
        // Create notification content based on timing
        val notificationTitle = if (isUpcoming) {
            if (minutesToStart <= 5) "Starting Soon: $title" else "Coming Up: $title"
        } else {
            "Now: $title"
        }
        
        val notificationText = if (isUpcoming) {
            val timeText = if (minutesToStart <= 5) {
                "Starting in a few minutes"
            } else {
                "Starting at ${startTime.format(timeFormatter)}"
            }
            
            if (description.isNotEmpty()) {
                "$timeText\n$description"
            } else {
                timeText
            }
        } else {
            val endTimeText = "Until ${endTime.format(timeFormatter)}"
            if (description.isNotEmpty()) {
                "$endTimeText\n$description"
            } else {
                endTimeText
            }
        }
        
        // Create intent for when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_ROUTINE_TAB", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set notification priority based on item priority and focus status
        val notificationPriority = when {
            isFocus -> NotificationCompat.PRIORITY_HIGH
            priority == 3 -> NotificationCompat.PRIORITY_HIGH
            priority == 2 -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        
        // Build the notification
        val builder = NotificationCompat.Builder(context, MyApplication.CHANNEL_ID_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(notificationPriority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(if (isFocus) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
        
        // For focus items or high priority items, make it more prominent
        if (isFocus || priority == 3) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            
            // Add actions if needed
            builder.addAction(
                R.drawable.ic_check,
                "Mark Complete",
                createMarkCompleteIntent(notificationId)
            )
        }
        
        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
                Log.d(TAG, "Notification displayed for routine item: $title")
            } catch (e: SecurityException) {
                Log.e(TAG, "No permission to show notification", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification: ${e.message}", e)
            }
        }
    }
    
    /**
     * Create a PendingIntent for marking a routine item as complete
     */
    private fun createMarkCompleteIntent(notificationId: Int): PendingIntent {
        // In a real implementation, this would use a broadcast receiver to mark the item complete
        // For this example, we'll just dismiss the notification when tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.focusguard.app.MARK_ROUTINE_COMPLETE"
            putExtra("notification_id", notificationId)
        }
        
        return PendingIntent.getActivity(
            context, 
            notificationId + 1000, // Different request code to avoid collision
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
} 