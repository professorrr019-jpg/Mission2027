package com.mission.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.time.LocalDate

/** Settings as a real folder tree: each node opens a full-screen opaque page, folders may contain folders. */
class SettingsActivity : Activity() {
    private class Page(val title: String, val build: LinearLayout.() -> Unit)

    private val stack = ArrayList<Page>()
    private lateinit var title: TextView
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var back: View
    private val REQ_FILE = 85
    private val REQ_CSV = 86
    private val REQ_TREE = 81
    private val REQ_EXPORT = 82
    private val REQ_IMPORT = 83
    private val REQ_PHOTO = 84
    private val ui = Handler(Looper.getMainLooper())
    private var backCallback: android.window.OnBackInvokedCallback? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        if (Slot.strictOn()) {
            // Settings stay closed during a study slot; the emergency code screen opens instead.
            startActivity(Intent(this, StrictActivity::class.java).putExtra("pkg", packageName))
            finish()
            return
        }
        systemBars(T.page)
        val root = vbox()
        root.setBackgroundColor(T.page)
        root.setOnApplyWindowInsetsListener {
                v,
                i,
            ->
            val s = i.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(0, s.top, 0, s.bottom)
            i
        }
        val bar = hbox()
        bar.padDp(8, 8, 16, 8)
        back = iconBtn("chevl", T.text, 44) { goBack() }
        bar.addView(back)
        title = text("Settings", 22f, 3)
        bar.addView(title, lpm(0, -2, 4, 0, 0, 0, 1f))
        root.addView(bar)
        scroll =
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        body = vbox()
        body.setPadding(T.dp(16), T.dp(4), T.dp(16), T.dp(40))
        scroll.addView(body)
        val wide = resources.displayMetrics.widthPixels / resources.displayMetrics.density > 700
        val holder = FrameLayout(this)
        holder.addView(
            scroll,
            FrameLayout.LayoutParams(if (wide) T.dp(720) else -1, -1, if (wide) Gravity.CENTER_HORIZONTAL else Gravity.START),
        )
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { goBack() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        }
        push(Page("Settings") { home() })
        intent.getStringExtra("path")?.let {
                p ->
            when (p) {
                "zite" -> push(Page("Zite and data") { zite() })
                "ai" -> push(Page("AI") { ai() })
                "about" -> push(Page("About and diagnostics") { about() })
                "setup" -> push(Page("Setup checklist") { setup() })
                "reset" -> push(Page("Reset and data") { resetData() })
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = goBack()

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backCallback = null
        }
        super.onDestroy()
    }

    private fun goBack() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            show()
        } else {
            finish()
        }
    }

    private fun push(p: Page) {
        stack.add(p)
        show()
    }

    private fun show() {
        val p = stack.last()
        title.text = p.title
        back.visibility = View.VISIBLE
        val y = 0
        body.removeAllViews()
        p.build(body)
        scroll.scrollTo(0, y)
    }

    private fun redraw() {
        val y = scroll.scrollY
        body.removeAllViews()
        stack.last().build(body)
        scroll.post { scroll.scrollTo(0, y) }
    }

    // ---------- row helpers ----------
    private fun LinearLayout.group(vararg rows: View) {
        val c = Card(this@SettingsActivity, 20)
        c.padDp(6, 4, 6, 4)
        rows.forEachIndexed {
                i,
                r,
            ->
            if (i > 0) c.addView(divider(), lpm(-1, 1, 14, 0, 0, 0))
            c.addView(r)
        }
        addView(c, lpm(-1, -2, 0, 0, 0, 14))
    }

    private fun LinearLayout.label(s: String) =
        addView(
            text(s.uppercase(), 11.5f, 3, T.text3).apply {
                letterSpacing = 0.06f
                padDp(10, 10, 0, 6)
            },
        )

    private fun LinearLayout.note(s: String) = addView(text(s, 12f, 0, T.text3).apply { padDp(10, 0, 10, 12) })

    private fun rowBase(
        ic: String?,
        tint: Int,
        t: String,
        sub: String?,
        right: View?,
        click: (() -> Unit)?,
    ): View =
        hbox().apply {
            padDp(12, 13, 12, 13)
            minimumHeight = T.dp(52)
            if (ic != null) {
                addView(
                    FrameLayout(this@SettingsActivity).apply {
                        background = T.round(T.withAlpha(tint, 0.16f), 10)
                        addView(icon(ic, T.textAccent(tint), 18, 1.9f), FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.CENTER))
                    },
                    lpm(T.dp(34), T.dp(34), 0, 0, 12, 0),
                )
            }
            val col = vbox()
            col.addView(text(t, 15f, 1, T.text))
            if (!sub.isNullOrBlank()) col.addView(text(sub, 12f, 0, T.text3), lp(-2, -2, 2))
            addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            if (right != null) addView(right, lpm(-2, -2, 8, 0, 0, 0))
            if (click != null) tap(false) { click() }
        }

    private fun folder(
        ic: String,
        tint: Int,
        t: String,
        sub: String,
        build: LinearLayout.() -> Unit,
    ): View =
        rowBase(
            ic,
            tint,
            t,
            sub,
            icon("chev", T.text3, 16, 2f),
        ) {
            push(Page(t, build))
        }

    private fun action(
        ic: String?,
        tint: Int,
        t: String,
        sub: String? = null,
        f: () -> Unit,
    ): View = rowBase(ic, tint, t, sub, null, f)

    private fun value(
        t: String,
        sub: String? = null,
        v: String,
        f: () -> Unit,
    ): View =
        rowBase(
            null,
            0,
            t,
            sub,
            text(
                v,
                13.5f,
                1,
                T.text2,
                1,
            ).apply {
                maxWidth = T.dp(170)
            },
            f,
        )

    private fun sw(
        t: String,
        sub: String?,
        key: String,
        def: Boolean,
        after: ((Boolean) -> Unit)? = null,
    ): View {
        val s =
            Switch(this).apply {
                isChecked = Store.b(key, def)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(T.sage, T.withAlpha(T.text3, 0.45f)))
            }
        s.setOnCheckedChangeListener { _, on ->
            Store.put(key, on)
            after?.invoke(on)
        }
        return rowBase(null, 0, t, sub, s) { s.toggle() }
    }

    private fun choice(
        t: String,
        key: String,
        def: String,
        opts: List<Pair<String, String>>,
        sub: String? = null,
        after: (() -> Unit)? = null,
    ): View {
        val cur = Store.s(key, def)
        return value(t, sub, opts.firstOrNull { it.first == cur }?.second ?: cur) {
            Dlg.choose(this, t, opts.map { it.second }) { i ->
                Store.put(key, opts[i].first)
                after?.invoke()
                redraw()
            }
        }
    }

    private fun num(
        t: String,
        key: String,
        def: Int,
        lo: Int,
        hi: Int,
        unit: String,
        sub: String? = null,
        after: (() -> Unit)? = null,
    ): View =
        value(t, sub, "${Store.i(key, def)} $unit") {
            Dlg.input(this, t, "$lo - $hi", Store.i(key, def).toString(), true) { s ->
                s.toIntOrNull()?.let {
                    Store.put(key, it.coerceIn(lo, hi))
                    after?.invoke()
                    redraw()
                }
            }
        }

    private fun slider(
        t: String,
        key: String,
        def: Int,
        max: Int,
        suffix: String = "%",
        min: Int = 0,
        after: (() -> Unit)? = null,
    ): View =
        vbox().apply {
            val current = Store.i(key, def).coerceIn(min, max)
            padDp(14, 12, 14, 8)
            val v = text("$current$suffix", 13.5f, 1, T.text2)
            val r = hbox()
            r.addView(text(t, 15f, 1), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(v)
            addView(r)
            addView(
                SeekBar(this@SettingsActivity).apply {
                    this.max = (max - min).coerceAtLeast(1)
                    progress = current - min
                    progressTintList = ColorStateList.valueOf(T.sage)
                    thumbTintList = ColorStateList.valueOf(T.sage)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                s: SeekBar?,
                                p: Int,
                                u: Boolean,
                            ) {
                                v.text = "${p + min}$suffix"
                            }

                            override fun onStartTrackingTouch(s: SeekBar?) {}

                            override fun onStopTrackingTouch(s: SeekBar?) {
                                Store.put(key, min + s!!.progress)
                                after?.invoke()
                            }
                        },
                    )
                },
                lp(-1, -2, 2),
            )
        }

    private fun secret(
        t: String,
        get: () -> String,
        set: (String) -> Unit,
        hint: String,
    ): View {
        val k = get()
        val detail = SecureStore.lastError?.let { "$hint $it" } ?: hint
        return value(t, detail, if (k.isBlank()) "Not set" else "••••" + k.takeLast(4)) {
            Dlg.input(this, t, "Paste here", "", false, false, "Save", true) { s ->
                try {
                    set(s.trim())
                    redraw()
                } catch (e: Exception) {
                    Log.e("secret-save", e)
                    toast("Could not save this key securely")
                }
            }
        }
    }

    private fun multiPick(
        titleS: String,
        key: String,
        defaults: Set<String> = emptySet(),
        max: Int = 0,
    ) {
        val apps = Apps.list
        val cur = if (Store.has(key)) Store.stringList(key).toMutableSet() else defaults.toMutableSet()
        val names = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].pkg in cur }
        Dlg.builder(this).setTitle(titleS).setMultiChoiceItems(names, checked) { d, i, on ->
            if (on) {
                if (max > 0 && checked.count { it } > max) {
                    checked[i] = false
                    (d as android.app.AlertDialog).listView.setItemChecked(i, false)
                    toast("Max $max")
                }
            }
        }
            .setPositiveButton("Save") { _, _ ->
                val sel =
                    apps.filterIndexed {
                            i,
                            _,
                        ->
                        checked[i]
                    }.map { it.pkg }
                val keep =
                    cur.filter {
                            p ->
                        apps.none { it.pkg == p }
                    }
                Store.setStringList(key, keep + sel)
                Store.changed()
                redraw()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---------- root ----------
    private fun LinearLayout.home() {
        val z = Zite.data(this@SettingsActivity)
        group(
            action("search", T.accent, "Search settings", "Type a word like wallpaper, water, update, circle, clean") {
                Dlg.input(this@SettingsActivity, "Search settings", "wallpaper, water, pin...", "") { q ->
                    val pages: List<Pair<String, LinearLayout.() -> Unit>> =
                        listOf(
                            "Water reminders goal" to { water() }, "Study timer focus pomodoro distraction" to { study() }, "Exam date countdown" to { exam() },
                            "Gestures double tap swipe floating multitask" to { gestures() }, "Circle to Search Gemini prompt tag float" to { circle() },
                            "Limits mindfulness app time" to { limits() }, "Daily briefs morning evening" to { briefs() }, "Wallpaper photo online 4K live interval" to { wallpaper() },
                            "Home screen pages widgets layout navigation bar" to { homeScreen() }, "Apps dock pin hidden study memory cleaner clean" to { apps() },
                            "Appearance theme text size card small icon" to { appearance() }, "Security PIN lock" to { security() }, "Backup restore" to { backup() },
                            "Permissions overlay usage notification" to { permissions() }, "Features tools flashcards checklist" to { features() },
                            "About update diagnostics version auto update" to { about() }, "Zite sync API key database" to { zite() }, "AI provider key Gemini model" to { ai() },
                        )
                    val hits = pages.filter { it.first.contains(q.trim(), true) }
                    when {
                        q.isBlank() -> toast("Type something")
                        hits.isEmpty() -> toast("No setting found")
                        hits.size == 1 -> push(Page(hits[0].first.substringBefore(" "), hits[0].second))
                        else -> Dlg.choose(this@SettingsActivity, "Found", hits.map { it.first.take(40) }) { i -> push(Page(hits[i].first.substringBefore(" "), hits[i].second)) }
                    }
                }
            },
        )
        group(folder("checkc", T.sage, "Setup checklist", "${setupItems().count { it.ok }} of ${setupItems().size} ready") { setup() })
        group(folder("shield", T.danger, "Reset and data", "Password-protected cache deletion and restore") { resetData() })
        group(
            folder("refresh", T.sage, "Zite and data", Zite.syncState().name.lowercase().replace('_', ' ')) { zite() },
            folder("sparkle", T.plum, "AI provider", Ai.provider().name + " - " + Ai.model()) { ai() },
        )
        group(
            folder("drop", T.teal, "Water", "${Water.goal()} ml goal, reminders ${if (Water.enabled()) "on" else "off"}") { water() },
            folder("timer", T.amber, "Study, timer and focus", "Floating timer, pomodoro, distractions") { study() },
            folder("calendar", T.terra, "Exam", Store.s("exam_date", "2027-05-23")) { exam() },
        )
        group(
            folder("layers", T.plum, "Gestures and multitasking", "Double tap, swipe down, floating apps") { gestures() },
            folder(
                "search",
                T.plum,
                "Circle to Search",
                if (CircleService.running()) "Ready" else "Needs accessibility service",
            ) { circle() },
            folder("shield", T.rose, "Limits and mindfulness", "${Guard.limits().length()} app limits") { limits() },
            folder("bell", T.amber, "Daily briefs", "Morning, evening, weekly") { briefs() },
        )
        group(
            folder("image", T.slate, "Wallpaper", Wallpaper.scene() + (if (Wallpaper.enabled()) ", auto" else ", off")) { wallpaper() },
            folder("grid", T.olive, "Home screen", "Pages, widgets, layout") { homeScreen() },
            folder("grid", T.clay, "Apps and dock", "${Apps.pins().size} pinned, ${Apps.hidden().size} hidden") { apps() },
            folder("sliders", T.rose, "Appearance", Store.s("theme", "auto")) { appearance() },
        )
        group(
            folder("lock", T.plum, "Security and PIN", if (Pin.isSet()) "PIN on" else "No PIN") { security() },
            folder(
                "cloud",
                T.teal,
                "Backup and restore",
                if (Store.s("backup_tree").isBlank()) "Device only" else "Device + folder",
            ) { backup() },
            folder("shield", T.slate, "Permissions", "Overlay, usage, notifications") { permissions() },
        )
        group(
            folder("sliders", T.olive, "Features", "${FeatureRegistry.features.count { FeatureRegistry.enabled(it) }} optional features enabled") { features() },
        )
        group(folder("info", T.text2, "About and diagnostics", "Mission 2027 launcher") { about() })
    }

    private fun LinearLayout.features() {
        label("Syllabus")
        group(sw("Show Mains", "Adds Mains-only subjects and answer-writing tools", "show_mains", false))
        label("Launcher and focus features")
        val mainsOn = Store.b("show_mains", false)
        label("Working tools (tap to open)")
        val c = this@SettingsActivity
        val open: Map<String, () -> Unit> =
            mapOf(
                "x01_flashcards" to { Toolkit.flashDialog(c) },
                "x03_answer_timer" to { Plus.answerTimer(c) },
                "x07_free_slots" to { Plus.freeSlots(c) },
                "x08_slot_templates" to { Plus.slotTemplates(c) },
                "x09_focus_presets" to { Plus.focusPresets(c) },
                "x10_mock_overlay" to { Plus.mockOverlay(c) },
                "x11_distraction_report" to { Plus.distractionReport(c) },
                "x16_notes_overlay" to { startActivity(Intent(c, CaptureActivity::class.java)) },
                "x17_subject_notes" to { Plus.subjectNotes(c) },
                "x21_dnd_automation" to { push(Page("Study, timer and focus") { study() }) },
                "x25_launcher_shortcuts" to { Plus.shortcuts(c); toast("Long-press the Mission icon to see shortcuts") },
                "x27_sleep_winddown" to { Auto.open(c) },
                "x29_weather_water" to { push(Page("Water") { water() }) },
                "x30_air_advisory" to { Auto.open(c) },
                "x33_workspaces" to { Plus.workspaces(c) },
                "x39_app_sort" to { toast("Open the app drawer: tabs Social, Media, Tools, Google, Other sort apps automatically") },
                "x42_themes" to { push(Page("Appearance") { appearance() }) },
                "x46_offline_pyq" to { push(Page("Zite and data") { zite() }) },
                "x48_calendar_sync" to { toast("Targets card: Add to calendar") },
                "x24_qs_tiles" to { toast("Edit Quick Settings, add Mission Quick capture and Mission Timer tiles") },
                "x45_greeting" to { toast("Always on: the greeting shows on the home header") },
                "x04_affairs" to { Toolkit.caDialog(c) },
                "x05_weekly_review" to { Toolkit.reportDialog(c) },
                "x06_smart_plan" to { Toolkit.topDialog(c) },
                "x14_streak_freeze" to { Toolkit.streakBoard(c) },
                "x15_goal_rings" to { Toolkit.pointsDialog(c) },
                "x19_clipboard_notes" to { Toolkit.clipSave(c) },
                "x22_automation" to { Auto.open(c) },
                "x23_automation_templates" to { Auto.open(c) },
                "x26_app_limits" to { push(Page("Limits and mindfulness") { limits() }) },
                "x28_briefs" to { push(Page("Daily briefs") { briefs() }) },
                "x31_report_share" to { Toolkit.reportDialog(c) },
                "x44_streak_heatmap" to { Toolkit.streakBoard(c) },
                "x49_today_dashboard" to { Toolkit.open(c) },
                "x50_diagnostics" to { push(Page("About and diagnostics") { about() }) },
            )
        val live = FeatureRegistry.features.filter { it.id in open }
        group(*live.map { f -> action("sparkle", T.sage, f.title, "Works now. Tap to open") { open[f.id]?.invoke() } }.toTypedArray())
        label("Switches")
        FeatureRegistry.features.filter { it.id !in open }.forEach { feature ->
            if (feature.id == "x03_answer_timer" && !mainsOn) return@forEach
            if (feature.id == "x47_google_signin") {
                if (FeatureRegistry.enabled(feature)) FeatureRegistry.setEnabled(feature, false)
                group(
                    action("login", T.slate, "Google sign-in setup", "OAuth project and account backend required") {
                        Dlg.info(this@SettingsActivity, "Google sign-in is not configured", "A real sign-in needs a Google OAuth client ID and a defined account backend. This repository has neither. A device account picker would not authenticate a user or safely enable cloud sync, so Mission will not report a false signed-in state.")
                    },
                )
                return@forEach
            }
            val description =
                when (feature.id) {
                    "x12_autopause_grace" -> "Wait briefly before auto-pausing when a distracting app opens"
                    "x13_idle_detection" -> "Pause the timer when the screen locks; resume after unlock"
                    "x18_voice_note" -> "Dictate notes with the device speech service; audio is not retained"
                    "x20_notification_digest" -> "Summarize saved important alerts every six hours; notification access is required"
                    "x32_home_presets" -> "Apply a built-in home layout or save and restore your own"
                    "x34_keyboard_spotlight" -> "Press Search or type on a hardware keyboard to find apps"
                    "x35_stylus_note" -> "Use the stylus barrel button on the home screen for a note"
                    "x36_right_panel" -> "Open home shortcuts from the right edge"
                    "x37_hot_corners" -> "Assign a launcher shortcut to any screen corner"
                    "x38_wake_actions" -> "Run your configured double-tap action while the home screen is visible"
                    "x40_app_lock" -> "Require the launcher PIN before opening selected apps from Mission"
                    "x41_icon_pack" -> "Use classic appfilter.xml icon packs when available"
                    "x43_clock_styles" -> "Change the home-screen clock; Android lock-screen styling is system-owned"
                    else -> "Feature toggle · ${feature.id}"
                }
            group(
                sw(
                    feature.title,
                    description,
                    feature.id,
                    feature.defaultEnabled,
                ) { enabled ->
                    FeatureRegistry.setEnabled(feature, enabled)
                    if (feature.id == "x20_notification_digest") NotificationDigestWorker.setEnabled(this@SettingsActivity, enabled)
                    if (feature.id == "x13_idle_detection" && !enabled && Store.b("timer_idle_paused")) {
                        TimerState.resume()
                        Store.put("timer_idle_paused", false)
                    }
                },
            )
            when (feature.id) {
                "x12_autopause_grace" ->
                    group(
                        num("Auto-pause grace", "autopause_grace_seconds", 10, 0, 120, "sec", "0 pauses immediately"),
                    )
                "x18_voice_note" ->
                    group(
                        action("mic", T.rose, "Create a voice note", "Dictate or type; only the transcript is saved") {
                            startActivity(Intent(this@SettingsActivity, VoiceNoteActivity::class.java).putExtra("start_voice", true))
                        },
                    )
                "x32_home_presets" ->
                    group(
                        action("layers", T.olive, "Apply home preset", Store.s("home_preset_custom_name", "My home")) {
                            val options = HomePresets.names + listOf(Store.s("home_preset_custom_name", "My home"), "Save current as My home")
                            Dlg.choose(this@SettingsActivity, "Home preset", options) { index ->
                                when {
                                    index < HomePresets.names.size -> HomePresets.apply(HomePresets.names[index])
                                    index == HomePresets.names.size -> {
                                        if (!HomePresets.applyCustom()) toast("Save a custom home layout first")
                                    }
                                    else -> {
                                        HomePresets.saveCustom("My home")
                                        toast("Current home saved")
                                    }
                                }
                                redraw()
                            }
                        },
                    )
                "x37_hot_corners" ->
                    group(
                        *listOf("Top left", "Top right", "Bottom left", "Bottom right").mapIndexed { index, title ->
                            choice(title, "hot_corner_$index", "none", Gestures.options)
                        }.toTypedArray(),
                    )
                "x38_wake_actions" ->
                    group(
                        choice("Double tap action", "wake_action", Store.s("g_dtap", "lock"), Gestures.options),
                    )
                "x40_app_lock" -> {
                    if (FeatureRegistry.enabled(feature)) {
                        group(
                            action("lock", T.plum, "Choose locked apps", "${Store.stringList("locked_apps").size} selected") {
                                if (!Pin.isSet()) {
                                    toast("Set a launcher PIN first")
                                    startActivity(Intent(this@SettingsActivity, SettingsActivity::class.java).putExtra("path", "security"))
                                } else {
                                    multiPick("Lock apps from Mission", "locked_apps")
                                }
                            },
                        )
                        note("App lock protects launches started by Mission only. Android cannot prevent another launcher or an app's own deep link from opening the same app.")
                    }
                }
                "x41_icon_pack" ->
                    group(
                        action("grid", T.slate, "Choose icon pack", Store.s("icon_pack").ifBlank { "System icons" }) {
                            Apps.load(this@SettingsActivity) {
                                val packs = IconPacks.available(this@SettingsActivity)
                                val options = listOf("System icons") + packs.map { it.label }
                                Dlg.choose(this@SettingsActivity, "Icon pack", options) { index ->
                                    Store.put("icon_pack", packs.getOrNull(index - 1)?.packageName.orEmpty())
                                    FeatureRegistry.setEnabled(feature, index > 0)
                                    IconPacks.invalidate()
                                    Apps.load(this@SettingsActivity) { Store.changed() }
                                    redraw()
                                }
                            }
                        },
                    )
                "x43_clock_styles" ->
                    group(
                        choice("Home clock style", "clock_style", "classic", listOf("classic" to "Classic", "compact" to "Compact", "bold" to "Bold")),
                    )
            }
        }
    }

    // ---------- Zite ----------
    private fun LinearLayout.zite() {
        val c = this@SettingsActivity
        label("Connection")
        group(
            secret("API key", { SecureStore.get("zite") }, {
                SecureStore.put("zite", it)
                Zite.invalidate()
            }, "Zite > Settings > API keys. Encrypted with Android Keystore."),
            value("Database id", null, Zite.db().take(10) + "...") {
                Dlg.input(c, "Database id", Zite.DEFAULT_DB, Zite.db()) { s ->
                    Store.put("zite_db", s.ifBlank { Zite.DEFAULT_DB })
                    redraw()
                }
            },
            value("Portal link", "Opened by Quick links", Store.s("zite_portal", Zite.PORTAL).removePrefix("https://").take(26) + "...") {
                Dlg.input(c, "Portal URL", "https://", Store.s("zite_portal", Zite.PORTAL)) { s ->
                    Store.put("zite_portal", s.ifBlank { Zite.PORTAL })
                    redraw()
                }
            },
        )
        label("Sync")
        val last = Store.l("zite_last_ok", 0)
        val state =
            when (Zite.syncState()) {
                Zite.SyncState.LIVE -> "Live"
                Zite.SyncState.SYNCING -> "Syncing"
                Zite.SyncState.STALE -> "Stale"
                Zite.SyncState.ERROR -> "Last sync failed or was partial"
                Zite.SyncState.CACHED -> "Cached data"
                Zite.SyncState.NEVER -> "Not synced"
                Zite.SyncState.SNAPSHOT -> "Built-in snapshot"
            }
        val lastAt =
            if (last > 0) {
                java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH).apply {
                    timeZone = java.util.TimeZone.getTimeZone(Repo.zone())
                }.format(java.util.Date(last)) + " IST"
            } else {
                "No successful sync yet"
            }
        group(
            action("refresh", T.sage, "Sync now", "$state - last successful: $lastAt") {
                if (!Zite.hasKey()) {
                    toast("Add the API key first")
                } else {
                    toast("Syncing...")
                    Thread {
                        val m = Zite.sync(c, true)
                        runOnUiThread {
                            toast(m)
                            redraw()
                        }
                    }.start()
                }
            },
        )
        note(
            "This is a pull-only sync: syllabus, mocks, attempts, responses, and PYQs refresh in the background about every 15 minutes when connected (Android may defer work for battery). Manual sync is queued until a network is available. The current Zite API integration has no push endpoint, so it cannot provide instant or two-way account sync.",
        )
        if (Store.s("zite_last_err").isNotBlank()) {
            label("Last error")
            note(Store.s("zite_last_err"))
        }
    }

    // ---------- AI ----------
    private fun LinearLayout.ai() {
        val c = this@SettingsActivity
        val p = Ai.provider()
        label("Provider")
        group(
            value("Active provider", null, p.name) {
                Dlg.choose(c, "AI provider", Ai.providers.map { it.name }) { i ->
                    Store.put("ai_provider", Ai.providers[i].id)
                    redraw()
                }
            },
            secret(p.name + " API key", {
                Ai.key(p.id)
            }, { Ai.setKey(p.id, it) }, if (p.id == "gemini") "Free key: aistudio.google.com/apikey" else "Stored only on this device"),
            value("Model", "Pick from your key's live list", Ai.model(p).take(26)) {
                if (!Ai.ready(p)) {
                    toast("Add the key first")
                } else {
                    toast("Loading models...")
                    Thread {
                        try {
                            val m = Ai.fetchModels(p)
                            runOnUiThread {
                                Dlg.choose(c, "Model", m) { i ->
                                    Store.put("ai_model_${p.id}", m[i])
                                    redraw()
                                }
                            }
                        } catch (
                            e: Exception,
                        ) {
                            runOnUiThread { toast(e.message ?: "Failed") }
                        }
                    }.start()
                }
            },
            action(null, 0, "Type a model name manually", Ai.model(p)) {
                Dlg.input(c, "Model id", p.model, Ai.model(p)) { s ->
                    Store.put("ai_model_${p.id}", s)
                    redraw()
                }
            },
        )
        if (p.needsBase) {
            group(
                value("Base URL", "OpenAI-compatible endpoint", Ai.base(p).removePrefix("https://").take(24)) {
                    Dlg.input(c, "Base URL", p.base, Ai.base(p)) { s ->
                        Store.put("ai_base_${p.id}", s)
                        redraw()
                    }
                },
            )
        }
        label("Behaviour")
        group(sw("Google Gemini can act on your data", "Add tasks, notes, water, events, start timer from chat", "ai_tools", true))
        label("Other assistants on this phone")
        group(
            action("sparkle", T.plum, "Open Xiaomi / system assistant", "Hyper AI / Google Assistant, whichever is the default") {
                if (!Ai.assist(c)) toast("No assistant found")
            },
            *Ai.installedAiApps(c).map {
                    a ->
                action(null, 0, a.label, "Open app") { if (!Ai.openApp(c, a.pkg)) toast("Cannot open") }
            }.toTypedArray().ifEmpty { arrayOf(note0("No other AI apps detected")) },
        )
        note(
            "Xiaomi's Hyper AI has no public API, so it is used through the system assistant button and the share sheet. Keys for Google Gemini, OpenAI ChatGPT, Anthropic Claude, or any OpenAI-compatible service work inside the launcher.",
        )
    }

    private fun note0(s: String): View = text(s, 13f, 0, T.text3).apply { padDp(14, 12, 14, 12) }

    // ---------- Water ----------
    private fun LinearLayout.water() {
        val c = this@SettingsActivity
        label("Reminders")
        group(
            sw("Water reminders", "Persistent notification until you act on it", "water_on", true) {
                Water.arm(c, true)
                if (!it) Water.clear(c)
            },
            sw("Insist until I drink", "If dismissed it returns, getting firmer each time", "water_insist", true),
            num("Daily goal", "water_goal", 3000, 500, 8000, "ml") { Water.arm(c, true) },
            value("Start time", "IST (HH:mm)", "%02d:%02d".format(Water.startTime().hour, Water.startTime().minute)) {
                Dlg.time(c, Water.startTime()) {
                    Store.put("water_start_time", it)
                    Water.arm(c, true)
                    redraw()
                }
            },
            value("End time", "IST (HH:mm)", "%02d:%02d".format(Water.endTime().hour, Water.endTime().minute)) {
                Dlg.time(c, Water.endTime()) {
                    Store.put("water_end_time", it)
                    Water.arm(c, true)
                    redraw()
                }
            },
            action("layers", T.teal, "Floating water card", if (c.canOverlay()) "Allowed" else "Allow display over other apps") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (
                    e: Exception,
                ) {
                    Log.e("water-overlay-settings", e)
                    toast("Could not open overlay settings")
                }
            },
            num("Remind every", "water_interval", 90, 20, 240, "min") { Water.arm(c, true) },
            num("Nag again after", "water_nag", 8, 2, 30, "min"),
        )
        label("Quick amounts")
        group(
            value("Cup sizes", "Shown as buttons in the notification and card", Water.cups().joinToString(", ") + " ml") {
                Dlg.input(c, "Cup sizes (ml, comma separated)", "150,250,500", Water.cups().joinToString(",")) { s ->
                    Store.put("water_cups", s)
                    redraw()
                }
            },
        )
        label("Test")
        group(action("bell", T.teal, "Send a reminder now", "Check the notification, buttons and floating card") { Water.remind(c, false) })
        note("Water history is part of the daily backup (device and your chosen Drive folder).")
    }

    // ---------- Study ----------
    private fun LinearLayout.study() {
        val c = this@SettingsActivity
        label("Daily goal")
        group(num("Study goal", "study_goal_min", 360, 30, 1000, "min"))
        label("Strict study slot")
        group(
            sw("Strict focus during a slot", "While a target's study slot is open only study apps and pinned apps work. Needs the accessibility service (Circle to Search section)", "strict_slot", true),
            num("Emergency unlock time", "strict_unlock_min", 15, 5, 60, "min", "Typing the random 15 character code unlocks for this long"),
        )
        note("Settings and every other app are blocked in the slot. Add your study apps in Study apps, and pin the ones you need on the dock.")
        label("Timer")
        group(
            num("Pomodoro focus", "pomo_work", 25, 5, 120, "min"),
            num("Pomodoro break", "pomo_break", 5, 1, 60, "min"),
            sw("Auto-pause on distraction apps", "Pauses on YouTube, music, reels and your distraction list. Needs Usage access", "timer_auto_pause", true) {
                if (it && !Usage.granted(c)) {
                    Dlg.confirm(
                        c,
                        "Allow Usage access",
                        "Usage access is needed to tell study apps from video and music apps.",
                        "Open settings",
                    ) {
                        Usage.openSettings(c)
                    }
                }
            },
            sw("YouTube counts as study", "Turn on only when you watch lectures. Off = YouTube pauses the timer", "yt_counts_study", false) { },
            action("layers", T.teal, "Floating timer permission", if (c.canOverlay()) "Allowed" else "Needed to float over other apps") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            },
        )
        label("Focus mode")
        group(
            sw("Do Not Disturb during focus", "Needs Do Not Disturb access", "focus_dnd", true) {
                if (it) {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } catch (
                        e: Exception,
                    ) {
                    }
                }
            },
            action(
                "grid",
                T.amber,
                "Apps allowed during focus",
                "${Apps.study().size} study apps",
            ) { multiPick("Study apps", "study_apps") },
        )
        label("Distractions")
        group(
            action("bell", T.rose, "Muted notification apps", "${Notifs.block().size} apps muted (saved to Important log, not deleted)") {
                multiPick("Mute these apps", "block_apps", Notifs.block())
            },
            action(
                "bell",
                T.sage,
                "Always-allowed apps",
                "${Notifs.keep().size} apps",
            ) { multiPick("Never mute these", "keep_apps", Notifs.keep()) },
            action("shield", T.slate, "Notification access", "Required for muting") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (
                    e: Exception,
                ) {
                }
            },
            action(
                "clock",
                T.clay,
                "Usage access",
                if (Usage.granted(c)) "Granted" else "Needed for screen-time card",
            ) { Usage.openSettings(c) },
        )
    }

    // ---------- Exam ----------
    private fun LinearLayout.exam() {
        val c = this@SettingsActivity
        group(
            value("Main exam name", null, Store.s("exam_name", "UPSC Prelims 2027")) {
                Dlg.input(c, "Exam name", "e.g. UPSC Prelims 2027", Store.s("exam_name", "UPSC Prelims 2027")) { n ->
                    if (n.isNotBlank()) Store.put("exam_name", n)
                    redraw()
                }
            },
            value("Main exam date", null, Store.s("exam_date", "2027-05-23")) {
                Dlg.date(c, Repo.examDate()) { d ->
                    Store.put("exam_date", d.toString())
                    redraw()
                }
            },
            action("sparkle", T.sage, "Add balanced weekly template", "Five study blocks a day") { Cards.template(c) },
        )
        note("The countdown, lock-screen text and AI context use this date.")
    }

    // ---------- Wallpaper ----------
    private fun LinearLayout.wallpaper() {
        val c = this@SettingsActivity
        label("Live wallpaper")
        group(
            action(
                "image",
                T.sage,
                "Set live wallpaper",
                if (Wallpaper.liveActive(c)) "Active live wallpaper" else "Open Android wallpaper chooser",
            ) {
                if (!Wallpaper.launchLiveWallpaper(c)) toast("Could not open the wallpaper chooser")
            },
            action("refresh", T.teal, "Apply now", "Refresh static fallback or live boundary") {
                Thread {
                    val ok = Wallpaper.apply(c, true)
                    runOnUiThread { toast(if (ok) "Wallpaper updated" else "Static fallback paused or unavailable") }
                }.start()
            },
            sw("Animation", "30 fps maximum while visible", "wp_animation", true),
            slider("FPS cap", "wp_fps", 30, 30, " fps", 1),
            sw("Live weather", "Open-Meteo · Prayagraj · no API key", "wp_weather", true) { Weather.refreshIfNeeded(c, force = it) },
        )

        fun updateWeather() {
            Wallpaper.scheduleNext(c)
            Thread {
                if (Weather.enabled()) Weather.refreshIfNeeded(c, force = true)
                val applied = Wallpaper.enabled() && Wallpaper.apply(c, true, refreshWeather = false)
                runOnUiThread {
                    toast(if (applied) "Wallpaper applied · ${Weather.state()}" else Weather.state())
                    redraw()
                }
            }.start()
        }
        label("Automation")
        group(
            sw("Auto wallpaper", "Sky, light and tint follow the time of day", "wp_sys", true) { Wallpaper.scheduleNext(c) },
            sw("Follow the clock", "Off = freeze at the hour below", "wp_auto", true) { redraw() },
            if (!Store.b("wp_auto", true)) num("Frozen hour", "wp_fixed_hour", 12, 0, 23, "h") else note0("Smooth change, never abrupt"),
            num("Update every", "wp_interval", 30, 15, 240, "min", "Android may defer background alarms", { Wallpaper.scheduleNext(c) }),
            action("image", T.slate, "Apply now", "Renders at full resolution") {
                toast("Rendering...")
                Thread {
                    val ok = Wallpaper.apply(c, true)
                    runOnUiThread { toast(if (ok) "Wallpaper applied" else "Could not set wallpaper") }
                }.start()
            },
        )
        label("Weather-aware rendering")
        group(
            sw("Weather effects", "Use modeled fog, clouds, rain and snow in procedural scenes", "wp_weather", true) { updateWeather() },
            choice(
                "Weather city",
                "wp_weather_city",
                "prayagraj",
                Weather.cities.map { it.id to it.label },
                "Fixed city; no location permission",
            ) {
                Store.put("wp_weather_error", "")
                updateWeather()
            },
            value("Weather status", "Current modeled conditions", Weather.state()) {
                Dlg.info(
                    c,
                    "Weather status",
                    "${Weather.state()}\n${Weather.lastFetchedLabel()}\n\nOpen-Meteo conditions are model-based, not sensor-level real-time.",
                )
            },
            action("refresh", T.teal, "Refresh weather now", Weather.lastFetchedLabel()) { updateWeather() },
        )
        note(
            "Weather data by Open-Meteo. Current conditions use 15-minute model data; attribution is required under CC BY 4.0, and the free API is for non-commercial use.",
        )
        label("Scene")
        group(
            choice(
                "Scene",
                "wp_scene",
                "sangam",
                listOf("online" to "Real photos (online, free)", "sangam" to "Prayagraj Sangam (4K, live)", "hills" to "Misty hills", "dunes" to "Soft dunes", "forest" to "Pine forest", "ocean" to "Ocean horizon", "waves" to "Calm waves", "gradient" to "Aurora gradient", "paper" to "Warm paper", "amoled" to "Pure dark (OLED)", "photo" to "My photos"),
            ) {
            },
            choice("Photo source", "online_src", "wiki", listOf("wiki" to "Wikimedia Commons: Prayagraj photos", "bing" to "Bing photo of the day (India)", "apod" to "NASA astronomy picture of the day", "picsum" to "Lorem Picsum: free nature and city photos")),
            sw("Edge sharpening", "Detects edges and sharpens only them, so photos look crisper (8K feel)", "wp_sharpen", true) { Wallpaper.apply(this@SettingsActivity, true) },
            secret("NASA API key (optional)", { SecureStore.get("nasa") }, { SecureStore.put("nasa", it) }, "Free at api.nasa.gov, removes the shared DEMO_KEY limit"),
            action("refresh", T.teal, "Download photos now", if (Online.credit().isBlank()) "Needs internet. ${Online.files(this@SettingsActivity).size} saved" else Online.credit()) {
                toast("Downloading...")
                Online.refreshAsync(this@SettingsActivity, true) { n -> runOnUiThread { toast(if (n > 0) "$n photos ready, choose Apply now" else "No photo could be downloaded") ; redraw() } }
            },
            choice("Apply to", "wp_target", "both", listOf("both" to "Home and lock", "home" to "Home only", "lock" to "Lock only")),
        )
        label("Eye comfort")
        group(
            sw("Warm tint at night", "Reduces blue light in the evening", "wp_comfort", true),
            sw("Film grain", "Prevents colour banding on gradients", "wp_grain", true),
            sw("Seasonal touches", "Gentle changes through the year", "wp_season", true),
            slider("Dim", "wp_dim", 6, 40),
            sw("Parallax with pages", "Wallpaper drifts as you swipe", "wp_parallax", true),
        )
        label("Smart pausing")
        group(
            sw("Static fallback pauses in battery saver", "Live mode keeps a static frame instead", "wp_battery_pause", true),
            sw("Countdown on lock screen", "Shows days to Prelims", "wp_lock_text", true),
        )
        label("My photos")
        group(
            action("plus", T.sage, "Add photo", "${Wallpaper.photos(c).size} saved") {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
                    REQ_PHOTO,
                )
            },
            choice(
                "Rotation",
                "wp_photo_mode",
                "daily",
                listOf("daily" to "New photo daily", "hourly" to "New photo hourly", "single" to "First photo only"),
            ),
            slider("Blur", "wp_blur", 0, 24, ""),
        )
    }

    // ---------- Home screen ----------
    private fun LinearLayout.homeScreen() {
        val c = this@SettingsActivity
        group(
            action("sliders", T.olive, "Edit home layout", "Move and add cards; resize Android widgets") {
                setResult(RESULT_OK)
                startActivity(
                    Intent(
                        c,
                        MainActivity::class.java,
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ).putExtra("edit", true).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                )
                finish()
            },
            sw("Hide bottom navigation bar", "Removes the gesture pill on home. Swipe up from the bottom edge to show it briefly", "hide_nav", true),
            sw("Auto-hiding left edge handle", "Tap to open the app drawer; hidden after inactivity", "edge_handle", false),
            sw("Lock home layout", "Prevent accidental add, remove and move actions", "lock_home_layout", false),
            sw("Launcher haptics", "Use light confirmation feedback for launcher actions", "launcher_haptics", true),
            choice(
                "Haptic character",
                "launcher_haptic_style",
                "crisp",
                listOf("soft" to "Soft (iOS-like light)", "crisp" to "Crisp (precise touch)",
                ),
            ),
            sw("Reduced motion", "Disable large pager and interface animations", "reduce_motion", false),
            sw("Two-finger quick capture", "Pinch with two fingers on home to open Quick Capture", "two_finger_capture", true),
            choice(
                "Bottom swipe action",
                "bottom_gesture",
                "drawer",
                listOf(
                    "drawer" to "Open app drawer",
                    "recents" to "Open recent apps",
                    "right" to "Open right control panel",
                    "note" to "Create quick note",
                    "chat" to "Open Mission AI",
                ),
            ),
            num("Bottom swipe sensitivity", "gesture_sensitivity", 130, 70, 220, "dp", "Smaller value opens the action with a shorter swipe"),
        )
        note(
            "New installs start with empty home pages. Tap the sliders icon on the home header or long-press the page dots to choose your cards. Pins are set under Apps and dock.",
        )
    }

    // ---------- Apps ----------
    private fun LinearLayout.apps() {
        group(
            action(
                "grid",
                T.clay,
                "Dock apps",
                Apps.pins().mapNotNull {
                    Apps.byPkg(it)?.label
                }.joinToString(", ").ifBlank { "None" },
            ) { multiPick("Dock apps (max 5)", "pins", max = 5) },
            action("close", T.text2, "Hidden apps", "${Apps.hidden().size} hidden. Shown under the Hidden tab in the drawer") {
                multiPick("Hide from drawer", "hidden_apps")
            },
            action("book", T.sage, "Study apps", "${Apps.study().size} apps in Study tab and allowed in focus") {
                multiPick("Study apps", "study_apps")
            },
            action("clock", T.slate, "Clear recent apps", "${LauncherRecents.packages().size} remembered") {
                Dlg.confirm(this@SettingsActivity, "Clear launcher recents?", "Search and launch suggestions will no longer show old apps.", "Clear") {
                    Store.put("launcher_recents", "")
                    Store.changed()
                    redraw()
                }
            },
            action("search", T.plum, "Clear search history", "${SearchHistory.entries().size} searches saved") {
                Dlg.confirm(this@SettingsActivity, "Clear app search history?", "Only recent search suggestions will be cleared.", "Clear") {
                    Store.put("launcher_search_history", "")
                    Store.changed()
                    redraw()
                }
            },
        )
        label("Memory cleaner")
        group(
            sw("Auto clear unused apps", "Every 30 minutes stops background apps you have not used (needs Usage access)", "clean_auto", true) { Cleaner.schedule(this@SettingsActivity) },
            num("Idle minutes", "clean_idle", 30, 10, 240, "min", "An app is cleared only if unused for this long"),
            sw("Show a message after cleaning", null, "clean_toast", false),
            action("refresh", T.teal, "Clean now", "Last run cleared ${Store.i("clean_count", 0)} apps") {
                if (!Usage.granted(this@SettingsActivity)) {
                    toast("Grant usage access first")
                    Usage.openSettings(this@SettingsActivity)
                } else {
                    Thread {
                        val n = Cleaner.run(this@SettingsActivity)
                        runOnUiThread {
                            toast("Cleared $n idle apps")
                            redraw()
                        }
                    }.start()
                }
            },
            action("refresh", T.danger, "Restart + clean everything", "Password protected: deletes cache, stops idle apps, restarts Mission") { Restart.ask(this@SettingsActivity) },
            action("shield", T.sage, "Never clear these", "${Store.stringList("clean_keep").size} apps kept") { multiPick("Keep running", "clean_keep") },
        )
        note("Only background apps are stopped. Android or HyperOS may restart some of them. Telegram, WhatsApp, Flexcil, Gemini, music apps, keyboard and dialer are always kept.")
        if (FeatureRegistry.enabled("x40_app_lock")) {
            group(
                action("lock", T.plum, "Locked apps", "${Store.stringList("locked_apps").size} protected from Mission") {
                    if (!Pin.isSet()) {
                        toast("Set a launcher PIN first")
                        startActivity(Intent(this@SettingsActivity, SettingsActivity::class.java).putExtra("path", "security"))
                    } else {
                        multiPick("Lock apps from Mission", "locked_apps")
                    }
                },
            )
        }
        note("Recent apps are never shown on the home screen.")
    }

    // ---------- Appearance ----------
    private fun LinearLayout.appearance() {
        val c = this@SettingsActivity
        group(
            sw("Compact cards", "Smaller cards, 3 columns on the tablet", "compact_cards", true) { recreate() },
            choice(
                "Theme",
                "theme",
                "auto",
                listOf(
                    "auto" to "Follow system",
                    "light" to "Light (warm paper)",
                    "dark" to "Dark (soft)",
                    "schedule" to "Dark on a schedule",
                ),
            ) {
                T.init(c.applicationContext)
                recreate()
            },
            num("Dark from", "dark_from", 19, 0, 23, "h", "Used when theme is a schedule") {
                T.init(c.applicationContext)
            },
            num("Light from", "dark_to", 6, 0, 23, "h") { T.init(c.applicationContext) },
            num(
                "Drawer columns",
                "drawer_cols",
                0,
                0,
                16,
                "",
                "0 = automatic (fits the screen)",
            ),
            num(
                "Drawer icon size",
                "drawer_icon",
                30,
                20,
                88,
                "dp",
            ),
            num("Dock opacity", "dock_alpha", 45, 10, 100, "%", "Lower is more transparent (glass look)"),
            num("Pinned app icon size", "dock_icon", 15, 10, 72, "dp"), sw("Drawer app names", null, "drawer_labels", true),
            slider("Text size", "text_scale", 100, 130, min = 80) {
                T.init(c.applicationContext)
                recreate()
            },
            slider("Interface size", "ui_scale", 100, 130, min = 80) {
                T.init(c.applicationContext)
                recreate()
            },
            num("Study graph height", "history_chart_height", 160, 80, 240, "dp", "Adjust the study-history chart size"),
        )
        note("Soft warm neutrals with sage and clay accents; no strong blues. Inter is bundled so text renders the same on every device.")
    }

    // ---------- Security ----------
    private fun LinearLayout.security() {
        val c = this@SettingsActivity
        label("PIN")
        group(
            action("lock", T.plum, if (Pin.isSet()) "Change PIN" else "Set PIN", "4 to 8 digits") { setPin(false) },
            if (Pin.isSet()) {
                action("trash", T.danger, "Remove PIN", null) {
                    verifyThen {
                        Pin.clear()
                        redraw()
                    }
                }
            } else {
                note0("No PIN set")
            },
        )
        if (Pin.isSet()) {
            label("Where it applies")
            group(
                sw("Ask when opening the launcher", "Otherwise only for Settings", "pin_home", false),
                sw("Allow fingerprint / face", null, "pin_bio", true),
                num("Stay unlocked for", "pin_window", 10, 0, 120, "min", "0 = ask every time"),
            )
            note("Five wrong tries lock the keypad for 30 seconds, growing with each further miss.")
        }
    }

    private fun verifyThen(f: () -> Unit) {
        if (!Pin.isSet() || Pin.unlocked()) {
            f()
            return
        }
        val root = window.decorView as android.view.ViewGroup
        lateinit var pad: PinPad
        pad =
            PinPad(this, "Enter current PIN", Pin.len(), true, { code, p ->
                if (Pin.check(code)) {
                    root.removeView(p)
                    f()
                } else {
                    p.error(if (Pin.lockLeft() > 0) "Locked ${Pin.lockLeft()}s" else "Wrong PIN")
                }
            }, { root.removeView(pad) })
        root.addView(pad, android.view.ViewGroup.LayoutParams(-1, -1))
    }

    private fun setPin(confirmed: Boolean) {
        verifyThen {
            Dlg.choose(this, "PIN length", listOf("4 digits", "6 digits", "8 digits")) { i ->
                val len = intArrayOf(4, 6, 8)[i]
                val root = window.decorView as android.view.ViewGroup
                var first = ""
                lateinit var pad: PinPad
                pad =
                    PinPad(this, "Choose a $len-digit PIN", len, true, { code, p ->
                        if (first.isEmpty()) {
                            first = code
                            p.reset("Repeat the PIN")
                        } else if (first == code) {
                            Pin.set(code)
                            root.removeView(p)
                            toast("PIN saved")
                            redraw()
                        } else {
                            first = ""
                            p.error("Did not match. Start again")
                            p.reset("Choose a $len-digit PIN")
                        }
                    }, { root.removeView(pad) })
                root.addView(pad, android.view.ViewGroup.LayoutParams(-1, -1))
            }
        }
    }

    // ---------- Backup ----------
    private fun LinearLayout.backup() {
        val c = this@SettingsActivity
        val last = Store.l("backup_last_ms")
        label("Automatic")
        group(
            sw("Daily backup", "Everything: tasks, notes, water, sessions, settings", "backup_on", true),
            action(
                "cloud",
                T.teal,
                "Back up now",
                if (last > 0) "Last: " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(last)) + " (" + Store.s("backup_where") + ")" else "Never",
            ) {
                Thread {
                    val m = Backup.runNow(c)
                    runOnUiThread {
                        toast(m)
                        redraw()
                    }
                }.start()
            },
        )
        label("Google Drive (recommended)")
        val bf = Store.s("backup_file")
        group(
            action(
                "cloud",
                T.sage,
                if (bf.isBlank()) "Choose backup file in Drive" else "Change backup file",
                if (bf.isBlank()) "Pick Google Drive in the next screen, then Save. The same file is updated every day." else "Linked: " + (Uri.parse(bf).lastPathSegment ?: "file"),
            ) {
                startActivityForResult(
                    Intent(
                        Intent.ACTION_CREATE_DOCUMENT,
                    ).setType(
                        "application/json",
                    ).addCategory(
                        Intent.CATEGORY_OPENABLE,
                    ).putExtra(
                        Intent.EXTRA_TITLE,
                        "mission-backup.json",
                    ).addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                    ),
                    REQ_FILE,
                )
            },
            if (bf.isNotBlank()) {
                action("close", T.danger, "Stop Drive file backups", null) {
                    Store.put("backup_file", null)
                    redraw()
                }
            } else {
                note0("Google Drive does not allow folder picking, so one file is used.")
            },
        )
        label("Folder (device, SD card)")
        val tree = Store.s("backup_tree")
        group(
            action(
                "cloud",
                T.sage,
                if (tree.isBlank()) "Choose Drive folder" else "Change Drive folder",
                if (tree.isBlank()) "Pick a folder inside Google Drive in the next screen" else Uri.parse(tree).lastPathSegment,
            ) {
                startActivityForResult(
                    Intent(
                        Intent.ACTION_OPEN_DOCUMENT_TREE,
                    ).addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                    ),
                    REQ_TREE,
                )
            },
            if (tree.isNotBlank()) {
                action("close", T.danger, "Stop Drive backups", null) {
                    Store.put("backup_tree", null)
                    redraw()
                }
            } else {
                note0("Uses your signed-in Google account through the system picker. No keys are stored.")
            },
        )
        note("Android Auto Backup also copies the launcher data to your Google account when phone backup is on.")
        label("Files")
        group(
            action("up", T.slate, "Export to file", "Save a copy anywhere") {
                startActivityForResult(
                    Intent(
                        Intent.ACTION_CREATE_DOCUMENT,
                    ).setType("application/json").putExtra(Intent.EXTRA_TITLE, "mission-backup-${LocalDate.now(Repo.zone())}.json"),
                    REQ_EXPORT,
                )
            },
            action("up", T.slate, "Export study hours (CSV)", "Opens in Excel or Sheets") {
                startActivityForResult(
                    Intent(
                        Intent.ACTION_CREATE_DOCUMENT,
                    ).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "study-hours-${LocalDate.now(Repo.zone())}.csv"),
                    REQ_CSV,
                )
            },
        )
        label("Local copies")
        val files = Backup.localFiles(c)
        if (files.isEmpty()) {
            group(note0("None yet"))
        } else {
            group(note0("${files.size} local copies. Restore only from Settings > Reset and data."))
        }
    }

    // ---------- Reset and data ----------
    private fun LinearLayout.resetData() {
        val c = this@SettingsActivity
        if (!ResetGate.isSet()) {
            label("Reset password")
            note("Set a 6 to 12 digit password. It is stored as a salted PBKDF2 hash and is never included in backups.")
            ResetGate.ensureSet(c) { redraw() }
            return
        }

        note("Every deletion first saves an automatic backup. A reset password is required for each action.")
        label("Per-cache deletion")
        group(
            action("timer", T.amber, "Study sessions", "${Store.rows("sessions").size} saved sessions") {
                gatedReset("Delete study sessions?") { Store.clear("sessions") }
            },
            action("checkc", T.sage, "Syllabus ticks", "Clear all manual syllabus progress") {
                gatedReset("Delete all syllabus ticks?") { Store.setStringList("syl_done", emptyList()) }
            },
            action("drop", T.teal, "Water today", "${Water.todayEntries().size} entries") {
                gatedReset("Delete today's water entries?") {
                    Water.todayEntries().forEach { Water.remove(it.optString("id")) }
                    Water.clear(c)
                    Water.arm(c, true)
                }
            },
            action("sparkle", T.plum, "AI chat", "${Ai.history().size} saved messages") {
                gatedReset("Delete AI chat history?") { Ai.clearHistory() }
            },
            action("calendar", T.terra, "Schedule", "${Store.rows("schedule").size} schedule blocks") {
                gatedReset("Delete the schedule?") { Store.clear("schedule") }
            },
            action("refresh", T.slate, "Zite cache", "Remove locally cached Zite data") {
                gatedReset("Delete the Zite cache?") {
                    Store.saveObj("cache_zite", org.json.JSONObject())
                    Zite.invalidate()
                }
            },
            action("image", T.slate, "Wallpaper photos", "${Wallpaper.photos(c).size} saved photos") {
                gatedReset("Delete saved wallpaper photos?") { Wallpaper.clearPhotos(c) }
            },
            action("grid", T.olive, "Home layout", "Reset app pages; keep Android widgets") {
                gatedReset("Reset app pages?") { HomeLayout.reset() }
            },
            action("grid", T.slate, "Remove Android widget", "${HomeLayout.widgetIds().size} placed") { removeHomeWidget() },
        )

        label("Restore")
        group(
            action("down", T.slate, "Import backup", "Choose a file; current data is backed up first") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_IMPORT)
            },
        )
        val files = Backup.localFiles(c)
        if (files.isNotEmpty()) {
            group(
                *files.map { file ->
                    action(
                        "refresh",
                        T.sage,
                        file.name.removePrefix("mission-backup-").removeSuffix(".json"),
                        "${file.length() / 1024} KB",
                    ) {
                        gatedReset("Restore ${file.name}?") {
                            val result = Backup.restoreLocal(c, file)
                            if (result < 0) throw IllegalStateException("Backup restore failed")
                            Zite.invalidate()
                        }
                    }
                }.toTypedArray(),
            )
        }

        label("Final option")
        group(
            action("trash", T.danger, "Erase all launcher data", "Last resort; cannot be undone") {
                gatedReset("Erase all launcher data?") {
                    TimerService.stop(c)
                    Water.clear(c)
                    Sched.cancelWater(c)
                    Wallpaper.clearPhotos(c)
                    SecureStore.eraseAll()
                    Store.eraseAll()
                    Zite.invalidate()
                    Store.put("water_on", false)
                    Store.flushNow()
                }
            },
        )
    }

    private fun gatedReset(
        title: String,
        operation: () -> Unit,
    ) {
        Dlg.confirm(this, title, "A local backup is saved before changes are made.", "Continue") {
            ResetGate.verifyAndBackup(this) {
                try {
                    operation()
                    Store.changed()
                    toast("Backup saved. Reset complete.")
                    redraw()
                } catch (e: Exception) {
                    Log.e("reset-data", e)
                    toast("Reset failed: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun removeHomeWidget() {
        val c = this
        val ids = HomeLayout.widgetIds()
        if (ids.isEmpty()) {
            toast("No Android widgets are placed")
            return
        }
        val manager = AppWidgetManager.getInstance(c)
        val labels =
            ids.map { id ->
                manager.getAppWidgetInfo(id)?.loadLabel(packageManager)?.toString() ?: "Android widget #$id"
            }
        Dlg.choose(c, "Select a widget to remove", labels) { index ->
            val id = ids[index]
            gatedReset("Remove ${labels[index]}?") {
                check(HomeLayout.removeWidget(id)) { "Widget is no longer on the home screen" }
                AppWidgetHost(c, 1024).deleteAppWidgetId(id)
                Store.put("widget_height_$id", null)
            }
        }
    }

    // ---------- Permissions ----------
    private fun LinearLayout.permissions() {
        val c = this@SettingsActivity

        fun st(b: Boolean) = if (b) "Granted" else "Not granted"
        group(
            action("layers", T.teal, "Display over other apps", st(c.canOverlay()) + " - floating timer and water card") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            },
            action("clock", T.clay, "Usage access", st(Usage.granted(c)) + " - screen-time card") { Usage.openSettings(c) },
            action("bell", T.rose, "Notification access", "Mute distractions, keep important alerts") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (
                    e: Exception,
                ) {
                }
            },
            action("shield", T.plum, "Do Not Disturb access", "For focus mode") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (
                    e: Exception,
                ) {
                }
            },
            action("bell", T.amber, "App notifications", "Water, timer, backups") {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            },
            action("refresh", T.sage, "Alarms and reminders", "Exact water reminders") {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    } catch (
                        _: Exception,
                    ) {
                    }
                }
            },
            action(
                "bolt",
                T.gold,
                "Battery: do not optimise",
                "Keeps hourly sync and reminders on time (Xiaomi: also set Autostart and No restrictions)",
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (
                    e: Exception,
                ) {
                }
            },
            action("grid", T.slate, "Set as default home app", "Choose Mission as your launcher") {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (
                    e: Exception,
                ) {
                }
            },
        )
        note(
            "Xiaomi HyperOS may stop background apps: open Security > Apps > Manage apps > Mission > Autostart on, Battery saver: No restrictions.",
        )
    }

    // ---------- About ----------
    private fun LinearLayout.about() {
        val c = this@SettingsActivity
        group(
            value(
                "Version",
                null,
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                } catch (
                    e: Exception,
                ) {
                    ""
                },
            ) { },
            sw("Update by itself at night", "Installs new builds between 12 and 6 am on Wi-Fi (needs Install unknown apps)", "update_silent", true),
            action("link", T.slate, "Update source repo", Updater.repo()) {
                Dlg.input(c, "GitHub repo (owner/name)", "e.g. sure1234-del/Home-launcher-", Updater.repo()) { v ->
                    if (Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(v)) {
                        Store.put("update_repo", v)
                        toast("Updates will now come from $v")
                        redraw()
                    } else {
                        toast("Use the form owner/name")
                    }
                }
            },
            action("down", T.teal, "Check for update", "Downloads the newest build from GitHub (" + Updater.repo() + ")") {
                toast("Checking...")
                Thread {
                    val info = Updater.check(c)
                    runOnUiThread {
                        if (info == null) {
                            toast("You have the latest build (or no internet)")
                        } else {
                            Dlg.confirm(c, "Update available", "New build: " + info.name + " (" + info.size / 1048576 + " MB). Your data stays.", "Install") {
                                Updater.install(c, info) { m -> toast(m) }
                            }
                        }
                    }
                }.start()
            },
            sw("Check for updates automatically", "Every few hours, with a notification", "update_auto", true),
            value("Data stored", "On this device (app storage)", "${Store.rows("sessions").size} sessions") { },
            action(
                "info",
                T.text2,
                "Show recent error log",
                "For troubleshooting",
            ) { Dlg.info(c, "Log", Log.dump().ifBlank { "No errors" }) },
            action("shield", T.rose, "Diagnostics", "Crash, memory, process exits") { Dlg.info(c, "Diagnostics", diagnosticsText()) },
        )
    }

    private fun diagnosticsText(): String {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val crash =
            try {
                java.io.File(filesDir, "crash.log").takeIf { it.exists() }?.readText()?.take(12000)
            } catch (
                _: Exception,
            ) {
                null
            }
        val exits =
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                am.getHistoricalProcessExitReasons(packageName, 0, 8).joinToString("\n") {
                    "${it.reason}:${it.status} ${it.description ?: ""}"
                }
            } else {
                "Process exit reasons require Android 11+"
            }
        val rt = Runtime.getRuntime()
        val trim = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
        val trimLevel = trim.getInt("last_trim_level", -1)
        val trimAt = trim.getLong("last_trim", 0L)
        return "Memory: ${mi.availMem / 1048576} MB free / ${mi.totalMem / 1048576} MB total\n" +
            "Memory class: ${am.memoryClass} MB${if (am.isLowRamDevice) " (low-RAM)" else ""}\n" +
            "Heap: ${rt.totalMemory() / 1048576}/${rt.maxMemory() / 1048576} MB\n\n" +
            "Last trim: level $trimLevel at $trimAt\n\n" +
            "Process exits:\n$exits\n\nCrash log:\n${crash ?: "No crash log"}"
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(
        req: Int,
        res: Int,
        d: Intent?,
    ) {
        super.onActivityResult(req, res, d)
        val u = d?.data
        if (res != RESULT_OK || u == null) return
        when (req) {
            REQ_TREE -> {
                Backup.setTree(this, u)
                Thread {
                    val m = Backup.runNow(this)
                    runOnUiThread {
                        toast(m)
                        redraw()
                    }
                }.start()
            }
            REQ_FILE -> {
                Backup.setFile(this, u)
                Thread {
                    val m = Backup.runNow(this)
                    runOnUiThread {
                        toast(m)
                        redraw()
                    }
                }.start()
            }
            REQ_CSV ->
                toast(
                    if (try {
                            val output = contentResolver.openOutputStream(u, "wt") ?: throw IllegalStateException("Could not open CSV destination")
                            output.use { it.write(Repo.csv().toByteArray()) }
                            true
                        } catch (e: Exception) {
                            Log.e("csv-export", e)
                            false
                        }
                    ) {
                        "Exported"
                    } else {
                        "Export failed"
                    },
                )
            REQ_EXPORT -> toast(if (Backup.export(this, u)) "Exported" else "Export failed")
            REQ_IMPORT ->
                gatedReset("Import this backup?") {
                    val n = Backup.import(this, u, false)
                    if (n < 0) throw IllegalArgumentException("The selected file is not a valid backup")
                    Zite.invalidate()
                }
            REQ_PHOTO -> {
                toast(if (Wallpaper.addPhoto(this, u)) "Photo added" else "Could not read photo")
                redraw()
            }
        }
    }

    // ---------- Setup checklist ----------
    private class Item(val title: String, val sub: String, val ok: Boolean, val fix: () -> Unit)

    private fun setupItems(): List<Item> {
        val c = this
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val notif = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val listener =
            try {
                (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
            } catch (
                e: Exception,
            ) {
                false
            }
        val dnd =
            try {
                nm().isNotificationPolicyAccessGranted
            } catch (e: Exception) {
                false
            }
        val home =
            try {
                packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == packageName
            } catch (
                e: Exception,
            ) {
                false
            }

        fun open(
            a: String,
            data: Boolean = false,
        ) {
            try {
                startActivity(Intent(a).apply { if (data) setData(Uri.parse("package:$packageName")) })
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (x: Exception) {
                }
            }
        }
        return listOf(
            Item("Default home app", "Press Home and choose Mission, Always", home) { open(Settings.ACTION_HOME_SETTINGS) },
            Item("Notifications", "Water, timer, briefs", notif) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        5,
                    )
                } else {
                    open(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
            },
            Item(
                "Display over other apps",
                "Floating timer and water card",
                c.canOverlay(),
            ) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) },
            Item("Usage access", "Screen-time card and app limits", Usage.granted(c)) { Usage.openSettings(c) },
            Item("Notification access", "Mute distractions", listener) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            Item(
                "Accessibility service",
                "Circle to Search, double-tap lock, shade swipe",
                CircleService.running(),
            ) { CircleService.openSettings(c) },
            Item("Do Not Disturb access", "Focus mode", dnd) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            Item("Battery: no restrictions", "Hourly sync and reminders on time", pm.isIgnoringBatteryOptimizations(packageName)) {
                open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)
            },
            Item("Xiaomi Autostart", "Cannot be detected: open and switch Mission on", false) {
                val tries =
                    listOf(
                        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                    )
                var ok = false
                for (i in tries) {
                    try {
                        startActivity(i)
                        ok = true
                        break
                    } catch (e: Exception) {
                    }
                }
                if (!ok) open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
            },
        )
    }

    private fun LinearLayout.setup() {
        val items = setupItems()
        note("Tap any row marked Fix. This screen refreshes when you come back.")
        group(
            *items.map {
                    it ->
                rowBase(
                    null,
                    0,
                    it.title,
                    it.sub,
                    text(if (it.ok) "On" else "Fix", 13.5f, 3, T.textAccent(if (it.ok) T.sage else T.terra)),
                ) { it.fix() }
            }.toTypedArray(),
        )
    }

    override fun onResume() {
        super.onResume()
        if (stack.isNotEmpty()) redraw()
    }

    // ---------- Gestures ----------
    private fun LinearLayout.gestures() {
        label("Home gestures")
        group(
            choice("Double tap on home", "g_dtap", "lock", Gestures.options),
            choice("Swipe down on home", "g_down", "notif", Gestures.options),
            choice("Long-press empty area", "g_long", "edit", Gestures.options),
            choice("Swipe up on dock", "g_up", "drawer", Gestures.options),
        )
        note("Lock screen and notification gestures use the accessibility service. Turn it on under Circle to Search.")
        label("Full-screen gestures")
        group(
            sw("Edge swipe = Back", "Swipe in from the left or right edge, in any app, like the Redmi full-screen gesture. Needs the accessibility service", "edge_nav", true) { CircleService.inst?.updateEdges() },
            sw("Bottom swipe = Home / Recents", "Swipe up from the bottom centre for Home, swipe up and hold for Recents", "edge_bottom", true) { CircleService.inst?.updateEdges() },
            action("gear", T.sage, "Turn on system full-screen gestures", "Removes the bottom navigation buttons of the tablet") { NavGesture.enable(this@SettingsActivity) },
        )
        note("Android has no Forward action, so Back is the edge gesture. Turn on Mission under Accessibility (Circle to Search section) first.")
        label("Multitasking")
        group(
            choice(
                "Open apps from drawer in",
                "open_mode",
                "normal",
                listOf("normal" to "Full screen", "float" to "Floating window", "split" to "Split / adjacent"),
                "Long-press an app in the drawer to pick per app",
            ),
        )
        note(
            "Apps are started through the system launcher API, which keeps your other floating windows alive. The full-screen swipe-home gesture is drawn by HyperOS; Mission keeps system bars transparent and never adds its own gesture-exclusion zone.",
        )
    }

    // ---------- Circle to Search ----------
    private fun LinearLayout.circle() {
        val c = this@SettingsActivity
        group(
            action(
                "shield",
                T.plum,
                "Accessibility service",
                if (CircleService.running()) "Running" else "Off - tap, find Mission, switch on",
            ) {
                CircleService.openSettings(c)
            },
            action("shield", T.amber, "Switch is greyed out? (HyperOS)", "Open App info > three dots > Allow restricted settings, then come back") {
                CircleService.openAppInfo(c)
            },
            choice(
                "Where the circled area goes",
                "circle_target",
                "gemini",
                listOf("gemini" to "Gemini app (floating, UPSC Prelims 2027 tag)", "ask" to "Ask me every time (Gemini, Lens, share)"),
            ),
            sw("Floating Gemini window", "Small window on the right so your notes stay visible", "circle_float", true),
            num("Floating window width", "circle_w", 42, 30, 70, "%"),
            num("Floating window height", "circle_h", 62, 40, 90, "%"),
            sw("Press Send automatically", "Needs the accessibility service; Gemini starts searching at once", "circle_autosend", true),
            sw("Add 'UPSC Prelims 2027' tag", "Sent with every circled image so answers are exam focused", "circle_tag", true),
            value("Prompt sent with the image", null, CircleService.geminiPrompt().take(60) + "...") {
                Dlg.input(c, "Prompt", "Leave empty for the default", Store.s("circle_prompt"), multi = true) { v ->
                    Store.put("circle_prompt", v)
                    redraw()
                }
            },
            action("search", T.plum, "Try it now", "Freezes the screen so you can circle something") {
                Handler(Looper.getMainLooper()).postDelayed({ CircleService.capture(c) }, 400)
                moveTaskToBack(true)
            },
        )
        label("Ways to start")
        note(
            "1. Quick Settings tile: edit tiles and add Circle to Search.\n2. Accessibility button in the navigation bar (Settings > Accessibility > Mission > shortcut).\n3. Home gesture mapped under Gestures (double tap, long-press, swipe).\n4. Tools card on the Study page.\n\nThe circled area goes to the Gemini app with the UPSC Prelims 2027 tag (Lens is the fallback). Screens that block screenshots (banking, DRM video) cannot be captured. Xiaomi's own navigation-bar Circle to Search keeps working with its default launcher only.",
        )
    }

    // ---------- Limits ----------
    private fun LinearLayout.limits() {
        val c = this@SettingsActivity
        val lim = Guard.limits()
        label("Mindful pause")
        group(
            sw("Pause before distracting apps", "Short countdown and reminder of days left", "mindful", true),
            num("Pause length", "mindful_secs", 5, 3, 20, "sec"),
            action("grid", T.rose, "Distraction apps", "${Notifs.block().size} apps (also muted when notifying)") {
                multiPick("Distraction apps", "block_apps", Notifs.block())
            },
        )
        label("Daily limits")
        val keys = lim.keys().asSequence().toList()
        if (keys.isEmpty()) {
            group(note0("No limits yet"))
        } else {
            group(
                *keys.map {
                        k ->
                    value(Apps.byPkg(k)?.label ?: k, null, "${lim.optInt(k)} min") {
                        Dlg.input(c, "Limit (minutes, 0 removes)", "", lim.optInt(k).toString(), true) { v ->
                            Guard.setLimit(k, v.toIntOrNull() ?: 0)
                            redraw()
                        }
                    }
                }.toTypedArray(),
            )
        }
        group(
            action("plus", T.sage, "Add a limit", "Choose an app") {
                val l = Apps.list
                Dlg.choose(c, "App", l.map { it.label }) { i ->
                    Dlg.input(c, "Daily minutes for " + l[i].label, "e.g. 30", "", true) { v ->
                        v.toIntOrNull()?.let { Guard.setLimit(l[i].pkg, it) }
                        if (!Usage.granted(c)) Usage.openSettings(c)
                        redraw()
                    }
                }
            },
        )
        note("Limits are checked when you open an app from Mission. Usage access is required.")
    }

    // ---------- Briefs ----------
    private fun LinearLayout.briefs() {
        group(
            sw("Morning brief", "Days left, today's tasks, revisions", "brief_m_on", true),
            num("Morning hour", "brief_m_hour", 6, 0, 23, "h"),
            sw("Evening check-in", "Hours so far and open tasks", "brief_e_on", true),
            num("Evening hour", "brief_e_hour", 21, 0, 23, "h"),
            sw("Sunday weekly report", "Total hours, best day, water streak", "brief_w_on", true),
        )
        note("Delivered by the hourly background tick, so they arrive within the hour you choose.")
    }
}
