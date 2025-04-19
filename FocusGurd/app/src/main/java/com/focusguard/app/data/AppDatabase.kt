package com.focusguard.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusguard.app.data.converter.RoutineItemListConverter
import com.focusguard.app.data.dao.AppBlockDao
import com.focusguard.app.data.dao.AppBlockingDao
import com.focusguard.app.data.dao.DailyRoutineDao
import com.focusguard.app.data.dao.NotificationDao
import com.focusguard.app.data.dao.TaskDao
import com.focusguard.app.data.dao.UserHabitDao
import com.focusguard.app.data.dao.UserInsightDao
import com.focusguard.app.data.dao.UserInstructionPreferenceDao
import com.focusguard.app.data.entity.BlockedApp
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.Task
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.data.entity.UserInsight
import com.focusguard.app.data.entity.UserInstructionPreference
import com.focusguard.app.data.util.Converters

@Database(
    entities = [
        BlockedAppEntity::class,
        Task::class,
        UserHabit::class,
        Notification::class,
        UserInsight::class,
        BlockedApp::class,
        DailyRoutine::class,
        UserInstructionPreference::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class, RoutineItemListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appBlockDao(): AppBlockDao
    abstract fun appBlockingDao(): AppBlockingDao
    abstract fun taskDao(): TaskDao
    abstract fun userHabitDao(): UserHabitDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userInsightDao(): UserInsightDao
    abstract fun dailyRoutineDao(): DailyRoutineDao
    abstract fun userInstructionPreferenceDao(): UserInstructionPreferenceDao
    
    companion object {
        // No migrations needed when starting with version 1
    }
} 