package com.focusguard.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.focusguard.app.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot receiver to restart services after device reboot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                // Check if accessibility service is enabled
                if (!isAccessibilityServiceEnabled(context)) {
                    // Create a notification to prompt the user to enable the service again
                    // This would be handled by a NotificationHelper class in a full implementation
                    // For now, we'll just start the settings activity
                    val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                }
                
                // Also reschedule all task notifications
                // In a complete implementation, this would be handled by a TaskScheduler helper class
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled == 1) {
            val packageName = context.packageName
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            services?.let {
                return it.contains("$packageName/$packageName.services.AppBlockerAccessibilityService")
            }
        }
        return false
    }
} 