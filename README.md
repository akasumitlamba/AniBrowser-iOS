# AniBrowser for iOS

A native personal browser for iPhone and iPad using UIKit and WKWebView. Requires iOS 16 or newer. Includes AniHome, saved websites, eight tabs, persistent HTML5 video speed, seek controls, popup/redirect confirmation, a small ad-domain filter, fullscreen browsing, shared website cookies, and downloads.

**Status:** initial source implementation. Cloud compilation and physical iPhone installation/playback have not yet been verified. Not a stable release.

## Free installation using Windows

Read [INSTALL-WINDOWS.md](INSTALL-WINDOWS.md). A standard GitHub macOS runner compiles an unsigned IPA; Sideloadly on Windows signs and installs it using a free Apple Account. No personally owned Mac or paid developer membership is required. Free signing expires after seven days and needs refreshing.

Use this folder as the root of a separate public GitHub repository, including `.github/workflows/build-ios.yml`. Run **Actions → Build free iOS IPA**. The workflow refuses private repositories to avoid paid runner usage. Never upload Apple credentials or the outer Android workspace.

## Development

On macOS with Xcode and XcodeGen:

```sh
node --test Tests/*.test.cjs
swiftc Sources/BrowserPolicy.swift Tests/main.swift -o /tmp/ani-policy-tests
/tmp/ani-policy-tests
xcodegen generate
xcodebuild -project AniBrowser.xcodeproj -scheme AniBrowser -sdk iphoneos -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```

`Resources/playback.js` is the Android MPL-2.0 controller. `bridge.js` supplies its messaging interface in an isolated WKContentWorld in every frame. Native state is requested every 750 ms while visible; speed recovery remains bounded. Keep the copied controller and tests synchronized when updating Android behavior.

The workflow packages Payload/AniBrowser.app into an unsigned IPA with a SHA-256 as a short-lived artifact. See the installation guide for capability differences and device checks.

Source code is under [MPL-2.0](LICENSE). Product and site names belong to their respective owners.
