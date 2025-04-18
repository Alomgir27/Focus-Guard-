package com.focusguard.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.models.AppInfo

/**
 * Adapter for app selection in dialog
 */
class AppSelectionAdapter(
    private val apps: List<AppInfo>,
    initialSelectedPackages: Set<String> = emptySet()
) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>() {

    private val selectedPackages = initialSelectedPackages.toMutableSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    /**
     * Get the set of currently selected package names
     */
    fun getSelectedPackages(): Set<String> = selectedPackages

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)

        fun bind(app: AppInfo) {
            // Set app data
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            
            // Set checkbox state
            checkbox.isChecked = selectedPackages.contains(app.packageName)
            
            // Handle click events
            itemView.setOnClickListener {
                toggleSelection(app)
            }
            
            checkbox.setOnClickListener {
                toggleSelection(app)
            }
        }
        
        private fun toggleSelection(app: AppInfo) {
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName)
                checkbox.isChecked = false
            } else {
                selectedPackages.add(app.packageName)
                checkbox.isChecked = true
            }
        }
    }
} 