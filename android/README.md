# AniBrowser for Android

Native browser based on Mozilla Reference Browser, GeckoView and Android Components, with AniHome, playback speed control, navigation controls and fullscreen shortcuts.

## Build

Install Node.js, a JDK supported by the pinned Android Gradle Plugin, and the Android SDK. The project uses Java 17 bytecode, SDK 37.1 and Build Tools 37.0.0. Dependency versions are in `gradle/libs.versions.toml`. Configure the SDK through Android Studio, `ANDROID_HOME`, or a local `local.properties` file.

From the repository root on Windows:

```powershell
./build-android.ps1
```

Or from this directory:

```sh
node --test app/src/test/playback.test.cjs app/src/test/navigation.test.cjs
./gradlew :app:assembleDebug
```

Windows can use `gradlew.bat`. APKs appear in `app/build/outputs/apk/debug/`; choose your device architecture. Debug and nightly variants are enabled; no production release is provided. Keep the same signing identity for updates. Device installation and protected playback need separate verification.

## Upstream

Initially based on Mozilla Reference Browser commit `3af922ebcbcc0ab707c2f08a285d84d5dd0df40a`. Application changes are included directly. Source notices and [MPL-2.0](LICENSE) are retained.
