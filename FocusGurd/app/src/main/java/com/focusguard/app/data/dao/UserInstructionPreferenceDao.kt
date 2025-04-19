package com.focusguard.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.UserInstructionPreference
import java.time.LocalDateTime

@Dao
interface UserInstructionPreferenceDao {
    @Query("SELECT * FROM user_instruction_preferences WHERE isActive = 1 ORDER BY priority DESC, createdAt DESC")
    fun getAllActiveInstructions(): LiveData<List<UserInstructionPreference>>
    
    @Query("SELECT * FROM user_instruction_preferences WHERE isActive = 1 ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllActiveInstructionsSync(): List<UserInstructionPreference>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instruction: UserInstructionPreference): Long
    
    @Update
    suspend fun update(instruction: UserInstructionPreference)
    
    @Delete
    suspend fun delete(instruction: UserInstructionPreference)
    
    @Query("UPDATE user_instruction_preferences SET isActive = 0 WHERE id = :instructionId")
    suspend fun deactivateInstruction(instructionId: Long)
    
    @Query("UPDATE user_instruction_preferences SET lastUsedAt = :usedAt WHERE id = :instructionId")
    suspend fun updateLastUsed(instructionId: Long, usedAt: LocalDateTime)
    
    @Query("DELETE FROM user_instruction_preferences WHERE isActive = 0 AND lastUsedAt < :olderThan")
    suspend fun deleteOldInstructions(olderThan: LocalDateTime)
} 