package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startTime: String,  // Format: HH:mm
    val endTime: String,    // Format: HH:mm
    val days: List<DayOfWeek>,
    val isActive: Boolean = true
) 