package com.mission.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Study slots: the daily floating reminder of a target (subject buttons, topic picker, Later = 5 minutes),
 * and the strict focus that only lets study and pinned apps run while the slot is open.
 */
object Slot {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private const val LATER_MS = 5 * 60000L
    private val main = Handler(Looper.getMainLooper())

    // ---------------- data ----------------
    fun complete(t: Targets.JSONObject2): Boolean {
        val (done, total) = Targets.progress(t)
        return total > 0 && done >= total
    }

    /** Pending topic keys of a target, most asked in PYQs first. */
    /** Foreground app seen by the accessibility service (for the timer label). */
    @Volatile var fgPkg: String = ""

    /** After a circle search, result links (browser, custom tabs) may open for a few minutes without being blocked by strict focus. */
    @Volatile var grantUntil: Long = 0L

    fun pending(c: Context, t: Targets.JSONObject2): List<String> {
        val done = Syl.doneSet()
        val back = Review.backlogKeys()
        return t.topics.filter { it !in done }.sortedWith(compareByDescending<String> { it in back }.thenByDescending { pyq(c, it) })
    }

    /** Questions asked on this topic in the PYQ data (15 years of Prelims). */
    fun pyq(c: Context, key: String): Int =
        try {
            Pyq.load(c)
            Pyq.micro(key)?.let { return it.last15 }
            val sub = key.substringBefore('|')
            val topic = key.substringAfter('|', key)
            Zite.data(c).syl.firstOrNull { it.name == sub }?.micros?.firstOrNull { it.name == topic }?.q ?: 0
        } catch (e: Exception) {
            0
        }

    fun pyqYears(c: Context): Int =
        try {
            15
        } catch (e: Exception) {
            15
        }

    /** Minutes studied today in the subjects of a target (from the study timer sessions). */
    fun studiedToday(t: Targets.JSONObject2): Int =
        try {
            val subs = t.topics.map { it.substringBefore('|').lowercase() }.toSet()
            val m = Repo.byDaySubject(listOf(Targets.today())).firstOrNull() ?: emptyMap()
            m.filter { it.key.lowercase() in subs }.values.sum()
        } catch (e: Exception) {
            0
        }

    // ---------------- reminder card ----------------
    fun tag(t: Targets.JSONObject2) = "target_" + t.id

    fun dismiss(c: Context) {
        for (t in Targets.all()) Overlay.remove(c, tag(t))
    }

    fun later(c: Context, t: Targets.JSONObject2) {
        Overlay.remove(c, tag(t))
        try {
            val i = Intent(c, TargetReceiver::class.java).setAction("target.later").putExtra("tid", t.id)
            val pi = PendingIntent.getBroadcast(c, 4400 + (t.id.hashCode() and 0xff), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Sched.setAt(c, System.currentTimeMillis() + LATER_MS, pi)
        } catch (e: Exception) {
            Log.e("slot-later", e)
        }
    }

    fun reshow(c: Context, id: String?) {
        val t = Targets.all().firstOrNull { it.id == id } ?: return
        val end = t.end
        if (end != null && end.isBefore(Targets.today())) return
        if (complete(t) || TimerState.active()) return
        card(c, t)
    }

    fun begin(c: Context, t: Targets.JSONObject2, subject: String, topic: String?) {
        if (TimerState.active()) {
            c.toast("A timer is already running")
        } else {
            TimerState.start(subject, "stopwatch", topic)
            TimerService.start(c)
            Store.put("slot_target", t.id)
            c.toast("Timer started: " + (topic?.let { Targets.topicName(it) } ?: subject))
        }
        dismiss(c)
    }

    /** Study timer with a choice: pick subject(s) and any number of topics, then the timer starts and asks for completion at the end. */
    fun startTopics(a: android.app.Activity) {
        if (TimerState.active()) {
            a.toast("A timer is already running")
            return
        }
        PyqUi.pick(a, "Choose subject and topics", null, emptyList(), false, "Start timer") { keys ->
            if (TimerState.active()) return@pick
            if (keys.isEmpty()) {
                TimerState.start()
            } else {
                val names = keys.joinToString(" + ") { Targets.topicName(it) }
                TimerState.start(keys[0].substringBefore('|'), "stopwatch", if (keys.size == 1) keys[0] else names)
                Store.put("slot_target", "")
                Store.setStringList("slot_keys", if (keys.size == 1) emptyList() else keys)
            }
            TimerService.start(a)
            a.toast(if (keys.isEmpty()) "Timer started" else "Timer started: ${keys.size} topic(s)")
            Store.changed()
        }
    }

    /** Starts one timer for several topics at once. Subject = the first topic's subject, topic text = names joined. */
    fun beginMulti(c: Context, t: Targets.JSONObject2, keys: List<String>) {
        if (keys.isEmpty()) {
            begin(c, t, t.firstSubject, null)
            return
        }
        if (keys.size == 1) {
            begin(c, t, keys[0].substringBefore('|'), keys[0])
            return
        }
        if (TimerState.active()) {
            c.toast("A timer is already running")
        } else {
            val subj = keys[0].substringBefore('|')
            val names = keys.joinToString(" + ") { Targets.topicName(it) }
            TimerState.start(subj, "stopwatch", names)
            TimerService.start(c)
            Store.put("slot_target", t.id)
            Store.setStringList("slot_keys", keys)
            c.toast("Timer started: ${keys.size} topics")
        }
        dismiss(c)
    }

    /** Dialog used on the home Targets card: pick one or many topics (colour coded, with PYQ counts), then the timer starts. */
    fun pickTopic(a: Activity, t: Targets.JSONObject2, subject: String?) {
        val all = pending(a, t)
        val keys = if (subject == null) all else all.filter { it.substringBefore('|') == subject }
        if (keys.isEmpty()) {
            begin(a, t, subject ?: t.firstSubject, null)
            return
        }
        PyqUi.pick(a, (subject?.let { Syl.short(it) } ?: t.title) + ": choose topics for the timer", keys, emptyList(), false, "Start timer") { beginMulti(a, t, it) }
    }

    fun card(c0: Context, t: Targets.JSONObject2) {
        val c = c0.applicationContext
        if (!c.canOverlay()) return
        val box =
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = T.round(T.cardSolid, 18, 1, t.color)
                padDp(12, 10, 12, 10)
                T.elevate(this, 8, 18)
            }
        val dm = c.resources.displayMetrics
        val w = T.dp(250)
        val lp =
            Overlay.params(w).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dm.widthPixels - w - T.dp(12)
                y = T.dp(110)
            }

        var showTopics: (String, List<String>) -> Unit = { _, _ -> }

        fun home() {
            box.removeAllViews()
            val pend = pending(c, t)
            val (done, total) = Targets.progress(t)
            val head = c.hbox()
            head.addView(View(c).apply { background = T.round(t.color, 5) }, LinearLayout.LayoutParams(T.dp(10), T.dp(10)))
            head.addView(c.text(t.title, 13f, 2), lpm(-2, -2, 8, 0, 0, 0, 1f))
            box.addView(head)
            val slotEnd = t.at.plusMinutes(t.mins.toLong()).let { "%02d:%02d".format(it.hour, it.minute) }
            val strictChips = c.hbox()
            strictChips.addView(PyqUi.chip(c, if (Store.b("strict_slot", true)) "STRICT FOCUS until $slotEnd" else "Slot until $slotEnd", if (Store.b("strict_slot", true)) T.danger else T.sage, true))
            strictChips.addView(PyqUi.chip(c, "Study apps + pinned only", T.amber), lpm(-2, -2, 6, 0, 0, 0))
            box.addView(strictChips, lp(-2, -2, 6))
            val today = studiedToday(t)
            box.addView(c.text("Time to study  -  ${fmtMins(t.mins)} slot, ${Targets.daysLeft(t)} days left", 11.5f, 0, T.text2), lp(-2, -2, 4))
            box.addView(c.text("Today $today of ${t.mins} min  -  $done/$total topics done", 11f, 0, T.text3), lp(-2, -2, 2))
            val subs = pend.groupBy { it.substringBefore('|') }
            if (subs.isEmpty()) {
                box.addView(c.pill("Start timer", "timer", true, t.color) { begin(c, t, t.firstSubject, null) }, lp(-2, -2, 8))
            } else {
                box.addView(c.text("Pick a subject to start its timer", 11f, 1, T.text2), lp(-2, -2, 8))
                subs.entries.take(6).forEach { (s, ks) ->
                    box.addView(
                        c.pill(Syl.short(s) + "  (" + ks.size + " left, " + ks.sumOf { pyq(c, it) } + " Q)", "timer", true, Pyq.colorOf(s)) {
                            if (ks.size == 1) begin(c, t, s, ks[0]) else showTopics(s, ks)
                        },
                        lp(-1, -2, 5),
                    )
                }
            }
            if (subs.size > 1) {
                box.addView(c.text("Tip: open a subject and tick several topics to run one timer for all of them.", 10.5f, 0, T.text3), lp(-2, -2, 6))
            }
            box.addView(c.pill("Later (5 min)", null, false) { later(c, t) }, lp(-2, -2, 8))
        }

        fun topicsView(s: String, ks: List<String>) {
            box.removeAllViews()
            val col = Pyq.colorOf(s)
            val chosen = LinkedHashSet<String>()
            box.addView(c.text(Syl.short(s) + ": tick one or more topics", 12.5f, 2, T.textAccent(col)), lp(-2, -2, 0))
            val listBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val startBtn = c.pill("Start timer", "timer", true, col) { }
            fun redo() {
                listBox.removeAllViews()
                ks.take(10).forEach { k ->
                    val m = Pyq.micro(k)
                    val on = k in chosen
                    val tc = m?.let { Pyq.tierColor(it.tier) } ?: T.slate
                    val row = c.hbox()
                    row.background = T.round(if (on) T.withAlpha(col, 0.22f) else T.inset, 10)
                    row.padDp(8, 6, 8, 6)
                    row.addView(c.text(if (on) "\u2713" else "\u25CB", 13f, 3, T.textAccent(col)), lpm(T.dp(16), -2, 0, 0, 6, 0))
                    row.addView(c.text(Targets.topicName(k), 11.5f, 2, T.text, 2), LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(PyqUi.chip(c, (m?.last15 ?: pyq(c, k)).toString() + " Q", tc, true), lpm(-2, -2, 6, 0, 0, 0))
                    row.tap(false) { if (!chosen.add(k)) chosen.remove(k); redo() }
                    listBox.addView(row, lp(-1, -2, 4))
                }
                ((startBtn.getChildAt(startBtn.childCount - 1)) as? TextView)?.text = if (chosen.isEmpty()) "Start (whole subject)" else "Start timer (${chosen.size})"
            }
            val sv = android.widget.ScrollView(c).apply { addView(listBox) }
            box.addView(sv, LinearLayout.LayoutParams(-1, T.dp(min(260, 44 * ks.take(10).size + 8)), 0f).apply { topMargin = T.dp(6) })
            if (ks.size > 10) box.addView(c.text("+${ks.size - 10} more: use Start in the Targets card", 10.5f, 0, T.text3), lp(-2, -2, 4))
            redo()
            startBtn.setOnClickListener { if (chosen.isEmpty()) begin(c, t, s, null) else beginMulti(c, t, ks.filter { it in chosen }) }
            val r = c.hbox()
            r.addView(startBtn, lpm(-2, -2, 0, 0, 8, 0))
            r.addView(c.pill("Back", null, false) { home() })
            box.addView(r, lp(-2, -2, 8))
        }
        showTopics = { s, ks -> topicsView(s, ks) }
        home()
        Overlay.draggable(c, box, lp, tag(t))
        // no auto-remove: it stays until the timer is started, or Later brings it back after 5 minutes
        Overlay.show(c, tag(t), box, lp, 0)
    }

    // ---------------- strict focus ----------------
    fun current(): Targets.JSONObject2? {
        val now = LocalDateTime.now(IST)
        for (t in Targets.all()) {
            val end = t.end
            if (end != null && now.toLocalDate().isAfter(end)) continue
            if (complete(t)) continue
            val s = now.toLocalDate().atTime(t.at)
            val e = s.plusMinutes(t.mins.toLong())
            if (!now.isBefore(s) && now.isBefore(e) && studiedToday(t) < t.mins) return t
        }
        return null
    }

    /** Strict focus is ON during a booked slot and for as long as a study timer is running (unless a code-protected break is active). */
    fun strictOn(): Boolean =
        Store.b("strict_slot", true) && System.currentTimeMillis() >= Store.l("strict_off_until", 0L) &&
            (current() != null || (Store.b("strict_timer", true) && TimerState.active()))

    /** True while the code-protected 2 hour mock test break is running. */
    fun mockBreak(): Boolean = System.currentTimeMillis() < Store.l("strict_off_until", 0L) && Store.b("strict_is_mock", false)

    fun endsAt(): String = current()?.let { t -> t.at.plusMinutes(t.mins.toLong()).let { "%02d:%02d".format(it.hour, it.minute) } } ?: ""

    fun allowed(c: Context, pkg: String): Boolean {
        if (pkg == c.packageName || pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg in CircleService.GEMINI_PACKAGES || pkg in CircleService.LENS_PACKAGES) return true
        if (pkg in Apps.pins() || pkg in Apps.study() || Apps.allowedInFocus(c, pkg)) return true
        if (System.currentTimeMillis() < grantUntil && listOf("chrome", "browser", "firefox", "webview", "customtabs", "opera", "brave", "edge", "duckduckgo").any { pkg.contains(it) }) return true
        val keep = listOf("telecom", "dialer", "incallui", "permissioncontroller", "inputmethod", "keyboard", "com.miui.home", "com.android.launcher", "com.miui.notification", "com.miui.systemui", "com.android.packageinstaller", "packageinstaller", "com.google.android.gms", "com.android.documentsui", "com.android.providers", "com.google.android.documentsui")
        if (keep.any { pkg.contains(it) }) return true
        try {
            val ime = Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
            if (pkg == ime) return true
        } catch (e: Exception) { Log.e("slot", e) }
        return false
    }

    private var lastBlock = 0L

    private var pendingPkg: String? = null

    /**
     * Called by the accessibility service whenever a window changes. The foreground app is re-checked after a short delay, so short pop-ups,
     * keyboards, share sheets and permission dialogs of allowed apps never trigger a false block.
     */
    fun guard(svc: AccessibilityService, pkg: String) {
        try {
            if (!strictOn() || allowed(svc, pkg)) return
            if (pendingPkg != null) return
            pendingPkg = pkg
            main.postDelayed({
                pendingPkg = null
                try {
                    if (!strictOn()) return@postDelayed
                    val top = try { svc.rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null } ?: pkg
                    if (allowed(svc, top)) return@postDelayed
                    val now = System.currentTimeMillis()
                    if (now - lastBlock < 1200) return@postDelayed
                    lastBlock = now
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    main.postDelayed({
                        try {
                            svc.startActivity(Intent(svc, StrictActivity::class.java).putExtra("pkg", top).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        } catch (e: Exception) {
                            Log.e("slot-guard", e)
                        }
                    }, 200)
                } catch (e: Exception) {
                    Log.e("slot-guard", e)
                }
            }, 450)
        } catch (e: Exception) {
            pendingPkg = null
            Log.e("slot-guard", e)
        }
    }

    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

    /** A fresh random 15 character code for the emergency exit. */
    fun newCode(): String {
        val r = SecureRandom()
        return (1..15).map { CHARS[r.nextInt(CHARS.length)] }.joinToString("")
    }
}

/** Shown instead of a blocked app while a study slot is open. Emergency exit needs a random 15 character code typed by hand. */
class StrictActivity : Activity() {
    private var code = Slot.newCode()
    private var mockCode = Slot.newCode()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        T.init(applicationContext)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(T.page))
        val pkg = intent.getStringExtra("pkg") ?: ""
        val label =
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        render(pkg, label)
    }

    private fun render(pkg: String, label: String) {
        val box = vbox()
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.padDp(24, 40, 24, 24)
        val until = Slot.endsAt()
        box.addView(text("Strict focus is ON", 24f, 3, T.accent))
        box.addView(
            text(
                (if (until.isNotBlank()) "$label is blocked until $until. " else "$label is blocked. ") + "Only study apps and your pinned apps work during a study slot.",
                14f,
                0,
                T.text2,
            ),
            lp(-2, -2, 10),
        )
        box.addView(
            pill("Back to study", "timer", true) {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            },
            lp(-2, -2, 18),
        )
        val today = java.time.LocalDate.now(Repo.zone()).toString()
        val used = if (Store.s("em_day") == today) Store.i("em_used", 0) else 0
        val left = (5 - used).coerceAtLeast(0)
        box.addView(
            pill("Emergency 5 min  ($left of 5 left today)", "timer", false, T.danger) {
                val u = if (Store.s("em_day") == java.time.LocalDate.now(Repo.zone()).toString()) Store.i("em_used", 0) else 0
                if (u >= 5) {
                    toast("All 5 emergency passes used today")
                } else {
                    Store.put("em_day", java.time.LocalDate.now(Repo.zone()).toString())
                    Store.put("em_used", u + 1)
                    Store.put("strict_off_until", System.currentTimeMillis() + 5 * 60000L)
                    Store.put("strict_is_mock", false)
                    toast("Emergency pass: 5 minutes (" + (4 - u) + " left today)")
                    try {
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } catch (e: Exception) { Log.e("slot", e) }
                    finish()
                }
            },
            lp(-2, -2, 22),
        )
        box.addView(text("2 hour break (mock test)", 14f, 3, T.textAccent(T.amber)), lp(-2, -2, 26))
        box.addView(text("Meant for taking a mock test. Type this code exactly; strict focus pauses for 2 hours.", 12f, 0, T.text3), lp(-2, -2, 6))
        val mockShown =
            text(mockCode, 22f, 3, T.text).apply {
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.12f
                setTextIsSelectable(false)
                isLongClickable = false
            }
        box.addView(mockShown, lp(-2, -2, 8))
        val mf = Dlg.field(this, "Type the mock test code here", "")
        mf.isSingleLine = true
        box.addView(mf, lp(-1, -2, 8))
        box.addView(
            pill("Start 2 hour mock break", "timer", false, T.amber) {
                if (mf.text.toString().trim() == mockCode) {
                    Store.put("strict_off_until", System.currentTimeMillis() + 120 * 60000L)
                    Store.put("strict_is_mock", true)
                    toast("Mock test break: 2 hours")
                    try {
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } catch (e: Exception) { Log.e("slot", e) }
                    finish()
                } else {
                    toast("Wrong code, a new one is shown")
                    mockCode = Slot.newCode()
                    mf.setText("")
                    mockShown.text = mockCode
                }
            },
            lp(-2, -2, 10),
        )
        box.addView(text("Ways out: finish the timer, a 5 minute emergency pass (5 a day), or the 2 hour mock break.", 12f, 0, T.text3), lp(-2, -2, 22))
        setContentView(android.widget.ScrollView(this).apply { addView(box) })
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
