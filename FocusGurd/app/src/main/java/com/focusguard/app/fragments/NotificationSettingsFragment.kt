package com.focusguard.app.fragments

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.focusguard.app.R
import com.focusguard.app.databinding.FragmentNotificationSettingsBinding
import com.focusguard.app.util.NotificationScheduler
import com.focusguard.app.fragments.NotificationHistoryFragment
import com.focusguard.app.util.ApiKeyManager
import com.focusguard.app.util.NotificationCacheManager
import com.focusguard.app.util.NotificationGenerator
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
    private lateinit var cacheManager: NotificationCacheManager
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var notificationGenerator: NotificationGenerator
    
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
        cacheManager = NotificationCacheManager(requireContext())
        apiKeyManager = ApiKeyManager(requireContext())
        notificationGenerator = NotificationGenerator(requireContext())
        
        setupSlider()
        setupTimePickers()
        setupNotificationSwitch()
        setupAiPreferences()
        setupNotificationHistoryButton()
    }
    
    private fun setupSlider() {
        // Load saved notification frequency
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val currentFrequency = prefs.getInt("notification_frequency", 2)
        
        binding.sliderNotificationFrequency.value = currentFrequency.toFloat()
        updateFrequencyText(currentFrequency)
        
        binding.sliderNotificationFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val frequency = value.toInt()
                updateFrequencyText(frequency)
                
                // Save the new frequency
                prefs.edit().putInt("notification_frequency", frequency).apply()
                
                // Apply the new frequency to the scheduler
                notificationScheduler.setNotificationFrequency(frequency)
            }
        }
    }
    
    private fun updateFrequencyText(frequency: Int) {
        val text = when (frequency) {
            1 -> "Low (1-2 per day)"
            2 -> "Medium (3-4 per day)"
            3 -> "High (5-6 per day)"
            4 -> "Very High (7-8 per day)"
            else -> "Medium (3-4 per day)"
        }
        binding.textViewFrequencyValue.text = text
    }
    
    private fun setupTimePickers() {
        // Get the saved times
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val savedMorningHour = prefs.getInt("morning_hour", 8)
        val savedMorningMinute = prefs.getInt("morning_minute", 0)
        val savedEveningHour = prefs.getInt("evening_hour", 19)
        val savedEveningMinute = prefs.getInt("evening_minute", 0)
        
        morningTime = LocalTime.of(savedMorningHour, savedMorningMinute)
        eveningTime = LocalTime.of(savedEveningHour, savedEveningMinute)
        
        updateTimeText(binding.textViewMorningTime, morningTime)
        updateTimeText(binding.textViewEveningTime, eveningTime)
        
        binding.buttonSetMorningTime.setOnClickListener {
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                morningTime = LocalTime.of(hour, minute)
                updateTimeText(binding.textViewMorningTime, morningTime)
                
                // Save the time
                prefs.edit()
                    .putInt("morning_hour", hour)
                    .putInt("morning_minute", minute)
                    .apply()
                
                // Update the scheduler
                notificationScheduler.setMorningTime(morningTime)
            }
            
            TimePickerDialog(
                requireContext(),
                timeSetListener,
                morningTime.hour,
                morningTime.minute,
                true
            ).show()
        }
        
        binding.buttonSetEveningTime.setOnClickListener {
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                eveningTime = LocalTime.of(hour, minute)
                updateTimeText(binding.textViewEveningTime, eveningTime)
                
                // Save the time
                prefs.edit()
                    .putInt("evening_hour", hour)
                    .putInt("evening_minute", minute)
                    .apply()
                
                // Update the scheduler
                notificationScheduler.setEveningTime(eveningTime)
            }
            
            TimePickerDialog(
                requireContext(),
                timeSetListener,
                eveningTime.hour,
                eveningTime.minute,
                true
            ).show()
        }
    }
    
    private fun updateTimeText(textView: android.widget.TextView, time: LocalTime) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        textView.text = time.format(formatter)
    }
    
    private fun setupNotificationSwitch() {
        // Get saved preferences
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        
        // Setup main notification toggle
        binding.switchEnableNotifications.isChecked = prefs.getBoolean("enable_notifications", true)
        binding.switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_notifications", isChecked).apply()
            
            // Enable or disable all notification features
            binding.sliderNotificationFrequency.isEnabled = isChecked
            binding.buttonSetMorningTime.isEnabled = isChecked
            binding.buttonSetEveningTime.isEnabled = isChecked
            
            if (!isChecked) {
                // Cancel all scheduled notifications
                notificationScheduler.cancelAllNotifications()
            } else {
                // Reschedule notifications
                notificationScheduler.scheduleAllNotifications()
            }
        }
    }
    
    private fun setupAiPreferences() {
        // Load saved AI preferences
        val prefs = requireContext().getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        
        // Rename the title to Routine Preferences
        binding.textViewAIPreferencesTitle.text = "Routine Preferences"
        binding.textViewAIPreferencesSubtitle.text = "Customize your routines based on your personal preferences"
        
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
                    "Preferences saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("NotificationSettings", "Error saving preferences: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to save preferences",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun setupNotificationHistoryButton() {
        binding.buttonViewNotificationHistory.setOnClickListener {
            // Navigate to notification history fragment
            val fragmentManager = requireActivity().supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, NotificationHistoryFragment())
                .addToBackStack(null)
                .commit()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 