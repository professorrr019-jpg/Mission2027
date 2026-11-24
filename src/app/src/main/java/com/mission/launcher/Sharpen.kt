package com.mission.launcher

import android.graphics.Bitmap

/**
 * Edge-aware sharpening: finds edges with a Laplacian (edge detection) and boosts only the pixels that sit on a real edge,
 * so skies and water stay smooth while ridges, buildings and ghats look crisp. Gives photos a "higher resolution" feel.
 */
object Sharpen {
    fun edges(src: Bitmap, amount: Float = 0.55f): Bitmap {
        return try {
            val w = src.width
            val h = src.height
            if (w < 64 || h < 64) return src
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val out = px.copyOf()
            val th = 7
            for (y in 1 until h - 1) {
                var i = y * w + 1
                for (x in 1 until w - 1) {
                    val c = px[i]
                    val u = px[i - w]
                    val d = px[i + w]
                    val l = px[i - 1]
                    val r = px[i + 1]
                    var res = c and 0xFF000000.toInt()
                    var sh = 0
                    while (sh <= 16) {
                        val cc = (c shr sh) and 0xFF
                        val lap = 4 * cc - ((u shr sh) and 0xFF) - ((d shr sh) and 0xFF) - ((l shr sh) and 0xFF) - ((r shr sh) and 0xFF)
                        val add = if (lap > th || lap < -th) (lap * amount).toInt() else 0
                        res = res or ((cc + add).coerceIn(0, 255) shl sh)
                        sh += 8
                    }
                    out[i] = res
                    i++
                }
            }
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            b.setPixels(out, 0, w, 0, 0, w, h)
            b
        } catch (e: Throwable) {
            Log.e("sharpen", e)
            src
        }
    }
}
