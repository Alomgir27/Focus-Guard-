package com.focusguard.app.ui.usagestats

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.focusguard.app.R
import com.focusguard.app.databinding.FragmentUsageStatsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class UsageStatsFragment : Fragment(), UsageStatsAdapter.OnItemClickListener {

    private var _binding: FragmentUsageStatsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: UsageStatsViewModel
    private lateinit var adapter: UsageStatsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsageStatsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupAdapter()
        setupTabLayout()
        setupChartAppearance()
        checkUsageStatsPermission()
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[UsageStatsViewModel::class.java]
        
        // Observe usage stats list changes
        viewModel.usageStatsList.observe(viewLifecycleOwner) { usageStats ->
            adapter.submitList(usageStats)
            updatePieChart(usageStats)
            
            // Calculate and display total app opens
            val totalOpens = usageStats.sumOf { it.launchCount }
            binding.totalOpensCount.text = totalOpens.toString()
        }
        
        // Observe total usage time
        viewModel.totalUsageTime.observe(viewLifecycleOwner) { totalTime ->
            val formattedTime = viewModel.formatUsageTime(totalTime)
            binding.totalUsageTime.text = formattedTime
            adapter.setTotalUsageTime(totalTime)
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.cardTotalUsage.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.cardAppList.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                binding.errorMessage.text = errorMessage
                binding.errorMessage.visibility = View.VISIBLE
            } else {
                binding.errorMessage.visibility = View.GONE
            }
        }
    }
    
    private fun setupAdapter() {
        adapter = UsageStatsAdapter()
        adapter.setOnItemClickListener(this)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@UsageStatsFragment.adapter
        }
    }
    
    override fun onItemClick(appInfo: UsageStatsViewModel.AppUsageInfo) {
        showAppDetailDialog(appInfo)
    }
    
    private fun showAppDetailDialog(appInfo: UsageStatsViewModel.AppUsageInfo) {
        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(appInfo.appName)
            .setIcon(appInfo.appIcon)
            
        // Create the detail view
        val formattedTime = viewModel.formatUsageTime(appInfo.timeInForeground)
        val lastTimeUsed = if (appInfo.lastTimeUsed > 0) {
            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(appInfo.lastTimeUsed))
        } else {
            "Not available"
        }
        
        val totalLaunches = appInfo.launchCount
        
        val messageBuilder = StringBuilder()
        messageBuilder.append("Package: ${appInfo.packageName}\n\n")
        messageBuilder.append("Total usage: $formattedTime\n\n")
        messageBuilder.append("Last used: $lastTimeUsed\n\n")
        messageBuilder.append("Total launches: $totalLaunches")
        
        // If we have daily breakdown, add it
        if (appInfo.dailyUsage.isNotEmpty()) {
            messageBuilder.append("\n\nDaily Usage:\n")
            appInfo.dailyUsage.forEach { (date, time) ->
                val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                val timeStr = viewModel.formatUsageTime(time)
                messageBuilder.append("$dateStr: $timeStr\n")
            }
        }
        
        dialogBuilder.setMessage(messageBuilder.toString())
        
        // Add buttons
        dialogBuilder.setPositiveButton("Close", null)
        
        // Show dialog
        dialogBuilder.create().show()
    }
    
    private fun setupTabLayout() {
        // Set tab selection listener
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val timeRange = when (tab.position) {
                    0 -> UsageStatsViewModel.TimeRange.DAY
                    1 -> UsageStatsViewModel.TimeRange.WEEK
                    2 -> UsageStatsViewModel.TimeRange.MONTH
                    else -> UsageStatsViewModel.TimeRange.DAY
                }
                
                // Load usage stats for selected time range
                loadUsageStats(timeRange)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Default to DAY view
        binding.tabLayout.getTabAt(0)?.select()
    }
    
    private fun setupChartAppearance() {
        binding.pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            centerText = "App Usage"
            setCenterTextColor(Color.WHITE)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            animateY(1400, Easing.EaseInOutQuad)
            legend.isEnabled = true
            legend.textColor = Color.WHITE
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
        }
    }
    
    private fun updatePieChart(stats: List<UsageStatsViewModel.AppUsageInfo>) {
        // If there's no data, don't try to update the chart
        if (stats.isEmpty()) {
            binding.pieChart.setNoDataText("No usage data available")
            binding.pieChart.setNoDataTextColor(Color.WHITE)
            binding.pieChart.invalidate()
            return
        }

        // Get the top 5 apps (or fewer if there are less than 5)
        val topApps = stats.take(5)
        
        // Calculate total time for percentage calculation
        val topAppsTime = topApps.sumOf { it.timeInForeground }
        
        // Create pie chart entries
        val entries = ArrayList<PieEntry>()
        for (app in topApps) {
            // Only add if the app has some usage time
            if (app.timeInForeground > 0) {
                val percentage = (app.timeInForeground.toFloat() / topAppsTime.toFloat()) * 100f
                entries.add(PieEntry(percentage, app.appName))
            }
        }
        
        // Create dataset
        val dataSet = PieDataSet(entries, "App Usage")
        dataSet.apply {
            sliceSpace = 3f
            selectionShift = 5f
            setColors(*ColorTemplate.MATERIAL_COLORS)
        }
        
        // Create pie data
        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(binding.pieChart))
            setValueTextSize(11f)
            setValueTextColor(Color.WHITE)
        }
        
        // Update chart
        binding.pieChart.apply {
            data = pieData
            highlightValues(null)
            invalidate()
        }
    }
    
    private fun loadUsageStats(timeRange: UsageStatsViewModel.TimeRange) {
        if (hasUsageStatsPermission()) {
            binding.errorMessage.visibility = View.GONE
            viewModel.loadUsageStatistics(timeRange)
        } else {
            binding.errorMessage.text = "Usage stats permission required"
            binding.errorMessage.visibility = View.VISIBLE
        }
    }
    
    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Permission Required")
                .setMessage("This feature requires usage statistics permission.")
                .setPositiveButton("Grant") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Load usage stats for the default tab (Today)
            loadUsageStats(UsageStatsViewModel.TimeRange.DAY)
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 