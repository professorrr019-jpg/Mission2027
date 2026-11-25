package com.mission.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real photographs from free, key-less web sources, cached on the device:
 *  - wiki: Wikimedia Commons (Prayagraj / Sangam / Ganga / Yamuna / fort), Creative Commons, credit saved
 *  - bing: Bing photo of the day (India market), UHD
 *  - apod: NASA Astronomy Picture of the Day (DEMO_KEY, images only)
 * Photos are downloaded in the background at most once every 12 hours; nothing is fetched on a metered-off or offline device.
 */
object Online {
    private const val UA = "Mission2027Launcher/3.1 (personal UPSC launcher; contact via GitHub)"
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun dir(c: Context) = File(c.filesDir, "online").apply { mkdirs() }

    fun files(c: Context): List<File> = dir(c).listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()

    fun credit(): String = Store.s("online_credit", "")

    private fun get(u: String): String? =
        try {
            val cn = URL(u).openConnection() as HttpURLConnection
            cn.connectTimeout = 10000
            cn.readTimeout = 15000
            cn.setRequestProperty("User-Agent", UA)
            if (cn.responseCode != 200) null else cn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("online-get", e)
            null
        }

    private fun save(c: Context, url: String, name: String): Boolean =
        try {
            val cn = URL(url).openConnection() as HttpURLConnection
            cn.connectTimeout = 10000
            cn.readTimeout = 30000
            cn.setRequestProperty("User-Agent", UA)
            if (cn.responseCode != 200) {
                false
            } else {
                val tmp = File(dir(c), "$name.part")
                cn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                // only keep what decodes as a real landscape photo at least 1600 px wide
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.path, o)
                if (o.outWidth >= 1600 && o.outWidth > o.outHeight) tmp.renameTo(File(dir(c), "$name.jpg")) else {
                    tmp.delete()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("online-save", e)
            false
        }

    private fun wiki(c: Context): Int {
        var got = 0
        val queries = listOf("Triveni Sangam Prayagraj", "Ganges Yamuna confluence Allahabad", "Allahabad Fort Yamuna", "Prayagraj Ganga ghat sunrise", "Naini bridge Yamuna Prayagraj")
        val q = queries[(System.currentTimeMillis() / 86400000L % queries.size).toInt()]
        val u =
            "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
                java.net.URLEncoder.encode("$q filetype:bitmap", "UTF-8") +
                "&gsrnamespace=6&gsrlimit=20&prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=2560&format=json"
        val pages = JSONObject(get(u) ?: return 0).optJSONObject("query")?.optJSONObject("pages") ?: return 0
        val it = pages.keys()
        while (it.hasNext() && got < 4) {
            val p = pages.getJSONObject(it.next())
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            if (ii.optInt("width") < 2400 || ii.optInt("width") <= ii.optInt("height")) continue
            val lic = ii.optJSONObject("extmetadata")?.optJSONObject("LicenseShortName")?.optString("value", "") ?: ""
            if (!lic.startsWith("CC") && !lic.contains("Public", true)) continue
            val thumb = ii.optString("thumburl")
            if (thumb.isBlank()) continue
            val name = "wiki_" + p.optString("title").hashCode().toUInt().toString(16)
            if (File(dir(c), "$name.jpg").exists() || save(c, thumb, name)) {
                got++
                val artist = (ii.optJSONObject("extmetadata")?.optJSONObject("Artist")?.optString("value", "") ?: "").replace(Regex("<[^>]*>"), "")
                Store.put("online_credit", "Wikimedia Commons: " + p.optString("title").removePrefix("File:") + " - " + artist.take(60) + " (" + lic + ")")
            }
        }
        return got
    }

    private fun bing(c: Context): Int {
        var got = 0
        val arr: JSONArray = JSONObject(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=6&mkt=en-IN") ?: return 0).optJSONArray("images") ?: return 0
        for (i in 0 until arr.length()) {
            val im = arr.getJSONObject(i)
            val base = im.optString("urlbase")
            if (base.isBlank()) continue
            val name = "bing_" + base.hashCode().toUInt().toString(16)
            if (File(dir(c), "$name.jpg").exists() || save(c, "https://www.bing.com$base" + "_UHD.jpg", name)) {
                got++
                Store.put("online_credit", "Bing: " + im.optString("title") + " - " + im.optString("copyright").take(70))
            }
        }
        return got
    }

    private fun apod(c: Context): Int {
        val j = JSONObject(get("https://api.nasa.gov/planetary/apod?api_key=" + SecureStore.get("nasa").ifBlank { "DEMO_KEY" }) ?: return 0)
        if (j.optString("media_type") != "image") return 0
        val url = j.optString("hdurl", j.optString("url"))
        val name = "apod_" + j.optString("date")
        return if (File(dir(c), "$name.jpg").exists() || save(c, url, name)) {
            Store.put("online_credit", "NASA APOD: " + j.optString("title") + " " + j.optString("copyright").trim())
            1
        } else {
            0
        }
    }

    private fun picsum(c: Context): Int {
        // Lorem Picsum: free, no key, random landscape photos by Unsplash photographers, one set per day
        var got = 0
        val day = TimeProvider.today().toString()
        for (i in 0 until 4) {
            val name = "pics_$day-$i"
            if (File(dir(c), "$name.jpg").exists() || save(c, "https://picsum.photos/seed/mission-$day-$i/2880/1800.jpg", name)) got++
        }
        if (got > 0) Store.put("online_credit", "Lorem Picsum - photos by Unsplash photographers (free licence)")
        return got
    }

    /** Download new photos in the background (max once per 12 h unless [force]). Old files beyond 12 are removed. */
    fun refreshAsync(c0: Context, force: Boolean = false, done: ((Int) -> Unit)? = null) {
        val c = c0.applicationContext
        if (!force && System.currentTimeMillis() - Store.l("online_at", 0L) < 12 * 3600000L && files(c).isNotEmpty()) return
        if (!running.compareAndSet(false, true)) return
        Thread {
            var n = 0
            try {
                Store.put("online_at", System.currentTimeMillis())
                n =
                    when (Store.s("online_src", "wiki")) {
                        "bing" -> bing(c)
                        "apod" -> apod(c)
                        "picsum" -> picsum(c)
                        else -> wiki(c)
                    }
                files(c).drop(12).forEach { it.delete() }
                if (n > 0) Store.put("wp_last", 0L)
            } catch (e: Exception) {
                Log.e("online", e)
            } finally {
                running.set(false)
                done?.invoke(n)
            }
        }.start()
    }

    private var sharpKey = ""
    private var sharpBmp: Bitmap? = null

    /** Today's photo, cover-decoded to about w x h. */
    fun decode(c: Context, w: Int, h: Int): Bitmap? {
        val list = files(c).filter { it.name.startsWith(Store.s("online_src", "wiki").take(4)) }.ifEmpty { files(c) }
        if (list.isEmpty()) return null
        val f = list[(TimeProvider.today().dayOfYear + (java.time.LocalTime.now(Repo.zone()).hour / 6)) % list.size]
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var ss = 1
            while (b.outWidth / (ss * 2) >= w && b.outHeight / (ss * 2) >= h) ss *= 2
            val key = f.path + "@" + ss
            if (key == sharpKey && sharpBmp != null && !sharpBmp!!.isRecycled) return sharpBmp
            val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss }) ?: return null
            val out = if (Store.b("wp_sharpen", true)) Sharpen.edges(raw, 0.45f) else raw
            if (out !== raw) raw.recycle()
            sharpKey = key
            sharpBmp = out
            out
        } catch (e: Throwable) {
            null
        }
    }
}
