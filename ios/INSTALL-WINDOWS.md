# Install AniBrowser using Windows, for free

You need Windows, an iPhone/iPad with iOS/iPadOS 16 or newer, a USB cable, a free Apple Account, and the built IPA. No personally owned Mac or paid Apple Developer membership is required. Free signing expires every seven days; refresh it to keep using the app.

## Get the IPA

In the AniBrowser GitHub repository, select **Actions → Build free iOS IPA → Run workflow**. After a successful run, download **AniBrowser-iOS-unsigned** under Artifacts. Extract the ZIP on Windows. Use **AniBrowser-unsigned.ipa**, not the ZIP.

The workflow uses a standard macOS runner in a **public** repository, which GitHub provides free. It refuses private repository runs. It needs no Apple credentials or paid runner. Artifacts expire after three days; save the IPA on your PC or run the workflow again.

## Sign and install

1. Download the Windows version from [Sideloadly](https://sideloadly.io/). Follow its current Windows prerequisites for Apple device support/iTunes and iCloud.
2. Connect your iPhone by USB, unlock it, and tap **Trust** if asked.
3. Open Sideloadly, select the device, and choose **AniBrowser-unsigned.ipa**.
4. Enter your free Apple Account in Sideloadly and press **Start**. Complete login/verification locally. Never put Apple credentials in GitHub or chat.
5. On iPhone, trust the developer profile under **Settings → General → VPN & Device Management**, if prompted.
6. Enable **Settings → Privacy & Security → Developer Mode**, if required, and follow the restart prompts. This option may appear only after attempting installation.
7. Open **AniBrowser**, visit a website, and try the speedometer controls with a video.

Sideloadly signs the unsigned IPA for your device. Simply tapping it in Files will not install it. Basic installation requires no paid Sideloadly feature.

## Refresh every seven days

Refresh/reinstall using the **same Apple Account and bundle identifier**. Keep AniBrowser installed when updating to preserve settings. Automatic refresh depends on Sideloadly's documented PC/device connectivity conditions. If it fails, reconnect by USB and install again. Free Apple signing also has active-app and App ID limits; this is not a permanent maintenance-free installation.

## Controls

- **Home:** saved sites; hold a tile to remove it, or use + to add one.
- **Address:** website or DuckDuckGo search.
- **Speedometer:** persistent 1×–2× speed; seek ±10 seconds on playing videos.
- **Tabs:** eight in-memory tabs. Open tabs do not survive app termination; cookies and saved sites persist.
- **Menu:** fullscreen, desktop/mobile layout, small ad-domain filter, sharing, and redirect permission reset.
- **Fullscreen:** floating exit button restores controls.
- **Downloads:** Files → On My iPhone/iPad → AniBrowser.

## Limits

This is an initial WebKit implementation, not the Android engine. Android extensions, launcher shortcuts, and its full ad interceptor are not included. The small filter is not comprehensive and does not remove video ads. Approved popups open in the same tab; flows requiring a separate window/opener may fail. This app does not register as the default iOS browser.

Physical-device installation, DRM streaming, subtitles, native fullscreen speed, login flows and picture-in-picture need actual iPhone testing. Some services disallow embedded browsers or ignore speed changes. No DRM bypass is included. Compilation does not establish streaming compatibility or stability.

Sources checked September 9, 2026: [Apple free signing limits](https://developer.apple.com/support/compare-memberships/), [GitHub runner pricing](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), [Sideloadly FAQ](https://sideloadly.io/faq).
