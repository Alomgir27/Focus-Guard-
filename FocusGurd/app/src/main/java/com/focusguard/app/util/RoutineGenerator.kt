package com.focusguard.app.util

import android.content.Context
import android.util.Log
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.RoutineItem
import com.focusguard.app.data.entity.Task
import com.focusguard.app.data.repository.DailyRoutineRepository
import com.focusguard.app.data.repository.TaskRepository
import com.focusguard.app.data.repository.UserInstructionRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import java.time.temporal.ChronoUnit

class RoutineGenerator(
    private val context: Context,
    private val dailyRoutineRepository: DailyRoutineRepository,
    private val taskRepository: TaskRepository,
    private val userInstructionRepository: UserInstructionRepository
) {
    companion object {
        private const val TAG = "RoutineGenerator"
        
        // Constants for routine generation
        private val DEFAULT_WAKEUP_TIME = LocalTime.of(6, 0)
        private val DEFAULT_SLEEP_TIME = LocalTime.of(22, 0)
        private val MIN_ROUTINE_ITEMS = 6
        private const val AI_ROUTINE_GENERATION_KEY = "ai_routine_generation"
    }
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    /**
     * Alias for generateRoutine to maintain backward compatibility
     */
    suspend fun generateDailyRoutine(date: LocalDate): DailyRoutine {
        return generateRoutine(date)
    }
    
    /**
     * Generate a new routine for the specified date
     */
    suspend fun generateRoutine(
        date: LocalDate,
        userPreferences: Map<String, Any?> = getUserPreferences(),
        userInstructions: List<String>? = null
    ): DailyRoutine {
        Log.d(TAG, "Generating routine for date: $date")
        
        // Check if routine already exists
        val existingRoutine = dailyRoutineRepository.getRoutineForDate(date)
        if (existingRoutine != null) {
            Log.d(TAG, "Routine already exists for date: $date")
            return existingRoutine
        }
        
        // Get any pending tasks for that day
        val pendingTasks = getPendingTasksForDate(date)
        
        // Get user preferences
        val wakeupTime = (userPreferences["wakeup_time"] as? LocalTime) ?: DEFAULT_WAKEUP_TIME
        val sleepTime = (userPreferences["sleep_time"] as? LocalTime) ?: DEFAULT_SLEEP_TIME
        val focusHours = (userPreferences["focus_hours"] as? Int) ?: 4
        
        // Get user instructions if not provided
        val instructions = userInstructions ?: getUserInstructionsForAI()
        
        // Generate routine items
        val routineItems = generateRoutineItems(
            date,
            wakeupTime,
            sleepTime,
            focusHours,
            pendingTasks,
            instructions
        )
        
        // Create and save the routine
        val routine = DailyRoutine(
            date = date.atStartOfDay(),
            items = routineItems,
            generatedAt = LocalDateTime.now(),
            lastModifiedAt = LocalDateTime.now(),
            isActive = date == LocalDate.now()
        )
        
        val routineId = dailyRoutineRepository.insert(routine)
        if (routine.isActive) {
            dailyRoutineRepository.deactivateOtherRoutines(routineId.toInt())
        }
        
        Log.d(TAG, "Generated routine with ${routineItems.size} items for $date")
        return routine.copy(id = routineId.toInt())
    }
    
    /**
     * Regenerate a routine for the specified date based on user instructions
     */
    suspend fun regenerateRoutine(
        date: LocalDate,
        userInstructions: List<String>,
        preserveCompletedItems: Boolean = true
    ): DailyRoutine {
        Log.d(TAG, "Regenerating routine for date: $date with instructions")
        
        try {
            // Get existing routine if any
            val existingRoutine = dailyRoutineRepository.getRoutineForDate(date)
            Log.d(TAG, "Found existing routine: ${existingRoutine != null}")
            
            // Save completed items if needed
            val completedItems = if (preserveCompletedItems) {
                existingRoutine?.items?.filter { it.isCompleted } ?: emptyList()
            } else {
                emptyList()
            }
            Log.d(TAG, "Found ${completedItems.size} completed items to preserve")
            
            // Delete existing routine if it exists
            if (existingRoutine != null) {
                try {
                    dailyRoutineRepository.delete(existingRoutine)
                    Log.d(TAG, "Successfully deleted existing routine")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting existing routine: ${e.message}", e)
                    // Continue anyway - we'll overwrite it
                }
            }
            
            // Generate new routine with instructions
            Log.d(TAG, "Generating new routine with ${userInstructions.size} instructions")
            val newRoutine = try {
                generateRoutine(date, getUserPreferences(), userInstructions)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating new routine: ${e.message}", e)
                throw Exception("Failed to generate routine: ${e.message}")
            }
            
            Log.d(TAG, "Generated new routine with ${newRoutine.items.size} items")
            
            // If we want to preserve completed items, we need to merge them with the new routine
            if (preserveCompletedItems && completedItems.isNotEmpty()) {
                Log.d(TAG, "Merging ${completedItems.size} completed items into new routine")
                val updatedItems = newRoutine.items.toMutableList()
                
                // Add completed items that don't conflict with new ones
                completedItems.forEach { completedItem ->
                    // Check for time conflicts
                    val hasConflict = updatedItems.any { newItem ->
                        (completedItem.startTime.isBefore(newItem.endTime) && 
                         completedItem.endTime.isAfter(newItem.startTime))
                    }
                    
                    if (!hasConflict) {
                        updatedItems.add(completedItem)
                        Log.d(TAG, "Added completed item: ${completedItem.title}")
                    } else {
                        Log.d(TAG, "Could not add completed item due to time conflict: ${completedItem.title}")
                    }
                }
                
                // Sort the merged items by start time
                val sortedItems = updatedItems.sortedWith(compareBy { it.startTime })
                
                // Update the routine
                val updatedRoutine = newRoutine.copy(
                    items = sortedItems,
                    lastModifiedAt = LocalDateTime.now()
                )
                
                Log.d(TAG, "Saving merged routine with ${updatedRoutine.items.size} items")
                try {
                    dailyRoutineRepository.update(updatedRoutine)
                    Log.d(TAG, "Merged routine saved successfully")
                    return updatedRoutine
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving merged routine: ${e.message}", e)
                    // If update fails, return the original new routine
                    return newRoutine
                }
            }
            
            Log.d(TAG, "Returning new routine without merging (no completed items to preserve)")
            return newRoutine
        } catch (e: Exception) {
            Log.e(TAG, "Error in regenerateRoutine: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Preview a regenerated routine without saving it to the database
     */
    suspend fun previewRegeneratedRoutine(
        date: LocalDate,
        userInstructions: List<String>,
        preserveCompletedItems: Boolean = true
    ): DailyRoutine {
        Log.d(TAG, "Previewing regenerated routine for date: $date with instructions")
        
        try {
            // Get existing routine if any (to preserve completed items)
            val existingRoutine = dailyRoutineRepository.getRoutineForDate(date)
            Log.d(TAG, "Found existing routine for preview: ${existingRoutine != null}")
            
            // Save completed items if needed
            val completedItems = if (preserveCompletedItems) {
                existingRoutine?.items?.filter { it.isCompleted } ?: emptyList()
            } else {
                emptyList()
            }
            Log.d(TAG, "Found ${completedItems.size} completed items to preserve in preview")
            
            // Generate new routine with instructions but don't save it
            Log.d(TAG, "Generating preview routine with ${userInstructions.size} instructions")
            
            // Get user preferences
            val userPrefs = getUserPreferences()
            
            // Get pending tasks for the date
            val pendingTasks = getPendingTasksForDate(date)
            
            // Get the wake and sleep times
            val wakeupTime = (userPrefs["wakeup_time"] as? LocalTime) ?: DEFAULT_WAKEUP_TIME
            val sleepTime = (userPrefs["sleep_time"] as? LocalTime) ?: DEFAULT_SLEEP_TIME
            val focusHours = (userPrefs["focus_hours"] as? Int) ?: 4
            
            // Generate routine items
            val routineItems = generateRoutineItems(
                date,
                wakeupTime,
                sleepTime,
                focusHours,
                pendingTasks,
                userInstructions
            )
            
            // Create a new routine with the generated items (without saving to DB)
            var previewRoutine = DailyRoutine(
                date = date.atStartOfDay(),
                items = routineItems,
                generatedAt = LocalDateTime.now(),
                lastModifiedAt = LocalDateTime.now(),
                isActive = date == LocalDate.now()
            )
            
            // Merge completed items if necessary
            if (preserveCompletedItems && completedItems.isNotEmpty()) {
                // First, sort the completed items by their start times
                val sortedCompletedItems = completedItems.sortedBy { it.startTime }
                
                // Create a mutable list of new routine items
                val mergedItems = routineItems.toMutableList()
                
                // For each completed item, try to add it back (if it doesn't conflict)
                for (completedItem in sortedCompletedItems) {
                    val conflictingItemIndex = mergedItems.indexOfFirst { newItem ->
                        doesTimeOverlap(completedItem.startTime, completedItem.endTime, 
                                       newItem.startTime, newItem.endTime)
                    }
                    
                    if (conflictingItemIndex == -1) {
                        // No conflict, add the completed item
                        mergedItems.add(completedItem)
                    }
                    // If there's a conflict, we skip this completed item
                }
                
                // Sort all items by start time
                val sortedItems = mergedItems.sortedBy { it.startTime }
                
                // Update the preview routine with merged items
                previewRoutine = previewRoutine.copy(items = sortedItems)
            }
            
            Log.d(TAG, "Preview routine generated with ${previewRoutine.items.size} items")
            return previewRoutine
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in previewRegeneratedRoutine: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Checks if two time ranges overlap
     */
    private fun doesTimeOverlap(
        start1: LocalDateTime, 
        end1: LocalDateTime, 
        start2: LocalDateTime, 
        end2: LocalDateTime
    ): Boolean {
        // Two time ranges overlap if start of one is before end of the other
        // and end of one is after start of the other
        return start1.isBefore(end2) && end1.isAfter(start2)
    }
    
    /**
     * Get user instructions for AI routine generation
     */
    private suspend fun getUserInstructionsForAI(): List<String> {
        val instructionPreferences = userInstructionRepository.getAllActiveInstructionsSync()
        
        // Mark all instructions as used
        instructionPreferences.forEach { instruction ->
            userInstructionRepository.markInstructionAsUsed(instruction.id)
        }
        
        return instructionPreferences.map { it.instruction }
    }
    
    /**
     * Generate routine items based on user preferences, tasks, and instructions
     */
    private suspend fun generateRoutineItems(
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime,
        focusHours: Int,
        pendingTasks: List<Task>,
        userInstructions: List<String>? = null
    ): List<RoutineItem> {
        // Try to use AI-based generation if user instructions are provided
        if (!userInstructions.isNullOrEmpty()) {
            try {
                val aiGeneratedItems = generateRoutineItemsWithAI(
                    date, wakeupTime, sleepTime, focusHours, pendingTasks, userInstructions
                )
                if (aiGeneratedItems.isNotEmpty()) {
                    Log.d(TAG, "Successfully generated ${aiGeneratedItems.size} routine items using AI")
                    return validateAndFixRoutineTimeline(aiGeneratedItems, date, wakeupTime, sleepTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating routine with AI: ${e.message}", e)
                // Fall back to rule-based generation if AI fails
            }
        }
        
        // Fallback: rule-based generation (existing implementation)
        val routineItems = mutableListOf<RoutineItem>()
        
        // Add morning routine
        routineItems.add(createRoutineItem(
            "Morning Routine", 
            "Wake up, freshen up, and prepare for the day",
            date.atTime(wakeupTime),
            date.atTime(wakeupTime.plusMinutes(45)),
            priority = 3
        ))
        
        // Add breakfast
        routineItems.add(createRoutineItem(
            "Breakfast", 
            "Healthy breakfast to start the day",
            date.atTime(wakeupTime.plusMinutes(45)),
            date.atTime(wakeupTime.plusMinutes(75)),
            priority = 2
        ))
        
        // Current time position for scheduling
        var currentTime = wakeupTime.plusMinutes(90)
        
        // Schedule focus blocks
        val minutesPerFocusBlock = 50
        val breakMinutes = 10
        val focusBlocksNeeded = (focusHours * 60) / minutesPerFocusBlock
        
        for (i in 1..focusBlocksNeeded) {
            // Skip lunch time (12:00 - 13:00)
            if (currentTime.hour == 12) {
                routineItems.add(createRoutineItem(
                    "Lunch Break", 
                    "Take a proper lunch break",
                    date.atTime(currentTime),
                    date.atTime(currentTime.plusMinutes(60)),
                    priority = 2
                ))
                currentTime = currentTime.plusMinutes(60)
            }
            
            // Make sure we don't schedule focus time beyond sleep time
            if (currentTime.plusMinutes(minutesPerFocusBlock.toLong()).isAfter(sleepTime)) {
                break
            }
            
            // Add focus block
            routineItems.add(createRoutineItem(
                "Focus Time $i", 
                "Deep work session focused on priority tasks",
                date.atTime(currentTime),
                date.atTime(currentTime.plusMinutes(minutesPerFocusBlock.toLong())),
                isFocusTime = true,
                priority = 3
            ))
            
            // Add short break (unless it's the last block or would exceed sleep time)
            if (i < focusBlocksNeeded && !currentTime.plusMinutes((minutesPerFocusBlock + breakMinutes).toLong()).isAfter(sleepTime)) {
                routineItems.add(createRoutineItem(
                    "Short Break", 
                    "Take a short break to refresh",
                    date.atTime(currentTime.plusMinutes(minutesPerFocusBlock.toLong())),
                    date.atTime(currentTime.plusMinutes((minutesPerFocusBlock + breakMinutes).toLong())),
                    priority = 1
                ))
                
                currentTime = currentTime.plusMinutes((minutesPerFocusBlock + breakMinutes).toLong())
            } else {
                currentTime = currentTime.plusMinutes(minutesPerFocusBlock.toLong())
            }
        }
        
        // Add afternoon activities if there's a gap before dinner
        if (currentTime.plusMinutes(60) < LocalTime.of(17, 0) && LocalTime.of(17, 0).isBefore(sleepTime)) {
            routineItems.add(createRoutineItem(
                "Afternoon Activities",
                "Time for personal projects or errands",
                date.atTime(currentTime),
                date.atTime(LocalTime.of(17, 0)),
                priority = 2
            ))
            currentTime = LocalTime.of(17, 0)
        }
        
        // Add dinner if it fits before sleep time
        if (currentTime.plusMinutes(60) < sleepTime) {
            val dinnerTime = if (currentTime.hour < 18) LocalTime.of(18, 0) else currentTime
            
            // Make sure dinner time is not after sleep time
            if (dinnerTime.plusMinutes(60).isBefore(sleepTime)) {
                routineItems.add(createRoutineItem(
                    "Dinner", 
                    "Evening meal",
                    date.atTime(dinnerTime),
                    date.atTime(dinnerTime.plusMinutes(60)),
                    priority = 2
                ))
                
                currentTime = dinnerTime.plusMinutes(60)
            } else {
                // If dinner would go past sleep time, adjust it
                routineItems.add(createRoutineItem(
                    "Dinner", 
                    "Evening meal",
                    date.atTime(currentTime),
                    date.atTime(currentTime.plusMinutes(Math.min(60, ChronoUnit.MINUTES.between(currentTime, sleepTime.minusMinutes(60))))),
                    priority = 2
                ))
                
                currentTime = currentTime.plusMinutes(Math.min(60, ChronoUnit.MINUTES.between(currentTime, sleepTime.minusMinutes(60))))
            }
        }
        
        // Fill any remaining gap before evening routine
        if (currentTime.isBefore(sleepTime.minusMinutes(60)) && ChronoUnit.MINUTES.between(currentTime, sleepTime.minusMinutes(60)) > 30) {
            routineItems.add(createRoutineItem(
                "Evening Free Time", 
                "Relax and wind down with activities you enjoy",
                date.atTime(currentTime),
                date.atTime(sleepTime.minusMinutes(60)),
                priority = 1
            ))
        }
        
        // Add evening routine
        routineItems.add(createRoutineItem(
            "Evening Routine", 
            "Wind down and prepare for sleep",
            date.atTime(sleepTime.minusMinutes(60)),
            date.atTime(sleepTime),
            priority = 2
        ))
        
        // Handle user instructions if provided
        userInstructions?.forEach { instruction ->
            processUserInstruction(instruction, routineItems, date, wakeupTime, sleepTime)
        }
        
        // Process pending tasks and create items for them
        pendingTasks.forEach { task ->
            processTask(task, routineItems, date, wakeupTime, sleepTime)
        }
        
        // Sort items by start time and validate the timeline
        return validateAndFixRoutineTimeline(routineItems.sortedWith(compareBy { it.startTime }), date, wakeupTime, sleepTime)
    }
    
    /**
     * Validate and fix routine timeline to ensure no large gaps
     */
    private fun validateAndFixRoutineTimeline(
        routineItems: List<RoutineItem>, 
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime
    ): List<RoutineItem> {
        if (routineItems.isEmpty()) {
            return routineItems
        }
        
        // Sort items by start time
        val sortedItems = routineItems.sortedBy { it.startTime }
        val result = mutableListOf<RoutineItem>()
        
        // Add all items and check for gaps
        for (i in sortedItems.indices) {
            val item = sortedItems[i]
            result.add(item)
            
            // Check if there's a gap of more than 30 minutes to the next item
            if (i < sortedItems.size - 1) {
                val nextItem = sortedItems[i + 1]
                val gap = ChronoUnit.MINUTES.between(item.endTime, nextItem.startTime)
                
                if (gap > 30) {
                    // Add a filler item
                    result.add(createRoutineItem(
                        "Free Time",
                        "Time to take a break or work on personal tasks",
                        item.endTime,
                        nextItem.startTime,
                        priority = 1
                    ))
                }
            }
        }
        
        // Ensure the routine covers from wake-up to sleep time
        val firstItem = result.first()
        val lastItem = result.last()
        
        // If the routine doesn't start at wake-up time, add a morning item
        if (firstItem.startTime.toLocalTime().isAfter(wakeupTime)) {
            result.add(0, createRoutineItem(
                "Morning Start",
                "Begin your day with intention",
                date.atTime(wakeupTime),
                firstItem.startTime,
                priority = 2
            ))
        }
        
        // If the routine doesn't end at sleep time, add an evening item
        if (lastItem.endTime.toLocalTime().isBefore(sleepTime)) {
            result.add(createRoutineItem(
                "Evening Wind Down",
                "Prepare for restful sleep",
                lastItem.endTime,
                date.atTime(sleepTime),
                priority = 2
            ))
        }
        
        // Sort again after adding any filler items
        return result.sortedBy { it.startTime }
    }
    
    /**
     * Generate routine items using AI based on user instructions and preferences
     */
    private suspend fun generateRoutineItemsWithAI(
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime,
        focusHours: Int,
        pendingTasks: List<Task>,
        userInstructions: List<String>
    ): List<RoutineItem> {
        try {
            // Import required classes
            val apiKeyManager = ApiKeyManager(context)
            var apiKey: String? = null
            
            try {
                apiKey = apiKeyManager.getCurrentApiKey()
                if (apiKey.isNullOrBlank()) {
                    Log.e(TAG, "Failed to get valid API key - no key available")
                    throw Exception("No valid API key available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving API key: ${e.message}", e)
                throw Exception("API key error: ${e.message}")
            }
            
            // Create a structured prompt for the AI
            val prompt = buildAIPrompt(
                date, wakeupTime, sleepTime, focusHours, pendingTasks, userInstructions
            )
            
            // System instructions that tell the AI how to format its response
            val systemPrompt = """
                You are an AI assistant that helps create daily routines.
                You will receive information about a user's wake time, sleep time, focus hour requirements,
                pending tasks, and specific instructions for their routine.
                
                Your response should be a JSON array of routine items in this format:
                [
                  {
                    "title": "Item title",
                    "description": "Item description",
                    "startTime": "HH:MM",
                    "endTime": "HH:MM",
                    "isFocusTime": true/false,
                    "priority": 1-3 (1=low, 3=high)
                  },
                  ...
                ]
                
                Important: 
                1. Times should be in 24-hour format (e.g., "13:30"), and all times must be between the user's wake time and sleep time.
                2. Make sure routine items don't overlap in time.
                3. Limit your response to 8-12 routine items to ensure it fits within response limits.
                4. ONLY respond with valid JSON. No explanatory text before or after.
                5. FOLLOW USER INSTRUCTIONS PRECISELY. If they specify language, activities, or preferences, prioritize these.
                6. Ensure there are no large gaps in the timeline - cover the whole day from wake to sleep time.
            """.trimIndent()
            
            // Create request object with increased max_tokens
            val request = com.focusguard.app.api.models.ChatCompletionRequest(
                model = "gpt-4o-mini",
                messages = listOf(
                    com.focusguard.app.api.models.Message("system", systemPrompt),
                    com.focusguard.app.api.models.Message("user", prompt)
                ),
                temperature = 0.7,
                max_tokens = 1200 // Updated token limit
            )
            
            Log.d(TAG, "Sending AI request for routine generation with prompt length: ${prompt.length}")
            
            // Call the OpenAI API to generate routine
            val result = try {
                com.focusguard.app.api.OpenAIClient.getInstance()
                    .createChatCompletion(apiKey, request)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call: ${e.message}", e)
                // Check if this is a token/authentication issue
                if (e.message?.contains("authentication") == true || 
                    e.message?.contains("auth") == true || 
                    e.message?.contains("key") == true || 
                    e.message?.contains("token") == true ||
                    e.message?.contains("401") == true ||
                    e.message?.contains("403") == true) {
                    // Try to rotate the API key and retry once
                    Log.w(TAG, "API key issue detected, trying to rotate keys")
                    apiKeyManager.rotateToNextKey()
                    val newKey = apiKeyManager.getCurrentApiKey()
                    if (!newKey.isNullOrBlank() && newKey != apiKey) {
                        Log.d(TAG, "Retrying with new API key")
                        com.focusguard.app.api.OpenAIClient.getInstance()
                            .createChatCompletion(newKey, request)
                    } else {
                        throw Exception("Failed to get a new valid API key")
                    }
                } else {
                    throw e
                }
            }
            
            if (result.isSuccess) {
                // Record API usage for rotation tracking
                apiKeyManager.recordUsage()
                
                val content = result.getOrNull() ?: ""
                Log.d(TAG, "AI response received, length: ${content.length}")
                
                if (content.isBlank()) {
                    Log.e(TAG, "Empty response from AI")
                    return emptyList()
                }
                
                // Parse the JSON response into routine items
                val routineItems = parseAIResponse(content, date)
                
                if (routineItems.isEmpty()) {
                    Log.e(TAG, "Failed to parse any routine items from AI response")
                }
                
                return routineItems
            } else {
                val exception = result.exceptionOrNull()
                Log.e(TAG, "Error from AI: ${exception?.message}", exception)
                
                // Check if this is a token-related error and try to rotate the key
                if (exception?.message?.contains("authentication") == true || 
                    exception?.message?.contains("auth") == true || 
                    exception?.message?.contains("key") == true || 
                    exception?.message?.contains("token") == true ||
                    exception?.message?.contains("401") == true ||
                    exception?.message?.contains("403") == true) {
                    Log.w(TAG, "API key issue detected in error response, rotating keys")
                    apiKeyManager.rotateToNextKey()
                }
                
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in AI routine generation", e)
            return emptyList()
        }
    }
    
    /**
     * Build a detailed prompt for the AI based on user preferences and instructions
     */
    private fun buildAIPrompt(
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime,
        focusHours: Int,
        pendingTasks: List<Task>,
        userInstructions: List<String>
    ): String {
        val dateStr = date.format(dateFormatter)
        val wakeupTimeStr = wakeupTime.format(timeFormatter)
        val sleepTimeStr = sleepTime.format(timeFormatter)
        
        // Get AI preferences from the user preferences
        val userPrefs = getUserPreferences()
        val habits = userPrefs["habits"] as? String ?: ""
        val goals = userPrefs["goals"] as? String ?: ""
        val interests = userPrefs["interests"] as? String ?: ""
        
        val taskDetails = if (pendingTasks.isNotEmpty()) {
            pendingTasks.joinToString("\n") { task ->
                "- Task: ${task.title}, Priority: ${task.priority}, " +
                "Est. minutes: ${task.estimatedMinutes ?: 30}, " +
                "Description: ${task.description ?: "N/A"}"
            }
        } else {
            "No specific tasks for today."
        }
        
        // Extract specific keywords from instructions for emphasis
        val keywordsList = mutableListOf<String>()
        userInstructions.forEach { instruction ->
            // Extract potential keywords (languages, activities, etc.)
            val words = instruction.split(" ", ",", ".", "!", "?")
            for (word in words) {
                if (word.length > 3 && !word.equals("routine", ignoreCase = true) && 
                    !word.equals("please", ignoreCase = true) && !word.equals("would", ignoreCase = true) &&
                    !word.equals("should", ignoreCase = true) && !word.equals("could", ignoreCase = true)) {
                    keywordsList.add(word.trim())
                }
            }
        }
        
        // Format user instructions with emphasis
        val instructionsText = userInstructions.joinToString("\n") { "- $it" }
        
        // Create the final prompt with emphasis on key instructions
        val keywordsSection = if (keywordsList.isNotEmpty()) {
            "\nKEY PRIORITIES: ${keywordsList.distinct().joinToString(", ")}"
        } else {
            ""
        }
        
        // Add user preferences section if any are available
        val preferencesSection = buildString {
            if (habits.isNotEmpty() || goals.isNotEmpty() || interests.isNotEmpty()) {
                append("\nUSER PREFERENCES (INCORPORATE THESE INTO THE ROUTINE):\n")
                
                if (habits.isNotEmpty()) {
                    append("Habits to maintain: $habits\n")
                }
                
                if (goals.isNotEmpty()) {
                    append("Goals to work towards: $goals\n")
                }
                
                if (interests.isNotEmpty()) {
                    append("Interests to incorporate: $interests\n")
                }
            }
        }
        
        return """
            Create a daily routine for $dateStr with the following parameters:
            
            Wake up time: $wakeupTimeStr
            Sleep time: $sleepTimeStr
            Required focus hours: $focusHours hours
            
            Pending tasks:
            $taskDetails
            $preferencesSection
            USER INSTRUCTIONS (THESE ARE VERY IMPORTANT - FOLLOW THEM PRECISELY):
            $instructionsText
            $keywordsSection
            
            Create a balanced routine that:
            1. Includes all the specified tasks
            2. Provides $focusHours hours of focused work time
            3. Includes breaks and meal times
            4. STRICTLY FOLLOWS the user's specific instructions
            5. Incorporates the user's habits, goals, and interests when possible
            6. Provides a healthy balance of productive work and rest
            
            Keep the number of routine items between 8-12 for a manageable schedule.
            Return ONLY the routine as a JSON array.
        """.trimIndent()
    }
    
    /**
     * Parse the AI response into routine items
     */
    private fun parseAIResponse(aiResponse: String, date: LocalDate): List<RoutineItem> {
        try {
            // Find JSON content in the response (in case there's explanatory text)
            val jsonPattern = "\\[\\s*\\{.*\\}\\s*\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonPattern.find(aiResponse)
            var jsonContent = jsonMatch?.value ?: aiResponse
            
            // Check if JSON is potentially truncated (missing closing brackets)
            if (!jsonContent.trim().endsWith("]")) {
                Log.w(TAG, "Potentially truncated JSON response, attempting to fix")
                
                // Try to complete it by finding the last complete item
                val itemPattern = "\\{[^\\{\\}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
                val items = itemPattern.findAll(jsonContent).toList()
                
                if (items.isNotEmpty()) {
                    // Reconstruct with only complete items
                    jsonContent = items.dropLast(1).joinToString(",", "[", "]") { it.value }
                    
                    // If we couldn't extract any complete items, return empty list
                    if (jsonContent == "[]") {
                        Log.e(TAG, "Could not extract any complete items from truncated JSON")
                        return emptyList()
                    }
                    
                    Log.d(TAG, "Reconstructed JSON: $jsonContent")
                } else {
                    Log.e(TAG, "Could not parse any items from the AI response")
                    return emptyList()
                }
            }
            
            // Parse JSON with Gson
            val gson = com.google.gson.Gson()
            try {
                val itemsType = object : com.google.gson.reflect.TypeToken<List<AIRoutineItem>>() {}.type
                val aiItems: List<AIRoutineItem> = gson.fromJson(jsonContent, itemsType)
                
                // Convert to app's RoutineItem model
                return aiItems.mapNotNull { aiItem ->
                    try {
                        val startTime = LocalTime.parse(aiItem.startTime, timeFormatter)
                        val endTime = LocalTime.parse(aiItem.endTime, timeFormatter)
                        
                        createRoutineItem(
                            title = aiItem.title,
                            description = aiItem.description,
                            startTime = date.atTime(startTime),
                            endTime = date.atTime(endTime),
                            isCompleted = false,
                            isFocusTime = aiItem.isFocusTime,
                            priority = aiItem.priority
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing AI item: ${e.message}", e)
                        null
                    }
                }
            } catch (e: Exception) {
                // Fall back to individual item parsing if list parsing fails
                Log.w(TAG, "Error parsing full list, trying individual items")
                
                val itemsPattern = "\\{[^\\{\\}]*\\}".toRegex()
                val itemMatches = itemsPattern.findAll(jsonContent)
                val routineItems = mutableListOf<RoutineItem>()
                
                itemMatches.forEach { match ->
                    try {
                        val item: AIRoutineItem = gson.fromJson(match.value, AIRoutineItem::class.java)
                        val startTime = LocalTime.parse(item.startTime, timeFormatter)
                        val endTime = LocalTime.parse(item.endTime, timeFormatter)
                        
                        routineItems.add(createRoutineItem(
                            title = item.title,
                            description = item.description,
                            startTime = date.atTime(startTime),
                            endTime = date.atTime(endTime),
                            isCompleted = false,
                            isFocusTime = item.isFocusTime,
                            priority = item.priority
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing individual item: ${e.message}")
                        // Continue with next item
                    }
                }
                
                return routineItems
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AI response: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Data class for parsing AI response
     */
    private data class AIRoutineItem(
        val title: String,
        val description: String,
        val startTime: String,
        val endTime: String,
        val isFocusTime: Boolean,
        val priority: Int
    )
    
    /**
     * Process a user instruction and modify routine items accordingly
     */
    private fun processUserInstruction(
        instruction: String,
        routineItems: MutableList<RoutineItem>,
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime
    ) {
        // Advanced AI would analyze the instruction here
        // For now, we'll implement simple parsing for some common instructions
        
        val lowerInstruction = instruction.lowercase()
        
        when {
            lowerInstruction.contains("pray") || lowerInstruction.contains("meditation") -> {
                // Add prayer/meditation time in the morning
                val prayerTime = wakeupTime.plusMinutes(15)
                routineItems.add(createRoutineItem(
                    if (lowerInstruction.contains("pray")) "Prayer Time" else "Meditation",
                    "Take time for spiritual connection",
                    date.atTime(prayerTime),
                    date.atTime(prayerTime.plusMinutes(20)),
                    priority = 3
                ))
            }
            lowerInstruction.contains("exercise") || lowerInstruction.contains("workout") -> {
                // Add exercise time
                val exerciseTime = if (lowerInstruction.contains("morning")) {
                    wakeupTime.plusMinutes(30)
                } else {
                    LocalTime.of(18, 0)
                }
                
                routineItems.add(createRoutineItem(
                    "Exercise",
                    "Physical activity to stay healthy",
                    date.atTime(exerciseTime),
                    date.atTime(exerciseTime.plusMinutes(45)),
                    priority = 2
                ))
            }
            lowerInstruction.contains("read") -> {
                // Add reading time in the evening
                val readingTime = sleepTime.minusMinutes(90)
                routineItems.add(createRoutineItem(
                    "Reading Time",
                    "Read a book before bed",
                    date.atTime(readingTime),
                    date.atTime(readingTime.plusMinutes(30)),
                    priority = 1
                ))
            }
            // More instruction processing could be added here
        }
    }
    
    /**
     * Process a task and add it to the routine
     */
    fun processTask(
        task: Task,
        routineItems: MutableList<RoutineItem>,
        date: LocalDate,
        wakeupTime: LocalTime,
        sleepTime: LocalTime
    ) {
        // Find available time slots
        val busyTimeSlots = routineItems.map { 
            Pair(it.startTime.toLocalTime(), it.endTime.toLocalTime()) 
        }
        
        // Sort tasks by priority
        val taskDuration = task.estimatedMinutes?.toLong() ?: 30L
        val availableSlot = findAvailableTimeSlot(
            busyTimeSlots, wakeupTime, sleepTime, taskDuration
        )
        
        if (availableSlot != null) {
            routineItems.add(createRoutineItem(
                task.title,
                task.description ?: "Complete this task",
                date.atTime(availableSlot.first),
                date.atTime(availableSlot.second),
                priority = task.priority
            ))
        }
    }
    
    /**
     * Find an available time slot for a task
     */
    private fun findAvailableTimeSlot(
        busySlots: List<Pair<LocalTime, LocalTime>>,
        wakeupTime: LocalTime,
        sleepTime: LocalTime,
        durationMinutes: Long
    ): Pair<LocalTime, LocalTime>? {
        val sortedBusySlots = busySlots.sortedBy { it.first }
        
        // Start with wake up time
        var currentTime = wakeupTime
        
        for (slot in sortedBusySlots) {
            val gapBeforeSlot = java.time.Duration.between(currentTime, slot.first).toMinutes()
            
            // If gap is sufficient for task, return it
            if (gapBeforeSlot >= durationMinutes) {
                return Pair(currentTime, currentTime.plusMinutes(durationMinutes))
            }
            
            // Move current time to end of this slot
            currentTime = slot.second
        }
        
        // Check if there's room after the last busy slot
        val finalGap = java.time.Duration.between(currentTime, sleepTime).toMinutes()
        if (finalGap >= durationMinutes) {
            return Pair(currentTime, currentTime.plusMinutes(durationMinutes))
        }
        
        // No suitable slot found
        return null
    }
    
    /**
     * Create a routine item with the given parameters
     */
    private fun createRoutineItem(
        title: String,
        description: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        isCompleted: Boolean = false,
        isFocusTime: Boolean = false,
        priority: Int = 1
    ): RoutineItem {
        return RoutineItem(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            isCompleted = isCompleted,
            isFocusTime = isFocusTime,
            priority = priority
        )
    }
    
    /**
     * Get user preferences from shared preferences or other source
     */
    private fun getUserPreferences(): Map<String, Any?> {
        // Load preferences from SharedPreferences
        val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val aiPrefs = context.getSharedPreferences("ai_preferences", Context.MODE_PRIVATE)
        
        // Get wakeup and sleep times
        val wakeupHour = prefs.getInt("wakeup_hour", 6)
        val wakeupMinute = prefs.getInt("wakeup_minute", 0)
        val sleepHour = prefs.getInt("sleep_hour", 22)
        val sleepMinute = prefs.getInt("sleep_minute", 0)
        val focusHours = prefs.getInt("focus_hours", 4)
        
        // Get AI preferences
        val habits = aiPrefs.getString("habits", "") ?: ""
        val goals = aiPrefs.getString("goals", "") ?: ""
        val interests = aiPrefs.getString("interests", "") ?: ""
        
        return mapOf(
            "wakeup_time" to LocalTime.of(wakeupHour, wakeupMinute),
            "sleep_time" to LocalTime.of(sleepHour, sleepMinute),
            "focus_hours" to focusHours,
            "habits" to habits,
            "goals" to goals,
            "interests" to interests
        )
    }
    
    /**
     * Get pending tasks for a specific date
     */
    suspend fun getPendingTasksForDate(date: LocalDate): List<Task> {
        return try {
            taskRepository.getTasksForDate(date)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting tasks for date: ${e.message}", e)
            emptyList()
        }
    }
} 