package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Daily score out of 100: study time 70, backlog 10, distraction apps 10, water 5, discipline (strict-mode passes) 5.
 * Study is the main part on purpose: 70 points = 70 percent of the score.
 * Manual hours count, but only up to 25 percent of the goal, and finished days are signed so they cannot be edited later.
 */
object DayScore {
    class Part(val key: String, val emoji: String, val label: String, val pts: Int, val max: Int, val color: Int, val note: String)

    class Result(val total: Int, val parts: List<Part>, val studyMins: Int, val manualMins: Int, val goal: Int)

    fun grade(s: Int): Triple<String, String, Int> =
        when {
            s >= 90 -> Triple("Topper day", "🏆", T.sage)
            s >= 75 -> Triple("Great day", "🔥", T.teal)
            s >= 60 -> Triple("Good day", "👍", T.accent)
            s >= 40 -> Triple("Average day", "😐", T.amber)
            else -> Triple("Needs effort", "😴", T.danger)
        }

    fun sessionsOn(d: LocalDate): List<org.json.JSONObject> =
        Store.rows("sessions").filter { java.time.Instant.ofEpochMilli(it.optLong("start")).atZone(Repo.zone()).toLocalDate() == d }

    fun studyMinsOn(d: LocalDate): Int = try { Repo.byDaySubject(listOf(d)).firstOrNull()?.values?.sum() ?: 0 } catch (e: Exception) { 0 }

    fun manualMinsOn(d: LocalDate): Int = sessionsOn(d).filter { it.optString("kind") == "manual" }.sumOf { it.optInt("mins") }

    fun compute(c: Context, d: LocalDate = Targets.today()): Result {
        val goal = Store.i("study_goal_min", 360).coerceAtLeast(30)
        val manual = manualMinsOn(d)
        var timerMins = (studyMinsOn(d) - manual).coerceAtLeast(0)
        if (d == Targets.today() && TimerState.active() && TimerState.running()) timerMins += ((System.currentTimeMillis() - Store.l("timer_seg", System.currentTimeMillis())) / 60000L).toInt().coerceAtLeast(0)
        val counted = timerMins + minOf(manual, goal / 4)
        val study = Math.round(70f * (counted.toFloat() / goal).coerceIn(0f, 1f))
        val isToday = d == Targets.today()
        val endMs = Repo.startOf(d.plusDays(1))
        // past days: a backlog row counts if it existed that day and was still open at its end
        fun openThen(r: org.json.JSONObject): Boolean = isToday && !r.optBoolean("done") || !isToday && r.optString("date").let { it.isBlank() || it <= d.toString() } && (!r.optBoolean("done") || r.optLong("updatedAt") > endMs)
        val bRows = Store.rows(Review.BACKLOG).filter { openThen(it) }
        val bk = bRows.filter { it.optString("type") == "topic" }.map { it.optString("key") }.toSet().size
        val bkMin = bRows.filter { it.optString("type") == "time" }.sumOf { it.optInt("mins") }
        val back = (10 - bk * 2 - bkMin / 30).coerceIn(0, 10)
        var dis = 5
        var disNote = "Usage access off: neutral 5"
        val use = if (Usage.granted(c)) Usage.on(c, d) else emptyMap()
        if (Usage.granted(c) && (isToday || use.isNotEmpty())) {
            val d0 = Usage.distraction()
            val ms = use.filter { it.key in d0 }.values.sum()
            val m = (ms / 60000L).toInt()
            dis = (10 - Math.round(10f * ((m - 15f) / 105f).coerceIn(0f, 1f)))
            disNote = "$m min on distraction apps"
        }
        val ml = Store.rows("water").filter { Repo.dayOf(it.optLong("t")) == d }.sumOf { it.optInt("ml") }
        val wg = Water.goal()
        val water = Math.round(5f * (ml.toFloat() / wg).coerceIn(0f, 1f))
        val used = if (Store.s("em_day") == d.toString()) Store.i("em_used", 0) else 0
        val disc = (5 - used).coerceIn(0, 5)
        val parts =
            listOf(
                Part("study", "📚", "Study", study, 70, T.sage, "$counted of $goal min" + (if (manual > 0) "  (manual $manual min, counted up to ${goal / 4})" else "")),
                Part("backlog", "📌", "Backlog", back, 10, T.amber, if (bk == 0 && bkMin == 0) "No backlog" else "$bk topics, $bkMin min pending"),
                Part("dist", "📵", "Distraction", dis, 10, T.rose, disNote),
                Part("water", "💧", "Water", water, 5, T.teal, "$ml of $wg ml"),
                Part("disc", "🔒", "Discipline", disc, 5, T.plum, if (used == 0) "No emergency pass used" else "$used emergency pass(es)"),
            )
        return Result(parts.sumOf { it.pts }, parts, counted, manual, goal)
    }

    // ---- tamper-proof history: only today can be written, every row carries a signature ----
    private fun sig(day: String, score: Int, p: String): String = Integer.toHexString((day + "|" + score + "|" + p + "|mission-2027").hashCode())

    /** Past days that were never saved (app not opened, update installed later) are rebuilt once from sessions, water, usage and reviews. */
    fun backfill(c: Context) {
        try {
            val todayS = Targets.today().toString()
            if (Store.s("score_backfill") == todayS) return
            Store.put("score_backfill", todayS)
            val have = saved().keys
            val first = Store.rows("sessions").minOfOrNull { it.optLong("start") }?.let { java.time.Instant.ofEpochMilli(it).atZone(Repo.zone()).toLocalDate() } ?: return
            for (i in 1..30) {
                val d = Targets.today().minusDays(i.toLong())
                if (d < first || d.toString() in have) continue
                save(compute(c, d), d, true)
            }
        } catch (e: Exception) {
            Log.e("score-backfill", e)
        }
    }

    fun save(r: Result, d: LocalDate = Targets.today(), rebuilt: Boolean = false) {
        try {
            val day = d.toString()
            val p = r.parts.joinToString(",") { it.pts.toString() }
            val ex = Store.rows("score_hist").firstOrNull { it.optString("day") == day }
            if (ex == null) {
                Store.add("score_hist", obj("day" to day, "score" to r.total, "p" to p, "m" to r.studyMins, "sig" to sig(day, r.total, p), "r" to (if (rebuilt) 1 else 0)))
            } else if (ex.optInt("score") != r.total || ex.optString("p") != p) {
                Store.update("score_hist", ex.optString("id")) { it.put("score", r.total).put("p", p).put("m", r.studyMins).put("sig", sig(day, r.total, p)) }
            }
        } catch (e: Exception) {
            Log.e("score-save", e)
        }
    }

    /** Saved, untouched rows only. A row whose signature does not match was edited and is ignored. */
    fun saved(): Map<String, org.json.JSONObject> =
        Store.rows("score_hist").filter { it.optString("sig") == sig(it.optString("day"), it.optInt("score"), it.optString("p")) }.associateBy { it.optString("day") }

    fun history(days: Int = 7): List<Pair<LocalDate, Int?>> {
        val rows = saved()
        return (days - 1 downTo 0).map { Targets.today().minusDays(it.toLong()).let { d -> d to rows[d.toString()]?.optInt("score") } }
    }

    private fun appName(c: Context, pkg: String): String = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }

    /** What lies behind one score part: the real items, and how to earn the missing points. */
    fun partDetail(a: Activity, p: Part, r: Result) {
        val box = a.vbox()
        box.padDp(18, 6, 18, 6)
        box.addView(a.text(p.emoji + "  " + p.pts + " / " + p.max + " points", 16f, 3, T.textAccent(p.color)))
        box.addView(a.text(p.note, 12.5f, 1, T.text2), lp(-2, -2, 4))
        fun line(t: String, col: Int = T.text) = box.addView(a.text(t, 12.5f, 1, col), lp(-1, -2, 6))
        when (p.key) {
            "study" -> {
                box.addView(a.text("Today's sessions", 12f, 3, T.text), lp(-2, -2, 12))
                val ss = sessionsOn(Targets.today())
                if (ss.isEmpty()) line("No session yet. Start the timer or add study hours.", T.text3)
                ss.sortedBy { it.optLong("start") }.forEach {
                    val t = java.time.Instant.ofEpochMilli(it.optLong("start")).atZone(Repo.zone()).toLocalTime()
                    line("%02d:%02d   %d min   %s%s".format(t.hour, t.minute, it.optInt("mins"), it.optString("subject"), if (it.optString("kind") == "manual") "   (manual)" else ""))
                }
                line("Rule: 70 points x (counted minutes / goal). Manual time counts up to a quarter of the goal.", T.text3)
                val need = (r.goal - r.studyMins).coerceAtLeast(0)
                if (need > 0) line("$need more minutes for the full 70.", T.textAccent(p.color))
            }
            "backlog" -> {
                val ks = Review.backlogKeys().toList()
                if (ks.isEmpty()) line("Nothing pending. Full 10.", T.textAccent(T.sage))
                ks.take(15).forEach { line("•  " + Targets.topicName(it), T.textAccent(Pyq.colorOf(it.substringBefore('|')))) }
                line("Rule: 10, minus 2 per backlog topic and 1 per 30 pending minutes.", T.text3)
            }
            "dist" -> {
                if (!Usage.granted(a)) {
                    line("Usage access is off, so a neutral 5 is given. Turn it on in Settings > Permissions.", T.text3)
                } else {
                    val d0 = Usage.distraction()
                    Usage.today(a).filter { it.key in d0 }.entries.sortedByDescending { it.value }.take(8).forEach { line(appName(a, it.key) + "   " + (it.value / 60000L) + " min") }
                    line("Rule: first 15 minutes free, 0 points at 2 hours.", T.text3)
                }
            }
            "water" -> {
                val rows = Store.rows("water").filter { Repo.dayOf(it.optLong("t")) == Targets.today() }
                if (rows.isEmpty()) line("No water logged today.", T.text3)
                rows.sortedBy { it.optLong("t") }.forEach {
                    val t = java.time.Instant.ofEpochMilli(it.optLong("t")).atZone(Repo.zone()).toLocalTime()
                    line("%02d:%02d   %d ml".format(t.hour, t.minute, it.optInt("ml")))
                }
                line("Rule: 5 x (water / goal).", T.text3)
            }
            else -> {
                line("Each emergency pass in strict mode costs 1 point (5 a day allowed).", T.text3)
            }
        }
        Dlg.builder(a).setTitle(p.label).setView(ScrollView(a).apply { addView(box) }).setPositiveButton("Close", null).show()
    }

    /** One finished day: its saved parts and the sessions studied that day. */
    fun dayDetail(a: Activity, d: LocalDate) {
        val row = saved()[d.toString()]
        val box = a.vbox()
        box.padDp(18, 6, 18, 6)
        if (row == null) {
            box.addView(a.text("No saved score for this day.", 13f, 1, T.text3))
        } else {
            val (name, emoji, col) = grade(row.optInt("score"))
            box.addView(a.text("$emoji  " + row.optInt("score") + " / 100   $name", 17f, 3, T.textAccent(col)))
            if (row.optInt("r") == 1) box.addView(a.text("Rebuilt from your sessions, water, app usage and reviews of that day.", 11f, 1, T.text3), lp(-1, -2, 2))
            val pts = row.optString("p").split(",").mapNotNull { it.toIntOrNull() }
            val labels = listOf("Study" to 70, "Backlog" to 10, "Distraction" to 10, "Water" to 5, "Discipline" to 5)
            labels.forEachIndexed { i, (l, mx) -> box.addView(a.text(l + "   " + (pts.getOrNull(i) ?: 0) + " / " + mx, 12.5f, 2, T.text), lp(-1, -2, 6)) }
        }
        box.addView(a.text("Sessions", 12f, 3, T.text), lp(-2, -2, 12))
        val ss = sessionsOn(d)
        if (ss.isEmpty()) box.addView(a.text("No sessions recorded.", 12f, 1, T.text3), lp(-1, -2, 4))
        ss.sortedBy { it.optLong("start") }.forEach {
            box.addView(a.text(it.optInt("mins").toString() + " min   " + it.optString("subject") + (if (it.optString("kind") == "manual") "   (manual)" else ""), 12.5f, 1, T.text), lp(-1, -2, 4))
        }
        Dlg.builder(a).setTitle(d.toString()).setView(ScrollView(a).apply { addView(box) }).setPositiveButton("Close", null).show()
    }

    /** 30 day history: bars, average, best day, number of good days; tap a day for its detail. */
    fun historyDialog(a: Activity) {
        val h = history(30)
        val vals = h.mapNotNull { it.second }
        val box = a.vbox()
        box.padDp(18, 6, 18, 6)
        if (vals.isEmpty()) {
            box.addView(a.text("History starts from the first day this score was saved.", 13f, 1, T.text3))
        } else {
            val stats = a.hbox()
            stats.addView(PyqUi.chip(a, "Average " + vals.average().toInt(), T.accent, true))
            stats.addView(PyqUi.chip(a, "Best " + vals.max(), T.sage, true), lpm(-2, -2, 6, 0, 0, 0))
            stats.addView(PyqUi.chip(a, vals.count { it >= 75 }.toString() + " great days", T.teal, true), lpm(-2, -2, 6, 0, 0, 0))
            box.addView(stats)
        }
        val bars = a.hbox()
        bars.gravity = android.view.Gravity.BOTTOM
        h.forEach { (d, s) ->
            val col = s?.let { grade(it).third } ?: T.text3
            bars.addView(View(a).apply { background = T.round(T.withAlpha(col, if (s == null) 0.25f else 1f), 2); tap(false) { dayDetail(a, d) } }, LinearLayout.LayoutParams(0, T.dp(((s ?: 4) / 100f * 90).toInt().coerceAtLeast(4)), 1f).apply { marginEnd = T.dp(2) })
        }
        box.addView(bars, lp(-1, T.dp(96), 12))
        box.addView(a.text("Days (tap one)", 12f, 3, T.text), lp(-2, -2, 12))
        h.reversed().forEach { (d, s) ->
            val col = s?.let { grade(it).third } ?: T.text3
            val row = a.hbox()
            row.padDp(10, 8, 10, 8)
            row.background = T.round(T.withAlpha(col, 0.12f), 10)
            row.addView(a.text(d.toString() + "  " + d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, 12.5f, 2, T.text), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(a.text(if (s == null) "-" else (grade(s).second + "  " + s), 13f, 3, T.textAccent(col)))
            row.tap(false) { dayDetail(a, d) }
            box.addView(row, lp(-1, -2, 5))
        }
        Dlg.builder(a).setTitle("Score history (30 days)").setView(ScrollView(a).apply { addView(box) }).setPositiveButton("Close", null).show()
    }

    /** The big card: a 3D ring, live numbers, tappable parts and history. */
    fun card(h: Host): View {
        val c = h.act
        val card = Card(c)
        card.addView(c.cardHeader("bolt", "Today's score", T.accent))
        val top = c.hbox()
        val ring = ScoreRing(c)
        top.addView(ring, LinearLayout.LayoutParams(T.dp(150), T.dp(150)))
        val side = c.vbox()
        val gradeT = c.text("", 18f, 3, T.text)
        val subT = c.text("", 12f, 1, T.text2)
        val avgT = c.text("", 12f, 2, T.text2)
        side.addView(gradeT)
        side.addView(subT, lp(-2, -2, 4))
        side.addView(avgT, lp(-2, -2, 4))
        side.addView(c.pill("History", "clock", false, T.accent) { historyDialog(c) }, lp(-2, -2, 10))
        top.addView(side, lpm(0, -2, 14, 0, 0, 0).also { (it as LinearLayout.LayoutParams).weight = 1f })
        card.addView(top, lp(-1, -2, 4))
        val partsBox = c.vbox()
        card.addView(partsBox, lp(-1, -2, 8))
        card.addView(c.text("Last 7 days", 11f, 2, T.text2), lp(-2, -2, 10))
        val hist = c.hbox()
        card.addView(hist, lp(-1, -2, 4))
        card.addView(c.text("Updates live. Tap the ring, a line or a day for details.", 10.5f, 0, T.text3), lp(-2, -2, 8))

        fun bind(r: Result) {
            val (name, emoji, col) = grade(r.total)
            ring.set(r.total, col, emoji)
            gradeT.text = "$emoji  $name"
            gradeT.setTextColor(T.textAccent(col))
            subT.text = "${r.studyMins} of ${r.goal} min studied"
            val vals = history(7).mapNotNull { it.second }
            avgT.text = if (vals.isEmpty()) "7-day average: starting" else "7-day average: " + vals.average().toInt()
            partsBox.removeAllViews()
            r.parts.forEach { p ->
                val row = c.hbox()
                row.padDp(0, 5, 0, 5)
                row.addView(c.text(p.emoji + "  " + p.label, 12f, 2, T.text), LinearLayout.LayoutParams(T.dp(112), -2))
                val bar = LinearLayout(c)
                val f = (p.pts.toFloat() / p.max).coerceIn(0.001f, 1f)
                bar.addView(View(c).apply { background = T.round(p.color, 4) }, LinearLayout.LayoutParams(0, T.dp(8), f))
                bar.addView(View(c).apply { background = T.round(T.withAlpha(p.color, 0.18f), 4) }, LinearLayout.LayoutParams(0, T.dp(8), (1f - f).coerceAtLeast(0.001f)))
                row.addView(bar, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(c.text(p.pts.toString() + "/" + p.max, 11.5f, 3, T.textAccent(p.color)), lpm(T.dp(46), -2, 8, 0, 0, 0))
                row.addView(c.text(">", 12f, 2, T.text3), lpm(-2, -2, 6, 0, 0, 0))
                row.tap(false) { partDetail(c, p, r) }
                partsBox.addView(row)
                partsBox.addView(c.text(p.note, 10.5f, 0, T.text3), lp(-2, -2, 0))
            }
            hist.removeAllViews()
            history().forEach { (d, s) ->
                val col2 = s?.let { grade(it).third } ?: T.text3
                val cell = c.vbox()
                cell.addView(c.text(s?.toString() ?: "-", 11f, 3, T.textAccent(col2)).apply { gravity = android.view.Gravity.CENTER })
                cell.addView(View(c).apply { background = T.round(col2, 3) }, LinearLayout.LayoutParams(-1, T.dp(((s ?: 0) / 100f * 28).toInt().coerceAtLeast(3))).apply { topMargin = T.dp(3) })
                cell.addView(c.text(d.dayOfWeek.name.take(1) + d.dayOfWeek.name.substring(1, 3).lowercase(), 9.5f, 0, T.text3).apply { gravity = android.view.Gravity.CENTER })
                cell.tap(false) { dayDetail(c, d) }
                hist.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }

        var last: Result? = null
        fun refresh() {
            Thread {
                val r = try { compute(c) } catch (e: Exception) { Log.e("score", e); null }
                if (r != null) {
                    save(r)
                    backfill(c)
                    c.runOnUiThread { last = r; bind(r) }
                }
            }.start()
        }
        ring.tap(false) { historyDialog(c) }
        // live: every 20 seconds while the card is on screen; stops when it is detached (no leak, no work in the background)
        val loop =
            object : Runnable {
                override fun run() {
                    refresh()
                    card.postDelayed(this, 20000)
                }
            }
        card.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    card.removeCallbacks(loop)
                    card.post(loop)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    card.removeCallbacks(loop)
                }
            },
        )
        compute(c).let { last = it; bind(it) }
        return card
    }
}

/** A 3D looking gauge: soft drop shadow, glossy gradient arc with round caps, domed centre. Animates to the new value. */
class ScoreRing(c: Context) : View(c) {
    private var shown = 0f
    private var target = 0
    private var col = T.accent
    private var emoji = ""
    private var anim: android.animation.ValueAnimator? = null
    private val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; maskFilter = android.graphics.BlurMaskFilter(T.dpf(7f), android.graphics.BlurMaskFilter.Blur.NORMAL) }
    private val track = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private val arc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
    private val gloss = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
    private val disc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val discShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { maskFilter = android.graphics.BlurMaskFilter(T.dpf(9f), android.graphics.BlurMaskFilter.Blur.NORMAL) }
    private val txt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
    private val small = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER }
    private val rect = android.graphics.RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for the blur shadows
        contentDescription = "Daily score"
    }

    fun set(v: Int, color: Int, e: String) {
        col = color
        emoji = e
        if (v == target && shown > 0f) {
            invalidate()
            return
        }
        target = v
        anim?.cancel()
        anim = android.animation.ValueAnimator.ofFloat(shown, v.toFloat()).apply {
            duration = 700
            interpolator = android.view.animation.PathInterpolator(0.22f, 0.9f, 0.25f, 1f)
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(cv: android.graphics.Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val sw = minOf(w, h) * 0.13f
        val r = minOf(w, h) / 2 - sw * 0.9f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f * (shown / 100f).coerceIn(0f, 1f)
        // soft shadow under the ring
        shadow.strokeWidth = sw
        shadow.color = T.withAlpha(0xFF000000.toInt(), 0.30f)
        cv.save(); cv.translate(0f, T.dpf(4f))
        cv.drawArc(rect, -90f, maxOf(sweep, 1f), false, shadow)
        cv.restore()
        track.strokeWidth = sw
        track.color = T.withAlpha(col, 0.16f)
        cv.drawArc(rect, 0f, 360f, false, track)
        // main arc with a lit-from-above gradient
        val light = blend(col, 0xFFFFFFFF.toInt(), 0.35f)
        val dark = blend(col, 0xFF000000.toInt(), 0.25f)
        arc.strokeWidth = sw
        arc.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(dark, col, light, col), floatArrayOf(0f, 0.35f, 0.8f, 1f)).also { sh -> val m = android.graphics.Matrix(); m.postRotate(-90f, cx, cy); sh.setLocalMatrix(m) }
        cv.drawArc(rect, -90f, maxOf(sweep, 0.5f), false, arc)
        // glossy highlight on the upper edge of the arc
        gloss.strokeWidth = sw * 0.28f
        gloss.color = 0x66FFFFFF
        val gr = android.graphics.RectF(rect); gr.inset(-sw * 0.22f, -sw * 0.22f)
        cv.drawArc(gr, -90f, maxOf(sweep - 2f, 0.5f), false, gloss)
        // domed centre
        val ir = r - sw * 0.85f
        discShadow.color = T.withAlpha(0xFF000000.toInt(), 0.22f)
        cv.drawCircle(cx, cy + T.dpf(3f), ir, discShadow)
        disc.shader = android.graphics.RadialGradient(cx - ir * 0.35f, cy - ir * 0.45f, ir * 1.5f, intArrayOf(blend(T.cardSolid, 0xFFFFFFFF.toInt(), 0.7f), T.cardSolid, blend(T.cardSolid, 0xFF000000.toInt(), 0.12f)), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, ir, disc)
        txt.textSize = ir * 0.95f
        txt.color = T.textAccent(col)
        cv.drawText(Math.round(shown).toString(), cx, cy + ir * 0.18f, txt)
        small.textSize = ir * 0.30f
        small.color = T.text3
        cv.drawText("of 100", cx, cy + ir * 0.55f, small)
        small.textSize = ir * 0.40f
        cv.drawText(emoji, cx, cy - ir * 0.52f, small)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = android.graphics.Color.red(a); val ag = android.graphics.Color.green(a); val ab = android.graphics.Color.blue(a)
        return android.graphics.Color.rgb((ar + (android.graphics.Color.red(b) - ar) * t).toInt(), (ag + (android.graphics.Color.green(b) - ag) * t).toInt(), (ab + (android.graphics.Color.blue(b) - ab) * t).toInt())
    }
}

/** Prelims pace: topics and MCQs per day still needed, versus what you actually study. */
object PaceCard {
    fun weakKeys(): List<String> {
        val back = Review.backlogKeys().toList()
        val revise = try { Store.rows(Review.BACKLOG).filter { !it.optBoolean("done") && it.optString("why").isNotBlank() && it.optString("type") == "topic" }.map { it.optString("key") } } catch (e: Exception) { emptyList() }
        val done = Syl.doneSet()
        return (back + revise).distinct().filter { it !in done }
    }

    fun makeWeakTarget(a: Activity): Boolean {
        val keys = weakKeys().take(15)
        if (keys.isEmpty()) {
            a.toast("No weak topics yet: they appear from your reviews")
            return false
        }
        if (Targets.all().any { it.title == "Weak topics" && Targets.today() <= (it.end ?: Targets.today()) }) {
            a.toast("A Weak topics target already exists")
            return false
        }
        Store.add(Targets.COLL, obj("title" to "Weak topics", "topics" to org.json.JSONArray(keys), "end" to Targets.today().plusDays(10).toString(), "mins" to 120, "at" to "06:00", "col" to 0))
        a.toast("Target created: ${keys.size} weak topics")
        Store.changed()
        return true
    }

    fun card(h: Host): View {
        val c = h.act
        Pyq.load(c)
        val card = Card(c)
        val left = (ChronoUnit.DAYS.between(Targets.today(), Repo.examDate())).coerceAtLeast(1)
        val done = Syl.doneSet()
        val all = Pyq.subjects.flatMap { it.micros }
        val rem = all.count { it.key !in done }
        val perDay = rem.toFloat() / left
        val mcqLeft = try { Pyq.mocks?.micros?.filter { it.key !in done }?.sumOf { it.total } ?: 0 } catch (e: Exception) { 0 }
        val week = (0 until 7).sumOf { DayScore.studyMinsOn(Targets.today().minusDays(it.toLong())) } / 7
        val perTopic = Store.i("pace_mins_per_topic", 45)
        val needMin = (perDay * perTopic).toInt()
        val (st, col) =
            when {
                rem == 0 -> "Syllabus complete" to T.sage
                week >= needMin * 1.1f -> "Ahead of pace" to T.sage
                week >= needMin * 0.8f -> "On pace" to T.teal
                week >= needMin * 0.5f -> "Slightly behind" to T.amber
                else -> "Behind pace" to T.danger
            }
        card.addView(c.cardHeader("target", "Prelims pace", col))
        val top = c.hbox()
        top.addView(c.text(left.toString(), 34f, 3, T.textAccent(T.danger)))
        top.addView(c.text(" days to Prelims", 13f, 1, T.text2), LinearLayout.LayoutParams(-2, -2).apply { topMargin = T.dp(14) })
        top.addView(c.flex())
        top.addView(PyqUi.chip(c, st, col, true))
        card.addView(top, lp(-1, -2, 4))
        fun line(label: String, v: String, k: Int) {
            val r = c.hbox()
            r.addView(c.text(label, 12f, 1, T.text2), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(c.text(v, 12.5f, 3, T.textAccent(k)))
            card.addView(r, lp(-1, -2, 6))
        }
        line("Topics left", "$rem of ${all.size}", T.accent)
        line("Needed per day", "%.1f topics  ~ %d min".format(perDay, needMin), T.amber)
        line("Your 7-day average", "$week min a day", col)
        if (mcqLeft > 0) line("Zite MCQs left", "$mcqLeft  (%d a day)".format(Math.ceil(mcqLeft.toDouble() / left).toInt()), T.plum)
        val wk = weakKeys()
        if (wk.isNotEmpty()) {
            card.addView(c.text("${wk.size} weak topics (backlog / revise)", 12f, 2, T.textAccent(T.danger)), lp(-2, -2, 8))
            card.addView(c.pill("Create weak-topic target", "target", false, T.danger) { makeWeakTarget(h.act); h.refresh() }, lp(-2, -2, 6))
        }
        card.addView(c.text("Estimate uses $perTopic min per topic.", 10.5f, 0, T.text3), lp(-2, -2, 6))
        return card
    }
}

/** Missed topics of yesterday's targets move to the backlog by themselves, once a day. */
object Carry {
    fun run(c: Context) {
        try {
            val today = Targets.today().toString()
            if (Store.s("carry_day") == today) return
            Store.put("carry_day", today)
            val y = Targets.today().minusDays(1)
            val studied = Repo.byDaySubject(listOf(y)).firstOrNull() ?: emptyMap()
            var n = 0
            Targets.all().forEach { t ->
                val active = t.end?.let { it >= y } ?: true
                if (!active || t.topics.isEmpty()) return@forEach
                val subjMins = studied.filter { e -> t.topics.any { Syl.short(it.substringBefore('|')).equals(Syl.short(e.key), true) } }.values.sum()
                if (subjMins >= t.mins / 2) return@forEach
                Slot.pending(c, t).take(2).forEach { k ->
                    if (Store.rows(Review.BACKLOG).none { it.optString("key") == k && !it.optBoolean("done") }) {
                        Store.add(Review.BACKLOG, obj("type" to "topic", "key" to k, "date" to y.toString(), "why" to "carried: missed target", "updatedAt" to System.currentTimeMillis()))
                        n++
                    }
                }
            }
            if (n > 0) Store.changed()
        } catch (e: Exception) {
            Log.e("carry", e)
        }
    }
}

/** Ongoing, silent notification visible on the lock screen: today's target, timer and days left. */
object LockInfo {
    private const val ID = 5030
    private const val CH = "today_info_v1"

    fun update(c: Context) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!Store.b("lock_info", true)) {
                nm.cancel(ID)
                return
            }
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(CH, "Today on lock screen", android.app.NotificationManager.IMPORTANCE_LOW).apply {
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setShowBadge(false)
                    },
                )
            }
            val days = ChronoUnit.DAYS.between(Targets.today(), Repo.examDate()).coerceAtLeast(0)
            val t = Targets.all().filter { (it.end ?: Targets.today()) >= Targets.today() }.minByOrNull { it.at }
            val tt = if (t == null) "No target today" else t.title + " " + "%02d:%02d".format(t.at.hour, t.at.minute)
            val timer = if (TimerState.active()) "Timer: " + TimerState.subject() else "Timer off"
            val score = DayScore.compute(c).total
            val n =
                Notification.Builder(c, CH).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentTitle("$days days to Prelims  |  Score $score")
                    .setContentText("$tt  |  $timer")
                    .setContentIntent(PendingIntent.getActivity(c, ID, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .build()
            nm.notify(ID, n)
        } catch (e: Exception) {
            Log.e("lockinfo", e)
        }
    }
}

/** Study mode home: while a timer or a strict slot runs, the home pages show only study cards (no weather, news, AI or widgets). */
object StudyMode {
    val ALLOWED = setOf("header", "timer", "targets", "score", "pace", "nexttopic", "plan", "revision", "notes", "thinkers", "countdown", "focus", "heat", "water", "important", "tools")

    fun on(): Boolean = Store.b("study_mode_home", true) && (TimerState.active() || Slot.strictOn())
}

/** Manual study hours: add time you studied without the timer (date, subject, minutes). It feeds the score, pace, heat map and targets. */
object ManualHours {
    fun add(a: Activity) {
        val today = Targets.today()
        val days = (0..6).map { today.minusDays(it.toLong()) }
        Dlg.choose(a, "Which day?", days.mapIndexed { i, d -> (if (i == 0) "Today" else if (i == 1) "Yesterday" else d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }) + "  " + d }) { di ->
            Pyq.load(a)
            val subs = Pyq.subjects.map { it.name } + "General / other"
            Dlg.choose(a, "Subject", subs.map { if (it == "General / other") it else Syl.short(it) }) { si ->
                Dlg.input(a, "Study time", "Minutes (90) or hours (1.5h)", "", false, false, "Add") { v ->
                    val t = v.trim().lowercase()
                    val mins = try { if (t.endsWith("h")) (t.dropLast(1).trim().toDouble() * 60).toInt() else t.toDouble().toInt() } catch (e: Exception) { 0 }
                    if (mins !in 1..900) {
                        a.toast("Enter a time between 1 minute and 15 hours")
                    } else {
                        val start = days[di].atTime(18, 0).atZone(Repo.zone()).toInstant().toEpochMilli()
                        val subject = if (si < Pyq.subjects.size) Syl.short(subs[si]) else "General"
                        Repo.addSession(start, start + mins * 60000L, subject, "manual")
                        Store.changed()
                        a.toast("Added $mins min of $subject on ${days[di]}")
                    }
                }
            }
        }
    }

    /** Recent study sessions (timer and manual). Tap a manual one to delete it. */
    fun sessionLog(a: Activity) {
        val rows = Store.rows("sessions").sortedByDescending { it.optLong("start") }.take(40)
        val items = rows.map {
            val d = java.time.Instant.ofEpochMilli(it.optLong("start")).atZone(Repo.zone()).toLocalDate()
            d.toString() + "  " + it.optInt("mins") + " min  " + it.optString("subject") + (if (it.optString("kind") == "manual") "  (manual)" else "")
        }
        if (items.isEmpty()) { a.toast("No study sessions yet"); return }
        Dlg.choose(a, "Study log (tap a manual entry to delete)", items) { i ->
            val r = rows[i]
            if (r.optString("kind") != "manual") {
                a.toast("Timer sessions cannot be deleted here")
            } else {
                Dlg.confirm(a, "Delete this entry?", items[i], "Delete") { Store.remove("sessions", r.optString("id")); Store.changed() }
            }
        }
    }
}
