package com.focusguard.app.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.focusguard.app.R

/**
 * Device admin receiver for uninstall protection
 * 
 * This class handles events related to device admin privileges which helps
 * prevent easy uninstallation of the app
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            "Device admin enabled. Uninstall protection activated.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "Device admin disabled. App is now vulnerable to uninstallation.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling device admin will allow the app to be easily uninstalled, " +
                "which may affect your scheduled app blocking. Are you sure you want to continue?"
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // Handle password failure if needed
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        // Handle password success if needed
    }
} 