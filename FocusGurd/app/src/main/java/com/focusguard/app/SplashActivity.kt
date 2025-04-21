package com.focusguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

/**
 * Splash screen activity that displays the app logo
 * before launching the main activity
 */
class SplashActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val PREF_FILE = "app_permissions"
        private const val PREF_ACCESSIBILITY_PERMISSION_RESPONDED = "accessibility_permission_responded"
        private const val ACCESSIBILITY_REMINDER_NOTIFICATION_ID = 10001
        private const val ACCESSIBILITY_REMINDER_CHANNEL_ID = "accessibility_reminder_channel"
        private const val SPLASH_DISPLAY_TIME = 1000L // 1 second
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        
        // Create notification channel for accessibility reminders
        createAccessibilityReminderChannel()
        
        // Always check accessibility permission status on app start
        checkAccessibilityPermission()
    }
    
    private fun checkAccessibilityPermission() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        
        // Detailed logging to help diagnose permission flow
        Log.d(TAG, "Splash - Accessibility enabled: $isAccessibilityEnabled")
        
        if (isAccessibilityEnabled) {
            // Permission is enabled, update preferences and proceed to main activity
            Log.d(TAG, "Accessibility service is enabled, proceeding to MainActivity")
            prefs.edit()
                .putBoolean(PREF_ACCESSIBILITY_PERMISSION_RESPONDED, true)
                .putBoolean("accessibility_verified", true)
                .apply()
                
            // Proceed to main activity after short delay
            proceedToMainActivity(SPLASH_DISPLAY_TIME)
        } else {
            // If accessibility is not enabled, always open the permission activity
            Log.d(TAG, "Accessibility not enabled, going to AccessibilityPermissionActivity")
            
            // Launch the dedicated accessibility permission activity
            val intent = Intent(this, AccessibilityPermissionActivity::class.java)
            startActivity(intent)
            finish() // End this activity
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // When we resume, check if accessibility is now enabled
        if (isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Splash onResume - Accessibility enabled, proceeding to MainActivity")
            prefs.edit().putBoolean("accessibility_verified", true).apply()
            proceedToMainActivity(300) // Short delay before proceeding to main
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityEnabled = try {
                Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }
            
            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                services?.let {
                    val serviceMatch = "${packageName}/${packageName}.services.AppBlockerAccessibilityService"
                    val isEnabled = it.contains(serviceMatch)
                    Log.d(TAG, "Accessibility check: enabled=$isEnabled, services=$services, looking for=$serviceMatch")
                    return isEnabled
                }
            }
            Log.d(TAG, "Accessibility not enabled in system")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}", e)
            return false
        }
    }
    
    private fun createAccessibilityReminderChannel() {
        // This function is intentionally left empty to prevent creating notification channels
        // for accessibility permission reminders
    }
    
    private fun proceedToMainActivity(delayMillis: Long = 0) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, delayMillis)
    }
} 