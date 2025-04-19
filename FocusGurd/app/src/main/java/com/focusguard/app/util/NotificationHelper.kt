package com.focusguard.app.util

import android.content.Context
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.focusguard.app.data.entity.NotificationType
import com.focusguard.app.service.OverlayNotificationService

/**
 * Helper class for showing notifications
 */
object NotificationHelper {

    /**
     * Show an overlay notification
     * @param context The context
     * @param title The notification title
     * @param message The notification message
     * @param type The notification type
     * @param id Optional notification ID for tracking
     * @return true if shown, false if permission not granted
     */
    fun showOverlayNotification(
        context: Context, 
        title: String, 
        message: String, 
        type: NotificationType = NotificationType.GENERAL,
        id: Long = -1
    ): Boolean {
        // Check if we have the permission
        if (!checkOverlayPermission(context)) {
            // Open settings to request permission
            requestOverlayPermission(context)
            return false
        }
        
        // Show the notification
        OverlayNotificationService.showNotification(context, title, message, type, id)
        return true
    }
    
    /**
     * Check if the app has permission to draw overlays
     */
    fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Before Android M, the permission was granted at install time
        }
    }
    
    /**
     * Request permission to draw overlays
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
} 