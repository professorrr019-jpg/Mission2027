package com.mission.launcher

import android.graphics.Bitmap

/**
 * Edge-aware unsharp mask on luminance only. Fine detail (difference to a 5-tap blur) is boosted with soft coring, so noise and flat
 * areas stay untouched, edges get no colour fringes and no hard halos (the boost is clamped). Photos look crisp, not artificial.
 */
object Sharpen {
    fun edges(src: Bitmap, amount: Float = 0.45f): Bitmap {
        return try {
            val w = src.width
            val h = src.height
            if (w < 64 || h < 64) return src
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val lum = IntArray(w * h)
            for (i in px.indices) {
                val c = px[i]
                lum[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            }
            // separable [1 4 6 4 1]/16 blur
            val tmp = IntArray(w * h)
            for (y in 0 until h) {
                val o = y * w
                for (x in 0 until w) {
                    val a = lum[o + (x - 2).coerceAtLeast(0)]
                    val b = lum[o + (x - 1).coerceAtLeast(0)]
                    val c = lum[o + x]
                    val d = lum[o + (x + 1).coerceAtMost(w - 1)]
                    val e = lum[o + (x + 2).coerceAtMost(w - 1)]
                    tmp[o + x] = (a + 4 * b + 6 * c + 4 * d + e) shr 4
                }
            }
            val core = 3
            val cap = 22
            for (y in 0 until h) {
                val y0 = (y - 2).coerceAtLeast(0) * w
                val y1 = (y - 1).coerceAtLeast(0) * w
                val y2 = y * w
                val y3 = (y + 1).coerceAtMost(h - 1) * w
                val y4 = (y + 2).coerceAtMost(h - 1) * w
                for (x in 0 until w) {
                    val blur = (tmp[y0 + x] + 4 * tmp[y1 + x] + 6 * tmp[y2 + x] + 4 * tmp[y3 + x] + tmp[y4 + x]) shr 4
                    val diff = lum[y2 + x] - blur
                    val mag = if (diff < 0) -diff else diff
                    if (mag <= core) continue
                    var add = ((mag - core) * amount).toInt().coerceAtMost(cap)
                    if (diff < 0) add = -add
                    val c = px[y2 + x]
                    val r = (((c shr 16) and 0xFF) + add).coerceIn(0, 255)
                    val g = (((c shr 8) and 0xFF) + add).coerceIn(0, 255)
                    val b = ((c and 0xFF) + add).coerceIn(0, 255)
                    px[y2 + x] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bm.setPixels(px, 0, w, 0, 0, w, h)
            bm
        } catch (e: Throwable) {
            Log.e("sharpen", e)
            src
        }
    }
}
