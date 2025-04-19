package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.focusguard.app.data.converter.RoutineItemListConverter
import java.time.LocalDateTime

@Entity(tableName = "daily_routines")
@TypeConverters(RoutineItemListConverter::class)
data class DailyRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: LocalDateTime,
    val items: List<RoutineItem>,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val lastModifiedAt: LocalDateTime = LocalDateTime.now(),
    val isActive: Boolean = true
)

data class RoutineItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val isCompleted: Boolean = false,
    val isFocusTime: Boolean = false,
    val priority: Int = 1 // 1 = low, 2 = medium, 3 = high
) 