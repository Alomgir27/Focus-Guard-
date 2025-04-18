package com.focusguard.app.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Base activity that applies safety measures to prevent NullPointerExceptions
 * with switch components
 */
open class BaseActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BaseActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install our safety factory before any views are created
        installSafetyFactory()
        super.onCreate(savedInstanceState)
    }

    /**
     * Install a LayoutInflater factory that ensures switch texts are never null
     */
    private fun installSafetyFactory() {
        try {
            val delegate = delegate
            if (delegate is AppCompatDelegate) {
                val layoutInflater = LayoutInflater.from(this)
                
                // Get the existing factory if available
                val existingFactory = layoutInflater.factory2
                
                if (existingFactory != null) {
                    // Wrap the existing factory with our safety measures
                    val safeFactory = UISafetyFactory(existingFactory)
                    LayoutInflater.from(this).factory2 = safeFactory
                    Log.d(TAG, "Safety factory installed on existing factory")
                } else {
                    // If no factory exists, we'll use our own and delegate to AppCompat
                    val safeFactory = object : LayoutInflater.Factory2 {
                        override fun onCreateView(
                            parent: View?,
                            name: String,
                            context: Context,
                            attrs: AttributeSet
                        ): View? {
                            // Let AppCompat handle view creation
                            val view = delegate.createView(parent, name, context, attrs)
                            
                            // Apply our safety measures
                            if (view is SwitchCompat || view is MaterialSwitch) {
                                ensureSwitchHasText(view)
                            }
                            
                            return view
                        }
                        
                        override fun onCreateView(
                            name: String,
                            context: Context,
                            attrs: AttributeSet
                        ): View? {
                            return onCreateView(null, name, context, attrs)
                        }
                    }
                    
                    // Install our factory
                    LayoutInflater.from(this).factory2 = safeFactory
                    Log.d(TAG, "Safety factory installed as new factory")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing safety factory: ${e.message}", e)
        }
    }
    
    /**
     * Ensure a switch component has text
     */
    private fun ensureSwitchHasText(view: View) {
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