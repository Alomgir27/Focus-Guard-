package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "user_habits")
data class UserHabit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitName: String,
    val description: String,
    val isDistracting: Boolean,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) 