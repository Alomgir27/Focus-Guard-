package com.focusguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a blocked application
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isActive: Boolean = true,
    // Schedule fields
    val startTime: String? = null,
    val endTime: String? = null,
    val blockAllDay: Boolean = false,
    val enabledDays: Int = 127, // Bit field for days: 1111111 (all days by default)
    val password: String? = null // Password for unlocking the app
) 