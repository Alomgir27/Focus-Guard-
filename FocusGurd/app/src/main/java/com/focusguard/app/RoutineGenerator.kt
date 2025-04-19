package com.focusguard.app

import com.focusguard.app.data.entity.RoutineItem
import com.focusguard.app.data.entity.Task
import java.time.LocalDate
import java.time.LocalTime

/**
 * This class is a wrapper around the main RoutineGenerator implementation
 * in the util package. This is to maintain backward compatibility with
 * existing code that may be importing from this location.
 */
class RoutineGenerator {
    private val actualGenerator = MyApplication.routineGenerator

    fun processTask(
        task: Task,
        routineItems: MutableList<RoutineItem>,
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime
    ) {
        // Delegate to the actual implementation in util package
        actualGenerator.processTask(task, routineItems, date, wakeupTime, sleepTime)
    }

    suspend fun getPendingTasksForDate(date: LocalDate): List<Task> {
        // Delegate to the actual implementation in util package
        return actualGenerator.getPendingTasksForDate(date)
    }
} 