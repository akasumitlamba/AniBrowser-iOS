/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import mozilla.components.concept.engine.EngineSession
import org.mozilla.reference.browser.ext.components

object SiteSettings {
    fun host(url: String) = Uri.parse(url).host?.lowercase().orEmpty()
    private fun prefs(context: Context) = context.getSharedPreferences("ani_sites", Context.MODE_PRIVATE)
    fun redirects(context: Context, url: String): Boolean? {
        val key = "redirects:${host(url)}"
        return if (prefs(context).contains(key)) prefs(context).getBoolean(key, false) else null
    }
    fun choose(context: Context, url: String, result: (Boolean) -> Unit) {
        val domain = host(url)
        if (domain.isEmpty()) { result(false); return }
        var answered = false
        val dialog = AlertDialog.Builder(context).setTitle("Browsing rules for $domain")
            .setMessage("Choose once for this domain. You can change this in Site settings. Block stops automatic redirects and new tabs; ordinary links in this tab still work.")
            .setPositiveButton("Block") { _, _ ->
                answered = true; prefs(context).edit().putBoolean("redirects:$domain", false).apply(); result(false)
            }.setNegativeButton("Allow") { _, _ ->
                answered = true; prefs(context).edit().putBoolean("redirects:$domain", true).apply(); result(true)
            }.setOnDismissListener { if (!answered) result(false) }.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#3B82F6"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#10B981"))
    }
    fun apply(context: Context, session: EngineSession, url: String) {
        val mode = prefs(context).getInt("view:${host(url)}", 0)
        session.toggleDesktopMode(mode == 2, false)
        session.settings.userAgentString = if (mode == 1)
            "Mozilla/5.0 (X11; Linux x86_64; rv:157.0) Gecko/20100101 Firefox/157.0" else null
    }
    fun show(context: Context, url: String) {
        val domain = host(url)
        if (domain.isEmpty()) return
        val mode = prefs(context).getInt("view:$domain", 0)
        val labels = arrayOf("Mobile", "Desktop identity, mobile layout (try)", "Desktop")
        AlertDialog.Builder(context).setTitle("Site settings · $domain")
            .setItems(arrayOf("Redirects and new tabs: ${if (redirects(context, url) == true) "Allow" else "Block"}",
                "Viewing mode: ${labels[mode]}")) { _, which ->
                if (which == 0) choose(context, url) {}
                else AlertDialog.Builder(context).setTitle("Always open $domain using")
                    .setSingleChoiceItems(labels, mode) { dialog, selected ->
                        prefs(context).edit().putInt("view:$domain", selected).apply()
                        context.components.core.store.state.tabs.filter { host(it.content.url) == domain }.forEach {
                            it.engineState.engineSession?.let { session -> apply(context, session, it.content.url); session.reload() }
                        }
                        dialog.dismiss()
                    }.setNegativeButton("Close", null).show()
            }.setNegativeButton("Close", null).show()
    }
}
