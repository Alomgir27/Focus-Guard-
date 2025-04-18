package com.focusguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.entity.BlockedApp

@Dao
interface AppBlockingDao {
    
    // Blocked Apps
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(blockedApp: BlockedApp)

    @Query("UPDATE blocked_apps_schedule SET isActive = :isActive WHERE packageName = :packageName")
    suspend fun updateBlockedAppStatus(packageName: String, isActive: Boolean)
    
    @Query("UPDATE blocked_apps_schedule SET startTime = :startTime, endTime = :endTime, blockAllDay = :blockAllDay WHERE packageName = :packageName")
    suspend fun updateBlockedAppSchedule(packageName: String, startTime: String?, endTime: String?, blockAllDay: Boolean)
    
    @Query("UPDATE blocked_apps_schedule SET enabledDays = :enabledDays WHERE packageName = :packageName")
    suspend fun updateBlockedAppDays(packageName: String, enabledDays: Int)

    @Query("SELECT * FROM blocked_apps_schedule")
    suspend fun getAllBlockedApps(): List<BlockedApp>

    @Query("SELECT * FROM blocked_apps_schedule WHERE isActive = 1")
    suspend fun getActiveBlockedApps(): List<BlockedApp>

    @Query("SELECT * FROM blocked_apps_schedule WHERE packageName = :packageName")
    suspend fun getBlockedApp(packageName: String): BlockedApp?

    @Query("DELETE FROM blocked_apps_schedule WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)
} 