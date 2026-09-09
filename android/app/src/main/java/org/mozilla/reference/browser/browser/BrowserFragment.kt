/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.browser

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.awesomebar.AwesomeBar.Suggestion
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.flow
import mozilla.components.feature.awesomebar.AwesomeBarFeature
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.readerview.view.ReaderViewControlsBar
import mozilla.components.feature.syncedtabs.SyncedTabsStorageSuggestionProvider
import mozilla.components.feature.toolbar.WebExtensionToolbarFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ani.ShortcutOverlay
import org.mozilla.reference.browser.ani.SiteSettings
import org.mozilla.reference.browser.ani.PlaybackController
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.search.AwesomeBarWrapper
import org.mozilla.reference.browser.tabs.TabsTrayFragment

/** Fragment used for browsing the web within the main app. */
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {
    private val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewIntegration>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()

    private val awesomeBar: AwesomeBarWrapper
        get() = requireView().findViewById(R.id.awesomeBar)

    private val toolbar: BrowserToolbar
        get() = requireView().findViewById(R.id.toolbar)

    private val engineView: EngineView
        get() = requireView().findViewById<View>(R.id.engineView) as EngineView

    private val readerViewBar: ReaderViewControlsBar
        get() = requireView().findViewById(R.id.readerViewBar)

    private val readerViewAppearanceButton: FloatingActionButton
        get() = requireView().findViewById(R.id.readerViewAppearanceButton)

    private val animeHubView: org.mozilla.reference.browser.ani.AnimeHubView?
        get() = view?.findViewById(R.id.animeHubView)

    override val shouldUseComposeUI: Boolean get() = false

    private var shortcutOverlay: ShortcutOverlay? = null

    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        AwesomeBarFeature(awesomeBar, toolbar, engineView)
            .addSearchProvider(
                requireComponents.core.store,
                requireComponents.useCases.searchUseCases.defaultSearch,
                fetchClient = requireComponents.core.client,
                mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                engine = requireComponents.core.engine,
                limit = 5,
                filterExactMatch = true,
            )
            .addSessionProvider(
                resources,
                requireComponents.core.store,
                requireComponents.useCases.tabsUseCases.selectTab,
            )
            .addHistoryProvider(
                requireComponents.core.historyStorage,
                requireComponents.useCases.sessionUseCases.loadUrl,
            )
            .addClipboardProvider(requireContext(), requireComponents.useCases.sessionUseCases.loadUrl)

        awesomeBar.addProviders(
            SyncedTabsStorageSuggestionProvider(
                requireComponents.backgroundServices.syncedTabsStorage,
                requireComponents.useCases.tabsUseCases.addTab,
                requireComponents.core.icons,
            )
        )
        awesomeBar.setOnRemoveSuggestionButtonClicked {
            awesomeBar.addHiddenSuggestion(it)
            (it.suggestion as? Suggestion)?.let { s -> deleteHistorySuggestion(s) }
        }

        val tabCount = org.mozilla.reference.browser.ani.TabCountAction(::showTabs)
        toolbar.addBrowserAction(tabCount)
        viewLifecycleOwner.lifecycleScope.launch {
            requireComponents.core.store.flow().map { it.tabs.size }.distinctUntilChanged().collect { tabCount.update(it) }
        }

        thumbnailsFeature.set(
            feature =
                BrowserThumbnails(
                    requireContext(),
                    engineView,
                    requireComponents.core.store,
                ),
            owner = this,
            view = view,
        )

        readerViewFeature.set(
            feature =
                ReaderViewIntegration(
                    requireContext(),
                    requireComponents.core.engine,
                    requireComponents.core.store,
                    toolbar,
                    readerViewBar,
                    readerViewAppearanceButton,
                ),
            owner = this,
            view = view,
        )

        webExtToolbarFeature.set(
            feature =
                WebExtensionToolbarFeature(
                    toolbar,
                    requireContext().components.core.store,
                ),
            owner = this,
            view = view,
        )

        engineView.setDynamicToolbarMaxHeight(resources.getDimensionPixelSize(R.dimen.browser_toolbar_height))

        // Shortcut / WebsiteActivity mode — toolbar hidden, fullscreen, gesture overlay
        if (activity is org.mozilla.reference.browser.ani.WebsiteActivity) {
            toolbar.visibility = View.GONE
            engineView.setDynamicToolbarMaxHeight(0)

            // Remove coordinator behaviour so engine fills screen top-to-bottom
            val refresh = view.findViewById<View>(R.id.swipeRefresh)
            (refresh.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
                behavior = null
                topMargin = 0
                bottomMargin = 0
            }

            // Immersive mode — bars visible transiently on swipe from screen edge
            androidx.core.view.WindowInsetsControllerCompat(requireActivity().window, view).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }

            // Attach gesture-triggered overlay on the root FrameLayout of the activity
            val rootContainer = requireActivity().window.decorView
                .findViewById<FrameLayout>(android.R.id.content)
            if (rootContainer != null) {
                val ctx = requireContext()
                val prefs = ctx.getSharedPreferences("anibrowser", android.content.Context.MODE_PRIVATE)
                val currentUrl = requireComponents.core.store.state.tabs
                    .firstOrNull { it.id == sessionId }?.content?.url.orEmpty()

                shortcutOverlay = ShortcutOverlay(
                    context = ctx,
                    container = rootContainer,
                    onSpeedSelected = { speed ->
                        PlaybackController.setSpeed(ctx, speed)
                    },
                    onSeek = { seconds ->
                        // Execute seek as a javascript: URL — GeckoView runs it in the current page context.
                        val js = "void(document.querySelectorAll('video').forEach(function(v){v.currentTime=Math.max(0,v.currentTime+($seconds))}))"
                        requireComponents.core.store.state.tabs
                            .firstOrNull { it.id == sessionId }
                            ?.engineState?.engineSession
                            ?.loadUrl(
                                "javascript:$js",
                                flags = mozilla.components.concept.engine.EngineSession.LoadUrlFlags.select(
                                    mozilla.components.concept.engine.EngineSession.LoadUrlFlags.ALLOW_JAVASCRIPT_URL
                                )
                            )
                    },
                    onViewModeSelected = { mode ->
                        SiteSettings.apply(ctx,
                            requireComponents.core.store.state.tabs
                                .firstOrNull { it.id == sessionId }
                                ?.engineState?.engineSession ?: return@ShortcutOverlay,
                            currentUrl
                        )
                        prefs.edit().putInt("view:${SiteSettings.host(currentUrl)}", mode).apply()
                    },
                    getCurrentSpeed = { prefs.getFloat("speed", 1f).toDouble() },
                    getCurrentViewMode = { prefs.getInt("view:${SiteSettings.host(currentUrl)}", 0) },
                )
            }
        }
        run {
            if (activity is org.mozilla.reference.browser.ani.WebsiteActivity) {
                (animeHubView?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin = 0
            }
            animeHubView?.apply {
                onOpenUrl = { targetUrl ->
                    visibility = View.GONE
                    releaseContent()
                    val tabId = sessionId ?: requireComponents.core.store.state.selectedTabId
                    if (tabId != null) {
                        requireComponents.useCases.sessionUseCases.loadUrl.invoke(targetUrl, sessionId = tabId)
                    } else {
                        requireComponents.useCases.tabsUseCases.addTab(targetUrl, selectTab = true)
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                requireComponents.core.store
                    .flow()
                    .map { state ->
                        val tab = state.findTabOrCustomTab(sessionId ?: state.selectedTabId.orEmpty()) ?: state.selectedTab
                        tab?.content?.url
                    }
                    .distinctUntilChanged()
                    .collect { url ->
                        val isHome = url.isNullOrBlank() || url == "about:home" || url == "about:blank"
                        animeHubView?.visibility = if (isHome) View.VISIBLE else View.GONE
                        if (isHome) {
                            animeHubView?.loadContent()
                        } else {
                            animeHubView?.releaseContent()
                        }
                    }
            }
        }
    }

    private fun showTabs() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            replace(R.id.container, TabsTrayFragment())
            commit()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun deleteHistorySuggestion(suggestion: Suggestion) {
        lifecycleScope.launch(Dispatchers.IO) {
            suggestion.description?.let {
                requireComponents.core.historyStorage.deleteHistoryMetadataForUrl(it)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (animeHubView?.visibility == View.VISIBLE) {
            val tab = requireComponents.core.store.state.findTabOrCustomTab(sessionId ?: requireComponents.core.store.state.selectedTabId.orEmpty())
            val curUrl = tab?.content?.url
            if (curUrl != null && curUrl != "about:home" && curUrl != "about:blank" && curUrl.isNotBlank()) {
                animeHubView?.visibility = View.GONE
                return true
            }
        }
        return readerViewFeature.onBackPressed() || super.onBackPressed()
    }

    override fun onDestroyView() {
        shortcutOverlay = null
        animeHubView?.releaseContent()
        super.onDestroyView()
    }

    companion object {
        fun create(sessionId: String? = null) =
            BrowserFragment().apply {
                arguments =
                    Bundle().apply {
                        putSessionId(sessionId)
                    }
            }
    }
}
