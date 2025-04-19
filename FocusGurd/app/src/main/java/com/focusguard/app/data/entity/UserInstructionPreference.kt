package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "user_instruction_preferences")
data class UserInstructionPreference(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val instruction: String,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastUsedAt: LocalDateTime? = null,
    val priority: Int = 1 // 1 = low, 2 = medium, 3 = high
) 