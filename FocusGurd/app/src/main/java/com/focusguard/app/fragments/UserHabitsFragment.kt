package com.focusguard.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focusguard.app.R
import com.focusguard.app.adapters.UserHabitsAdapter
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.databinding.FragmentUserHabitsBinding
import com.focusguard.app.viewmodels.UserHabitsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserHabitsFragment : Fragment() {
    
    private var _binding: FragmentUserHabitsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: UserHabitsViewModel
    private lateinit var adapter: UserHabitsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[UserHabitsViewModel::class.java]
        
        setupRecyclerView()
        observeHabits()
        
        binding.fabAddHabit.setOnClickListener {
            showAddHabitDialog()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = UserHabitsAdapter(
            onDeleteClick = { habit ->
                viewModel.deleteHabit(habit)
            }
        )
        
        binding.recyclerViewHabits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@UserHabitsFragment.adapter
        }
    }
    
    private fun observeHabits() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allHabits.collectLatest { habits ->
                adapter.submitList(habits)
                
                // Show empty state if needed
                if (habits.isEmpty()) {
                    binding.emptyStateLayout.visibility = View.VISIBLE
                } else {
                    binding.emptyStateLayout.visibility = View.GONE
                }
            }
        }
    }
    
    private fun showAddHabitDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_habit, null)
        
        val habitNameInput = dialogView.findViewById<TextInputEditText>(R.id.editTextHabitName)
        val habitDescriptionInput = dialogView.findViewById<TextInputEditText>(R.id.editTextHabitDescription)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_habit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val habitName = habitNameInput.text.toString().trim()
                val habitDescription = habitDescriptionInput.text.toString().trim()
                val isDistracting = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDistracting).isChecked
                
                if (habitName.isNotEmpty()) {
                    val habit = UserHabit(
                        habitName = habitName,
                        description = habitDescription,
                        isDistracting = isDistracting
                    )
                    viewModel.addHabit(habit)
                    Toast.makeText(requireContext(), R.string.habit_added, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.habit_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 