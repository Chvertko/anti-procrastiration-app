package com.chvertko.focusstep.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.chvertko.focusstep.AppPrefs
import com.chvertko.focusstep.Telemetry
import com.chvertko.focusstep.ui.GateScreen

class AppMonitorService : AccessibilityService() {
    private var lastTriggerPackage = ""
    private var lastTriggerAt = 0L
    private var overlayTarget: String? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var mutedMusicVolume: Int? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var disableReceiverRegistered = false
    private val disableProtectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != ACTION_DISABLE_PROTECTION) return
            removeGateOverlay()
            AppPrefs.clearProtectionSettings(this@AppMonitorService)
            com.chvertko.focusstep.ProtectionStatusService.stop(this@AppMonitorService)
            disableSelf()
        }
    }
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val reblockHandler = Handler(Looper.getMainLooper())
    private var currentForegroundPackage: String? = null
    private var pendingReblock: Runnable? = null
    private val heartbeat = object : Runnable {
        override fun run() {
            AppPrefs.recordMonitorHeartbeat(this@AppMonitorService)
            com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(this@AppMonitorService)
            com.chvertko.focusstep.ProtectionStatusService.refresh(this@AppMonitorService)
            heartbeatHandler.postDelayed(this, 20_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppPrefs.restoreInterruptedAudioMute(this)
        Telemetry.applyConsent(this)
        AppPrefs.setProtectionRequested(this, true)
        if (!disableReceiverRegistered) {
            val filter = IntentFilter(ACTION_DISABLE_PROTECTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(disableProtectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION") registerReceiver(disableProtectionReceiver, filter)
            }
            disableReceiverRegistered = true
        }
        com.chvertko.focusstep.ProtectionStatusService.refresh(this)
        heartbeatHandler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        currentForegroundPackage = packageName
        AppPrefs.setCurrentForegroundPackage(this, packageName)
        if (packageName == this.packageName) {
            com.chvertko.focusstep.ProtectionStatusService.refresh(this)
            return
        }

        if (packageName !in AppPrefs.selectedApps(this)) {
            if (overlayTarget != null) removeGateOverlay()
            com.chvertko.focusstep.ProtectionStatusService.refresh(this)
            return
        }
        if (AppPrefs.isAllowed(this, packageName)) {
            if (overlayTarget == packageName) removeGateOverlay()
            scheduleReblock(packageName)
            com.chvertko.focusstep.ProtectionStatusService.refresh(this)
            return
        }
        com.chvertko.focusstep.ProtectionStatusService.refresh(this)
        if (overlayTarget == packageName) return

        val now = System.currentTimeMillis()
        if (packageName == lastTriggerPackage && now - lastTriggerAt < 1_500L) return
        lastTriggerPackage = packageName
        lastTriggerAt = now
        AppPrefs.recordAttempt(this)
        Telemetry.event(this, "monitored_app_open_attempt")
        showGateOverlay(packageName)
    }

    private fun showGateOverlay(targetPackage: String) {
        removeGateOverlay()
        silenceTargetAudio()
        val owner = OverlayLifecycleOwner()
        var view: ComposeView? = null
        try {
            val gateView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                installLifecycleOwner(this, owner)
                installSavedStateRegistryOwner(this, owner)
                setContent {
                    GateScreen(
                        targetPackage = targetPackage,
                        onContinue = { completed ->
                            currentForegroundPackage = targetPackage
                            AppPrefs.setCurrentForegroundPackage(this@AppMonitorService, targetPackage)
                            AppPrefs.grantAccess(this@AppMonitorService, targetPackage)
                            AppPrefs.recordOutcome(this@AppMonitorService, completed, 0)
                            scheduleReblock(targetPackage)
                            com.chvertko.focusstep.ProtectionStatusService.refresh(this@AppMonitorService)
                            removeGateOverlay()
                        },
                        onReturnHome = {
                            removeGateOverlay()
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        },
                    )
                }
            }
            view = gateView
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                0,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
            params.setTitle("FocusStep pause")
            owner.moveTo(Lifecycle.State.RESUMED)
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(gateView, params)
            overlayTarget = targetPackage
            overlayView = gateView
            overlayLifecycleOwner = owner
        } catch (error: Exception) {
            restoreTargetAudio()
            owner.moveTo(Lifecycle.State.DESTROYED)
            view?.disposeComposition()
            Log.e(TAG, "Could not show accessibility overlay (${error.javaClass.simpleName})")
            Telemetry.event(this, "gate_overlay", mapOf("result" to "failure"))
            Telemetry.error(this, "gate_overlay", error)
        }
    }

    private fun removeGateOverlay() {
        val view = overlayView
        overlayView = null
        overlayTarget = null
        overlayLifecycleOwner?.moveTo(Lifecycle.State.DESTROYED)
        overlayLifecycleOwner = null
        if (view != null) {
            runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        }
        restoreTargetAudio()
    }

    override fun onInterrupt() {
        removeGateOverlay()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeGateOverlay()
        heartbeatHandler.removeCallbacks(heartbeat)
        pendingReblock?.let(reblockHandler::removeCallbacks)
        AppPrefs.clearMonitorHeartbeat(this)
        com.chvertko.focusstep.ProtectionStatusService.refresh(this)
        if (disableReceiverRegistered) {
            runCatching { unregisterReceiver(disableProtectionReceiver) }
            disableReceiverRegistered = false
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeGateOverlay()
        heartbeatHandler.removeCallbacks(heartbeat)
        pendingReblock?.let(reblockHandler::removeCallbacks)
        AppPrefs.clearMonitorHeartbeat(this)
        com.chvertko.focusstep.ProtectionStatusService.refresh(this)
        if (disableReceiverRegistered) {
            runCatching { unregisterReceiver(disableProtectionReceiver) }
            disableReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun silenceTargetAudio() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        if (mutedMusicVolume == null) {
            mutedMusicVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
            AppPrefs.setMutedMusicVolume(this, mutedMusicVolume ?: 0)
        }
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = focus
        runCatching { manager.requestAudioFocus(focus) }
        if ((mutedMusicVolume ?: 0) > 0) runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    private fun restoreTargetAudio() {
        val manager = audioManager ?: return
        AppPrefs.restoreInterruptedAudioMute(this)
        audioFocusRequest?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
        mutedMusicVolume = null
        audioFocusRequest = null
        audioManager = null
    }

    private fun scheduleReblock(targetPackage: String) {
        pendingReblock?.let(reblockHandler::removeCallbacks)
        val deadline = AppPrefs.allowedUntil(this, targetPackage)
        if (deadline <= 0L) return
        val task = Runnable {
            pendingReblock = null
            if (currentForegroundPackage == targetPackage &&
                targetPackage in AppPrefs.selectedApps(this) &&
                !AppPrefs.isAllowed(this, targetPackage)) {
                showGateOverlay(targetPackage)
            }
            com.chvertko.focusstep.ProtectionStatusService.refresh(this@AppMonitorService)
        }
        pendingReblock = task
        reblockHandler.postDelayed(task, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    companion object {
        const val ACTION_DISABLE_PROTECTION = "com.chvertko.focusstep.action.DISABLE_PROTECTION"
        private const val TAG = "FocusStepOverlay"
    }
}

private fun installLifecycleOwner(view: View, owner: LifecycleOwner) {
    val ownerType = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
    ownerType.getMethod("set", View::class.java, LifecycleOwner::class.java).invoke(null, view, owner)
}

private fun installSavedStateRegistryOwner(view: View, owner: SavedStateRegistryOwner) {
    val ownerType = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
    ownerType.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java).invoke(null, view, owner)
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun moveTo(state: Lifecycle.State) {
        lifecycleRegistry.currentState = state
    }
}
