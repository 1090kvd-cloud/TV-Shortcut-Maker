<div align="center">

<img src="docs/screenshots/screen1.png" width="640" alt="TV Sideload Shortcut Maker" />

# 📺 TV Sideload Shortcut Maker

**Launch any sideloaded app on your Android TV — with a banner that actually looks good.**

[![Platform](https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android&logoColor=white)](https://developer.android.com/tv)
[![Min SDK](https://img.shields.io/badge/minSdk-23-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose for TV](https://img.shields.io/badge/Jetpack%20Compose-TV%201.0.0-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/training/tv/playback/compose)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

**English** · [Русский](README.md)

</div>

---

## 🤔 The problem

Android TV's launcher only shows apps that declare `CATEGORY_LEANBACK_LAUNCHER`
in their manifest. Sideload a phone-only app — a browser, a torrent client, an
IPTV player — and it installs perfectly fine but **stays invisible**. The usual
workarounds are ugly file managers or ad-riddled "app drawer" apps.

**TV Sideload Shortcut Maker** finds those hidden apps, generates a proper
320×180 leanback banner from the app's own icon, and pins it to the home screen.
It also works as a fast, good-looking app drawer on its own.

## ✨ Features

| | |
|---|---|
| 🔍 **Full device scan** | Lists every installed package, including apps with no leanback entry point |
| 🎯 **Smart filters** | `Hidden on TV` · `All apps` · `TV apps` · `Favorites`, each with a live count |
| 🖼 **Banner generator** | Extracts the icon from the APK and composes it onto a 16:9 canvas at 3× resolution |
| 🎨 **Icon as-is** | The app's original icon, no gradients or overlaid text — the shortcut looks like a normal app |
| 📌 **Pinned shortcuts** | `ShortcutManagerCompat.requestPinShortcut()` with a launch-proxy trampoline |
| 🚀 **Built-in drawer** | Launch anything directly, even when the launcher refuses to pin shortcuts |
| ⭐ **Favorites** | Persisted locally, always one D-Pad press away |
| 🎮 **Made for the remote** | Focus scale-up, animated outlines, depth shadows, 5 % overscan-safe padding |
| 🌑 **Dark by default** | Near-black background with violet/blue accents — easy on OLED panels |
| 📱 **Phones too** | Portrait layout, column count follows screen width, detail panel goes full screen |

## 📸 Screenshots

> Replace these placeholders with real captures (`adb exec-out screencap -p > shot.png`).

| App grid | Detail panel | Shortcut icon |
|---|---|---|
| ![Grid](docs/screenshots/screen1.png) | ![Detail](docs/screenshots/screen2.png) | ![Icon](docs/screenshots/screen3.png) |

## 🏗 Architecture

Classic **MVVM**, no DI framework — a hand-rolled `AppContainer` keeps the sample
easy to read and copy.

```
UI (Compose for TV)  ──observes──▶  AppListViewModel  ──calls──▶  Repository / Helpers
      │                                    │                              │
  AppDrawerScreen                    StateFlow<AppListUiState>       AppRepository
  AppDetailPanel                                                     BannerFactory
  AppCard / TvControls                                               ShortcutHelper
                                                                     FavoritesStore
```

**How a shortcut actually works**

```
TV launcher ──▶ pinned shortcut ──▶ LaunchProxyActivity ──▶ target app
                (banner + label)     (resolves the intent)
```

The proxy activity is transparent and `noHistory`, so the user never sees it. The
indirection means the shortcut survives target-app updates and can fall back
between the leanback and the phone entry point.

## 📁 Project structure

```
TV-Sideload-Shortcut-Maker/
├── build.gradle.kts                 # project-level plugins
├── settings.gradle.kts
├── gradle/libs.versions.toml        # version catalog (single source of truth)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml      # leanback feature + QUERY_ALL_PACKAGES
│       ├── java/com/tvshortcut/maker/
│       │   ├── MainActivity.kt
│       │   ├── TvShortcutApplication.kt
│       │   ├── data/
│       │   │   ├── AppRepository.kt      # package scan + intent resolution
│       │   │   ├── BannerFactory.kt      # icon extraction + 320x180 rendering
│       │   │   ├── ShortcutHelper.kt     # pinned / dynamic shortcuts
│       │   │   ├── FavoritesStore.kt
│       │   │   └── model/AppInfo.kt
│       │   ├── launch/LaunchProxyActivity.kt
│       │   ├── ui/
│       │   │   ├── theme/{Color,Type,Theme}.kt
│       │   │   ├── components/{AppCard,TvControls,StatusViews,TvInteraction}.kt
│       │   │   └── screens/{AppDrawerScreen,AppDetailPanel}.kt
│       │   └── viewmodel/AppListViewModel.kt
│       └── res/{values,drawable,mipmap-*}
└── docs/screenshots/
```

## 🛠 Tech stack

- **Kotlin 2.0.21** + Coroutines
- **Jetpack Compose for TV** (`androidx.tv:tv-material:1.0.0`)
- **AndroidX Palette** for accent-colour extraction
- **MVVM** with `StateFlow` / `collectAsStateWithLifecycle`
- AGP 8.7.2 · Gradle 8.9 · JDK 17

## 🚀 Build

The Gradle wrapper is committed, so no local Gradle installation is needed —
only **JDK 17** and the **Android SDK** (platform 35 + build-tools).

```bash
git clone https://github.com/<your-name>/TV-Sideload-Shortcut-Maker.git
cd TV-Sideload-Shortcut-Maker

./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in **Android Studio Ladybug (2024.2)+** and hit ▶.

<details>
<summary>Release build with your own signing key</summary>

```bash
keytool -genkey -v -keystore release.keystore -alias tvshortcut \
        -keyalg RSA -keysize 2048 -validity 10000

./gradlew assembleRelease
```
</details>

## 🤖 CI — the APK builds itself

`.github/workflows/build.yml` assembles a debug APK on every push and uploads it
as a workflow artifact, so you never have to install the Android SDK locally.

```bash
# push the project and let GitHub build it
./push-to-github.sh                 # needs the GitHub CLI: gh auth login

gh run watch                        # follow the build
gh run download --name tv-shortcut-maker-debug   # grab the APK
```

Pushing a tag additionally publishes a GitHub Release with the APK attached:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## 📥 Install on a TV

```bash
# 1. Enable Developer options → USB/Network debugging on the TV
# 2. Find the TV's IP in Settings → Network
adb connect 192.168.1.42:5555
adb install -r app-debug.apk
```

Alternatively, sideload the APK with *Downloader (AFTV)*, *Send files to TV*, or a
USB stick.

> ⚠️ **First launch:** on Android 11+ the app needs `QUERY_ALL_PACKAGES` to see
> your sideloaded apps. It is a normal install-time permission — no prompt — but
> it is why this app is not distributed through Google Play.

## 🧭 Usage

1. Open **TV Shortcut Maker** from the home screen.
2. The default **Hidden on TV** filter already shows the apps the launcher hides.
3. Highlight an app → **OK** opens the detail panel (**long press OK** launches it instantly).
4. Pick a banner style, preview it live, then **Add to home screen**.
5. Confirm the launcher's pin dialog. Done.

## ⚠️ Known limitations

- **Pinning depends on the launcher.** Google TV and most AOSP TV launchers accept
  pinned shortcuts; some vendor skins (a few Xiaomi/Realme builds) silently ignore
  the request. The in-app drawer and **Favorites** cover that case — the app will
  tell you when pinning is unsupported.
- Mobile apps not designed for TV may render in portrait, ignore the D-Pad, or
  require a mouse. Pair a remote-with-mouse app if needed; this is a limitation of
  the target app, not of the shortcut.
- `QUERY_ALL_PACKAGES` is a restricted permission on Google Play. This project is
  meant for sideloading / F-Droid style distribution.

## 🗺 Roadmap

- [ ] Custom artwork picker (load your own PNG/JPG as a banner)
- [ ] Channel/row publishing via `TvProvider` for launchers without pin support
- [ ] Batch shortcut creation
- [ ] Search with the on-screen keyboard
- [ ] Backup/restore of favorites

## ❤️ Support the project

This app is free, ad-free and collects no data — and it will stay that way.
If it saved you some time, you can support the author:

- ☕ [Boosty](https://boosty.to/YOUR_NAME)
- 💳 [YooMoney](https://yoomoney.ru/to/YOUR_WALLET_ID)
- ⭐ Free but just as useful: star the repo and tell others about it

Donations go towards test devices (TV boxes from different vendors behave very
differently) and the time spent chasing bugs.

## 🤝 Contributing

PRs welcome. Please keep comments in English, follow the official Kotlin style
(`kotlin.code.style=official`) and run `./gradlew lint` before opening a PR.

## 📄 License

MIT — see [LICENSE](LICENSE).

<div align="center">
<sub>Not affiliated with Google. Android TV is a trademark of Google LLC.</sub>
</div>
