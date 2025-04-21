package com.focusguard.app.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.models.AppInfo
import com.focusguard.app.data.entity.BlockedAppEntity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AppListAdapter(
    private val onAppBlocked: (AppInfo, Boolean) -> Unit,
    private val onAppConfigureBlock: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val blockSwitch: SwitchMaterial = itemView.findViewById(R.id.block_switch)
        private val blockSchedule: TextView = itemView.findViewById(R.id.block_schedule)

        private var currentApp: AppInfo? = null

        init {
            // CRITICAL FIX: Ensure switch text is never null
            if (blockSwitch.text == null || blockSwitch.text.toString().isEmpty()) {
                blockSwitch.text = "Block"
            }
            
            // Set default text for text on/off states to prevent NPE
            if (blockSwitch.textOn == null) blockSwitch.textOn = "On"
            if (blockSwitch.textOff == null) blockSwitch.textOff = "Off"
            
            blockSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                currentApp?.let { app ->
                    Log.d("AppListAdapter", "Switch toggled for ${app.appName}: isChecked=$isChecked, app.isBlocked=${app.isBlocked}")
                    
                    if (isChecked && !app.isBlocked) {
                        // When turning on blocking, show the configuration screen
                        onAppConfigureBlock(app)
                    } else if (!isChecked && app.isBlocked) {
                        // When turning off, check if a password is required
                        checkPasswordBeforeUnblock(app)
                    } else if (isChecked != app.isBlocked) {
                        // Fix for mismatch between switch state and app state
                        blockSwitch.setOnCheckedChangeListener(null)
                        blockSwitch.isChecked = app.isBlocked
                        Log.d("AppListAdapter", "Fixed mismatch: Reset switch to ${app.isBlocked} for ${app.appName}")
                        
                        // Re-add the listener after a brief delay
                        blockSwitch.post {
                            blockSwitch.setOnCheckedChangeListener { _, newIsChecked ->
                                currentApp?.let { currentApp ->
                                    if (newIsChecked && !currentApp.isBlocked) {
                                        onAppConfigureBlock(currentApp)
                                    } else if (!newIsChecked && currentApp.isBlocked) {
                                        checkPasswordBeforeUnblock(currentApp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // No click listener on app name text or anywhere else on the card
        }

        private fun checkPasswordBeforeUnblock(app: AppInfo) {
            // If the app is already not blocked, no need to check password
            if (!app.isBlocked) {
                return
            }

            val context = itemView.context

            // Create a coroutine scope
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Get the blocked app to check if it has a password
                    val blockedApp = withContext(Dispatchers.IO) {
                        MyApplication.appBlockRepository.getBlockedApp(app.packageName)
                    }

                    if (blockedApp == null) {
                        // App is not in blocked list, proceed with unblock directly
                        onAppBlocked(app, false)
                        return@launch
                    }

                    // Check if the app is within its blocking schedule
                    val isWithinBlockTime = withContext(Dispatchers.IO) {
                        MyApplication.appBlockRepository.shouldBlockAppNow(app.packageName)
                    }

                    if (blockedApp.password.isNullOrEmpty() || !isWithinBlockTime) {
                        // No password set OR app is outside its scheduled block time
                        // In both cases, proceed with unblock directly without password check
                        withContext(Dispatchers.IO) {
                            // Create a BlockedAppEntity object with isActive = false
                            val updatedApp = BlockedAppEntity(
                                packageName = app.packageName,
                                appName = app.appName,
                                isActive = false,
                                startTime = blockedApp.startTime,
                                endTime = blockedApp.endTime,
                                blockAllDay = blockedApp.blockAllDay,
                                enabledDays = blockedApp.enabledDays,
                                password = blockedApp.password
                            )
                            // Update the app's status in the database
                            MyApplication.appBlockRepository.insertBlockedApp(updatedApp)
                            // Broadcast the change
                            val intent = Intent("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
                            context.sendBroadcast(intent)
                        }
                        onAppBlocked(app, false)
                        
                        // If bypassing password because outside block time, show a toast
                        if (!blockedApp.password.isNullOrEmpty() && !isWithinBlockTime) {
                            Toast.makeText(context, "App unblocked - outside scheduled block time", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // App has a password AND is within block time, show password dialog
                        showPasswordDialog(context, app, blockedApp.password)
                    }
                } catch (e: Exception) {
                    Log.e("AppListAdapter", "Error checking password: ${e.message}", e)
                    Toast.makeText(context, "Error unblocking app: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Revert switch if error occurs
                    blockSwitch.isChecked = true
                }
            }
        }
        
        private fun showPasswordDialog(context: Context, app: AppInfo, storedPassword: String) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.password_entry_dialog, null)
            val passwordEditText = dialogView.findViewById<EditText>(R.id.password_input)
            val titleText = dialogView.findViewById<TextView>(R.id.password_title)
            
            titleText.text = "Enter password to unblock ${app.appName}"

            val alertDialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create()
                
            // Get the submit and cancel buttons from the layout
            val submitButton = dialogView.findViewById<android.widget.Button>(R.id.submit_button)
            val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.cancel_button)
            
            // Set up submit button
            submitButton.setOnClickListener {
                val enteredPassword = passwordEditText.text.toString()
                if (enteredPassword.isEmpty()) {
                    Toast.makeText(context, "Please enter a password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Check if password matches
                if (enteredPassword == storedPassword) {
                    // Password correct, proceed with unblock
                    alertDialog.dismiss()
                    
                    // Create a coroutine scope to update the app status
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // Update the app's status in the database
                            withContext(Dispatchers.IO) {
                                MyApplication.appBlockRepository.updateBlockedAppStatus(app.packageName, false)
                                
                                // Broadcast the change to ensure all components are notified
                                val intent = Intent("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
                                context.sendBroadcast(intent)
                            }
                            
                            // Now call the callback to update UI
                            onAppBlocked(app, false)
                            
                            // Show success message
                            Toast.makeText(context, "${app.appName} has been unblocked", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("AppListAdapter", "Error unblocking app: ${e.message}", e)
                            // Revert switch if error occurs
                            blockSwitch.isChecked = true
                            Toast.makeText(context, "Error unblocking app: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Password incorrect, show error and leave dialog open
                    Toast.makeText(context, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show()
                    passwordEditText.text.clear()
                    
                    // No need to manually set switch state here - it will be handled by the 
                    // enhanced switch checked change listener when dialog is dismissed
                }
            }
            
            // Set up cancel button - ensuring switch reverts to correct state on cancel
            cancelButton.setOnClickListener {
                Log.d("AppListAdapter", "Password dialog canceled for ${app.appName}")
                
                // Reset the switch back to ON state without triggering the change listener
                blockSwitch.setOnCheckedChangeListener(null)
                blockSwitch.isChecked = true
                
                // Restore the listener after we've fixed the switch state
                blockSwitch.post {
                    blockSwitch.setOnCheckedChangeListener { _, isChecked ->
                        Log.d("AppListAdapter", "Switch changed for ${app.appName}: $isChecked")
                        
                        if (isChecked && !app.isBlocked) {
                            onAppConfigureBlock(app)
                        } else if (!isChecked && app.isBlocked) {
                            checkPasswordBeforeUnblock(app)
                        } else {
                            // Revert switch to match app state
                            blockSwitch.setOnCheckedChangeListener(null)
                            blockSwitch.isChecked = app.isBlocked
                            
                            // Re-add the listener with a post delay to prevent recursion
                            blockSwitch.post { 
                                blockSwitch.setOnCheckedChangeListener { _, newIsChecked ->
                                    if (newIsChecked && !app.isBlocked) {
                                        onAppConfigureBlock(app)
                                    } else if (!newIsChecked && app.isBlocked) {
                                        checkPasswordBeforeUnblock(app)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Dismiss the dialog
                alertDialog.dismiss()
            }
            
            alertDialog.show()
        }

        fun bind(app: AppInfo) {
            currentApp = app
            
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            packageName.text = app.packageName
            
            // CRITICAL FIX: Ensure switch text is never null during binding
            if (blockSwitch.text == null) {
                blockSwitch.text = "Block"
            }
            
            // Avoid triggering listener during binding by removing it first
            blockSwitch.setOnCheckedChangeListener(null)
            
            // Force update the switch state based on app.isBlocked
            Log.d("AppListAdapter", "Binding app ${app.appName} with blocked state: ${app.isBlocked}")
            blockSwitch.isChecked = app.isBlocked
            
            // Update block schedule information
            if (app.isBlocked) {
                // Fetch and display blocking schedule
                showBlockingSchedule(app.packageName)
            } else {
                // Hide schedule info if not blocked
                blockSchedule.visibility = View.GONE
            }
            
            // Re-establish the listener with improved handling
            blockSwitch.post {
                blockSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                    Log.d("AppListAdapter", "Switch changed for ${app.appName}: $isChecked")
                    
                    if (isChecked && !app.isBlocked) {
                        // When turning ON blocking and app isn't currently blocked, show the configuration screen
                        onAppConfigureBlock(app)
                    } 
                    else if (!isChecked && app.isBlocked) {
                        // Only when turning OFF blocking and app is currently blocked, check for password
                        checkPasswordBeforeUnblock(app)
                    }
                    else if (isChecked != app.isBlocked) {
                        // This handles switch state mismatch by resetting to match app state
                        Log.d("AppListAdapter", "State mismatch detected: switch=$isChecked, app.isBlocked=${app.isBlocked}")
                        blockSwitch.setOnCheckedChangeListener(null)
                        blockSwitch.isChecked = app.isBlocked
                        
                        // Re-add the listener
                        blockSwitch.post {
                            blockSwitch.setOnCheckedChangeListener { _, newIsChecked ->
                                if (newIsChecked && !app.isBlocked) {
                                    onAppConfigureBlock(app)
                                } else if (!newIsChecked && app.isBlocked) {
                                    checkPasswordBeforeUnblock(app)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private fun showBlockingSchedule(packageName: String) {
            // Show a loading indicator
            blockSchedule.text = "Loading schedule..."
            blockSchedule.visibility = View.VISIBLE
            
            // Launch a coroutine to fetch the block schedule
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val blockedApp = withContext(Dispatchers.IO) {
                        MyApplication.appBlockRepository.getBlockedApp(packageName)
                    }
                    
                    blockedApp?.let {
                        if (it.blockAllDay) {
                            // App is blocked all day
                            blockSchedule.text = "Blocked: All day"
                        } else if (it.startTime != null && it.endTime != null) {
                            // Format times for readability
                            val schedule = formatBlockSchedule(it.startTime, it.endTime, it.enabledDays)
                            blockSchedule.text = "Blocked: $schedule"
                        } else {
                            blockSchedule.visibility = View.GONE
                        }
                    } ?: run {
                        blockSchedule.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("AppListAdapter", "Error loading schedule: ${e.message}", e)
                    blockSchedule.visibility = View.GONE
                }
            }
        }
        
        private fun formatBlockSchedule(startTime: String, endTime: String, enabledDays: Int): String {
            // Format the time for better readability
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val outputFormatter = DateTimeFormatter.ofPattern("h:mma")
            
            try {
                val start = LocalTime.parse(startTime, formatter)
                val end = LocalTime.parse(endTime, formatter)
                
                val formattedStart = start.format(outputFormatter).toLowerCase()
                val formattedEnd = end.format(outputFormatter).toLowerCase()
                
                val days = formatDays(enabledDays)
                return if (days.isNotEmpty()) {
                    "$formattedStart-$formattedEnd ($days)"
                } else {
                    "$formattedStart-$formattedEnd"
                }
            } catch (e: Exception) {
                Log.e("AppListAdapter", "Error formatting time: ${e.message}", e)
                return "$startTime-$endTime"
            }
        }
        
        private fun formatDays(enabledDays: Int): String {
            val days = mutableListOf<String>()
            
            if ((enabledDays and (1 shl 0)) != 0) days.add("Sun")
            if ((enabledDays and (1 shl 1)) != 0) days.add("Mon")
            if ((enabledDays and (1 shl 2)) != 0) days.add("Tue")
            if ((enabledDays and (1 shl 3)) != 0) days.add("Wed")
            if ((enabledDays and (1 shl 4)) != 0) days.add("Thu")
            if ((enabledDays and (1 shl 5)) != 0) days.add("Fri")
            if ((enabledDays and (1 shl 6)) != 0) days.add("Sat")
            
            // If all days are enabled, just say "every day"
            if (days.size == 7) return "every day"
            
            // If weekdays only
            if (days.size == 5 && !days.contains("Sun") && !days.contains("Sat")) {
                return "weekdays"
            }
            
            // If weekends only
            if (days.size == 2 && days.contains("Sun") && days.contains("Sat")) {
                return "weekends"
            }
            
            return days.joinToString(", ")
        }
    }
}

class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
    override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        // Check if any important properties changed - especially isBlocked
        return oldItem.packageName == newItem.packageName &&
               oldItem.appName == newItem.appName &&
               oldItem.isBlocked == newItem.isBlocked
    }
} 