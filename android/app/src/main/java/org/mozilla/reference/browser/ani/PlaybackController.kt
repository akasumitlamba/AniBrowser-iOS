/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.WeakHashMap
import mozilla.components.concept.engine.EngineSession

object PlaybackController {
    var isReady = false
        private set
    private val rates = listOf(1.0, 1.25, 1.5, 1.75, 2.0)
    private val ports = mutableSetOf<WebExtension.Port>()
    private val startupLoads = WeakHashMap<EngineSession, String>()
    fun loadWhenReady(session: EngineSession, url: String) {
        startupLoads[session] = url
    }
    private fun prefs(context: Context) = context.getSharedPreferences("anibrowser", Context.MODE_PRIVATE)
    private fun speed(context: Context) = prefs(context).getFloat("speed", 1f).toDouble()

    fun install(context: Context, runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/anibrowser/", "playback@anibrowser.local"
        ).accept({ extension ->
            extension?.setMessageDelegate(object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    ports.add(port)
                    isReady = true
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val payload = message as? JSONObject ?: return
                            if (payload.optString("type") == "navigation") {
                                val id = payload.optString("id")
                                fun reply(allowed: Boolean) {
                                    if (port in ports) port.postMessage(JSONObject()
                                        .put("type", "navigationResult").put("id", id).put("allowed", allowed))
                                }
                                NavigationGuard.siteChoice(payload.optString("destination")) { reply(it) }
                            } else {
                                port.postMessage(JSONObject().put("speed", speed(context)))
                            }
                        }
                        override fun onDisconnect(port: WebExtension.Port) {
                            ports.remove(port)
                            isReady = ports.isNotEmpty()
                        }
                    })
                    port.postMessage(JSONObject().put("speed", speed(context)))
                    val queued = startupLoads.entries.map { it.key to it.value }
                    startupLoads.clear()
                    queued.forEach { (session, url) -> session.loadUrl(url) }
                }
            }, "anibrowser")
        }, { android.util.Log.e("AniBrowser", "Built-in playback controller failed to install") })
    }

    fun setSpeed(context: Context, value: Double) {
        if (!rates.contains(value)) return
        prefs(context).edit().putFloat("speed", value.toFloat()).apply()
        ports.toList().forEach { it.postMessage(JSONObject().put("speed", value)) }
    }

    fun show(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Playback speed — saved until you change it")
            .setSingleChoiceItems(rates.map { "${it}×" }.toTypedArray(), rates.indexOf(speed(context))) { dialog, index ->
                val value = rates[index]
                prefs(context).edit().putFloat("speed", value.toFloat()).apply()
                ports.toList().forEach { it.postMessage(JSONObject().put("speed", value)) }
                dialog.dismiss()
            }.setNegativeButton("Close", null).show()
    }
}
