package com.mission.launcher

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * "Restart + clean": password protected (same password as Reset). Deletes cache files, frees image/wallpaper memory,
 * stops idle background apps and floating helpers that are not needed, then restarts Mission fresh.
 * Never deletes your data (targets, notes, ticks, settings) and never stops a running study timer.
 */
object Restart {
    private fun wipe(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        var n = 0L
        if (f.isDirectory) f.listFiles()?.forEach { n += wipe(it) }
        n += f.length()
        try { f.delete() } catch (e: Exception) { Log.e("restart-wipe", e) }
        return n
    }

    /** Clears caches and idle processes. Returns a short report. */
    fun clean(c: Context): String {
        var bytes = 0L
        try {
            c.cacheDir.listFiles()?.forEach { bytes += wipe(it) }
            c.externalCacheDir?.listFiles()?.forEach { bytes += wipe(it) }
            c.codeCacheDir.listFiles()?.forEach { bytes += wipe(it) }
        } catch (e: Exception) {
            Log.e("restart-cache", e)
        }
        try {
            Apps.iconCacheClear?.invoke()
            MissionWallpaperService.releaseCachedFrame()
            Backdrop.clear()
        } catch (e: Exception) {
            Log.e("restart-mem", e)
        }
        // floating helpers that are not the running timer
        try {
            Slot.dismiss(c)
        } catch (e: Exception) {
            Log.e("restart-overlay", e)
        }
        val n = try { Cleaner.run(c, 0) } catch (e: Exception) { 0 }
        System.gc()
        return "Cache ${bytes / 1024} KB deleted, $n background apps stopped"
    }

    fun ask(a: Activity) {
        ResetGate.verify(a) {
            val timer = TimerState.active()
            Dlg.confirm(
                a,
                "Restart and clean",
                "Deletes all cache, stops idle background apps and restarts Mission fresh. Your data, targets, notes and ticks stay." +
                    (if (timer) "\n\nYour running timer keeps running (Mission will not restart, only clean)." else ""),
                "Clean now",
            ) { go(a, !timer) }
        }
    }

    fun go(a: Activity, relaunch: Boolean) {
        Thread {
            val msg = clean(a.applicationContext)
            a.runOnUiThread {
                a.toast(msg)
                if (relaunch) {
                    try {
                        val i = Intent(a, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        val pi = PendingIntent.getActivity(a, 77, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        (a.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 700, pi)
                        a.finishAffinity()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 350)
                    } catch (e: Exception) {
                        Log.e("restart-go", e)
                        a.recreate()
                    }
                } else {
                    a.recreate()
                }
            }
        }.start()
    }
}

/** Switches the tablet to full-screen gesture navigation (no bottom buttons). Needs WRITE_SECURE_SETTINGS once, otherwise guides the user. */
object NavGesture {
    fun enable(a: Activity) {
        var ok = false
        try {
            android.provider.Settings.Global.putInt(a.contentResolver, "force_fsg_nav_bar", 1)
            ok = true
        } catch (e: Exception) {
            Log.e("navgesture-global", e)
        }
        try {
            android.provider.Settings.Secure.putInt(a.contentResolver, "navigation_mode", 2)
            ok = true
        } catch (e: Exception) {
            Log.e("navgesture-secure", e)
        }
        if (ok) {
            a.toast("Full-screen gestures turned on")
            return
        }
        Dlg.builder(a).setTitle("Turn on full-screen gestures")
            .setMessage(
                "Android does not let apps change this without one-time permission.\n\nEasy way: Settings > System navigation > Gestures (Full screen gestures).\n\nOne-time automatic way: connect the tablet to a PC and run:\nadb shell pm grant com.mission.launcher android.permission.WRITE_SECURE_SETTINGS\nthen tap this button again.",
            )
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    a.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.e("navgesture-open", e)
                }
            }.setNegativeButton("Close", null).show()
    }
}
