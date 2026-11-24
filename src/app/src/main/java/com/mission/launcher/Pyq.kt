package com.mission.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * PYQ master data (assets/pyq_master.json): every Prelims question 2007-2026 allotted to one micro-theme of one subject.
 * This is the single source for the syllabus tree, question counts, tiers, colours and the PYQ explorer.
 * Tier is set by the number of questions in the last 5 years: Tier-1 six or more, Tier-2 two to five, Tier-3 fewer.
 */
object Pyq {
    class Q(val year: Int, val no: Int, val kw: String, val hi: String, val diff: String, val ans: String)

    class M(val subject: String, val subjIdx: Int, val name: String, val terms: List<Pair<String, Int>>, val qs: List<Q>) {
        val key: String get() = "$subject|$name"
        val total: Int get() = qs.size
        val last15: Int get() = qs.count { it.year >= Y15 }
        val recent5: Int get() = qs.count { it.year >= Y5 }
        val tier: Int get() = if (recent5 >= 6) 1 else if (recent5 >= 2) 2 else 3
        fun byYear(): Map<Int, Int> = qs.groupingBy { it.year }.eachCount()
    }

    class S(val name: String, val idx: Int, val micros: List<M>) {
        val total: Int get() = micros.sumOf { it.total }
        val color: Int get() = T.palette[idx % T.palette.size]
    }

    const val Y15 = 2012
    const val Y5 = 2022

    @Volatile var subjects: List<S> = emptyList()
        private set
    @Volatile var years: List<Int> = emptyList()
        private set
    val ready: Boolean get() = subjects.isNotEmpty()

    private val byKey = HashMap<String, M>()

    fun load(c: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            try {
                val j = JSONObject(c.assets.open("pyq_master.json").bufferedReader().use { it.readText() })
                val ys = j.getJSONArray("years")
                val yl = (0 until ys.length()).map { ys.getInt(it) }
                val sa = j.getJSONArray("s")
                val qa = j.getJSONArray("q")
                val buckets = HashMap<String, ArrayList<Q>>()
                for (i in 0 until qa.length()) {
                    val r = qa.getJSONArray(i)
                    val k = r.getInt(2).toString() + "/" + r.getInt(3)
                    buckets.getOrPut(k) { ArrayList() }.add(Q(r.getInt(0), r.getInt(1), r.optString(4), r.optString(5), r.optString(6), r.optString(7)))
                }
                val list = ArrayList<S>()
                byKey.clear()
                for (si in 0 until sa.length()) {
                    val so = sa.getJSONObject(si)
                    val sn = so.getString("n")
                    val ma = so.getJSONArray("m")
                    val ms = ArrayList<M>()
                    for (mi in 0 until ma.length()) {
                        val mo = ma.getJSONObject(mi)
                        val ta = mo.optJSONArray("t") ?: JSONArray()
                        val terms = (0 until ta.length()).map { ta.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
                        val qs = (buckets["$si/$mi"] ?: ArrayList<Q>()).sortedWith(compareByDescending<Q> { it.year }.thenBy { it.no })
                        val m = M(sn, si, mo.getString("n"), terms, qs)
                        ms.add(m)
                        byKey[m.key] = m
                    }
                    list.add(S(sn, si, ms))
                }
                years = yl
                subjects = list
            } catch (e: Exception) {
                Log.e("pyq-load", e)
            }
        }
    }

    fun micro(key: String): M? = byKey[key]

    fun subject(name: String): S? = subjects.firstOrNull { it.name == name } ?: subjects.firstOrNull { sameSubject(it.name, name) }

    fun colorOf(subjectName: String): Int = subject(subjectName)?.color ?: T.accent

    fun tierColor(t: Int): Int = when (t) { 1 -> T.danger; 2 -> T.amber; else -> T.slate }

    fun tierName(t: Int): String = "Tier-$t"

    fun totalQuestions(): Int = subjects.sumOf { it.total }

    /** year -> subject -> count, for the PYQ year grid. */
    fun yearTable(): Map<Int, Map<String, Int>> {
        val out = LinkedHashMap<Int, Map<String, Int>>()
        years.forEach { y ->
            out[y] = subjects.associate { s -> s.name to s.micros.sumOf { m -> m.qs.count { it.year == y } } }
        }
        return out
    }

    /** Syllabus tree: subject -> micro-theme -> top keyword terms (counts) as sub-topics. */
    fun syl(): List<Subj> = subjects.map { s -> Subj(s.name, s.micros.map { m -> Micro(m.name, m.terms.map { Sub(it.first, it.second) }.ifEmpty { listOf(Sub(m.name, m.total)) }) }) }

    // -------- mapping of old taxonomy names onto the new one --------
    private fun firstWord(s: String) = s.lowercase().replace(Regex("[^a-z ]"), " ").trim().split(' ').firstOrNull { it.isNotBlank() } ?: ""

    fun sameSubject(a: String, b: String) = firstWord(a) == firstWord(b) && firstWord(a).isNotEmpty()

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in STOP }.toSet()

    private val STOP = setOf("and", "the", "for", "with", "from", "other", "general", "basics")

    /** Best new micro-theme for an old (subject, micro) pair. Falls back to the old name when nothing matches. */
    fun mapMicro(subject: String, micro: String): String {
        if (!ready) return micro
        val s = subject(subject) ?: return micro
        if (s.micros.any { it.name == micro }) return micro
        val w = words(micro)
        if (w.isEmpty()) return micro
        var best: M? = null
        var bs = 0.0
        s.micros.forEach { m ->
            val mw = words(m.name) + m.terms.flatMap { words(it.first) }.toSet()
            val inter = w.intersect(mw).size
            if (inter > 0) {
                val sc = inter.toDouble() / (w + words(m.name)).size.coerceAtLeast(1) + inter * 0.05
                if (sc > bs) { bs = sc; best = m }
            }
        }
        return best?.name ?: micro
    }

    fun mapKey(key: String): String {
        val i = key.indexOf('|')
        if (i < 0 || !ready) return key
        val subj = key.substring(0, i)
        val rest = key.substring(i + 1)
        val j = rest.indexOf('|')
        val micro = if (j < 0) rest else rest.substring(0, j)
        val s = subject(subj) ?: return key
        return s.name + "|" + mapMicro(s.name, micro) + (if (j < 0) "" else rest.substring(j))
    }
}
