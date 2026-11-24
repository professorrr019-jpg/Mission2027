package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * In-app updates from the GitHub release "latest" that the Build APK workflow publishes.
 * An update is offered when the release asset is newer than the time this app was last installed.
 * Installing uses PackageInstaller (the system asks the user to confirm). Needs the same signing key as the installed app,
 * which is why app/mission.keystore is used for every build.
 */
object Updater {
    const val DEFAULT_REPO = "professorrr019-jpg/Mission2027"

    /** GitHub "owner/name" that publishes the APK. Changeable in Settings > About if you move to a new repo. */
    fun repo(): String = Store.s("update_repo", DEFAULT_REPO).trim().ifBlank { DEFAULT_REPO }

    class Info(val url: String, val size: Long, val at: Long, val name: String)

    private fun installedAt(c: Context): Long =
        try {
            c.packageManager.getPackageInfo(c.packageName, 0).lastUpdateTime
        } catch (e: Exception) {
            0L
        }

    /** Network call: run off the main thread. Returns null when up to date or when offline. */
    fun check(c: Context): Info? {
        return try {
            val cn = URL("https://api.github.com/repos/${repo()}/releases/tags/latest").openConnection() as HttpURLConnection
            cn.connectTimeout = 8000
            cn.readTimeout = 8000
            cn.setRequestProperty("Accept", "application/vnd.github+json")
            if (cn.responseCode != 200) return null
            val j = JSONObject(cn.inputStream.bufferedReader().use { it.readText() })
            val assets = j.optJSONArray("assets") ?: return null
            var pick: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    pick = a
                    break
                }
            }
            val a = pick ?: return null
            // Build number from the release title ("... build 12"): newer number than the installed versionCode means update.
            val bn = Regex("build\\s*(\\d+)", RegexOption.IGNORE_CASE).find(j.optString("name") + " " + j.optString("body"))?.groupValues?.get(1)?.toIntOrNull()
            val at = Instant.parse(a.optString("updated_at", j.optString("published_at"))).toEpochMilli()
            if (bn != null) {
                Store.put("update_checked", System.currentTimeMillis())
                val installed = try { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode } catch (e: Exception) { 0L }
                return if (bn + 100L > installed) Info(a.optString("browser_download_url"), a.optLong("size"), at, a.optString("name")) else null
            }
            Store.put("update_checked", System.currentTimeMillis())
            if (at > installedAt(c) + 3 * 60000L) Info(a.optString("browser_download_url"), a.optLong("size"), at, a.optString("name")) else null
        } catch (e: Exception) {
            Log.e("update-check", e)
            null
        }
    }

    /** Called by the half-hour tick: look at most every 6 hours and tell the user once. */
    fun backgroundCheck(c: Context, minGapMin: Int = 14) {
        if (!Store.b("update_auto", true)) return
        if (System.currentTimeMillis() - Store.l("update_checked", 0L) < minGapMin * 60000L) return
        val info = check(c)
        Store.put("update_checked", System.currentTimeMillis())
        if (info == null) {
            Store.put("update_avail", 0L)
            return
        }
        val hour = java.time.LocalTime.now(Repo.zone()).hour
        if (Store.b("update_silent", true) && hour in 0..5 && unmetered(c) && c.packageManager.canRequestPackageInstalls() && Store.l("update_try", 0L) != info.at) {
            // Night-time, Wi-Fi: update by itself. Android may still ask once; the receiver turns that into a notification.
            Store.put("update_try", info.at)
            install(c, info) { }
            return
        }
        if (Store.l("update_avail", 0L) == info.at) return
        Store.put("update_avail", info.at)
        try {
            val pi = PendingIntent.getActivity(c, 4300, Intent(c, SettingsActivity::class.java).putExtra("path", "about").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val act = PendingIntent.getBroadcast(c, 4303, Intent(c, UpdateActionReceiver::class.java).setPackage(c.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            c.nm().notify(
                4300,
                Notification.Builder(c, CH_INFO).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("New Mission build is ready")
                    .setContentText("Tap Install now, or open Settings > About").setContentIntent(pi).setAutoCancel(true)
                    .addAction(Notification.Action.Builder(null, "Install now", act).build()).build(),
            )
        } catch (e: Exception) {
            Log.e("update-notify", e)
        }
    }

    /** Downloads the APK and hands it to PackageInstaller. [say] is called on the UI thread with progress text. */
    private fun ui(a: Context, f: () -> Unit) {
        if (a is Activity) a.runOnUiThread { f() } else android.os.Handler(android.os.Looper.getMainLooper()).post { f() }
    }

    private fun unmetered(c: Context): Boolean =
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.isActiveNetworkMetered.not()
        } catch (e: Exception) {
            false
        }

    fun install(
        a: Context,
        info: Info,
        say: (String) -> Unit,
    ) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            say("Allow 'Install unknown apps' for Mission, then tap update again")
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            } catch (e: Exception) {
            }
            return
        }
        Thread {
            try {
                val f = File(a.cacheDir, "update.apk")
                val cn = URL(info.url).openConnection() as HttpURLConnection
                cn.connectTimeout = 15000
                cn.readTimeout = 30000
                cn.instanceFollowRedirects = true
                val total = cn.contentLengthLong.takeIf { it > 0 } ?: info.size
                var got = 0L
                var lastSay = 0L
                cn.inputStream.use { ins ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            val now = System.currentTimeMillis()
                            if (now - lastSay > 500) {
                                lastSay = now
                                ui(a) { say("Downloading " + (got / 1048576) + " / " + (total / 1048576) + " MB") }
                            }
                        }
                    }
                }
                ui(a) { say("Installing...") }
                val pi = a.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (android.os.Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    f.inputStream().use { ins -> s.openWrite("mission.apk", 0, f.length()).use { o -> ins.copyTo(o); s.fsync(o) } }
                    val cb = PendingIntent.getBroadcast(a, id, Intent(a, UpdateReceiver::class.java).setPackage(a.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    s.commit(cb.intentSender)
                }
            } catch (e: Exception) {
                Log.e("update-install", e)
                ui(a) { say("Update failed: " + (e.message ?: e.javaClass.simpleName).take(80)) }
            }
        }.start()
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    try {
                        c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        // Started from the background: Android blocks the screen, so offer it as a notification.
                        val pi = PendingIntent.getActivity(c, 4301, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        c.nm().notify(4301, Notification.Builder(c, CH_INFO).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Mission update is ready").setContentText("Tap to finish installing").setContentIntent(pi).setAutoCancel(true).build())
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Store.put("update_avail", 0L)
            else -> {
                val m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
                android.widget.Toast.makeText(c, "Update: $m (a different signing key needs one reinstall)", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** "Install now" button of the update notification: downloads the new build and installs it. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        val pr = goAsync()
        Thread {
            try {
                val info = Updater.check(c)
                if (info == null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(c, "Already on the latest build", android.widget.Toast.LENGTH_SHORT).show() }
                } else {
                    c.nm().cancel(4300)
                    Updater.install(c, info) { m -> android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(c, m, android.widget.Toast.LENGTH_SHORT).show() } }
                }
            } catch (e: Exception) {
                Log.e("update-action", e)
            } finally {
                pr.finish()
            }
        }.start()
    }
}
