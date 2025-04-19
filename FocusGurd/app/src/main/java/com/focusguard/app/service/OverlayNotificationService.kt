package com.focusguard.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.focusguard.app.R
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Service to display overlay notifications with action buttons
 */
class OverlayNotificationService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var isShowing = false
    
    companion object {
        private const val TAG = "OverlayNotification"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_ID = "extra_id"
        
        /**
         * Show an overlay notification
         */
        fun showNotification(context: Context, title: String, message: String, type: NotificationType, id: Long = -1) {
            val intent = Intent(context, OverlayNotificationService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_ID, id)
            }
            context.startService(intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Notification"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val typeString = intent.getStringExtra(EXTRA_TYPE) ?: NotificationType.GENERAL.name
        val type = try {
            NotificationType.valueOf(typeString)
        } catch (e: Exception) {
            NotificationType.GENERAL
        }
        val id = intent.getLongExtra(EXTRA_ID, -1)
        
        showOverlayNotification(title, message, type, id)
        
        return START_NOT_STICKY
    }
    
    private fun showOverlayNotification(title: String, message: String, type: NotificationType, id: Long) {
        if (isShowing) {
            // If already showing a notification, remove it first
            try {
                windowManager.removeView(overlayView)
                isShowing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error removing existing view", e)
            }
        }
        
        try {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.overlay_notification, null)
            
            // Configure the notification content
            val titleTextView = overlayView.findViewById<TextView>(R.id.notificationTitle)
            val messageTextView = overlayView.findViewById<TextView>(R.id.notificationMessage)
            val closeButton = overlayView.findViewById<ImageButton>(R.id.closeButton)
            val likeButton = overlayView.findViewById<Button>(R.id.likeButton)
            val insightButton = overlayView.findViewById<Button>(R.id.insightButton)
            val cardView = overlayView.findViewById<CardView>(R.id.notificationCard)
            
            // Add a "View" button
            val viewButton = overlayView.findViewById<Button>(R.id.viewButton)
            
            titleTextView.text = title
            messageTextView.text = message
            
            // Set card background color based on notification type
            val backgroundColor = when (type) {
                NotificationType.MOTIVATION -> R.color.notification_motivation
                NotificationType.HABIT_REMINDER -> R.color.notification_habit
                NotificationType.RELIGIOUS_QUOTE -> R.color.notification_religious
                NotificationType.INSIGHT -> R.color.notification_insight
                else -> R.color.notification_general
            }
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColor))
            
            // Set up button actions
            closeButton.setOnClickListener {
                dismissNotification()
            }
            
            likeButton.setOnClickListener {
                // Record that user liked this notification
                CoroutineScope(Dispatchers.IO).launch {
                    // Save like to database
                    // This would need to be implemented in your data layer
                    Toast.makeText(applicationContext, "Saved to favorites!", Toast.LENGTH_SHORT).show()
                }
                dismissNotification()
            }
            
            insightButton.setOnClickListener {
                // Open main activity instead since InsightActivity doesn't exist yet
                val mainIntent = Intent(this, com.focusguard.app.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("notification_id", id)
                }
                startActivity(mainIntent)
                dismissNotification()
            }
            
            // Set up view button to open main activity
            viewButton.setOnClickListener {
                val mainIntent = Intent(this, com.focusguard.app.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("notification_id", id)
                }
                startActivity(mainIntent)
                dismissNotification()
            }
            
            // Only show insight button for certain notification types
            if (type != NotificationType.INSIGHT && type != NotificationType.HABIT_REMINDER) {
                insightButton.visibility = View.GONE
            }
            
            // Always show the View button
            viewButton.visibility = View.VISIBLE
            
            // Define the layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }
            
            // Add the view to the window
            windowManager.addView(overlayView, params)
            isShowing = true
            
            // Auto-dismiss after a delay
            overlayView.postDelayed({
                if (isShowing) {
                    dismissNotification()
                }
            }, 7000) // 7 seconds
            
            // Record that this notification was shown
            if (id != -1L) {
                markNotificationAsShown(id)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay notification", e)
        }
    }
    
    private fun dismissNotification() {
        if (isShowing) {
            try {
                windowManager.removeView(overlayView)
                isShowing = false
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error removing notification view", e)
            }
        }
    }
    
    private fun markNotificationAsShown(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update the notification in the database
                // You would need to implement this with your notification repository
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as shown", e)
            }
        }
    }
    
    override fun onDestroy() {
        if (isShowing) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view on destroy", e)
            }
        }
        super.onDestroy()
    }
} 