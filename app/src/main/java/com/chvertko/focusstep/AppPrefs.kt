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

    fun recordOutcome(context: Context, completed: Boolean, seconds: Int) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        if (p.getString("stat_day", "") != today) {
            p.edit().putString("stat_day", today).putInt("stat_attempts", 0).putInt("stat_completed", 0).putInt("stat_waits", 0).putInt("stat_seconds", 0).apply()
        }
        p.edit()
            .putInt("stat_completed", p.getInt("stat_completed", 0) + if (completed) 1 else 0)
            .putInt("stat_waits", p.getInt("stat_waits", 0) + if (completed) 0 else 1)
            .putInt("stat_seconds", p.getInt("stat_seconds", 0) + seconds)
            .apply()
    }

    fun dailyStats(context: Context): DailyStats {
        val p = prefs(context)
        if (p.getString("stat_day", "") != LocalDate.now().toString()) return DailyStats(0, 0, 0, 0)
        return DailyStats(p.getInt("stat_attempts", 0), p.getInt("stat_completed", 0), p.getInt("stat_waits", 0), p.getInt("stat_seconds", 0))
    }
}

data class DailyStats(val attempts: Int, val completed: Int, val waits: Int, val seconds: Int)
