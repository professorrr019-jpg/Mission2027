package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reviews: (1) after every saved study session, (2) every midnight for the day that just ended.
 * Topics are marked Complete / Backlog / Revise, daily targets get Done / Partly / Not done with a reason, and short notes are saved.
 * While strict focus is on, the notes are mandatory and Back is blocked until the review is saved.
 */
object Review {
    const val COLL = "reviews"
    const val BACKLOG = "backlog"
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    fun mandatory(): Boolean = Store.b("strict_slot", true)

    fun backlogKeys(): Set<String> = try { Store.rows(BACKLOG).filter { !it.optBoolean("done") && it.optString("type") == "topic" }.map { it.optString("key") }.toSet() } catch (e: Exception) { emptySet() }

    fun backlogMins(): Int = try { Store.rows(BACKLOG).filter { !it.optBoolean("done") && it.optString("type") == "time" }.sumOf { it.optInt("mins") } } catch (e: Exception) { 0 }

    // ---------- session review ----------
    /** Called when a study session was saved. Keeps the details until the review is filled. */
    fun queueSession(subject: String, topic: String, mins: Int, start: Long) {
        try {
            val saved = Store.stringList("slot_keys")
            val keys =
                if (saved.isNotEmpty() && saved.joinToString(" + ") { Targets.topicName(it) } == topic) saved
                else if (topic.contains('|')) listOf(topic) else emptyList()
            Store.setStringList("slot_keys", emptyList())
            val o = JSONObject().put("subject", subject).put("topic", topic).put("mins", mins).put("start", start).put("keys", JSONArray(keys))
            Store.put("review_session", o.toString())
            launch(Store.app, "session")
        } catch (e: Exception) {
            Log.e("review-queue", e)
        }
    }

    fun sessionPending(): JSONObject? = try { Store.s("review_session").takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (e: Exception) { null }

    // ---------- daily review ----------
    private fun hadActivity(d: LocalDate): Boolean =
        try {
            val targets = Targets.all().any { t -> t.end?.let { !it.isBefore(d) } ?: true }
            targets || (Repo.byDaySubject(listOf(d)).firstOrNull()?.values?.sum() ?: 0) > 0
        } catch (e: Exception) {
            false
        }

    private fun reviewed(d: LocalDate): Boolean = Store.rows(COLL).any { it.optString("kind") == "daily" && it.optString("date") == d.toString() }

    /** Date (yesterday, or today after 23:00) whose daily review is still missing. */
    fun dailyDue(): LocalDate? {
        val now = LocalDateTime.now(IST)
        val y = now.toLocalDate().minusDays(1)
        if (!reviewed(y) && hadActivity(y) && Store.s("review_skip_day") != y.toString()) return y
        if (now.hour >= 23 && !reviewed(now.toLocalDate()) && hadActivity(now.toLocalDate()) && Store.s("review_skip_day") != now.toLocalDate().toString()) return now.toLocalDate()
        return null
    }

    fun launch(c: Context, mode: String, date: LocalDate? = null) {
        try {
            c.startActivity(Intent(c, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("mode", mode).putExtra("date", (date ?: LocalDate.now(IST)).toString()))
        } catch (e: Exception) {
            Log.e("review-launch", e)
        }
        notify(c, mode, date)
    }

    fun notify(c: Context, mode: String, date: LocalDate?) {
        try {
            val i = Intent(c, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("mode", mode).putExtra("date", (date ?: LocalDate.now(IST)).toString())
            val pi = PendingIntent.getActivity(c, 4402, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            c.nm().notify(
                4402,
                Notification.Builder(c, CH_INFO).setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentTitle(if (mode == "daily") "Daily review" else "Session review")
                    .setContentText(if (mode == "daily") "Aaj ka target complete hua? Backlog bhar do" else "Topics complete hue ya backlog? Notes save karo")
                    .setContentIntent(pi).setAutoCancel(true).build(),
            )
        } catch (e: Exception) {
            Log.e("review-notify", e)
        }
    }

    /** Midnight alarm: day just ended. */
    fun midnight(c: Context) {
        val d = dailyDue() ?: return
        launch(c, "daily", d)
    }

    /** Called when the app comes to the front: shows an open review at most every 20 minutes. */
    fun promptIfDue(c: Context) {
        val last = Store.l("review_prompted_at", 0L)
        if (System.currentTimeMillis() - last < 20 * 60000L) return
        val mode = if (sessionPending() != null) "session" else if (dailyDue() != null) "daily" else return
        Store.put("review_prompted_at", System.currentTimeMillis())
        try {
            c.startActivity(Intent(c, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("mode", mode).putExtra("date", (dailyDue() ?: LocalDate.now(IST)).toString()))
        } catch (e: Exception) {
            Log.e("review-prompt", e)
        }
    }

    fun nextMidnight(): Long = LocalDate.now(IST).plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() + 20_000L

    fun arm(c: Context) {
        try {
            val pi = PendingIntent.getBroadcast(c, 4401, Intent(c, TargetReceiver::class.java).setAction("review.daily"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Sched.setAt(c, nextMidnight(), pi)
        } catch (e: Exception) {
            Log.e("review-arm", e)
        }
    }

    /** Read-only log of the last reviews. */
    fun log(a: Activity) {
        val box = a.vbox()
        box.padDp(18, 6, 18, 6)
        val rows = Store.rows(COLL).sortedByDescending { it.optLong("updatedAt") }.take(150)
        if (rows.isEmpty()) box.addView(a.text("No reviews yet. They appear after you save a study session and every midnight.", 13f, 0, T.text2))
        box.addView(a.text("Backlog now: ${backlogKeys().size} topics, ${backlogMins()} min", 12.5f, 2, T.textAccent(T.amber)), lp(-2, -2, 0))
        rows.forEach { r ->
            val daily = r.optString("kind") == "daily"
            val col = if (daily) T.plum else T.teal
            val card = a.vbox()
            card.background = T.round(T.withAlpha(col, 0.12f), 12)
            card.padDp(10, 8, 10, 8)
            val h = a.hbox()
            h.addView(PyqUi.chip(a, if (daily) "Daily" else "Session", col, true))
            h.addView(a.text("  " + r.optString("date") + "  " + r.optString("summary"), 12f, 2, T.text))
            card.addView(h)
            if (r.optString("details").isNotBlank()) card.addView(a.text(r.optString("details"), 12f, 0, T.text2), lp(-1, -2, 4))
            box.addView(card, lp(-1, -2, 8))
        }
        Dlg.builder(a).setTitle("Review log").setView(ScrollView(a).apply { addView(box) }).setPositiveButton("Close", null).setNeutralButton("Add study hours") { _, _ -> ManualHours.add(a) }.setNegativeButton("Study log") { _, _ -> ManualHours.sessionLog(a) }.show()
    }
}

/** Colour coded review screen (session or daily). Back is blocked while the notes are mandatory. */
class ReviewActivity : Activity() {
    private val status = LinkedHashMap<String, String>()
    private lateinit var mode: String
    private lateinit var date: LocalDate

    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        T.init(applicationContext)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(T.page))
        mode = intent.getStringExtra("mode") ?: "session"
        date = try { LocalDate.parse(intent.getStringExtra("date")) } catch (e: Exception) { LocalDate.now() }
        render()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        mode = i.getStringExtra("mode") ?: mode
        date = try { LocalDate.parse(i.getStringExtra("date")) } catch (e: Exception) { date }
        status.clear()
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Review.mandatory()) toast("Fill the review first (details are mandatory in strict focus)") else finish()
    }

    private fun seg(label: String, color: Int, on: Boolean, f: () -> Unit): TextView =
        PyqUi.chip(this, label, color, on).apply {
            textSize = 12.5f
            padDp(12, 8, 12, 8)
            tap(false) { f() }
        }

    private fun render() {
        val box = vbox()
        box.padDp(22, 28, 22, 24)
        val details: EditText = Dlg.field(this, "", "", false, true)
        details.minLines = 3
        var reason = ""
        val title = if (mode == "daily") "Daily review - $date" else "Session review"
        box.addView(text(title, 22f, 3, T.text))
        val keys = ArrayList<String>()
        var summary = ""
        var tid = ""
        val saveRow = JSONObject()
        if (mode == "session") {
            val s = Review.sessionPending()
            if (s == null) {
                finish()
                return
            }
            val col = Pyq.colorOf(s.optString("subject"))
            summary = "${s.optInt("mins")} min  ${Syl.short(s.optString("subject"))}"
            box.addView(PyqUi.chip(this, summary, col, true), lp(-2, -2, 8))
            val ka = s.optJSONArray("keys") ?: JSONArray()
            for (i in 0 until ka.length()) keys.add(ka.getString(i))
            box.addView(text("Kya ye topics complete hue? Har topic ke liye ek chuno.", 13f, 1, T.text2), lp(-2, -2, 12))
            val rowsBox = vbox()
            fun draw() {
                rowsBox.removeAllViews()
                keys.forEach { k ->
                    val m = Pyq.micro(k)
                    val c = Pyq.colorOf(k.substringBefore('|'))
                    val card = vbox()
                    card.background = T.round(T.withAlpha(c, 0.12f), 12)
                    card.padDp(10, 8, 10, 10)
                    val h = hbox()
                    h.addView(text(Targets.topicName(k), 13.5f, 2, T.text, 2), LinearLayout.LayoutParams(0, -2, 1f))
                    if (m != null) h.addView(PyqUi.chip(this, "${m.last15} Q", Pyq.tierColor(m.tier), true))
                    card.addView(h)
                    val st = status[k]
                    val r = hbox()
                    r.addView(seg("Complete", T.sage, st == "done") { status[k] = "done"; draw() }, lpm(-2, -2, 0, 0, 6, 0))
                    r.addView(seg("Backlog", T.amber, st == "backlog") { status[k] = "backlog"; draw() }, lpm(-2, -2, 0, 0, 6, 0))
                    r.addView(seg("Revise again", T.plum, st == "revise") { status[k] = "revise"; draw() })
                    card.addView(chipRow(r), lp(-1, -2, 8))
                    rowsBox.addView(card, lp(-1, -2, 8))
                }
            }
            draw()
            box.addView(rowsBox)
            if (keys.isEmpty()) box.addView(text("Free session: no topic was chosen.", 12f, 0, T.text3), lp(-2, -2, 6))
        } else {
            val studied = Repo.byDaySubject(listOf(date)).firstOrNull() ?: emptyMap()
            val targets = Targets.all().filter { t -> t.end?.let { !it.isBefore(date) } ?: true }
            var short = 0
            targets.forEach { t ->
                val subs = t.topics.map { it.substringBefore('|').lowercase() }.toSet()
                val got = studied.filter { it.key.lowercase() in subs }.values.sum()
                short += (t.mins - got).coerceAtLeast(0)
                val col = t.color
                val card = vbox()
                card.background = T.round(T.withAlpha(col, 0.14f), 12)
                card.padDp(10, 8, 10, 10)
                card.addView(text(t.title, 14f, 2, T.text))
                card.addView(text("$got of ${t.mins} min studied", 12f, 1, T.textAccent(col)), lp(-2, -2, 2))
                val bar = hbox(false)
                val p = (got.toFloat() / t.mins.coerceAtLeast(1)).coerceIn(0f, 1f)
                bar.addView(android.view.View(this).apply { background = T.round(col, 4) }, LinearLayout.LayoutParams(0, T.dp(8), p.coerceAtLeast(0.001f)))
                bar.addView(android.view.View(this).apply { background = T.round(T.withAlpha(col, 0.25f), 4) }, LinearLayout.LayoutParams(0, T.dp(8), (1f - p).coerceAtLeast(0.001f)))
                card.addView(bar, lp(-1, -2, 6))
                box.addView(card, lp(-1, -2, 10))
            }
            tid = targets.joinToString(",") { it.id }
            summary = "$short min short"
            box.addView(text("Aaj ka target complete hua?", 15f, 2, T.text), lp(-2, -2, 14))
            val done = hbox()
            fun drawAns() {
                done.removeAllViews()
                val a = status["day"]
                done.addView(seg("Yes, complete", T.sage, a == "yes") { status["day"] = "yes"; drawAns() }, lpm(-2, -2, 0, 0, 6, 0))
                done.addView(seg("Partly", T.amber, a == "part") { status["day"] = "part"; drawAns() }, lpm(-2, -2, 0, 0, 6, 0))
                done.addView(seg("Not done", T.danger, a == "no") { status["day"] = "no"; drawAns() })
            }
            drawAns()
            box.addView(chipRow(done), lp(-1, -2, 8))
            box.addView(text("Agar backlog hai to reason:", 12.5f, 1, T.text2), lp(-2, -2, 12))
            val rs = hbox()
            fun drawReason() {
                rs.removeAllViews()
                listOf("Tired" to T.teal, "Distracted" to T.danger, "Time shortage" to T.amber, "Health" to T.plum, "Other" to T.slate).forEach { (l, c) ->
                    rs.addView(seg(l, c, reason == l) { reason = l; drawReason() }, lpm(-2, -2, 0, 0, 6, 0))
                }
            }
            drawReason()
            box.addView(chipRow(rs), lp(-1, -2, 6))
            saveRow.put("short", short)
        }
        box.addView(text(if (Review.mandatory()) "Details (mandatory, min 15 letters)" else "Details", 13f, 2, T.text), lp(-2, -2, 14))
        details.hint = if (mode == "daily") "Aaj kya padha, kya baaki raha, kal ka plan" else "Kya cover kiya, key points, doubts"
        box.addView(details, lp(-1, -2, 6))
        val row = hbox()
        row.addView(
            pill("Save review", "checkc", true) {
                val d = details.text.toString().trim()
                if (Review.mandatory() && d.length < 15) {
                    toast("Details are mandatory: write at least 15 letters")
                    return@pill
                }
                if (mode == "session") {
                    if (keys.any { status[it] == null }) {
                        toast("Choose Complete, Backlog or Revise for every topic")
                        return@pill
                    }
                } else if (status["day"] == null) {
                    toast("Tell if today's target was complete")
                    return@pill
                } else if (status["day"] != "yes" && reason.isBlank()) {
                    toast("Choose a reason for the backlog")
                    return@pill
                }
                save(keys, d, summary, reason, tid, saveRow.optInt("short"))
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        if (!Review.mandatory()) {
            row.addView(pill("Skip", null, false) {
                if (mode == "session") Store.put("review_session", "") else Store.put("review_skip_day", date.toString())
                finish()
            })
        }
        box.addView(row, lp(-2, -2, 16))
        setContentView(ScrollView(this).apply { addView(box); isFillViewport = true })
    }

    /** A completed or "revise" topic is added to the Revise today card, so reviews and revision are one chain. */
    private fun queueRevision(k: String) {
        try {
            val name = Targets.topicName(k)
            if (Store.rows("revisions").none { it.optString("topic") == name }) Repo.addRevision(name)
        } catch (e: Exception) {
            Log.e("review-revision", e)
        }
    }

    private fun save(keys: List<String>, details: String, summary: String, reason: String, tid: String, short: Int) {
        try {
            val now = System.currentTimeMillis()
            val st = JSONObject()
            if (mode == "session") {
                keys.forEach { k ->
                    val s = status[k] ?: return@forEach
                    st.put(k, s)
                    when (s) {
                        "done" -> {
                            if (k !in Syl.doneSet()) Syl.toggle(k)
                            queueRevision(k)
                            Store.rows(Review.BACKLOG).filter { it.optString("key") == k && !it.optBoolean("done") }.forEach { r -> Store.update(Review.BACKLOG, r.optString("id")) { it.put("done", true) } }
                        }
                        "backlog", "revise" -> if (s == "revise") queueRevision(k)
                    }
                    when (s) {
                        "backlog", "revise" -> if (Store.rows(Review.BACKLOG).none { it.optString("key") == k && !it.optBoolean("done") }) {
                            Store.add(Review.BACKLOG, obj("type" to "topic", "key" to k, "date" to date.toString(), "why" to s, "updatedAt" to now))
                        }
                    }
                }
                Store.put("review_session", "")
                Store.changed()
            } else {
                val a = status["day"] ?: "yes"
                st.put("day", a)
                if (a != "yes" && short > 0) Store.add(Review.BACKLOG, obj("type" to "time", "mins" to short, "date" to date.toString(), "why" to reason, "tid" to tid, "updatedAt" to now))
                if (a == "yes") Store.rows(Review.BACKLOG).filter { it.optString("type") == "time" && !it.optBoolean("done") }.forEach { r -> Store.update(Review.BACKLOG, r.optString("id")) { it.put("done", true) } }
            }
            Store.add(
                Review.COLL,
                obj("kind" to mode, "date" to date.toString(), "summary" to summary + (if (reason.isNotBlank()) " ($reason)" else ""), "details" to details, "status" to st.toString(), "updatedAt" to now),
            )
            Store.changed()
            toast("Review saved")
        } catch (e: Exception) {
            Log.e("review-save", e)
            toast("Could not save: ${e.message}")
            return
        }
        finish()
    }
}
