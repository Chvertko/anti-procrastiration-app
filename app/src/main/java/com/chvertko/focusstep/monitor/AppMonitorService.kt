package com.chvertko.focusstep.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.chvertko.focusstep.AppPrefs
import com.chvertko.focusstep.Telemetry
import com.chvertko.focusstep.ui.GateActivity

class AppMonitorService : AccessibilityService() {
    private var lastPackage = ""
    private var lastTriggerAt = 0L
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            AppPrefs.recordMonitorHeartbeat(this@AppMonitorService)
            heartbeatHandler.postDelayed(this, 20_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        heartbeatHandler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

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
        Telemetry.event(this, "monitored_app_open_attempt")

        try {
            startActivity(Intent(this, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(GateActivity.EXTRA_PACKAGE, packageName)
            })
        } catch (error: RuntimeException) {
            Telemetry.event(this, "gate_launch", mapOf("result" to "failure"))
            Telemetry.error(this, "gate_launch", error)
            // Android may deny background activity launches on some device/version combinations.
            // Keep the accessibility service alive; the feasibility limitation is documented in the app.
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        heartbeatHandler.removeCallbacks(heartbeat)
        AppPrefs.clearMonitorHeartbeat(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeat)
        AppPrefs.clearMonitorHeartbeat(this)
        super.onDestroy()
    }
}
