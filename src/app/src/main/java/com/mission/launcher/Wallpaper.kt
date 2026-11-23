package com.mission.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.net.Uri
import android.os.PowerManager
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Options for one render. */
class WOpts(val grain: Boolean = true, val comfort: Boolean = true, val season: Boolean = true, val dim: Float = 0f)

/**
 * Procedural, continuously time-of-day-aware wallpapers. Colours are interpolated smoothly between
 * keyframes (no sudden jumps), blue is reduced at night, and dithering grain prevents banding.
 */
object Scenes {
    val names =
        linkedMapOf(
            "online" to "Real photos (online)",
            "sangam" to "Prayagraj Sangam (4K)",
            "hills" to "Layered hills",
            "dunes" to "Soft dunes",
            "forest" to "Pine ridge",
            "ocean" to "Calm sea",
            "waves" to "Pastel waves",
            "gradient" to "Minimal glow",
            "paper" to "Warm paper",
            "amoled" to "AMOLED dark",
        )

    private fun lerp(
        a: Float,
        b: Float,
        t: Float,
    ) = a + (b - a) * t

    private fun lc(
        a: Int,
        b: Int,
        t: Float,
    ): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            255,
            lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), k).toInt(),
            lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), k).toInt(),
            lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), k).toInt(),
        )
    }

    private fun smooth(
        e0: Float,
        e1: Float,
        x: Float,
    ): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun hash(
        i: Int,
        seed: Int,
    ): Float {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x shr 13)) * 1274126177
        x = x xor (x shr 16)
        return (x and 0xffff) / 65535f
    }

    private fun noise(
        x: Float,
        seed: Int,
    ): Float {
        val i = floor(x).toInt()
        val f = x - i
        val u = f * f * (3 - 2 * f)
        return lerp(hash(i, seed), hash(i + 1, seed), u)
    }

    private fun fbm(
        x: Float,
        seed: Int,
    ): Float = noise(x, seed) * 0.55f + noise(x * 2.1f, seed + 7) * 0.28f + noise(x * 4.3f, seed + 13) * 0.17f

    // (hour, top colour, horizon colour)
    private val keys =
        listOf(
            Triple(0f, 0xFF0F1526.toInt(), 0xFF1D2740.toInt()), Triple(4.5f, 0xFF1A2138.toInt(), 0xFF3A3652.toInt()),
            Triple(6f, 0xFF5E6C99.toInt(), 0xFFF0B48C.toInt()), Triple(8f, 0xFF86A6CE.toInt(), 0xFFF6DEC4.toInt()),
            Triple(12f, 0xFF8EB3D6.toInt(), 0xFFE8EEEF.toInt()), Triple(16f, 0xFF90AECB.toInt(), 0xFFF1E4CF.toInt()),
            Triple(18f, 0xFF716C9C.toInt(), 0xFFF2AA7B.toInt()), Triple(19.4f, 0xFF3B3359.toInt(), 0xFFC66E5C.toInt()),
            Triple(21f, 0xFF161B30.toInt(), 0xFF2A3050.toInt()), Triple(24f, 0xFF0F1526.toInt(), 0xFF1D2740.toInt()),
        )

    fun sky(h: Float): Pair<Int, Int> {
        var i = 0
        while (i < keys.size - 2 && h >= keys[i + 1].first) i++
        val a = keys[i]
        val b = keys[i + 1]
        val t = smooth(0f, 1f, (h - a.first) / (b.first - a.first))
        return lc(a.second, b.second, t) to lc(a.third, b.third, t)
    }

    fun daylight(h: Float) = smooth(5.5f, 7.8f, h) * (1f - smooth(17.2f, 19.6f, h))

    /**
     * The colour table above is written for a 6:25 sunrise and 18:10 sunset. Real Prayagraj day length changes with the month
     * (winter sunset about 17:25, June sunset about 19:10), so the clock hour is stretched so that sky colours follow the real sun.
     */
    fun refHour(
        hour: Float,
        date: LocalDate,
    ): Float {
        val (sr, ss) = MoonMath.sunTimes(date)
        val r =
            when {
                hour < sr - 1.0f -> hour - (sr - 1.0f) + 5.3f
                hour < sr -> 5.3f + (hour - (sr - 1.0f)) * 1.1f
                hour <= ss -> 6.4f + (hour - sr) / (ss - sr) * (18.2f - 6.4f)
                hour <= ss + 1.4f -> 18.2f + (hour - ss) / 1.4f * 1.4f
                else -> hour - (ss + 1.4f) + 19.6f
            }
        return ((r % 24f) + 24f) % 24f
    }

    private fun comfort(
        c: Int,
        hour: Float,
        on: Boolean,
    ): Int {
        if (!on) return c
        val night = 1f - daylight(hour)
        val k = 0.16f * night
        return Color.argb(
            255,
            min(255, (Color.red(c) * (1f + k * 0.35f)).toInt()),
            (Color.green(c) * (1f - k * 0.05f)).toInt(),
            (Color.blue(c) * (1f - k)).toInt(),
        )
    }

    fun render(
        w: Int,
        h: Int,
        hour: Float,
        scene: String,
        month: Int,
        o: WOpts,
        dayKey: Int = 0,
        date: LocalDate = TimeProvider.today(),
        weather: WeatherSnapshot? = null,
    ): Bitmap {
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val ref = refHour(hour, date)
        val (top, hz) = sky(ref)
        val L = daylight(ref)
        val horizon =
            h *
                when (scene) {
                    "ocean" -> 0.58f
                    "dunes" -> 0.55f
                    "forest" -> 0.6f
                    else -> 0.62f
                }
        when (scene) {
            "paper" -> {
                cv.drawColor(lc(0xFFEDE8DE.toInt(), 0xFF2A2825.toInt(), 1f - L))
            }
            "amoled" -> {
                cv.drawColor(Color.BLACK)
                val g =
                    RadialGradient(
                        w * 0.5f,
                        h * 1.05f,
                        h * 0.9f,
                        intArrayOf(lc(0xFF000000.toInt(), top, 0.55f), 0xFF000000.toInt()),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                p.shader = g
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
            }
            "gradient" -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
                glow(cv, p, w * 0.72f, h * 0.28f, h * 0.7f, lc(hz, Color.WHITE, 0.4f), 0.30f)
            }
            "online" -> {
                val oc = Backdrop.ctx
                val ph = if (oc != null) Online.decode(oc, w, h) else null
                if (oc != null) Online.refreshAsync(oc)
                if (ph == null) {
                    val hy = Backdrop.draw(cv, w, h, ref, false)
                    if (hy < 0f) {
                        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP)
                        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                        p.shader = null
                    }
                } else {
                    val sc = max(w.toFloat() / ph.width, h.toFloat() / ph.height)
                    val m = Matrix()
                    m.postScale(sc, sc)
                    m.postTranslate((w - ph.width * sc) / 2f, (h - ph.height * sc) / 2f)
                    val pp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    // grade the real photo by the real light: darker and bluer at night, warm near sunrise and sunset
                    val k = 0.22f + 0.78f * L
                    val cm = ColorMatrix(floatArrayOf(k * 0.92f, 0f, 0f, 0f, 0f, 0f, k * 0.97f, 0f, 0f, 0f, 0f, 0f, k * (1f + (1f - L) * 0.25f).coerceAtMost(1.15f), 0f, 4f * (1f - L), 0f, 0f, 0f, 1f, 0f))
                    pp.colorFilter = ColorMatrixColorFilter(cm)
                    cv.drawBitmap(ph, m, pp)
                    val warm = (1f - kotlin.math.abs(L - 0.45f) * 2.2f).coerceIn(0f, 1f) * (if (L in 0.08f..0.8f) 1f else 0f)
                    if (warm > 0.02f) {
                        p.color = lc(hz, Color.WHITE, 0.2f)
                        p.alpha = (warm * 70).toInt()
                        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                        p.alpha = 255
                    }
                    if (L < 0.35f) {
                        val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val lc2 = Canvas(layer)
                        lc2.clipRect(0f, 0f, w.toFloat(), h * 0.55f)
                        celestial(lc2, p, w, h, h * 0.55f, hour, L, hz, lunarPhase(date), date, weather)
                        cv.drawBitmap(layer, 0f, 0f, null)
                        layer.recycle()
                    }
                    ph.recycle()
                }
            }
            "sangam" -> {
                val fog = weather?.let { Weather.mapWeatherCode(it.code, it.visibilityM) in listOf(Weather.Scene.FOG, Weather.Scene.HAZE, Weather.Scene.OVERCAST, Weather.Scene.DRIZZLE) } ?: false
                val hy = Backdrop.draw(cv, w, h, ref, fog)
                if (hy < 0f) {
                    p.shader = LinearGradient(0f, 0f, 0f, horizon, comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP)
                    cv.drawRect(0f, 0f, w.toFloat(), horizon + 2, p)
                    p.shader = null
                    celestial(cv, p, w, h, horizon, hour, L, hz, lunarPhase(date), date, weather)
                    clouds(cv, p, w, h, horizon, hour, L, hz, dayKey, 1f)
                    ridges(cv, p, w, h, horizon, hz, L, dayKey, 5, 0xFF8995A8.toInt(), 0xFF40504A.toInt(), 0.2f)
                } else {
                    // Live sky objects go on their own layer so the fort and bridge can hide them.
                    val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val lc2 = Canvas(layer)
                    lc2.clipRect(0f, 0f, w.toFloat(), hy)
                    celestial(lc2, p, w, h, hy, hour, L, hz, lunarPhase(date), date, weather)
                    Backdrop.cutSilhouettes(lc2, w, h)
                    cv.drawBitmap(layer, 0f, 0f, null)
                    layer.recycle()
                }
            }
            else -> {
                p.shader = LinearGradient(0f, 0f, 0f, horizon, comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), horizon + 2, p)
                p.shader = null
                celestial(cv, p, w, h, horizon, hour, L, hz, lunarPhase(date), date, weather)
                clouds(cv, p, w, h, horizon, hour, L, hz, dayKey, if (o.season && month in 6..9) 1.6f else 1f)
                when (scene) {
                    "hills" -> ridges(cv, p, w, h, horizon, hz, L, dayKey, 5, 0xFF8995A8.toInt(), 0xFF40504A.toInt(), 0.2f)
                    "forest" -> {
                        ridges(cv, p, w, h, horizon, hz, L, dayKey, 5, 0xFF8FA39A.toInt(), 0xFF24352E.toInt(), 0.26f)
                        pines(cv, p, w, h, L, dayKey)
                    }
                    "dunes" -> dunes(cv, p, w, h, horizon, hz, L, dayKey)
                    "ocean" -> ocean(cv, p, w, h, horizon, hz, top, hour, L)
                    "waves" -> waves(cv, p, w, h, horizon, hz, L)
                }
            }
        }
        if (o.season) {
            when {
                month in 6..9 && scene != "amoled" -> {
                    p.color = 0xFF55606E.toInt()
                    p.alpha = (40 * L + 18).toInt()
                    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                }
                (month == 12 || month <= 2) && scene != "amoled" -> {
                    p.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0x00E6ECF2, 0x66E6ECF2, Shader.TileMode.CLAMP)
                    cv.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), p)
                    p.shader = null
                }
                month in 3..5 && L > 0.2f -> {
                    p.color = 0xFFFF9A3C.toInt()
                    p.alpha = (26 * L).toInt()
                    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                }
            }
        }
        weather?.let { weatherEffects(cv, p, w, h, it) }
        finish(bm, o)
        return bm
    }

    fun finish(
        bm: Bitmap,
        o: WOpts,
    ) {
        val cv = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (o.grain) {
            val t = noiseTile()
            p.shader = BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            p.alpha = 255
            cv.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), p)
            p.shader = null
        }
        if (o.dim > 0f) {
            p.color = Color.BLACK
            p.alpha = (o.dim * 255).toInt()
            cv.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), p)
        }
    }

    private var tile: Bitmap? = null

    private fun noiseTile(): Bitmap {
        tile?.let { return it }
        val s = 96
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val r = java.util.Random(11)
        val px =
            IntArray(s * s) {
                val v = r.nextInt(255)
                Color.argb(9, v, v, v)
            }
        b.setPixels(px, 0, s, 0, 0, s, s)
        tile = b
        return b
    }

    private fun glow(
        cv: Canvas,
        p: Paint,
        x: Float,
        y: Float,
        r: Float,
        col: Int,
        a: Float,
    ) {
        p.shader = RadialGradient(x, y, r, intArrayOf(T.withAlpha(col, a), T.withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(x, y, r, p)
        p.shader = null
    }

    private fun lunarPhase(date: LocalDate): Float {
        return MoonMath.phase(date).toFloat()
    }

    private fun weatherEffects(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        weather: WeatherSnapshot,
    ) {
        when (weather.code) {
            45, 48 -> {
                p.shader =
                    LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        intArrayOf(0x24D8DFE4, 0x42D8DFE4, 0x18D8DFE4),
                        floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP,
                    )
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
            }
            3 -> {
                p.color = 0xFF667181.toInt()
                p.alpha = 28
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.alpha = 255
            }
            in 51..67, in 80..82, in 95..99 -> {
                val seed = (weather.fetchedAt / (30 * 60 * 1000L)).toInt()
                val random = java.util.Random(seed.toLong())
                val count = (w * h / 4200).coerceIn(80, 900)
                p.color = if (weather.code >= 95) 0xFFB7C7D9.toInt() else 0xFFDAE8F4.toInt()
                p.strokeWidth = max(1f, w / 900f)
                p.alpha = if (weather.code >= 95) 105 else 82
                for (i in 0 until count) {
                    val x = random.nextFloat() * w
                    val y = random.nextFloat() * h
                    val length = h * (0.008f + random.nextFloat() * 0.014f)
                    cv.drawLine(x, y, x - length * 0.2f, y + length, p)
                }
                p.alpha = 255
            }
            in 71..77, 85, 86 -> {
                val random = java.util.Random((weather.fetchedAt / (30 * 60 * 1000L)).toInt().toLong())
                val count = (w * h / 7000).coerceIn(60, 700)
                p.color = 0xFFEAF1F6.toInt()
                for (i in 0 until count) {
                    val radius = max(1f, w / 1100f) * (0.7f + random.nextFloat() * 1.4f)
                    p.alpha = 90 + random.nextInt(120)
                    cv.drawCircle(random.nextFloat() * w, random.nextFloat() * h, radius, p)
                }
                p.alpha = 255
            }
        }
    }

    private fun lunarIllumination(phase: Float): Float = (1f - kotlin.math.cos(phase * 2f * PI).toFloat()) * 0.5f

    /** 0 = sky fully hidden (fog, rain, overcast), 1 = crystal clear. Stars and moon fade with it. */
    private fun clearness(weather: WeatherSnapshot?): Float {
        if (weather == null) return 1f
        var k = 1f - weather.cloudCover / 100f * 0.92f
        when (weather.code) {
            45, 48 -> k = minOf(k, 0.12f)
            3 -> k = minOf(k, 0.2f)
            in 51..99 -> k = minOf(k, 0.15f)
        }
        if (weather.visibilityM < 4000) k *= 0.55f
        if (weather.visibilityM < 1000) k *= 0.4f
        return k.coerceIn(0.05f, 1f)
    }

    private val starTints = intArrayOf(0xFFFFFFFF.toInt(), 0xFFCFE0FF.toInt(), 0xFFFFE9C8.toInt(), 0xFFFFD2B0.toInt(), 0xFFBFD4FF.toInt())

    private fun celestial(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hour: Float,
        L: Float,
        hz: Int,
        phase: Float,
        date: LocalDate,
        weather: WeatherSnapshot?,
    ) {
        val clear = clearness(weather)
        val (sr, ss) = MoonMath.sunTimes(date)
        val moonT = MoonMath.moonArc(date, hour.toDouble())
        val moonLight = if (moonT != null) lunarIllumination(phase) else 0f
        // ---- stars: fixed sky (same constellations every night), brightness fades with twilight, cloud, fog and moonlight ----
        val night = (1f - L).coerceIn(0f, 1f)
        if (night > 0.05f) {
            val vis = night * night * clear * (1f - 0.55f * moonLight)
            val r = java.util.Random(5)
            val twinkle = java.util.Random(System.currentTimeMillis() / (30 * 60 * 1000L))
            // faint Milky Way band (procedural, not a photograph): soft strip + dense tiny stars along a diagonal
            if (vis > 0.25f && Store.b("wp_milkyway", true)) {
                val a0 = 0.07f * vis
                p.shader =
                    LinearGradient(
                        0f, horizon * 0.15f, w.toFloat(), horizon * 0.75f,
                        intArrayOf(0x00B8C4E8, Color.argb((255 * a0).toInt(), 190, 200, 235), 0x00B8C4E8),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                    )
                cv.save()
                cv.rotate(-18f, w / 2f, horizon / 2f)
                cv.drawRect(-w * 0.2f, horizon * 0.28f, w * 1.2f, horizon * 0.52f, p)
                cv.restore()
                p.shader = null
                val mr = java.util.Random(11)
                for (i in 0 until 260) {
                    val t = mr.nextFloat()
                    val x = w * (-0.05f + 1.1f * t)
                    val spread = (mr.nextGaussian().toFloat()) * horizon * 0.06f
                    val y = horizon * (0.62f - 0.5f * t) + spread
                    if (y < 0 || y > horizon * 0.9f) continue
                    p.color = Color.WHITE
                    p.alpha = (50 * vis * (0.4f + mr.nextFloat())).toInt()
                    cv.drawCircle(x, y, 0.5f + mr.nextFloat() * 0.6f, p)
                }
            }
            val count = if (w * h > 3_000_000) 620 else 420
            for (i in 0 until count) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * horizon * 0.9f
                val mag = r.nextFloat() // 0 = faintest, 1 = brightest
                val low = 1f - (y / (horizon * 0.9f)) // stars near the horizon are dimmer (thicker air)
                val brightStar = mag > 0.965f
                val size = if (brightStar) 1.6f + mag * 1.2f else 0.4f + mag * mag * 1.3f
                val tw = 0.8f + twinkle.nextFloat() * 0.4f
                p.color = starTints[r.nextInt(starTints.size)]
                p.alpha = (255 * vis * tw * (0.22f + mag * 0.78f) * (0.45f + 0.55f * low)).toInt().coerceIn(0, 255)
                cv.drawCircle(x, y, size * (w / 1920f).coerceIn(0.7f, 1.8f), p)
                if (brightStar && vis > 0.3f) {
                    p.alpha = (110 * vis).toInt()
                    val l = size * 5f
                    p.strokeWidth = 1f
                    cv.drawLine(x - l, y, x + l, y, p)
                    cv.drawLine(x, y - l, x, y + l, p)
                }
            }
            p.alpha = 255
        }
        // ---- sun: true sunrise/sunset for the date ----
        if (hour in sr..ss) {
            val t = ((hour - sr) / (ss - sr)).coerceIn(0f, 1f)
            val x = w * (0.1f + 0.8f * t)
            val y = horizon - sin(PI.toFloat() * t) * horizon * 0.78f
            val haze = 0.35f + 0.65f * clear
            glow(cv, p, x, y, h * 0.3f, 0xFFFFE6B0.toInt(), 0.55f * (0.4f + L * 0.6f) * haze)
            p.color = 0xFFFFF4D0.toInt()
            p.alpha = (255 * (0.55f + 0.45f * clear)).toInt()
            cv.drawCircle(x, y, h * 0.045f, p)
            p.alpha = 255
        }
        // ---- moon: only while above the horizon, with its real phase ----
        if (moonT != null && L < 0.9f) {
            val x = w * (0.1f + 0.8f * moonT)
            val y = horizon - sin(PI.toFloat() * moonT) * horizon * 0.7f
            val fade = (1f - L) * (0.25f + 0.75f * clear)
            val radius = h * 0.04f
            val illumination = lunarIllumination(phase)
            if (illumination > 0.02f) glow(cv, p, x, y, h * 0.2f, 0xFFDDE6FF.toInt(), 0.3f * illumination * fade)
            val disk = Path().apply { addCircle(x, y, radius, Path.Direction.CW) }
            cv.save()
            cv.clipPath(disk)
            // earthshine: the dark side is faintly visible, never a solid disc cut out of the sky
            p.color = 0xFF2B3347.toInt()
            p.alpha = (70 * fade).toInt()
            cv.drawCircle(x, y, radius, p)
            val light =
                when {
                    illumination <= 0.0001f -> null
                    illumination >= 0.9999f -> Path().apply { addCircle(x, y, radius, Path.Direction.CW) }
                    else -> {
                        val waxing = phase < 0.5f
                        val terminatorSide = if ((waxing && phase <= 0.25f) || (!waxing && phase <= 0.75f)) -1f else 1f
                        val terminatorRadius = abs(kotlin.math.cos(phase * 2f * PI).toFloat()) * radius
                        Path().apply {
                            moveTo(x, y - radius)
                            arcTo(RectF(x - radius, y - radius, x + radius, y + radius), -90f, if (waxing) 180f else -180f)
                            if (terminatorRadius < radius * 0.01f) {
                                lineTo(x, y - radius)
                            } else {
                                arcTo(RectF(x - terminatorRadius, y - radius, x + terminatorRadius, y + radius), 90f, 180f * terminatorSide)
                            }
                            close()
                        }
                    }
                }
            if (light != null) {
                p.shader = LinearGradient(x - radius, y - radius, x + radius, y + radius, 0xFFF6F2E6.toInt(), 0xFFD8D9D4.toInt(), Shader.TileMode.CLAMP)
                p.alpha = (255 * fade).toInt()
                cv.drawPath(light, p)
                p.shader = null
                // maria and craters (fixed pattern so the moon looks the same every night)
                cv.save()
                cv.clipPath(light)
                val cr = java.util.Random(77)
                for (i in 0 until 16) {
                    val a = cr.nextFloat() * 2f * PI.toFloat()
                    val d = sqrt(cr.nextFloat()) * radius * 0.82f
                    p.color = if (i < 6) 0xFF9AA0AE.toInt() else 0xFFB9BDC6.toInt()
                    p.alpha = ((if (i < 6) 95 else 60) * fade).toInt()
                    cv.drawCircle(x + kotlin.math.cos(a) * d, y + sin(a) * d, radius * (0.1f + cr.nextFloat() * (if (i < 6) 0.24f else 0.12f)), p)
                }
                cv.restore()
            }
            p.alpha = 255
            cv.restore()
        }
    }

    private fun clouds(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hour: Float,
        L: Float,
        hz: Int,
        seed: Int,
        density: Float,
    ) {
        val r = java.util.Random(seed.toLong() + 3)
        val n = (6 * density).toInt()
        val drift = (hour / 24f) * w * 0.5f
        for (i in 0 until n) {
            val cx = ((r.nextFloat() * w * 1.4f + drift) % (w * 1.3f)) - w * 0.15f
            val cy = horizon * (0.12f + r.nextFloat() * 0.5f)
            val rw = w * (0.09f + r.nextFloat() * 0.12f)
            val col = lc(0xFF55607A.toInt(), lc(hz, Color.WHITE, 0.7f), L)
            for (k in 0 until 4) {
                val ox = (k - 1.5f) * rw * 0.55f
                val ry = rw * (0.22f + 0.1f * (k % 2))
                p.shader = RadialGradient(cx + ox, cy, rw * 0.8f, intArrayOf(T.withAlpha(col, 0.32f * (0.4f + L * 0.6f)), T.withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
                cv.save()
                cv.scale(1f, ry / (rw * 0.8f), cx + ox, cy)
                cv.drawCircle(cx + ox, cy, rw * 0.8f, p)
                cv.restore()
            }
        }
        p.shader = null
    }

    private fun ridges(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hz: Int,
        L: Float,
        seed: Int,
        layers: Int,
        far: Int,
        near: Int,
        amp: Float,
    ) {
        for (l in 0 until layers) {
            val d = l / (layers - 1f)
            val base = horizon - h * 0.04f + (h - horizon) * (0.1f + 0.78f * d * d)
            val a = h * amp * (0.35f + 0.65f * (1f - d * 0.4f)) * 0.5f
            val dayCol = lc(lc(hz, far, 0.6f), near, d * 0.95f)
            val nightCol = lc(0xFF1D2438.toInt(), 0xFF0B0F1A.toInt(), d)
            val col = lc(nightCol, lc(dayCol, hz, 0.12f * (1f - d)), L)
            val path = Path()
            path.moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w + 8) {
                val y = base - a * fbm(x / (w * (0.26f - 0.03f * l)) + l * 9.3f, seed + l * 31)
                path.lineTo(x, y)
                x += 8f
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            p.shader = LinearGradient(0f, base - a, 0f, h.toFloat(), col, lc(col, Color.BLACK, 0.18f), Shader.TileMode.CLAMP)
            cv.drawPath(path, p)
            p.shader = null
            // atmospheric haze between layers
            p.shader = LinearGradient(0f, base - a, 0f, base + a * 0.9f, T.withAlpha(hz, 0.30f * (1f - d) * (0.3f + L * 0.7f)), T.withAlpha(hz, 0f), Shader.TileMode.CLAMP)
            cv.drawPath(path, p)
            p.shader = null
        }
    }

    private fun pines(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        L: Float,
        seed: Int,
    ) {
        val r = java.util.Random(seed.toLong() + 21)
        p.color = lc(0xFF0A0F14.toInt(), 0xFF1B2A23.toInt(), L)
        for (i in 0 until 38) {
            val x = r.nextFloat() * w
            val th = h * (0.05f + r.nextFloat() * 0.07f)
            val y = h * (0.9f + r.nextFloat() * 0.1f)
            val tw = th * 0.34f
            val pa = Path()
            pa.moveTo(x, y - th)
            pa.lineTo(x + tw, y)
            pa.lineTo(x - tw, y)
            pa.close()
            cv.drawPath(pa, p)
        }
    }

    private fun dunes(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hz: Int,
        L: Float,
        seed: Int,
    ) {
        for (l in 0 until 4) {
            val d = l / 3f
            val base = horizon + (h - horizon) * (0.05f + 0.8f * d * d)
            val dayCol = lc(lc(hz, 0xFFE0B98F.toInt(), 0.6f), 0xFF9C6F4E.toInt(), d)
            val col = lc(lc(0xFF1F2236.toInt(), 0xFF0E0F18.toInt(), d), dayCol, L)
            val path = Path()
            path.moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w + 6) {
                val y = base - h * 0.07f * (0.5f + 0.5f * sin(x / w * (3.2f + l) * PI.toFloat() + l * 1.7f + seed * 0.1f)) - h * 0.02f * noise(x / (w * 0.2f), seed + l)
                path.lineTo(x, y)
                x += 6f
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            p.color = col
            cv.drawPath(path, p)
        }
    }

    private fun ocean(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hz: Int,
        top: Int,
        hour: Float,
        L: Float,
    ) {
        p.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), lc(hz, 0xFF6C8FA0.toInt(), 0.35f), lc(top, 0xFF1F3B4B.toInt(), 0.6f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val day = hour in 5.8f..18.4f
        val t = (if (day) (hour - 5.8f) / 12.6f else ((hour - 18.4f + 24f) % 24f) / 11.4f).coerceIn(0f, 1f)
        val sx = w * (0.1f + 0.8f * t)
        for (i in 0 until 26) {
            val y = horizon + (h - horizon) * (i / 26f) * (i / 26f) + 4
            val half = w * (0.012f + 0.0045f * i) * (0.5f + 0.5f * noise(i * 1.7f, 3))
            p.color = if (day) 0xFFFFF0CC.toInt() else 0xFFE3EAFF.toInt()
            p.alpha = (110 * (1f - i / 28f) * (0.4f + 0.6f * (if (day) L else 0.5f))).toInt()
            cv.drawRoundRect(sx - half, y, sx + half, y + 2.4f, 2f, 2f, p)
        }
        p.alpha = 255
    }

    private fun waves(
        cv: Canvas,
        p: Paint,
        w: Int,
        h: Int,
        horizon: Float,
        hz: Int,
        L: Float,
    ) {
        for (l in 0 until 5) {
            val d = l / 4f
            val base = horizon * 0.8f + (h - horizon * 0.8f) * (0.1f + 0.85f * d)
            val dayCol = lc(lc(hz, 0xFFB8C7C4.toInt(), 0.5f), 0xFF6E8E89.toInt(), d)
            val col = lc(lc(0xFF222A3E.toInt(), 0xFF0F1420.toInt(), d), dayCol, L)
            val path = Path()
            path.moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w + 6) {
                path.lineTo(x, base - h * 0.035f * sin(x / w * 2f * PI.toFloat() * (1.2f + l * 0.35f) + l * 2.1f))
                x += 6f
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            p.color = col
            cv.drawPath(path, p)
        }
    }
}

object Wallpaper {
    private fun dims(c: Context): IntArray {
        val dm = Resources.getSystem().displayMetrics
        return intArrayOf(max(dm.widthPixels, dm.heightPixels), min(dm.widthPixels, dm.heightPixels))
    }

    fun enabled() = Store.b("wp_sys", true)

    fun scene() = Store.s("wp_scene", "sangam")

    fun opts() = WOpts(Store.b("wp_grain", true), Store.b("wp_comfort", true), Store.b("wp_season", true), Store.i("wp_dim", 6) / 100f)

    fun hourNow(): Float {
        val n = LocalTime.now(Repo.zone())
        return n.hour + n.minute / 60f
    }

    fun photos(c: Context): List<File> =
        File(c.filesDir, "wp").listFiles()?.filter {
            it.extension == "jpg"
        }?.sortedBy { it.name } ?: emptyList()

    fun scheduleNext(c: Context) {
        if (!enabled()) {
            Sched.cancelWallpaper(c)
            Store.put("wp_next", 0L)
            return
        }
        val at = TimeProvider.nextHalfHour(System.currentTimeMillis())
        Store.put("wp_next", at)
        Sched.wallpaper(c, at)
    }

    fun liveActive(c: Context): Boolean =
        try {
            WallpaperManager.getInstance(c).getWallpaperInfo()?.packageName == c.packageName
        } catch (_: Exception) {
            false
        }

    fun launchLiveWallpaper(c: Context): Boolean {
        return try {
            val intent =
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        android.content.ComponentName(c, MissionWallpaperService::class.java),
                    )
                }
            c.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                c.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun build(
        c: Context,
        w: Int,
        h: Int,
        forLock: Boolean = false,
    ): Bitmap {
        val now = TimeProvider.today()
        val sc = scene()
        val o = opts()
        val pw = if (Store.b("wp_parallax", true)) (w * 1.2f).toInt() else w
        val bm: Bitmap =
            if (sc == "photo") {
                photoBitmap(c, pw, h, o) ?: Scenes.render(pw, h, hourNow(), "hills", now.monthValue, o, now.dayOfYear, now)
            } else {
                Scenes.render(
                    pw, h,
                    if (Store.b(
                            "wp_auto",
                            true,
                        )
                    ) {
                        hourNow()
                    } else {
                        Store.i("wp_fixed_hour", 12).toFloat()
                    },
                    sc, now.monthValue, o, now.dayOfYear, now, Weather.forRendering(),
                )
            }
        if (forLock && Store.b("wp_lock_text", true)) {
            val cv = Canvas(bm)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.typeface = T.font(2)
            p.textAlign = Paint.Align.CENTER
            val days = java.time.temporal.ChronoUnit.DAYS.between(now, Repo.examDate())
            p.textSize = h * 0.032f
            p.color = Color.WHITE
            p.alpha = 190
            cv.drawText("$days days to Prelims", pw / 2f, h * 0.93f, p)
        }
        return bm
    }

    private fun photoBitmap(
        c: Context,
        w: Int,
        h: Int,
        o: WOpts,
    ): Bitmap? {
        val list = photos(c)
        if (list.isEmpty()) return null
        val date = TimeProvider.today()
        val idx =
            when (Store.s("wp_photo_mode", "daily")) {
                "hourly" -> (date.dayOfYear * 24 + LocalTime.now(Repo.zone()).hour) % list.size
                "single" -> 0
                else -> date.dayOfYear % list.size
            }
        val src =
            try {
                BitmapFactory.decodeFile(list[idx].path)
            } catch (e: Throwable) {
                null
            } ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * s
        val dh = src.height * s
        var draw = src
        val blur = Store.i("wp_blur", 0)
        if (blur > 0) {
            val k = 1 + blur / 6
            val small = Bitmap.createScaledBitmap(src, max(8, src.width / (4 * k)), max(8, src.height / (4 * k)), true)
            draw = small
        }
        cv.drawBitmap(draw, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p)
        // eye comfort: warm + slightly darker at night
        val night = 1f - Scenes.daylight(hourNow())
        if (o.comfort && night > 0.05f) {
            p.color = 0xFFFF9A4D.toInt()
            p.alpha = (46 * night).toInt()
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.alpha = 255
        }
        val vg =
            RadialGradient(
                w / 2f,
                h / 2f,
                max(w, h) * 0.75f,
                intArrayOf(0x00000000, 0x55000000),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        p.shader = vg
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        Scenes.finish(out, WOpts(o.grain, false, false, max(o.dim, 0.18f)))
        return out
    }

    fun apply(
        c: Context,
        force: Boolean = false,
        refreshWeather: Boolean = true,
    ): Boolean {
        try {
            Backdrop.ctx = c.applicationContext
            if (liveActive(c)) {
                Store.put("wp_last", System.currentTimeMillis())
                if (refreshWeather) Weather.refreshIfNeeded(c)
                scheduleNext(c)
                return true
            }
            if (!force) {
                if (!enabled()) return false
                if (Store.b("wp_battery_pause", true) && (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode) return false
            }
            if (refreshWeather) Weather.refreshIfNeeded(c)
            val d = dims(c)
            val wm = WallpaperManager.getInstance(c)
            val tgt = Store.s("wp_target", "both")
            if (tgt == "both" || tgt == "home") {
                val b = build(c, d[0], d[1])
                wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM)
                b.recycle()
            }
            if (tgt == "both" || tgt == "lock") {
                val b = build(c, d[0], d[1], true)
                wm.setBitmap(b, null, true, WallpaperManager.FLAG_LOCK)
                b.recycle()
            }
            Store.put("wp_last", System.currentTimeMillis())
            scheduleNext(c)
            return true
        } catch (e: Throwable) {
            Log.e("wallpaper", e)
            return false
        }
    }

    /** Static fallback: runs at every IST half-hour boundary. */
    fun hourly(c: Context) {
        if (!enabled() || Store.s("wp_scene") == "photo" && photos(c).isEmpty()) {
            if (!enabled()) return
        }
        apply(c)
    }

    fun preview(
        c: Context,
        w: Int,
        h: Int,
    ): Bitmap = build(c, w, h)

    fun addPhoto(
        c: Context,
        uri: Uri,
    ): Boolean {
        return try {
            val dir = File(c.filesDir, "wp").apply { mkdirs() }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1
            while (o.outWidth / ss > 3000) ss *= 2
            val bm =
                c.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss })
                } ?: return false
            File(dir, "p${System.currentTimeMillis()}.jpg").outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bm.recycle()
            true
        } catch (e: Throwable) {
            Log.e("wp-add", e)
            false
        }
    }

    fun clearPhotos(c: Context) {
        val directory = File(c.filesDir, "wp")
        if (directory.exists()) {
            val files = directory.listFiles() ?: throw IllegalStateException("Could not list wallpaper photos")
            val failures = files.filter { it.exists() && !it.delete() }
            check(failures.isEmpty()) { "Could not delete ${failures.size} wallpaper photos" }
            check(directory.delete()) { "Could not remove wallpaper photo folder" }
        }
    }
}
