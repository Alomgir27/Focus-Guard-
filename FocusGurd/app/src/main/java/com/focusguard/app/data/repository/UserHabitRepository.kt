package com.focusguard.app.data.repository

import com.focusguard.app.data.dao.UserHabitDao
import com.focusguard.app.data.entity.UserHabit
import kotlinx.coroutines.flow.Flow

class UserHabitRepository(private val userHabitDao: UserHabitDao) {
    
    fun getAllHabits(): Flow<List<UserHabit>> = userHabitDao.getAllHabits()
    
    fun getDistractingHabits(): Flow<List<UserHabit>> = userHabitDao.getDistractingHabits()
    
    suspend fun getHabitById(habitId: Long): UserHabit? = userHabitDao.getHabitById(habitId)
    
    suspend fun addHabit(habit: UserHabit): Long = userHabitDao.insert(habit)
    
    suspend fun updateHabit(habit: UserHabit) = userHabitDao.update(habit)
    
    suspend fun deleteHabit(habit: UserHabit) = userHabitDao.delete(habit)
    
    suspend fun deleteHabitById(habitId: Long) = userHabitDao.deleteById(habitId)
} 