package com.focusguard.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.focusguard.app.services.AppBlockerAccessibilityService

/**
 * BroadcastReceiver that receives RESTART_APP_BLOCKER intents to restart
 * the app blocking service if it was killed
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        
        if (intent.action == "com.focusguard.app.RESTART_APP_BLOCKER") {
            Log.d(TAG, "Restarting app blocker service")
            
            try {
                // Try to start the settings activity to re-enable the service
                val settingsIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // Create a pending intent for the notification
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context, 
                    0, 
                    settingsIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                context.startActivity(settingsIntent)
                
                // Show notification to user
                val notificationUtil = com.focusguard.app.util.NotificationUtils
                val notificationManager = android.app.NotificationManager::class.java.getMethod("from", Context::class.java)
                    .invoke(null, context) as android.app.NotificationManager
                
                // Create notification channel for Android O+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "service_restart",
                        "Service Restart",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager.createNotificationChannel(channel)
                }
                
                val builder = androidx.core.app.NotificationCompat.Builder(context, "service_restart")
                    .setSmallIcon(com.focusguard.app.R.drawable.logo2)
                    .setContentTitle("App Blocker Service Stopped")
                    .setContentText("Please re-enable the app blocking service in Accessibility Settings")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    // Add a View button that opens the accessibility settings
                    .addAction(android.R.drawable.ic_menu_view, "View", pendingIntent)
                
                notificationManager.notify(5000, builder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting service: ${e.message}", e)
            }
        }
    }
} 