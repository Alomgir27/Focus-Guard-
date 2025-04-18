package com.focusguard.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(
        itemView: View,
        private val onItemClick: (Notification) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val typeView: TextView = itemView.findViewById(R.id.textViewNotificationType)
        private val timeView: TextView = itemView.findViewById(R.id.textViewNotificationTime)
        private val titleView: TextView = itemView.findViewById(R.id.textViewNotificationTitle)
        private val contentView: TextView = itemView.findViewById(R.id.textViewNotificationContent)
        
        private var currentNotification: Notification? = null
        
        init {
            itemView.setOnClickListener {
                currentNotification?.let { notification ->
                    onItemClick(notification)
                }
            }
        }
        
        fun bind(notification: Notification) {
            currentNotification = notification
            
            // Set notification type display name and color
            typeView.text = getTypeDisplayName(notification.type)
            typeView.setBackgroundResource(getTypeBackgroundColor(notification.type))
            
            // Format the time
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            timeView.text = notification.createdAt.format(formatter)
            
            // Set title and full content
            titleView.text = notification.title
            contentView.text = notification.content
            
            // Visual indication for unread notifications
            val alpha = if (notification.wasClicked) 0.7f else 1.0f
            itemView.alpha = alpha
        }
        
        private fun getTypeDisplayName(type: NotificationType): String {
            return when (type) {
                NotificationType.HABIT_REMINDER -> "HABIT"
                NotificationType.MOTIVATION -> "MOTIVATION"
                NotificationType.INSIGHT -> "INSIGHT"
                NotificationType.RELIGIOUS_QUOTE -> "QUOTE"
                NotificationType.APP_USAGE_WARNING -> "WARNING"
                NotificationType.GENERAL -> "GENERAL"
            }
        }
        
        private fun getTypeBackgroundColor(type: NotificationType): Int {
            return R.drawable.rounded_badge_background
            // In the future, could return different drawable resources based on type
        }
    }
    
    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
} 