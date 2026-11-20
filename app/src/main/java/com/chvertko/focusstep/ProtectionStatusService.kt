package com.chvertko.focusstep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class ProtectionStatusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH) updateNotification()
        }
    }
    private val refreshTask = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_REFRESH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") registerReceiver(refreshReceiver, filter)
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(refreshTask)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        updateNotification()
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, UPDATE_INTERVAL_MS)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        updateNotification()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        runCatching { unregisterReceiver(refreshReceiver) }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Статус защиты", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun updateNotification() {
        val readiness = ProtectionReadinessChecker.check(this)
        val foregroundPackage = AppPrefs.currentForegroundPackage(this)
        val grantPackage = foregroundPackage?.takeIf { AppPrefs.isAllowed(this, it) }
        val grantUntil = grantPackage?.let { AppPrefs.allowedUntil(this, it) } ?: 0L
        val grantLabel = grantPackage?.let { packageName ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
                .getOrDefault("приложение")
        }
        val title = when {
            !readiness.isReady -> "FocusStep · требуется настройка"
            grantUntil > System.currentTimeMillis() -> "FocusStep · временный доступ"
            else -> "FocusStep · защита активна"
        }
        val content = when {
            !readiness.isReady -> readiness.missing.firstOrNull() ?: "Проверь настройки защиты"
            grantUntil > System.currentTimeMillis() -> "$grantLabel · доступ открыт до конца отсчёта"
            else -> "Работает в фоне · приложений: ${AppPrefs.selectedApps(this).size}"
        }
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (grantUntil > System.currentTimeMillis()) {
            builder.setWhen(grantUntil).setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
        }
        val notification = builder.build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            Log.e(TAG, "Could not update persistent protection status (${error.javaClass.simpleName})")
        }
    }

    companion object {
        const val CHANNEL_ID = "focusstep_protection"
        const val ACTION_REFRESH = "com.chvertko.focusstep.action.REFRESH_STATUS"
        private const val ACTION_STOP = "com.chvertko.focusstep.action.STOP_STATUS_SERVICE"
        private const val NOTIFICATION_ID = 4107
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val TAG = "FocusStepStatus"

        fun ensureRunning(context: Context) {
            if (!AppPrefs.protectionRequested(context)) return
            val intent = Intent(context, ProtectionStatusService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure { Log.e(TAG, "Could not start persistent status service (${it.javaClass.simpleName})") }
        }

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProtectionStatusService::class.java)) }
        }
    }
}

class ProtectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (AppPrefs.protectionRequested(context) && AppPrefs.selectedApps(context).isNotEmpty()) {
            ProtectionStatusService.ensureRunning(context)
        }
    }
}
