package com.focusguard.app.data.repository

import androidx.lifecycle.LiveData
import com.focusguard.app.data.dao.UserInstructionPreferenceDao
import com.focusguard.app.data.entity.UserInstructionPreference
import java.time.LocalDateTime

class UserInstructionRepository(private val userInstructionPreferenceDao: UserInstructionPreferenceDao) {
    
    fun getAllActiveInstructions(): LiveData<List<UserInstructionPreference>> {
        return userInstructionPreferenceDao.getAllActiveInstructions()
    }
    
    suspend fun getAllActiveInstructionsSync(): List<UserInstructionPreference> {
        return userInstructionPreferenceDao.getAllActiveInstructionsSync()
    }
    
    suspend fun addInstruction(instruction: String, priority: Int = 1): Long {
        val newInstruction = UserInstructionPreference(
            instruction = instruction,
            priority = priority,
            createdAt = LocalDateTime.now()
        )
        return userInstructionPreferenceDao.insert(newInstruction)
    }
    
    suspend fun updateInstruction(instruction: UserInstructionPreference) {
        userInstructionPreferenceDao.update(instruction)
    }
    
    suspend fun deleteInstruction(instruction: UserInstructionPreference) {
        userInstructionPreferenceDao.delete(instruction)
    }
    
    suspend fun deactivateInstruction(instructionId: Long) {
        userInstructionPreferenceDao.deactivateInstruction(instructionId)
    }
    
    suspend fun markInstructionAsUsed(instructionId: Long) {
        userInstructionPreferenceDao.updateLastUsed(instructionId, LocalDateTime.now())
    }
    
    suspend fun cleanupOldInstructions(keepDays: Int = 30) {
        val threshold = LocalDateTime.now().minusDays(keepDays.toLong())
        userInstructionPreferenceDao.deleteOldInstructions(threshold)
    }
} 