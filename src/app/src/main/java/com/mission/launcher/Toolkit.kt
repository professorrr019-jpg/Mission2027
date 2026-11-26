package com.mission.launcher

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

/**
 * Study Toolkit: 23 small, offline, additive tools (nothing here replaces an existing feature).
 * Everything is stored through [Store], so it is included in backups. Deleting any entry is password-gated.
 */
object Toolkit {
    private val COUNTERS =
        listOf(
            Triple("answers", "Answer writing", "Answers written today"),
            Triple("news", "Newspaper reading", "Newspapers finished today"),
            Triple("csat", "CSAT practice", "CSAT sets done today"),
            Triple("pyq", "PYQ practice", "PYQ sets done today"),
            Triple("essay", "Essay practice", "Essays / outlines written today"),
            Triple("urge", "Distraction urges resisted", "Times you resisted the phone today"),
            Triple("rounds", "Revision rounds", "Revision rounds done today"),
            Triple("mcq", "MCQ practice", "MCQs solved today (count each 10 as 1 if you like)"),
            Triple("editorial", "Editorial reading", "Editorials read today"),
            Triple("flash", "Flashcards reviewed", "Flashcard sessions done today"),
            Triple("mockt", "Mock tests", "Mock tests attempted today"),
            Triple("vocab", "Vocabulary / terms", "New terms learned today"),
        )
    private val LOGS =
        listOf(
            Triple("mistake", "Mistake log", "What went wrong and why"),
            Triple("mnemonic", "Mnemonic notebook", "Topic and the trick that works"),
            Triple("mood", "Energy and mood log", "How is focus and energy right now?"),
            Triple("ethics", "Ethics reflection", "A GS4 case, stand and reasoning"),
            Triple("sleep", "Sleep and wake log", "Slept / woke at, how it felt"),
            Triple("doubt", "Doubt list", "A doubt to clear with a teacher or book"),
            Triple("ca1", "Current-affairs one-liners", "Fact, date, place or number worth remembering"),
            Triple("map", "Map practice", "Place, river, mountain or site you located"),
            Triple("interview", "Interview and DAF prep", "Hobby, state, service or opinion you must be ready on"),
            Triple("win", "Daily wins", "One thing that went well today"),
            Triple("idea", "Ideas and reminders", "Anything you do not want to forget"),
            Triple("fc", "Flashcards (question | answer)", "Write like:  Article 21 | Right to life and personal liberty"),
            Triple("clip", "Saved from clipboard", "Text copied from other apps"),
        )
    private val MAPS =
        listOf(
            "Locate: Sangam, Prayagraj (Ganga-Yamuna-Saraswati)", "Locate: Western Ghats, Eastern Ghats and Nilgiri", "Locate: Narmada and Tapti, both west-flowing",
            "Locate: Chilika, Pulicat and Vembanad", "Locate: Siachen, Zoji La, Nathu La", "Locate: Strait of Hormuz, Malacca and Bab-el-Mandeb",
            "Locate: Ramsar sites of Rajasthan and Odisha", "Locate: Tiger reserves of Madhya Pradesh", "Locate: Indus Valley sites: Dholavira, Lothal, Rakhigarhi",
            "Locate: Major ports: Kandla, JNPT, Kochi, Paradip", "Locate: Black Sea, Caspian Sea and their neighbours", "Locate: Gulf of Mannar and Palk Strait",
        )
    private val FACTS =
        listOf(
            "Fundamental Rights are in Part III, Articles 12 to 35", "DPSP are in Part IV, Articles 36 to 51", "Fundamental Duties: Part IVA, Article 51A, added by 42nd Amendment",
            "Money Bill: Article 110, Rajya Sabha can only recommend", "President's rule: Article 356; Financial emergency: Article 360", "Ramsar convention was signed in 1971",
            "Montreal Protocol 1987 protects the ozone layer", "Kyoto Protocol 1997; Paris Agreement 2015", "GST council: Article 279A", "Finance Commission: Article 280, every 5 years",
            "Gharial is Critically Endangered, found in National Chambal Sanctuary", "Tropic of Cancer passes through 8 Indian states",
        )
    private val REPORTS =
        listOf(
            "Sarkaria Commission (1983): Centre-State relations", "Punchhi Commission (2007): Centre-State relations", "Second ARC: Ethics in governance, e-governance",
            "Mandal Commission (1979): OBC reservation", "Kothari Commission (1964): Education", "Swaminathan Commission: Farmers",
            "Shah Commission: Emergency excesses", "Balwant Rai Mehta (1957): Panchayati Raj", "Ashok Mehta Committee (1977): Panchayati Raj", "Vohra Committee: Crime-politics nexus",
        )
    private val CADAY =
        listOf(
            "Read 1 newspaper (Hindu / Indian Express)", "Read 1 editorial and note its stand", "Make or revise 10 CA notes", "Attempt today's CA quiz",
            "Link 2 current items to static syllabus", "Revise yesterday's CA in 10 minutes",
        )
    private val ESSAY =
        listOf(
            "Technology is a good servant but a bad master", "Forests are the best protection against climate change",
            "Education without values is useless", "Judicial activism: boon or bane for democracy?",
            "Cooperative federalism: promise and practice", "Data is the new oil: privacy versus growth",
            "Rural-urban divide and inclusive development", "Mind is everything: you become what you think",
            "Women's work and the care economy", "Water security as national security",
            "Freedom of expression and its reasonable limits", "Small is beautiful: role of MSMEs in India",
            "Philosophy of Gandhi in a violent world", "Artificial intelligence and the future of work",
            "Culture is what remains when everything else is forgotten",
        )
    private val ETHICS =
        listOf(
            "You find that your superior is favouring a contractor. Tender is under your charge. What do you do?",
            "A subordinate confides he falsified a minor record out of family distress. Rules say report him. Options and values?",
            "A flood relief fund is short; the politically strong village demands first priority. How do you allocate?",
            "A friend asks you to leak the date of an inspection. Loyalty versus integrity: your response?",
            "As DM you must evict a settlement before monsoon; they have no alternative. Steps and ethical basis?",
            "A media story wrongly accuses your department. Respond with transparency or protect the institution?",
            "Your honest report may stall a popular project and a colleague's career. Decide and justify.",
            "A whistleblower approaches you but fears retaliation. How do you protect them and the process?",
            "You get an expensive gift from a grateful citizen after a legitimate decision. Accept, refuse, or declare?",
            "Your team works overtime but credit goes to you. How do you act?",
            "A rule-compliant order will deny a poor widow pension on a technicality. What is the ethical path?",
            "Pressure to show good numbers: data can be massaged slightly. Where do you draw the line?",
        )
    private val THINKERS =
        listOf(
            "Plato: world of Forms is real; knowledge is recollection; the soul has reason, spirit, appetite.",
            "Aristotle: form and matter, four causes; virtue is the golden mean.",
            "Descartes: cogito ergo sum; mind-body dualism; method of doubt; innate ideas.",
            "Spinoza: one substance (God or Nature); parallelism of thought and extension.",
            "Leibniz: monads, pre-established harmony, principle of sufficient reason.",
            "Locke: tabula rasa; primary and secondary qualities; representative realism.",
            "Berkeley: esse est percipi; subjective idealism; God sustains perception.",
            "Hume: impressions and ideas; causation as habit; bundle theory of self.",
            "Kant: synthetic a priori; phenomena and noumena; categorical imperative.",
            "Hegel: dialectic of thesis, antithesis, synthesis; Absolute Spirit.",
            "Wittgenstein: picture theory (Tractatus); meaning as use, language games (Investigations).",
            "Sartre: existence precedes essence; radical freedom and bad faith.",
            "Shankara: Advaita; Brahman alone is real; the world is maya.",
            "Ramanuja: Vishishtadvaita; qualified non-dualism; bhakti and prapatti.",
            "Nagarjuna: Madhyamika; shunyata; the two truths.",
            "Buddhism: four noble truths; anatta; pratityasamutpada; kshanabhangavada.",
            "Jainism: anekantavada, syadvada, jiva and ajiva, three jewels.",
            "Nyaya: four pramanas; asatkaryavada; theory of inference and fallacies.",
            "Samkhya: dualism of Purusha and Prakriti; satkaryavada; three gunas.",
            "Radhakrishnan: intuition, religion of spirit, idealist view of life.",
        )
    private val QUOTES =
        listOf(
            "Consistency beats intensity. One honest 6-hour day, repeated, wins the exam.",
            "Revise what you studied; do not just collect more material.",
            "A topic is done only when you can write it from memory in 7 minutes.",
            "Every PYQ you solve is a free hint from the examiner.",
            "Sleep is part of the syllabus.",
            "Today's one hard topic is tomorrow's easy mark.",
            "Do not compare your chapter 3 with someone's chapter 20.",
            "The phone is a tool for the next 2 hours only if you are the one deciding.",
            "Small daily ticks make the 100% syllabus.",
            "Answer writing is a skill; skills need reps, not reading.",
        )
    private val EXAMDAY =
        listOf(
            "Printed e-admit card (and a spare copy)", "Original photo ID named on the admit card",
            "Two black ball-point pens, spare included", "Passport photo if the admit card asks for it",
            "Transparent water bottle, plain snacks", "Analog watch only; no smart devices",
            "Visited / planned route to the exam centre", "Reach 60+ minutes before reporting time",
            "Checked dress and item rules on the admit card", "Slept 7 hours the night before",
        )
    private val GAPS = listOf(1, 3, 7, 21, 45)

    private fun today(): LocalDate = Repo.dayOf(System.currentTimeMillis())

    // ---------------- counters ----------------
    private fun ck(id: String, d: LocalDate) = "tkc_${id}_$d"

    fun count(id: String, d: LocalDate = today()): Int = Store.i(ck(id, d), 0)

    fun streak(id: String): Int {
        var d = today()
        if (count(id, d) == 0) d = d.minusDays(1)
        var n = 0
        while (count(id, d) > 0 && n < 3650) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun gated(c: android.content.Context, f: () -> Unit) {
        val a = c as? Activity
        if (a == null) c.toast("Open from the app to confirm with the password") else ResetGate.verifyAndBackup(a, f)
    }

    fun row(c: android.content.Context, ic: String, title: String, sub: String, tint: Int, f: () -> Unit): View =
        c.hbox().apply {
            background = T.round(T.inset, 14)
            padDp(10, 9, 10, 9)
            addView(c.icon(ic, tint, 20, 1.9f))
            addView(
                c.vbox().apply {
                    addView(c.text(title, 13.5f, 2, T.text, 1))
                    addView(c.text(sub, 11f, 0, T.text3, 2), lp(-1, -2, 2))
                },
                lpm(0, -2, 10, 0, 0, 0, 1f),
            )
            tap { f() }
        }

    private fun counterDialog(c: android.content.Context, id: String, title: String, sub: String) {
        val box = c.vbox()
        val big = c.text("", 34f, 2, T.accent)
        val info = c.text("", 11.5f, 0, T.text3)
        fun refresh() {
            big.text = count(id).toString()
            val days = (6 downTo 0).map { count(id, today().minusDays(it.toLong())) }
            info.text = "$sub\nStreak ${streak(id)} days  -  last 7 days: ${days.joinToString(" ")}"
        }
        fun bump(n: Int) {
            val v = (count(id) + n).coerceAtLeast(0)
            Store.put(ck(id, today()), v)
            Store.changed()
            refresh()
        }
        box.addView(big)
        box.addView(info, lp(-1, -2, 4))
        val r = c.hbox(false)
        r.addView(c.pill("+1", null, true, T.accent) { bump(1) }, lpm(-2, -2, 0, 0, 8, 0))
        r.addView(c.pill("+5", null, false) { bump(5) }, lpm(-2, -2, 0, 0, 8, 0))
        r.addView(c.pill("-1", null, false) { bump(-1) })
        box.addView(r, lp(-2, -2, 12))
        refresh()
        Dlg.view(c, title, box)
    }

    // ---------------- logs ----------------
    fun logDialog(c: android.content.Context, kind: String, title: String, hint: String) {
        val box = c.vbox()
        fun fill() {
            box.removeAllViews()
            box.addView(
                c.pill("Add entry", "note", true, T.accent) {
                    Dlg.input(c, title, hint, multi = true) { t ->
                        if (t.isNotBlank()) {
                            Store.add("tk_log", obj("kind" to kind, "at" to System.currentTimeMillis(), "text" to t))
                            fill()
                        }
                    }
                },
                lp(-2, -2, 0),
            )
            val rows = Store.rows("tk_log").filter { it.optString("kind") == kind }.sortedByDescending { it.optLong("at") }.take(40)
            if (rows.isEmpty()) box.addView(c.text("Nothing logged yet.", 12f, 0, T.text3), lp(-1, -2, 10))
            rows.forEach { r ->
                val d = Repo.dayOf(r.optLong("at"))
                box.addView(
                    row(c, "note", r.optString("text").take(120), "$d  -  tap to delete (password)", T.clay) {
                        gated(c) {
                            Store.remove("tk_log", r.optString("id"))
                            fill()
                        }
                    },
                    lp(-1, -2, 8),
                )
            }
        }
        fill()
        Dlg.view(c, title, box)
    }

    // ---------------- top 3 ----------------
    fun topDialog(c: android.content.Context) {
        val box = c.vbox()
        fun fill() {
            box.removeAllViews()
            val d = today().toString()
            val rows = Store.rows("tk_top").filter { it.optString("d") == d }
            box.addView(c.text("Pick the few things that make today a win. Tap to tick, long text is fine.", 11.5f, 0, T.text3))
            rows.forEach { r ->
                val done = r.optBoolean("done")
                box.addView(
                    row(c, if (done) "checkc" else "target", r.optString("t"), if (done) "Done" else "Tap when finished", if (done) T.sage else T.amber) {
                        Store.update("tk_top", r.optString("id")) { it.put("done", !done) }
                        fill()
                    },
                    lp(-1, -2, 8),
                )
            }
            box.addView(
                c.pill("Add priority", "bolt", true, T.accent) {
                    Dlg.input(c, "Today's priority", "e.g. Polity: Parliament + 20 PYQs") { t ->
                        if (t.isNotBlank()) {
                            Store.add("tk_top", obj("d" to d, "t" to t, "done" to false))
                            fill()
                        }
                    }
                },
                lp(-2, -2, 12),
            )
        }
        fill()
        Dlg.view(c, "Top priorities today", box)
    }

    // ---------------- books ----------------
    private fun bookDialog(c: android.content.Context) {
        val box = c.vbox()
        fun fill() {
            box.removeAllViews()
            Store.rows("tk_book").forEach { b ->
                box.addView(
                    row(c, "layers", b.optString("n"), "${b.optInt("p")}% read  -  tap to update, long names ok", T.slate) {
                        Dlg.input(c, b.optString("n"), "Percent read (0-100)", b.optInt("p").toString(), numeric = true) { v ->
                            Store.update("tk_book", b.optString("id")) { it.put("p", (v.toIntOrNull() ?: 0).coerceIn(0, 100)) }
                            fill()
                        }
                    },
                    lp(-1, -2, 8),
                )
            }
            val r = c.hbox(false)
            r.addView(
                c.pill("Add resource", "link", true, T.accent) {
                    Dlg.input(c, "Book / course / notes", "e.g. Laxmikanth, Polity") { t ->
                        if (t.isNotBlank()) {
                            Store.add("tk_book", obj("n" to t, "p" to 0))
                            fill()
                        }
                    }
                },
                lpm(-2, -2, 0, 0, 8, 0),
            )
            if (Store.rows("tk_book").isNotEmpty()) {
                r.addView(
                    c.pill("Remove one", null, false) {
                        val items = Store.rows("tk_book")
                        Dlg.choose(c, "Remove which?", items.map { it.optString("n") }) { i ->
                            gated(c) {
                                Store.remove("tk_book", items[i].optString("id"))
                                fill()
                            }
                        }
                    },
                )
            }
            box.addView(r, lp(-2, -2, 12))
        }
        fill()
        Dlg.view(c, "Books and resources", box)
    }

    // ---------------- exam-day checklist ----------------
    private fun examDayDialog(c: android.content.Context) {
        val box = c.vbox()
        fun done() = Store.s("tk_examday").split("\u001f").filter { it.isNotEmpty() }.toMutableSet()
        fun fill() {
            box.removeAllViews()
            val d = done()
            EXAMDAY.forEachIndexed { i, t ->
                val ok = i.toString() in d
                box.addView(
                    row(c, if (ok) "checkc" else "target", t, if (ok) "Ready" else "Tap when ready", if (ok) T.sage else T.amber) {
                        val s = done()
                        if (!s.add(i.toString())) s.remove(i.toString())
                        Store.put("tk_examday", s.joinToString("\u001f"))
                        fill()
                    },
                    lp(-1, -2, 6),
                )
            }
            box.addView(c.text("Always follow the instructions printed on your own e-admit card.", 11f, 0, T.text3), lp(-1, -2, 10))
        }
        fill()
        Dlg.view(c, "Prelims day checklist", box)
    }

    // ---------------- extra tools ----------------
    fun caDialog(c: android.content.Context) {
        val box = c.vbox()
        val key = "tk_ca_" + today()
        fun done() = Store.s(key).split("\u001f").filter { it.isNotEmpty() }.toMutableSet()
        fun fill() {
            box.removeAllViews()
            val d = done()
            CADAY.forEachIndexed { i, t ->
                val ok = i.toString() in d
                box.addView(
                    row(c, if (ok) "checkc" else "target", t, if (ok) "Done" else "Tap when done", if (ok) T.sage else T.amber) {
                        val x = done()
                        if (!x.add(i.toString())) x.remove(i.toString())
                        Store.put(key, x.joinToString("\u001f"))
                        fill()
                    },
                    lp(-1, -2, 6),
                )
            }
            box.addView(c.text("${done().size} of ${CADAY.size} done today", 11f, 0, T.text3), lp(-1, -2, 10))
        }
        fill()
        Dlg.view(c, "Current-affairs checklist", box)
    }

    fun flashDialog(c: android.content.Context) {
        val cards = Store.rows("tk_log").filter { it.optString("kind") == "fc" && it.optString("text").contains("|") }
        if (cards.isEmpty()) {
            c.toast("Add cards first: Flashcards (question | answer)")
            logDialog(c, "fc", "Flashcards (question | answer)", "Write like:  Article 21 | Right to life and personal liberty")
            return
        }
        var i = (System.currentTimeMillis() / 1000 % cards.size).toInt()
        val q = c.text("", 16f, 2, T.text)
        val a = c.text("", 14f, 0, T.sage)
        val box = c.vbox()
        fun show() {
            val t = cards[i].optString("text")
            q.text = t.substringBefore("|").trim()
            a.text = ""
            a.tag = t.substringAfter("|").trim()
        }
        show()
        box.addView(q)
        box.addView(a, lp(-1, -2, 10))
        val r = c.hbox()
        r.addView(c.pill("Show answer", "eye", true, T.accent) { a.text = a.tag as String }, lpm(-2, -2, 0, 0, 8, 0))
        r.addView(
            c.pill("Next", "refresh", false) {
                i = (i + 1) % cards.size
                show()
            },
        )
        box.addView(r, lp(-1, -2, 14))
        box.addView(
            c.pill("I reviewed these", "check", false, T.sage) {
                Store.put(ck("flash", today()), count("flash") + 1)
                c.toast("Counted")
            },
            lp(-2, -2, 10),
        )
        Dlg.view(c, "Flashcard review (${cards.size} cards)", box)
    }

    fun streakBoard(c: android.content.Context) {
        val box = c.vbox()
        box.addView(c.text("Study streak: ${Repo.streak()} days", 15f, 2, T.text))
        COUNTERS.forEach { (id, title, _) -> box.addView(row(c, "chart", title, "Today ${count(id)}  -  streak ${streak(id)} days", T.sage) { }, lp(-1, -2, 6)) }
        Dlg.view(c, "Streak board", box)
    }

    fun pointsDialog(c: android.content.Context) {
        val d = today()
        val items = COUNTERS.map { (id, n, _) -> Triple(n, count(id, d), 10) }
        val mins = Repo.byDaySubject(listOf(d)).firstOrNull()?.values?.sum() ?: 0
        val total = items.sumOf { it.second * it.third } + mins / 6
        val box = c.vbox()
        box.addView(c.text("$total points today", 20f, 3, T.accent))
        box.addView(c.text("10 points per counter tap, 1 point per 6 study minutes ($mins min)", 11f, 0, T.text3), lp(-1, -2, 4))
        items.filter { it.second > 0 }.forEach { box.addView(c.text("${it.first}: ${it.second} x ${it.third}", 13f, 0, T.text), lp(-1, -2, 6)) }
        val lvl = when { total >= 300 -> "Topper mode"; total >= 150 -> "Strong day"; total >= 60 -> "Good start"; else -> "Warm up" }
        box.addView(c.text(lvl, 14f, 2, T.sage), lp(-1, -2, 12))
        Store.put("tk_best", maxOf(Store.i("tk_best", 0), total))
        box.addView(c.text("Best day so far: ${Store.i("tk_best", 0)}", 12f, 0, T.text3), lp(-1, -2, 4))
        Dlg.view(c, "Daily score", box)
    }

    fun clipSave(c: android.content.Context) {
        val cm = c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val t = try { cm.primaryClip?.getItemAt(0)?.coerceToText(c)?.toString() ?: "" } catch (e: Exception) { "" }
        if (t.isBlank()) {
            c.toast("Clipboard is empty")
            return
        }
        Store.add("tk_log", obj("kind" to "clip", "at" to System.currentTimeMillis(), "text" to t.take(2000)))
        c.toast("Saved to Saved from clipboard")
    }

    fun exportAll(c: android.content.Context) {
        val sb = StringBuilder("Mission 2027 - my logs\n")
        LOGS.forEach { (kind, title, _) ->
            val rows = Store.rows("tk_log").filter { it.optString("kind") == kind }.sortedBy { it.optLong("at") }
            if (rows.isNotEmpty()) {
                sb.append("\n## $title\n")
                rows.forEach { sb.append("- ${Repo.dayOf(it.optLong("at"))}: ${it.optString("text")}\n") }
            }
        }
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Share logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------- generators ----------------
    fun generator(c: android.content.Context, title: String, list: List<String>, label: String) {
        var i = (today().toEpochDay() % list.size).toInt()
        val t = c.text(list[i], 15f, 1, T.text, 0)
        val box = c.vbox()
        box.addView(c.text(label, 11f, 0, T.text3))
        box.addView(t, lp(-1, -2, 8))
        box.addView(
            c.pill("Another one", "refresh", false) {
                i = (i + 1) % list.size
                t.text = list[i]
            },
            lp(-2, -2, 12),
        )
        Dlg.view(c, title, box)
    }

    // ---------------- spaced revision ----------------
    private fun revisionDialog(c: android.content.Context) {
        val box = c.vbox()
        fun fill() {
            box.removeAllViews()
            val t = today()
            val due = Store.rows("tk_rev").filter { !it.optBoolean("done") && (LocalDate.parse(it.optString("due")) <= t) }.sortedBy { it.optString("due") }
            val later = Store.rows("tk_rev").filter { !it.optBoolean("done") && (LocalDate.parse(it.optString("due")) > t) }.sortedBy { it.optString("due") }.take(8)
            box.addView(c.text("Add a topic on the day you study it. Revision is scheduled at day ${GAPS.joinToString(", ")}.", 11.5f, 0, T.text3))
            box.addView(c.text("Due now (${due.size})", 13f, 2, T.text), lp(-1, -2, 10))
            if (due.isEmpty()) box.addView(c.text("Nothing due. Good.", 12f, 0, T.text3), lp(-1, -2, 4))
            due.forEach { r ->
                box.addView(
                    row(c, "refresh", r.optString("topic"), "Revision ${r.optInt("n")} of ${GAPS.size}  -  tap when revised", T.rose) {
                        Store.update("tk_rev", r.optString("id")) { it.put("done", true) }
                        fill()
                    },
                    lp(-1, -2, 6),
                )
            }
            if (later.isNotEmpty()) {
                box.addView(c.text("Coming up", 13f, 2, T.text), lp(-1, -2, 12))
                later.forEach { r -> box.addView(c.text("${r.optString("due")}  -  ${r.optString("topic")}", 11.5f, 0, T.text3), lp(-1, -2, 4)) }
            }
            box.addView(
                c.pill("Add topic studied today", "bolt", true, T.accent) {
                    val ticked = Syl.doneSet().toList().takeLast(25).reversed()
                    val items = listOf("Type a topic...") + ticked.map { it.replace("|", "  >  ") }
                    Dlg.choose(c, "Which topic?", items) { k ->
                        fun schedule(name: String) {
                            if (name.isBlank()) return
                            GAPS.forEachIndexed { n, g -> Store.add("tk_rev", obj("topic" to name, "due" to t.plusDays(g.toLong()).toString(), "n" to n + 1, "done" to false)) }
                            fill()
                        }
                        if (k == 0) Dlg.input(c, "Topic", "e.g. Polity > Parliament") { schedule(it) } else schedule(items[k])
                    }
                },
                lp(-2, -2, 14),
            )
        }
        fill()
        Dlg.view(c, "Spaced revision", box)
    }

    // ---------------- weak topics ----------------
    private fun weakDialog(c: android.content.Context) {
        val z = Zite.data(c)
        val rows =
            z.microResp.entries.map { (k, v) -> Triple(k, v[0], v.sum()) }.filter { it.third >= 5 }.sortedBy { it.second.toDouble() / it.third }.take(12)
        val box = c.vbox()
        if (rows.isEmpty()) box.addView(c.text("Not enough Zite responses yet (needs 5+ answers per topic). Sync from the Zite card.", 12f, 0, T.text3))
        rows.forEach { (k, ok, n) ->
            box.addView(row(c, "trend", k.replace("|", "  >  "), "${ok * 100 / n}% correct out of $n answers", if (ok * 100 / n < 50) T.rose else T.amber) {}, lp(-1, -2, 6))
        }
        Dlg.view(c, "Weakest topics (Zite)", box)
    }

    // ---------------- milestones ----------------
    private fun milestoneDialog(c: android.content.Context) {
        val exam = Repo.examDate()
        val left = java.time.temporal.ChronoUnit.DAYS.between(today(), exam)
        val box = c.vbox()
        box.addView(c.text("$left days to ${Store.s("exam_name", "UPSC Prelims 2027")} (${exam})", 15f, 2, T.accent))
        val phase =
            when {
                left > 200 -> "Foundation phase: finish the syllabus once, build notes."
                left > 100 -> "Consolidation: second reading, PYQs by topic, start tests."
                left > 45 -> "Test phase: full-length mocks weekly, analyse every error."
                left > 15 -> "Revision: only your own notes and error log."
                else -> "Final stretch: sleep, light revision, no new sources."
            }
        box.addView(c.text(phase, 12.5f, 0, T.text2), lp(-1, -2, 6))
        listOf(365, 200, 100, 75, 50, 30, 15, 7, 1).forEach { d ->
            val on = exam.minusDays(d.toLong())
            val past = on < today()
            box.addView(c.text((if (past) "Done  " else "      ") + "T-$d days  -  $on", 12f, if (past) 0 else 2, if (past) T.text3 else T.text), lp(-1, -2, 5))
        }
        Dlg.view(c, "Milestones", box)
    }

    // ---------------- weekly report ----------------
    fun reportDialog(c: android.content.Context) {
        val days = (6 downTo 0).map { today().minusDays(it.toLong()) }
        val per = Repo.byDaySubject(days)
        val total = per.sumOf { it.values.sum() }
        val subj = HashMap<String, Int>()
        per.forEach { m -> m.forEach { (k, v) -> subj.merge(k, v, Int::plus) } }
        val sb = StringBuilder("Mission 2027 - last 7 days\nStudied: ${fmtMins(total)}  -  streak ${Repo.streak()} days\n")
        subj.entries.sortedByDescending { it.value }.forEach { sb.append("- ${it.key}: ${fmtMins(it.value)}\n") }
        COUNTERS.forEach { (id, name, _) ->
            val n = days.sumOf { count(id, it) }
            if (n > 0) sb.append("- $name: $n\n")
        }
        val text = sb.toString()
        val box = c.vbox()
        box.addView(c.text(text, 13f, 0, T.text))
        box.addView(
            c.pill("Share", "link", true, T.accent) {
                c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
            },
            lp(-2, -2, 12),
        )
        Dlg.view(c, "Weekly report", box)
    }

    // ---------------- Zite mock schedule ----------------
    fun mockSchedule(c: android.content.Context) {
        val z = Zite.data(c)
        val box = c.vbox()
        if (z.mocks.isEmpty()) box.addView(c.text("No mock tests synced yet. Open the Zite card and sync.", 12f, 0, T.text3))
        val t = today()
        z.mocks.sortedBy { it.date ?: LocalDate.MAX }.forEach { m ->
            val past = m.date?.isBefore(t) == true
            val sub = (m.date?.toString() ?: "no date") + "  -  " + m.q + " Qs  -  " + m.status + (if (m.dur > 0) "  -  ${m.dur} min" else "") + (if (m.access.isNotBlank()) "  -  ${m.access}" else "")
            box.addView(row(c, if (past) "checkc" else "calendar", (if (m.tcode.isNotBlank()) m.tcode + "  " else "") + m.title, sub, if (past) T.sage else T.slate) {
                if (m.core.isNotBlank()) Dlg.info(c, m.title, (if (m.grp.isNotBlank()) m.grp + "\n\n" else "") + m.core)
            }, lp(-1, -2, 6))
        }
        Dlg.view(c, "Mock tests (${z.mocks.size})", box)
    }

    // ---------------- hub ----------------
    fun open(c: android.content.Context) {
        val box = c.vbox()
        fun add(ic: String, title: String, sub: String, tint: Int, f: () -> Unit) = box.addView(row(c, ic, title, sub, tint, f), lp(-1, -2, 6))
        fun head(s: String) = box.addView(c.text(s, 12f, 2, T.text3), lp(-1, -2, 14))
        head("AUTOMATION")
        add("bolt", "Automation rules", "${Auto.rules.size} ready rules, ${Store.s("auto_on").split("\u001f").count { it.isNotEmpty() }} on", T.amber) { Auto.open(c) }
        add("calendar", "Mock test schedule", "All tests from Zite with date and topic", T.slate) { mockSchedule(c) }
        head("TODAY")
        add("bolt", "Top priorities", "${Store.rows("tk_top").count { it.optString("d") == today().toString() }} set today", T.amber) { topDialog(c) }
        val due = Store.rows("tk_rev").count { !it.optBoolean("done") && LocalDate.parse(it.optString("due")) <= today() }
        add("refresh", "Spaced revision", "$due due now", T.rose) { revisionDialog(c) }
        add("trend", "Weakest topics", "From your Zite answers", T.plum) { weakDialog(c) }
        head("DAILY COUNTERS")
        COUNTERS.forEach { (id, title, sub) -> add("chart", title, "Today ${count(id)}  -  streak ${streak(id)}", T.sage) { counterDialog(c, id, title, sub) } }
        head("LOGS")
        LOGS.forEach { (kind, title, hint) -> add("note", title, "${Store.rows("tk_log").count { it.optString("kind") == kind }} entries", T.clay) { logDialog(c, kind, title, hint) } }
        head("PLANNING")
        add("layers", "Books and resources", "${Store.rows("tk_book").size} tracked", T.slate) { bookDialog(c) }
        add("calendar", "Milestones", "T-365 to T-1 for your exam", T.teal) { milestoneDialog(c) }
        add("checkc", "Prelims day checklist", "Ready ${Store.s("tk_examday").split("\u001f").count { it.isNotEmpty() }}/${EXAMDAY.size}", T.sage) { examDayDialog(c) }
        add("chart", "Weekly report", "Share last 7 days", T.accent) { reportDialog(c) }
        head("IDEAS")
        add("sparkle", "Essay topic", "One per day", T.plum) { generator(c, "Essay topic", ESSAY, "Write a 10-point outline in 15 minutes") }
        add("sparkle", "Ethics case", "GS4 practice", T.plum) { generator(c, "Ethics case", ETHICS, "Write stakeholders, values, options, final decision") }
        add("sparkle", "Philosophy thinker", "Optional revision", T.plum) { generator(c, "Philosophy thinker", THINKERS, "Recall in 3 lines, then check") }
        add("sparkle", "Thought for today", "Motivation", T.plum) { generator(c, "Thought for today", QUOTES, "") }
        add("sparkle", "Map of the day", "Locate it on a map", T.plum) { generator(c, "Map of the day", MAPS, "Find it in the atlas, then close your eyes and trace it") }
        add("sparkle", "Prelims fact", "One high-yield fact", T.plum) { generator(c, "Prelims fact", FACTS, "Say it aloud, then make one MCQ from it") }
        add("sparkle", "Committee or report", "Static polity and governance", T.plum) { generator(c, "Committee or report", REPORTS, "Recall year, chair and recommendation") }
        head("MORE TOOLS")
        add("checkc", "Current-affairs checklist", "Daily 6-step routine", T.sage) { caDialog(c) }
        add("note", "Flashcard review", "${Store.rows("tk_log").count { it.optString("kind") == "fc" }} cards", T.accent) { flashDialog(c) }
        add("chart", "Streak board", "Every counter and its streak", T.sage) { streakBoard(c) }
        add("trend", "Daily score", "Points for today's work", T.amber) { pointsDialog(c) }
        add("link", "Save clipboard to notes", "Keeps copied text from any app", T.clay) { clipSave(c) }
        add("up", "Share all my logs", "Export every log as text", T.slate) { exportAll(c) }
        Dlg.view(c, "Study toolkit", box)
    }
}
