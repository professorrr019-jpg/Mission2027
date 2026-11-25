package com.mission.launcher

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

const val CH_WATER = "water_v2"
const val CH_TIMER = "timer_v1"
const val CH_INFO = "info_v1"
const val CH_DIGEST = "digest_v1"

object Boot {
    /** Safe to call from anywhere (activity, receiver, service). */
    fun ensure(c: Context) {
        Store.init(c)
        T.init(c.applicationContext)
        channels(c.applicationContext)
    }

    fun channels(c: Context) {
        val nm = c.nm()
        if (nm.getNotificationChannel(CH_WATER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_WATER, "Water reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Stays on screen until you log your water"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 80, 120)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        if (nm.getNotificationChannel(CH_TIMER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_TIMER, "Study timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while the floating study timer runs"
                    setShowBadge(false)
                },
            )
        }
        if (nm.getNotificationChannel(CH_INFO) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_INFO, "Study updates", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (nm.getNotificationChannel(CH_DIGEST) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_DIGEST, "Notification digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Periodic summaries of important notifications saved by Mission"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }
}

fun Context.online(): Boolean =
    try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork)
        c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        false
    }

fun Context.canOverlay(): Boolean = Settings.canDrawOverlays(this)

// ---------------- scheduling ----------------
object Sched {
    private fun pi(
        c: Context,
        code: Int,
        cls: Class<*>,
        act: String?,
        flags: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ): PendingIntent {
        val i = Intent(c, cls)
        if (act != null) i.action = act
        return PendingIntent.getBroadcast(c, code, i, flags)
    }

    fun setAt(
        c: Context,
        at: Long,
        p: PendingIntent,
    ) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        @Suppress("MissingPermission")
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    p,
                )
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (
            e: SecurityException,
        ) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun hourly(c: Context) {
        val t = TimeProvider.nextHalfHour(System.currentTimeMillis()) + 10_000L
        setAt(c, t, pi(c, 11, TickReceiver::class.java, "tick"))
    }

    fun wallpaper(
        c: Context,
        at: Long,
    ) = setAt(c, at, pi(c, 12, WallpaperReceiver::class.java, "wallpaper.tick"))

    fun cancelWallpaper(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 12, WallpaperReceiver::class.java, "wallpaper.tick"))
    }

    fun water(
        c: Context,
        at: Long,
    ) = setAt(c, at, pi(c, 21, WaterReceiver::class.java, WaterReceiver.TICK))

    fun waterNag(
        c: Context,
        at: Long,
    ) {
        val i = Intent(c, WaterReceiver::class.java).setAction(WaterReceiver.NAG).putExtra("scheduledAt", at)
        val p = PendingIntent.getBroadcast(c, 22, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAt(c, at, p)
    }

    fun cancelWater(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 21, WaterReceiver::class.java, WaterReceiver.TICK))
        am.cancel(pi(c, 22, WaterReceiver::class.java, WaterReceiver.NAG))
    }

    fun focus(
        c: Context,
        at: Long,
    ) = setAt(c, at + 1000, pi(c, 31, TickReceiver::class.java, "focus"))

    fun target(
        c: Context,
        at: Long,
    ) = setAt(c, at, pi(c, 41, TargetReceiver::class.java, "target.tick"))

    fun auto(
        c: Context,
        at: Long,
    ) = setAt(c, at, pi(c, 42, AutoReceiver::class.java, "auto.tick"))

    fun clean(
        c: Context,
        at: Long,
    ) = setAt(c, at, pi(c, 43, CleanReceiver::class.java, "clean.tick"))

    fun cancelClean(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 43, CleanReceiver::class.java, "clean.tick"))
    }

    fun cancelAuto(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 42, AutoReceiver::class.java, "auto.tick"))
    }

    fun cancelTarget(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(c, 41, TargetReceiver::class.java, "target.tick"))
    }
}

// ---------------- overlay windows (floating widgets over every app) ----------------
object Overlay {
    private val views = HashMap<String, View>()
    private val main = Handler(Looper.getMainLooper())

    fun params(
        w: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        focusable: Boolean = false,
    ): WindowManager.LayoutParams {
        var f = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            w,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            f,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    fun has(tag: String) = views.containsKey(tag)

    /** Lets the user drag a floating card anywhere (window uses TOP|START gravity). Taps on buttons still work. */
    fun draggable(
        c: Context,
        v: View,
        lp: WindowManager.LayoutParams,
        tag: String,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (moved || kotlin.math.abs(dx) > T.dp(6) || kotlin.math.abs(dy) > T.dp(6)) {
                        moved = true
                        val dm = c.resources.displayMetrics
                        lp.x = (startX + dx).toInt().coerceIn(0, dm.widthPixels - v.width.coerceAtLeast(1))
                        lp.y = (startY + dy).toInt().coerceIn(0, dm.heightPixels - v.height.coerceAtLeast(1))
                        update(c, tag, lp)
                    }
                    true
                }
                else -> moved
            }
        }
    }

    private val timers = HashMap<String, Runnable>()

    fun show(
        c: Context,
        tag: String,
        v: View,
        lp: WindowManager.LayoutParams,
        autoRemoveMs: Long = 0,
    ) {
        main.post {
            try {
                if (!c.canOverlay()) return@post
                views.remove(tag)?.let {
                        old ->
                    try {
                        (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(old)
                    } catch (e: Exception) { Log.e("platform", e) }
                }
                val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(v, lp)
                views[tag] = v
                timers.remove(tag)?.let { main.removeCallbacks(it) }
                if (autoRemoveMs > 0) {
                    val r = Runnable { if (views[tag] === v) remove(c, tag) }
                    timers[tag] = r
                    main.postDelayed(r, autoRemoveMs)
                }
            } catch (e: Exception) {
                Log.e("overlay", e)
            }
        }
    }

    fun update(
        c: Context,
        tag: String,
        lp: WindowManager.LayoutParams,
    ) {
        try {
            val v = views[tag] ?: return
            (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp)
        } catch (e: Exception) { Log.e("platform", e) }
    }

    fun remove(
        c: Context,
        tag: String,
    ) {
        main.post {
            try {
                timers.remove(tag)?.let { main.removeCallbacks(it) }
                val v = views.remove(tag) ?: return@post
                (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
            } catch (e: Exception) { Log.e("platform", e) }
        }
    }
}

// ---------------- receivers ----------------
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        val pr = goAsync()
        val act = i.action
        Thread {
            try {
                Boot.ensure(c)
                if (act == "focus") {
                    Focus.finishIfDue(c)
                    return@Thread
                }
                Focus.finishIfDue(c)
                Backup.auto(c)
                Briefs.tick(c)
                Water.arm(c)
                Targets.schedule(c)
                Auto.fire(c)
                if (Store.l("clean_next", 0L) <= System.currentTimeMillis() - 5 * 60000L) Cleaner.fire(c)
                Updater.backgroundCheck(c)
                try {
                    if (Zite.hasKey() && Zite.stale() && c.online()) Zite.sync(c)
                } catch (e: Exception) {
                    Log.e("tick-sync", e)
                }
                try {
                    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    c.cacheDir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
                } catch (e: Exception) {
                    Log.e("cache-cleanup", e)
                }
            } catch (e: Exception) {
                Log.e("tick", e)
            } finally {
                Sched.hourly(c)
                pr.finish()
            }
        }.start()
    }
}

class WallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        val pending = goAsync()
        Thread {
            try {
                Boot.ensure(c)
                Wallpaper.hourly(c)
            } catch (e: Exception) {
                Log.e("wallpaper-alarm", e)
            } finally {
                try {
                    Wallpaper.scheduleNext(c)
                } catch (e: Exception) {
                    Log.e("wallpaper-schedule", e)
                }
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        if (i.action !in
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
        ) {
            return
        }
        Boot.ensure(c)
        Sched.hourly(c)
        Water.arm(c); Targets.schedule(c); Auto.schedule(c); Cleaner.schedule(c)
        Wallpaper.scheduleNext(c)
        if (TimerState.active()) TimerService.start(c)
    }
}

class WaterReceiver : BroadcastReceiver() {
    companion object {
        const val TICK = "w.tick"
        const val NAG = "w.nag"
        const val ADD = "w.add"
        const val CUSTOM = "w.custom"
        const val SNOOZE = "w.snooze"
        const val DISMISSED = "w.dismissed"
        const val SKIP = "w.skip"
    }

    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        Boot.ensure(c)
        when (i.action) {
            TICK -> Water.remind(c, false)
            NAG -> Water.remind(c, true, i.getLongExtra("scheduledAt", 0L))
            ADD -> Water.logFromAction(c, i.getIntExtra("ml", 250))
            CUSTOM -> {
                val raw = android.app.RemoteInput.getResultsFromIntent(i)?.getCharSequence("ml")?.toString()?.trim().orEmpty()
                val amount = raw.toIntOrNull()
                if (amount != null && amount in 10..3000) Water.logFromAction(c, amount) else Water.remind(c, true)
            }
            SNOOZE -> Water.snooze(c)
            SKIP -> Water.skip(c)
            DISMISSED -> Water.onDismissed(c)
        }
    }
}

fun Context.nm(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
