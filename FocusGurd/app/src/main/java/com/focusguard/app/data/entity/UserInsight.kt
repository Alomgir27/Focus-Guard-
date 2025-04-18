package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "user_insights")
data class UserInsight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val source: InsightSource,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isArchived: Boolean = false,
    val wasShown: Boolean = false
)

enum class InsightSource {
    APP_USAGE_ANALYSIS,
    HABIT_TRACKING,
    AI_SUGGESTION,
    SYSTEM_GENERATED
} 