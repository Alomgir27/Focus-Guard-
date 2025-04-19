package com.focusguard.app.fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.databinding.FragmentDailyRoutineBinding
import com.focusguard.app.data.entity.RoutineItem
import com.focusguard.app.viewmodels.DailyRoutineViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class DailyRoutineFragment : Fragment() {

    private var _binding: FragmentDailyRoutineBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: DailyRoutineViewModel
    private lateinit var adapter: RoutineItemAdapter
    
    // Handler for updating the current time and progress
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateCurrentTimeAndProgress()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }
    
    companion object {
        private const val UPDATE_INTERVAL = 60000L // Update every minute
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[DailyRoutineViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        adapter = RoutineItemAdapter(
            onItemClick = { /* Handle item click */ },
            onCheckboxClick = { item -> viewModel.toggleItemCompletion(viewModel.currentRoutine.value?.id ?: 0, item) },
            timeFormatter = viewModel.timeFormatter,
            progressCalculator = { item -> viewModel.calculateItemProgress(item) }
        )
        
        binding.rvRoutineItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutineItems.adapter = adapter
        
        // Add swipe-to-delete functionality
        setupItemTouchHelper()
        
        // Set up pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            val currentDate = viewModel.selectedDate.value ?: LocalDate.now()
            viewModel.refreshRoutine(currentDate)
        }
    }
    
    /**
     * Set up swipe to delete functionality
     */
    private fun setupItemTouchHelper() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, // No drag and drop
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Support swiping in both directions
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We don't support moving items around
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                
                // Delete the item using ViewModel
                val routineId = viewModel.currentRoutine.value?.id ?: 0
                viewModel.deleteRoutineItem(item.id)
                
                // Show an undo snackbar
                Snackbar.make(
                    binding.root,
                    "Routine item deleted",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore the item if user clicks undo
                    viewModel.restoreDeletedItem(routineId, item)
                }.show()
            }
            
            // Add background and icon during swipe
            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val height = itemView.bottom - itemView.top
                    val background = ColorDrawable()
                    val deleteIcon = ContextCompat.getDrawable(
                        requireContext(),
                        android.R.drawable.ic_menu_delete
                    )
                    
                    // Set background color
                    background.color = Color.parseColor("#EF5350") // Red color
                    
                    // Calculate icon dimensions and position
                    val iconMargin = (height - deleteIcon!!.intrinsicHeight) / 2
                    val iconTop = itemView.top + (height - deleteIcon.intrinsicHeight) / 2
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight
                    
                    // Swiping to the right
                    if (dX > 0) {
                        val iconLeft = itemView.left + iconMargin
                        val iconRight = iconLeft + deleteIcon.intrinsicWidth
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        
                        background.setBounds(
                            itemView.left, itemView.top,
                            itemView.left + dX.toInt(), itemView.bottom
                        )
                    } 
                    // Swiping to the left
                    else if (dX < 0) {
                        val iconRight = itemView.right - iconMargin
                        val iconLeft = iconRight - deleteIcon.intrinsicWidth
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        
                        background.setBounds(
                            itemView.right + dX.toInt(), itemView.top,
                            itemView.right, itemView.bottom
                        )
                    } 
                    // No swipe
                    else {
                        background.setBounds(0, 0, 0, 0)
                    }
                    
                    background.draw(canvas)
                    deleteIcon.draw(canvas)
                }
                
                super.onChildDraw(
                    canvas, recyclerView, viewHolder, dX, dY,
                    actionState, isCurrentlyActive
                )
            }
        }
        
        // Attach the swipe handler to the RecyclerView
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvRoutineItems)
    }
    
    private fun setupListeners() {
        // Add new routine item button
        binding.fabAddRoutineItem.setOnClickListener {
            try {
                // Show dialog to add new routine item
                showAddRoutineItemDialog()
            } catch (e: Exception) {
                Log.e("DailyRoutineFragment", "Error showing add routine dialog: ${e.message}", e)
                // Show a toast message if there's an error
                Toast.makeText(context, "Error showing dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Set up date selection in calendar
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            viewModel.changeDate(selectedDate)
        }
        
        // Set up regenerate button in toolbar
        binding.buttonRegenerateRoutine.setOnClickListener {
            showRegenerateRoutineDialog()
        }
    }
    
    private fun observeViewModel() {
        // Observe selected date
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            binding.tvDate.text = date.format(viewModel.dateFormatter)
            
            // Set an empty title to remove any header text
            binding.collapsingToolbar.title = ""
            binding.toolbar.subtitle = null
        }
        
        // Observe routine data
        viewModel.currentRoutine.observe(viewLifecycleOwner) { routine ->
            Log.d("DailyRoutineFragment", "Observed routine update: ${routine?.id}, items: ${routine?.items?.size ?: 0}")
            
            // Stop refresh animation if it's running
            binding.swipeRefreshLayout.isRefreshing = false
            
            if (routine != null && routine.items.isNotEmpty()) {
                Log.d("DailyRoutineFragment", "Updating adapter with ${routine.items.size} items")
                adapter.submitList(routine.items)
                binding.tvEmptyRoutine.visibility = View.GONE
                binding.rvRoutineItems.visibility = View.VISIBLE
                updateCurrentActivity(routine.items)
            } else {
                Log.d("DailyRoutineFragment", "No items in routine, showing empty state")
                adapter.submitList(emptyList())
                binding.tvEmptyRoutine.visibility = View.VISIBLE
                binding.rvRoutineItems.visibility = View.GONE
                // Clear current activity
                binding.cardCurrentActivity.visibility = View.GONE
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show loading indicator if needed
            binding.swipeRefreshLayout.isRefreshing = isLoading
            if (isLoading) {
                Log.d("DailyRoutineFragment", "Loading state: $isLoading")
            }
        }
        
        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                // Show error message
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateCurrentTimeAndProgress() {
        val now = LocalDateTime.now()
        
        viewModel.currentRoutine.value?.let { routine ->
            // Update current activity
            updateCurrentActivity(routine.items)
            
            // Refresh adapter to update progress bars
            adapter.notifyDataSetChanged()
        }
    }
    
    private fun updateCurrentActivity(items: List<RoutineItem>) {
        val now = LocalDateTime.now()
        val currentItem = items.firstOrNull { 
            it.startTime.isBefore(now) && it.endTime.isAfter(now)
        }
        
        // Always show the card
        binding.cardCurrentActivity.visibility = View.VISIBLE
        
        if (currentItem != null) {
            // Display current activity info
            binding.tvCurrentActivityTitle.text = currentItem.title
            binding.tvCurrentActivityTime.text = String.format(
                "%s - %s",
                currentItem.startTime.format(viewModel.timeFormatter),
                currentItem.endTime.format(viewModel.timeFormatter)
            )
            
            // Calculate and display progress
            val progress = viewModel.calculateItemProgress(currentItem) * 100
            binding.progressCurrentActivity.progress = progress.toInt()
            binding.progressCurrentActivity.visibility = View.VISIBLE
        } else {
            // Show message that there's no current scheduled activity
            binding.tvCurrentActivityTitle.text = "Free Time"
            binding.tvCurrentActivityTime.text = "No scheduled activity at this time"
            binding.progressCurrentActivity.visibility = View.GONE
            
            // Find the next upcoming activity to display
            val nextItem = items.firstOrNull { it.startTime.isAfter(now) }
            if (nextItem != null) {
                // Calculate time until next activity
                val minutesUntilNext = java.time.Duration.between(now, nextItem.startTime).toMinutes()
                binding.tvCurrentActivityTime.text = String.format(
                    "Next activity in %d min: %s at %s",
                    minutesUntilNext,
                    nextItem.title,
                    nextItem.startTime.format(viewModel.timeFormatter)
                )
            }
        }
    }
    
    private fun showAddRoutineItemDialog() {
        try {
            // Create dialog layout
            val dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_add_routine_item, null
            )
            
            // Get references to views in the dialog
            val titleEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemTitle)
            val descriptionEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemDescription)
            val startTimeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTime)
            val endTimeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEndTime)
            val prioritySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerPriority)
            val focusSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchFocusTime)
            
            // Set up priority spinner
            val priorities = arrayOf("Low", "Medium", "High")
            val adapter = android.widget.ArrayAdapter(
                requireContext(), 
                android.R.layout.simple_spinner_item, 
                priorities
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            prioritySpinner.adapter = adapter
            
            // Default times (current hour rounded to nearest half hour for start, +1 hour for end)
            var startTime = LocalDateTime.now().withMinute(if (LocalDateTime.now().minute < 30) 0 else 30)
            var endTime = startTime.plusHours(1)
            
            // Update button text to show the selected times
            startTimeButton.text = startTime.format(viewModel.timeFormatter)
            endTimeButton.text = endTime.format(viewModel.timeFormatter)
            
            // Set up time pickers
            startTimeButton.setOnClickListener {
                showTimePicker(startTime) { selectedTime ->
                    startTime = selectedTime
                    startTimeButton.text = startTime.format(viewModel.timeFormatter)
                    
                    // If end time is before or equal to start time, update it
                    if (!endTime.isAfter(startTime)) {
                        endTime = startTime.plusHours(1)
                        endTimeButton.text = endTime.format(viewModel.timeFormatter)
                    }
                }
            }
            
            endTimeButton.setOnClickListener {
                showTimePicker(endTime) { selectedTime ->
                    // Only allow end time to be after start time
                    if (selectedTime.isAfter(startTime)) {
                        endTime = selectedTime
                        endTimeButton.text = endTime.format(viewModel.timeFormatter)
                    } else {
                        // Show error message
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "End time must be after start time",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            
            // Create the dialog
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add New Routine Item")
                .setView(dialogView)
                .setPositiveButton("Add") { _, _ ->
                    try {
                        // Get values from dialog
                        val title = titleEditText.text.toString()
                        val description = descriptionEditText.text.toString()
                        val priority = prioritySpinner.selectedItemPosition + 1 // 1=Low, 2=Medium, 3=High
                        val isFocusTime = focusSwitch.isChecked
                        
                        // Validate title
                        if (title.isBlank()) {
                            com.google.android.material.snackbar.Snackbar.make(
                                binding.root,
                                "Title cannot be empty",
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                            ).show()
                            return@setPositiveButton
                        }
                        
                        // Create a new routine item
                        val newItem = RoutineItem(
                            id = java.util.UUID.randomUUID().toString(),
                            title = title,
                            description = if (description.isBlank()) null else description,
                            startTime = startTime,
                            endTime = endTime,
                            isCompleted = false,
                            isFocusTime = isFocusTime,
                            priority = priority
                        )
                        
                        // Add the item to the current routine
                        val routineId = viewModel.currentRoutine.value?.id ?: 0
                        Log.d("DailyRoutineFragment", "Adding routine item to routine ID: $routineId")
                        
                        try {
                            viewModel.addRoutineItem(routineId, newItem)
                            
                            // Show success message
                            Toast.makeText(context, "Routine item added", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("DailyRoutineFragment", "Error adding routine item: ${e.message}", e)
                            
                            // If we failed to add to routine ID, try creating a new routine
                            if (e.message?.contains("Routine not found") == true || e.message?.contains("routine") == true) {
                                Log.w("DailyRoutineFragment", "Trying to create a new routine...")
                                try {
                                    // Create new routine directly
                                    viewModel.addRoutineItem(0, newItem)
                                    Toast.makeText(context, "Created new routine with your item", Toast.LENGTH_SHORT).show()
                                } catch (e2: Exception) {
                                    Log.e("DailyRoutineFragment", "Final error adding item: ${e2.message}", e2)
                                    Toast.makeText(context, "Failed to add item: ${e2.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Error adding item: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DailyRoutineFragment", "Error adding routine item: ${e.message}", e)
                        Toast.makeText(context, "Error adding item: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()
            
            dialog.show()
        } catch (e: Exception) {
            Log.e("DailyRoutineFragment", "Error creating add dialog: ${e.message}", e)
            Toast.makeText(context, "Error creating dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showTimePicker(initialTime: LocalDateTime, onTimeSelected: (LocalDateTime) -> Unit) {
        val picker = android.app.TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val newTime = initialTime
                    .withHour(hourOfDay)
                    .withMinute(minute)
                onTimeSelected(newTime)
            },
            initialTime.hour,
            initialTime.minute,
            false // 12-hour format
        )
        picker.show()
    }
    
    private fun showRegenerateRoutineDialog() {
        val currentDate = viewModel.selectedDate.value ?: LocalDate.now()
        val dialog = RegenerateRoutineDialog.newInstance(currentDate)
        dialog.show(childFragmentManager, "regenerate_routine_dialog")
    }
    
    override fun onResume() {
        super.onResume()
        // Start updating the UI
        handler.post(updateTimeRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Stop updating the UI when fragment is not visible
        handler.removeCallbacks(updateTimeRunnable)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying routine items in a RecyclerView
 */
class RoutineItemAdapter(
    private val onItemClick: (RoutineItem) -> Unit,
    private val onCheckboxClick: (RoutineItem) -> Unit,
    private val timeFormatter: java.time.format.DateTimeFormatter,
    private val progressCalculator: (RoutineItem) -> Float
) : androidx.recyclerview.widget.ListAdapter<RoutineItem, RoutineItemAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.focusguard.app.databinding.ItemRoutineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: com.focusguard.app.databinding.ItemRoutineBinding) : 
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.checkboxComplete.setOnClickListener {
                val position = adapterPosition
                if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    onCheckboxClick(getItem(position))
                }
            }
        }
        
        fun bind(item: RoutineItem) {
            binding.tvItemTitle.text = item.title
            binding.tvItemDescription.text = item.description
            binding.tvItemStartTime.text = item.startTime.format(timeFormatter)
            binding.tvItemEndTime.text = item.endTime.format(timeFormatter)
            binding.checkboxComplete.isChecked = item.isCompleted
            
            // Set priority indicator color based on priority level
            val colorRes = when (item.priority) {
                3 -> android.R.color.holo_red_dark
                2 -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_blue_dark
            }
            binding.viewPriority.setBackgroundColor(binding.root.context.getColor(colorRes))
            
            // Show focus icon if this is a focus time
            binding.ivFocusIcon.visibility = if (item.isFocusTime) View.VISIBLE else View.GONE
            
            // Update progress bar
            val progress = progressCalculator(item) * 100
            binding.progressItem.progress = progress.toInt()
        }
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<RoutineItem>() {
        override fun areItemsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean {
            return oldItem == newItem
        }
    }
} 