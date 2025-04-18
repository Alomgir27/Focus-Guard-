package com.focusguard.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusguard.app.data.dao.AppBlockDao
import com.focusguard.app.data.dao.AppBlockingDao
import com.focusguard.app.data.dao.NotificationDao
import com.focusguard.app.data.dao.TaskDao
import com.focusguard.app.data.dao.UserHabitDao
import com.focusguard.app.data.dao.UserInsightDao
import com.focusguard.app.data.entity.BlockedApp
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.Task
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.data.entity.UserInsight
import com.focusguard.app.data.util.Converters

@Database(
    entities = [
        BlockedAppEntity::class,
        Task::class,
        UserHabit::class,
        Notification::class,
        UserInsight::class,
        BlockedApp::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appBlockDao(): AppBlockDao
    abstract fun appBlockingDao(): AppBlockingDao
    abstract fun taskDao(): TaskDao
    abstract fun userHabitDao(): UserHabitDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userInsightDao(): UserInsightDao
    
    companion object {
        // Migration from version 7 to version 8 - Add password field to blocked_apps_schedule table
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the password column to the blocked_apps_schedule table
                database.execSQL(
                    "ALTER TABLE blocked_apps_schedule ADD COLUMN password TEXT"
                )
            }
        }
        
        // Migration from version 8 to version 9 - Add schedule fields to blocked_apps table
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add schedule columns to the blocked_apps table
                database.execSQL("ALTER TABLE blocked_apps ADD COLUMN startTime TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE blocked_apps ADD COLUMN endTime TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE blocked_apps ADD COLUMN blockAllDay INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE blocked_apps ADD COLUMN enabledDays INTEGER NOT NULL DEFAULT 127")
                database.execSQL("ALTER TABLE blocked_apps ADD COLUMN password TEXT DEFAULT NULL")
            }
        }
    }
} 