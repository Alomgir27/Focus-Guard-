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
import androidx.lifecycle.lifecycleScope
import com.focusguard.app.R
import com.focusguard.app.databinding.FragmentNotificationSettingsBinding
import com.focusguard.app.util.NotificationScheduler
import com.focusguard.app.fragments.NotificationHistoryFragment
import com.focusguard.app.util.ApiKeyManager
import com.focusguard.app.util.NotificationCacheManager
import com.focusguard.app.util.NotificationGenerator
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
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
        setupSwitches()
        setupNotificationHistory()
        setupAiPreferences()
        setupAiSettings()
    }
    
    private fun setupSlider() {
        // Load saved notification frequency
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val currentFrequency = prefs.getInt("notification_frequency", 3)
        
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
    
    private fun setupSwitches() {
        // Get saved preferences
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        
        // Setup motivation notifications switch
        binding.switchMotivational.isChecked = prefs.getBoolean("enable_motivational", true)
        binding.switchMotivational.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_motivational", isChecked).apply()
            notificationScheduler.setNotificationTypeEnabled(com.focusguard.app.data.entity.NotificationType.MOTIVATION, isChecked)
        }
        
        // Setup habit reminder notifications switch
        binding.switchHabitReminders.isChecked = prefs.getBoolean("enable_habit_reminders", true)
        binding.switchHabitReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_habit_reminders", isChecked).apply()
            notificationScheduler.setNotificationTypeEnabled(com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER, isChecked)
        }
        
        // Setup religious quotes notifications switch
        binding.switchReligiousQuotes.isChecked = prefs.getBoolean("enable_religious_quotes", true)
        binding.switchReligiousQuotes.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_religious_quotes", isChecked).apply()
            notificationScheduler.setNotificationTypeEnabled(com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE, isChecked)
        }
        
        // Setup insight notifications switch
        binding.switchPersonalizedInsights.isChecked = prefs.getBoolean("enable_insights", true)
        binding.switchPersonalizedInsights.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_insights", isChecked).apply()
            notificationScheduler.setNotificationTypeEnabled(com.focusguard.app.data.entity.NotificationType.INSIGHT, isChecked)
        }
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
    
    private fun setupAiSettings() {
        // Get shared preferences
        val prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        
        // Initialize switches with saved values
        binding.switchUseAI.isChecked = prefs.getBoolean("use_ai", true)
        binding.switchUseCache.isChecked = prefs.getBoolean("use_cache", true)
        binding.switchPreGenerateOffline.isChecked = prefs.getBoolean("pre_generate_offline", true)
        
        // Set up listeners
        binding.switchUseAI.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_ai", isChecked).apply()
            
            // Update dependent controls
            binding.switchUseCache.isEnabled = isChecked
            binding.switchPreGenerateOffline.isEnabled = isChecked
        }
        
        binding.switchUseCache.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_cache", isChecked).apply()
        }
        
        binding.switchPreGenerateOffline.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pre_generate_offline", isChecked).apply()
            
            // If turned on, start pre-generating content
            if (isChecked) {
                lifecycleScope.launch {
                    try {
                        notificationGenerator.preGenerateOfflineContent()
                        Toast.makeText(
                            requireContext(),
                            "Pre-generating content for offline use",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e("NotificationSettings", "Error pre-generating content: ${e.message}")
                    }
                }
            }
        }
        
        // Set up clear cache button
        binding.buttonClearCache.setOnClickListener {
            cacheManager.clearAllCache()
            Toast.makeText(
                requireContext(),
                "AI content cache cleared",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Update API usage statistics
        updateApiUsageStats()
    }
    
    private fun updateApiUsageStats() {
        // Get API usage stats from preferences
        val prefs = requireContext().getSharedPreferences("ai_usage", Context.MODE_PRIVATE)
        val todayUsage = prefs.getInt(
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
            0
        )
        
        // Update the text view
        binding.textViewApiUsage.text = "API calls today: $todayUsage/5"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 