package com.focusguard.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.data.entity.RoutineItem
import java.time.format.DateTimeFormatter

/**
 * Adapter for displaying routine item previews in the regenerate dialog
 */
class RoutinePreviewAdapter(
    private val timeFormatter: DateTimeFormatter
) : ListAdapter<RoutineItem, RoutinePreviewAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.textViewItemTitle)
        val descriptionTextView: TextView = view.findViewById(R.id.textViewItemDescription)
        val timeTextView: TextView = view.findViewById(R.id.textViewItemTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_routine_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        
        holder.titleTextView.text = item.title
        holder.descriptionTextView.text = item.description
        
        val timeText = "${item.startTime.format(timeFormatter)} - ${item.endTime.format(timeFormatter)}"
        holder.timeTextView.text = timeText
    }

    class DiffCallback : DiffUtil.ItemCallback<RoutineItem>() {
        override fun areItemsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean {
            return oldItem == newItem
        }
    }
} 