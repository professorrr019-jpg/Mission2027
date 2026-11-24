package com.mission.launcher

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.view.accessibility.AccessibilityNodeInfo
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Accessibility service used for: Circle to Search (screenshot + lasso), lock screen, notification shade.
 * It does not read screen content (canRetrieveWindowContent is off); it only takes a screenshot when you ask.
 */
class CircleService : AccessibilityService() {
    companion object {
        @Volatile var inst: CircleService? = null

        fun running() = inst != null

        fun enabled(c: Context): Boolean =
            try {
                (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(
                    c.packageName + "/",
                )
            } catch (e: Exception) {
                false
            }

        fun openSettings(c: Context) {
            try {
                c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }

        const val TAG = "UPSC Prelims 2027"
        const val GEMINI = "com.google.android.apps.bard"
        val GEMINI_PACKAGES =
            listOf(
                GEMINI,
                "com.google.android.apps.googleassistant",
            )

        /** Prompt that travels with every circled image (editable in Settings > Circle to Search). */
        fun geminiPrompt(): String {
            val custom = Store.s("circle_prompt")
            val base =
                if (custom.isNotBlank()) {
                    custom
                } else {
                    "I am a UPSC CSE aspirant (Hindi medium, Philosophy optional) preparing for $TAG. Explain what is circled in this image for $TAG and Mains: " +
                        "key facts, static + current-affairs linkage, likely MCQ angles and one mnemonic. Answer in simple Hindi + English."
                }
            return if (Store.b("circle_tag", true) && !base.contains(TAG)) "[$TAG] $base" else base
        }

        /** HyperOS blocks accessibility for sideloaded apps until "Allow restricted settings" is chosen in App info. */
        fun openAppInfo(c: Context) {
            try {
                c.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
            }
        }

        fun need(c: Context) {
            Toast.makeText(c, "Turn on Mission accessibility service first", Toast.LENGTH_LONG).show()
            openSettings(c)
        }

        fun capture(c: Context) {
            val s = inst
            if (s == null) need(c) else s.snap()
        }

        fun lock(c: Context): Boolean {
            val s =
                inst ?: run {
                    need(c)
                    return false
                }
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun shade(c: Context): Boolean {
            try {
                val sb = c.getSystemService("statusbar")
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
                return true
            } catch (
                e: Throwable,
            ) {
            }
            val s =
                inst ?: run {
                    need(c)
                    return false
                }
            return s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    private var overlay: View? = null
    private val ui = Handler(Looper.getMainLooper())
    private val btn =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(c: AccessibilityButtonController) {
                snap()
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        Boot.ensure(this)
        try {
            accessibilityButtonController.registerAccessibilityButtonCallback(btn)
        } catch (e: Throwable) {
        }
        updateEdges()
    }

    // ---------------- edge swipe gestures (back from the left or right edge, optional bottom swipe) ----------------
    private val edgeViews = ArrayList<View>()

    fun removeEdges() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        edgeViews.forEach {
            try {
                wm.removeViewImmediate(it)
            } catch (e: Exception) {
            }
        }
        edgeViews.clear()
    }

    fun updateEdges() {
        removeEdges()
        if (!Store.b("edge_nav", true)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = resources.displayMetrics
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            for (left in listOf(true, false)) {
                val v = View(this)
                v.setOnTouchListener(sideSwipe(left))
                val lp = WindowManager.LayoutParams(T.dp(18), (dm.heightPixels * 0.62f).toInt(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
                lp.gravity = (if (left) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
                wm.addView(v, lp)
                edgeViews.add(v)
            }
            if (Store.b("edge_bottom", false)) {
                val v = View(this)
                v.setOnTouchListener(bottomSwipe())
                val lp = WindowManager.LayoutParams((dm.widthPixels * 0.4f).toInt(), T.dp(14), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wm.addView(v, lp)
                edgeViews.add(v)
            }
        } catch (e: Exception) {
            Log.e("edges", e)
        }
    }

    private fun sideSwipe(left: Boolean): View.OnTouchListener {
        var x0 = 0f
        var y0 = 0f
        var fired = false
        return View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX
                    y0 = e.rawY
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (if (left) e.rawX - x0 else x0 - e.rawX)
                    if (!fired && dx > T.dp(56) && dx > Math.abs(e.rawY - y0) * 1.2f) {
                        fired = true
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
            true
        }
    }

    private fun bottomSwipe(): View.OnTouchListener {
        var y0 = 0f
        var t0 = 0L
        var fired = false
        return View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    y0 = e.rawY
                    t0 = System.currentTimeMillis()
                    fired = false
                }
                MotionEvent.ACTION_UP -> {
                    val up = y0 - e.rawY
                    if (!fired && up > T.dp(60)) {
                        fired = true
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        performGlobalAction(if (System.currentTimeMillis() - t0 > 450) GLOBAL_ACTION_RECENTS else GLOBAL_ACTION_HOME)
                    }
                }
            }
            true
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = e.packageName?.toString() ?: return
            Slot.guard(this, pkg)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(i: Intent?): Boolean {
        inst = null
        removeEdges()
        closeOverlay()
        return super.onUnbind(i)
    }

    fun snap() {
        if (overlay != null) return
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(r: ScreenshotResult) {
                        val hb = r.hardwareBuffer
                        val bm =
                            try {
                                Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                            } finally {
                                hb.close()
                            }
                        if (bm == null) toast("Screenshot failed") else showLasso(bm)
                    }

                    override fun onFailure(code: Int) {
                        toast(
                            if (code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) "Wait a second and try again" else "Screenshot blocked on this screen",
                        )
                    }
                },
            )
        } catch (e: Throwable) {
            Log.e("circle", e)
            toast("Circle to Search is not available")
        }
    }

    private fun toast(s: String) {
        ui.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    }

    private fun add(v: View) {
        val lp =
            WindowManager.LayoutParams(
                -1,
                -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(v, lp)
        overlay = v
    }

    private fun closeOverlay() {
        overlay?.let {
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (e: Exception) {
            }
        }
        overlay = null
    }

    private fun showLasso(bm: Bitmap) {
        ui.post {
            add(
                Lasso(this, bm, { crop ->
                    if (crop == null) {
                        closeOverlay()
                    } else if (Store.s("circle_target", "gemini") == "gemini" && sendToGemini(crop)) {
                        closeOverlay()
                    } else {
                        closeOverlay()
                        showResult(crop)
                    }
                }, { closeOverlay() }),
            )
        }
    }

    private fun showResult(crop: Bitmap) {
        val root = FrameLayout(this)
        root.setBackgroundColor(0xCC000000.toInt())
        root.isClickable = true
        val col =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                padDp(20, 20, 20, 20)
                background = T.round(T.cardSolid, 28)
            }
        col.addView(
            ImageView(this).apply {
                setImageBitmap(crop)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = T.round(T.inset, 14)
                padDp(6, 6, 6, 6)
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        val row = hbox().apply { gravity = Gravity.CENTER }
        var uri: Uri? = null

        fun saved(): Uri? {
            if (uri == null) uri = saveImage(crop)
            return uri
        }
        row.addView(
            pill("Google Lens", "search", true, T.slate) {
                val u = saved()
                if (u != null) {
                    closeOverlay()
                    sendToLens(u)
                } else {
                    toast("Could not save image")
                }
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        row.addView(
            pill("Gemini", "sparkle", false, T.plum) {
                closeOverlay()
                if (!sendToGemini(crop)) toast("Gemini app not found")
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        row.addView(
            pill("Share", "up", false) {
                val u = saved()
                if (u != null) {
                    closeOverlay()
                    share(u, null)
                }
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        row.addView(
            pill("Save", "down", false) {
                toast(if (saved() != null) "Saved in Pictures/Mission" else "Could not save")
            },
            lpm(-2, -2, 0, 0, 8, 0),
        )
        row.addView(pill("Close", "close", false) { closeOverlay() })
        col.addView(chipRow(row), lp(-1, -2, 14))
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        root.addView(col, FrameLayout.LayoutParams(minOf(w - T.dp(32), T.dp(520)), (h * 0.7f).toInt(), Gravity.CENTER))
        ui.post { add(root) }
    }

    private fun saveImage(bm: Bitmap): Uri? =
        try {
            val cv =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "mission_circle_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mission")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
            contentResolver.openOutputStream(u)!!.use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cv.clear()
            cv.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(u, cv, null, null)
            u
        } catch (e: Throwable) {
            Log.e("circle-save", e)
            null
        }

    private fun intentFor(u: Uri) =
        Intent(
            Intent.ACTION_SEND,
        ).setType(
            "image/png",
        ).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Circled image + the UPSC tag prompt straight into the Gemini app, in a small floating window when the system allows it. */
    private fun sendToGemini(crop: Bitmap): Boolean {
        val u = saveImage(crop) ?: return false
        val prompt = geminiPrompt()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("UPSC prompt", prompt))
        } catch (e: Exception) {
        }
        val i =
            GEMINI_PACKAGES.asSequence().map { pkg ->
                intentFor(u).setPackage(pkg).putExtra(Intent.EXTRA_TEXT, prompt).putExtra(Intent.EXTRA_SUBJECT, TAG)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.firstOrNull { packageManager.queryIntentActivities(it, 0).isNotEmpty() } ?: return false
        return try {
            val dm = resources.displayMetrics
            val wf = Store.i("circle_w", 42).coerceIn(30, 70) / 100f
            val hf = Store.i("circle_h", 62).coerceIn(40, 90) / 100f
            val w = (dm.widthPixels * wf).toInt()
            val h = (dm.heightPixels * hf).toInt()
            val l = dm.widthPixels - w - T.dp(12)
            val t = (dm.heightPixels - h) / 2
            val bounds = Rect(l, t, l + w, t + h)
            val o = ActivityOptions.makeBasic()
            if (Store.b("circle_float", true)) {
                o.setLaunchBounds(bounds)
                try {
                    // 5 = freeform windowing mode; forces a floating window where the system allows it.
                    ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5)
                } catch (e: Throwable) {
                }
            }
            val ob = o.toBundle() ?: Bundle()
            if (Store.b("circle_float", true)) ob.putInt("android.activity.windowingMode", 5)
            if (Store.b("circle_float", true)) {
                // Step 1: open Gemini itself in the small floating window. Step 2: send the image into that same task,
                // so Gemini cannot bounce it into a second full-screen task (which made the floating window vanish).
                val pkg = i.`package` ?: GEMINI_PACKAGES.first()
                val main = packageManager.getLaunchIntentForPackage(pkg)
                if (main != null) {
                    startActivity(main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ob)
                    ui.postDelayed({
                        try {
                            startActivity(i)
                        } catch (e: Exception) {
                            Log.e("circle-gemini2", e)
                        }
                    }, 1100)
                } else {
                    startActivity(i, ob)
                }
            } else {
                startActivity(i)
            }
            checkFloat()
            toast("Sent to Gemini  -  prompt copied if it needs pasting")
            autoSend(0)
            true
        } catch (e: Exception) {
            Log.e("circle-gemini", e)
            false
        }
    }

    /** Best effort: once Gemini is in front, press its send button so the search starts without another tap. */
    /** If Gemini still opened (almost) full screen, tell the user once how to allow freeform windows. */
    private fun checkFloat() {
        if (!Store.b("circle_float", true) || Store.b("circle_float_hint", false)) return
        ui.postDelayed({
            try {
                val dm = resources.displayMetrics
                for (w in windows) {
                    val packageName = w.root?.packageName ?: continue
                    if (packageName !in GEMINI_PACKAGES) continue
                    val r = Rect()
                    w.getBoundsInScreen(r)
                    if (r.width().toLong() * r.height() > dm.widthPixels.toLong() * dm.heightPixels * 0.8) {
                        Store.put("circle_float_hint", true)
                        toast("Gemini opened full screen. On this device, enable freeform windows in Developer options for a floating result.")
                    }
                }
            } catch (e: Exception) {
            }
        }, 2200)
    }

    private fun geminiRoot(): AccessibilityNodeInfo? {
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName in GEMINI_PACKAGES) return r
            }
        } catch (e: Exception) {
        }
        return try {
            rootInActiveWindow?.takeIf { it.packageName in GEMINI_PACKAGES }
        } catch (e: Exception) {
            null
        }
    }

    private var typedAt = 0

    private fun hasImage(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 16) return false
        val d = (node.contentDescription?.toString() ?: "").lowercase()
        if (d.contains("image") || d.contains("photo") || d.contains("attachment") || d.contains("तस्वीर")) return true
        if ((node.className?.toString() ?: "").contains("ImageView") && node.isVisibleToUser && node.parent?.className?.toString()?.contains("EditText") != true && depth > 3) return true
        for (k in 0 until node.childCount) {
            val ch = node.getChild(k) ?: continue
            if (hasImage(ch, depth + 1)) return true
        }
        return false
    }

    private fun autoSend(n: Int) {
        if (n == 0) typedAt = -1
        if (n > 60 || !Store.b("circle_autosend", true)) return
        ui.postDelayed({
            var done = false
            try {
                val root = geminiRoot()
                if (root != null) {
                    // Gemini may ask Continue / Start / Got it first: press those by itself
                    if (pressPrompt(root, 0)) {
                        autoSend(n + 1)
                        return@postDelayed
                    }
                    val ed = findEditor(root, 0)
                    val cur = ed?.text?.toString() ?: ""
                    if (ed != null && !cur.contains(TAG)) {
                        // Gemini drops shared text when an image comes with it, so the tag + prompt is typed into its box here.
                        ed.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ed.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val args = Bundle().putCharSequenceCompat(geminiPrompt())
                        if (!ed.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) ed.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        typedAt = n
                    } else if (ed != null && cur.contains(TAG)) {
                        if (typedAt < 0) typedAt = n
                        // send once the image preview is there, or after a short wait
                        if (hasImage(root, 0) || n - typedAt >= 5) done = clickSend(root, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("circle-auto", e)
            }
            if (!done) autoSend(n + 1)
        }, 600)
    }

    private val PROMPT_BUTTONS =
        setOf("continue", "start", "get started", "got it", "i agree", "agree", "accept", "next", "ok", "okay", "allow", "skip", "no thanks", "not now", "जारी रखें", "शुरू करें", "ठीक है", "स्वीकार करें", "अभी नहीं")

    private fun pressPrompt(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 14) return false
        val t = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
        if (t.isNotEmpty() && t in PROMPT_BUTTONS && node.isVisibleToUser && (node.className?.toString() ?: "").let { it.contains("Button") || node.isClickable }) {
            var c: AccessibilityNodeInfo? = node
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        for (k in 0 until node.childCount) {
            val ch = node.getChild(k) ?: continue
            if (pressPrompt(ch, depth + 1)) return true
        }
        return false
    }

    private fun Bundle.putCharSequenceCompat(t: CharSequence): Bundle {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
        return this
    }

    private fun findEditor(
        node: AccessibilityNodeInfo,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (depth > 16) return null
        if (node.isEditable || (node.className?.toString() ?: "").contains("EditText")) return node
        for (k in 0 until node.childCount) {
            val ch = node.getChild(k) ?: continue
            findEditor(ch, depth + 1)?.let { return it }
        }
        return null
    }

    private fun clickSend(
        node: AccessibilityNodeInfo,
        depth: Int,
    ): Boolean {
        if (depth > 14) return false
        val d = (node.contentDescription?.toString() ?: "").lowercase()
        val id = (node.viewIdResourceName ?: "").lowercase()
        if (node.isEnabled && (d == "send" || d.startsWith("send ") || d.contains("submit") || d.contains("भेजें") || id.contains("send"))) {
            var t: AccessibilityNodeInfo? = node
            while (t != null && !t.isClickable) t = t.parent
            if (t != null && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        for (k in 0 until node.childCount) {
            val ch = node.getChild(k) ?: continue
            if (clickSend(ch, depth + 1)) return true
        }
        return false
    }

    private fun sendToLens(u: Uri) {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("UPSC prompt", geminiPrompt()))
        } catch (e: Exception) {
        }
        for (pkg in listOf("com.google.android.googlequicksearchbox", "com.google.ar.lens")) {
            val i = intentFor(u).setPackage(pkg)
            if (packageManager.queryIntentActivities(i, 0).isNotEmpty()) {
                try {
                    startActivity(i)
                    return
                } catch (e: Exception) {
                }
            }
        }
        share(u, "Search with")
    }

    private fun share(
        u: Uri,
        t: String?,
    ) {
        try {
            startActivity(Intent.createChooser(intentFor(u), t ?: "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("No app to handle the image")
        }
    }
}

/** Full-screen frozen screenshot where the user draws a loop around what to search. */
private class Lasso(c: Context, val bm: Bitmap, val done: (Bitmap?) -> Unit, val cancel: () -> Unit) : View(c) {
    private val path = Path()
    private val glow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = T.dpf(5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFFFFFFFF.toInt()
        }
    private val halo =
        Paint(glow).apply {
            strokeWidth = T.dpf(12f)
            color = 0x55FFFFFF
        }
    private val dim = Paint().apply { color = 0x66000000 }
    private val hint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = T.dpf(15f)
            textAlign = Paint.Align.CENTER
            typeface = T.font(2)
        }
    private var l = 1e9f
    private var t = 1e9f
    private var r = 0f
    private var b = 0f
    private var drawing = false
    private var any = false
    private val src = Rect(0, 0, bm.width, bm.height)

    override fun onDraw(cv: Canvas) {
        cv.drawBitmap(bm, src, Rect(0, 0, width, height), null)
        cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        if (!any) {
            cv.drawText("Draw a circle around anything to search", width / 2f, T.dpf(90f), hint)
            cv.drawText("Tap outside to cancel", width / 2f, T.dpf(114f), hint.apply { alpha = 170 })
            hint.alpha = 255
        } else {
            cv.save()
            cv.clipRect(l, t, r, b)
            cv.drawBitmap(bm, src, Rect(0, 0, width, height), null)
            cv.restore()
            cv.drawPath(path, halo)
            cv.drawPath(path, glow)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path.reset()
                path.moveTo(e.x, e.y)
                l = e.x
                r = e.x
                t = e.y
                b = e.y
                drawing = true
                any = false
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(e.x, e.y)
                l = minOf(l, e.x)
                r = maxOf(r, e.x)
                t = minOf(t, e.y)
                b = maxOf(b, e.y)
                any = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!any || (r - l < T.dp(36) && b - t < T.dp(36))) {
                    cancel()
                    return true
                }
                val pad = T.dp(18)
                val sx = bm.width / width.toFloat()
                val sy = bm.height / height.toFloat()
                val x0 = ((l - pad) * sx).toInt().coerceIn(0, bm.width - 2)
                val y0 = ((t - pad) * sy).toInt().coerceIn(0, bm.height - 2)
                val x1 = ((r + pad) * sx).toInt().coerceIn(x0 + 1, bm.width)
                val y1 = ((b + pad) * sy).toInt().coerceIn(y0 + 1, bm.height)
                done(
                    try {
                        Bitmap.createBitmap(bm, x0, y0, x1 - x0, y1 - y0)
                    } catch (ex: Exception) {
                        null
                    },
                )
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }
}

/** Quick Settings tile: collapse the shade, then capture. */
class CircleTile : TileService() {
    override fun onStartListening() {
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.label = "Circle to Search"
        qsTile?.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, CircleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

class CircleActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        Handler(Looper.getMainLooper()).postDelayed({
            CircleService.capture(applicationContext)
            finish()
        }, 500)
    }
}
