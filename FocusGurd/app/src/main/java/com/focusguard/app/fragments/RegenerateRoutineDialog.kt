package com.focusguard.app.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.adapters.RoutinePreviewAdapter
import com.focusguard.app.data.entity.DailyRoutine
import com.focusguard.app.data.entity.UserInstructionPreference
import com.focusguard.app.data.repository.UserInstructionRepository
import com.focusguard.app.databinding.DialogRegenerateRoutineBinding
import com.focusguard.app.viewmodels.DailyRoutineViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class RegenerateRoutineDialog : DialogFragment() {
    
    private var _binding: DialogRegenerateRoutineBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var routineViewModel: DailyRoutineViewModel
    private lateinit var userInstructionRepository: UserInstructionRepository
    private lateinit var previewAdapter: RoutinePreviewAdapter
    private var accessibilityDialog: androidx.appcompat.app.AlertDialog? = null
    
    private var selectedDate: LocalDate = LocalDate.now()
    private var previewedRoutine: DailyRoutine? = null
    private val TAG = "RegenerateRoutineDialog"
    
    companion object {
        private const val ARG_DATE = "selected_date"
        
        fun newInstance(date: LocalDate): RegenerateRoutineDialog {
            val args = Bundle().apply {
                putString(ARG_DATE, date.toString())
            }
            return RegenerateRoutineDialog().apply {
                arguments = args
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
        
        arguments?.getString(ARG_DATE)?.let {
            selectedDate = LocalDate.parse(it)
        }
        
        try {
            userInstructionRepository = MyApplication.userInstructionRepository
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing repository: ${e.message}", e)
            Toast.makeText(context, "Error initializing: ${e.message}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            _binding = DialogRegenerateRoutineBinding.inflate(inflater, container, false)
            return binding.root
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView: ${e.message}", e)
            // Fallback to an empty view if there's an error
            return View(requireContext())
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            // Check accessibility permission first
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPermissionDialog()
                return
            }
            
            routineViewModel = ViewModelProvider(requireActivity())[DailyRoutineViewModel::class.java]
            
            setupToolbar()
            setupRecyclerView()
            loadPreviousInstructions()
            setupInstructionChips()
            setupButtons()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error loading dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = "Regenerate Routine"
        binding.toolbar.subtitle = "Custom instructions for AI"
        
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }
    
    private fun setupRecyclerView() {
        previewAdapter = RoutinePreviewAdapter(routineViewModel.timeFormatter)
        binding.recyclerViewPreview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = previewAdapter
        }
    }
    
    private fun loadPreviousInstructions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val instructions = userInstructionRepository.getAllActiveInstructionsSync()
                displayPreviousInstructions(instructions)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading instructions: ${e.message}", e)
            }
        }
    }
    
    private fun displayPreviousInstructions(instructions: List<UserInstructionPreference>) {
        binding.chipGroupPreviousInstructions.removeAllViews()
        
        // If no previous instructions, hide the section
        if (instructions.isEmpty()) {
            binding.textViewPreviousInstructions.visibility = View.GONE
            binding.chipGroupPreviousInstructions.visibility = View.GONE
            return
        }
        
        // Show previous instructions section
        binding.textViewPreviousInstructions.visibility = View.VISIBLE
        binding.chipGroupPreviousInstructions.visibility = View.VISIBLE
        
        // Add each instruction as a chip
        instructions.forEach { instruction ->
            val chip = Chip(requireContext()).apply {
                text = instruction.instruction
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val currentText = binding.editTextInstructions.text.toString()
                        if (currentText.isNotEmpty()) {
                            binding.editTextInstructions.setText("$currentText\n${instruction.instruction}")
                        } else {
                            binding.editTextInstructions.setText(instruction.instruction)
                        }
                    }
                }
            }
            binding.chipGroupPreviousInstructions.addView(chip)
        }
    }
    
    private fun setupInstructionChips() {
        val commonInstructions = listOf(
            "Add more exercise time",
            "Add prayer/meditation time",
            "More focus blocks",
            "More breaks",
            "Earlier wakeup time",
            "Later sleep time",
            "Include reading time",
            "Schedule meal prep"
        )
        
        binding.chipGroupSuggestions.removeAllViews()
        commonInstructions.forEach { instruction ->
            val chip = Chip(requireContext()).apply {
                text = instruction
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val currentText = binding.editTextInstructions.text.toString()
                        if (currentText.isNotEmpty()) {
                            binding.editTextInstructions.setText("$currentText\n$instruction")
                        } else {
                            binding.editTextInstructions.setText(instruction)
                        }
                    }
                }
            }
            binding.chipGroupSuggestions.addView(chip)
        }
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonRegenerateRoutine.setOnClickListener {
            val instructions = binding.editTextInstructions.text.toString().trim()
            
            if (instructions.isNotEmpty()) {
                generatePreview(instructions)
            } else {
                binding.textInputLayoutInstructions.error = "Please enter instructions for AI"
            }
        }
        
        binding.buttonBackToEdit.setOnClickListener {
            showInputMode()
        }
        
        binding.buttonApplyRoutine.setOnClickListener {
            applyPreviewedRoutine()
        }
    }
    
    private fun showInputMode() {
        // Show input elements
        binding.textInputLayoutInstructions.visibility = View.VISIBLE
        binding.textViewPreviousInstructions.visibility = View.VISIBLE
        binding.chipGroupPreviousInstructions.visibility = View.VISIBLE
        binding.buttonContainerInput.visibility = View.VISIBLE
        
        // Make sure the Generate Preview button is enabled
        binding.buttonRegenerateRoutine.isEnabled = true
        
        // Hide preview elements
        binding.previewSectionContainer.visibility = View.GONE
        binding.buttonContainerPreview.visibility = View.GONE
    }
    
    private fun showPreviewMode() {
        // Hide input elements
        binding.textInputLayoutInstructions.visibility = View.GONE
        binding.textViewPreviousInstructions.visibility = View.GONE
        binding.chipGroupPreviousInstructions.visibility = View.GONE
        binding.buttonContainerInput.visibility = View.GONE
        
        // Show preview elements
        binding.previewSectionContainer.visibility = View.VISIBLE
        binding.buttonContainerPreview.visibility = View.VISIBLE
    }
    
    private fun generatePreview(instructions: String) {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.buttonRegenerateRoutine.isEnabled = false
        binding.textInputLayoutInstructions.error = null
        
        Log.d(TAG, "Attempting to generate preview for date: $selectedDate")
        
        // Save the instruction for future use
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Check if accessibility service is enabled again
                if (!isAccessibilityServiceEnabled()) {
                    Log.w(TAG, "Accessibility service not enabled")
                    showAccessibilityPermissionDialog()
                    binding.progressBar.visibility = View.GONE
                    binding.buttonRegenerateRoutine.isEnabled = true
                    return@launch
                }
                
                // Save instruction to repository
                try {
                    userInstructionRepository.addInstruction(instructions)
                    Log.d(TAG, "Saved instruction to repository")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving instruction: ${e.message}", e)
                    // Continue even if saving instruction fails
                }
                
                // Split instructions into a list (by line breaks)
                val instructionsList = instructions.split("\n").filter { it.isNotEmpty() }
                Log.d(TAG, "Split instructions into ${instructionsList.size} items")
                
                try {
                    // Show toast before starting preview generation
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(), 
                            "Generating routine preview...", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // Generate routine preview
                    Log.d(TAG, "Calling previewRegeneratedRoutine")
                    previewedRoutine = routineViewModel.previewRegeneratedRoutine(selectedDate, instructionsList)
                    
                    Log.d(TAG, "Preview generation successful")
                    
                    // Display the preview
                    withContext(Dispatchers.Main) {
                        displayPreview(previewedRoutine)
                        binding.progressBar.visibility = View.GONE
                        
                        // Switch to preview mode
                        showPreviewMode()
                    }
                } catch (e: Exception) {
                    // Handle generation error
                    Log.e(TAG, "Error generating preview: ${e.message}", e)
                    
                    withContext(Dispatchers.Main) {
                        binding.textInputLayoutInstructions.error = "Error: ${e.message}"
                        
                        // Also show a toast with the error
                        Toast.makeText(
                            requireContext(),
                            "Failed to generate preview: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        binding.progressBar.visibility = View.GONE
                        binding.buttonRegenerateRoutine.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                
                withContext(Dispatchers.Main) {
                    binding.textInputLayoutInstructions.error = "Unexpected error: ${e.message}"
                    binding.progressBar.visibility = View.GONE
                    binding.buttonRegenerateRoutine.isEnabled = true
                    
                    Toast.makeText(
                        requireContext(),
                        "An unexpected error occurred: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun displayPreview(routine: DailyRoutine?) {
        if (routine == null || routine.items.isEmpty()) {
            binding.recyclerViewPreview.visibility = View.GONE
            binding.textViewPreviewEmpty.visibility = View.VISIBLE
            return
        }
        
        binding.recyclerViewPreview.visibility = View.VISIBLE
        binding.textViewPreviewEmpty.visibility = View.GONE
        
        // Submit sorted list to the adapter
        val sortedItems = routine.items.sortedBy { it.startTime }
        previewAdapter.submitList(sortedItems)
    }
    
    private fun applyPreviewedRoutine() {
        if (previewedRoutine == null) {
            Toast.makeText(requireContext(), "No preview to apply", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.buttonApplyRoutine.isEnabled = false
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Apply the previewed routine
                routineViewModel.applyPreviewedRoutine(previewedRoutine!!)
                
                // Force refresh the UI
                if (previewedRoutine!!.date.toLocalDate() == LocalDate.now()) {
                    routineViewModel.refreshTodayRoutine()
                    // Give the database time to update
                    delay(500)
                }
                
                // Show success message and dismiss dialog
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Routine applied successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    dismiss()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying preview: ${e.message}", e)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to apply routine: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    binding.progressBar.visibility = View.GONE
                    binding.buttonApplyRoutine.isEnabled = true
                }
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val context = requireContext()
            val accessibilityEnabled = try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }
            
            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                services?.let {
                    val packageName = context.packageName
                    return it.contains("$packageName/$packageName.services.AppBlockerAccessibilityService")
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}", e)
            return false
        }
    }
    
    private fun showAccessibilityPermissionDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Accessibility Permission Required")
            .setMessage("FocusGuard requires accessibility service to function properly. Please enable it in Settings.")
            .setCancelable(false)
            .setPositiveButton("Enable Now") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    
                    // Record timestamp when we redirect to accessibility settings
                    requireContext().getSharedPreferences("app_permissions", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_accessibility_settings_time", System.currentTimeMillis())
                        .apply()
                        
                    startActivity(intent)
                    Toast.makeText(
                        requireContext(),
                        "Find and enable 'FocusGuard: App Blocker' in the list",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Don't dismiss the dialog - we'll check when we return
                    // The user can press Cancel if they don't want to enable
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening accessibility settings: ${e.message}", e)
                    dismiss() // Only dismiss if there was an error opening settings
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                dismiss() // Just close the dialog if user cancels
            }
            .create()
            
        // Store the dialog reference
        accessibilityDialog = dialog
        dialog.show()
    }
    
    override fun onResume() {
        super.onResume()
        
        // When fragment resumes, check if accessibility service is now enabled
        if (isAccessibilityServiceEnabled()) {
            // If we were showing the accessibility dialog and now have permission, dismiss it
            accessibilityDialog?.let {
                if (it.isShowing) {
                    it.dismiss()
                    accessibilityDialog = null
                    
                    // Refresh the UI to properly initialize
                    try {
                        routineViewModel = ViewModelProvider(requireActivity())[DailyRoutineViewModel::class.java]
                        setupToolbar()
                        setupRecyclerView()
                        loadPreviousInstructions()
                        setupInstructionChips()
                        setupButtons()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing UI after enabling permission: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        // Dismiss any showing dialog to prevent window leaks
        accessibilityDialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        accessibilityDialog = null
        
        super.onDestroyView()
        _binding = null
    }
} 