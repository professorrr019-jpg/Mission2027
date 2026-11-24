package com.mission.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import java.time.LocalDate
import java.time.LocalTime

/** Real implementations behind the optional features (answer timer, mock overlay, slot templates, free slots, workspaces ...). */
object Plus {
    private fun today(): LocalDate = Repo.dayOf(System.currentTimeMillis())

    private fun fmt(s: Long) = "%02d:%02d".format(s / 60, s % 60)

    // ---- Mains answer timer ----
    fun answerTimer(c: Context) {
        Dlg.choose(c, "Answer timer", listOf("7 minutes (GS 10 marks)", "11 minutes (GS 15 marks)", "15 minutes (long answer)", "20 minutes (Essay outline)")) { i ->
            val mins = intArrayOf(7, 11, 15, 20)[i]
            val big = c.text(fmt(mins * 60L), 40f, 3, T.accent)
            val box = c.vbox()
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.addView(big)
            box.addView(c.text("Write one answer. Intro, 3 to 4 points, conclusion.", 12f, 0, T.text3), lp(-2, -2, 8))
            var ct: CountDownTimer? = null
            ct =
                object : CountDownTimer(mins * 60000L, 1000) {
                    override fun onTick(ms: Long) {
                        big.text = fmt(ms / 1000)
                    }

                    override fun onFinish() {
                        big.text = "Time up"
                        try {
                            (c.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createOneShot(600, 255))
                        } catch (e: Exception) {
                        }
                    }
                }.start()
            Dlg.view(c, "Answer timer", box, "Done, count it") {
                ct?.cancel()
                Store.put("tkc_answers_" + today(), Toolkit.count("answers") + 1)
                c.toast("Answer counted")
            }
        }
    }

    // ---- Slot templates ----
    private val TEMPLATES =
        listOf(
            "Full study day" to listOf("06:00 Polity / GS block 1 (2 h)", "09:30 Optional: Philosophy (2 h)", "14:00 Current affairs and notes (1.5 h)", "17:00 PYQ or MCQ practice (1 h)", "20:00 Revision (1 h)", "22:00 Plan tomorrow (10 min)"),
            "Light day" to listOf("07:00 One subject chapter (1.5 h)", "11:00 Newspaper and editorial (1 h)", "19:00 Revision (45 min)"),
            "Mock test day" to listOf("09:00 Full-length mock (2 h)", "11:30 Check answers and log mistakes (1.5 h)", "16:00 Revise weak topics (1.5 h)", "20:00 Mistake log review (30 min)"),
            "Revision day" to listOf("06:00 Revise notes round 1 (2 h)", "10:00 PYQ of the same topics (1.5 h)", "15:00 Flashcards and mnemonics (1 h)", "20:00 Quick CA revision (45 min)"),
        )

    fun slotTemplates(c: Context) {
        Dlg.choose(c, "Add to today's plan", TEMPLATES.map { it.first }) { i ->
            TEMPLATES[i].second.forEach {
                Store.add("tasks", obj("date" to today().toString(), "text" to it.substring(6), "done" to false, "time" to it.take(5)))
            }
            Store.changed()
            c.toast("${TEMPLATES[i].second.size} tasks added to today")
        }
    }

    // ---- Focus presets ----
    fun focusPresets(c: Context) {
        val p = listOf("Pomodoro 25 min" to 25, "Deep work 50 min" to 50, "Long block 90 min" to 90, "Quick 15 min" to 15)
        Dlg.choose(c, "Start a focus session", p.map { it.first }) { i ->
            if (Focus.active()) {
                c.toast("A focus session is already running")
            } else {
                Focus.start(c, p[i].second, "Focus")
                c.toast("Focus started for ${p[i].second} min")
            }
        }
    }

    // ---- Mock overlay ----
    fun mockOverlay(c: Context) {
        if (!c.canOverlay()) {
            c.toast("Allow 'Display over other apps' for Mission")
            try {
                c.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
            return
        }
        Dlg.choose(c, "Floating mock timer", listOf("GS Paper 1: 120 min", "CSAT: 120 min", "Sectional: 60 min", "Half test: 30 min")) { i ->
            val total = intArrayOf(120, 120, 60, 30)[i] * 60L
            val end = System.currentTimeMillis() + total * 1000
            val tv = c.text(fmt(total), 15f, 3, T.contentColor(T.accent)).apply { padDp(12, 7, 12, 7) }
            val h = Handler(Looper.getMainLooper())
            val tick =
                object : Runnable {
                    override fun run() {
                        val left = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        tv.text = if (left > 0) (if (left >= 3600) "%d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60) else fmt(left)) else "TIME UP"
                        if (left > 0) h.postDelayed(this, 1000)
                    }
                }
            tv.background = T.round(T.accent, 16)
            tv.setOnLongClickListener {
                h.removeCallbacks(tick)
                Overlay.remove(c, "mocktimer")
                true
            }
            val pr = Overlay.params().apply { x = T.dp(20); y = T.dp(60) }
            Overlay.show(c, "mocktimer", tv, pr)
            Overlay.draggable(c, tv, pr, "mocktimer")
            h.post(tick)
            c.toast("Floating timer started. Long-press it to close")
        }
    }

    // ---- Distraction report ----
    fun distractionReport(c: Context) {
        val box = c.vbox()
        if (!Usage.granted(c)) {
            box.addView(c.text("Grant usage access first.", 13f, 0, T.text))
            box.addView(c.pill("Open settings", "settings", true) { Usage.openSettings(c) }, lp(-2, -2, 10))
            Dlg.view(c, "Distraction report", box)
            return
        }
        val dis = Usage.distraction()
        val used = Usage.today(c)
        val rows = used.filter { it.key in dis && it.value > 60000 }.entries.sortedByDescending { it.value }
        val total = rows.sumOf { it.value } / 60000
        box.addView(c.text("$total min on distraction apps today", 16f, 2, if (total > 60) T.danger else T.sage))
        val pm = c.packageManager
        rows.take(12).forEach {
            val name = try { pm.getApplicationLabel(pm.getApplicationInfo(it.key, 0)).toString() } catch (e: Exception) { it.key }
            box.addView(c.text("$name: ${it.value / 60000} min", 13f, 0, T.text), lp(-1, -2, 6))
        }
        if (rows.isEmpty()) box.addView(c.text("Nothing noticeable today. Well done.", 12f, 0, T.text3), lp(-1, -2, 8))
        Dlg.view(c, "Distraction report", box)
    }

    // ---- Subject notes ----
    private val SUBJECTS = listOf("Polity", "Economy", "Geography", "History", "Environment", "Agriculture", "Art and Culture", "Current Affairs", "International Relations", "Science and Tech", "Philosophy", "Ethics")

    fun subjectNotes(c: Context) {
        Dlg.choose(c, "Subject notes", SUBJECTS.map { it + "  (" + Store.rows("tk_log").count { r -> r.optString("kind") == "sn_$it" } + ")" }) { i ->
            Toolkit.logDialog(c, "sn_" + SUBJECTS[i], SUBJECTS[i] + " notes", "A note, a book page or a link for ${SUBJECTS[i]}")
        }
    }

    // ---- Free slots from the calendar ----
    fun freeSlots(c: Context) {
        if (c.checkSelfPermission("android.permission.READ_CALENDAR") != PackageManager.PERMISSION_GRANTED) {
            if (c is Activity) c.requestPermissions(arrayOf("android.permission.READ_CALENDAR"), 91) else c.toast("Allow calendar access")
            c.toast("Allow calendar, then tap again")
            return
        }
        val zone = TimeProvider.zone
        val d = today()
        val from = d.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        val to = d.atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val busy = ArrayList<Pair<Long, Long>>()
        try {
            val u = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from)
                android.content.ContentUris.appendId(it, to)
            }.build()
            c.contentResolver.query(u, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY), null, null, "begin ASC")?.use {
                while (it.moveToNext()) if (it.getInt(2) == 0) busy.add(maxOf(it.getLong(0), from) to minOf(it.getLong(1), to))
            }
        } catch (e: Exception) {
            Log.e("free-slots", e)
        }
        var cur = from
        val box = c.vbox()
        val f = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
        fun t(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(f)
        var n = 0
        for ((b, e) in busy.sortedBy { it.first } + listOf(to to to)) {
            if (b - cur >= 45 * 60000L) {
                box.addView(Toolkit.row(c, "calendar", "${t(cur)} to ${t(b)}", "${(b - cur) / 60000} min free", T.sage) { }, lp(-1, -2, 6))
                n++
            }
            cur = maxOf(cur, e)
        }
        if (n == 0) box.addView(c.text("No free block of 45 minutes or more today.", 13f, 0, T.text))
        Dlg.view(c, "Free study slots today", box)
    }

    // ---- Workspaces (two apps side by side) ----
    fun workspaces(a: Activity) {
        val ws = Store.rows("ws")
        val items = ws.map { it.optString("name") } + "+ New workspace"
        Dlg.choose(a, "Workspaces", items) { i ->
            if (i == ws.size) {
                val apps = Apps.list.filter { it.pkg !in Apps.hidden() }
                Dlg.choose(a, "First app", apps.map { it.label }) { x ->
                    Dlg.choose(a, "Second app (side by side)", apps.map { it.label }) { y ->
                        Store.add("ws", obj("name" to apps[x].label + " + " + apps[y].label, "a" to apps[x].pkg, "b" to apps[y].pkg))
                        a.toast("Saved. Open Workspaces again to launch")
                    }
                }
            } else {
                val w = ws[i]
                Apps.launch(a, w.optString("a"), "normal")
                a.window.decorView.postDelayed({ Apps.launch(a, w.optString("b"), "split") }, 900)
            }
        }
    }

    // ---- Launcher shortcuts (long-press the Mission icon) ----
    fun shortcuts(c: Context) {
        try {
            val sm = c.getSystemService(ShortcutManager::class.java) ?: return
            fun sc(id: String, label: String, open: String) =
                ShortcutInfo.Builder(c, id).setShortLabel(label).setLongLabel(label)
                    .setIcon(Icon.createWithResource(c, android.R.drawable.ic_menu_agenda))
                    .setIntent(Intent(c, MainActivity::class.java).setAction(Intent.ACTION_MAIN).putExtra("open", open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
            sm.dynamicShortcuts = listOf(sc("tk", "Study toolkit", "toolkit"), sc("au", "Automation", "auto"), sc("mk", "Mock schedule", "mocks"))
        } catch (e: Exception) {
            Log.e("shortcuts", e)
        }
    }
}
