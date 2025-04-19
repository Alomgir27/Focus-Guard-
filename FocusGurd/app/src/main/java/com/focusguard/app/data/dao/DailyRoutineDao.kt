package com.focusguard.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.DailyRoutine
import java.time.LocalDateTime

@Dao
interface DailyRoutineDao {
    @Query("SELECT * FROM daily_routines ORDER BY date DESC")
    fun getAllRoutines(): LiveData<List<DailyRoutine>>
    
    @Query("SELECT * FROM daily_routines WHERE date = :date LIMIT 1")
    suspend fun getRoutineForDate(date: LocalDateTime): DailyRoutine?
    
    @Query("SELECT * FROM daily_routines WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getRoutinesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): LiveData<List<DailyRoutine>>
    
    @Query("SELECT * FROM daily_routines WHERE isActive = 1 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestActiveRoutine(): DailyRoutine?
    
    @Query("SELECT * FROM daily_routines WHERE id = :id LIMIT 1")
    suspend fun getRoutineById(id: Int): DailyRoutine?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: DailyRoutine): Long
    
    @Update
    suspend fun update(routine: DailyRoutine)
    
    @Delete
    suspend fun delete(routine: DailyRoutine)
    
    @Query("UPDATE daily_routines SET isActive = 0 WHERE id != :activeRoutineId")
    suspend fun deactivateOtherRoutines(activeRoutineId: Int)
    
    @Query("DELETE FROM daily_routines WHERE date < :olderThan")
    suspend fun deleteOldRoutines(olderThan: LocalDateTime)
} 