package com.chvertko.focusstep

import android.content.Context
import java.time.LocalDate

object AppPrefs {
    private const val FILE = "focusstep"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selectedApps(context: Context): Set<String> = prefs(context).getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()
    fun setSelectedApps(context: Context, values: Set<String>) { prefs(context).edit().putStringSet("selected_apps", values.toSet()).apply() }
    fun selectedCategories(context: Context): Set<String> = prefs(context).getStringSet("selected_categories", setOf("javascript", "typescript", "web"))?.toSet() ?: emptySet()
    fun setSelectedCategories(context: Context, values: Set<String>) { prefs(context).edit().putStringSet("selected_categories", values.toSet()).apply() }
    fun waitMinutes(context: Context): Int = prefs(context).getInt("wait_minutes", 3).coerceIn(2, 5)
    fun setWaitMinutes(context: Context, value: Int) { prefs(context).edit().putInt("wait_minutes", value.coerceIn(2, 5)).apply() }
    fun remoteUrl(context: Context): String = prefs(context).getString("remote_url", "") ?: ""
    fun setRemoteUrl(context: Context, value: String) { prefs(context).edit().putString("remote_url", value.trim()).apply() }

    fun xiaomiWindowSetupConfirmed(context: Context): Boolean = prefs(context).getBoolean("xiaomi_window_setup_confirmed", false)
    fun setXiaomiWindowSetupConfirmed(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("xiaomi_window_setup_confirmed", value).apply()
    }
    fun xiaomiBackgroundSetupConfirmed(context: Context): Boolean = prefs(context).getBoolean("xiaomi_background_setup_confirmed", false)
    fun setXiaomiBackgroundSetupConfirmed(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("xiaomi_background_setup_confirmed", value).apply()
    }

    fun recordMonitorHeartbeat(context: Context) {
        prefs(context).edit().putLong("monitor_heartbeat_ms", System.currentTimeMillis()).apply()
    }

    fun clearMonitorHeartbeat(context: Context) {
        prefs(context).edit().putLong("monitor_heartbeat_ms", 0L).apply()
    }

    fun isMonitorHeartbeatFresh(context: Context, maxAgeMillis: Long = 90_000L): Boolean {
        val heartbeat = prefs(context).getLong("monitor_heartbeat_ms", 0L)
        val age = System.currentTimeMillis() - heartbeat
        return heartbeat > 0L && age in 0..maxAgeMillis
    }

    fun grantAccess(context: Context, packageName: String, minutes: Int = 10) {
        prefs(context).edit().putLong("allow_until_$packageName", System.currentTimeMillis() + minutes * 60_000L).apply()
    }
    fun isAllowed(context: Context, packageName: String): Boolean = prefs(context).getLong("allow_until_$packageName", 0) > System.currentTimeMillis()

    fun recordAttempt(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        if (p.getString("stat_day", "") != today) {
            p.edit().putString("stat_day", today).putInt("stat_attempts", 0).putInt("stat_completed", 0).putInt("stat_waits", 0).putInt("stat_seconds", 0).apply()
        }
        p.edit().putInt("stat_attempts", p.getInt("stat_attempts", 0) + 1).apply()
    }

    fun recordOutcome(context: Context, completed: Boolean, savedSeconds: Int) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        if (p.getString("stat_day", "") != today) {
            p.edit().putString("stat_day", today).putInt("stat_attempts", 0).putInt("stat_completed", 0).putInt("stat_waits", 0).putInt("stat_seconds", 0).apply()
        }
        p.edit()
            .putInt("stat_completed", p.getInt("stat_completed", 0) + if (completed) 1 else 0)
            .putInt("stat_waits", p.getInt("stat_waits", 0) + if (completed) 0 else 1)
            .putInt("stat_saved_seconds", p.getInt("stat_saved_seconds", 0) + savedSeconds.coerceAtLeast(0))
            .apply()
    }

    fun dailyStats(context: Context): DailyStats {
        val p = prefs(context)
        if (p.getString("stat_day", "") != LocalDate.now().toString()) return DailyStats(0, 0, 0, 0)
        return DailyStats(p.getInt("stat_attempts", 0), p.getInt("stat_completed", 0), p.getInt("stat_waits", 0), p.getInt("stat_saved_seconds", 0))
    }
}

data class DailyStats(val attempts: Int, val completed: Int, val waits: Int, val savedSeconds: Int)
