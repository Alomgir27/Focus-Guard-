package com.focusguard.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.data.entity.UserHabit
import com.focusguard.app.databinding.ItemUserHabitBinding

class UserHabitsAdapter(
    private val onDeleteClick: (UserHabit) -> Unit
) : ListAdapter<UserHabit, UserHabitsAdapter.HabitViewHolder>(HabitDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemUserHabitBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HabitViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class HabitViewHolder(
        private val binding: ItemUserHabitBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(habit: UserHabit) {
            binding.apply {
                textViewHabitName.text = habit.habitName
                textViewHabitDescription.text = habit.description
                
                // Set chip appearance based on habit type
                if (habit.isDistracting) {
                    chipHabitType.text = "Distracting"
                    chipHabitType.setChipBackgroundColorResource(com.focusguard.app.R.color.distracting_habit)
                } else {
                    chipHabitType.text = "Positive"
                    chipHabitType.setChipBackgroundColorResource(com.focusguard.app.R.color.positive_habit)
                }
                
                buttonDelete.setOnClickListener {
                    onDeleteClick(habit)
                }
            }
        }
    }
    
    private class HabitDiffCallback : DiffUtil.ItemCallback<UserHabit>() {
        override fun areItemsTheSame(oldItem: UserHabit, newItem: UserHabit): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: UserHabit, newItem: UserHabit): Boolean {
            return oldItem == newItem
        }
    }
} 