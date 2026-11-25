package com.mission.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Process
import android.os.StrictMode
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

class MissionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Boot.ensure(this)
        installCrashLogger()
        Backdrop.ctx = applicationContext
        Thread { try { Pyq.load(applicationContext) } catch (e: Exception) { android.util.Log.e("pyq", "load", e) } }.start()
        registerNavBarCallbacks()
        MissionSyncWorker.schedule(this)
        NotificationDigestWorker.setEnabled(this, FeatureRegistry.enabled("x20_notification_digest"))
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    /** Every Mission screen: fully transparent navigation bar; if "Hide bottom navigation bar" is on, it hides again 5 seconds after it appears. */
    private fun registerNavBarCallbacks() {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private fun style(a: android.app.Activity) {
                try {
                    a.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    a.window.isNavigationBarContrastEnforced = false
                    if (!Store.b("hide_nav", true)) return
                    val ic = a.window.insetsController ?: return
                    ic.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    a.window.decorView.setOnApplyWindowInsetsListener { v, ins ->
                        if (ins.isVisible(android.view.WindowInsets.Type.navigationBars())) {
                            h.removeCallbacksAndMessages(a)
                            h.postAtTime({ try { a.window.insetsController?.hide(android.view.WindowInsets.Type.navigationBars()) } catch (e: Throwable) { } }, a, android.os.SystemClock.uptimeMillis() + 5000L)
                        }
                        v.onApplyWindowInsets(ins)
                    }
                    ic.hide(android.view.WindowInsets.Type.navigationBars())
                } catch (e: Throwable) {
                    android.util.Log.e("MissionLauncher", "nav style", e)
                }
            }

            override fun onActivityResumed(a: android.app.Activity) = style(a)

            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}

            override fun onActivityStarted(a: android.app.Activity) {}

            override fun onActivityPaused(a: android.app.Activity) {
                h.removeCallbacksAndMessages(a)
            }

            override fun onActivityStopped(a: android.app.Activity) {}

            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}

            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Apps.iconCacheClear?.invoke()
            try {
                MissionWallpaperService.releaseCachedFrame()
                Backdrop.clear()
            } catch (e: Exception) { Log.e("MissionLauncher", "failed", e) }
        }
        try {
            val prefs = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
            prefs.edit().putLong("last_trim", System.currentTimeMillis()).putInt("last_trim_level", level).apply()
        } catch (e: Exception) { Log.e("MissionLauncher", "failed", e) }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(filesDir, "crash.log").writeText(
                    "Thread: ${thread.name}\n$trace",
                    StandardCharsets.UTF_8,
                )
                val prefs = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val first = prefs.getLong("crash_window_first", 0L)
                val window = if (first <= 0L || now - first > 5 * 60 * 1000L) now else first
                val count = if (window == now) 1 else prefs.getInt("crash_window_count", 0) + 1
                prefs.edit().putLong("crash_window_first", window).putInt("crash_window_count", count)
                    .putBoolean("safe_mode", count >= 3).apply()
            } catch (loggingError: Throwable) {
                Log.e("MissionLauncher", "Unable to write the local crash log", loggingError)
            } finally {
                // A crash in a background worker thread is logged and survived; only the main thread (or out-of-memory) ends the process.
                val survivable = thread.name != "main" && error !is OutOfMemoryError
                if (survivable) {
                    android.util.Log.e("MissionLauncher", "Background thread ${thread.name} failed, app kept alive", error)
                } else if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }
}
