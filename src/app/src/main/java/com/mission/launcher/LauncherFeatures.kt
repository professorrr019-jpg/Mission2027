package com.mission.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object LauncherNavigation {
    enum class BackStep {
        CLOSE_PANEL,
        CLOSE_FOLDER_OR_DRAWER,
        CLOSE_EDITOR,
        PREVIOUS_PAGE,
        MOVE_TASK_BACK,
    }

    fun step(
        hasPanel: Boolean,
        hasDrawer: Boolean,
        drawerInFolder: Boolean,
        editing: Boolean,
        pageIndex: Int,
    ): BackStep =
        when {
            hasPanel -> BackStep.CLOSE_PANEL
            hasDrawer && drawerInFolder -> BackStep.CLOSE_FOLDER_OR_DRAWER
            hasDrawer -> BackStep.CLOSE_FOLDER_OR_DRAWER
            editing -> BackStep.CLOSE_EDITOR
            pageIndex > 0 -> BackStep.PREVIOUS_PAGE
            else -> BackStep.MOVE_TASK_BACK
        }

    fun folderStep(folderOpen: Boolean, drawerOpen: Boolean): BackStep =
        if (folderOpen) BackStep.CLOSE_FOLDER_OR_DRAWER else if (drawerOpen) BackStep.CLOSE_FOLDER_OR_DRAWER else BackStep.MOVE_TASK_BACK
}

object HomePresets {
    val names = listOf("Study", "Focus", "Minimal")

    fun apply(name: String): Boolean {
        val cards =
            when (name) {
                "Study" -> listOf(listOf("header", "countdown", "plan", "upcoming"), listOf("history", "heat", "mockstats"), listOf("timer", "focus", "revision", "syl3d"))
                "Focus" -> listOf(listOf("header", "focus", "timer", "plan", "water"), listOf("upcoming", "revision", "nexttopic"), listOf("history", "heat"))
                "Minimal" -> listOf(listOf("header", "countdown", "upcoming"), listOf("plan", "timer"), listOf("history"))
                else -> return false
            }

        val current = HomeLayout.load()
        val widgets = (0 until current.length()).flatMap { HomeLayout.cards(current.getJSONObject(it)).filter { id -> id.startsWith("w:") } }
        val pages =
            JSONArray().apply {
                cards.forEachIndexed { index, ids ->
                    val pageCards = ids.toMutableList()
                    if (index == cards.lastIndex) pageCards.addAll(widgets)
                    put(org.json.JSONObject().put("name", listOf("Today", "Progress", "Study").getOrElse(index) { "Page ${index + 1}" }).put("cards", JSONArray(pageCards)))
                }
            }
        HomeLayout.save(pages)
        Store.put("home_page", 0)
        Store.changed()
        return true
    }
    fun saveCustom(name: String) {
        Store.put("home_preset_custom_name", name.trim().ifBlank { "My home" })
        Store.put("home_preset_custom", HomeLayout.load().toString())
    }

    fun applyCustom(): Boolean {
        val saved = Store.s("home_preset_custom")
        if (saved.isBlank()) return false
        return try {
            val pages = org.json.JSONArray(saved)
            if (pages.length() == 0) return false
            val current = HomeLayout.load()
            val currentWidgets = (0 until current.length()).flatMap { HomeLayout.cards(current.getJSONObject(it)).filter { id -> id.startsWith("w:") } }
            val finalPage = pages.getJSONObject(pages.length() - 1)
            val savedCards = HomeLayout.cards(finalPage)
            currentWidgets.filterNot { it in savedCards }.forEach(savedCards::add)
            HomeLayout.setCards(finalPage, savedCards)
            HomeLayout.save(pages)
            Store.put("home_page", 0)
            Store.changed()
            true
        } catch (e: Exception) {
            Log.e("home-preset", e)
            false
        }
    }
}

object AppFolders {
    fun name(pkg: String): String = Store.s("app_folder_$pkg").trim()

    fun set(pkg: String, name: String) {
        val cleaned = name.trim().take(24)
        if (cleaned.isEmpty()) Store.put("app_folder_$pkg", null) else Store.put("app_folder_$pkg", cleaned)
        Store.changed()
    }

    fun names(): List<String> =
        Apps.list.mapNotNull { entry ->
            name(entry.pkg).takeIf { it.isNotBlank() }
        }.distinct().sortedBy { it.lowercase() }

    fun packages(folder: String): Set<String> =
        Apps.list.map { it.pkg }.filter { name(it).equals(folder, ignoreCase = true) }.toSet()
}

object LauncherRecents {
    private const val KEY = "launcher_recents"

    fun record(pkg: String) {
        val recents = Store.stringList(KEY).toMutableList()
        recents.remove(pkg)
        recents.add(0, pkg)
        Store.setStringList(KEY, recents.take(20))
        Store.changed()
    }

    fun packages(): List<String> = Store.stringList(KEY)
}

object SearchHistory {
    private const val KEY = "launcher_search_history"

    fun add(query: String) {
        val value = query.trim()
        if (value.length < 2) return
        val history = Store.stringList(KEY).toMutableList()
        history.removeAll { it.equals(value, ignoreCase = true) }
        history.add(0, value)
        Store.setStringList(KEY, history.take(12))
        Store.changed()
    }

    fun entries(): List<String> = Store.stringList(KEY)
}

object LauncherHaptics {
    fun tap(view: android.view.View) {
        if (!Store.b("launcher_haptics", true)) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun confirm(view: android.view.View) {
        if (!Store.b("launcher_haptics", true)) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    }
}

object IconPacks {
    data class Pack(val packageName: String, val label: String)

    private val cache = HashMap<String, Map<ComponentName, Int>>()

    fun available(c: Context): List<Pack> =
        Apps.list.mapNotNull { entry ->
            try {
                val resources = c.packageManager.getResourcesForApplication(entry.pkg)
                val id = resources.getIdentifier("appfilter", "xml", entry.pkg)
                if (id == 0 || mappings(c, entry.pkg).isEmpty()) null else Pack(entry.pkg, entry.label)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.distinctBy { it.packageName }

    fun icon(c: Context, component: ComponentName, fallback: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
        if (!FeatureRegistry.enabled("x41_icon_pack")) return fallback
        val pack = Store.s("icon_pack")
        if (pack.isBlank()) return fallback
        return try {
            val id = mappings(c, pack)[component] ?: return fallback
            c.packageManager.getResourcesForApplication(pack).getDrawable(id, c.theme) ?: fallback
        } catch (e: Exception) {
            synchronized(cache) { cache[pack] = emptyMap() }
            Log.e("icon-pack", e)
            fallback
        }
    }

    private fun mappings(c: Context, packageName: String): Map<ComponentName, Int> =
        synchronized(cache) {
            cache[packageName]?.let { return it }
            val out = LinkedHashMap<ComponentName, Int>()
            val resources = c.packageManager.getResourcesForApplication(packageName)
            val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
            if (xmlId == 0) {
                cache[packageName] = emptyMap()
                return emptyMap()
            }
            val parser = resources.getXml(xmlId)
            try {
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        val parsed =
                            component?.removePrefix("ComponentInfo{")?.removeSuffix("}")?.let { value ->
                                val slash = value.indexOf('/')
                                if (slash > 0) {
                                    val pkg = value.substring(0, slash)
                                    val cls = value.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
                                    ComponentName(pkg, cls)
                                } else {
                                    null
                                }
                            }
                        val drawableId = drawable?.let { resources.getIdentifier(it, "drawable", packageName) } ?: 0
                        if (parsed != null && drawableId != 0) out[parsed] = drawableId
                    }
                    event = parser.next()
                }
            } finally {
                parser.close()
            }
            out.also { cache[packageName] = it }
        }

    fun invalidate() = synchronized(cache) { cache.clear() }
}

class VoiceNoteActivity : Activity() {
    private lateinit var note: EditText
    private val requestVoice = 122
    private val source by lazy {
        intent.getStringExtra("source") ?: if (intent.getBooleanExtra("start_voice", false)) "voice" else "note"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Boot.ensure(this)
        systemBars(T.page)
        val root = vbox().apply { setBackgroundColor(T.page); padDp(16, 16, 16, 16) }
        root.addView(text(if (source == "stylus") "Stylus quick note" else "Quick note", 20f, 3))
        root.addView(
            text(
                if (source == "voice") "Speech is transcribed by the device speech service. Audio is not saved by Mission." else "Write with your stylus or type. Notes stay on this device.",
                12f,
                0,
                T.text3,
            ),
            lp(-1, -2, 6),
        )
        note =
            Dlg.field(this, "Speak or write a note", multi = true).apply {
                minLines = 5
                gravity = Gravity.TOP
                setText(intent.getStringExtra("initial_text").orEmpty())
                background = T.round(T.card, 18)
                padDp(14, 12, 14, 12)
            }
        val scroll = ScrollView(this).apply { addView(note) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(12) })
        val actions = hbox()
        actions.addView(pill("Speak", "mic", false) { recognize() }, lpm(-2, -2, 0, 0, 8, 0))
        actions.addView(pill("Save note", "check", true) { save() })
        root.addView(actions, lp(-1, -2, 12))
        setContentView(root)
        if (intent.getBooleanExtra("start_voice", false)) root.post { recognize() }
        else note.requestFocus()
    }

    private fun recognize() {
        try {
            startActivityForResult(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, intent.getStringExtra("language") ?: "hi-IN")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
                requestVoice,
            )
        } catch (e: Exception) {
            Log.e("voice-note", e)
            toast("Speech recognition is not available on this device")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVoice && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                val before = note.text.toString().trimEnd()
                note.setText(if (before.isEmpty()) spoken else "$before\n$spoken")
                note.setSelection(note.length())
            }
        }
    }

    private fun save() {
        val content = note.text.toString().trim()
        if (content.isEmpty()) {
            toast("Write or dictate a note first")
            return
        }
        Store.add("notes", obj("date" to Store.today(), "text" to content, "source" to source, "t" to System.currentTimeMillis()))
        toast(if (source == "voice") "Voice note saved" else "Note saved")
        finish()
    }
}

class NotificationDigestWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Boot.ensure(applicationContext)
        return try {
            Notifs.postDigest(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("notification-digest", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "mission_notification_digest"

        fun setEnabled(c: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(c)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<NotificationDigestWorker>(6, TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
