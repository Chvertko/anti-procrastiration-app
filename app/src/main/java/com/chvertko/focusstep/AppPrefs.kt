package com.chvertko.focusstep

import android.content.Context
import android.media.AudioManager
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
    fun accessMinutes(context: Context): Int = prefs(context).getInt("access_minutes", 10).coerceIn(5, 30)
    fun setAccessMinutes(context: Context, value: Int) { prefs(context).edit().putInt("access_minutes", value.coerceIn(5, 30)).apply() }
    fun setBackgroundAutostartConfirmed(context: Context, value: Boolean) { prefs(context).edit().putBoolean("background_autostart_confirmed", value).apply() }
    fun backgroundAutostartConfirmed(context: Context): Boolean = prefs(context).getBoolean("background_autostart_confirmed", false)
    fun setRecentsLockConfirmed(context: Context, value: Boolean) { prefs(context).edit().putBoolean("recents_lock_confirmed", value).apply() }
    fun recentsLockConfirmed(context: Context): Boolean = prefs(context).getBoolean("recents_lock_confirmed", false)
    fun setCurrentForegroundPackage(context: Context, packageName: String?) {
        prefs(context).edit().putString("current_foreground_package", packageName).apply()
    }
    fun currentForegroundPackage(context: Context): String? = prefs(context).getString("current_foreground_package", null)
    fun remoteUrl(context: Context): String = prefs(context).getString("remote_url", "") ?: ""
    fun setRemoteUrl(context: Context, value: String) { prefs(context).edit().putString("remote_url", value.trim()).apply() }

    fun protectionRequested(context: Context): Boolean = prefs(context).getBoolean("protection_requested", false)
    fun setProtectionRequested(context: Context, value: Boolean) { prefs(context).edit().putBoolean("protection_requested", value).apply() }
    fun widgetStatsVisible(context: Context): Boolean = prefs(context).getBoolean("widget_stats_visible", true)
    fun setWidgetStatsVisible(context: Context, value: Boolean) { prefs(context).edit().putBoolean("widget_stats_visible", value).apply() }
    fun mutedMusicVolume(context: Context): Int = prefs(context).getInt("muted_music_volume", -1)
    fun setMutedMusicVolume(context: Context, value: Int) { prefs(context).edit().putInt("muted_music_volume", value).apply() }

    fun restoreInterruptedAudioMute(context: Context) {
        val previous = mutedMusicVolume(context)
        if (previous < 0) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val restored = previous == 0 || current != 0 || runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, previous, 0)
        }.isSuccess
        if (restored) prefs(context).edit().remove("muted_music_volume").apply()
    }

    fun clearProtectionSettings(context: Context) {
        val p = prefs(context)
        val editor = p.edit().remove("selected_apps").putBoolean("protection_requested", false).putLong("monitor_heartbeat_ms", 0L)
        p.all.keys.filter { it.startsWith("allow_until_") || it.startsWith("wait_until_") }.forEach(editor::remove)
        editor.apply()
        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
    }

    fun recordMonitorHeartbeat(context: Context) {
        prefs(context).edit().putLong("monitor_heartbeat_ms", System.currentTimeMillis()).apply()
    }

    fun clearMonitorHeartbeat(context: Context) {
        prefs(context).edit().putLong("monitor_heartbeat_ms", 0L).apply()
        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
    }

    fun isMonitorHeartbeatFresh(context: Context, maxAgeMillis: Long = 90_000L): Boolean {
        val heartbeat = prefs(context).getLong("monitor_heartbeat_ms", 0L)
        val age = System.currentTimeMillis() - heartbeat
        return heartbeat > 0L && age in 0..maxAgeMillis
    }

    fun grantAccess(context: Context, packageName: String, minutes: Int = accessMinutes(context)) {
        prefs(context).edit().putLong("allow_until_$packageName", System.currentTimeMillis() + minutes * 60_000L).apply()
    }
    fun allowedUntil(context: Context, packageName: String): Long = prefs(context).getLong("allow_until_$packageName", 0L)
    fun isAllowed(context: Context, packageName: String): Boolean = prefs(context).getLong("allow_until_$packageName", 0) > System.currentTimeMillis()

    fun recordAttempt(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        if (p.getString("stat_day", "") != today) {
            p.edit().putString("stat_day", today).putInt("stat_attempts", 0).putInt("stat_completed", 0).putInt("stat_waits", 0).putInt("stat_seconds", 0).apply()
        }
        p.edit().putInt("stat_attempts", p.getInt("stat_attempts", 0) + 1).apply()
        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
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
        com.chvertko.focusstep.widget.FocusStepWidgetProvider.updateAll(context)
    }

    fun dailyStats(context: Context): DailyStats {
        val p = prefs(context)
        if (p.getString("stat_day", "") != LocalDate.now().toString()) return DailyStats(0, 0, 0, 0)
        return DailyStats(p.getInt("stat_attempts", 0), p.getInt("stat_completed", 0), p.getInt("stat_waits", 0), p.getInt("stat_saved_seconds", 0))
    }
}

data class DailyStats(val attempts: Int, val completed: Int, val waits: Int, val savedSeconds: Int)
