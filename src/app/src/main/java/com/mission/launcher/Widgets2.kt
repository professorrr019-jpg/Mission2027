package com.mission.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Real PYQ counts (year -> subject -> questions) read from the Zite "PYQ Questions" table. Used until a live sync replaces them. */
object PyqStatic {
    val subjects =
        listOf(
            "Agriculture & Rural Development", "Art & Culture", "Current Affairs & Miscellaneous", "Economy & Finance", "Environment & Ecology",
            "Geography & Maps", "History & National Movement", "International Relations & Institutions", "Polity & Governance", "Science & Technology",
        )
    private val rows =
        mapOf(
            2007 to "4,9,27,8,5,20,13,16,11,34", 2008 to "4,9,22,11,9,29,15,6,10,35", 2009 to "1,17,29,22,8,20,10,13,11,19", 2010 to "7,1,16,34,24,17,15,2,10,24",
            2011 to "5,1,0,21,20,7,10,2,12,22", 2012 to "4,4,0,16,19,9,18,0,17,13", 2013 to "6,8,0,17,18,13,9,0,17,12", 2014 to "6,10,0,11,21,12,10,4,11,15",
            2015 to "5,4,2,18,18,11,11,8,14,9", 2016 to "5,4,2,18,22,5,11,14,7,12", 2017 to "4,4,3,20,17,6,8,6,24,8", 2018 to "7,5,3,17,14,8,16,3,15,12",
            2019 to "4,3,0,21,19,7,11,5,15,15", 2020 to "13,1,0,15,17,5,18,3,16,12", 2021 to "8,4,3,15,19,5,15,2,18,11", 2022 to "5,2,2,16,14,11,12,8,13,17",
            2023 to "4,5,3,17,16,16,6,8,16,9", 2024 to "2,3,3,14,17,17,7,7,20,10", 2025 to "2,1,3,17,15,13,14,7,13,15", 2026 to "2,8,6,17,9,8,13,10,12,15",
        )
    val data: Map<Int, Map<String, Int>> by lazy {
        rows.mapValues { (_, v) -> v.split(',').mapIndexed { i, n -> subjects[i] to n.toInt() }.toMap() }
    }
    val code =
        mapOf(
            subjects[0] to "Ag", subjects[1] to "Ar", subjects[2] to "CA", subjects[3] to "Ec", subjects[4] to "En",
            subjects[5] to "Ge", subjects[6] to "Hi", subjects[7] to "IR", subjects[8] to "Po", subjects[9] to "ST",
        )
}

private fun blend(
    bg: Int,
    fg: Int,
    a: Float,
): Int {
    val r = (android.graphics.Color.red(bg) * (1 - a) + android.graphics.Color.red(fg) * a).toInt()
    val g = (android.graphics.Color.green(bg) * (1 - a) + android.graphics.Color.green(fg) * a).toInt()
    val b = (android.graphics.Color.blue(bg) * (1 - a) + android.graphics.Color.blue(fg) * a).toInt()
    return android.graphics.Color.rgb(r, g, b)
}

/** Year x subject grid. Colour = subject colour, strength = number of questions that year. */
class PyqGrid(c: Context, val years: List<Int>, val data: Map<Int, Map<String, Int>>, val subjects: List<String>) : View(c) {

    private val k0 = T.ck
    private val t0 = T.ct

    override fun onMeasure(
        wSpec: Int,
        hSpec: Int,
    ) {
        val a = T.ck
        val b = T.ct
        T.ck = k0
        T.ct = t0
        try {
            measure0(wSpec, hSpec)
        } finally {
            T.ck = a
            T.ct = b
        }
    }

    override fun onDraw(cv: Canvas) {
        val a = T.ck
        val b = T.ct
        T.ck = k0
        T.ct = t0
        try {
            draw0(cv)
        } finally {
            T.ck = a
            T.ct = b
        }
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rowH get() = T.dp(22).toFloat()
    private val labelW get() = T.dp(40).toFloat()
    private val maxV = (years.flatMap { y -> subjects.map { s -> data[y]?.get(s) ?: 0 } }.maxOrNull() ?: 1).coerceAtLeast(1)

    private fun measure0(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(MeasureSpec.getSize(w), (rowH * (years.size + 1)).toInt() + T.dp(2))

    private fun draw0(cv: Canvas) {
        val cw = (width - labelW) / subjects.size
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = T.dp(10).toFloat()
        subjects.forEachIndexed { i, s ->
            p.color = T.textAccent(T.subjectColor(s))
            cv.drawText(PyqStatic.code[s] ?: s.take(2), labelW + cw * i + cw / 2, rowH * 0.7f, p)
        }
        years.forEachIndexed { r, y ->
            val top = rowH * (r + 1)
            p.textAlign = Paint.Align.LEFT
            p.color = T.text2
            p.typeface = Typeface.DEFAULT
            p.textSize = T.dp(10.5f.toInt()).toFloat()
            cv.drawText(y.toString(), 0f, top + rowH * 0.68f, p)
            subjects.forEachIndexed { i, s ->
                val v = data[y]?.get(s) ?: 0
                val rect = RectF(labelW + cw * i + 1.5f, top + 1.5f, labelW + cw * (i + 1) - 1.5f, top + rowH - 1.5f)
                p.color = if (v == 0) T.inset else blend(T.cardSolid, T.subjectColor(s), 0.18f + 0.82f * v / maxV)
                cv.drawRoundRect(rect, T.dp(4).toFloat(), T.dp(4).toFloat(), p)
                if (v > 0) {
                    p.color = if (v * 1f / maxV > 0.55f) T.contentColor(T.subjectColor(s)) else T.text
                    p.textAlign = Paint.Align.CENTER
                    p.typeface = Typeface.DEFAULT_BOLD
                    p.textSize = T.dp(10).toFloat()
                    cv.drawText(v.toString(), rect.centerX(), rect.centerY() + T.dp(3.5f.toInt()), p)
                }
            }
        }
    }
}

/** GitHub-style study heatmap: columns are weeks (Mon..Sun rows), colour = minutes studied. */
class StudyHeat(c: Context, val weeks: Int, val minutes: Map<LocalDate, Int>, val goal: Int) : View(c) {

    private val k0 = T.ck
    private val t0 = T.ct

    override fun onMeasure(
        wSpec: Int,
        hSpec: Int,
    ) {
        val a = T.ck
        val b = T.ct
        T.ck = k0
        T.ct = t0
        try {
            measure0(wSpec, hSpec)
        } finally {
            T.ck = a
            T.ct = b
        }
    }

    override fun onDraw(cv: Canvas) {
        val a = T.ck
        val b = T.ct
        T.ck = k0
        T.ct = t0
        try {
            draw0(cv)
        } finally {
            T.ck = a
            T.ct = b
        }
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cell get() = T.dp(14).toFloat()
    private val gap get() = T.dp(3).toFloat()

    private fun measure0(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(MeasureSpec.getSize(w), ((cell + gap) * 7).toInt() + T.dp(2))

    private fun draw0(cv: Canvas) {
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val first = monday.minusWeeks((weeks - 1).toLong())
        val step = cell + gap
        val shown = ((width / step).toInt()).coerceAtMost(weeks)
        val startWeek = weeks - shown
        for (w in 0 until shown) for (d in 0 until 7) {
            val day = first.plusWeeks((startWeek + w).toLong()).plusDays(d.toLong())
            if (day.isAfter(today)) continue
            val m = minutes[day] ?: 0
            p.color = if (m == 0) T.inset else blend(T.cardSolid, T.sage, (0.25f + 0.75f * (m.toFloat() / goal).coerceAtMost(1f)))
            cv.drawRoundRect(RectF(w * step, d * step, w * step + cell, d * step + cell), T.dp(3).toFloat(), T.dp(3).toFloat(), p)
        }
    }
}

object Cards2 {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun bar(
        c: Context,
        f: Float,
        col: Int,
    ): View {
        val w = LinearLayout(c).apply { background = T.round(T.inset, 4) }
        val x = f.coerceIn(0f, 1f)
        w.addView(View(c).apply { background = T.round(col, 4) }, LinearLayout.LayoutParams(0, T.dp(6), x + 0.0001f))
        w.addView(View(c), LinearLayout.LayoutParams(0, T.dp(6), (1f - x) + 0.0001f))
        return w
    }

    // ---------------- targets ----------------
    fun targets(h: Host): View {
        val c = h.act
        val card = Card(c)
        card.addView(c.cardHeader("target", "Targets", T.terra, c.iconBtn("plus", T.text2, 32) { Targets.edit(c, null) { h.refresh() } }))
        val list = Targets.all()
        if (list.isEmpty()) {
            card.addView(c.emptyNote("No target yet. Tap + : pick subject and topics, finish date, daily hours and reminder time."), lp(-2, -2, 6))
            return card
        }
        list.take(8).forEach { t ->
            val (done, total) = Targets.progress(t)
            val dl = Targets.daysLeft(t)
            val (st, stCol) = Targets.status(t)
            val row = c.vbox().apply { padDp(0, 8, 0, 6) }
            val top = c.hbox()
            top.addView(View(c).apply { background = T.round(t.color, 5) }, LinearLayout.LayoutParams(T.dp(10), T.dp(10)))
            top.addView(c.text(t.title, 14f, 2), lpm(-2, -2, 8, 0, 0, 0, 1f))
            top.addView(c.text(if (dl >= 0) "$dl d" else "late", 14f, 3, T.textAccent(if (dl in 0..3) T.danger else t.color)).tabular())
            row.addView(top)
            row.addView(bar(c, if (total == 0) 0f else done.toFloat() / total, t.color), lp(-1, -2, 6))
            row.addView(c.text("$done/$total topics  -  " + fmtMins(t.mins) + "/day at " + "%02d:%02d".format(t.at.hour, t.at.minute), 11f, 0, T.text3), lp(-2, -2, 4))
            row.addView(c.text(st, 11f, 1, T.textAccent(stCol)), lp(-2, -2, 1))
            row.addView(c.text("Today ${Slot.studiedToday(t)} of ${t.mins} min studied", 11f, 0, T.text3), lp(-2, -2, 1))
            val subs = Slot.pending(c, t).groupBy { it.substringBefore('|') }
            if (subs.isNotEmpty()) {
                val chips = c.hbox()
                subs.entries.take(4).forEach { (sName, ks) ->
                    chips.addView(c.pill(Syl.short(sName) + " " + ks.size, "timer", false, t.color) { Slot.pickTopic(c, t, sName) }, lpm(-2, -2, 0, 0, 6, 0))
                }
                row.addView(chips, lp(-2, -2, 6))
            }
            row.setOnClickListener { detail(h, t) }
            card.addView(row)
        }
        card.addView(c.text("Tap a target to edit, tick topics or add it to Google Calendar", 10.5f, 0, T.text3), lp(-2, -2, 4))
        return card
    }

    private fun detail(
        h: Host,
        t: Targets.JSONObject2,
    ) {
        val a = h.act
        val (done, total) = Targets.progress(t)
        val d = Syl.doneSet()
        val box = a.vbox()
        box.addView(a.text("${Targets.daysLeft(t)} days left  -  finish by ${t.end}", 13f, 2))
        box.addView(a.text("$done of $total topics done", 12f, 0, T.text2), lp(-2, -2, 2))
        t.topics.forEach { k ->
            val ok = k in d
            box.addView(a.text((if (ok) "[x] " else "[ ] ") + Targets.topicName(k) + "  (" + Syl.short(k.substringBefore('|')) + ")", 12.5f, 1, if (ok) T.textAccent(T.sage) else T.text), lp(-2, -2, 4))
        }
        val b = Dlg.builder(a).setTitle(t.title).setView(android.widget.ScrollView(a).apply { setPadding(T.dp(22), T.dp(8), T.dp(22), 0); addView(box) })
        b.setPositiveButton("Edit") { _, _ -> Targets.edit(a, t) { h.refresh() } }
        b.setNeutralButton("Calendar") { _, _ -> Targets.addToCalendar(a, t) }
        b.setNegativeButton("Delete") { _, _ -> Targets.remove(a, t) { h.refresh() } }
        b.show()
    }

    // ---------------- PYQ year x subject ----------------
    fun pyqMap(h: Host): View {
        val c = h.act
        val card = Card(c)
        card.addView(c.cardHeader("grid", "PYQ by year and subject", T.plum))
        val z = Zite.data(c)
        val data = z.pyqYears.filterKeys { it in 2007..2026 }.ifEmpty { PyqStatic.data }
        var all = false
        val holder = c.vbox()

        fun render() {
            holder.removeAllViews()
            val years = data.keys.sortedDescending().let { if (all) it else it.take(10) }
            holder.addView(PyqGrid(c, years, data, PyqStatic.subjects), lp(-1, -2, 4))
            // 5-year average per subject = what to prioritise
            val last5 = data.keys.sortedDescending().take(5)
            val avg = PyqStatic.subjects.map { s -> s to last5.sumOf { data[it]?.get(s) ?: 0 } / 5f }.sortedByDescending { it.second }
            holder.addView(c.text("Last 5 yrs avg/yr: " + avg.take(5).joinToString("  ") { (PyqStatic.code[it.first] ?: "") + " " + "%.1f".format(it.second) }, 11.5f, 2, T.text2), lp(-2, -2, 8))
            holder.addView(
                c.text(
                    PyqStatic.subjects.joinToString("   ") { (PyqStatic.code[it] ?: "") + "=" + Syl.short(it) },
                    10f,
                    0,
                    T.text3,
                ),
                lp(-2, -2, 4),
            )
            holder.addView(c.pill(if (all) "Show last 10 years" else "Show all years (2007-2026)", "chart") { all = !all; render() }, lp(-2, -2, 8))
        }
        render()
        card.addView(holder)
        return card
    }

    // ---------------- study heatmap ----------------
    fun heat(h: Host): View {
        val c = h.act
        val card = Card(c)
        card.addView(c.cardHeader("chart", "Study streak calendar", T.sage))
        val mins = HashMap<LocalDate, Int>()
        Store.rows("sessions").forEach { s -> mins.merge(Repo.dayOf(s.optLong("start")), s.optInt("mins"), Int::plus) }
        val goal = Store.i("study_goal_min", 360).coerceAtLeast(60)
        card.addView(StudyHeat(c, 20, mins, goal), lp(-1, -2, 8))
        val total = mins.values.sum()
        card.addView(c.text("Streak ${Repo.streak()} days  -  ${fmtMins(total)} studied in total  -  darker = closer to your ${fmtMins(goal)} goal", 11f, 0, T.text3), lp(-2, -2, 6))
        return card
    }

    // ---------------- weather / sky ----------------
    private fun aqiFetch(c: Context) {
        try {
            val city = Weather.city()
            val u = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${city.latitude}&longitude=${city.longitude}&current=us_aqi,pm2_5&timezone=Asia%2FKolkata"
            val cn = URL(u).openConnection() as HttpURLConnection
            cn.connectTimeout = 7000
            cn.readTimeout = 7000
            val j = JSONObject(cn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            Store.put("aqi_us", j.optInt("us_aqi", -1))
            Store.put("aqi_pm25", j.optDouble("pm2_5", 0.0).toFloat().toString())
            Store.put("aqi_at", System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("aqi", e)
        }
    }

    private fun aqiLabel(v: Int) =
        when {
            v < 0 -> "n/a"
            v <= 50 -> "Good"
            v <= 100 -> "Moderate"
            v <= 150 -> "Unhealthy for sensitive"
            v <= 200 -> "Unhealthy"
            v <= 300 -> "Very unhealthy"
            else -> "Hazardous"
        }

    fun weather(h: Host): View {
        val c = h.act
        val card = Card(c)
        val city = Weather.label()
        card.addView(c.cardHeader("cloud", "$city sky", T.slate, c.iconBtn("refresh", T.text2, 32) { reload(h, true) }))
        val w = Weather.cached()
        if (w == null) {
            card.addView(c.emptyNote("No weather yet. Connect to the internet and tap refresh."), lp(-2, -2, 6))
            reload(h, false)
            return card
        }
        val row = c.hbox()
        row.addView(c.text("%.0f".format(w.temperatureC) + "°", 40f, 3).tabular())
        val side = c.vbox()
        side.addView(c.text(Weather.condition(w.code), 15f, 2))
        side.addView(c.text("Humidity ${w.humidity}%  -  Wind ${"%.0f".format(w.windSpeedKmh)} km/h", 11.5f, 0, T.text2))
        row.addView(side, lpm(-2, -2, 12, 0, 0, 0, 1f))
        card.addView(row, lp(-1, -2, 6))
        val fmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
        fun clock(ms: Long) = if (ms > 0) Instant.ofEpochMilli(ms).atZone(IST).format(fmt) else "--"
        val today = LocalDate.now(IST)
        val ill = (MoonMath.illumination(today) * 100).toInt()
        card.addView(c.text("Sunrise ${clock(w.sunriseEpoch)}   Sunset ${clock(w.sunsetEpoch)}", 12f, 1, T.text), lp(-2, -2, 8))
        card.addView(c.text("Moon ${moonName(MoonMath.phase(today))}  $ill% lit  -  visibility ${"%.1f".format(w.visibilityM / 1000)} km  -  cloud ${w.cloudCover}%", 12f, 0, T.text2), lp(-2, -2, 3))
        val aqi = Store.i("aqi_us", -1)
        if (aqi >= 0) {
            val col = if (aqi <= 100) T.sage else if (aqi <= 200) T.amber else T.danger
            card.addView(c.text("AQI $aqi (${aqiLabel(aqi)})  PM2.5 ${Store.s("aqi_pm25")}", 12f, 2, T.textAccent(col)), lp(-2, -2, 3))
        }
        val advice =
            when {
                w.visibilityM < 1000 || w.code == 45 || w.code == 48 -> "Dense fog: keep the morning walk short, wear a mask and a layer."
                w.precipitationMm > 0.2 || w.code in 51..67 || w.code in 80..99 -> "Rain: carry an umbrella; water goal stays the same."
                aqi > 200 -> "Poor air: study indoors, skip the outdoor walk."
                w.temperatureC >= 38 -> "Very hot: drink 500 ml extra water today."
                else -> "Good conditions for a short walk break."
            }
        card.addView(c.text(advice, 11.5f, 1, T.text3), lp(-2, -2, 6))
        card.addView(c.text("Updated " + Weather.lastFetchedLabel() + "  -  Open-Meteo model data", 10.5f, 0, T.text3), lp(-2, -2, 3))
        reload(h, false)
        return card
    }

    private fun moonName(phase: Double): String =
        when {
            phase < 0.03 || phase > 0.97 -> "New"
            phase < 0.22 -> "Waxing crescent"
            phase < 0.28 -> "First quarter"
            phase < 0.47 -> "Waxing gibbous"
            phase < 0.53 -> "Full"
            phase < 0.72 -> "Waning gibbous"
            phase < 0.78 -> "Last quarter"
            else -> "Waning crescent"
        }

    @Volatile private var reloading = false

    private fun reload(
        h: Host,
        force: Boolean,
    ) {
        val c = h.act
        val stale = System.currentTimeMillis() - Store.l("aqi_at", 0L) > 30 * 60000L
        if (reloading || (!force && !stale)) return
        reloading = true
        Store.put("aqi_at", System.currentTimeMillis()) // rate-limit attempts, also when offline
        Thread {
            try {
                Weather.refreshIfNeeded(c, force = force)
                aqiFetch(c)
            } finally {
                reloading = false
            }
            c.runOnUiThread { if (force || stale) h.refresh() }
        }.start()
    }
}
