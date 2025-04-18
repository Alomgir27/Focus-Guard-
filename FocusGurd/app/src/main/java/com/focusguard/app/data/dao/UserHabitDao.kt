package com.focusguard.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.UserHabit
import kotlinx.coroutines.flow.Flow

@Dao
interface UserHabitDao {
    @Query("SELECT * FROM user_habits ORDER BY createdAt DESC")
    fun getAllHabits(): Flow<List<UserHabit>>
    
    @Query("SELECT * FROM user_habits WHERE isDistracting = 1 ORDER BY createdAt DESC")
    fun getDistractingHabits(): Flow<List<UserHabit>>
    
    @Query("SELECT * FROM user_habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: Long): UserHabit?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: UserHabit): Long
    
    @Update
    suspend fun update(habit: UserHabit)
    
    @Delete
    suspend fun delete(habit: UserHabit)
    
    @Query("DELETE FROM user_habits WHERE id = :habitId")
    suspend fun deleteById(habitId: Long)
} 