package com.focusguard.app.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * A factory that ensures UI components, particularly switches, 
 * are created with proper text to prevent NullPointerExceptions
 */
class UISafetyFactory(private val delegate: LayoutInflater.Factory2) : LayoutInflater.Factory2 {
    companion object {
        private const val TAG = "UISafetyFactory"
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        try {
            // Let the delegate create the view first
            val view = delegate.onCreateView(parent, name, context, attrs)
            
            // Apply safety measures to specific view types
            if (view is SwitchCompat || view is MaterialSwitch) {
                Log.d(TAG, "Applying safety to $name")
                ensureSwitchText(view)
            }
            
            return view
        } catch (e: Exception) {
            Log.e(TAG, "Error in UISafetyFactory: ${e.message}", e)
            // Fall back to delegate
            return delegate.onCreateView(parent, name, context, attrs)
        }
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }
    
    /**
     * Ensures a switch component has non-null text
     */
    private fun ensureSwitchText(view: View) {
        try {
            when (view) {
                is SwitchCompat -> {
                    if (view.text == null) {
                        view.text = ""
                    }
                }
                is MaterialSwitch -> {
                    if (view.text == null) {
                        view.text = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring switch text: ${e.message}", e)
        }
    }
} 