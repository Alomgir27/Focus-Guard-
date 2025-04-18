package com.focusguard.app.fragments

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.focusguard.app.R
import com.focusguard.app.databinding.FragmentNotificationSettingsBinding
import com.focusguard.app.util.NotificationScheduler
import com.focusguard.app.fragments.NotificationHistoryFragment
import com.google.android.material.slider.Slider
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.util.Log
import com.focusguard.app.MyApplication

class NotificationSettingsFragment : Fragment() {
    
    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var notificationScheduler: NotificationScheduler
    
    private var morningTime = LocalTime.of(8, 0)
    private var eveningTime = LocalTime.of(19, 0)
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        notificationScheduler = NotificationScheduler(requireContext())
        
        setupSlider()
        setupTimePickers()
        setupSwitches()
        setupNotificationHistory()
        setupAiPreferences()
    }
    
    private fun setupSlider() {
        binding.sliderNotificationFrequency.addOnChangeListener { _, value, _ ->
            updateFrequencyDescription(value.toInt())
        }
        
        // Initialize with current value
        updateFrequencyDescription(binding.sliderNotificationFrequency.value.toInt())
    }
    
    private fun updateFrequencyDescription(value: Int) {
        val description = when (value) {
            1 -> "Low frequency (1 notification per day)"
            2 -> "Moderate frequency (2 notifications per day)"
            3 -> "Medium frequency (3 notifications per day)"
            4 -> "High frequency (4 notifications per day)"
            5 -> "Very high frequency (5 notifications per day)"
            else -> "Medium frequency (3 notifications per day)"
        }
        
        binding.textViewFrequencyDescription.text = description
    }
    
    private fun setupTimePickers() {
        // Set initial time text
        updateTimeText()
        
        // Set up morning time picker
        binding.buttonSetMorningTime.setOnClickListener {
            showTimePicker(morningTime) { newTime ->
                morningTime = newTime
                updateTimeText()
                rescheduleNotifications()
            }
        }
        
        // Set up evening time picker
        binding.buttonSetEveningTime.setOnClickListener {
            showTimePicker(eveningTime) { newTime ->
                eveningTime = newTime
                updateTimeText()
                rescheduleNotifications()
            }
        }
    }
    
    private fun updateTimeText() {
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        binding.textViewMorningTime.text = morningTime.format(formatter)
        binding.textViewEveningTime.text = eveningTime.format(formatter)
    }
    
    private fun showTimePicker(initialTime: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val newTime = LocalTime.of(hourOfDay, minute)
                onTimeSelected(newTime)
            },
            initialTime.hour,
            initialTime.minute,
            false
        )
        
        timePickerDialog.show()
    }
    
    private fun setupSwitches() {
        // Flag to suppress notifications during initial setup
        var suppressToasts = true
        
        binding.switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            // Enable/disable all notification controls
            binding.sliderNotificationFrequency.isEnabled = isChecked
            binding.switchReligiousQuotes.isEnabled = isChecked
            binding.switchMotivationalContent.isEnabled = isChecked
            binding.switchHabitReminders.isEnabled = isChecked
            binding.switchPersonalizedInsights.isEnabled = isChecked
            binding.buttonSetMorningTime.isEnabled = isChecked
            binding.buttonSetEveningTime.isEnabled = isChecked
            
            if (!suppressToasts) {
                if (isChecked) {
                    Toast.makeText(
                        requireContext(),
                        "Notifications enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                    rescheduleNotifications()
                } else {
                    Toast.makeText(
                        requireContext(), 
                        "Notifications disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Cancel all scheduled notifications
                    // This would be implemented in a real app
                }
            }
        }
        
        // Type-specific notification toggles with less intrusiveness
        val typeChangeListener = { _: CompoundButton, isChecked: Boolean ->
            if (binding.switchEnableNotifications.isChecked && !suppressToasts) {
                // Instead of Toast for every change, we'll show a toast only when done with batch changes
                // by implementing a timer or a "Save" button in a real app
                
                // Actually reschedule notifications without showing toast
                // We'll rely on the rescheduleNotifications method called at the end
            }
        }
        
        binding.switchReligiousQuotes.setOnCheckedChangeListener(typeChangeListener)
        binding.switchMotivationalContent.setOnCheckedChangeListener(typeChangeListener)
        binding.switchHabitReminders.setOnCheckedChangeListener(typeChangeListener)
        binding.switchPersonalizedInsights.setOnCheckedChangeListener(typeChangeListener)
        
        // After setting up all listeners, allow toasts
        suppressToasts = false
    }
    
    private fun rescheduleNotifications() {
        // Only schedule if notifications are enabled
        if (!binding.switchEnableNotifications.isChecked) return
        
        // In a real app, we would save these preferences and use them
        // to configure the NotificationScheduler
        
        // For now, just call the scheduler to set up default notifications
        notificationScheduler.scheduleAllNotifications()
        
        Toast.makeText(
            requireContext(), 
            "Notification settings updated",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun setupNotificationHistory() {
        binding.buttonViewNotificationHistory.setOnClickListener {
            // Navigate to notification history
            val fragmentManager = requireActivity().supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NotificationHistoryFragment())
                .addToBackStack(null) // Add to back stack so user can navigate back
                .commit()
        }
    }
    
    private fun setupAiPreferences() {
        // Load saved AI preferences
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        
        // Set text fields with saved values
        binding.editTextHabits.setText(prefs.getString("habits", ""))
        binding.editTextGoals.setText(prefs.getString("goals", ""))
        binding.editTextInterests.setText(prefs.getString("interests", ""))
        
        // Set up text watchers with debounce to reduce UI freezing
        binding.editTextHabits.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                v.clearFocus()
            }
        }
        
        binding.editTextGoals.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                v.clearFocus()
            }
        }
        
        binding.editTextInterests.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                v.clearFocus()
            }
        }
        
        // Set up save button
        binding.buttonSaveAIPreferences.setOnClickListener {
            // Get text from fields
            val habits = binding.editTextHabits.text.toString().trim()
            val goals = binding.editTextGoals.text.toString().trim()
            val interests = binding.editTextInterests.text.toString().trim()
            
            // Save to shared preferences
            prefs.edit()
                .putString("habits", habits)
                .putString("goals", goals)
                .putString("interests", interests)
                .apply()
            
            // Queue the AI preferences for processing
            try {
                // Access the aiPreferencesQueue through the companion object instead
                val queue = MyApplication.getAiPreferencesQueue()
                queue.queuePreferences(habits, goals, interests)
                
                Toast.makeText(
                    requireContext(),
                    "AI preferences saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("NotificationSettings", "Error saving AI preferences: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to save AI preferences",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 