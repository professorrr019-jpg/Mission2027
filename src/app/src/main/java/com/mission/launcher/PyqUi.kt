package com.mission.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Colour coded topic picker, PYQ detail and PYQ explorer. Subject colour + Tier colour (Tier-1 red, Tier-2 amber, Tier-3 blue). */
object PyqUi {
    /** Small colour-coded keyword / badge chip. */
    fun chip(c: Context, label: String, color: Int, filled: Boolean = false): TextView =
        c.text(label, 10.5f, 2, if (filled) T.contentColor(color) else T.textAccent(color)).apply {
            background = T.round(if (filled) color else T.withAlpha(color, 0.16f), 8)
            padDp(7, 3, 7, 3)
            maxLines = 1
        }

    fun tierChip(c: Context, m: Pyq.M): TextView = chip(c, Pyq.tierName(m.tier), Pyq.tierColor(m.tier), true)

    fun counts(m: Pyq.M): String = "${m.total} Q total  |  last 15y ${m.last15}  |  last 5y ${m.recent5}"

    private fun tick(c: Context, on: Boolean, color: Int): View =
        c.text(if (on) "✓" else "", 13f, 3, T.contentColor(color)).apply {
            gravity = Gravity.CENTER
            background = if (on) T.round(color, 7) else T.round(T.inset, 7, 2, T.withAlpha(color, 0.6f))
            layoutParams = LinearLayout.LayoutParams(T.dp(24), T.dp(24))
        }

    /**
     * Topic picker. keys = allowed topic keys ("Subject|Micro"), null = whole syllabus. browse = tap opens PYQ detail instead of ticking.
     */
    fun pick(
        a: Activity,
        title: String,
        keys: Collection<String>?,
        pre: Collection<String>,
        browse: Boolean = false,
        okLabel: String = "Add",
        ok: (List<String>) -> Unit = {},
    ) {
        Pyq.load(a)
        if (!Pyq.ready) {
            a.toast("PYQ data not available")
            return
        }
        val allow = keys?.toSet()
        val sel = LinkedHashSet<String>(pre.filter { allow == null || it in allow })
        val done = Syl.doneSet()
        var tierF = 0
        val subjF = LinkedHashSet<Int>()
        var pendOnly = false
        var query = ""
        val root = a.vbox()
        root.padDp(14, 6, 14, 4)
        val dm = a.resources.displayMetrics
        root.minimumHeight = (dm.heightPixels * 0.72f).toInt()
        val search = Dlg.field(a, "Search topic or keyword")
        search.isSingleLine = true
        root.addView(search, lp(-1, -2, 0))
        val filters = a.vbox()
        root.addView(filters, lp(-1, -2, 6))
        val info = a.text("", 11.5f, 2, T.text2)
        root.addView(info, lp(-2, -2, 6))
        val list = a.vbox()
        val sv = ScrollView(a).apply { addView(list) }
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(6) })
        val buttons = a.hbox()
        root.addView(buttons, lp(-1, -2, 8))
        var dlg: android.app.AlertDialog? = null

        fun visible(): List<Pyq.M> =
            Pyq.withPractice.filter { subjF.isEmpty() || it.idx in subjF }.flatMap { it.micros }.filter { m ->
                (allow == null || m.key in allow) &&
                    (tierF == 0 || m.tier == tierF) &&
                    (!pendOnly || m.key !in done) &&
                    (query.isBlank() || m.name.contains(query, true) || m.terms.any { it.first.contains(query, true) } || m.qs.any { it.kw.contains(query, true) || it.hi.contains(query) })
            }

        lateinit var render: () -> Unit

        fun renderFilters() {
            filters.removeAllViews()
            val tiers =
                listOf("All tiers" to 0, "Tier-1 (6+ in 5y)" to 1, "Tier-2 (2-5)" to 2, "Tier-3 (0-1)" to 3).map { (l, v) ->
                    val col = if (v == 0) T.accent else Pyq.tierColor(v)
                    chip(a, l, col, tierF == v).apply { tap(false) { tierF = v; render() } }
                }
            filters.addView(a.chipRow(*tiers.toTypedArray()))
            val subs =
                Pyq.withPractice.filter { s -> allow == null || s.micros.any { it.key in allow } }.map { s ->
                    chip(a, if (s.practice) "PYQ Practice" else Syl.short(s.name), s.color, s.idx in subjF).apply { tap(false) { if (!subjF.add(s.idx)) subjF.remove(s.idx); render() } }
                } + chip(a, if (pendOnly) "Pending only" else "Show done too", T.text2, pendOnly).apply { tap(false) { pendOnly = !pendOnly; render() } }
            filters.addView(a.chipRow(*subs.toTypedArray()), lp(-1, -2, 6))
        }

        fun row(m: Pyq.M): View {
            val col = Pyq.colorOf(m.subject)
            val on = m.key in sel
            val r = a.hbox(false)
            r.background = T.round(if (on) T.withAlpha(col, 0.14f) else T.inset, 12)
            r.padDp(0, 0, 8, 0)
            r.addView(View(a).apply { background = T.round(col, 3) }, lpm(T.dp(5), -1, 0, 0, 8, 0))
            if (!browse) r.addView(tick(a, on, col), lpm(T.dp(24), T.dp(24), 0, 10, 8, 0))
            val mid = a.vbox()
            val nm = (if (m.key in done) "[done] " else "") + m.name
            mid.addView(a.text(nm, 13f, 2, T.text), lp(-1, -2, 8))
            val meta = a.hbox()
            meta.addView(tierChip(a, m))
            meta.addView(a.text("  " + counts(m), 10.5f, 1, T.text2))
            mid.addView(meta, lp(-2, -2, 4))
            val kws = m.terms.take(4).map { chip(a, it.first + " " + it.second, col) }
            if (kws.isNotEmpty()) mid.addView(a.chipRow(*kws.toTypedArray()), lp(-1, -2, 4))
            mid.addView(a.spacer(8))
            r.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            if (!browse) r.addView(chip(a, "PYQ", Pyq.tierColor(m.tier)).apply { tap(false) { detail(a, m.key) } }, lpm(-2, -2, 6, 8, 0, 0))
            r.tap(false) {
                if (browse) {
                    detail(a, m.key)
                } else {
                    if (!sel.add(m.key)) sel.remove(m.key)
                    render()
                }
            }
            r.setOnLongClickListener { detail(a, m.key); true }
            return r
        }

        fun renderButtons() {
            buttons.removeAllViews()
            if (!browse) {
                buttons.addView(a.pill("Select all shown", null, false) { visible().forEach { sel.add(it.key) }; render() }, lpm(-2, -2, 0, 0, 6, 0))
                buttons.addView(a.pill("Clear", null, false) { sel.clear(); render() }, lpm(-2, -2, 0, 0, 6, 0))
            }
            buttons.addView(a.flex())
            buttons.addView(a.pill(if (browse) "Close" else "Cancel", null, false) { dlg?.dismiss() }, lpm(-2, -2, 0, 0, 6, 0))
            if (!browse) {
                buttons.addView(a.pill("$okLabel (${sel.size})", "checkc", true) { dlg?.dismiss(); ok(sel.toList()) })
            }
        }

        render = {
            renderFilters()
            val v = visible()
            info.text = "${v.size} topics  |  ${v.sumOf { it.total }} questions" + (if (!browse) "  |  ${sel.size} selected" else "") + "   (tap PYQ or long-press for every question)"
            list.removeAllViews()
            v.forEach { list.addView(row(it), lp(-1, -2, 6)) }
            renderButtons()
        }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}

                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    render()
                }
            },
        )
        render()
        dlg = Dlg.builder(a).setTitle(title).setView(root).create()
        dlg.show()
        dlg.window?.setLayout((dm.widthPixels * 0.94f).toInt().coerceAtMost(T.dpFixed(900)), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    /** Year-wise bars and every question of one micro-theme. */
    fun detail(a: Activity, key: String) {
        val m = Pyq.micro(key) ?: return
        val col = Pyq.colorOf(m.subject)
        val box = a.vbox()
        box.padDp(18, 6, 18, 6)
        val head = a.hbox()
        head.addView(tierChip(a, m))
        head.addView(a.text("  " + Syl.short(m.subject), 12f, 2, T.textAccent(col)))
        box.addView(head)
        box.addView(a.text(counts(m), 12f, 1, T.text2), lp(-2, -2, 6))
        val by = m.byYear()
        val mx = (by.values.maxOrNull() ?: 1).coerceAtLeast(1)
        box.addView(a.text("Year-wise questions", 12f, 2, T.text), lp(-2, -2, 12))
        Pyq.years.sortedDescending().forEach { y ->
            val n = by[y] ?: 0
            val r = a.hbox()
            r.addView(a.text(y.toString(), 11f, 1, if (y >= Pyq.Y5) T.text else T.text3), LinearLayout.LayoutParams(T.dp(40), -2))
            val barBox = LinearLayout(a)
            val w = if (n == 0) 0.001f else n.toFloat() / mx
            barBox.addView(View(a).apply { background = T.round(if (y >= Pyq.Y5) col else T.withAlpha(col, 0.55f), 3) }, LinearLayout.LayoutParams(0, T.dp(8), w))
            barBox.addView(View(a), LinearLayout.LayoutParams(0, 1, (1f - w).coerceAtLeast(0.001f)))
            r.addView(barBox, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(a.text(n.toString(), 11f, 2, T.text2), lpm(T.dp(24), -2, 6, 0, 0, 0))
            box.addView(r, lp(-1, -2, 3))
        }
        if (m.terms.isNotEmpty()) {
            box.addView(a.text("Keywords", 12f, 2, T.text), lp(-2, -2, 12))
            box.addView(a.chipRow(*m.terms.take(10).map { chip(a, it.first + " " + it.second, col) }.toTypedArray()), lp(-1, -2, 4))
        }
        box.addView(a.text("All ${m.total} questions (newest first)", 12f, 2, T.text), lp(-2, -2, 12))
        m.qs.forEach { q ->
            val card = a.vbox()
            card.background = T.round(T.inset, 10)
            card.padDp(10, 8, 10, 8)
            val h = a.hbox()
            h.addView(chip(a, q.year.toString() + "  Q" + q.no, if (q.year >= Pyq.Y5) Pyq.tierColor(1) else T.slate))
            if (q.diff.isNotBlank()) h.addView(chip(a, "Lvl " + q.diff, T.amber), lpm(-2, -2, 6, 0, 0, 0))
            if (q.ans.isNotBlank()) h.addView(chip(a, "Ans " + q.ans, T.sage, true), lpm(-2, -2, 6, 0, 0, 0))
            card.addView(h)
            if (q.hi.isNotBlank()) card.addView(a.text(q.hi, 12.5f, 1, T.text), lp(-1, -2, 6))
            if (q.kw.isNotBlank()) card.addView(a.text(q.kw, 10.5f, 1, T.textAccent(col)), lp(-1, -2, 4))
            box.addView(card, lp(-1, -2, 6))
        }
        val sv = ScrollView(a).apply { addView(box) }
        Dlg.builder(a).setTitle(m.name).setView(sv).setPositiveButton("Close", null).show()
    }

    /** Whole-syllabus PYQ explorer: every micro-theme with counts, tap for all questions. */
    fun explorer(a: Activity) {
        Pyq.load(a)
        pick(a, "PYQ explorer 2007-2026 (${Pyq.totalQuestions()} Q)", null, emptyList(), true)
    }
}
