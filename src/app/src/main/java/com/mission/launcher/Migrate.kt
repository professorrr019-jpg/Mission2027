package com.mission.launcher

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/** One-time import of data written by the previous launcher version (SharedPreferences "m"). */
object Migrate {
    fun run(c: Context) {
        val p = c.getSharedPreferences("m", 0)
        p.getString("gkey", null)?.takeIf { it.isNotBlank() }?.let {
            try {
                SecureStore.put("ai:gemini", it)
                check(p.edit().remove("gkey").commit()) { "Legacy credential could not be removed" }
            } catch (e: Exception) {
                Log.e("migrate-credential", e)
                check(p.edit().remove("gkey").commit()) { "Legacy credential could not be removed" }
            }
        }
        if (!Store.b("circle_panel_v1")) {
            Store.put("circle_style", "panel")
            Store.put("circle_panel_v1", true)
        }
        if (!Store.b("pro_cards_v1")) {
            try {
                val pages = HomeLayout.load()
                if (pages.length() > 0) {
                    fun add(i: Int, id: String) {
                        val pg = pages.getJSONObject(minOf(i, pages.length() - 1))
                        val cs = HomeLayout.cards(pg)
                        if (id !in cs) HomeLayout.setCards(pg, listOf(id) + cs)
                    }
                    add(0, "score")
                    add(1, "pace")
                    add(2, "thinkers")
                    HomeLayout.save(pages)
                }
                Store.put("pro_cards_v1", true)
            } catch (e: Exception) {
                Log.e("migrate-pro", e)
            }
        }
        if (!Store.b("size_v2")) {
            if (Store.i("dock_icon", 30) == 30) Store.put("dock_icon", 15)
            Store.put("size_v2", true)
        }
        if (Store.b("migrated_v1")) return
        if (p.all.isEmpty()) {
            Store.put("migrated_v1", true)
            return
        }

        fun rows(k: String) = (p.getString(k, "") ?: "").split("\n").filter { it.isNotBlank() }
        try {
            rows("plan").forEach {
                    l ->
                val x = l.split("|", limit = 3)
                if (x.size == 3) Store.add("tasks", obj("date" to x[0], "done" to (x[1] == "1"), "text" to x[2]))
            }
            rows("notes").forEach {
                    l ->
                val d = l.substringBefore("|")
                Store.add("notes", obj("date" to d, "text" to l.substringAfter("|"), "t" to dayMs(d)))
            }
            rows("scores").forEach {
                    l ->
                val x = l.split("|")
                if (x.size >= 2) Store.add("scores", obj("date" to x[0], "score" to (x[1].toDoubleOrNull() ?: 0.0), "title" to "Mock"))
            }
            rows("rev").forEach {
                    l ->
                val x = l.split("|")
                if (x.size >= 3) Store.add("revisions", obj("topic" to x[0], "last" to x[1], "round" to (x[2].toIntOrNull() ?: 0)))
            }
            rows("water").forEach {
                    l ->
                val x = l.split("|")
                if (x.size >= 2) Store.add("water", obj("t" to localMs(x[0], LocalTime.NOON), "ml" to (x[1].toIntOrNull() ?: 0)))
            }
            p.all.keys.filter { it.startsWith("fm_") }.forEach {
                    k ->
                val d = k.removePrefix("fm_")
                val m = p.getInt(k, 0)
                if (m > 0) {
                    val s = localMs(d, LocalTime.of(9, 0))
                    Store.add(
                        "sessions",
                        obj("start" to s, "end" to s + m * 60000L, "mins" to m, "subject" to "General", "kind" to "focus"),
                    )
                }
            }
            val pins = (p.getString("pins", "") ?: "").split(",").filter { it.isNotBlank() }
            if (pins.isNotEmpty()) Store.setStringList("pins", pins)
            p.getString("exam", null)?.let { Store.put("exam_date", it) }
            p.getInt("wgoal", 0).let { if (it in 500..8000) Store.put("water_goal", it) }
        } catch (e: Exception) {
            Log.e("migrate", e)
        }
        Store.put("migrated_v1", true)
    }

    private fun dayMs(d: String): Long =
        try {
            LocalDate.parse(d).atStartOfDay(Repo.zone()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

    private fun localMs(
        d: String,
        time: LocalTime,
    ): Long =
        try {
            LocalDate.parse(d).atTime(time).atZone(Repo.zone()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
}
