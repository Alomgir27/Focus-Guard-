package com.focusguard.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.MainActivity
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.data.repository.NotificationRepository
import com.focusguard.app.data.repository.UserHabitRepository
import com.focusguard.app.data.repository.UserInsightRepository
import com.focusguard.app.util.NotificationUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue
import android.widget.RemoteViews

class NotificationService(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationService"
        
        // Notification channel IDs
        const val CHANNEL_MOTIVATION = "motivation_notifications"
        const val CHANNEL_HABIT_REMINDER = "habit_reminder_notifications"
        const val CHANNEL_RELIGIOUS = "religious_notifications"
        const val CHANNEL_INSIGHTS = "insight_notifications"
        const val CHANNEL_GENERAL = "channel_general"
        
        // Notification ID base values - each type will have a range of 1000 IDs
        private const val NOTIFICATION_ID_MOTIVATION = 1000
        private const val NOTIFICATION_ID_HABIT_REMINDER = 2000
        private const val NOTIFICATION_ID_RELIGIOUS = 3000
        private const val NOTIFICATION_ID_INSIGHTS = 4000
        private const val NOTIFICATION_ID_GENERAL = 5000
        
        // Max notifications per day
        const val MAX_DAILY_NOTIFICATIONS = 5
        
        // Group key for bundling notifications
        const val GROUP_KEY_NOTIFICATIONS = "com.focusguard.app.NOTIFICATIONS"
        
        // Max notification content length
        private const val MAX_NOTIFICATION_CONTENT_LENGTH = 500
        
        // Notification queue to manage notifications
        private val notificationQueue = ConcurrentLinkedQueue<Notification>()
        private val handler = Handler(Looper.getMainLooper())
        private var isProcessingQueue = false
        private const val QUEUE_PROCESSING_DELAY_MS = 3000L // 3 seconds between notifications
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationRepository = MyApplication.notificationRepository
    private val userHabitRepository = MyApplication.userHabitRepository
    private val userInsightRepository = MyApplication.userInsightRepository
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates the notification channels for different notification types
     */
    private fun createNotificationChannels() {
        // Only needed for Android 8.0 (API 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create audio attributes for sound
            val audioAttributes = NotificationUtils.createNotificationAudioAttributes()
            val soundUri = NotificationUtils.getNotificationSoundUri(context)
            
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_MOTIVATION,
                    "Motivational Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily motivational messages"
                    setShowBadge(true)
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                },
                
                NotificationChannel(
                    CHANNEL_HABIT_REMINDER,
                    "Habit Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders about habits and goals"
                    setShowBadge(true)
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                },
                
                NotificationChannel(
                    CHANNEL_RELIGIOUS,
                    "Religious Quotes",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Quranic verses and religious reflections"
                    setShowBadge(true)
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                },
                
                NotificationChannel(
                    CHANNEL_INSIGHTS,
                    "Personalized Insights",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Insights about your habits and usage patterns"
                    setShowBadge(true)
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                },
                
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "General tips and information"
                    setShowBadge(false)
                    enableVibration(false)
                }
            )
            
            // Register all channels
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Created notification channel: ${channel.id}")
            }
        }
    }
    
    /**
     * Queues a notification to be sent later
     */
    fun queueNotification(notification: Notification) {
        // Add to queue
        notificationQueue.add(notification)
        Log.d(TAG, "Added notification to queue: ${notification.title}")
        
        // Start processing queue if not already doing so
        if (!isProcessingQueue) {
            processNotificationQueue()
        }
    }
    
    /**
     * Optimize notification content to be shorter and more significant
     * This is only used for the system notifications, not for storage in the database
     */
    private fun optimizeNotificationContent(notification: Notification): String {
        var optimizedContent = notification.content
        
        // If content is too long, trim it for display in system notification
        if (optimizedContent.length > MAX_NOTIFICATION_CONTENT_LENGTH) {
            // Find the last complete sentence within the limit
            val endIdx = findLastSentenceBreak(optimizedContent, MAX_NOTIFICATION_CONTENT_LENGTH)
            optimizedContent = optimizedContent.substring(0, endIdx)
            
            // Add ellipsis if we trimmed the content
            if (endIdx < notification.content.length) {
                optimizedContent += "..."
            }
        }
        
        return optimizedContent
    }
    
    /**
     * Find the last sentence break (period, question mark, exclamation) within a given limit
     */
    private fun findLastSentenceBreak(text: String, limit: Int): Int {
        val actualLimit = Math.min(text.length, limit)
        val lastPeriod = text.lastIndexOf(".", actualLimit)
        val lastQuestion = text.lastIndexOf("?", actualLimit)
        val lastExclamation = text.lastIndexOf("!", actualLimit)
        
        val lastBreak = maxOf(lastPeriod, lastQuestion, lastExclamation)
        
        // If no sentence break was found, just cut at the limit
        return if (lastBreak > 0) lastBreak + 1 else actualLimit
    }
    
    /**
     * Process the notification queue
     */
    private fun processNotificationQueue() {
        if (notificationQueue.isEmpty() || isProcessingQueue) {
            return
        }
        
        isProcessingQueue = true
        val notification = notificationQueue.poll()
        
        notification?.let {
            sendNotification(it)
            
            // Schedule next notification processing after delay
            handler.postDelayed({
                isProcessingQueue = false
                if (notificationQueue.isNotEmpty()) {
                    processNotificationQueue()
                }
            }, QUEUE_PROCESSING_DELAY_MS)
        } ?: run {
            isProcessingQueue = false
        }
    }
    
    /**
     * Sends a notification to the user
     */
    fun sendNotification(notification: Notification): Int {
        Log.d(TAG, "Sending notification: ${notification.title}")
        
        val channelId = getChannelIdForType(notification.type)
        val notificationId = getNotificationIdForType(notification.type)
        
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_id", notification.id)
            // Add action to open specific notification in notification history
            putExtra("open_notification_history", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Optimize content only for the system notification display
        val optimizedContent = optimizeNotificationContent(notification)
        
        // Create custom remote view for the notification with logo
        val customView = RemoteViews(context.packageName, R.layout.custom_notification_layout)
        customView.setTextViewText(R.id.textViewNotificationTitle, notification.title)
        customView.setTextViewText(R.id.textViewNotificationContent, optimizedContent)
        customView.setImageViewResource(R.id.imageViewNotificationLogo, R.drawable.logo)
        
        // Create notification builder with optimized content
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(optimizedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(optimizedContent))
            .setPriority(getPriorityForType(notification.type))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_NOTIFICATIONS)
            .setCategory(getCategoryForType(notification.type))
            .setCustomBigContentView(customView)
            // Add a View button to the notification
            .addAction(android.R.drawable.ic_menu_view, "View", pendingIntent)
        
        // Add sound for pre-Oreo devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(NotificationUtils.getNotificationSoundUri(context))
        }
        
        try {
            notificationManager.notify(notificationId, builder.build())
            
            // Check if we need to show a summary notification for bundling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                showSummaryNotification()
            }
            
            // Update notification status in database
            val updatedNotification = notification.copy(wasShown = true)
            
            // Launch in a coroutine scope from companion object
            MainScope().launch {
                notificationRepository.updateNotification(updatedNotification)
            }
            
            Log.d(TAG, "Notification sent with ID: $notificationId")
            return notificationId
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
            return -1
        }
    }
    
    /**
     * Shows a summary notification for bundling on Android N+
     */
    private fun showSummaryNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Create an intent to launch the main activity
            val intent = Intent(context, com.focusguard.app.MainActivity::class.java)
            intent.putExtra("open_notification_history", true)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
                .setContentTitle("New Notifications")
                .setContentText("You have new notifications")
                .setSmallIcon(R.drawable.ic_notification)
                .setGroup(GROUP_KEY_NOTIFICATIONS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                // Add a View button that opens notification history
                .addAction(android.R.drawable.ic_menu_view, "View All", pendingIntent)
                .build()
            
            notificationManager.notify(9999, summaryNotification)
        }
    }
    
    /**
     * Get the appropriate channel ID for the notification type
     */
    private fun getChannelIdForType(type: com.focusguard.app.data.entity.NotificationType): String {
        return when (type) {
            com.focusguard.app.data.entity.NotificationType.MOTIVATION -> CHANNEL_MOTIVATION
            com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER -> CHANNEL_HABIT_REMINDER
            com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE -> CHANNEL_RELIGIOUS
            com.focusguard.app.data.entity.NotificationType.INSIGHT -> CHANNEL_INSIGHTS
            com.focusguard.app.data.entity.NotificationType.APP_USAGE_WARNING -> CHANNEL_INSIGHTS
            com.focusguard.app.data.entity.NotificationType.GENERAL -> CHANNEL_GENERAL
        }
    }
    
    /**
     * Get a unique notification ID for the type
     */
    private fun getNotificationIdForType(type: com.focusguard.app.data.entity.NotificationType): Int {
        val baseId = when (type) {
            com.focusguard.app.data.entity.NotificationType.MOTIVATION -> NOTIFICATION_ID_MOTIVATION
            com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER -> NOTIFICATION_ID_HABIT_REMINDER
            com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE -> NOTIFICATION_ID_RELIGIOUS
            com.focusguard.app.data.entity.NotificationType.INSIGHT -> NOTIFICATION_ID_INSIGHTS
            com.focusguard.app.data.entity.NotificationType.APP_USAGE_WARNING -> NOTIFICATION_ID_INSIGHTS
            com.focusguard.app.data.entity.NotificationType.GENERAL -> NOTIFICATION_ID_GENERAL
        }
        
        // Use the current time to generate a unique ID within the range
        return baseId + (System.currentTimeMillis() % 1000).toInt()
    }
    
    /**
     * Get the appropriate priority for the notification type
     */
    private fun getPriorityForType(type: com.focusguard.app.data.entity.NotificationType): Int {
        return when (type) {
            com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER -> NotificationCompat.PRIORITY_HIGH
            com.focusguard.app.data.entity.NotificationType.APP_USAGE_WARNING -> NotificationCompat.PRIORITY_HIGH
            com.focusguard.app.data.entity.NotificationType.INSIGHT -> NotificationCompat.PRIORITY_DEFAULT
            com.focusguard.app.data.entity.NotificationType.MOTIVATION -> NotificationCompat.PRIORITY_DEFAULT
            com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE -> NotificationCompat.PRIORITY_DEFAULT
            com.focusguard.app.data.entity.NotificationType.GENERAL -> NotificationCompat.PRIORITY_LOW
        }
    }
    
    /**
     * Get the appropriate category for the notification type
     */
    private fun getCategoryForType(type: com.focusguard.app.data.entity.NotificationType): String {
        return when (type) {
            com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER -> NotificationCompat.CATEGORY_REMINDER
            com.focusguard.app.data.entity.NotificationType.APP_USAGE_WARNING -> NotificationCompat.CATEGORY_ALARM
            com.focusguard.app.data.entity.NotificationType.INSIGHT -> NotificationCompat.CATEGORY_RECOMMENDATION
            com.focusguard.app.data.entity.NotificationType.MOTIVATION -> NotificationCompat.CATEGORY_RECOMMENDATION
            com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE -> NotificationCompat.CATEGORY_RECOMMENDATION
            com.focusguard.app.data.entity.NotificationType.GENERAL -> NotificationCompat.CATEGORY_STATUS
        }
    }
    
    /**
     * Cancel a specific notification
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
} 