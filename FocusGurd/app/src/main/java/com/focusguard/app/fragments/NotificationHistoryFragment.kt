package com.focusguard.app.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focusguard.app.MyApplication
import com.focusguard.app.R
import com.focusguard.app.adapters.NotificationAdapter
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.databinding.FragmentNotificationHistoryBinding
import com.focusguard.app.services.NotificationService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class NotificationHistoryFragment : Fragment() {
    
    private var _binding: FragmentNotificationHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val notificationRepository = MyApplication.notificationRepository
    private lateinit var notificationService: NotificationService
    private lateinit var notificationAdapter: NotificationAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        notificationService = NotificationService(requireContext())
        
        // Initialize the toolbar and its components
        initializeToolbar()
        
        // Hide the bottom navigation
        hideBottomNavigation()
        
        setupRecyclerView()
        setupClearButton()
        setupBackButton()
        setupSwipeRefresh()
        loadNotifications()
    }
    
    private fun initializeToolbar() {
        // Make sure the toolbar is visible
        binding.appBarLayout.visibility = View.VISIBLE
        binding.toolbar.visibility = View.VISIBLE
        
        // Set the toolbar title and subtitle
        binding.textViewNotificationsTitle.visibility = View.VISIBLE
        binding.textViewNotificationHistorySubtitle.visibility = View.VISIBLE
    }
    
    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            onItemClick = { notification ->
                markNotificationAsRead(notification)
                showNotificationDetailDialog(notification)
            },
            onDeleteClick = { notification ->
                showDeleteConfirmationDialog(notification)
            }
        )
        
        binding.recyclerViewNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }
    
    private fun markNotificationAsRead(notification: Notification) {
        if (!notification.wasClicked) {
            lifecycleScope.launch {
                val updatedNotification = notification.copy(wasClicked = true)
                notificationRepository.updateNotification(updatedNotification)
                // Refresh data after marking as read
                loadNotifications()
            }
        }
    }
    
    private fun showDeleteConfirmationDialog(notification: Notification) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Notification")
            .setMessage("Are you sure you want to delete this notification?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { _, _ ->
                deleteNotification(notification)
            }
            .show()
    }
    
    private fun deleteNotification(notification: Notification) {
        lifecycleScope.launch {
            // Delete from database
            notificationRepository.deleteNotification(notification)
            
            // If this notification has a pending system notification, cancel it
            notification.id.let { notificationId ->
                notificationService.cancelNotification(notificationId.toInt())
            }
            
            // Refresh the UI
            loadNotifications()
            
            // Show confirmation
            Snackbar.make(
                binding.root,
                "Notification deleted",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showNotificationDetailDialog(notification: Notification) {
        // Inflate the detail dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_notification_detail, null
        )
        
        // Find views in the dialog layout
        val typeView = dialogView.findViewById<TextView>(R.id.textViewDialogNotificationType)
        val timeView = dialogView.findViewById<TextView>(R.id.textViewDialogNotificationTime)
        val titleView = dialogView.findViewById<TextView>(R.id.textViewDialogNotificationTitle)
        val contentView = dialogView.findViewById<TextView>(R.id.textViewDialogNotificationContent)
        val closeButton = dialogView.findViewById<Button>(R.id.buttonDialogClose)
        
        // Set notification data to views
        typeView.text = getTypeDisplayName(notification.type)
        
        // Format time
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        timeView.text = notification.createdAt.format(formatter)
        
        // Set title and content
        titleView.text = notification.title
        contentView.text = notification.content
        
        // Create and show the dialog
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Set close button click listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun getTypeDisplayName(type: com.focusguard.app.data.entity.NotificationType): String {
        return when (type) {
            com.focusguard.app.data.entity.NotificationType.HABIT_REMINDER -> "HABIT"
            com.focusguard.app.data.entity.NotificationType.MOTIVATION -> "MOTIVATION"
            com.focusguard.app.data.entity.NotificationType.INSIGHT -> "INSIGHT"
            com.focusguard.app.data.entity.NotificationType.RELIGIOUS_QUOTE -> "QUOTE"
            com.focusguard.app.data.entity.NotificationType.APP_USAGE_WARNING -> "WARNING"
            com.focusguard.app.data.entity.NotificationType.GENERAL -> "GENERAL"
        }
    }
    
    private fun setupClearButton() {
        binding.buttonClearNotifications.setOnClickListener {
            // Show confirmation dialog
            showClearConfirmationDialog()
        }
    }
    
    private fun setupBackButton() {
        binding.buttonBack.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            // Update notifications
            loadNotifications()
            
            // Hide refresh indicator after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1000)
        }
    }
    
    private fun loadNotifications() {
        lifecycleScope.launch {
            notificationRepository.getAllNotifications().collectLatest { allNotifications ->
                // Update UI with notifications
                notificationAdapter.submitList(allNotifications)
                
                // Update empty state visibility
                updateEmptyState(allNotifications.isEmpty())
                
                // Update notification counts
                updateNotificationCounts(allNotifications)
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerViewNotifications.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerViewNotifications.visibility = View.VISIBLE
        }
    }
    
    private fun updateNotificationCounts(notifications: List<Notification>) {
        // Calculate counts
        val totalCount = notifications.size
        val unreadCount = notifications.count { !it.wasClicked }
        
        // Today's count - notifications from today
        val today = java.time.LocalDate.now()
        val todayCount = notifications.count { 
            it.createdAt.toLocalDate() == today 
        }
        
        // Update UI
        binding.textViewTotalCount.text = "Total: $totalCount"
        binding.textViewUnreadCount.text = "Unread: $unreadCount"
        binding.textViewTodayCount.text = "Today: $todayCount"
    }
    
    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Notifications")
            .setMessage("Are you sure you want to clear all notifications? This action cannot be undone.")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Clear All") { _, _ ->
                clearAllNotifications()
            }
            .show()
    }
    
    private fun clearAllNotifications() {
        lifecycleScope.launch {
            // Permanently delete from database
            notificationRepository.deleteAllNotifications()
            
            // Cancel all system notifications
            notificationService.cancelAllNotifications()
            
            // Load notifications to update UI
            loadNotifications()
            
            // Show confirmation
            Snackbar.make(
                binding.root,
                "All notifications cleared",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onDestroyView() {
        // Show the bottom navigation again when fragment is destroyed
        showBottomNavigation()
        
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Hide the bottom navigation bar
     */
    private fun hideBottomNavigation() {
        activity?.findViewById<View>(R.id.bottom_navigation)?.let { bottomNav ->
            bottomNav.visibility = View.GONE
        }
    }
    
    /**
     * Show the bottom navigation bar
     */
    private fun showBottomNavigation() {
        activity?.findViewById<View>(R.id.bottom_navigation)?.let { bottomNav ->
            bottomNav.visibility = View.VISIBLE
        }
    }
} 