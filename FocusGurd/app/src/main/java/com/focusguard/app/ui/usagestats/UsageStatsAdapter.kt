package com.focusguard.app.ui.usagestats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import java.util.concurrent.TimeUnit

class UsageStatsAdapter : ListAdapter<UsageStatsViewModel.AppUsageInfo, UsageStatsAdapter.ViewHolder>(DIFF_CALLBACK) {

    // Interface for click listeners
    interface OnItemClickListener {
        fun onItemClick(appInfo: UsageStatsViewModel.AppUsageInfo)
    }

    private var clickListener: OnItemClickListener? = null
    private var totalUsageTime: Long = 0

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    fun setTotalUsageTime(time: Long) {
        totalUsageTime = time
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener, totalUsageTime)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val appIcon: ImageView = view.findViewById(R.id.app_icon)
        private val appName: TextView = view.findViewById(R.id.app_name)
        private val packageName: TextView = view.findViewById(R.id.package_name)
        private val usageTime: TextView = view.findViewById(R.id.usage_time)
        private val usageProgressBar: ProgressBar = view.findViewById(R.id.usage_progress_bar)
        private val usagePercentage: TextView = view.findViewById(R.id.usage_percentage)

        fun bind(item: UsageStatsViewModel.AppUsageInfo, listener: OnItemClickListener?, totalTime: Long) {
            appName.text = item.appName
            packageName.text = item.packageName
            
            // Set app icon
            item.appIcon?.let {
                appIcon.setImageDrawable(it)
            } ?: run {
                appIcon.setImageResource(R.mipmap.ic_launcher)
            }
            
            // Format usage time
            val hours = TimeUnit.MILLISECONDS.toHours(item.timeInForeground)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(item.timeInForeground) % 60
            
            usageTime.text = if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
            
            // Calculate and set percentage - fix calculation to avoid 0%
            val percentage = if (totalTime > 0 && item.timeInForeground > 0) {
                val pct = (item.timeInForeground * 100.0 / totalTime).toInt()
                // Ensure minimum 1% if there's any usage at all
                maxOf(1, pct)
            } else {
                0
            }
            
            usageProgressBar.progress = percentage
            usagePercentage.text = "$percentage%"
            
            // Set click listener
            itemView.setOnClickListener {
                listener?.onItemClick(item)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<UsageStatsViewModel.AppUsageInfo>() {
            override fun areItemsTheSame(oldItem: UsageStatsViewModel.AppUsageInfo, newItem: UsageStatsViewModel.AppUsageInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: UsageStatsViewModel.AppUsageInfo, newItem: UsageStatsViewModel.AppUsageInfo): Boolean {
                return oldItem.timeInForeground == newItem.timeInForeground
            }
        }
    }
} 