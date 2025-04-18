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
            
            // Force check for accessibility service first - this is critical for app blocking
            if (!isAccessibilityServiceEnabled()) {
                Log.d(TAG, "Accessibility service is NOT enabled - prompting user")
                MaterialAlertDialogBuilder(this)
                    .setTitle("App Blocking Not Working")
                    .setMessage("The accessibility service is required for app blocking to work. You must enable it in Settings.")
                    .setCancelable(false)
                    .setPositiveButton("Enable Now") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(
                            this, 
                            "Find and enable 'FocusGuard: App Blocker' in the list", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
            } else {
                Log.d(TAG, "Accessibility service is enabled")
                // Check for other required permissions only after accessibility is confirmed
                checkRequiredPermissions()
            }
            
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
    
    private fun checkRequiredPermissions() {
        try {
            // Check for Accessibility Service first (most important for app blocking)
            // Only show the dialog if we haven't verified accessibility and it's not enabled
            if (!prefs.getBoolean("accessibility_verified", false) && !isAccessibilityServiceEnabled()) {
                showAccessibilityPermissionDialog()
                return // Don't check other permissions until this one is handled
            }
            
            // Check other permissions if accessibility is granted
            checkOtherPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}", e)
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
        // Check if accessibility service is now enabled
        if (!prefs.getBoolean("accessibility_verified", false) && isAccessibilityServiceEnabled()) {
            // Mark as verified since it's now enabled
            prefs.edit().putBoolean("accessibility_verified", true).apply()
            Toast.makeText(
                this,
                "Accessibility permission granted successfully!",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Check if overlay permission is now enabled
        if (!prefs.getBoolean("overlay_verified", false) && Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("overlay_verified", true).apply()
        }
        
        // Check if usage stats permission is now enabled
        if (!prefs.getBoolean("usage_stats_verified", false) && hasUsageStatsPermission()) {
            prefs.edit().putBoolean("usage_stats_verified", true).apply()
        }
    }

    private fun hasBeenAskedRecently(prefKey: String): Boolean {
        val lastAsked = prefs.getLong(prefKey, 0)
        return lastAsked > 0
    }
    
    private fun showAccessibilityPermissionDialog() {
        // Don't show again if we've already asked recently
        if (hasBeenAskedRecently(PREF_ACCESSIBILITY_ASKED)) {
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("App blocking requires the accessibility service to be enabled. This is required only once. Would you like to enable it now?")
            .setPositiveButton("Enable Now") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                
                // Show a toast with instructions
                Toast.makeText(
                    this, 
                    "Find and enable 'FocusGuard: App Blocker' in the list", 
                    Toast.LENGTH_LONG
                ).show()
                
                markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
            }
            .setNegativeButton("Later") { _, _ ->
                markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Check if app blocker accessibility service is enabled
     * and prompt user to enable it if not
     */
    fun checkAppBlockerService() {
        // Skip if already verified to avoid duplicate prompts
        if (prefs.getBoolean("accessibility_verified", false)) {
            return
        }
        
        // Only check if we haven't asked recently
        if (hasBeenAskedRecently(PREF_ACCESSIBILITY_ASKED)) {
            return
        }
        
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val isServiceEnabled = enabledServices.any { 
            it.id.contains(packageName + "/.services.AppBlockerAccessibilityService") 
        }
        
        if (!isServiceEnabled) {
            val builder = AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage("The app blocker requires accessibility permission to monitor and block apps. Please enable it in the settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
                }
                .setNegativeButton("Later") { _, _ ->
                    markPermissionAsked(PREF_ACCESSIBILITY_ASKED)
                }
                .setCancelable(true)
            
            builder.show()
        }
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