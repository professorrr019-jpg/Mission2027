package com.mission.launcher

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Home layout model: ordered pages, each an ordered list of card ids ("w:<id>" = system widget). */
object HomeLayout {
    private const val KEY = "home_layout"

    fun defaults(): JSONArray =
        JSONArray()
            .put(page("Today"))
            .put(page("Progress"))
            .put(page("Study"))

    private fun page(
        name: String,
        vararg ids: String,
    ) = JSONObject().put("name", name).put("cards", JSONArray(ids.toList()))

    fun load(): JSONArray =
        try {
            val saved = Store.s(KEY)
            if (saved.isBlank()) {
                defaults()
            } else {
                JSONArray(saved).also { pages ->
                    if (pages.length() == 0) return defaults()
                    for (i in 0 until pages.length()) {
                        val page = pages.optJSONObject(i) ?: continue
                        setCards(page, cards(page).filterNot { it == "mains" })
                    }
                }
            }
        } catch (e: Exception) {
            // Keep the unreadable layout so phone-widget placements are never silently lost.
            Store.s(KEY).takeIf { it.isNotBlank() }?.let { Store.put("${KEY}_corrupt_backup", it) }
            defaults()
        }

    fun save(a: JSONArray) {
        Store.put(KEY, a.toString())
    }

    fun reset() {
        val current = load()
        val widgetsByPage =
            (0 until current.length()).associateWith { index ->
                cards(current.getJSONObject(index)).filter { it.startsWith("w:") }
            }
        val reset = defaults()
        widgetsByPage.forEach { (index, widgets) ->
            if (widgets.isNotEmpty()) {
                val destination = minOf(index, reset.length() - 1)
                val page = reset.getJSONObject(destination)
                setCards(page, cards(page) + widgets)
            }
        }
        save(reset)
    }

    fun widgetIds(): List<Int> =
        load().let { pages ->
            (0 until pages.length()).flatMap { cards(pages.getJSONObject(it)) }
                .filter { it.startsWith("w:") }
                .mapNotNull { it.removePrefix("w:").toIntOrNull() }
        }

    fun removeWidget(id: Int): Boolean {
        val pages = load()
        var removed = false
        for (index in 0 until pages.length()) {
            val page = pages.getJSONObject(index)
            val cards = cards(page)
            if (cards.remove("w:$id")) {
                setCards(page, cards)
                removed = true
            }
        }
        if (removed) save(pages)
        return removed
    }

    fun cards(p: JSONObject): MutableList<String> {
        val a = p.optJSONArray("cards") ?: JSONArray()
        return (0 until a.length()).map { a.optString(it) }.toMutableList()
    }

    fun setCards(
        p: JSONObject,
        l: List<String>,
    ) {
        p.put("cards", JSONArray(l))
    }
}

class MainActivity : Activity(), Host {
    override val act: Activity get() = this
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var pager: Pager
    private lateinit var dots: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var editBar: LinearLayout
    private lateinit var content: FrameLayout
    private var drawer: DrawerView? = null
    private var gateView: View? = null
    private var editing = false
    private var edgeHandle: View? = null
    private var rightHandle: View? = null
    private var rightPanel: View? = null
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var bottomEdgeDown = false
    private val edgeHide =
        Runnable {
            edgeHandle?.let { h ->
                h.animate().cancel()
                h.animate().alpha(0f).setDuration(180).withEndAction {
                    if (edgeHandle === h) {
                        val lp = h.layoutParams as FrameLayout.LayoutParams
                        lp.width = T.dp(8)
                        h.layoutParams = lp
                        h.visibility = View.VISIBLE
                    }
                }.start()
            }
        }
    private var topInset = 0
    private var botInset = 0
    private val scrollY = HashMap<Int, Int>()
    private lateinit var widgetHost: AppWidgetHost
    private val REQ_PICK = 71
    private val REQ_BIND = 72
    private val REQ_CONF = 73
    private val REQ_NOTIF = 74
    private val STATE_PENDING_WIDGET = "pending_app_widget_id"
    private var pendingWidget = -1
    private var layout = JSONArray()
    private val listener: () -> Unit = {
        scheduleRefresh()
        updateEdgeHandle()
    }
    private val refreshRun =
        Runnable {
            if (pager.busy) {
                scheduleRefresh(600)
            } else if (currentFocus !is EditText) {
                rebuild()
            } else {
                scheduleRefresh(2500)
            }
        }

    private val pkgReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                i: Intent,
            ) {
                Apps.load(this@MainActivity) {
                    drawer?.refresh()
                    buildDock()
                }
            }
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val diagnostics = getSharedPreferences("mission_diagnostics", MODE_PRIVATE)
        if (diagnostics.getBoolean("safe_mode", false)) {
            Store.put("safe_mode", true)
            toast("Safe mode: repeated crashes detected. Diagnostics are available in Settings.")
        } else {
            Store.put("safe_mode", false)
        }
        pendingWidget = b?.getInt(STATE_PENDING_WIDGET, -1) ?: -1
        Boot.ensure(this)
        Migrate.run(this)
        systemBars()
        hideNav()
        widgetHost = AppWidgetHost(this, 1024)
        root = FrameLayout(this)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            val callback =
                android.window.OnBackInvokedCallback {
                    handleBack()
                }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        }
        root.setOnApplyWindowInsetsListener {
                _,
                ins,
            ->
            val s = ins.getInsets(WindowInsets.Type.systemBars())
            topInset = s.top
            botInset = s.bottom
            applyInsets()
            ins
        }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        pager = Pager(this)
        content.addView(pager, FrameLayout.LayoutParams(-1, -1))
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        content.addView(dots, FrameLayout.LayoutParams(-1, T.dp(18), Gravity.BOTTOM))
        dock =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = glassDock()
                padDp(8, 5, 8, 5)
            }
        content.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        updateEdgeHandle()
        editBar =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = T.round(T.cardSolid, 24)
                T.elevate(this, 6, 24)
                padDp(10, 8, 10, 8)
                visibility = View.GONE
            }
        content.addView(editBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        pager.onPos = { p ->
            drawDots(p)
            parallax(p)
        }
        pager.onSettled = { Store.put("home_page", it) }
        dots.setOnLongClickListener {
            toggleEdit()
            true
        }
        Store.listeners.add(listener)
        try {
            val f =
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    pkgReceiver,
                    f,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                registerReceiver(pkgReceiver, f)
            }
        } catch (
            e: Throwable,
        ) {
            Log.e("pkgrecv", e)
        }
        rebuild(Store.i("home_page", 0))
        Apps.load(this) {
            buildDock()
            drawer?.refresh()
        }
        Sched.hourly(this)
        Water.arm(this)
        Backdrop.ctx = applicationContext
        Targets.schedule(this)
        Auto.schedule(this)
        Cleaner.schedule(this)
        Plus.shortcuts(this)
        window.decorView.post { handleOpen(intent) }
        Wallpaper.scheduleNext(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIF,
            )
        }
        if (TimerState.active()) TimerService.start(this)
        if (!Store.b("wp_applied") && Wallpaper.enabled()) Thread { if (Wallpaper.apply(this, true)) Store.put("wp_applied", true) }.start()
        if (!Store.b("welcomed")) {
            Store.put("welcomed", true)
            ui.postDelayed({
                Dlg.info(
                    this,
                    "Welcome to Mission 2027",
                    "Swipe between Today, Progress and Study. Swipe up on the dock or tap the search icon for all apps. Tap the sliders icon to rearrange or add widgets.\n\nSet your reset password first. Then open Settings > Zite to configure live sync.",
                )
                openSettings("reset")
            }, 700)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backCallback = null
        }
        Store.listeners.remove(listener)
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Exception) {
        }
        ui.removeCallbacks(refreshRun)
        ui.removeCallbacks(edgeHide)
        rightPanel?.let(root::removeView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PENDING_WIDGET, pendingWidget)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            widgetHost.stopListening()
        } catch (e: Exception) {
        }
        Store.flushNow()
    }

    /** Hides the system navigation bar / gesture pill on home; a swipe from the bottom edge shows it briefly. */
    /** Translucent "glass" dock: see-through fill, hairline light border, no heavy shadow. */
    private fun glassDock() =
        T.round(
            (T.card and 0x00FFFFFF) or ((Store.i("dock_alpha", 45).coerceIn(10, 100) * 255 / 100) shl 24),
            20,
            1,
            if (T.dark) 0x33FFFFFF else 0x66FFFFFF,
        )

    private fun hideNav() {
        try {
            val ic = window.insetsController ?: return
            if (Store.b("hide_nav", true)) {
                ic.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ic.hide(WindowInsets.Type.navigationBars())
            } else {
                ic.show(WindowInsets.Type.navigationBars())
            }
        } catch (e: Throwable) {
            Log.e("hide-nav", e)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNav()
    }

    override fun onResume() {
        super.onResume()
        hideNav()
        if (::dock.isInitialized) dock.background = glassDock()
        val was = T.dark
        T.init(applicationContext)
        if (was != T.dark) {
            systemBars()
            rebuild()
        }
        updateEdgeHandle()
        if (Zite.hasKey() && Zite.stale() && this.online()) {
            Thread {
                Zite.sync(this)
                runOnUiThread { scheduleRefresh() }
            }.start()
        }
        if (Focus.active()) Focus.finishIfDue(this)
        if (online()) Thread { try { Updater.backgroundCheck(applicationContext, 10) } catch (e: Exception) { Log.e("update-resume", e) } }.start()
        if (Store.b("pin_home") && Pin.isSet() && !Pin.unlocked()) gate { }
        scheduleRefresh(150)
        try { if (!editing && gateView == null) Review.promptIfDue(this) } catch (e: Exception) { Log.e("review", e) }
    }

    /** Notifications and floating cards deep-link into the toolkit and automation screens. */
    private fun handleOpen(i: Intent?) {
        when (i?.getStringExtra("open")) {
            "toolkit" -> Toolkit.open(this)
            "auto" -> Auto.open(this)
            "auto_detail" -> Auto.detail(this, i.getStringExtra("rule"))
            "mocks" -> Toolkit.mockSchedule(this)
        }
        i?.removeExtra("open")
        i?.removeExtra("rule")
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        handleOpen(i)
        if (i.action == Intent.ACTION_MAIN && i.hasCategory(Intent.CATEGORY_HOME)) {
            closeDrawer()
            if (i.getBooleanExtra("edit", false)) {
                i.removeExtra("edit")
                if (!editing) toggleEdit()
            } else {
                if (editing) toggleEdit()
                pager.goTo(0)
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        val g = gateView as? PinPad
        if (g != null) {
            if (g.allowCancel) g.onCancel() // a mandatory home PIN simply swallows Back
            return
        }
        val step =
            LauncherNavigation.step(
                hasPanel = rightPanel != null,
                hasDrawer = drawer != null,
                drawerInFolder = drawer?.inFolder() == true,
                editing = editing,
                pageIndex = pager.index,
            )
        when (step) {
            LauncherNavigation.BackStep.CLOSE_PANEL -> closeRightPanel()
            LauncherNavigation.BackStep.CLOSE_FOLDER_OR_DRAWER -> drawer?.goBack()
            LauncherNavigation.BackStep.CLOSE_EDITOR -> toggleEdit()
            LauncherNavigation.BackStep.PREVIOUS_PAGE -> pager.goTo(pager.index - 1)
            LauncherNavigation.BackStep.MOVE_TASK_BACK -> moveTaskToBack(true)
        }
    }

    // ---------- layout ----------
    private fun applyInsets() {
        val dockH = if (dock.height > 0) dock.height else T.dp(Store.i("dock_icon", 15).coerceIn(10, 72) + 18)
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(8) + dockH + T.dp(8)
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(8)
        dock.post {
            val want = botInset + T.dp(8) + dock.height + T.dp(8)
            val lp = dots.layoutParams as FrameLayout.LayoutParams
            if (dock.height > 0 && lp.bottomMargin != want) {
                lp.bottomMargin = want
                dots.requestLayout()
            }
        }
        (editBar.layoutParams as FrameLayout.LayoutParams).bottomMargin = botInset + T.dp(8)
        for (i in 0 until pager.childCount) (pager.getChildAt(i) as? ScrollView)?.setPadding(0, topInset, 0, 0)
        dots.requestLayout()
        dock.requestLayout()
    }

    /** Split screen, floating window or rotation: the window size changed, so cards, dock and drawer are laid out again for the new width. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            val reopen = drawer != null
            if (reopen) closeDrawer()
            closeRightPanel()
            T.init(applicationContext)
            scheduleRefresh(250)
            updateEdgeHandle()
            if (reopen) ui.postDelayed({ if (drawer == null) openDrawer() }, 450)
        } catch (e: Exception) {
            Log.e("config-change", e)
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        scheduleRefresh(250)
    }

    private fun scheduleRefresh(delay: Long = 300) {
        ui.removeCallbacks(refreshRun)
        ui.postDelayed(refreshRun, delay)
    }

    override fun refresh() = scheduleRefresh(50)

    private fun rebuild(keepPage: Int = pager.index) {
        val safeMode = Store.b("safe_mode", false)
        for (i in 0 until pager.childCount) (pager.getChildAt(i) as? ScrollView)?.let { scrollY[i] = it.scrollY }
        layout = HomeLayout.load()
        val pages = (0 until layout.length()).map { buildPage(it) }
        pager.setPages(pages, keepPage)
        pages.forEachIndexed { i, v -> (v as ScrollView).post { v.scrollTo(0, scrollY[i] ?: 0) } }
        applyInsets()
        drawDots(pager.pos)
        buildDock()
        updateEditBar()
    }

    private fun buildPage(i: Int): View {
        val p = layout.getJSONObject(i)
        val sv =
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                setPadding(0, topInset, 0, 0)
            }
        attachGestures(sv)
        val col = vbox()
        val wide = resources.configuration.screenWidthDp > 700
        col.setPadding(T.dp(if (wide) 24 else 14), T.dp(8), T.dp(if (wide) 24 else 14), T.dp(maxOf(60, Store.i("dock_icon", 15).coerceIn(10, 72) + 18 + 14 + 24)) + botInset)
        val ids = if (Store.b("safe_mode", false)) emptyList() else HomeLayout.cards(p)
        if (editing) {
            col.addView(text(p.optString("name") + "  (page ${i + 1}/${layout.length()})", 13f, 3, T.text2).apply { padDp(6, 6, 6, 6) })
        } else if (i > 0 && ids.firstOrNull() != "header") {
            col.addView(
                text(p.optString("name"), 28f, 3).apply {
                    padDp(6, 14, 6, 4)
                    setShadowLayer(8f, 0f, 2f, T.withAlpha(T.page, 0.8f))
                },
            )
        }
        if (wide && !editing) {
            // two-column masonry on tablets (Xiaomi Pad): header/countdown full width, the rest alternate
            val full = ids.filter { Cards.catalog[it]?.wide == true || it.startsWith("w:") }
            val rest = ids.filter { it !in full }
            full.filter { it == "header" }.forEach { addCard(col, i, it, ids) }
            full.filter { it != "header" }.forEach { addCard(col, i, it, ids) }
            val widthDp = resources.configuration.screenWidthDp.toFloat()
            val n = if (widthDp > 1100 && Store.b("compact_cards", true)) 3 else 2
            val colsV = (0 until n).map { vbox() }
            rest.forEachIndexed { k, id -> addCard(colsV[k % n], i, id, ids) }
            val two = hbox(false)
            colsV.forEachIndexed { k, v ->
                if (k > 0) two.addView(hspace(12))
                two.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
            }
            two.gravity = Gravity.TOP
            col.addView(two)
        } else {
            ids.forEach { addCard(col, i, it, ids) }
        }
        if (ids.isEmpty()) {
            col.addView(
                emptyNote(
                    if (editing) "Empty page. Use Add widget below." else "No widgets added yet. Tap the sliders icon to choose your cards.",
                ),
            )
        }
        sv.addView(col, FrameLayout.LayoutParams(-1, -2))
        return sv
    }

    private fun addCard(
        col: LinearLayout,
        page: Int,
        id: String,
        ids: List<String>,
    ) {
        val small = Store.b("compact_cards", true)
        val v =
            try {
                val z = when (Store.s("csz_$id", "m")) { "s" -> 0.8f; "l" -> 1.3f; else -> 1f }
                T.ck = (if (small) 0.5f else 0.65f) * z
                T.ct = (if (small) 0.65f else 0.78f) * (if (z < 1f) 0.9f else if (z > 1f) 1.15f else 1f)
                cardView(id)
            } finally {
                T.ck = 1f
                T.ct = 1f
            } ?: return
        val wrap = vbox()
        if (editing) {
            val idx = ids.indexOf(id)
            val title = if (id.startsWith("w:")) "System widget" else Cards.catalog[Cards.canon(id)]?.title ?: id
            val bar =
                hbox().apply {
                    background = T.round(T.inset, 14)
                    padDp(12, 4, 4, 4)
                }
            bar.addView(text(title, 12.5f, 2, T.text2), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(iconBtn("up", T.text2, 34) { moveCard(page, idx, -1) })
            bar.addView(iconBtn("down", T.text2, 34) { moveCard(page, idx, 1) })
            bar.addView(iconBtn("chev", T.text2, 34) { moveToPage(page, idx) })
            if (id.startsWith("w:")) {
                val wid = id.substringAfter("w:").toIntOrNull()
                val info = wid?.let { AppWidgetManager.getInstance(this).getAppWidgetInfo(it) }
                if (wid != null && info != null && (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
                    bar.addView(iconBtn("pencil", T.text2, 34) { resizeWidget(wid) })
                }
            } else {
                val cur = Store.s("csz_$id", "m")
                bar.addView(
                    text(cur.uppercase(), 12.5f, 3, T.accent).apply {
                        padDp(10, 6, 10, 6)
                        setOnClickListener {
                            Store.put("csz_$id", when (cur) { "s" -> "m"; "m" -> "l"; else -> "s" })
                            rebuild()
                        }
                    },
                )
                bar.addView(iconBtn("close", T.danger, 34) { removeCard(page, idx) })
            }
            wrap.addView(bar, lpm(-1, -2, 0, 0, 0, 6))
            v.alpha = 0.96f
        }
        wrap.addView(v, LinearLayout.LayoutParams(-1, -2))
        col.addView(wrap, lpm(-1, -2, 0, 0, 0, 14))
    }

    private fun attachGestures(sv: ScrollView) {
        val gd =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!editing) {
                            val action = if (FeatureRegistry.enabled("x38_wake_actions")) Store.s("wake_action", Store.s("g_dtap", "lock")) else Store.s("g_dtap", "lock")
                            Gestures.run(this@MainActivity, action)
                        }
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!editing) {
                            LauncherHaptics.confirm(sv)
                            Gestures.run(this@MainActivity, Store.s("g_long", "edit"))
                        }
                    }
                },
            )
        sv.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            false
        }
    }

    private var dx0 = 0f
    private var dy0 = 0f
    private var topAtDown = false
    private var hotCornerDown = -1

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (FeatureRegistry.enabled("x34_keyboard_spotlight") && drawer == null && gateView == null && rightPanel == null && !editing && event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 && currentFocus !is EditText) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_SEARCH || event.keyCode == android.view.KeyEvent.KEYCODE_SLASH) {
                openSpotlight("", true)
                return true
            }
            val char = event.unicodeChar.toChar()
            if (!event.isAltPressed && !event.isCtrlPressed && char.isLetterOrDigit()) {
                openSpotlight(char.toString())
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (Store.b("two_finger_capture", true) && e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && e.pointerCount == 2 && drawer == null && gateView == null) {
            startActivity(Intent(this, CaptureActivity::class.java))
            LauncherHaptics.confirm(root)
            return true
        }
        if (FeatureRegistry.enabled("x35_stylus_note") && e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && e.pointerCount > 0 &&
            e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        ) {
            startActivity(Intent(this, VoiceNoteActivity::class.java).putExtra("source", "stylus"))
            return true
        }
        if (drawer == null && gateView == null && rightPanel == null && !editing) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = e.rawX
                    dy0 = e.rawY
                    topAtDown = (pager.getChildAt(pager.index) as? ScrollView)?.scrollY == 0
                    hotCornerDown = if (FeatureRegistry.enabled("x37_hot_corners")) hotCornerAt(e.rawX, e.rawY) else -1
                    bottomEdgeDown = e.rawY >= resources.displayMetrics.heightPixels - botInset - T.dp(Store.i("bottom_gesture_edge", 44))
                }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - dy0
                    val dx = e.rawX - dx0
                    if (bottomEdgeDown && dy < -T.dp(Store.i("gesture_sensitivity", 130).coerceIn(70, 220)) && Math.abs(dx) < Math.abs(dy) * 0.8f) {
                        bottomEdgeDown = false
                        when (Store.s("bottom_gesture", "drawer")) {
                            "right" -> openRightPanel()
                            "note" -> startActivity(Intent(this, VoiceNoteActivity::class.java))
                            "chat" -> openChat()
                            "recents" -> openRecentSwitcher()
                            else -> openDrawer()
                        }
                        LauncherHaptics.confirm(root)
                        return true
                    }
                    val hotCornerUp = if (FeatureRegistry.enabled("x37_hot_corners")) hotCornerAt(e.rawX, e.rawY) else -1
                    if (hotCornerDown >= 0 && hotCornerDown == hotCornerUp && kotlin.math.abs(dx) < T.dp(12) && kotlin.math.abs(dy) < T.dp(12)) {
                        val corner = hotCornerDown
                        hotCornerDown = -1
                        val action = Store.s("hot_corner_$corner", "none")
                        if (action != "none") {
                            Gestures.run(this, action)
                            return true
                        }
                    }
                    if (topAtDown && dy > T.dp(130) && dy > Math.abs(dx) * 1.8f && e.rawY - dy0 < resources.displayMetrics.heightPixels) {
                        topAtDown = false
                        Gestures.run(this, Store.s("g_down", "notif"))
                    }
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    private fun hotCornerAt(x: Float, y: Float): Int {
        val edge = T.dp(42)
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val left = x <= edge
        val right = x >= width - edge
        val top = y <= topInset + edge
        val bottom = y >= height - botInset - edge
        return when {
            left && top -> 0
            right && top -> 1
            left && bottom -> 2
            right && bottom -> 3
            else -> -1
        }
    }

    private fun openSpotlight(query: String, showKeyboard: Boolean = false) {
        openDrawer()
        drawer?.focusSearch(query, showKeyboard)
    }

    private fun cardView(id: String): View? {
        if (id.startsWith("w:")) {
            val wid = id.substring(2).toIntOrNull()
            return if (wid == null) widgetUnavailable("Invalid saved widget ID") else widgetView(wid)
        }
        return Cards.make(this, id)
    }

    private fun widgetView(wid: Int): View? =
        try {
            val info =
                AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)
                    ?: return widgetUnavailable("Its provider is unavailable. Reinstall or enable the widget app, or remove this placement in Settings.")
            val hv = widgetHost.createView(this, wid, info)
            hv.setAppWidget(wid, info)
            val defaultDp = maxOf(110, widgetPxToDp(info.minHeight)).coerceIn(100, 600)
            val heightDp =
                if ((info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
                    val bounds = widgetHeightBounds(info)
                    Store.i("widget_height_$wid", defaultDp).coerceIn(bounds.first, bounds.last)
                } else {
                    defaultDp
                }
            FrameLayout(this).apply {
                background = T.round(T.card, 24)
                T.elevate(this, 3, 24)
                clipToOutline = true
                addView(hv, FrameLayout.LayoutParams(-1, T.dpFixed(heightDp)))
            }
        } catch (e: Exception) {
            Log.e("widget", e)
            widgetUnavailable("Could not load this Android widget: ${e.message ?: e.javaClass.simpleName}")
        }

    private fun widgetUnavailable(message: String): View =
        Card(this, 22).apply {
            addView(this@MainActivity.cardHeader("grid", "Android widget unavailable", T.slate))
            addView(this@MainActivity.text(message, 12.5f, 0, T.text2), lp(-2, -2, 8))
            addView(
                this@MainActivity.text(
                    "Its saved home placement is kept. Remove it only from Settings > Reset and data > Remove Android widget.",
                    11.5f,
                    0,
                    T.text3,
                ),
                lp(-2, -2, 4),
            )
        }

    private fun resizeWidget(wid: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(wid)
        if (info == null) {
            toast("The widget provider is unavailable")
            return
        }
        if ((info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) == 0) {
            toast("This widget does not support vertical resizing")
            return
        }
        val bounds = widgetHeightBounds(info)
        val minDp = bounds.first
        val maxDp = bounds.last
        val initial = Store.i("widget_height_$wid", maxOf(minDp, widgetPxToDp(info.minHeight))).coerceIn(minDp, maxDp)
        Dlg.input(this, "Android widget height", "$minDp-$maxDp dp", initial.toString(), true) { value ->
            val height = value.toIntOrNull()
            if (height == null || height !in minDp..maxDp) {
                toast("Enter a height from $minDp to $maxDp dp")
                return@input
            }
            try {
                val options = manager.getAppWidgetOptions(wid)
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                manager.updateAppWidgetOptions(wid, options)
            } catch (e: Exception) {
                Log.e("widget-resize", e)
                toast("Could not resize this widget")
                return@input
            }
            Store.put("widget_height_$wid", height)
            rebuild()
        }
    }

    private fun widgetPxToDp(px: Int): Int = Math.round(px / resources.displayMetrics.density)

    private fun widgetHeightBounds(info: AppWidgetProviderInfo): IntRange {
        val minPx = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        val minDp = widgetPxToDp(minPx).coerceIn(100, 600)
        val maxDp =
            if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) {
                widgetPxToDp(info.maxResizeHeight).coerceIn(minDp, 600)
            } else {
                600
            }
        return minDp..maxDp
    }

    // ---------- editing ----------
    override fun toggleEdit() {
        if (!editing && Store.b("lock_home_layout", false)) {
            toast("Home layout is locked in Settings")
            return
        }
        editing = !editing
        dock.visibility = if (editing) View.GONE else View.VISIBLE
        editBar.visibility = if (editing) View.VISIBLE else View.GONE
        if (editing) {
            ui.removeCallbacks(edgeHide)
            edgeHandle?.let {
                it.animate().cancel()
                it.visibility = View.GONE
            }
        } else {
            showEdgeHandle()
        }
        rebuild()
    }

    private fun updateEditBar() {
        editBar.removeAllViews()
        if (!editing) return
        editBar.addView(pill("Add widget", "plus", true) { addWidgetDialog() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Page", "layers", false) { pageMenu() }, lpm(-2, -2, 0, 0, 6, 0))
        editBar.addView(pill("Done", "check", false, T.sage) { toggleEdit() })
    }

    private fun mutate(f: (JSONArray) -> Unit) {
        if (Store.b("lock_home_layout", false)) {
            toast("Home layout is locked")
            return
        }
        val a = HomeLayout.load()
        f(a)
        HomeLayout.save(a)
        rebuild()
    }

    private fun moveCard(
        page: Int,
        idx: Int,
        d: Int,
    ) = mutate { a ->
        val p = a.getJSONObject(page)
        val l = HomeLayout.cards(p)
        val j = idx + d
        if (j in l.indices) {
            val t = l[idx]
            l[idx] = l[j]
            l[j] = t
            HomeLayout.setCards(p, l)
        }
    }

    private fun removeCard(
        page: Int,
        idx: Int,
    ) {
        val pages = HomeLayout.load()
        val p = pages.optJSONObject(page) ?: return
        val id = HomeLayout.cards(p).getOrNull(idx) ?: return
        Dlg.confirm(
            this,
            "Remove ${Cards.catalog[Cards.canon(id)]?.title ?: id} from home?",
            "Only its home-screen placement is removed. Your saved study data stays intact.",
            "Remove",
        ) {
            mutate { current ->
                val target = current.optJSONObject(page) ?: return@mutate
                val ids = HomeLayout.cards(target)
                if (ids.getOrNull(idx) == id) {
                    ids.removeAt(idx)
                    HomeLayout.setCards(target, ids)
                }
            }
        }
    }

    private fun moveToPage(
        page: Int,
        idx: Int,
    ) {
        val a = HomeLayout.load()
        val names = (0 until a.length()).filter { it != page }.map { it to a.getJSONObject(it).optString("name") }
        Dlg.choose(this, "Move to page", names.map { it.second }) { k ->
            mutate {
                    arr ->
                val src = arr.getJSONObject(page)
                val l = HomeLayout.cards(src)
                val id = l.removeAt(idx)
                HomeLayout.setCards(src, l)
                val dst = arr.getJSONObject(names[k].first)
                val dl = HomeLayout.cards(dst)
                dl.add(id)
                HomeLayout.setCards(dst, dl)
            }
        }
    }

    private fun addWidgetDialog() {
        val pages = HomeLayout.load()
        val pageIndex = pager.index.coerceIn(0, pages.length() - 1)
        val placed = HomeLayout.cards(pages.getJSONObject(pageIndex)).toSet()
        val available = Cards.catalog.filter { it.key !in placed }
        val systemWidgetOption = "system:android-widget"
        val options =
            available.map { (id, meta) ->
                WidgetCatalogDialog.Option(
                    id,
                    meta.title,
                    if (meta.wide) "Mission widget · full width" else "Mission widget",
                )
            } +
                WidgetCatalogDialog.Option(
                    systemWidgetOption,
                    "Android app widgets",
                    "Widgets provided by apps installed on this device",
                )
        WidgetCatalogDialog.show(this, options) { option ->
            if (option.id == systemWidgetOption) {
                pickSystemWidget()
            } else {
                mutate { current ->
                    val page = current.optJSONObject(pageIndex) ?: return@mutate
                    val cards = HomeLayout.cards(page)
                    if (option.id in Cards.catalog && option.id !in cards) {
                        cards.add(option.id)
                        HomeLayout.setCards(page, cards)
                    }
                }
            }
        }
    }

    private fun pageMenu() {
        Dlg.choose(this, "Page", listOf("Add new page", "Rename this page", "Move page left", "Move page right")) { i ->
            val pi = pager.index
            when (i) {
                0 ->
                    Dlg.input(this, "New page name", "e.g. Revision", "", false, false, "Add") { n ->
                        mutate {
                                a ->
                            a.put(JSONObject().put("name", n.ifBlank { "Page ${a.length() + 1}" }).put("cards", JSONArray()))
                        }
                        pager.goTo(HomeLayout.load().length() - 1)
                    }
                1 ->
                    Dlg.input(
                        this,
                        "Rename page",
                        "",
                        HomeLayout.load().getJSONObject(pi).optString("name"),
                    ) { n ->
                        if (n.isNotBlank()) {
                            mutate {
                                it.getJSONObject(pi).put("name", n)
                            }
                        }
                    }
                2, 3 -> {
                    val d = if (i == 2) -1 else 1
                    val a = HomeLayout.load()
                    val j = pi + d
                    if (j in 0 until a.length()) {
                        val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
                        val t = l[pi]
                        l[pi] = l[j]
                        l[j] = t
                        HomeLayout.save(JSONArray(l))
                        rebuild(j)
                    }
                }
            }
        }
    }

    // ---------- system widgets ----------
    override fun pickSystemWidget() {
        var allocated = -1
        try {
            allocated = widgetHost.allocateAppWidgetId()
            pendingWidget = allocated
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget),
                REQ_PICK,
            )
        } catch (e: Exception) {
            Log.e("widget-pick", e)
            dropWidget(allocated)
            toast("Could not open the Android widget picker")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(
        req: Int,
        res: Int,
        d: Intent?,
    ) {
        super.onActivityResult(req, res, d)
        when (req) {
            REQ_PICK -> {
                val id = d?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget
                if (res != RESULT_OK) {
                    if (id >= 0) dropWidget(id)
                    if (pendingWidget >= 0 && pendingWidget != id) dropWidget(pendingWidget)
                    return
                }
                if (id < 0) {
                    if (pendingWidget >= 0) dropWidget(pendingWidget)
                    Log.e("widget-pick", IllegalStateException("Picker returned no widget ID"))
                    toast("The widget picker returned no widget ID")
                    return
                }
                if (pendingWidget >= 0 && id != pendingWidget) dropWidget(pendingWidget)
                bindWidget(id)
            }
            REQ_BIND -> if (res == RESULT_OK) configureWidget(pendingWidget) else dropWidget(pendingWidget)
            REQ_CONF -> if (res == RESULT_OK) placeWidget(pendingWidget) else dropWidget(pendingWidget)
        }
    }

    private fun dropWidget(id: Int) {
        if (id < 0) return
        try {
            widgetHost.deleteAppWidgetId(id)
        } catch (e: Exception) {
            Log.e("widget-id-release", e)
        }
        if (pendingWidget == id) pendingWidget = -1
    }

    private fun bindWidget(id: Int) {
        pendingWidget = id
        val awm = AppWidgetManager.getInstance(this)
        val info = awm.getAppWidgetInfo(id)
        if (info == null) {
            dropWidget(id)
            toast("The selected widget provider is no longer available")
            return
        }
        try {
            if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
                configureWidget(id)
            } else {
                startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                    REQ_BIND,
                )
            }
        } catch (e: Exception) {
            Log.e("widget-bind", e)
            dropWidget(id)
            toast("Could not bind the Android widget")
        }
    }

    private fun configureWidget(id: Int) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info == null) {
            dropWidget(id)
            toast("The selected widget provider is no longer available")
            return
        }
        if (info.configure == null) {
            placeWidget(id)
            return
        }
        try {
            startActivityForResult(
                Intent(
                    AppWidgetManager.ACTION_APPWIDGET_CONFIGURE,
                ).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                REQ_CONF,
            )
        } catch (e: Exception) {
            Log.e("widget-configure", e)
            toast("Widget setup could not open; adding its default layout")
            placeWidget(id)
        }
    }

    private fun placeWidget(id: Int) {
        if (id < 0) {
            toast("Cannot add a widget without a valid ID")
            return
        }
        mutate {
                a ->
            val p = a.getJSONObject(pager.index.coerceIn(0, a.length() - 1))
            val l = HomeLayout.cards(p)
            if ("w:$id" !in l) l.add("w:$id")
            HomeLayout.setCards(p, l)
        }
        if (pendingWidget == id) pendingWidget = -1
    }

    // ---------- dock / drawer ----------
    private fun buildDock() {
        dock.removeAllViews()
        val pins = Apps.pins().mapNotNull { Apps.byPkg(it) }.take(5)
        val pinSize = T.dp(Store.i("dock_icon", 15).coerceIn(10, 72))
        pins.forEach { e ->
            dock.addView(
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(e.icon.constantState?.newDrawable()?.mutate() ?: e.icon)
                    pressScale()
                    setOnClickListener { Apps.launch(this@MainActivity, e.pkg) }
                    setOnLongClickListener {
                        Dlg.choose(
                            this@MainActivity,
                            e.label,
                            listOf("Open in floating window", "Open in split / adjacent", "App info"),
                        ) { i ->
                            if (i == 2) {
                                Apps.info(this@MainActivity, e.pkg)
                            } else {
                                Apps.launch(this@MainActivity, e.pkg, if (i == 0) "float" else "split")
                            }
                        }
                        true
                    }
                },
                lpm(pinSize, pinSize, 4, 0, 4, 0),
            )
        }
        if (pins.isEmpty()) dock.addView(text("Pin apps in Settings > Apps", 12f, 1, T.text3).apply { padDp(10, 0, 10, 0) })
        dock.addView(
            FrameLayout(this).apply {
                background = T.round(T.inset, 16)
                addView(icon("grid", T.text, 14, 1.8f), FrameLayout.LayoutParams(T.dp(14), T.dp(14), Gravity.CENTER))
                tap { openDrawer() }
            },
            lpm(T.dp(28), T.dp(28), 4, 0, 4, 0),
        )
        var y0 = 0f
        dock.setOnTouchListener {
                _,
                e,
            ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> y0 = e.rawY
                MotionEvent.ACTION_UP -> if (y0 - e.rawY > T.dp(40)) Gestures.run(this, Store.s("g_up", "drawer"))
            }
            false
        }
    }

    private fun updateEdgeHandle() {
        edgeHandle?.let { root.removeView(it) }
        edgeHandle = null
        rightHandle?.let { root.removeView(it) }
        rightHandle = null
        ui.removeCallbacks(edgeHide)
        if (Store.b("edge_handle", false)) {
            val handle =
                FrameLayout(this).apply {
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Open app drawer"
                    val bar = View(this@MainActivity).apply { background = T.round(T.withAlpha(T.text2, 0.72f), 10) }
                    addView(bar, FrameLayout.LayoutParams(T.dp(8), T.dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == MotionEvent.ACTION_DOWN) showEdgeHandle()
                        false
                    }
                    setOnClickListener {
                        ui.removeCallbacks(edgeHide)
                        openDrawer()
                    }
                }
            edgeHandle = handle
            root.addView(handle, FrameLayout.LayoutParams(T.dp(24), T.dp(52), Gravity.START or Gravity.CENTER_VERTICAL))
            showEdgeHandle()
        }
        if (FeatureRegistry.enabled("x36_right_panel")) {
            val handle =
                FrameLayout(this).apply {
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Open launcher control panel"
                    val bar = View(this@MainActivity).apply { background = T.round(T.withAlpha(T.text2, 0.72f), 10) }
                    addView(bar, FrameLayout.LayoutParams(T.dp(8), T.dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
                    setOnClickListener { openRightPanel() }
                }
            rightHandle = handle
            root.addView(handle, FrameLayout.LayoutParams(T.dp(24), T.dp(56), Gravity.END or Gravity.CENTER_VERTICAL))
        }
    }

    private fun openRecentSwitcher() {
        openRightPanel()
    }

    private fun openRightPanel() {
        if (drawer != null || gateView != null || editing || rightPanel != null) return
        val overlay =
            FrameLayout(this).apply {
                setBackgroundColor(0x55000000)
                isClickable = true
                setOnClickListener { closeRightPanel() }
            }
        val panel =
            vbox().apply {
                background = T.round(T.cardSolid, 24)
                padDp(16, 16, 16, 16)
                isClickable = true
                elevation = T.dpf(12f)
                setOnClickListener { }
            }
        panel.addView(text("Control panel", 18f, 3), lp(-1, -2, 4))
        panel.addView(text("Swipe or tap outside to close", 11f, 0, T.text3), lp(-1, -2, 2))
        val recentRow = hbox()
        var recentCount = 0
        LauncherRecents.packages().mapNotNull(Apps::byPkg).take(5).forEach { entry ->
            recentCount++
            recentRow.addView(
                FrameLayout(this).apply {
                    background = T.round(T.inset, 18)
                    padDp(8, 8, 8, 8)
                    addView(
                        ImageView(this@MainActivity).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(entry.icon.constantState?.newDrawable()?.mutate() ?: entry.icon)
                        },
                        FrameLayout.LayoutParams(T.dp(34), T.dp(34), Gravity.CENTER),
                    )
                    contentDescription = entry.label
                    tap { closeRightPanel(); Apps.launch(this@MainActivity, entry.pkg) }
                },
                lpm(T.dp(50), T.dp(50), 4, 0, 4, 0),
            )
        }
        if (recentCount > 0) panel.addView(recentRow, lp(-1, -2, 10))
        fun tile(ic: String, label: String, tint: Int, f: () -> Unit): View =
            vbox().apply {
                gravity = Gravity.CENTER
                padDp(4, 12, 4, 10)
                background = T.round(T.withAlpha(tint, 0.16f), 18)
                addView(icon(ic, T.textAccent(tint), 24, 1.9f))
                addView(text(label, 11f, 2, T.text, 1).apply { gravity = Gravity.CENTER }, lp(-2, -2, 6))
                tap { f() }
            }
        val tiles =
            listOf(
                tile("chevl", "Back", T.sage) { closeRightPanel(); ui.postDelayed({ CircleService.global(this, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }, 200) },
                tile("home", "Home", T.slate) { closeRightPanel() },
                tile("layers", "Recents", T.plum) { closeRightPanel(); ui.postDelayed({ CircleService.global(this, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) }, 200) },
                tile("trash", "Clear cache", T.amber) {
                    closeRightPanel()
                    Thread {
                        val n = try { Cleaner.run(this, 0) } catch (e: Exception) { 0 }
                        var kb = 0L
                        try { cacheDir.listFiles()?.forEach { f -> kb += f.length(); f.deleteRecursively() } } catch (e: Exception) { }
                        try { Apps.iconCacheClear?.invoke(); Backdrop.clear() } catch (e: Exception) { }
                        runOnUiThread { toast("Cache ${kb / 1024} KB cleared, $n apps stopped") }
                    }.start()
                },
                tile("refresh", "Restart", T.danger) { closeRightPanel(); Restart.ask(this) },
                tile("timer", if (TimerState.active()) "Stop timer" else "Study timer", T.terra) {
                    if (TimerState.active()) {
                        TimerState.finish(true)
                        TimerService.stop(this)
                    } else {
                        TimerState.start()
                        TimerService.start(this)
                    }
                    closeRightPanel()
                },
                tile("split", "Split view", T.plum) { closeRightPanel(); Split.pick(this) },
                tile("layers", "Last split", T.sage) { closeRightPanel(); Split.last(this) },
                tile("split", "Split toggle", T.slate) { closeRightPanel(); ui.postDelayed({ CircleService.global(this, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) }, 250) },
                tile("eye", "Show timer", T.teal) { closeRightPanel(); TimerState.bringBack(this) },
                tile("search", "Circle search", T.plum) { closeRightPanel(); ui.postDelayed({ CircleService.capture(this) }, 250) },
                tile("note", "Quick note", T.clay) { closeRightPanel(); startActivity(Intent(this, VoiceNoteActivity::class.java)) },
                tile("grid", "PYQ explorer", T.terra) { closeRightPanel(); PyqUi.explorer(this) },
                tile("bell", "Notifications", T.amber) { closeRightPanel(); CircleService.shade(this) },
                tile("lock", "Lock", T.slate) { closeRightPanel(); CircleService.lock(this) },
                tile("apps", "Apps", T.sage) { closeRightPanel(); openDrawer() },
                tile("sparkle", "Toolkit", T.sage) { closeRightPanel(); Toolkit.open(this) },
                tile("gear", "Settings", T.text2) { closeRightPanel(); openSettings() },
            )
        tiles.chunked(3).forEach { rowTiles ->
            val r = hbox(false)
            rowTiles.forEach { r.addView(it, lpm(0, -2, 3, 0, 3, 0, 1f)) }
            repeat(3 - rowTiles.size) { r.addView(View(this), lpm(0, 1, 3, 0, 3, 0, 1f)) }
            panel.addView(r, lp(-1, -2, 8))
        }
        val width = minOf(T.dp(380), ((if (root.width > 0) root.width else resources.displayMetrics.widthPixels) * 0.9f).toInt())
        overlay.addView(
            panel,
            FrameLayout.LayoutParams(width, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = T.dp(12)
            },
        )
        rightPanel = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeRightPanel() {
        rightPanel?.let { root.removeView(it) }
        rightPanel = null
    }

    private fun showEdgeHandle() {
        val h = edgeHandle ?: return
        if (drawer != null || gateView != null || editing) {
            ui.removeCallbacks(edgeHide)
            h.animate().cancel()
            h.alpha = 0f
            h.visibility = View.GONE
            return
        }
        h.animate().cancel()
        val lp = h.layoutParams as FrameLayout.LayoutParams
        lp.width = T.dp(24)
        h.layoutParams = lp
        h.alpha = 1f
        h.visibility = View.VISIBLE
        ui.removeCallbacks(edgeHide)
        ui.postDelayed(edgeHide, 3500)
    }

    private fun hideEdgeStrip(h: View) {
        h.animate().cancel()
        h.alpha = 0f
        val lp = h.layoutParams as FrameLayout.LayoutParams
        lp.width = T.dp(8)
        h.layoutParams = lp
        h.visibility = View.VISIBLE
    }

    override fun openDrawer() {
        if (drawer != null || gateView != null || editing) return
        ui.removeCallbacks(edgeHide)
        edgeHandle?.let { hideEdgeStrip(it) }
        val d = DrawerView(this) { closeDrawer() }
        d.setPadding(0, topInset, 0, botInset)
        drawer = d
        root.addView(d, FrameLayout.LayoutParams(-1, -1))
        d.translationY = (if (root.height > 0) root.height else resources.displayMetrics.heightPixels).toFloat()
        d.animate().translationY(0f).setDuration(240).start()
        var y0 = 0f
    }

    private fun closeDrawer() {
        val d = drawer ?: return
        drawer = null
        d.animate().translationY(
            resources.displayMetrics.heightPixels.toFloat(),
        ).setDuration(200).withEndAction { root.removeView(d) }.start()
        ui.postDelayed({ showEdgeHandle() }, 220)
    }

    // ---------- PIN gate ----------
    fun gate(then: () -> Unit) {
        if (!Pin.isSet() || Pin.unlocked()) {
            then()
            return
        }
        if (gateView != null) return
        ui.removeCallbacks(edgeHide)
        edgeHandle?.let {
            it.animate().cancel()
            it.visibility = View.GONE
        }
        val pad =
            PinPad(
                this,
                "Enter PIN",
                Pin.len(),
                !Store.b("pin_home"),
                { code, p ->
                    if (Pin.lockLeft() > 0) {
                        p.error("Locked: wait ${Pin.lockLeft()}s")
                    } else if (Pin.check(code)) {
                        root.removeView(p)
                        gateView = null
                        showEdgeHandle()
                        then()
                    } else {
                        p.error(if (Pin.lockLeft() > 0) "Too many attempts: ${Pin.lockLeft()}s" else "Wrong PIN")
                    }
                },
                {
                    root.removeView(gateView)
                    gateView = null
                    showEdgeHandle()
                },
                if (Bio.available(this) && Store.b("pin_bio", true)) {
                    (
                        {
                            Bio.prompt(this) {
                                runOnUiThread {
                                    gateView?.let { root.removeView(it) }
                                    gateView = null
                                    showEdgeHandle()
                                    then()
                                }
                            }
                        }
                    )
                } else {
                    null
                },
            )
        gateView = pad
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- Host ----------
    override fun openChat(prefill: String?) {
        startActivity(Intent(this, ChatActivity::class.java).apply { if (prefill != null) putExtra("q", prefill) })
    }

    override fun openSettings(path: String?) {
        gate { startActivity(Intent(this, SettingsActivity::class.java).apply { if (path != null) putExtra("path", path) }) }
    }

    private fun drawDots(p: Float) {
        val n = pager.count
        if (dots.childCount != n) {
            dots.removeAllViews()
            for (i in 0 until n) dots.addView(View(this), lpm(T.dp(8), T.dp(8), 4, 0, 4, 0))
        }
        for (i in 0 until n) {
            val v = dots.getChildAt(i)
            val a = (1f - Math.abs(i - p)).coerceIn(0f, 1f)
            v.background = T.round(T.withAlpha(T.text, 0.25f + 0.65f * a), 4)
            val lp = v.layoutParams
            lp.width = T.dp(8) + (T.dp(10) * a).toInt()
            v.layoutParams = lp
        }
        dots.visibility = if (n > 1) View.VISIBLE else View.GONE
    }

    private fun parallax(p: Float) {
        if (!Store.b("wp_parallax", true) || pager.count < 2) return
        try {
            WallpaperManager.getInstance(this).setWallpaperOffsets(root.windowToken, p / (pager.count - 1), 0.5f)
        } catch (e: Exception) {
        }
    }
}
