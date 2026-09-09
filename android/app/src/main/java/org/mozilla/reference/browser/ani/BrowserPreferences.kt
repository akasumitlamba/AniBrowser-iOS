/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog

object BrowserPreferences {
    val names = arrayOf("DuckDuckGo", "Google", "Bing", "Brave Search", "Ecosia")
    private val urls = arrayOf(
        "https://duckduckgo.com/?q=",
        "https://www.google.com/search?q=",
        "https://www.bing.com/search?q=",
        "https://search.brave.com/search?q=",
        "https://www.ecosia.org/search?q=",
    )
    fun index(context: Context) = context.getSharedPreferences("anibrowser", 0).getInt("search_engine", 0).coerceIn(names.indices)
    fun searchUrl(context: Context, query: String) = urls[index(context)] + Uri.encode(query)
    fun chooseSearch(context: Context, changed: () -> Unit = {}) {
        AlertDialog.Builder(context).setTitle("Default search engine")
            .setSingleChoiceItems(names, index(context)) { dialog, selected ->
                context.getSharedPreferences("anibrowser", 0).edit().putInt("search_engine", selected).apply()
                changed()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }
    fun about(context: Context) {
        AlertDialog.Builder(context).setTitle("About AniBrowser")
            .setMessage("AniBrowser\n\nA personal browser for watching and browsing comfortably.\n\nPersistent playback speed • Per-site navigation controls • Fullscreen website shortcuts\n\nBuilt on Mozilla Reference Browser and GeckoView. Open-source notices remain available in Settings → About.\n\nLocal-network pairing is not available in this build.")
            .setPositiveButton("Close", null).show()
    }
}
