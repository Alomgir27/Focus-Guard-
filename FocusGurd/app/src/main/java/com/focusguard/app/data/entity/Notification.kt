package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val type: NotificationType,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val scheduledFor: LocalDateTime? = null,
    val wasShown: Boolean = false,
    val wasClicked: Boolean = false
)

enum class NotificationType {
    HABIT_REMINDER, 
    MOTIVATION, 
    INSIGHT,
    RELIGIOUS_QUOTE,
    APP_USAGE_WARNING,
    GENERAL
} 