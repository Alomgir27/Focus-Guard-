package com.focusguard.app.utils

import android.widget.CompoundButton
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Utility class for UI-related helpers
 */
object UIUtils {
    
    /**
     * Ensures that a switch-type component always has text to prevent NullPointerException
     * during layout measurement
     * 
     * @param switch The switch component to ensure has text
     * @param defaultText The default text to set if current text is null
     */
    fun ensureSwitchText(switch: CompoundButton, defaultText: String = "") {
        // Make sure to handle both null and empty strings
        if (switch.text == null || switch.text.toString().isEmpty()) {
            switch.text = defaultText
        }
    }
    
    /**
     * Ensures that a MaterialButton component always has text to prevent layout measurement issues
     * 
     * @param button The button component to ensure has text
     * @param defaultText The default text to set if current text is null
     */
    fun ensureButtonText(button: MaterialButton, defaultText: String = "") {
        // Make sure to handle both null and empty strings
        if (button.text == null || button.text.toString().isEmpty()) {
            button.text = defaultText
        }
    }
    
    /**
     * Extension function to ensure a CompoundButton has non-null text
     */
    fun CompoundButton.ensureText(defaultText: String = "") {
        if (this.text == null || this.text.toString().isEmpty()) {
            this.text = defaultText
        }
    }
    
    /**
     * Extension function to ensure a MaterialButton has non-null text
     */
    fun MaterialButton.ensureText(defaultText: String = "") {
        if (this.text == null || this.text.toString().isEmpty()) {
            this.text = defaultText
        }
    }
} 