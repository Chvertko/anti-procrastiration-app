package com.chvertko.focusstep

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import com.chvertko.focusstep.data.PinCode

data class ProtectionReadiness(
    val appsSelected: Boolean,
    val pinSet: Boolean,
    val accessibilityEnabled: Boolean,
    val serviceResponsive: Boolean,
    val notificationsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val autostartConfirmed: Boolean,
    val recentsLockConfirmed: Boolean,
    val isXiaomi: Boolean,
) {
    val isReady: Boolean
        get() = appsSelected && pinSet && accessibilityEnabled && serviceResponsive && notificationsAllowed && batteryUnrestricted &&
            (!isXiaomi || (autostartConfirmed && recentsLockConfirmed))

    val missing: List<String>
        get() = buildList {
            if (!appsSelected) add("Выбери хотя бы одно приложение")
            if (!pinSet) add("Задай PIN")
            if (!accessibilityEnabled) add("Включи службу FocusStep в специальных возможностях")
            else if (!serviceResponsive) add("Служба сейчас не отвечает")
            if (!notificationsAllowed) add("Разреши уведомления FocusStep")
            if (!batteryUnrestricted) add("Разреши работу без ограничений батареи")
            if (isXiaomi && !autostartConfirmed) add("Разреши фоновый автозапуск Xiaomi")
            if (isXiaomi && !recentsLockConfirmed) add("Закрепи FocusStep в списке последних приложений")
        }
}

object ProtectionReadinessChecker {
    fun check(context: Context): ProtectionReadiness {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(context, com.chvertko.focusstep.monitor.AppMonitorService::class.java)
        val accessibilityEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == component.packageName && it.resolveInfo.serviceInfo.name == component.className }
        val notificationsAllowed =
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
                context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel("focusstep_protection")?.importance != NotificationManager.IMPORTANCE_NONE)
        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } else true
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val xiaomi = listOf("xiaomi", "redmi", "poco").any { it in manufacturer || it in brand }
        return ProtectionReadiness(
            appsSelected = AppPrefs.selectedApps(context).isNotEmpty(),
            pinSet = PinCode.isSet(context),
            accessibilityEnabled = accessibilityEnabled,
            serviceResponsive = AppPrefs.isMonitorHeartbeatFresh(context),
            notificationsAllowed = notificationsAllowed,
            batteryUnrestricted = batteryUnrestricted,
            autostartConfirmed = AppPrefs.backgroundAutostartConfirmed(context),
            recentsLockConfirmed = AppPrefs.recentsLockConfirmed(context),
            isXiaomi = xiaomi,
        )
    }
}
