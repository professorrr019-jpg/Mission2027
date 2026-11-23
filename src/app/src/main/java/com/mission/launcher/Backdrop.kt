package com.mission.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Pre-rendered 3840x2400 Prayagraj Sangam scenes (fort, Yamuna cable bridge, river, boats) for 10 times of day,
 * plus a foggy variant for the morning and day hours. They are cross-faded by the real (sunrise/sunset aware) clock;
 * stars, moon, sun and weather are still drawn live on top.
 */
object Backdrop {
    @Volatile var ctx: Context? = null
    private const val IW = 3840
    private const val IH = 2400
    private const val IHZ = 1488
    private val NAMES = arrayOf("n2", "d5", "s62", "m85", "n12", "a155", "g175", "s185", "t193", "n215")
    private val HOURS = floatArrayOf(2f, 5f, 6.2f, 8.5f, 12f, 15.5f, 17.5f, 18.5f, 19.3f, 21.5f)
    private val FOG = setOf("d5", "s62", "m85", "n12", "a155", "g175")
    private val cache = LinkedHashMap<String, Bitmap>()
    private var maskBmp: Bitmap? = null
    private var maskKey = ""

    fun available(): Boolean =
        try {
            ctx?.assets?.list("wallpaper")?.contains("wp_n12.jpg") == true
        } catch (e: Exception) {
            false
        }

    @Synchronized
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        maskBmp?.recycle()
        maskBmp = null
        maskKey = ""
    }

    @Synchronized
    private fun load(name: String, needW: Float): Bitmap? {
        val key = "$name@${needW.toInt()}"
        cache[key]?.let {
            if (!it.isRecycled) return it
        }
        val c = ctx ?: return null
        return try {
            var ss = 1
            while (IW / (ss * 2) >= needW && ss < 4) ss *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = ss }
            val b = c.assets.open("wallpaper/$name.jpg").use { BitmapFactory.decodeStream(it, null, o) } ?: return null
            while (cache.size >= 3) {
                val k = cache.keys.first()
                cache.remove(k)?.let { old -> if (!old.isRecycled) old.recycle() }
            }
            cache[key] = b
            b
        } catch (e: Exception) {
            Log.e("backdrop", e)
            null
        }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** Draws the scene cover-fitted into w x h. Returns the horizon y in output pixels, or -1f when assets are missing. */
    fun draw(cv: Canvas, w: Int, h: Int, ref: Float, fog: Boolean): Float {
        if (!available()) return -1f
        val s = maxOf(w / IW.toFloat(), h / IH.toFloat())
        val offX = (w - IW * s) / 2f
        val offY = (h - IH * s) / 2f
        var hr = ref
        if (hr < HOURS[0]) hr += 24f
        var i = HOURS.size - 1
        for (k in HOURS.indices) if (HOURS[k] <= hr) i = k
        val j = (i + 1) % HOURS.size
        val ha = HOURS[i]
        val hb = if (j == 0) HOURS[0] + 24f else HOURS[j]
        val t = smooth(((hr - ha) / (hb - ha)).coerceIn(0f, 1f))
        fun nm(n: String) = if (fog && n in FOG) n + "_fog" else n
        val a = load(nm(NAMES[i]), IW * s) ?: return -1f
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        fun put(b: Bitmap, alpha: Int) {
            val m = Matrix()
            m.postScale(IW / b.width.toFloat() * s, IH / b.height.toFloat() * s)
            m.postTranslate(offX, offY)
            p.alpha = alpha
            cv.drawBitmap(b, m, p)
        }
        put(a, 255)
        if (t > 0.01f) load(nm(NAMES[j]), IW * s)?.let { put(it, (t * 255).toInt()) }
        return offY + IHZ * s
    }

    /** Alpha mask of the silhouettes above the horizon, placed like [draw], so stars never show through the fort or bridge. */
    @Synchronized
    fun cutSilhouettes(layer: Canvas, w: Int, h: Int) {
        val c = ctx ?: return
        try {
            val key = "$w"
            if (maskBmp == null || maskKey != key) {
                maskBmp?.recycle()
                val b = c.assets.open("wallpaper/wp_land_mask.png").use { BitmapFactory.decodeStream(it) } ?: return
                maskBmp = b.extractAlpha()
                b.recycle()
                maskKey = key
            }
            val mb = maskBmp ?: return
            val s = maxOf(w / IW.toFloat(), h / IH.toFloat())
            val m = Matrix()
            m.postScale(IW / mb.width.toFloat() * s, IW / mb.width.toFloat() * s)
            m.postTranslate((w - IW * s) / 2f, (h - IH * s) / 2f)
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
            layer.drawBitmap(mb, m, p)
        } catch (e: Exception) {
            Log.e("backdrop-mask", e)
        }
    }
}
