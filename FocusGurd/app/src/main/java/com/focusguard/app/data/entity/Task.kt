package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String?,
    val dueDate: LocalDateTime,
    val completed: Boolean = false,
    val priority: Int = 0,  // 0 = low, 1 = medium, 2 = high
    val estimatedMinutes: Int? = 30 // Default to 30 minutes if not specified
) 