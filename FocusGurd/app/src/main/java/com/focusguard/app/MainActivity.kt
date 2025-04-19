package com.focusguard.app

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.focusguard.app.databinding.ActivityMainBinding
import com.focusguard.app.fragments.NotificationHistoryFragment
import com.focusguard.app.fragments.NotificationSettingsFragment
import com.focusguard.app.receivers.DeviceAdminReceiver
import com.focusguard.app.ui.BaseActivity
import com.focusguard.app.ui.apps.AppsFragment
import com.focusguard.app.ui.usagestats.UsageStatsFragment
import com.focusguard.app.util.NotificationUtils
import com.focusguard.app.viewmodels.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.app.AlertDialog
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Switch
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.widget.SwitchCompat
import android.view.ViewGroup

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences
    
    // Device Policy Manager for Device Admin functionality
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponentName: ComponentName
    
    // Permission launcher for notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.d(TAG, "Notification permission denied")
            Toast.makeText(this, "Notifications are disabled. You can enable them in settings.", Toast.LENGTH_SHORT).show()
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val PREF_FILE = "app_permissions"
        private const val PREF_USAGE_STATS_ASKED = "usage_stats_asked"
        private const val PREF_ACCESSIBILITY_ASKED = "accessibility_asked"
        private const val PREF_ACCESSIBILITY_PERMISSION_RESPONDED = "accessibility_permission_responded"
        private const val PREF_DEVICE_ADMIN_ASKED = "device_admin_asked"
        private const val PREF_OVERLAY_ASKED = "overlay_asked"
        private const val PREF_NOTIFICATION_ASKED = "notification_asked"
        private const val PREF_PACKAGE_VISIBILITY_ASKED = "package_visibility_asked"
        private const val PERMISSION_ASK_DELAY_DAYS = 3 // Only ask again after 3 days
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate started")
        
        try {
            // Initialize SharedPreferences
            prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            
            // Initialize view binding
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "View binding initialized")
            
            // Add global switch text safety
            addGlobalSwitchTextSafety()
            
            // Initialize ViewModel
            viewModel = ViewModelProvider(this)[MainViewModel::class.java]
            
            // Initialize Device Admin components
            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            deviceAdminComponentName = ComponentName(this, DeviceAdminReceiver::class.java)
            
            // Hide debug text completely
            binding.debugText.text = ""
            binding.debugText.visibility = android.view.View.GONE
            
            // Setup bottom navigation
            setupBottomNavigation()
            
            // Load initial fragment if this is first creation
            if (savedInstanceState == null) {
                binding.bottomNavigation.selectedItemId = R.id.navigation_apps
            }
            
            // The accessibility service check is now streamlined and unified
            // No complex dialogs - a quick check and notification is sufficient
            
            // Check for other required permissions (not accessibility which is handled in SplashActivity)
            checkOtherPermissions()
            
            // Check if we were opened from a notification
            handleNotificationIntent(intent)
            
            Log.d(TAG, "MainActivity onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in MainActivity.onCreate: ${e.message}", e)
            // Show a simple crash message if possible
            try {
                Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { /* Ignore */ }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }
    
    private fun handleNotificationIntent(intent: Intent) {
        // Check if we should open notification history
        if (intent.getBooleanExtra("open_notification_history", false)) {
            // Get notification ID if present
            val notificationId = intent.getLongExtra("notification_id", -1L)
            
            // Create bundle with notification ID
            val bundle = Bundle().apply {
                if (notificationId != -1L) {
                    putLong("notification_id", notificationId)
                }
            }
            
            // Open notification history fragment with bundle
            val fragment = NotificationHistoryFragment().apply {
                arguments = bundle
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit()
        }
    }
    
    private fun openNotificationHistory() {
        safelyLoadFragment(NotificationHistoryFragment())
    }
    
    private fun setupBottomNavigation() {
        try {
            // Make sure the NavHostFragment is properly initialized
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? androidx.navigation.fragment.NavHostFragment
                ?: throw IllegalStateException("Nav host fragment not found")
            
            val navController = navHostFragment.navController
            binding.bottomNavigation.setupWithNavController(navController)
            
            binding.bottomNavigation.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_apps -> navController.navigate(R.id.appsFragment)
                    R.id.navigation_routine -> navController.navigate(R.id.dailyRoutineFragment)
                    R.id.navigation_usage_stats -> navController.navigate(R.id.usageStatsFragment)
                    R.id.navigation_settings -> navController.navigate(R.id.notificationSettingsFragment)
                    else -> return@setOnItemSelectedListener false
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation: ${e.message}", e)
            // Fallback to manual fragment loading if navigation fails
            binding.bottomNavigation.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_apps -> safelyLoadFragment(AppsFragment())
                    R.id.navigation_routine -> safelyLoadFragment(com.focusguard.app.fragments.DailyRoutineFragment())
                    R.id.navigation_usage_stats -> safelyLoadFragment(UsageStatsFragment())
                    R.id.navigation_settings -> safelyLoadFragment(NotificationSettingsFragment())
                    else -> return@setOnItemSelectedListener false
                }
                true
            }
        }
    }
    
    private fun safelyLoadFragment(fragment: Fragment): Boolean {
        return try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fragment: ${e.message}", e)
            false
        }
    }
    
    private fun checkOtherPermissions() {
        try {
            // Check for Usage Stats permission (for app blocking)
            if (!hasUsageStatsPermission() && shouldShowPermissionDialog(PREF_USAGE_STATS_ASKED)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage(getString(R.string.permission_usage_stats))
                    .setPositiveButton("Go to Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        markPermissionAsked(PREF_USAGE_STATS_ASKED)
                    }
                    .setNegativeButton("Later") { _, _ ->
                        markPermissionAsked(PREF_USAGE_STATS_ASKED)
                    }
                    .show()
            }
            
            // On Android 11+, we need to check package visibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && shouldShowPermissionDialog(PREF_PACKAGE_VISIBILITY_ASKED)) {
                // Show a dialog to make sure QUERY_ALL_PACKAGES permission is effective
                MaterialAlertDialogBuilder(this)
                    .setTitle("Package Visibility")
                    .setMessage("To see all installed apps, make sure to grant all permissions in App Info > Permissions. If apps are still not showing, please restart the application.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        markPermissionAsked(PREF_PACKAGE_VISIBILITY_ASKED)
                    }
                    .setNegativeButton("OK") { _, _ ->
                        markPermissionAsked(PREF_PACKAGE_VISIBILITY_ASKED)
                    }
                    .show()
            }
            
            // Check for Device Admin (for uninstall protection)
            if (!devicePolicyManager.isAdminActive(deviceAdminComponentName) && shouldShowPermissionDialog(PREF_DEVICE_ADMIN_ASKED)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage(getString(R.string.device_admin_description))
                    .setPositiveButton("Enable") { _, _ ->
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponentName)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                                getString(R.string.device_admin_description))
                        }
                        startActivity(intent)
                        markPermissionAsked(PREF_DEVICE_ADMIN_ASKED)
                    }
                    .setNegativeButton("Later") { _, _ ->
                        markPermissionAsked(PREF_DEVICE_ADMIN_ASKED)
                    }
                    .show()
            }
            
            // Check for notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasNotificationPermission() && shouldShowPermissionDialog(PREF_NOTIFICATION_ASKED)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Permission Required")
                        .setMessage(getString(R.string.permission_notification))
                        .setPositiveButton("Grant") { _, _ ->
                            NotificationUtils.requestNotificationPermission(
                                this,
                                notificationPermissionLauncher
                            )
                            markPermissionAsked(PREF_NOTIFICATION_ASKED)
                        }
                        .setNegativeButton("Later") { _, _ ->
                            markPermissionAsked(PREF_NOTIFICATION_ASKED)
                        }
                        .show()
                }
            }
            
            // Check for overlay permission
            if (!Settings.canDrawOverlays(this) && shouldShowPermissionDialog(PREF_OVERLAY_ASKED)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage(getString(R.string.permission_overlay))
                    .setPositiveButton("Go to Settings") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        markPermissionAsked(PREF_OVERLAY_ASKED)
                    }
                    .setNegativeButton("Later") { _, _ ->
                        markPermissionAsked(PREF_OVERLAY_ASKED)
                    }
                    .show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking other permissions: ${e.message}", e)
        }
    }
    
    private fun shouldShowPermissionDialog(prefKey: String): Boolean {
        val lastAsked = prefs.getLong(prefKey, 0)
        val now = System.currentTimeMillis()
        val daysDiff = (now - lastAsked) / (1000 * 60 * 60 * 24) // Convert to days

        // Show dialog if never asked before, or if PERMISSION_ASK_DELAY_DAYS have passed
        return lastAsked == 0L || daysDiff >= PERMISSION_ASK_DELAY_DAYS
    }
    
    private fun markPermissionAsked(prefKey: String) {
        prefs.edit().putLong(prefKey, System.currentTimeMillis()).apply()
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission: ${e.message}", e)
            return false
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
                    return it.contains("${packageName}/${packageName}.services.AppBlockerAccessibilityService")
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}", e)
            return false
        }
    }
    
    private fun hasNotificationPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification permission: ${e.message}", e)
            true
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if permissions were just granted
        checkPermissionsAfterReturn()
    }
    
    private fun checkPermissionsAfterReturn() {
        // Check if accessibility service is now revoked (simple quick check)
        if (!isAccessibilityServiceEnabled() && prefs.getBoolean("accessibility_verified", false)) {
            // The permission was previously granted but now revoked
            prefs.edit().putBoolean("accessibility_verified", false).apply()
            
            // Record that we asked about the permission
            markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
            
            // Only show the toast if we weren't just returning from accessibility settings
            // This prevents the annoying immediate toast when app first launches
            val currentTime = System.currentTimeMillis()
            val lastAccessibilitySettingsTime = prefs.getLong("last_accessibility_settings_time", 0)
            
            // Increased time threshold from 3 seconds to 10 seconds to prevent premature notifications
            Log.d(TAG, "Time since last settings access: ${currentTime - lastAccessibilitySettingsTime}ms")
            if (currentTime - lastAccessibilitySettingsTime > 10000) {
                // Just show a toast as a gentle reminder instead of interrupting with a dialog
                Toast.makeText(
                    this,
                    "Accessibility permission was revoked. App blocking won't work.",
                    Toast.LENGTH_LONG
                ).show()
                
                // Show a more detailed dialog to guide user to re-enable permissions
                showAccessibilityPermissionDialog()
            }
        } else if (isAccessibilityServiceEnabled() && !prefs.getBoolean("accessibility_verified", false)) {
            // Permission was just granted - update the flag and show a short toast
            prefs.edit().putBoolean("accessibility_verified", true).apply()
            Toast.makeText(this, "App blocking is now active", Toast.LENGTH_SHORT).show()
        }
        
        // Check if overlay permission is now enabled
        if (!prefs.getBoolean("overlay_verified", false) && Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("overlay_verified", true).apply()
        }
        
        // Check for usage stats permission
        if (!prefs.getBoolean("usage_stats_verified", false) && hasUsageStatsPermission()) {
            prefs.edit().putBoolean("usage_stats_verified", true).apply()
        }
    }
    
    /**
     * Show a dialog to guide the user to re-enable accessibility permission
     */
    private fun showAccessibilityPermissionDialog() {
        // Only show this dialog if we haven't recently asked
        if (!shouldShowPermissionDialog(PREF_ACCESSIBILITY_ASKED)) {
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("App blocking requires accessibility permission. Would you like to enable it now?")
            .setPositiveButton("Enable") { _, _ ->
                // Record that we asked and when we directed to settings
                markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
                prefs.edit().putLong("last_accessibility_settings_time", System.currentTimeMillis()).apply()
                
                // Send to dedicated permission activity instead of directly to settings
                val intent = Intent(this, AccessibilityPermissionActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Later") { _, _ ->
                markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
            }
            .show()
    }

    /**
     * Add a global safety mechanism to handle null switch text
     * This helps prevent crashes from SwitchCompat with null text
     */
    private fun addGlobalSwitchTextSafety() {
        val rootView = binding.root
        
        // Add a pre-draw listener to check for null text in switches
        rootView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // Find all switches in the view hierarchy and ensure they have text
                findAndFixSwitchesRecursively(rootView)
                return true
            }
        })
    }

    /**
     * Recursively find all switches in the view hierarchy and ensure they have text
     */
    private fun findAndFixSwitchesRecursively(view: View) {
        // Fix switch text if this is a switch
        when (view) {
            is SwitchCompat -> {
                if (view.text == null || view.text.toString().isEmpty()) {
                    view.text = "Switch"
                }
                // Ensure switch tracks are properly initialized
                if (view.textOn == null) view.textOn = "On"
                if (view.textOff == null) view.textOff = "Off"
            }
            is MaterialSwitch -> {
                if (view.text == null || view.text.toString().isEmpty()) {
                    view.text = "Switch"
                }
            }
            is SwitchMaterial -> {
                if (view.text == null || view.text.toString().isEmpty()) {
                    view.text = "Switch"
                }
            }
        }
        
        // Check children if this is a ViewGroup
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndFixSwitchesRecursively(view.getChildAt(i))
            }
        }
    }

    /**
     * Check if our Accessibility Service is enabled and prompt the user if not
     * This method is now obsolete as we handle accessibility checks in checkRequiredPermissions
     */
    private fun checkAccessibilityService() {
        // Method disabled - we handle this in checkRequiredPermissions and onResume now
        // Don't delete the method to avoid breaking any references to it
    }
}