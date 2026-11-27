package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Daily score out of 100: study time 70, backlog 10, distraction apps 10, water 5, discipline (strict-mode passes) 5.
 * Study is the main part on purpose: 70 points = 70 percent of the score.
 */
object DayScore {
    class Part(val emoji: String, val label: String, val pts: Int, val max: Int, val color: Int, val note: String)

    class Result(val total: Int, val parts: List<Part>, val studyMins: Int, val goal: Int)

    fun grade(s: Int): Triple<String, String, Int> =
        when {
            s >= 90 -> Triple("Topper day", "🏆", T.sage)
            s >= 75 -> Triple("Great day", "🔥", T.teal)
            s >= 60 -> Triple("Good day", "👍", T.accent)
            s >= 40 -> Triple("Average day", "😐", T.amber)
            else -> Triple("Needs effort", "😴", T.danger)
        }

    fun studyMinsOn(d: LocalDate): Int = try { Repo.byDaySubject(listOf(d)).firstOrNull()?.values?.sum() ?: 0 } catch (e: Exception) { 0 }

    fun compute(c: Context, d: LocalDate = Targets.today()): Result {
        val goal = Store.i("study_goal_min", 360).coerceAtLeast(30)
        var mins = studyMinsOn(d)
        val today = d == Targets.today()
        if (today && TimerState.active() && TimerState.running()) mins += ((System.currentTimeMillis() - Store.l("timer_seg", System.currentTimeMillis())) / 60000L).toInt().coerceAtLeast(0)
        val study = Math.round(70f * (mins.toFloat() / goal).coerceIn(0f, 1f))
        val bk = Review.backlogKeys().size
        val bkMin = Review.backlogMins()
        val back = (10 - bk * 2 - bkMin / 30).coerceIn(0, 10)
        var dis = 5
        var disNote = "Usage access off: neutral 5"
        if (Usage.granted(c)) {
            val d0 = Usage.distraction()
            val ms = Usage.today(c).filter { it.key in d0 }.values.sum()
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
                Part("📚", "Study", study, 70, T.sage, "$mins of $goal min"),
                Part("📌", "Backlog", back, 10, T.amber, if (bk == 0 && bkMin == 0) "No backlog" else "$bk topics, $bkMin min pending"),
                Part("📵", "Distraction", dis, 10, T.rose, disNote),
                Part("💧", "Water", water, 5, T.teal, "$ml of $wg ml"),
                Part("🔒", "Discipline", disc, 5, T.plum, if (used == 0) "No emergency pass used" else "$used emergency pass(es)"),
            )
        return Result(parts.sumOf { it.pts }, parts, mins, goal)
    }

    fun save(r: Result) {
        try {
            val day = Targets.today().toString()
            val ex = Store.rows("score_hist").firstOrNull { it.optString("day") == day }
            if (ex == null) Store.add("score_hist", obj("day" to day, "score" to r.total)) else if (ex.optInt("score") != r.total) Store.update("score_hist", ex.optString("id")) { it.put("score", r.total) }
        } catch (e: Exception) {
            Log.e("score-save", e)
        }
    }

    fun history(days: Int = 7): List<Pair<LocalDate, Int?>> {
        val rows = Store.rows("score_hist").associate { it.optString("day") to it.optInt("score") }
        return (days - 1 downTo 0).map { Targets.today().minusDays(it.toLong()).let { d -> d to rows[d.toString()] } }
    }

    fun card(h: Host): View {
        val c = h.act
        val r = compute(c)
        save(r)
        val (name, emoji, col) = grade(r.total)
        val card = Card(c)
        card.addView(c.cardHeader("bolt", "Today's score", col))
        val top = c.hbox()
        top.addView(c.text(r.total.toString(), 40f, 3, T.textAccent(col)))
        top.addView(c.text(" / 100", 14f, 1, T.text3), LinearLayout.LayoutParams(-2, -2).apply { topMargin = T.dp(18) })
        top.addView(c.flex())
        top.addView(c.text("$emoji  $name", 14f, 3, T.textAccent(col)))
        card.addView(top, lp(-1, -2, 4))
        r.parts.forEach { p ->
            val row = c.hbox()
            row.addView(c.text(p.emoji + "  " + p.label, 12f, 2, T.text), LinearLayout.LayoutParams(T.dp(110), -2))
            val bar = LinearLayout(c)
            val f = (p.pts.toFloat() / p.max).coerceIn(0.001f, 1f)
            bar.addView(View(c).apply { background = T.round(p.color, 3) }, LinearLayout.LayoutParams(0, T.dp(6), f))
            bar.addView(View(c).apply { background = T.round(T.withAlpha(p.color, 0.18f), 3) }, LinearLayout.LayoutParams(0, T.dp(6), (1f - f).coerceAtLeast(0.001f)))
            row.addView(bar, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(c.text(p.pts.toString() + "/" + p.max, 11.5f, 3, T.textAccent(p.color)), lpm(T.dp(44), -2, 8, 0, 0, 0))
            card.addView(row, lp(-1, -2, 7))
            card.addView(c.text(p.note, 10.5f, 0, T.text3), lp(-2, -2, 1))
        }
        card.addView(c.text("Last 7 days", 11f, 2, T.text2), lp(-2, -2, 10))
        val hist = c.hbox()
        history().forEach { (d, s) ->
            val col2 = s?.let { grade(it).third } ?: T.text3
            val cell = c.vbox()
            cell.addView(c.text(s?.toString() ?: "-", 11f, 3, T.textAccent(col2)).apply { gravity = android.view.Gravity.CENTER })
            cell.addView(View(c).apply { background = T.round(col2, 3) }, LinearLayout.LayoutParams(-1, T.dp(((s ?: 0) / 100f * 28).toInt().coerceAtLeast(3))).apply { topMargin = T.dp(3) })
            cell.addView(c.text(d.dayOfWeek.name.take(1) + d.dayOfWeek.name.substring(1, 3).lowercase(), 9.5f, 0, T.text3).apply { gravity = android.view.Gravity.CENTER })
            hist.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(hist, lp(-1, -2, 4))
        return card
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
