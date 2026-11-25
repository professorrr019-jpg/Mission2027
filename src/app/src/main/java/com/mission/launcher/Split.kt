package com.mission.launcher

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper

/** Two apps side by side. "split" = real split screen (needs accessibility service), "float" = two small freeform windows. */
object Split {
    private val ui = Handler(Looper.getMainLooper())

    private fun intentFor(c: Context, pkg: String): Intent? =
        c.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    fun pair(a: Activity, p1: String, p2: String) {
        Store.put("split_last", "$p1,$p2")
        val i1 = intentFor(a, p1)
        val i2 = intentFor(a, p2)
        if (i1 == null || i2 == null) {
            a.toast("App not found")
            return
        }
        try {
            if (Store.s("split_mode", "split") == "float") {
                val dm = a.resources.displayMetrics
                fun opts(r: Rect): android.os.Bundle {
                    val o = ActivityOptions.makeBasic().setLaunchBounds(r)
                    try {
                        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5)
                    } catch (e: Throwable) { Log.e("split", e) }
                    return o.toBundle() ?: android.os.Bundle()
                }
                val w = dm.widthPixels / 2
                a.startActivity(i1, opts(Rect(0, 0, w, dm.heightPixels)))
                ui.postDelayed({ try { a.startActivity(i2, opts(Rect(w, 0, dm.widthPixels, dm.heightPixels))) } catch (e: Exception) { Log.e("split-b", e) } }, 700)
                return
            }
            a.startActivity(i1.apply { removeFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK) })
            ui.postDelayed({
                if (!CircleService.global(a, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) return@postDelayed
                ui.postDelayed({
                    try {
                        a.startActivity(i2.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
                    } catch (e: Exception) {
                        Log.e("split-b", e)
                    }
                }, 1200)
            }, 1000)
        } catch (e: Exception) {
            Log.e("split", e)
            a.toast("Could not open split view")
        }
    }

    fun last(a: Activity) {
        val l = Store.s("split_last").split(',')
        if (l.size == 2 && l[0].isNotBlank() && l[1].isNotBlank()) pair(a, l[0], l[1]) else pick(a)
    }

    /** Choose exactly two apps (pinned, study and recent apps first). */
    fun pick(a: Activity) {
        val first = (Apps.pins() + Apps.study().toList() + LauncherRecents.packages()).distinct()
        val ordered = (first.mapNotNull(Apps::byPkg) + Apps.list.filter { it.pkg !in first }).distinct().take(80)
        val sel = BooleanArray(ordered.size)
        val d =
            Dlg.builder(a).setTitle("Split view: tick two apps")
                .setMultiChoiceItems(ordered.map { it.label }.toTypedArray(), sel) { dlg, i, on ->
                    sel[i] = on
                    if (sel.count { it } > 2) {
                        sel[i] = false
                        (dlg as android.app.AlertDialog).listView.setItemChecked(i, false)
                        a.toast("Only two apps")
                    }
                }
                .setPositiveButton("Open") { _, _ ->
                    val p = ordered.filterIndexed { i, _ -> sel[i] }.map { it.pkg }
                    if (p.size == 2) pair(a, p[0], p[1]) else a.toast("Tick two apps")
                }
                .setNegativeButton("Cancel", null)
        d.show()
    }
}
