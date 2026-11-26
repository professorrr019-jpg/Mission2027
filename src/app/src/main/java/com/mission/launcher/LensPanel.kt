package com.mission.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A real floating Lens result panel at the right corner. It does not depend on the tablet's freeform support, and the circled app is never
 * touched: the circled picture is uploaded to Google Lens from here and the result page opens in a small overlay window you can drag.
 * If the upload fails the caller falls back to the normal Lens app.
 */
object LensPanel {
    private const val TAG = "lens_panel"
    private val ui = Handler(Looper.getMainLooper())
    private var web: WebView? = null

    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun close(c: Context) {
        ui.post {
            try { web?.stopLoading(); web?.destroy() } catch (e: Exception) { Log.e("lens-panel", e) }
            web = null
            Overlay.remove(c, TAG)
        }
    }

    /** Uploads the image on a worker thread; opens the panel when Lens answered, otherwise calls [failed]. */
    fun open(c: Context, img: Uri, text: String, failed: () -> Unit) {
        if (!c.canOverlay()) {
            failed()
            return
        }
        Thread {
            val url = try { upload(c, img, text) } catch (e: Exception) { Log.e("lens-panel-upload", e); null }
            ui.post { if (url == null) failed() else show(c, url, failed) }
        }.start()
    }

    private fun upload(c: Context, img: Uri, text: String): String? {
        val bytes = c.contentResolver.openInputStream(img)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty() || bytes.size > 8_000_000) return null
        val b = "----mission" + System.currentTimeMillis()
        val cn = URL("https://lens.google.com/v3/upload?hl=en&re=df&st=" + System.currentTimeMillis() + "&ep=gsbubb").openConnection() as HttpURLConnection
        cn.requestMethod = "POST"
        cn.doOutput = true
        cn.instanceFollowRedirects = false
        cn.connectTimeout = 12000
        cn.readTimeout = 20000
        cn.setRequestProperty("User-Agent", UA)
        cn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        cn.outputStream.use { o ->
            o.write(("--$b\r\nContent-Disposition: form-data; name=\"encoded_image\"; filename=\"mission.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray())
            o.write(bytes)
            o.write("\r\n--$b--\r\n".toByteArray())
        }
        val code = cn.responseCode
        val loc = cn.getHeaderField("Location")
        cn.disconnect()
        if (code !in 300..399 || loc.isNullOrBlank() || !loc.startsWith("https://")) return null
        // the tag is added as the text part of the search ("Add to your search")
        return loc + (if (loc.contains("?")) "&" else "?") + "q=" + URLEncoder.encode(text, "UTF-8")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun show(c: Context, url: String, failed: () -> Unit) {
        try {
            val dm = c.resources.displayMetrics
            val w = (dm.widthPixels * Store.i("circle_w", 42).coerceIn(30, 70) / 100f).toInt()
            val h = (dm.heightPixels * Store.i("circle_h", 62).coerceIn(40, 90) / 100f).toInt()
            val wv =
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = UA
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    webChromeClient = WebChromeClient()
                    webViewClient =
                        object : WebViewClient() {
                            override fun onReceivedError(v: WebView, req: android.webkit.WebResourceRequest, err: android.webkit.WebResourceError) {
                                if (req.isForMainFrame) { close(c); failed() }
                            }
                        }
                }
            web = wv
            val box = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                background = T.round(T.cardSolid, 18, 1, T.withAlpha(T.text3, 0.35f))
                clipToOutline = true
                elevation = T.dpf(10f)
            }
            val bar = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(T.dp(12), T.dp(6), T.dp(8), T.dp(6))
                background = T.round(T.inset, 0)
            }
            bar.addView(c.text("Lens  |  " + Store.s("circle_tag_text").ifBlank { CircleService.TAG }, 12.5f, 3, T.text), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(c.pill("Open", null, false) {
                try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wv.url ?: url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { Log.e("lens-panel", e) }
            }, lpm(-2, -2, 0, 0, 6, 0))
            bar.addView(c.pill("Close", null, true, T.danger) { close(c) })
            box.addView(bar, LinearLayout.LayoutParams(-1, -2))
            box.addView(FrameLayout(c).apply { addView(wv, FrameLayout.LayoutParams(-1, -1)) }, LinearLayout.LayoutParams(-1, 0, 1f))
            val lp = Overlay.params(w, true)
            lp.height = h
            lp.x = dm.widthPixels - w - T.dp(10)
            lp.y =
                when (Store.s("circle_corner", "top")) {
                    "bottom" -> dm.heightPixels - h - T.dp(24)
                    "center" -> (dm.heightPixels - h) / 2
                    else -> T.dp(36)
                }
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            Overlay.draggable(c, bar, lp, TAG)
            Overlay.show(c, TAG, box, lp, 0)
            wv.loadUrl(url)
        } catch (e: Exception) {
            Log.e("lens-panel-show", e)
            failed()
        }
    }
}
