package com.focusguard.app.util

import android.content.Context
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.focusguard.app.data.entity.NotificationType

/**
 * Helper class for showing notifications
 */
object NotificationHelper {
    
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