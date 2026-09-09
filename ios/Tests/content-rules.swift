import Foundation
import WebKit

let rules = try String(contentsOfFile: "Resources/blocklist.json", encoding: .utf8)
WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "AniRulesTest", encodedContentRuleList: rules) { list, error in
    if let error = error { print("FAIL: \(error)"); exit(1) }
    guard list != nil else { print("FAIL: missing rule list"); exit(1) }
    print("PASS: WebKit content rules compile")
    exit(0)
}
DispatchQueue.main.asyncAfter(deadline: .now() + 30) { print("FAIL: compilation timeout"); exit(1) }
RunLoop.main.run()
