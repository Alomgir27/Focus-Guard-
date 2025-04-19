package com.focusguard.app.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.RoutineItem
import com.focusguard.app.data.repository.DailyRoutineRepository
import com.focusguard.app.util.RoutineGenerator
import com.focusguard.app.util.RoutineScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DailyRoutineViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dailyRoutineRepository = MyApplication.dailyRoutineRepository
    private val routineGenerator = MyApplication.routineGenerator
    private val routineScheduler = MyApplication.getRoutineScheduler()
    private val dailyRoutineDao = MyApplication.database.dailyRoutineDao()
    
    // LiveData for the current active routine
    private val _currentRoutine = MutableLiveData<DailyRoutine?>()
    val currentRoutine: LiveData<DailyRoutine?> = _currentRoutine
    
    // LiveData for the current date being viewed
    private val _selectedDate = MutableLiveData<LocalDate>()
    val selectedDate: LiveData<LocalDate> = _selectedDate
    
    // LiveData for the current time for progress tracking
    private val _currentTime = MutableLiveData<LocalDateTime>()
    val currentTime: LiveData<LocalDateTime> = _currentTime
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error state
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Time formatters
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    
    init {
        _selectedDate.value = LocalDate.now()
        _currentTime.value = LocalDateTime.now()
        loadTodayRoutine()
        
        // You might want to set up a handler to update current time periodically
        // for accurate progress tracking
    }
    
    /**
     * Load the routine for today
     */
    fun loadTodayRoutine() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // First try to get existing routine
                val routine = dailyRoutineRepository.getTodayRoutine()
                if (routine != null) {
                    _currentRoutine.postValue(routine)
                    _error.postValue(null)
                } else {
                    // If no routine exists for today, try to generate one
                    try {
                        val newRoutine = routineGenerator.generateRoutine(LocalDate.now())
                        _currentRoutine.postValue(newRoutine)
                        _error.postValue(null)
                    } catch (e: Exception) {
                        Log.e("DailyRoutineViewModel", "Error generating routine: ${e.message}", e)
                        // If generation fails, create a minimal empty routine so the page isn't completely empty
                        createEmptyRoutine(LocalDate.now())
                    }
                }
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error loading today's routine: ${e.message}", e)
                _error.postValue("Error loading today's routine: ${e.message}")
                // Create an empty routine if we can't load or generate one
                createEmptyRoutine(LocalDate.now())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Create a minimal empty routine for the given date
     */
    private fun createEmptyRoutine(date: LocalDate) {
        try {
            val emptyRoutine = DailyRoutine(
                id = 0,
                date = date.atStartOfDay(),
                items = emptyList(),
                generatedAt = LocalDateTime.now(),
                lastModifiedAt = LocalDateTime.now(),
                isActive = true
            )
            _currentRoutine.postValue(emptyRoutine)
        } catch (e: Exception) {
            Log.e("DailyRoutineViewModel", "Error creating empty routine: ${e.message}", e)
        }
    }
    
    /**
     * Change the viewed date
     */
    fun changeDate(date: LocalDate) {
        _selectedDate.value = date
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val routine = dailyRoutineRepository.getRoutineForDate(date)
                if (routine != null) {
                    _currentRoutine.postValue(routine)
                    _error.postValue(null)
                } else {
                    if (date.isAfter(LocalDate.now()) || date.isEqual(LocalDate.now())) {
                        try {
                            // Generate a routine for today or future dates
                            val newRoutine = routineGenerator.generateRoutine(date)
                            _currentRoutine.postValue(newRoutine)
                            _error.postValue(null)
                        } catch (e: Exception) {
                            Log.e("DailyRoutineViewModel", "Error generating routine: ${e.message}", e)
                            // Create an empty routine if generation fails
                            createEmptyRoutine(date)
                        }
                    } else {
                        // For past dates without routines, show empty
                        createEmptyRoutine(date)
                    }
                }
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error loading routine: ${e.message}", e)
                _error.postValue("Error loading routine: ${e.message}")
                // Create an empty routine for this date
                createEmptyRoutine(date)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Toggle the completion status of a routine item
     */
    fun toggleItemCompletion(routineId: Int, item: RoutineItem) {
        val updatedItem = item.copy(isCompleted = !item.isCompleted)
        
        viewModelScope.launch {
            try {
                dailyRoutineRepository.updateRoutineItem(routineId, updatedItem)
                // Refresh the routine
                _currentRoutine.value?.let { routine ->
                    val updatedItems = routine.items.map { 
                        if (it.id == item.id) updatedItem else it 
                    }
                    _currentRoutine.postValue(
                        routine.copy(
                            items = updatedItems,
                            lastModifiedAt = LocalDateTime.now()
                        )
                    )
                }
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue("Error updating item: ${e.message}")
            }
        }
    }
    
    /**
     * Generate a new routine for tomorrow immediately
     */
    fun generateRoutineForTomorrow() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                routineScheduler.generateRoutineImmediately()
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue("Error generating tomorrow's routine: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Get the current routine item based on the time
     */
    fun getCurrentRoutineItem(): RoutineItem? {
        val now = LocalDateTime.now()
        return _currentRoutine.value?.items?.firstOrNull { 
            it.startTime.isBefore(now) && it.endTime.isAfter(now)
        }
    }
    
    /**
     * Calculate progress percentage for a routine item
     */
    fun calculateItemProgress(item: RoutineItem): Float {
        val now = LocalDateTime.now()
        
        // If item is in the future or past, return appropriate values
        if (now.isBefore(item.startTime)) return 0f
        if (now.isAfter(item.endTime)) return 1f
        
        // Calculate progress within the time range
        val totalDuration = java.time.Duration.between(item.startTime, item.endTime).toMillis()
        val elapsedDuration = java.time.Duration.between(item.startTime, now).toMillis()
        
        return (elapsedDuration.toFloat() / totalDuration)
    }
    
    /**
     * Update a routine item
     */
    fun updateRoutineItem(routineId: Int, updatedItem: RoutineItem) {
        viewModelScope.launch {
            try {
                dailyRoutineRepository.updateRoutineItem(routineId, updatedItem)
                // Refresh the routine
                loadTodayRoutine()
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue("Error updating item: ${e.message}")
            }
        }
    }
    
    /**
     * Add a new item to the routine
     */
    fun addRoutineItem(routineId: Int, newItem: RoutineItem) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            
            try {
                Log.d("DailyRoutineViewModel", "Starting to add routine item with id: ${newItem.id}")
                
                // Check if the current routine exists and is valid
                val currentRoutine = if (routineId != 0) {
                    try {
                        val routine = dailyRoutineRepository.getRoutineById(routineId)
                        if (routine == null) {
                            Log.w("DailyRoutineViewModel", "Routine with ID $routineId not found, creating new one")
                            null
                        } else {
                            routine
                        }
                    } catch (e: Exception) {
                        Log.w("DailyRoutineViewModel", "Error fetching routine: ${e.message}", e)
                        null
                    }
                } else {
                    null
                }
                
                if (currentRoutine == null) {
                    // No valid routine exists, create one
                    val today = LocalDate.now()
                    Log.d("DailyRoutineViewModel", "Creating new routine for today: $today")
                    
                    val newRoutine = DailyRoutine(
                        date = today.atStartOfDay(),
                        items = listOf(newItem),
                        generatedAt = LocalDateTime.now(),
                        lastModifiedAt = LocalDateTime.now(),
                        isActive = true
                    )
                    
                    val newId = dailyRoutineRepository.insert(newRoutine)
                    Log.d("DailyRoutineViewModel", "Created new routine with ID: $newId")
                } else {
                    // Add to existing routine
                    Log.d("DailyRoutineViewModel", "Adding item to existing routine with ID: ${currentRoutine.id}")
                    try {
                        dailyRoutineRepository.addRoutineItem(currentRoutine.id, newItem)
                        Log.d("DailyRoutineViewModel", "Successfully added item to routine")
                    } catch (e: Exception) {
                        Log.e("DailyRoutineViewModel", "Error adding item to routine: ${e.message}", e)
                        throw e
                    }
                }
                
                // Refresh the routine
                Log.d("DailyRoutineViewModel", "Reloading today's routine after adding item")
                loadTodayRoutine()
                _error.postValue(null)
                
                Log.d("DailyRoutineViewModel", "Current routine now has ${_currentRoutine.value?.items?.size ?: 0} items")
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error adding item: ${e.message}", e)
                _error.postValue("Error adding item: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Delete a routine item by ID
     */
    fun deleteRoutineItem(itemId: String) {
        val currentRoutine = _currentRoutine.value ?: return
        
        // Store the item that's being deleted for potential restoration
        val itemToDelete = currentRoutine.items.find { it.id == itemId }
        
        // Remove the item from the list
        val updatedItems = currentRoutine.items.filter { it.id != itemId }
        
        val updatedRoutine = currentRoutine.copy(
            items = updatedItems,
            lastModifiedAt = LocalDateTime.now()
        )
        
        viewModelScope.launch {
            try {
                dailyRoutineRepository.update(updatedRoutine)
                _currentRoutine.postValue(updatedRoutine)
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue("Error deleting item: ${e.message}")
            }
        }
    }
    
    /**
     * Restore a deleted routine item
     */
    fun restoreDeletedItem(routineId: Int, item: RoutineItem) {
        val currentRoutine = _currentRoutine.value ?: return
        
        // Add the item back to the list
        val updatedItems = currentRoutine.items.toMutableList()
        updatedItems.add(item)
        
        // Sort items by start time
        val sortedItems = updatedItems.sortedWith(compareBy { it.startTime })
        
        // Update the routine
        val updatedRoutine = currentRoutine.copy(
            items = sortedItems,
            lastModifiedAt = LocalDateTime.now()
        )
        
        viewModelScope.launch {
            try {
                dailyRoutineRepository.update(updatedRoutine)
                _currentRoutine.postValue(updatedRoutine)
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue("Error restoring item: ${e.message}")
            }
        }
    }
    
    /**
     * Regenerate a routine with custom instructions
     */
    fun regenerateRoutineWithInstructions(date: LocalDate, instructions: List<String>) {
        _isLoading.value = true
        _error.value = null // Clear any previous errors
        
        viewModelScope.launch {
            try {
                Log.d("DailyRoutineViewModel", "Starting routine regeneration for date: $date")
                
                // Check if we have a routine for this date
                val existingRoutine = dailyRoutineRepository.getRoutineForDate(date)
                Log.d("DailyRoutineViewModel", "Existing routine found: ${existingRoutine != null}")
                
                // Use the routine generator to create a new routine with instructions
                val regeneratedRoutine = try {
                    routineGenerator.regenerateRoutine(
                        date,
                        instructions,
                        preserveCompletedItems = true
                    )
                } catch (e: Exception) {
                    Log.e("DailyRoutineViewModel", "Error in regenerateRoutine: ${e.message}", e)
                    throw Exception("Failed to generate routine: ${e.message}")
                }
                
                Log.d("DailyRoutineViewModel", "Regeneration successful, new routine has ${regeneratedRoutine.items.size} items")
                
                // Update the current routine with the new one
                _currentRoutine.postValue(regeneratedRoutine)
                
                // Also load the routine fresh from the database to ensure it's properly stored
                loadTodayRoutine()
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error regenerating routine: ${e.message}", e)
                _error.postValue("Error regenerating routine: ${e.message}")
                // Re-throw the exception so the UI layer can handle it
                throw e
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Preview a regenerated routine with custom instructions without applying it
     */
    suspend fun previewRegeneratedRoutine(date: LocalDate, instructions: List<String>): DailyRoutine {
        try {
            Log.d("DailyRoutineViewModel", "Previewing routine regeneration for date: $date")
            
            // Use the routine generator to create a new routine with instructions
            // but don't save it to the database
            val previewRoutine = try {
                routineGenerator.previewRegeneratedRoutine(
                    date,
                    instructions,
                    preserveCompletedItems = true
                )
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error in previewRegeneratedRoutine: ${e.message}", e)
                throw Exception("Failed to generate routine preview: ${e.message}")
            }
            
            Log.d("DailyRoutineViewModel", "Preview generation successful, preview routine has ${previewRoutine.items.size} items")
            
            return previewRoutine
        } catch (e: Exception) {
            Log.e("DailyRoutineViewModel", "Error previewing routine: ${e.message}", e)
            throw e
        }
    }

    /**
     * Apply a previewed routine
     */
    fun applyPreviewedRoutine(previewedRoutine: DailyRoutine) {
        _isLoading.value = true
        _error.value = null // Clear any previous errors
        
        viewModelScope.launch {
            try {
                Log.d("DailyRoutineViewModel", "Applying previewed routine for date: ${previewedRoutine.date.toLocalDate()}")
                
                // Check if we have a routine for this date
                val existingRoutine = dailyRoutineRepository.getRoutineForDate(previewedRoutine.date.toLocalDate())
                Log.d("DailyRoutineViewModel", "Existing routine found: ${existingRoutine != null}")
                
                // Delete existing routine if it exists
                if (existingRoutine != null) {
                    try {
                        dailyRoutineRepository.delete(existingRoutine)
                        Log.d("DailyRoutineViewModel", "Successfully deleted existing routine")
                    } catch (e: Exception) {
                        Log.e("DailyRoutineViewModel", "Error deleting existing routine: ${e.message}", e)
                        // Continue anyway - we'll overwrite it
                    }
                }
                
                // Set isActive to true if it's today's routine
                val isToday = previewedRoutine.date.toLocalDate() == LocalDate.now()
                val modifiedPreviewRoutine = previewedRoutine.copy(
                    isActive = isToday,
                    lastModifiedAt = LocalDateTime.now()
                )
                
                // Save the previewed routine to the database
                val savedRoutineId = dailyRoutineRepository.insert(modifiedPreviewRoutine)
                Log.d("DailyRoutineViewModel", "Saved previewed routine with ID: $savedRoutineId")
                
                // Activate this routine if it's for today
                if (isToday) {
                    Log.d("DailyRoutineViewModel", "Deactivating other routines as this is today's routine")
                    dailyRoutineRepository.deactivateOtherRoutines(savedRoutineId.toInt())
                    
                    // Force refresh of the current routine
                    _currentRoutine.postValue(modifiedPreviewRoutine.copy(id = savedRoutineId.toInt()))
                }
                
                // Also load the routine fresh from the database to ensure it's properly stored
                if (isToday) {
                    loadTodayRoutine()
                }
                
                Log.d("DailyRoutineViewModel", "Routine application completed successfully")
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error applying previewed routine: ${e.message}", e)
                _error.postValue("Error applying routine: ${e.message}")
                throw e
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Force refresh today's routine from the database
     */
    fun refreshTodayRoutine() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Explicitly get from database to ensure we get the latest
                val freshRoutine = dailyRoutineRepository.getTodayRoutine()
                _currentRoutine.postValue(freshRoutine)
                
                // Also check if there's an active routine (should be the same as today's)
                val activeRoutine = dailyRoutineRepository.getLatestActiveRoutine()
                if (freshRoutine == null && activeRoutine != null) {
                    // If today's routine is null but we have an active one, use that
                    _currentRoutine.postValue(activeRoutine)
                }
                
                Log.d("DailyRoutineViewModel", "Today's routine refreshed: ${freshRoutine?.id}")
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error refreshing today's routine: ${e.message}", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Refresh routine for the given date by regenerating it using AI preferences
     */
    fun refreshRoutine(date: LocalDate) {
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                Log.d("DailyRoutineViewModel", "Refreshing routine for date: $date")
                
                // Generate a new routine
                val refreshedRoutine = routineGenerator.generateRoutine(date)
                
                // Update the current routine
                _currentRoutine.postValue(refreshedRoutine)
                
                Log.d("DailyRoutineViewModel", "Routine refreshed successfully for $date")
            } catch (e: Exception) {
                Log.e("DailyRoutineViewModel", "Error refreshing routine: ${e.message}", e)
                _error.postValue("Error refreshing routine: ${e.message}")
                
                // If we failed to generate a new one, try to get the existing one
                try {
                    val existingRoutine = dailyRoutineRepository.getRoutineForDate(date)
                    if (existingRoutine != null) {
                        _currentRoutine.postValue(existingRoutine)
                    }
                } catch (innerEx: Exception) {
                    Log.e("DailyRoutineViewModel", "Error fetching existing routine: ${innerEx.message}", innerEx)
                }
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
} 