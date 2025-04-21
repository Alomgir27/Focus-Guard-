package com.focusguard.app.workers

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.data.repository.NotificationRepository
import com.focusguard.app.services.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import com.focusguard.app.data.entity.Notification

class NotificationWorker(
    context: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "NotificationWorker"
    }
    
    private val notificationService = NotificationService(context)
    private val notificationRepository = MyApplication.notificationRepository
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting notification worker")
        
        return try {
            // Check if we have a specific notification type from input data
            val specificType = inputData.getString("notification_type")?.let {
                NotificationType.valueOf(it)
            }
            
            // Skip general notifications - they're annoying when app is in background
            if (specificType == NotificationType.GENERAL) {
                Log.d(TAG, "Skipping general notification as they're distracting")
                return Result.success()
            }
            
            // If no specific type was specified or it's GENERAL, select an interesting type instead
            val notificationType = when (specificType) {
                null, NotificationType.GENERAL -> {
                    // Choose a more useful notification type instead of GENERAL
                    val types = listOf(
                        NotificationType.MOTIVATION,
                        NotificationType.HABIT_REMINDER,
                        NotificationType.INSIGHT
                    )
                    types.random()
                }
                else -> specificType
            }
            
            // Generate and send notification
            generateAndSendNotification(notificationType)
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification worker", e)
            Result.failure()
        }
    }
    
    private suspend fun generateAndSendNotification(type: NotificationType) {
        Log.d(TAG, "Generating notification for type: $type")
        
        try {
            // Generate notification content based on type
            val notification = generateSimpleNotification(type)
            
            // Store in database
            val notificationId = notificationRepository.addNotification(notification)
            Log.d(TAG, "Stored notification in database with ID: $notificationId")
            
            // Send the notification
            notificationService.queueNotification(notification.copy(id = notificationId))
            
            Log.d(TAG, "Notification queued for delivery")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating notification", e)
            
            // Fallback to a simple notification in case of error
            val fallbackNotification = Notification(
                id = 0,
                title = "Focus Reminder",
                content = "Remember to take a moment to focus on what matters today.",
                type = NotificationType.MOTIVATION, // Using MOTIVATION instead of GENERAL
                createdAt = LocalDateTime.now(),
                wasShown = false
            )
            
            val notificationId = notificationRepository.addNotification(fallbackNotification)
            notificationService.queueNotification(fallbackNotification.copy(id = notificationId))
        }
    }
    
    /**
     * Generate a simple notification based on type
     */
    private fun generateSimpleNotification(type: NotificationType): Notification {
        val (title, content) = when (type) {
            NotificationType.MOTIVATION -> Pair(
                "Daily Motivation",
                "Success is not final, failure is not fatal: It is the courage to continue that counts."
            )
            NotificationType.HABIT_REMINDER -> Pair(
                "Habit Check-in",
                "Have you taken time for your important habits today? Small consistent actions lead to big results."
            )
            NotificationType.RELIGIOUS_QUOTE -> Pair(
                "Daily Reflection",
                "Patience and perseverance have a magical effect before which difficulties disappear and obstacles vanish."
            )
            NotificationType.INSIGHT -> Pair(
                "Focus Insight",
                "You've been making steady progress on your goals. Remember to take breaks and celebrate small wins along the way!"
            )
            NotificationType.APP_USAGE_WARNING -> Pair(
                "Usage Reminder",
                "Taking regular breaks from screen time helps maintain focus and productivity."
            )
            NotificationType.GENERAL -> Pair(
                "Focus Reminder", // Changed from "Focus Guard" to be less annoying
                "A small pause to reflect can make your day more productive and meaningful."
            )
        }
        
        return Notification(
            id = 0,
            title = title,
            content = content,
            type = type,
            createdAt = LocalDateTime.now(),
            wasShown = false
        )
    }
} 