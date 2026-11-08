package com.chvertko.focusstep.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.chvertko.focusstep.AppPrefs
import com.chvertko.focusstep.ui.GateActivity

class AppMonitorService : AccessibilityService() {
    private var lastPackage = ""
    private var lastTriggerAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || packageName !in AppPrefs.selectedApps(this)) return
        if (AppPrefs.isAllowed(this, packageName)) return

        val now = System.currentTimeMillis()
        if (packageName == lastPackage && now - lastTriggerAt < 1_500) return
        lastPackage = packageName
        lastTriggerAt = now
        AppPrefs.recordAttempt(this)

        try {
            startActivity(Intent(this, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(GateActivity.EXTRA_PACKAGE, packageName)
            })
        } catch (_: RuntimeException) {
            // Android may deny background activity launches on some device/version combinations.
            // Keep the accessibility service alive; the feasibility limitation is documented in the app.
        }
    }

    override fun onInterrupt() = Unit
}
