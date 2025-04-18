package com.focusguard.app.ui.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.adapters.AppListAdapter
import com.focusguard.app.models.AppInfo
import com.focusguard.app.viewmodels.AppsViewModel
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class AppsFragment : Fragment() {

    private lateinit var viewModel: AppsViewModel
    private lateinit var adapter: AppListAdapter
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var emptyView: TextView
    private lateinit var retryButton: View
    
    private val TAG = "AppsFragment"
    
    // Broadcast receiver for UI updates
    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.focusguard.app.ACTION_UPDATE_APP_UI" -> {
                    val packageName = intent.getStringExtra("package_name") ?: return
                    val isBlocked = intent.getBooleanExtra("is_blocked", false)
                    Log.d(TAG, "Received UI update broadcast: package=$packageName, blocked=$isBlocked")
                    viewModel.updateBlockedAppStatus(packageName, isBlocked)
                }
                "com.focusguard.app.ACTION_REFRESH_BLOCKED_APPS",
                "com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS" -> {
                    Log.d(TAG, "Received refresh broadcast, reloading apps")
                    viewModel.loadApps()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        recyclerView = view.findViewById(R.id.apps_recycler_view)
        searchEditText = view.findViewById(R.id.search_edit_text)
        progressIndicator = view.findViewById(R.id.progress_indicator)
        emptyView = view.findViewById(R.id.empty_view)
        retryButton = view.findViewById(R.id.retry_button) ?: View(requireContext()).also { 
            Log.w(TAG, "Retry button not found in layout, functionality will be limited")
        }
        
        // Set retry button click listener
        retryButton.setOnClickListener {
            Log.d(TAG, "Retry button clicked, reloading apps")
            viewModel.loadApps()
        }
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = AppListAdapter(
            onAppBlocked = { app, isBlocked -> onAppBlockToggled(app, isBlocked) },
            onAppConfigureBlock = { app -> navigateToAppBlockSetup(app) }
        )
        recyclerView.adapter = adapter
        
        // Set up search functionality with debouncing
        var searchTimer: Timer? = null
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Cancel previous timer
                searchTimer?.cancel()
                
                // Create new timer with delay
                searchTimer = Timer()
                searchTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        // Run on UI thread since we're updating UI
                        activity?.runOnUiThread {
                            viewModel.filterApps(s.toString().trim())
                        }
                    }
                }, 300) // 300ms delay before applying filter
            }
        })
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AppsViewModel::class.java]
        
        // Observe data
        viewModel.filteredApps.observe(viewLifecycleOwner) { apps ->
            Log.d(TAG, "Received ${apps.size} apps from viewModel")
            // Debug log each app
            apps.forEach { app ->
                Log.d(TAG, "App in filtered list: ${app.appName} (${app.packageName})")
            }
            adapter.submitList(apps)
            updateEmptyView(apps.isEmpty())
            
            // Hide retry button when we have apps
            retryButton.visibility = if (apps.isEmpty() && !progressIndicator.isVisible) View.VISIBLE else View.GONE
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            progressIndicator.isVisible = isLoading
            
            // Always update empty view when loading state changes
            updateEmptyView(viewModel.filteredApps.value?.isEmpty() ?: true)
            
            // Hide retry button during loading
            retryButton.visibility = if (!isLoading && (viewModel.filteredApps.value?.isEmpty() ?: true)) View.VISIBLE else View.GONE
        }
        
        // Force load apps
        Log.d(TAG, "Initial load of apps on fragment creation")
        viewModel.loadApps()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Register the UI update receiver with the proper flags for Android 13+
        val intentFilter = IntentFilter().apply {
            addAction("com.focusguard.app.ACTION_UPDATE_APP_UI")
            addAction("com.focusguard.app.ACTION_REFRESH_BLOCKED_APPS")
            addAction("com.focusguard.app.ACTION_RELOAD_BLOCKED_APPS")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                uiUpdateReceiver, 
                intentFilter, 
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(uiUpdateReceiver, intentFilter)
        }
        
        // Check if we need to force refresh the app list
        val prefs = requireActivity().getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
        val needsRefresh = prefs.getBoolean("needs_refresh", false)
        
        if (needsRefresh) {
            // Clear the flag
            prefs.edit().putBoolean("needs_refresh", false).apply()
            
            // Force reload apps
            viewModel.loadApps()
            
            Log.d(TAG, "Forced refresh of app list due to block settings change")
        } else {
            // Normal refresh
            viewModel.loadApps()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister the UI update receiver
        try {
            requireContext().unregisterReceiver(uiUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
    
    private fun onAppBlockToggled(app: AppInfo, isBlocked: Boolean) {
        Log.d(TAG, "App toggle changed for ${app.appName}: isBlocked=$isBlocked")
        if (isBlocked) {
            Log.d(TAG, "Blocking app: ${app.packageName}")
            viewModel.blockApp(app)
        } else {
            Log.d(TAG, "Unblocking app: ${app.packageName}")
            viewModel.unblockApp(app)
        }
    }
    
    /**
     * Navigate to the app block setup screen
     */
    private fun navigateToAppBlockSetup(app: AppInfo) {
        Log.d(TAG, "Navigating to block setup for ${app.appName}")
        
        // Create bundle with app info
        val bundle = bundleOf(
            "packageName" to app.packageName,
            "appName" to app.appName
        )
        
        // Navigate to setup fragment
        findNavController().navigate(
            R.id.action_appsFragment_to_appBlockSetupFragment,
            bundle
        )
    }
    
    private fun updateEmptyView(isEmpty: Boolean) {
        val isLoading = progressIndicator.isVisible
        Log.d(TAG, "Updating empty view. isEmpty=$isEmpty, isLoading=$isLoading")
        
        emptyView.isVisible = isEmpty && !isLoading
        recyclerView.isVisible = !isEmpty
        
        // Show helpful message if no apps found
        if (isEmpty && !isLoading) {
            emptyView.text = "No apps found. Ensure the app has permission to view installed apps."
        }
    }
} 