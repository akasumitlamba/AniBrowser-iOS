/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.mozilla.reference.browser.settings.SettingsActivity
import java.io.File

/**
 * Hosts AniHome only while it is visible. Destroying the system WebView when a site opens avoids
 * keeping Chromium alive beside GeckoView during normal browsing.
 */
class AnimeHubView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var internalWebView: WebView? = null
    var onOpenUrl: ((String) -> Unit)? = null

    inner class AniHomeBridge {
        @JavascriptInterface fun refreshLogos() { AniHomeManager.refreshLogos(context) }
        @JavascriptInterface fun searchUrl(query: String) = BrowserPreferences.searchUrl(context, query)
        @JavascriptInterface fun openUrl(url: String) = post {
            if (url == "anibrowser://settings") openSettings() else onOpenUrl?.invoke(url)
        }

        @JavascriptInterface fun openSettings() = post {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        @JavascriptInterface fun toggleAdShield(): Boolean {
            val prefs = context.getSharedPreferences("anibrowser_prefs", Context.MODE_PRIVATE)
            return (!prefs.getBoolean("adshield_enabled", true)).also {
                prefs.edit().putBoolean("adshield_enabled", it).apply()
            }
        }

        @JavascriptInterface fun isAdShieldActive() = context
            .getSharedPreferences("anibrowser_prefs", Context.MODE_PRIVATE)
            .getBoolean("adshield_enabled", true)

        @JavascriptInterface fun addTile(url: String, title: String) = post {
            AniHomeManager.addManualTile(context, url, title)
        }

        @JavascriptInterface fun updateTile(id: String, title: String) = post {
            AniHomeManager.updateTileTitle(context, id, title)
        }

        @JavascriptInterface fun deleteTile(id: String) = post {
            AniHomeManager.removeTile(context, id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        internalWebView?.let { return it }
        return WebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#070A12"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addJavascriptInterface(AniHomeBridge(), "AniHomeBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        onOpenUrl?.invoke(url)
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.host != LOCAL_HOST) return null
                    return when {
                        uri.path == "/assets/logo.png" -> runCatching {
                            WebResourceResponse("image/png", null, context.assets.open("anibrowser_logo_160.png"))
                        }.getOrNull()
                        uri.path?.startsWith("/icon/") == true -> iconResponse(uri)
                        else -> null
                    }
                }
            }
            this@AnimeHubView.addView(this)
            internalWebView = this
        }
    }

    private fun iconResponse(uri: Uri): WebResourceResponse? {
        val name = uri.lastPathSegment ?: return null
        val id = name.removeSuffix(".png")
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
        val file = File(context.filesDir, "anihome_icons/$id.png")
        if (!file.isFile) return null
        return runCatching { WebResourceResponse("image/png", null, file.inputStream()) }.getOrNull()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AniHomeManager.onTilesChanged = { post { if (visibility == VISIBLE) loadContent() } }
    }

    override fun onDetachedFromWindow() {
        AniHomeManager.onTilesChanged = null
        releaseContent()
        super.onDetachedFromWindow()
    }

    fun loadContent() {
        ensureWebView().loadDataWithBaseURL("https://$LOCAL_HOST/", AnimeHub.getHtml(context), "text/html", "UTF-8", null)
    }

    fun releaseContent() {
        internalWebView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("AniHomeBridge")
            it.webViewClient = WebViewClient()
            removeView(it)
            it.destroy()
        }
        internalWebView = null
    }

    private companion object { const val LOCAL_HOST = "anibrowser.local" }
}
