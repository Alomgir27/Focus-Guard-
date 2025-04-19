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
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Splash screen activity that displays the app logo
 * before launching the main activity
 */
class SplashActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val PREF_FILE = "app_permissions"
        private const val PREF_FIRST_LAUNCH = "first_launch"
        private const val ACCESSIBILITY_REMINDER_NOTIFICATION_ID = 10001
        private const val ACCESSIBILITY_REMINDER_CHANNEL_ID = "accessibility_reminder_channel"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        
        // Check if this is the first launch
        val isFirstLaunch = prefs.getBoolean(PREF_FIRST_LAUNCH, true)
        
        if (isFirstLaunch) {
            // Mark that the app has been launched
            prefs.edit().putBoolean(PREF_FIRST_LAUNCH, false).apply()
            
            // Create notification channel for accessibility reminders (for Android O+)
            createAccessibilityReminderChannel()
            
            // Show the accessibility permission dialog
            showAccessibilityPermissionDialog()
        } else {
            // Not first launch, proceed to main activity after delay
            proceedToMainActivity()
        }
    }
    
    private fun createAccessibilityReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Accessibility Permission Reminders"
            val description = "Notifications to remind you to enable required permissions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(
                ACCESSIBILITY_REMINDER_CHANNEL_ID,
                name,
                importance
            ).apply {
                this.description = description
            }
            
            // Register the channel with the system
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showAccessibilityPermissionDialog() {
        Log.d(TAG, "Showing accessibility permission dialog on first launch")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("FocusGuard requires accessibility service to block distracting apps. Would you like to enable it now?")
            .setCancelable(false)
            .setPositiveButton("Enable Now") { _, _ ->
                // Open accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                
                // Show a toast with instructions
                android.widget.Toast.makeText(
                    this, 
                    "Find and enable 'FocusGuard: App Blocker' in the list", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
                // Mark this permission as having been asked for
                prefs.edit().putLong("accessibility_asked", System.currentTimeMillis()).apply()
                
                // Proceed to main activity
                proceedToMainActivity()
            }
            .setNegativeButton("Later") { _, _ ->
                // Mark this permission as having been asked for
                prefs.edit().putLong("accessibility_asked", System.currentTimeMillis()).apply()
                
                // Show notification to remind user
                showAccessibilityReminderNotification()
                
                // Proceed to main activity
                proceedToMainActivity()
            }
            .show()
    }
    
    private fun showAccessibilityReminderNotification() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        val builder = NotificationCompat.Builder(this, ACCESSIBILITY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Accessibility Permission Required")
            .setContentText("FocusGuard needs accessibility permission to function properly")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ACCESSIBILITY_REMINDER_NOTIFICATION_ID, builder.build())
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
                    return it.contains("${packageName}/${packageName}.services.AppBlockerAccessibilityService")
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}", e)
            return false
        }
    }
    
    private fun proceedToMainActivity() {
        // Use a handler to delay loading the main activity
        Handler(Looper.getMainLooper()).postDelayed({
            // Start the main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            
            // Close this activity
            finish()
        }, 1500) // 1.5 seconds delay
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if accessibility permission has been granted
        if (isAccessibilityServiceEnabled()) {
            // Mark as verified
            prefs.edit().putBoolean("accessibility_verified", true).apply()
            
            // Cancel any pending accessibility reminder notifications
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(ACCESSIBILITY_REMINDER_NOTIFICATION_ID)
        }
    }
} 