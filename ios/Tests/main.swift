/* SPDX-License-Identifier: MPL-2.0 */
import Foundation
func check(_ value: Bool, _ message: String) {
    if !value { fatalError(message) }
}
check(BrowserPolicy.destination("   ") == nil, "Empty search should do nothing")
check(BrowserPolicy.webURL("example.com/path")?.absoluteString == "https://example.com/path", "Default HTTPS")
check(BrowserPolicy.webURL("javascript:alert(1)") == nil, "Reject script schemes")
check(BrowserPolicy.webURL("file:///etc/passwd") == nil, "Reject file URL")
check(BrowserPolicy.webURL("https://user:password@example.com") == nil, "Reject URL credentials")
check(BrowserPolicy.destination("anime & video")?.query == "q=anime%20%26%20video", "Encode search query")
let source = URL(string: "https://example.com/watch")!
let cross = URL(string: "https://example.com.attacker.test/")!
check(BrowserPolicy.needsConsent(source: source, target: cross, userInitiated: false, isPopup: false, policy: nil), "Cross-host redirect needs consent")
check(!BrowserPolicy.needsConsent(source: source, target: cross, userInitiated: true, isPopup: false, policy: nil), "Direct user links stay usable")
check(BrowserPolicy.needsConsent(source: source, target: source, userInitiated: true, isPopup: true, policy: "allow"), "Popup always needs consent")
check(!BrowserPolicy.needsConsent(source: source, target: cross, userInitiated: false, isPopup: false, policy: "allow"), "Remembered permission")
check(BrowserPolicy.needsConsent(source: source, target: cross, userInitiated: false, isPopup: false, policy: "block"), "Remembered denial")
check(BrowserPolicy.speeds == [1, 1.25, 1.5, 1.75, 2], "Compatible speed values")
print("PASS: 12 native URL and navigation checks")
