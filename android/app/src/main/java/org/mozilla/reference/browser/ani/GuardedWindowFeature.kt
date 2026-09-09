/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.window.WindowRequest
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.kotlinx.coroutines.flow.filterChanged

class GuardedWindowFeature(private val store: BrowserStore, private val tabsUseCases: TabsUseCases) : LifecycleAwareFeature {
    private var scope: CoroutineScope? = null
    override fun start() {
        scope = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.mapNotNull { it.tabs }.filterChanged { it.content.windowRequest }.collect { tab ->
                val request = tab.content.windowRequest ?: return@collect
                store.dispatch(ContentAction.ConsumeWindowRequestAction(tab.id))
                if (request.type == WindowRequest.Type.OPEN) {
                    NavigationGuard.allowWindow(tab.content.url) {
                        // The opener may have navigated or closed while the prompt was visible.
                        val current = store.state.tabs.find { it.id == tab.id }
                        if (current?.content?.url == tab.content.url) {
                            val engineSession = request.prepare()
                            tabsUseCases.addTab(selectTab = true, parentId = tab.id,
                                engineSession = engineSession, private = tab.content.private)
                            request.start()
                        }
                    }
                }
                // Websites cannot close the user's current tab without their action.
            }
        }
    }
    override fun stop() { scope?.cancel(); scope = null }
}
