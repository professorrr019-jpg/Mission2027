package com.mission.launcher

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Stops background processes of apps not used for a while. Android allows this only for background (cached) processes,
 * and some apps restart themselves; it never touches the foreground app, media apps, or the keep list.
 */
object Cleaner {
    private val ALWAYS =
        setOf(
            "com.flexcil.flexcilnote", "org.telegram.messenger", "com.whatsapp", "com.google.android.apps.bard", "com.android.deskclock",
            "com.google.android.deskclock", "com.android.systemui", "com.android.phone", "com.android.mms", "com.google.android.apps.messaging",
        )

    private fun protectedPkgs(c: Context): Set<String> {
        val out = HashSet<String>(ALWAYS)
        out.add(c.packageName)
        try {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let { out.add(it) }
        } catch (e: Exception) { Log.e("cleaner", e) }
        for (i in listOf(Intent(Intent.ACTION_DIAL), Intent(Intent.ACTION_VIEW, android.net.Uri.parse("sms:")), Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))) {
            try {
                c.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName?.let { out.add(it) }
            } catch (e: Exception) { Log.e("cleaner", e) }
        }
        out.addAll(Store.stringList("clean_keep"))
        // every app that has a window on screen right now (split screen, floating windows, picture-in-picture)
        try { out.addAll(CircleService.visiblePackages()) } catch (e: Exception) { Log.e("cleaner-visible", e) }
        Slot.fgPkg.takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    /** Returns how many apps were asked to stop. */
    fun run(c: Context, idleOverride: Int? = null): Int {
        if (idleOverride == null && !Usage.granted(c)) return 0
        var n = 0
        try {
            val now = System.currentTimeMillis()
            val idle = (idleOverride ?: Store.i("clean_idle", 30).coerceIn(10, 240)) * 60000L
            val last = HashMap<String, Long>()
            if (idleOverride == null) {
                val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 3600000L, now)?.forEach {
                    if (it.lastTimeUsed > (last[it.packageName] ?: 0L)) last[it.packageName] = it.lastTimeUsed
                }
            }
            val keep = protectedPkgs(c)
            val fg = if (Usage.granted(c)) Usage.foregroundPackage(c) else null
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = c.packageManager
            val pkgs = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.distinct()
            val media = listOf("music", "spotify", "youtube", "podcast", "player", "radio", "jio", "gaana", "wynk", "audio")
            for (pkg in pkgs) {
                if (pkg in keep || pkg == fg || media.any { pkg.lowercase().contains(it) }) continue
                if (now - (last[pkg] ?: 0L) < idle) continue
                try {
                    am.killBackgroundProcesses(pkg)
                    n++
                } catch (x: Exception) {
                    Log.e("cleaner", x)
                }
            }
        } catch (e: Exception) {
            Log.e("cleaner", e)
        }
        Store.put("clean_last", System.currentTimeMillis())
        Store.put("clean_count", n)
        return n
    }

    fun schedule(c: Context) {
        if (!Store.b("clean_auto", true)) {
            Sched.cancelClean(c)
            return
        }
        val gap = 30 * 60000L
        val next = Store.l("clean_next", 0L)
        val now = System.currentTimeMillis()
        val at = if (next > now) next else now + gap
        Store.put("clean_next", at)
        Sched.clean(c, at)
    }

    fun fire(c: Context) {
        try {
            if (Store.b("clean_auto", true)) {
                val n = run(c)
                if (Store.b("clean_toast", false)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(c, "Cleared $n idle apps", android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
        } finally {
            Store.put("clean_next", System.currentTimeMillis() + 30 * 60000L)
            schedule(c)
        }
    }
}

class CleanReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        val pr = goAsync()
        Thread {
            try {
                Cleaner.fire(c)
            } catch (e: Exception) {
                Log.e("clean-receiver", e)
            } finally {
                pr.finish()
            }
        }.start()
    }
}
