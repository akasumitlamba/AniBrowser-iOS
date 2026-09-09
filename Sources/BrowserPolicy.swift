/* SPDX-License-Identifier: MPL-2.0 */
import Foundation

enum BrowserPolicy {
    static let speeds: [Double] = [1, 1.25, 1.5, 1.75, 2]

    static func webURL(_ input: String) -> URL? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !text.contains(where: { $0.isWhitespace }) else { return nil }
        let candidate = text.contains("://") ? text : "https://" + text
        guard let url = URL(string: candidate),
              ["https", "http"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty,
              url.user == nil, url.password == nil else { return nil }
        return url
    }

    static func destination(_ input: String) -> URL? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if (text.contains(".") || text.contains("://") || text == "localhost"),
           let url = webURL(text) { return url }
        var components = URLComponents(string: "https://duckduckgo.com/")!
        components.queryItems = [URLQueryItem(name: "q", value: text)]
        return components.url
    }

    // Exact host comparison: never grant example.com permission to example.com.attacker.test.
    static func host(_ url: URL?) -> String {
        url?.host?.lowercased() ?? ""
    }

    static func needsConsent(source: URL?, target: URL, userInitiated: Bool,
                             isPopup: Bool, policy: String?) -> Bool {
        if isPopup { return true }
        if userInitiated || host(source).isEmpty || host(source) == host(target) { return false }
        return policy != "allow"
    }
}

struct HomeSite: Codable {
    var title: String
    var url: String
    static let defaults = [HomeSite(title: "YouTube", url: "https://www.youtube.com"),
                           HomeSite(title: "Crunchyroll", url: "https://www.crunchyroll.com")]
}
