package com.focusguard.app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.data.repository.UserHabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserHabitsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: UserHabitRepository = MyApplication.userHabitRepository
    
    // Observe all habits
    val allHabits = repository.getAllHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Observe distracting habits
    val distractingHabits = repository.getDistractingHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Add a new habit
    fun addHabit(habit: UserHabit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addHabit(habit)
        }
    }
    
    // Update an existing habit
    fun updateHabit(habit: UserHabit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateHabit(habit)
        }
    }
    
    // Delete a habit
    fun deleteHabit(habit: UserHabit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHabit(habit)
        }
    }
} 