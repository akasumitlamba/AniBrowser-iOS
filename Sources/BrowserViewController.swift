/* SPDX-License-Identifier: MPL-2.0 */
import UIKit
import WebKit

final class BrowserTab {
    let webView: WKWebView
    var observers: [NSKeyValueObservation] = []
    var directURL: URL?
    var home = true
    var desktop = false
    init(_ webView: WKWebView) { self.webView = webView }
}

// WKUserContentController retains handlers; this proxy prevents a controller/webview cycle.
final class PlaybackHandler: NSObject, WKScriptMessageHandler {
    weak var owner: BrowserViewController?
    init(_ owner: BrowserViewController) { self.owner = owner }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        owner?.receivePlayback(message)
    }
}

final class BrowserViewController: UIViewController, WKNavigationDelegate, WKUIDelegate,
                                   UITextFieldDelegate, WKDownloadDelegate {
    private let defaults = UserDefaults.standard
    private var tabs: [BrowserTab] = []
    private var selected = 0
    private var current: BrowserTab? { tabs.indices.contains(selected) ? tabs[selected] : nil }
    private let content = UIView()
    private let home = UIScrollView()
    private let homeStack = UIStackView()
    private let chrome = UIStackView()
    private let address = UITextField()
    private let progress = UIProgressView(progressViewStyle: .bar)
    private let floatButton = UIButton(type: .system)
    private var backButton = UIButton(type: .system)
    private var forwardButton = UIButton(type: .system)
    private var tabsButton = UIButton(type: .system)
    private var speedButton = UIButton(type: .system)
    private var fullscreen = false
    private var blocker: WKContentRuleList?
    private var sites: [HomeSite] = []
    private var speed: Double = 1
    private var seekID = 0
    private var seek = 0
    private var seekIssuedAt = Date.distantPast
    private weak var seekOwner: WKWebView?
    private var downloadURLs: [ObjectIdentifier: URL] = [:]
    private let navy = UIColor(red: 15/255, green: 17/255, blue: 23/255, alpha: 1)
    private let orange = UIColor(red: 1, green: 107/255, blue: 53/255, alpha: 1)
    private var shield: Bool {
        get { defaults.object(forKey: "shield") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "shield") }
    }

    override var prefersStatusBarHidden: Bool { fullscreen }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = navy
        view.tintColor = orange
        let saved = defaults.double(forKey: "speed")
        speed = BrowserPolicy.speeds.contains(saved) ? saved : 1
        if let data = defaults.data(forKey: "sites"), let stored = try? JSONDecoder().decode([HomeSite].self, from: data) {
            sites = stored
        } else { sites = HomeSite.defaults }
        buildUI()
        renderHome()
        // Compile rules before creating the first webview, so its first request is covered.
        let rules = resource("blocklist", "json")
        WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "AniShield-v1", encodedContentRuleList: rules) { [weak self] list, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.blocker = list
                self.newTab()
                if error != nil { self.notice("Ad filtering unavailable", "The popup guard is still available. Content rules could not be loaded.") }
            }
        }
    }

    private func resource(_ name: String, _ ext: String) -> String {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            preconditionFailure("Missing bundled resource: \(name).\(ext)")
        }
        return text
    }

    private func button(_ symbol: String, _ label: String, _ action: @escaping () -> Void) -> UIButton {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: symbol), for: .normal)
        button.accessibilityLabel = label
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        button.widthAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        return button
    }

    private func buildUI() {
        let layout = UIStackView(arrangedSubviews: [content, progress, chrome])
        layout.axis = .vertical
        layout.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(layout)
        NSLayoutConstraint.activate([
            layout.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            layout.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            layout.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            layout.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])
        chrome.axis = .vertical
        chrome.spacing = 4
        chrome.isLayoutMarginsRelativeArrangement = true
        chrome.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 6, leading: 8, bottom: 4, trailing: 8)
        address.placeholder = "Search or enter address"
        address.borderStyle = .roundedRect
        address.backgroundColor = UIColor(red: 30/255, green: 33/255, blue: 48/255, alpha: 1)
        address.keyboardType = .webSearch
        address.returnKeyType = .go
        address.autocorrectionType = .no
        address.autocapitalizationType = .none
        address.clearButtonMode = .whileEditing
        address.delegate = self
        address.accessibilityLabel = "Search or website address"
        address.heightAnchor.constraint(equalToConstant: 44).isActive = true
        chrome.addArrangedSubview(address)
        backButton = button("chevron.left", "Back") { [weak self] in self?.current?.webView.goBack() }
        forwardButton = button("chevron.right", "Forward") { [weak self] in self?.current?.webView.goForward() }
        tabsButton = button("square.on.square", "Tabs") { [weak self] in self?.showTabs() }
        speedButton = button("speedometer", "Playback controls") { [weak self] in self?.showPlayback() }
        let controls = UIStackView(arrangedSubviews: [
            button("house", "AniHome") { [weak self] in self?.showHome() }, backButton, forwardButton,
            button("arrow.clockwise", "Reload") { [weak self] in self?.current?.webView.reload() },
            speedButton, tabsButton, button("ellipsis", "Browser menu") { [weak self] in self?.showMenu() }
        ])
        controls.distribution = .fillEqually
        chrome.addArrangedSubview(controls)
        homeStack.axis = .vertical
        homeStack.spacing = 16
        homeStack.translatesAutoresizingMaskIntoConstraints = false
        home.addSubview(homeStack)
        NSLayoutConstraint.activate([
            homeStack.topAnchor.constraint(equalTo: home.contentLayoutGuide.topAnchor, constant: 28),
            homeStack.bottomAnchor.constraint(equalTo: home.contentLayoutGuide.bottomAnchor, constant: -24),
            homeStack.leadingAnchor.constraint(equalTo: home.contentLayoutGuide.leadingAnchor, constant: 20),
            homeStack.trailingAnchor.constraint(equalTo: home.contentLayoutGuide.trailingAnchor, constant: -20),
            homeStack.widthAnchor.constraint(equalTo: home.frameLayoutGuide.widthAnchor, constant: -40)
        ])
        floatButton.setImage(UIImage(systemName: "arrow.down.right.and.arrow.up.left"), for: .normal)
        floatButton.accessibilityLabel = "Exit fullscreen"
        floatButton.backgroundColor = navy.withAlphaComponent(0.85)
        floatButton.layer.cornerRadius = 22
        floatButton.translatesAutoresizingMaskIntoConstraints = false
        floatButton.addAction(UIAction { [weak self] _ in self?.setFullscreen(false) }, for: .touchUpInside)
        view.addSubview(floatButton)
        NSLayoutConstraint.activate([
            floatButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            floatButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            floatButton.widthAnchor.constraint(equalToConstant: 44), floatButton.heightAnchor.constraint(equalToConstant: 44)
        ])
        floatButton.isHidden = true
    }

    private func renderHome() {
        homeStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let heading = UILabel()
        heading.text = "AniHome"
        heading.font = .systemFont(ofSize: 34, weight: .bold)
        homeStack.addArrangedSubview(heading)
        let subtitle = UILabel()
        subtitle.text = "Your sites. Your pace."
        subtitle.textColor = .secondaryLabel
        homeStack.addArrangedSubview(subtitle)
        for rowStart in stride(from: 0, to: sites.count, by: 2) {
            let row = UIStackView()
            row.spacing = 14
            row.distribution = .fillEqually
            for index in rowStart..<min(rowStart + 2, sites.count) {
                let site = sites[index]
                let tile = UIButton(type: .system)
                var config = UIButton.Configuration.filled()
                config.title = site.title
                config.image = UIImage(systemName: "play.rectangle.fill")
                config.imagePlacement = .top
                config.imagePadding = 16
                config.baseForegroundColor = .white
                config.baseBackgroundColor = UIColor(red: 30/255, green: 33/255, blue: 48/255, alpha: 1)
                config.cornerStyle = .large
                tile.configuration = config
                tile.heightAnchor.constraint(equalToConstant: 130).isActive = true
                tile.addAction(UIAction { [weak self] _ in
                    if let url = BrowserPolicy.webURL(site.url) { self?.navigate(url) }
                }, for: .touchUpInside)
                tile.menu = UIMenu(children: [UIAction(title: "Remove from AniHome", attributes: .destructive) { [weak self] _ in
                    self?.sites.removeAll { $0.url == site.url }
                    self?.saveSites()
                }])
                row.addArrangedSubview(tile)
            }
            if row.arrangedSubviews.count == 1 { row.addArrangedSubview(UIView()) }
            homeStack.addArrangedSubview(row)
        }
        let add = UIButton(type: .system)
        add.setTitle("+ Add website", for: .normal)
        add.heightAnchor.constraint(equalToConstant: 48).isActive = true
        add.addAction(UIAction { [weak self] _ in self?.addSite() }, for: .touchUpInside)
        homeStack.addArrangedSubview(add)
    }

    private func saveSites() {
        defaults.set(try? JSONEncoder().encode(sites), forKey: "sites")
        renderHome()
    }

    private func newTab() {
        guard tabs.count < 8 else { notice("Tab limit", "Close a tab before opening another. Up to eight tabs are supported."); return }
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.allowsPictureInPictureMediaPlayback = true
        let controller = config.userContentController
        controller.add(PlaybackHandler(self), contentWorld: .defaultClient, name: "aniPlayback")
        controller.addUserScript(WKUserScript(source: resource("bridge", "js") + "\n" + resource("playback", "js"),
                                             injectionTime: .atDocumentStart, forMainFrameOnly: false, in: .defaultClient))
        if shield, let blocker = blocker { controller.add(blocker) }
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.uiDelegate = self
        web.allowsBackForwardNavigationGestures = true
        let tab = BrowserTab(web)
        tab.observers = [web.observe(\.estimatedProgress, options: [.new]) { [weak self] _, _ in self?.refreshChrome() },
                         web.observe(\.url, options: [.new]) { [weak self] _, _ in self?.refreshChrome() },
                         web.observe(\.canGoBack, options: [.new]) { [weak self] _, _ in self?.refreshChrome() },
                         web.observe(\.canGoForward, options: [.new]) { [weak self] _, _ in self?.refreshChrome() }]
        pauseCurrent()
        tabs.append(tab)
        selected = tabs.count - 1
        mountCurrent()
    }

    private func pauseCurrent() { current?.webView.pauseAllMediaPlayback(completionHandler: nil) }

    private func mountCurrent() {
        content.subviews.forEach { $0.removeFromSuperview() }
        guard let tab = current else { return }
        let child = tab.home ? home : tab.webView
        child.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(child)
        NSLayoutConstraint.activate([
            child.topAnchor.constraint(equalTo: content.topAnchor), child.bottomAnchor.constraint(equalTo: content.bottomAnchor),
            child.leadingAnchor.constraint(equalTo: content.leadingAnchor), child.trailingAnchor.constraint(equalTo: content.trailingAnchor)
        ])
        refreshChrome()
    }

    private func showHome() {
        pauseCurrent()
        current?.home = true
        setFullscreen(false)
        mountCurrent()
    }

    private func navigate(_ url: URL) {
        guard let tab = current else { return }
        address.resignFirstResponder()
        tab.home = false
        tab.directURL = url
        mountCurrent()
        tab.webView.load(URLRequest(url: url))
    }

    private func refreshChrome() {
        guard let tab = current else { return }
        if !address.isFirstResponder { address.text = tab.home ? "" : tab.webView.url?.absoluteString }
        backButton.isEnabled = !tab.home && tab.webView.canGoBack
        forwardButton.isEnabled = !tab.home && tab.webView.canGoForward
        progress.progress = Float(tab.webView.estimatedProgress)
        progress.isHidden = tab.home || progress.progress >= 1 || fullscreen
        tabsButton.accessibilityLabel = "Tabs, \(tabs.count) open"
        speedButton.accessibilityLabel = "Playback speed \(speed) times"
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if let url = BrowserPolicy.destination(textField.text ?? "") { navigate(url) }
        return true
    }

    private func sheet(_ title: String) -> UIAlertController {
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        alert.popoverPresentationController?.sourceView = chrome
        alert.popoverPresentationController?.sourceRect = chrome.bounds
        return alert
    }

    private func show(_ alert: UIAlertController) {
        guard presentedViewController == nil else { return }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        present(alert, animated: true)
    }

    private func afterMenuDismiss(_ action: @escaping () -> Void) {
        if let menu = presentedViewController {
            menu.dismiss(animated: true, completion: action)
        } else { action() }
    }

    private func showTabs() {
        let alert = sheet("Tabs · \(tabs.count)/8")
        for (index, tab) in tabs.enumerated() {
            let title = tab.home ? "AniHome" : (tab.webView.title ?? tab.webView.url?.host ?? "Website")
            alert.addAction(UIAlertAction(title: (index == selected ? "✓ " : "") + title, style: .default) { [weak self] _ in
                self?.pauseCurrent(); self?.selected = index; self?.mountCurrent()
            })
        }
        if current?.home == true, current?.webView.url != nil {
            alert.addAction(UIAlertAction(title: "Return to current website", style: .default) { [weak self] _ in
                self?.current?.home = false; self?.mountCurrent()
            })
        }
        let addTab = UIAlertAction(title: "New tab", style: .default) { [weak self] _ in self?.newTab() }
        addTab.isEnabled = tabs.count < 8
        alert.addAction(addTab)
        alert.addAction(UIAlertAction(title: "Close current tab", style: .destructive) { [weak self] _ in
            guard let self = self, let tab = self.current else { return }
            self.pauseCurrent()
            tab.webView.stopLoading()
            tab.webView.configuration.userContentController.removeScriptMessageHandler(forName: "aniPlayback", contentWorld: .defaultClient)
            self.tabs.remove(at: self.selected)
            self.selected = max(0, self.tabs.count - 1)
            if self.tabs.isEmpty { self.newTab() } else { self.mountCurrent() }
        })
        show(alert)
    }

    private func showPlayback() {
        let alert = sheet("Playback · \(speed)×")
        for value in BrowserPolicy.speeds {
            alert.addAction(UIAlertAction(title: (value == speed ? "✓ " : "") + "\(value)×", style: .default) { [weak self] _ in
                self?.speed = value; self?.defaults.set(value, forKey: "speed"); self?.refreshChrome()
            })
        }
        for delta in [-10, 10] {
            alert.addAction(UIAlertAction(title: delta < 0 ? "Back 10 seconds" : "Forward 10 seconds", style: .default) { [weak self] _ in
                self?.seek = delta; self?.seekID += 1
                self?.seekIssuedAt = Date()
                self?.seekOwner = self?.current?.webView
            })
        }
        show(alert)
    }

    func receivePlayback(_ message: WKScriptMessage) {
        guard message.body as? String == "state", let web = message.webView else { return }
        let active = current?.webView === web && current?.home == false && seekOwner === web && Date().timeIntervalSince(seekIssuedAt) < 2
        let data: [String: Any] = ["speed": speed, "seekID": seekID, "seek": active ? seek : 0]
        guard let json = try? JSONSerialization.data(withJSONObject: data), let text = String(data: json, encoding: .utf8) else { return }
        web.evaluateJavaScript("globalThis.aniReceive(\(text))", in: message.frameInfo, in: .defaultClient) { _ in }
    }

    private func showMenu() {
        let alert = sheet("AniBrowser")
        alert.addAction(UIAlertAction(title: "Add to AniHome", style: .default) { [weak self] _ in
            self?.afterMenuDismiss { [weak self] in self?.addSite() }
        })
        alert.addAction(UIAlertAction(title: "Fullscreen", style: .default) { [weak self] _ in self?.setFullscreen(true) })
        alert.addAction(UIAlertAction(title: "Ad filter: \(shield && blocker != nil ? "on" : "off") · toggle", style: .default) { [weak self] _ in
            guard let self = self else { return }
            self.shield.toggle()
            for tab in self.tabs {
                tab.webView.configuration.userContentController.removeAllContentRuleLists()
                if self.shield, let blocker = self.blocker { tab.webView.configuration.userContentController.add(blocker) }
            }
            self.current?.webView.reload()
        })
        alert.addAction(UIAlertAction(title: "Switch mobile / desktop layout", style: .default) { [weak self] _ in
            guard let tab = self?.current else { return }
            tab.desktop.toggle()
            tab.webView.reload()
        })
        alert.addAction(UIAlertAction(title: "Reset redirect permissions", style: .default) { [weak self] _ in
            self?.defaults.removeObject(forKey: "redirects")
        })
        alert.addAction(UIAlertAction(title: "Share website", style: .default) { [weak self] _ in
            guard let self = self, let url = self.current?.webView.url else { return }
            self.afterMenuDismiss { [weak self] in self?.share(url) }
        })
        show(alert)
    }

    private func setFullscreen(_ enabled: Bool) {
        fullscreen = enabled
        chrome.isHidden = enabled
        floatButton.isHidden = !enabled
        setNeedsStatusBarAppearanceUpdate()
        refreshChrome()
    }

    private func addSite() {
        guard presentedViewController == nil else { return }
        let alert = UIAlertController(title: "Add website", message: "Save a website to AniHome. Hold a tile to remove it.", preferredStyle: .alert)
        alert.addTextField { $0.placeholder = "Name"; $0.text = self.current?.home == false ? self.current?.webView.title : nil }
        alert.addTextField { $0.placeholder = "https://example.com"; $0.keyboardType = .URL; $0.autocapitalizationType = .none; $0.text = self.current?.home == false ? self.current?.webView.url?.absoluteString : nil }
        alert.addAction(UIAlertAction(title: "Save", style: .default) { [weak self, weak alert] _ in
            guard let self = self, let url = BrowserPolicy.webURL(alert?.textFields?[1].text ?? "") else { return }
            let name = alert?.textFields?[0].text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            self.sites.removeAll { $0.url == url.absoluteString }
            self.sites.append(HomeSite(title: name.isEmpty ? (url.host ?? "Website") : name, url: url.absoluteString))
            self.saveSites()
        })
        show(alert)
    }

    private func notice(_ title: String, _ message: String) {
        guard presentedViewController == nil else { return }
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private func consent(_ url: URL, source: URL?, remember: Bool, completion: @escaping (Bool) -> Void) {
        guard presentedViewController == nil else { completion(false); return }
        let alert = UIAlertController(title: "Allow navigation?", message: "\(source?.host ?? "Website") wants to open:\n\(url.absoluteString)", preferredStyle: .alert)
        var resolved = false
        func finish(_ allowed: Bool, _ save: Bool) {
            guard !resolved else { return }
            resolved = true
            if save, let host = source?.host {
                var policies = self.defaults.dictionary(forKey: "redirects") as? [String: String] ?? [:]
                policies[host.lowercased()] = allowed ? "allow" : "block"
                self.defaults.set(policies, forKey: "redirects")
            }
            completion(allowed)
        }
        alert.addAction(UIAlertAction(title: "Block", style: .cancel) { _ in finish(false, false) })
        alert.addAction(UIAlertAction(title: "Allow once", style: .default) { _ in finish(true, false) })
        if remember {
            alert.addAction(UIAlertAction(title: "Always allow redirects from this site", style: .default) { _ in finish(true, true) })
            alert.addAction(UIAlertAction(title: "Always block redirects from this site", style: .destructive) { _ in finish(false, true) })
        }
        present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 10) { [weak alert] in
            guard !resolved else { return }
            finish(false, false)
            alert?.dismiss(animated: true)
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 preferences: WKWebpagePreferences,
                 decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        preferences.preferredContentMode = tabs.first { $0.webView === webView }?.desktop == true ? .desktop : .mobile
        self.webView(webView, decidePolicyFor: action) { policy in decisionHandler(policy, preferences) }
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        let scheme = url.scheme?.lowercased() ?? ""
        let isMain = action.targetFrame?.isMainFrame ?? true
        if !["https", "http", "about", "blob", "data"].contains(scheme) {
            decisionHandler(.cancel)
            guard isMain, ["mailto", "tel", "sms", "itms-apps"].contains(scheme) else { return }
            consent(url, source: webView.url, remember: false) { allow in
                if allow { UIApplication.shared.open(url) }
            }
            return
        }
        // New windows are handled once by WKUIDelegate.
        if action.targetFrame == nil { decisionHandler(.allow); return }
        if action.shouldPerformDownload { decisionHandler(.download); return }
        guard isMain, ["http", "https"].contains(scheme) else { decisionHandler(.allow); return }
        let tab = tabs.first { $0.webView === webView }
        if tab?.directURL == url { tab?.directURL = nil; decisionHandler(.allow); return }
        let source = action.sourceFrame.request.url ?? webView.url
        let policy = (defaults.dictionary(forKey: "redirects") as? [String: String])?[BrowserPolicy.host(source)]
        let userInitiated = [.linkActivated, .formSubmitted, .formResubmitted, .backForward, .reload].contains(action.navigationType)
        guard BrowserPolicy.needsConsent(source: source, target: url, userInitiated: userInitiated, isPopup: false, policy: policy) else {
            decisionHandler(.allow); return
        }
        if policy == "block" { decisionHandler(.cancel); return }
        consent(url, source: source, remember: true) { allowed in decisionHandler(allowed ? .allow : .cancel) }
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        guard let url = action.request.url, ["https", "http"].contains(url.scheme?.lowercased() ?? "") else { return nil }
        consent(url, source: webView.url, remember: false) { [weak self] allowed in
            guard allowed, let self = self else { return }
            // Keep the original request (including POST body) and shared website session.
            self.tabs.first { $0.webView === webView }?.directURL = url
            webView.load(action.request)
        }
        return nil
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        tabs.first { $0.webView === webView }?.directURL = nil
        refreshChrome()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        tabs.first { $0.webView === webView }?.directURL = nil
        if (error as NSError).code != NSURLErrorCancelled { notice("Page could not load", error.localizedDescription) }
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        notice("Website stopped", "Tap Reload to reopen this page.")
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping () -> Void) {
        guard presentedViewController == nil else { completionHandler(); return }
        let alert = UIAlertController(title: frame.request.url?.host ?? "Website", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping (Bool) -> Void) {
        guard presentedViewController == nil else { completionHandler(false); return }
        let alert = UIAlertController(title: frame.request.url?.host ?? "Website", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        decisionHandler(response.canShowMIMEType ? .allow : .download)
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) { download.delegate = self }
    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) { download.delegate = self }

    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String,
                  completionHandler: @escaping (URL?) -> Void) {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let name = (suggestedFilename as NSString).lastPathComponent
        let url = documents.appendingPathComponent(UUID().uuidString + "-" + (name.isEmpty ? "download" : name))
        downloadURLs[ObjectIdentifier(download)] = url
        completionHandler(url)
    }

    func downloadDidFinish(_ download: WKDownload) {
        downloadURLs.removeValue(forKey: ObjectIdentifier(download))
        notice("Download saved", "Find it in Files → On My iPhone / iPad → AniBrowser.")
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        if let url = downloadURLs.removeValue(forKey: ObjectIdentifier(download)) { try? FileManager.default.removeItem(at: url) }
        notice("Download failed", error.localizedDescription)
    }

    private func share(_ url: URL) {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = chrome
        controller.popoverPresentationController?.sourceRect = chrome.bounds
        present(controller, animated: true)
    }
}
