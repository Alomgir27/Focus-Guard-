package com.focusguard.app.ui.apps

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppBlockRepository
import com.focusguard.app.viewmodels.AppBlockViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AppBlockSetupFragment : Fragment() {

    private lateinit var viewModel: AppBlockViewModel
    private lateinit var repository: AppBlockRepository
    
    private val TAG = "AppBlockSetupFragment"

    // UI elements
    private lateinit var appNameTextView: TextView
    private lateinit var switchAllDay: SwitchMaterial
    private lateinit var startTimeEditText: TextInputEditText
    private lateinit var endTimeEditText: TextInputEditText
    private lateinit var passwordTextView: TextView
    private lateinit var copyPasswordButton: Button
    private lateinit var setBlockButton: Button
    private lateinit var timeSelectionLayout: View
    
    // Chip group for days
    private lateinit var chipSunday: Chip
    private lateinit var chipMonday: Chip
    private lateinit var chipTuesday: Chip
    private lateinit var chipWednesday: Chip
    private lateinit var chipThursday: Chip
    private lateinit var chipFriday: Chip
    private lateinit var chipSaturday: Chip
    
    // Data
    private var startTime: LocalTime = LocalTime.of(9, 0) // Default 9:00 AM
    private var endTime: LocalTime = LocalTime.of(17, 0) // Default 5:00 PM
    private var password: String = ""
    private lateinit var packageName: String
    private lateinit var appName: String
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_block_setup, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        val args = arguments
        packageName = args?.getString("packageName") ?: ""
        appName = args?.getString("appName") ?: ""
        
        if (packageName.isEmpty()) {
            findNavController().navigateUp()
            return
        }
        
        // Initialize repository
        repository = MyApplication.appBlockRepository
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AppBlockViewModel::class.java]
        
        // Initialize UI elements
        initializeViews(view)
        
        // Generate a random password
        generateNewPassword()
        
        // Set up listeners
        setupListeners()
        
        // Set initial UI state
        updateUIState()
    }
    
    private fun initializeViews(view: View) {
        // Text views
        appNameTextView = view.findViewById(R.id.tvAppName)
        passwordTextView = view.findViewById(R.id.tvPassword)
        
        // Time selection
        switchAllDay = view.findViewById(R.id.switchAllDay)
        startTimeEditText = view.findViewById(R.id.etStartTime)
        endTimeEditText = view.findViewById(R.id.etEndTime)
        timeSelectionLayout = view.findViewById(R.id.layoutTimeSelection)
        
        // Day chips
        chipSunday = view.findViewById(R.id.chipSunday)
        chipMonday = view.findViewById(R.id.chipMonday)
        chipTuesday = view.findViewById(R.id.chipTuesday)
        chipWednesday = view.findViewById(R.id.chipWednesday)
        chipThursday = view.findViewById(R.id.chipThursday)
        chipFriday = view.findViewById(R.id.chipFriday)
        chipSaturday = view.findViewById(R.id.chipSaturday)
        
        // Buttons
        copyPasswordButton = view.findViewById(R.id.btnCopyPassword)
        setBlockButton = view.findViewById(R.id.btnSetBlock)
        
        // Set app name
        appNameTextView.text = appName
    }
    
    private fun setupListeners() {
        // Set time selection switch listener
        switchAllDay.setOnCheckedChangeListener { _, isChecked ->
            timeSelectionLayout.visibility = if (isChecked) View.GONE else View.VISIBLE
        }
        
        // Set time pickers
        startTimeEditText.setOnClickListener {
            showTimePickerDialog(true)
        }
        
        endTimeEditText.setOnClickListener {
            showTimePickerDialog(false)
        }
        
        // Set copy password button
        copyPasswordButton.setOnClickListener {
            copyPasswordToClipboard()
        }
        
        // Set block button
        setBlockButton.setOnClickListener {
            saveBlockSettings()
        }
    }
    
    private fun showTimePickerDialog(isStartTime: Boolean) {
        val timeToShow = if (isStartTime) startTime else endTime
        
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                if (isStartTime) {
                    startTime = LocalTime.of(hourOfDay, minute)
                    startTimeEditText.setText(startTime.format(timeFormatter))
                } else {
                    endTime = LocalTime.of(hourOfDay, minute)
                    endTimeEditText.setText(endTime.format(timeFormatter))
                }
            },
            timeToShow.hour,
            timeToShow.minute,
            true
        ).show()
    }
    
    private fun updateUIState() {
        // Set time text
        startTimeEditText.setText(startTime.format(timeFormatter))
        endTimeEditText.setText(endTime.format(timeFormatter))
        
        // Set password text
        passwordTextView.text = password
    }
    
    private fun generateNewPassword() {
        // Generate a random 8-digit password
        password = repository.generateRandomPassword()
        passwordTextView.text = password
    }
    
    private fun copyPasswordToClipboard() {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Block Password", password)
        clipboardManager.setPrimaryClip(clipData)
        
        Toast.makeText(requireContext(), "Password copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun getEnabledDaysBitfield(): Int {
        var bitfield = 0
        
        if (chipSunday.isChecked) bitfield = bitfield or (1 shl 0)
        if (chipMonday.isChecked) bitfield = bitfield or (1 shl 1)
        if (chipTuesday.isChecked) bitfield = bitfield or (1 shl 2)
        if (chipWednesday.isChecked) bitfield = bitfield or (1 shl 3)
        if (chipThursday.isChecked) bitfield = bitfield or (1 shl 4)
        if (chipFriday.isChecked) bitfield = bitfield or (1 shl 5)
        if (chipSaturday.isChecked) bitfield = bitfield or (1 shl 6)
        
        return bitfield
    }
    
    private fun saveBlockSettings() {
        val blockAllDay = switchAllDay.isChecked
        val enabledDays = getEnabledDaysBitfield()
        
        // Validate that at least one day is selected
        if (enabledDays == 0) {
            Toast.makeText(requireContext(), "Please select at least one day", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Ensure password is not empty
        if (password.isEmpty()) {
            generateNewPassword()
            Toast.makeText(requireContext(), "Generated a new password for security", Toast.LENGTH_SHORT).show()
        }
        
        // Validate times if not blocking all day
        if (!blockAllDay) {
            // Check that start time is before end time
            if (startTime >= endTime) {
                // Show warning if start time is after or equal to end time
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Time Settings Warning")
                    .setMessage("You've set the start time (${startTime.format(timeFormatter)}) to be after or equal to the end time (${endTime.format(timeFormatter)}). This means the app will be blocked overnight. Is this what you intended?")
                    .setPositiveButton("Yes, Continue") { _, _ ->
                        // User confirms, proceed with saving
                        doSaveBlockSettings(blockAllDay, enabledDays)
                    }
                    .setNegativeButton("No, Let Me Fix It") { _, _ ->
                        // User wants to fix the times
                        return@setNegativeButton
                    }
                    .show()
                return
            }
        }
        
        // All validations passed, proceed with saving
        doSaveBlockSettings(blockAllDay, enabledDays)
    }
    
    private fun doSaveBlockSettings(blockAllDay: Boolean, enabledDays: Int) {
        // Format times
        val startTimeStr = startTime.format(timeFormatter)
        val endTimeStr = endTime.format(timeFormatter)
        
        // Show a loading indicator
        setBlockButton.isEnabled = false
        setBlockButton.text = "Saving..."
        
        // Log the values being saved for debugging
    Log.d(TAG, "Saving block settings: packageName=$packageName, blockAllDay=$blockAllDay, " +
              "startTime=$startTimeStr, endTime=$endTimeStr, enabledDays=$enabledDays, " +
              "passwordLength=${password.length}")
        
        // Save to repository
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // First, make sure the app is in the blocked list and marked as active
                val blockedApp = BlockedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isActive = true,
                    startTime = if (blockAllDay) null else startTimeStr,
                    endTime = if (blockAllDay) null else endTimeStr,
                    blockAllDay = blockAllDay,
                    enabledDays = enabledDays,
                    password = password
                )
                
                Log.d(TAG, "Inserting blocked app: $packageName, isActive=true")
                repository.insertBlockedApp(blockedApp)
                
                // Then update the schedule
                Log.d(TAG, "Updating block schedule for: $packageName")
                repository.updateBlockedAppSchedule(
                    packageName = packageName,
                    startTime = if (blockAllDay) null else startTimeStr,
                    endTime = if (blockAllDay) null else endTimeStr,
                    blockAllDay = blockAllDay,
                    enabledDays = enabledDays,
                    password = password
                )
                
                // Force refresh of blocked apps list to update UI
                Log.d(TAG, "Sending broadcast to refresh blocked apps")
                val refreshIntent = Intent("com.focusguard.app.ACTION_REFRESH_BLOCKED_APPS")
                context?.sendBroadcast(refreshIntent)
                
                // Notify the service to reload blocked apps
                Log.d(TAG, "Sending broadcast to reload blocked apps in service")
                val serviceIntent = Intent("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
                context?.sendBroadcast(serviceIntent)
                
                // Add small delay to ensure signals are processed
                delay(300)  // Increased delay for better chance of signal processing
                
                // Verify the app was added to blocked list
                val verifyApp = repository.getBlockedApp(packageName)
                if (verifyApp != null && verifyApp.isActive) {
                    Log.d(TAG, "Verified app is in blocked list and active: $packageName")
                } else {
                    Log.w(TAG, "Failed to verify app in blocked list: $packageName")
                }
                
                Toast.makeText(requireContext(), "Block settings saved", Toast.LENGTH_SHORT).show()
                
                // Set a flag in shared preferences to force refresh the app list when returning
                val prefs = requireContext().getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("needs_refresh", true).apply()
                
                // Navigate back
                Log.d(TAG, "Block setup complete, navigating back")
                findNavController().navigateUp()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving block settings: ${e.message}", e)
                Toast.makeText(requireContext(), "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
                setBlockButton.isEnabled = true
                setBlockButton.text = "Set Block"
            }
        }
    }
} 