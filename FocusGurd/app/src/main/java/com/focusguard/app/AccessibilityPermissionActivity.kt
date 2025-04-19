package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Dedicated activity that requests accessibility permissions
 * with explicit guidance and no automatic redirections.
 */
class AccessibilityPermissionActivity : AppCompatActivity() {

    private val TAG = "AccessibilityPermission"
    private val PERMISSION_CHECK_INTERVAL = 2000L // 2 seconds
    private val handler = Handler(Looper.getMainLooper())
    private var lastSettingsOpenTime = 0L
    private var checkingActive = false
    private var settingsOpened = false
    
    // UI elements
    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var enableButton: Button
    private lateinit var manualCheckButton: Button
    private lateinit var instructionsView: TextView
    private lateinit var statusIcon: ImageView
    
    // Runnable that checks if permission has been granted
    private val permissionCheckRunnable = object : Runnable {
        override fun run() {
            val permissionEnabled = isAccessibilityServiceEnabled()
            Log.d(TAG, "Checking accessibility permission: enabled=$permissionEnabled")
            
            if (permissionEnabled) {
                // Permission was granted
                showPermissionGrantedUI()
                
                // Update the shared preference
                getSharedPreferences("app_permissions", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("accessibility_verified", true)
                    .apply()
                
                // Allow going to main activity after a short delay
                handler.postDelayed({
                    enableContinueButton()
                }, 1000)
                
                // Stop checking
                checkingActive = false
            } else if (checkingActive) {
                // Permission still not granted, keep checking
                handler.postDelayed(this, PERMISSION_CHECK_INTERVAL)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_permission)
        
        // Initialize UI elements
        titleTextView = findViewById(R.id.textViewPermissionTitle)
        messageTextView = findViewById(R.id.textViewPermissionMessage)
        enableButton = findViewById(R.id.buttonEnablePermission)
        statusIcon = findViewById(R.id.imageViewStatus)
        
        // Create instruction steps with formatting
        instructionsView = findViewById(R.id.textViewInstructions)
        setInstructionSteps()
        
        // Add a manual check button
        manualCheckButton = findViewById<Button>(R.id.buttonCheckPermission)
        if (manualCheckButton == null) {
            // If the button doesn't exist in layout, let the user know they need to update the app
            Toast.makeText(
                this,
                "Please update your app for the latest accessibility features",
                Toast.LENGTH_LONG
            ).show()
        } else {
            manualCheckButton.visibility = View.VISIBLE
            manualCheckButton.setOnClickListener {
                checkPermissionManually()
            }
        }
        
        titleTextView.text = "Accessibility Permission Required"
        messageTextView.text = "FocusGuard needs accessibility permission to block distracting apps. Click the button below to open settings and follow the instructions."
        
        // Check current permission state
        if (isAccessibilityServiceEnabled()) {
            showPermissionGrantedUI()
            enableContinueButton()
        } else {
            showPermissionNeededUI()
            // Auto-open accessibility settings after a short delay
            handler.postDelayed({
                openAccessibilitySettings()
            }, 500)
        }
        
        // Set up the button to open accessibility settings
        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }
    }
    
    private fun setInstructionSteps() {
        val steps = SpannableString("FOLLOW THESE STEPS:\n\n" +
                "1. Find 'FocusGuard: App Blocker' in the list\n\n" +
                "2. Tap on it and toggle the switch to ON\n\n" +
                "3. Tap OK or ALLOW if prompted\n\n" +
                "4. Return to this app\n\n" +
                "This app will automatically detect when permission is granted")
        
        // Make the title bold and larger
        steps.setSpan(RelativeSizeSpan(1.2f), 0, 18, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        steps.setSpan(ForegroundColorSpan(Color.RED), 0, 18, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        // Make the step numbers bold
        val stepPositions = listOf(20, 59, 101, 142)
        for (pos in stepPositions) {
            steps.setSpan(ForegroundColorSpan(Color.BLUE), pos, pos+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            steps.setSpan(RelativeSizeSpan(1.1f), pos, pos+2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        instructionsView.text = steps
    }
    
    private fun openAccessibilitySettings() {
        try {
            // Record the time when settings are opened
            lastSettingsOpenTime = System.currentTimeMillis()
            settingsOpened = true
            
            // Store the timestamp in shared preferences
            getSharedPreferences("app_permissions", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_accessibility_settings_time", lastSettingsOpenTime)
                .apply()
            
            // Start permission check runnable
            checkingActive = true
            handler.removeCallbacks(permissionCheckRunnable)
            handler.postDelayed(permissionCheckRunnable, PERMISSION_CHECK_INTERVAL)
            
            // Open accessibility settings directly
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            
            // Show toast with instructions
            Toast.makeText(
                this,
                "Find 'FocusGuard: App Blocker' and toggle ON",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}", e)
            Toast.makeText(this, "Error opening settings. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissionManually() {
        Log.d(TAG, "Manual permission check requested")
        Toast.makeText(this, "Checking permission status...", Toast.LENGTH_SHORT).show()
        
        // Start continuous checking
        checkingActive = true
        handler.removeCallbacks(permissionCheckRunnable)
        handler.post(permissionCheckRunnable)
    }
    
    private fun showPermissionNeededUI() {
        titleTextView.text = "Accessibility Permission Required"
        enableButton.text = "OPEN SETTINGS" 
        enableButton.isEnabled = true
        
        if (this::statusIcon.isInitialized) {
            statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            statusIcon.visibility = View.VISIBLE
        }
        
        // Make manual check button visible
        if (this::manualCheckButton.isInitialized) {
            manualCheckButton.visibility = View.VISIBLE
            manualCheckButton.text = "CHECK PERMISSION"
        }
    }
    
    private fun showPermissionGrantedUI() {
        titleTextView.text = "Accessibility Permission Granted!"
        messageTextView.text = "Thank you! FocusGuard will now be able to block distracting apps."
        
        if (this::statusIcon.isInitialized) {
            statusIcon.setImageResource(android.R.drawable.ic_dialog_info)
            statusIcon.visibility = View.VISIBLE
        }
        
        // Hide manual check button
        if (this::manualCheckButton.isInitialized) {
            manualCheckButton.visibility = View.GONE
        }
        
        // Stop continuous checking
        checkingActive = false
        handler.removeCallbacks(permissionCheckRunnable)
    }
    
    private fun enableContinueButton() {
        enableButton.text = "CONTINUE TO APP"
        enableButton.isEnabled = true
        
        // Change button action to go to main activity
        enableButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Don't stop checking when activity is paused
        // We want to continue checking while they are in settings
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check the current permission state
        if (isAccessibilityServiceEnabled()) {
            showPermissionGrantedUI()
            enableContinueButton()
        } else if (settingsOpened) {
            // They returned from settings, but permission isn't enabled yet
            Log.d(TAG, "Returned from settings but permission not granted yet")
            
            // Restart the checking if it's not already active
            if (!checkingActive) {
                checkingActive = true
                handler.removeCallbacks(permissionCheckRunnable)
                handler.postDelayed(permissionCheckRunnable, PERMISSION_CHECK_INTERVAL)
            }
            
            // Show toast reminding user to enable permission
            Toast.makeText(
                this,
                "Please enable accessibility permission for FocusGuard",
                Toast.LENGTH_LONG
            ).show()
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
} 