package com.chvertko.focusstep

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Sends coarse product events only after the user opts in. */
object Telemetry {
    private const val PREFS = "focusstep"
    private const val CONSENT_KEY = "telemetry_enabled"

    fun isEnabled(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CONSENT_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(CONSENT_KEY, enabled).apply()
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        runCatching {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }
    }

    fun applyConsent(context: Context) {
        if (BuildConfig.FIREBASE_CONFIGURED) setEnabled(context, isEnabled(context))
    }

    fun event(context: Context, name: String, params: Map<String, String> = emptyMap()) {
        if (!isEnabled(context)) return
        runCatching {
            val bundle = Bundle().apply { params.forEach { (key, value) -> putString(key, value) } }
            FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
        }
    }

    /** Avoid sending exception messages that might contain URLs or other user-provided text. */
    fun error(context: Context, operation: String, error: Throwable) {
        if (!isEnabled(context)) return
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("operation", operation)
                recordException(Exception(error::class.java.simpleName))
            }
        }
    }
}
