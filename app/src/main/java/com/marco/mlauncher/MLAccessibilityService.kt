package com.marco.mlauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MLAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun lock(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    companion object {
        var instance: MLAccessibilityService? = null
    }
}
