package com.focusguard.app.data.repository

import androidx.lifecycle.LiveData
import com.focusguard.app.data.dao.DailyRoutineDao
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.RoutineItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class DailyRoutineRepository(private val dailyRoutineDao: DailyRoutineDao) {
    
    val allRoutines: LiveData<List<DailyRoutine>> = dailyRoutineDao.getAllRoutines()
    
    suspend fun getRoutineForDate(date: LocalDate): DailyRoutine? {
        val startDateTime = date.atStartOfDay()
        val endDateTime = date.atTime(LocalTime.MAX)
        return dailyRoutineDao.getRoutineForDate(startDateTime)
    }
    
    suspend fun getRoutinesForDateRange(startDate: LocalDate, endDate: LocalDate): LiveData<List<DailyRoutine>> {
        val startDateTime = startDate.atStartOfDay()
        val endDateTime = endDate.atTime(LocalTime.MAX)
        return dailyRoutineDao.getRoutinesForDateRange(startDateTime, endDateTime)
    }
    
    suspend fun getLatestActiveRoutine(): DailyRoutine? {
        return dailyRoutineDao.getLatestActiveRoutine()
    }
    
    suspend fun getTodayRoutine(): DailyRoutine? {
        val today = LocalDate.now()
        return getRoutineForDate(today)
    }
    
    suspend fun insert(routine: DailyRoutine): Long {
        return dailyRoutineDao.insert(routine)
    }
    
    suspend fun update(routine: DailyRoutine) {
        dailyRoutineDao.update(routine)
    }
    
    suspend fun delete(routine: DailyRoutine) {
        dailyRoutineDao.delete(routine)
    }
    
    suspend fun deactivateOtherRoutines(activeRoutineId: Int) {
        dailyRoutineDao.deactivateOtherRoutines(activeRoutineId)
    }
    
    /**
     * Get routine by ID, with null safety
     */
    suspend fun getRoutineById(routineId: Int): DailyRoutine? {
        return try {
            dailyRoutineDao.getRoutineById(routineId)
        } catch (e: Exception) {
            android.util.Log.e("DailyRoutineRepo", "Error getting routine by ID: ${e.message}", e)
            null
        }
    }
    
    suspend fun updateRoutineItem(routineId: Int, item: RoutineItem) {
        val routine = dailyRoutineDao.getLatestActiveRoutine() ?: return
        if (routine.id != routineId) return
        
        val updatedItems = routine.items.map { 
            if (it.id == item.id) item else it 
        }
        
        val updatedRoutine = routine.copy(
            items = updatedItems,
            lastModifiedAt = LocalDateTime.now()
        )
        
        dailyRoutineDao.update(updatedRoutine)
    }
    
    suspend fun createEmptyRoutineForDate(date: LocalDate): DailyRoutine {
        val startOfDay = date.atStartOfDay()
        val routine = DailyRoutine(
            date = startOfDay,
            items = emptyList(),
            generatedAt = LocalDateTime.now(),
            lastModifiedAt = LocalDateTime.now(),
            isActive = true
        )
        
        val id = dailyRoutineDao.insert(routine)
        dailyRoutineDao.deactivateOtherRoutines(id.toInt())
        
        return routine.copy(id = id.toInt())
    }
    
    suspend fun cleanupOldRoutines(keepDays: Int = 30) {
        val threshold = LocalDateTime.now().minusDays(keepDays.toLong())
        dailyRoutineDao.deleteOldRoutines(threshold)
    }
    
    /**
     * Add a new routine item to an existing routine
     */
    suspend fun addRoutineItem(routineId: Int, newItem: RoutineItem) {
        android.util.Log.d("DailyRoutineRepo", "Fetching routine with ID: $routineId")
        val routine = dailyRoutineDao.getRoutineById(routineId)
        
        if (routine == null) {
            android.util.Log.w("DailyRoutineRepo", "Routine not found with ID: $routineId, creating a new routine")
            
            // Create a new routine for today
            val today = java.time.LocalDate.now()
            val newRoutine = DailyRoutine(
                date = today.atStartOfDay(),
                items = listOf(newItem),
                generatedAt = java.time.LocalDateTime.now(),
                lastModifiedAt = java.time.LocalDateTime.now(),
                isActive = true
            )
            
            android.util.Log.d("DailyRoutineRepo", "Created new routine with item '${newItem.title}'")
            val newId = dailyRoutineDao.insert(newRoutine)
            
            // Deactivate other routines as this is now the active one
            dailyRoutineDao.deactivateOtherRoutines(newId.toInt())
            
            android.util.Log.d("DailyRoutineRepo", "New routine created with ID: $newId")
            return
        }
        
        android.util.Log.d("DailyRoutineRepo", "Found routine with ${routine.items.size} items")
        
        // Create a new list with all existing items plus the new one
        val updatedItems = ArrayList(routine.items)
        updatedItems.add(newItem)
        
        android.util.Log.d("DailyRoutineRepo", "Adding new item '${newItem.title}' to routine")
        
        // Create updated routine with items sorted by start time
        val updatedRoutine = routine.copy(
            items = updatedItems.sortedWith(compareBy { it.startTime }),
            lastModifiedAt = java.time.LocalDateTime.now()
        )
        
        android.util.Log.d("DailyRoutineRepo", "Updating routine with ${updatedRoutine.items.size} items")
        dailyRoutineDao.update(updatedRoutine)
        android.util.Log.d("DailyRoutineRepo", "Routine successfully updated")
    }
} 