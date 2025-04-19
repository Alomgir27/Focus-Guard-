package com.focusguard.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.Task
import java.time.LocalDateTime

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    fun getAllTasks(): LiveData<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY dueDate ASC")
    fun getActiveTasks(): LiveData<List<Task>>
    
    @Query("SELECT * FROM tasks WHERE completed = 1 ORDER BY dueDate DESC")
    fun getCompletedTasks(): LiveData<List<Task>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)
    
    @Update
    suspend fun update(task: Task)
    
    @Delete
    suspend fun delete(task: Task)
    
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?
    
    @Query("UPDATE tasks SET completed = :isCompleted WHERE id = :taskId")
    suspend fun updateTaskCompletionStatus(taskId: Int, isCompleted: Boolean)
    
    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY dueDate ASC")
    suspend fun getActiveTasksSync(): List<Task>
} 