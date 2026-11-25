package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Study targets (5+ at once). Stored in collection "targets":
 * id, title, topics (JSON array of "Subject|Micro" syllabus keys), end (yyyy-MM-dd), mins (daily minutes),
 * at (HH:mm daily reminder), col (palette index), fired (last day the reminder was shown).
 * Topic progress is read live from the same `syl_done` set the Prelims syllabus card writes, so both stay in sync.
 */
object Targets {
    const val COLL = "targets"
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    fun all(): List<JSONObject2> = Store.rows(COLL).map { JSONObject2(it) }

    class JSONObject2(val o: org.json.JSONObject) {
        val id: String get() = o.optString("id")
        val title: String get() = o.optString("title", "Target")
        val end: LocalDate? get() = try { LocalDate.parse(o.optString("end")) } catch (e: Exception) { null }
        val mins: Int get() = o.optInt("mins", 120)
        val at: LocalTime get() = try { LocalTime.parse(o.optString("at", "06:00")) } catch (e: Exception) { LocalTime.of(6, 0) }
        val color: Int get() = T.palette[o.optInt("col", 0).coerceAtLeast(0) % T.palette.size]
        val topics: List<String> get() {
            val a = o.optJSONArray("topics") ?: JSONArray()
            return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.map { if (Pyq.ready) Pyq.mapKey(it) else it }.distinct()
        }
        val firstSubject: String get() = topics.firstOrNull()?.substringBefore('|') ?: "General"
    }

    fun today(): LocalDate = LocalDate.now(IST)

    fun daysLeft(t: JSONObject2): Long = t.end?.let { ChronoUnit.DAYS.between(today(), it) } ?: 0L

    fun progress(t: JSONObject2): Pair<Int, Int> {
        val d = Syl.doneSet()
        val topics = t.topics
        return topics.count { it in d } to topics.size
    }

    /** Topics per day still needed to finish on time. */
    fun pace(t: JSONObject2): Float {
        val (done, total) = progress(t)
        val left = (total - done).coerceAtLeast(0)
        val days = (daysLeft(t) + 1).coerceAtLeast(1)
        return left.toFloat() / days
    }

    fun status(t: JSONObject2): Pair<String, Int> {
        val (done, total) = progress(t)
        val dl = daysLeft(t)
        return when {
            total > 0 && done >= total -> "Completed" to T.sage
            dl < 0 -> "Overdue" to T.danger
            total == 0 -> "No topics selected" to T.text3
            pace(t) > 3f -> "Behind: ${"%.1f".format(pace(t))} topics/day needed" to T.danger
            pace(t) > 1.5f -> "Tight: ${"%.1f".format(pace(t))} topics/day" to T.amber
            else -> "On track: ${"%.1f".format(pace(t))} topics/day" to T.sage
        }
    }

    fun topicName(key: String) = key.substringAfter('|', key)

    // ---------------- reminders ----------------
    /** Next reminder instant across all active targets, or null. */
    private fun nextFire(): Long? {
        val now = LocalDateTime.now(IST)
        var best: Long? = null
        for (t in all()) {
            val end = t.end ?: continue
            if (end.isBefore(now.toLocalDate())) continue
            if (progress(t).let { it.second > 0 && it.first >= it.second }) continue
            var cand = now.toLocalDate().atTime(t.at)
            val firedToday = t.o.optString("fired") == now.toLocalDate().toString()
            if (!cand.isAfter(now) || firedToday) cand = cand.plusDays(1)
            if (cand.toLocalDate().isAfter(end)) continue
            val ms = cand.atZone(IST).toInstant().toEpochMilli()
            if (best == null || ms < best) best = ms
        }
        return best
    }

    fun schedule(c: Context) {
        val at = nextFire()
        if (at == null) Sched.cancelTarget(c) else Sched.target(c, at)
        Review.arm(c)
    }

    /** Called by [TargetReceiver] at the reminder time. Shows a colour-coded notification and a small floating card. */
    fun fire(c: Context) {
        Boot.ensure(c)
        val now = LocalDateTime.now(IST)
        for (t in all()) {
            val end = t.end ?: continue
            if (end.isBefore(now.toLocalDate())) continue
            if (t.o.optString("fired") == now.toLocalDate().toString()) continue
            if (progress(t).let { it.second > 0 && it.first >= it.second }) continue
            val due = now.toLocalDate().atTime(t.at)
            val lateMin = ChronoUnit.MINUTES.between(due, now)
            if (lateMin < 0 || lateMin > 120) continue // missed by more than 2 h: skip silently
            Store.update(COLL, t.id) { it.put("fired", now.toLocalDate().toString()) }
            notify(c, t)
            card(c, t)
        }
        schedule(c)
    }

    private fun notify(
        c: Context,
        t: JSONObject2,
    ) {
        try {
            val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(c, 4100 + (t.id.hashCode() and 0xff), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val (done, total) = progress(t)
            val n =
                Notification.Builder(c, CH_INFO)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle(t.title + " - " + fmtMins(t.mins) + " today")
                    .setContentText("${daysLeft(t)} days left - $done/$total topics done")
                    .setColor(t.color)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            c.nm().notify(4100 + (t.id.hashCode() and 0xff), n)
        } catch (e: Exception) {
            Log.e("target-notify", e)
        }
    }

    private fun card(
        c0: Context,
        t: JSONObject2,
    ) = Slot.card(c0, t)

    // ---------------- Google Calendar ----------------
    fun addToCalendar(
        c: Context,
        t: JSONObject2,
    ) {
        try {
            val end = t.end ?: today().plusDays(30)
            var begin = today().atTime(t.at)
            if (!begin.isAfter(LocalDateTime.now(IST))) begin = begin.plusDays(1)
            val bms = begin.atZone(IST).toInstant().toEpochMilli()
            val ems = bms + t.mins * 60000L
            val until = end.plusDays(1).atStartOfDay(ZoneId.of("UTC")).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
            val i =
                Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Study: " + t.title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, "Topics: " + t.topics.joinToString(", ") { topicName(it) } + "\nTarget end: $end (UPSC Prelims 2027)")
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, bms)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ems)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Kolkata")
                    .putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY;UNTIL=$until")
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Exception) {
            Log.e("target-calendar", e)
            c.toast("No calendar app found")
        }
    }

    // ---------------- editor ----------------
    fun edit(
        a: Activity,
        existing: JSONObject2?,
        done: () -> Unit,
    ) {
        val z = Zite.data(a)
        val topics = LinkedHashSet<String>(existing?.topics ?: emptyList())
        var title = existing?.title ?: ""
        var end: LocalDate = existing?.end ?: today().plusDays(30)
        var mins = existing?.mins ?: 120
        var at: LocalTime = existing?.at ?: LocalTime.of(6, 0)
        val box = a.vbox()
        val tf = Dlg.field(a, "Target name (e.g. Polity revision)", title)
        box.addView(tf)
        val topicsInfo = a.text("", 12f, 0, T.text2)

        fun refreshInfo() {
            val subs = topics.map { it.substringBefore('|') }.distinct()
            topicsInfo.text = if (topics.isEmpty()) "No topics selected" else "${topics.size} topics  -  " + subs.joinToString(", ") { Syl.short(it) }
        }
        refreshInfo()
        box.addView(topicsInfo, lp(-2, -2, 10))
        box.addView(
            a.pill("Choose topics (colour coded, with PYQ counts)", "grid") {
                PyqUi.pick(a, "Choose topics for this target", null, topics, false, "Add") { sel ->
                    topics.clear()
                    topics.addAll(sel)
                    refreshInfo()
                }
            },
            lp(-2, -2, 8),
        )
        val endBtn = a.pill("Finish by: $end", "calendar") { }
        val minBtn = a.pill("Daily: " + fmtMins(mins), "timer") { }
        val atBtn = a.pill("Reminder: " + "%02d:%02d".format(at.hour, at.minute), "bell") { }
        fun endClock(): String = at.plusMinutes(mins.toLong()).let { "%02d:%02d".format(it.hour, it.minute) }
        val slotEndBtn = a.pill("Slot ends: " + endClock(), "clock") { }
        box.addView(endBtn, lp(-2, -2, 8))
        box.addView(atBtn, lp(-2, -2, 8))
        box.addView(slotEndBtn, lp(-2, -2, 8))
        box.addView(minBtn, lp(-2, -2, 8))
        val slotInfo = a.text("", 11.5f, 2, T.textAccent(T.sage))
        box.addView(slotInfo, lp(-2, -2, 6))
        fun refreshSlot() {
            (slotEndBtn.getChildAt(2) as? android.widget.TextView)?.text = "Slot ends: " + endClock()
            val cl = clash(at, mins, existing?.id)
            slotInfo.text = if (cl == null) "Slot ${"%02d:%02d".format(at.hour, at.minute)} - ${endClock()} is free and will be booked daily" else "Clashes with '${cl.title}' (${"%02d:%02d".format(cl.at.hour, cl.at.minute)} - ${cl.at.plusMinutes(cl.mins.toLong()).let { "%02d:%02d".format(it.hour, it.minute) }}). Next free start: ${nextFree(mins, existing?.id)}"
            slotInfo.setTextColor(if (cl == null) T.textAccent(T.sage) else T.textAccent(T.danger))
        }
        (slotEndBtn.getChildAt(2) as? android.widget.TextView)?.let {
            slotEndBtn.setOnClickListener {
                Dlg.time(a, at.plusMinutes(mins.toLong())) { v ->
                    val e = LocalTime.parse(v)
                    val d = java.time.Duration.between(at, e).toMinutes().toInt()
                    if (d in 15..960) { mins = d; (minBtn.getChildAt(2) as? android.widget.TextView)?.text = "Daily: " + fmtMins(mins); refreshSlot() } else a.toast("End time must be after the start time")
                }
            }
        }
        refreshSlot()
        (endBtn.getChildAt(2) as? android.widget.TextView)?.let { tv ->
            endBtn.setOnClickListener { Dlg.date(a, end) { d -> end = d; tv.text = "Finish by: $d" } }
        }
        (minBtn.getChildAt(2) as? android.widget.TextView)?.let { tv ->
            minBtn.setOnClickListener {
                Dlg.input(a, "Daily study time (hours)", "e.g. 2 or 1.5", "%.1f".format(mins / 60f)) { v ->
                    val h = v.replace(',', '.').toFloatOrNull()
                    if (h != null && h in 0.25f..16f) { mins = (h * 60).toInt(); tv.text = "Daily: " + fmtMins(mins); refreshSlot() }
                }
            }
        }
        (atBtn.getChildAt(2) as? android.widget.TextView)?.let { tv ->
            atBtn.setOnClickListener { Dlg.time(a, at) { s -> at = LocalTime.parse(s); tv.text = "Reminder: $s"; refreshSlot() } }
        }
        if (existing != null) {
            box.addView(
                a.pill("Tick finished topics in syllabus", "checkc") {
                    val open = topics.filter { it !in Syl.doneSet() }
                    if (open.isEmpty()) {
                        a.toast("All topics are already ticked")
                        return@pill
                    }
                    val sel = BooleanArray(open.size)
                    Dlg.builder(a).setTitle("Mark as done")
                        .setMultiChoiceItems(open.map { topicName(it) }.toTypedArray(), sel) { _, i, on -> sel[i] = on }
                        .setPositiveButton("Tick") { _, _ ->
                            open.forEachIndexed { i, k -> if (sel[i]) Syl.toggle(k) }
                            Store.changed()
                            done()
                        }.setNegativeButton("Cancel", null).show()
                },
                lp(-2, -2, 8),
            )
            box.addView(a.text("Un-ticking a topic and deleting a target need the reset password (Settings > Reset and data).", 11f, 0, T.text3), lp(-2, -2, 8))
        }
        fun save(): JSONObject2? {
            title = tf.text.toString().trim()
            if (title.isBlank()) { a.toast("Give the target a name"); return null }
            val cl = clash(at, mins, existing?.id)
            if (cl != null) { a.toast("Slot already booked by '${cl.title}'. Next free start: ${nextFree(mins, existing?.id)}"); return null }
            val arr = JSONArray(topics.toList())
            return if (existing == null) {
                val col = Store.rows(COLL).size % T.palette.size
                JSONObject2(Store.add(COLL, obj("title" to title, "topics" to arr, "end" to end.toString(), "mins" to mins, "at" to "%02d:%02d".format(at.hour, at.minute), "col" to col)))
            } else {
                Store.update(COLL, existing.id) { it.put("title", title); it.put("topics", arr); it.put("end", end.toString()); it.put("mins", mins); it.put("at", "%02d:%02d".format(at.hour, at.minute)); it.remove("fired") }
                all().firstOrNull { it.id == existing.id }
            }
        }
        val b = Dlg.builder(a).setTitle(if (existing == null) "New target" else "Edit target")
        val sv = android.widget.ScrollView(a).apply { setPadding(T.dp(22), T.dp(8), T.dp(22), 0); addView(box) }
        b.setView(sv)
        b.setPositiveButton("Save", null)
        b.setNeutralButton("Save + Google Calendar", null)
        b.setNegativeButton("Cancel", null)
        val dlg = b.create()
        dlg.show()
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (save() != null) { schedule(a); done(); dlg.dismiss() } }
        dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save()?.let { schedule(a); addToCalendar(a, it); done(); dlg.dismiss() } }
    }

    private fun mod(t: LocalTime) = t.hour * 60 + t.minute

    /** Another running target whose daily slot overlaps [at, at+mins). Slots are booked daily until the target ends or is complete. */
    fun clash(at: LocalTime, mins: Int, excludeId: String?): JSONObject2? {
        val s = mod(at)
        val e = s + mins
        return all().firstOrNull { t ->
            t.id != excludeId && !(t.end?.isBefore(today()) ?: false) && !Slot.complete(t) &&
                mod(t.at).let { ts -> ts < e && s < ts + t.mins }
        }
    }

    /** First free start time (HH:MM, 5-minute steps from 04:00) where a slot of [mins] fits. */
    fun nextFree(mins: Int, excludeId: String?): String {
        var m = 4 * 60
        while (m + mins <= 24 * 60) {
            val st = LocalTime.of(m / 60, m % 60)
            if (clash(st, mins, excludeId) == null) return "%02d:%02d".format(st.hour, st.minute)
            m += 5
        }
        return "none today"
    }

    fun remove(
        a: Activity,
        t: JSONObject2,
        done: () -> Unit,
    ) {
        ResetGate.verifyAndBackup(a) {
            Store.remove(COLL, t.id)
            schedule(a)
            done()
        }
    }
}

class TargetReceiver : BroadcastReceiver() {
    override fun onReceive(
        c: Context,
        i: Intent,
    ) {
        val pr = goAsync()
        Thread {
            try {
                when (i.action) {
                    "target.later" -> Slot.reshow(c, i.getStringExtra("tid"))
                    "review.daily" -> { Review.midnight(c); Review.arm(c) }
                    else -> Targets.fire(c)
                }
            } catch (e: Exception) {
                Log.e("target-receiver", e)
            } finally {
                pr.finish()
            }
        }.start()
    }
}

/** Multiple exam countdowns. Collection "exams": id, name, date (yyyy-MM-dd). The main exam stays `exam_date`. */
object Exams {
    class E(val id: String, val name: String, val date: LocalDate)

    fun others(): List<E> =
        Store.rows("exams").mapNotNull {
            try { E(it.optString("id"), it.optString("name"), LocalDate.parse(it.optString("date"))) } catch (e: Exception) { null }
        }.sortedBy { it.date }

    fun daysLeft(e: E): Long = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Asia/Kolkata")), e.date)

    fun add(
        a: Activity,
        done: () -> Unit,
    ) {
        Dlg.input(a, "Exam name", "e.g. UPSC Mains 2027, UP PCS") { name ->
            if (name.isBlank()) return@input
            Dlg.date(a, LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(60)) { d ->
                Store.add("exams", obj("name" to name, "date" to d.toString()))
                done()
            }
        }
    }
}
