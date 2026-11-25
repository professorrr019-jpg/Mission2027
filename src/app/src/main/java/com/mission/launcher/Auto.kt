package com.mission.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Automation engine: 52 ready rules, every one OFF until you switch it on (or tap "Recommended").
 * A rule = time of day + optional conditions + an action. Each rule fires at most once a day.
 * Conditions use only data already in the app (study minutes, counters, exam date, weather, AQI).
 */
object Auto {
    private val IST = ZoneId.of("Asia/Kolkata")

    /** act: n = notification, nf = notification + small floating card, sync = Zite sync, wall = refresh wallpaper. */
    class Rule(val id: String, val group: String, val title: String, val spec: String, val msg: String, val act: String = "nf", val rec: Boolean = false)

    val rules: List<Rule> =
        listOf(
            // ---- study rhythm ----
            Rule("s01", "Study rhythm", "Morning start", "t=06:00", "Good morning. First block starts now: pick your hardest topic.", rec = true),
            Rule("s02", "Study rhythm", "Mid-morning check", "t=10:00;study<60", "One hour in and almost nothing logged. Start the timer.", rec = true),
            Rule("s03", "Study rhythm", "Noon progress", "t=13:00;study<180", "Less than 3 hours done by 1 pm. Plan the afternoon block now."),
            Rule("s04", "Study rhythm", "Afternoon slump", "t=15:30", "Slump time: stand up, water, then a 25-minute focused block.", "n"),
            Rule("s05", "Study rhythm", "Evening review", "t=18:30;study<240", "Under 4 hours so far. Two more focused blocks are still possible."),
            Rule("s06", "Study rhythm", "Streak at risk", "t=21:00;study<1", "No study logged today. Even 25 minutes keeps your streak alive.", rec = true),
            Rule("s07", "Study rhythm", "Night wrap-up", "t=22:00", "Wrap up. Write tomorrow's top 3 priorities.", rec = true),
            Rule("s08", "Study rhythm", "No priorities set", "t=08:00;top0", "You have no priorities for today. Set the top 3 in the Toolkit."),
            Rule("s09", "Study rhythm", "Weekend deep block", "t=07:00;dow=6", "Saturday: one long uninterrupted block of 3 hours today.", "n"),
            Rule("s10", "Study rhythm", "Sunday planning", "t=19:00;dow=7", "Sunday evening: plan the week and set targets.", rec = true),
            Rule("s11", "Study rhythm", "Monday reset", "t=06:30;dow=1", "New week. Review last week's report and pick 3 targets.", "n"),
            Rule("s12", "Study rhythm", "Friday review", "t=20:00;dow=5", "Friday: check this week's report and mistakes.", "n"),
            // ---- revision and practice ----
            Rule("r01", "Revision and practice", "Revision due (morning)", "t=07:30;rev>0", "You have topics due for revision today.", rec = true),
            Rule("r02", "Revision and practice", "Revision due (evening)", "t=19:30;rev>0", "Revision is still pending. Clear it before sleep."),
            Rule("r03", "Revision and practice", "Answer writing", "t=17:00;cnt:answers<1", "No answer written today. Write one now, 7 minutes."),
            Rule("r04", "Revision and practice", "Newspaper", "t=08:30;cnt:news<1", "Newspaper not read yet. 40 minutes, with notes.", rec = true),
            Rule("r05", "Revision and practice", "Newspaper (late)", "t=20:30;cnt:news<1", "Newspaper still not done. Skim headlines and editorials."),
            Rule("r06", "Revision and practice", "CSAT set", "t=16:00;cnt:csat<1", "No CSAT set today. 20 minutes is enough."),
            Rule("r07", "Revision and practice", "PYQ practice", "t=11:30;cnt:pyq<1", "Solve a PYQ set for your current topic."),
            Rule("r08", "Revision and practice", "Essay outline", "t=18:00;dow=3;cnt:essay<1", "Wednesday essay practice: write one outline.", "n"),
            Rule("r09", "Revision and practice", "Mistake log", "t=21:30", "Add today's mistakes to your mistake log.", "n"),
            Rule("r10", "Revision and practice", "Weekly mock", "t=09:00;dow=7", "Sunday mock day. Sit the test in exam conditions.", rec = true),
            // ---- exam countdown ----
            Rule("e01", "Exam countdown", "365 days to go", "t=08:00;days=365", "365 days left. Build the base, one subject at a time."),
            Rule("e02", "Exam countdown", "200 days to go", "t=08:00;days=200", "200 days left. Finish the first full syllabus pass."),
            Rule("e03", "Exam countdown", "100 days to go", "t=08:00;days=100", "100 days left. Tests and revision from now.", rec = true),
            Rule("e04", "Exam countdown", "75 days to go", "t=08:00;days=75", "75 days left. Two full mocks a week."),
            Rule("e05", "Exam countdown", "50 days to go", "t=08:00;days=50", "50 days left. Only your own notes and error log."),
            Rule("e06", "Exam countdown", "30 days to go", "t=08:00;days=30", "30 days left. Revise, test, sleep.", rec = true),
            Rule("e07", "Exam countdown", "15 days to go", "t=08:00;days=15", "15 days left. No new sources."),
            Rule("e08", "Exam countdown", "7 days to go", "t=08:00;days=7", "One week left. Check the admit card and centre."),
            Rule("e09", "Exam countdown", "Tomorrow", "t=20:00;days=1", "Exam tomorrow. Admit card, ID, pens, sleep early.", rec = true),
            // ---- health and focus ----
            Rule("h01", "Health and focus", "Water morning", "t=09:00", "Drink a glass of water.", "n"),
            Rule("h02", "Health and focus", "Water afternoon", "t=14:30", "Water time. A short walk helps too.", "n"),
            Rule("h03", "Health and focus", "Water evening", "t=18:00", "Water time. Stretch for two minutes.", "n"),
            Rule("h04", "Health and focus", "Eye rest", "t=11:00", "20-20-20: look 20 feet away for 20 seconds.", "n"),
            Rule("h05", "Health and focus", "Posture", "t=16:30", "Check posture and shoulders.", "n"),
            Rule("h06", "Health and focus", "Exercise", "t=05:45", "Move for 20 minutes before the day starts.", "n"),
            Rule("h07", "Health and focus", "Wind down", "t=22:30", "Phone down. Sleep is part of the syllabus.", "nf", true),
            Rule("h08", "Health and focus", "Distraction urge log", "t=20:00;cnt:urge<1", "Resisted a distraction today? Log it as a win.", "n"),
            // ---- weather and environment ----
            Rule("w01", "Weather and environment", "Bad air outside", "t=07:00;aqi>200", "AQI is above 200. Skip outdoor exercise today.", "n", true),
            Rule("w02", "Weather and environment", "Poor air (moderate)", "t=07:00;aqi>100", "AQI is above 100. Keep the morning walk short.", "n"),
            Rule("w03", "Weather and environment", "Fog", "t=06:30;fog", "Fog in Prayagraj. Good light-less morning: study indoors.", "n"),
            Rule("w04", "Weather and environment", "Rain", "t=07:00;rain", "Rain today. Take the day for a long library-style block.", "n"),
            Rule("w05", "Weather and environment", "Heat", "t=11:00;hot", "Very hot. Study in the coolest room, drink water.", "n"),
            Rule("w06", "Weather and environment", "Cold", "t=06:30;cold", "Cold morning. Warm up before the first block.", "n"),
            // ---- sync and system ----
            Rule("y01", "Sync and system", "Morning Zite sync", "t=06:00", "", "sync", true),
            Rule("y02", "Sync and system", "Afternoon Zite sync", "t=14:00", "", "sync"),
            Rule("y03", "Sync and system", "Night Zite sync", "t=23:00", "", "sync", true),
            Rule("y04", "Sync and system", "Wallpaper dawn", "t=05:30", "", "wall"),
            Rule("y05", "Sync and system", "Wallpaper noon", "t=12:00", "", "wall"),
            Rule("y06", "Sync and system", "Wallpaper dusk", "t=17:30", "", "wall"),
            Rule("y07", "Sync and system", "Wallpaper night", "t=19:30", "", "wall"),
            Rule("y08", "Sync and system", "Clear idle apps at noon", "t=13:00", "", "clean"),
            Rule("y09", "Sync and system", "Clear idle apps in the evening", "t=19:00", "", "clean"),
            Rule("y10", "Sync and system", "Sync syllabus and mocks at night", "t=22:15", "", "sync"),
            Rule("g01", "Targets", "Target behind schedule", "t=20:00;tbehind", "A target is behind its pace. Use tomorrow's slot for the weakest subject.", rec = true),
            Rule("g02", "Targets", "Daily targets summary", "t=21:30;tany", "Open the Targets card: see today's minutes and the topics still left.", "n", rec = true),
            Rule("g03", "Targets", "Sunday target review", "t=10:30;dow=7;tany", "Weekly review: tick finished topics, then adjust the finish dates.", "n"),
            Rule("g04", "Targets", "No target set", "t=09:00;notarget", "You have no target. Add one in the Targets card: subject, topics, finish date, daily time.", "n"),
        )

    // ---------------- state ----------------
    private fun onSet(): MutableSet<String> = Store.s("auto_on").split("\u001f").filter { it.isNotEmpty() }.toMutableSet()

    fun enabled(id: String) = id in onSet()

    fun setEnabled(id: String, on: Boolean) {
        val s = onSet()
        if (on) s.add(id) else s.remove(id)
        Store.put("auto_on", s.joinToString("\u001f"))
    }

    private fun timeOf(r: Rule): LocalTime {
        val o = Store.s("auto_t_${r.id}")
        val raw = o.ifBlank { r.spec.split(";").first { it.startsWith("t=") }.removePrefix("t=") }
        return try {
            LocalTime.parse(raw)
        } catch (e: Exception) {
            LocalTime.of(8, 0)
        }
    }

    private fun conditionsOk(c: Context, r: Rule, today: LocalDate): Boolean {
        for (p in r.spec.split(";")) {
            if (p.startsWith("t=")) continue
            val ok =
                when {
                    p.startsWith("study<") -> Repo.minutesOn(today) < p.removePrefix("study<").toInt()
                    p.startsWith("cnt:") -> {
                        val (id, n) = p.removePrefix("cnt:").split("<")
                        Toolkit.count(id) < n.toInt()
                    }
                    p == "rev>0" -> Store.rows("tk_rev").any { !it.optBoolean("done") && LocalDate.parse(it.optString("due")) <= today }
                    p.startsWith("aqi>") -> Store.i("aqi_us", -1) > p.removePrefix("aqi>").toInt()
                    p == "fog" -> Weather.cached()?.code in listOf(45, 48)
                    p == "rain" -> (Weather.cached()?.code ?: 0).let { it in 51..67 || it in 80..82 || it in 95..99 }
                    p == "hot" -> (Weather.cached()?.temperatureC ?: 0.0) >= 40.0
                    p == "cold" -> (Weather.cached()?.temperatureC ?: 99.0) <= 8.0
                    p.startsWith("dow=") -> today.dayOfWeek.value == p.removePrefix("dow=").toInt()
                    p.startsWith("days=") -> ChronoUnit.DAYS.between(today, Repo.examDate()) == p.removePrefix("days=").toLong()
                    p == "tbehind" -> Targets.all().any { Targets.status(it).first.let { st -> st.startsWith("Behind") || st == "Overdue" } }
                    p == "tany" -> Targets.all().isNotEmpty()
                    p == "notarget" -> Targets.all().isEmpty()
                    p == "top0" -> Store.rows("tk_top").none { it.optString("d") == today.toString() }
                    else -> true
                }
            if (!ok) return false
        }
        return true
    }

    // ---------------- scheduling ----------------
    private fun nextFire(): Long? {
        val now = LocalDateTime.now(IST)
        val on = onSet()
        var best: Long? = null
        for (r in rules) {
            if (r.id !in on) continue
            var cand = now.toLocalDate().atTime(timeOf(r))
            if (!cand.isAfter(now) || Store.s("auto_fired_${r.id}") == now.toLocalDate().toString()) cand = cand.plusDays(1)
            val ms = cand.atZone(IST).toInstant().toEpochMilli()
            if (best == null || ms < best) best = ms
        }
        return best
    }

    fun schedule(c: Context) {
        val at = nextFire()
        if (at == null) Sched.cancelAuto(c) else Sched.auto(c, at)
    }

    /** Runs due rules. Safe to call any time (alarm, hourly tick, boot): each rule fires once a day, at most 90 minutes late. */
    fun fire(c: Context) {
        try {
            Boot.ensure(c)
            val now = LocalDateTime.now(IST)
            val today = now.toLocalDate()
            val on = onSet()
            for (r in rules) {
                if (r.id !in on) continue
                if (Store.s("auto_fired_${r.id}") == today.toString()) continue
                val late = ChronoUnit.MINUTES.between(today.atTime(timeOf(r)), now)
                if (late < 0 || late > 90) continue
                Store.put("auto_fired_${r.id}", today.toString())
                if (!conditionsOk(c, r, today)) continue
                run(c, r)
            }
        } catch (e: Exception) {
            Log.e("auto", e)
        } finally {
            schedule(c)
        }
    }

    private fun run(c: Context, r: Rule) {
        when (r.act) {
            "sync" -> Zite.sync(c)
            "clean" -> Cleaner.run(c)
            "wall" -> if (Wallpaper.enabled()) Wallpaper.apply(c, true)
            else -> {
                notify(c, r)
                if (r.act == "nf") card(c, r)
            }
        }
    }

    private fun notify(c: Context, r: Rule) {
        try {
            val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open", "auto_detail").putExtra("rule", r.id)
            val nid = 4300 + (r.id.hashCode() and 0xff)
            val pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n =
                Notification.Builder(c, CH_INFO)
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentTitle(r.title)
                    .setContentText(r.msg)
                    .setStyle(Notification.BigTextStyle().bigText(r.msg))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            c.nm().notify(nid, n)
        } catch (e: Exception) {
            Log.e("auto-notify", e)
        }
    }

    private fun card(c0: Context, r: Rule) {
        val c = c0.applicationContext
        if (!c.canOverlay()) return
        val tag = "auto_${r.id}"
        val box =
            LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = T.round(T.cardSolid, 18, 1, T.accent)
                padDp(12, 10, 12, 10)
                T.elevate(this, 8, 18)
            }
        box.addView(c.text(r.title, 13f, 2))
        box.addView(c.text(r.msg, 11.5f, 0, T.text2), lp(-1, -2, 4))
        val row = c.hbox()
        row.addView(
            c.pill("Open", null, true, T.accent) {
                c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open", "auto_detail").putExtra("rule", r.id))
                Overlay.remove(c, tag)
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        row.addView(c.pill("Dismiss", null, false) { Overlay.remove(c, tag) })
        box.addView(row, lp(-2, -2, 8))
        val dm = c.resources.displayMetrics
        val lp =
            Overlay.params(T.dp(230)).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dm.widthPixels - T.dp(230) - T.dp(12)
                y = T.dp(110)
            }
        Overlay.draggable(c, box, lp, tag)
        Overlay.show(c, tag, box, lp, 60_000L)
    }

    /** What a tap on a rule's notification or floating card shows: the full message and what to do, not the settings list. */
    fun detail(a: android.app.Activity, id: String?) {
        val r = rules.firstOrNull { it.id == id } ?: run { open(a); return }
        val t = timeOf(r)
        val box = a.vbox()
        box.padDp(20, 8, 20, 4)
        box.addView(PyqUi.chip(a, r.group, T.accent))
        box.addView(a.text(r.msg.ifBlank { r.title }, 14f, 0, T.text), lp(-1, -2, 10))
        box.addView(a.text("Runs daily around " + "%02d:%02d".format(t.hour, t.minute), 11.5f, 1, T.text3), lp(-1, -2, 8))
        val b = Dlg.builder(a).setTitle(r.title).setView(box).setPositiveButton("Done", null)
        if (r.id[0] == 's' || r.id[0] == 'r') b.setNeutralButton("Open tool") { _, _ -> Toolkit.open(a) }
        b.setNegativeButton("All reminders") { _, _ -> open(a) }
        b.show()
    }

    // ---------------- screen ----------------
    fun open(c: Context) {
        val box = c.vbox()
        fun fill() {
            box.removeAllViews()
            val on = onSet()
            box.addView(c.text("${on.size} of ${rules.size} rules on. Every rule fires at most once a day and is off until you switch it on.", 11.5f, 0, T.text3))
            val r0 = c.hbox(false)
            r0.addView(
                c.pill("Recommended (${rules.count { it.rec }})", "bolt", true, T.accent) {
                    rules.filter { it.rec }.forEach { setEnabled(it.id, true) }
                    schedule(c)
                    fill()
                },
                lpm(-2, -2, 0, 0, 8, 0),
            )
            r0.addView(
                c.pill("All off", null, false) {
                    Store.put("auto_on", "")
                    schedule(c)
                    fill()
                },
            )
            box.addView(r0, lp(-2, -2, 10))
            var last = ""
            rules.forEach { r ->
                if (r.group != last) {
                    last = r.group
                    box.addView(c.text(r.group.uppercase(), 11.5f, 2, T.text3), lp(-1, -2, 14))
                }
                val isOn = r.id in on
                val t = timeOf(r)
                val line =
                    c.hbox().apply {
                        background = T.round(T.inset, 14)
                        padDp(10, 8, 10, 8)
                        addView(c.icon(if (isOn) "checkc" else "bell", if (isOn) T.sage else T.text3, 20, 1.9f))
                        addView(
                            c.vbox().apply {
                                addView(c.text(r.title, 13f, 2, T.text, 1))
                                addView(c.text("%02d:%02d".format(t.hour, t.minute) + (if (r.msg.isNotBlank()) "  -  " + r.msg else "  -  runs quietly"), 10.5f, 0, T.text3, 2), lp(-1, -2, 2))
                            },
                            lpm(0, -2, 10, 0, 0, 0, 1f),
                        )
                        tap {
                            Dlg.choose(c, r.title, listOf(if (isOn) "Turn off" else "Turn on", "Change time")) { k ->
                                if (k == 0) {
                                    setEnabled(r.id, !isOn)
                                    schedule(c)
                                    fill()
                                } else {
                                    Dlg.time(c, t) { hm ->
                                        Store.put("auto_t_${r.id}", hm)
                                        Store.put("auto_fired_${r.id}", "")
                                        schedule(c)
                                        fill()
                                    }
                                }
                            }
                        }
                    }
                box.addView(line, lp(-1, -2, 6))
            }
        }
        fill()
        Dlg.view(c, "Automation (${rules.size} rules)", box)
    }
}

class AutoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        Thread {
            try {
                Auto.fire(c)
            } catch (e: Exception) {
                Log.e("auto-receiver", e)
            } finally {
                pr.finish()
            }
        }.start()
    }
}
