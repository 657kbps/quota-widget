<h1 align="center">Quota Widget · 配额监控</h1>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a>
</p>

<p align="center">
  Open-source Android home-screen widgets for API balance and usage at a glance
</p>

<p align="center">
  <a href="https://github.com/657kbps/quota-widget/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/657kbps/quota-widget?style=flat-square"></a>
  <a href="https://github.com/657kbps/quota-widget/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/657kbps/quota-widget/total?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <img src="docs/screenshots/app-home.png" alt="App home" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-dark.png" alt="Dark widgets" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="Light widgets" width="30%" />
</p>

## Features

- **Multi-platform monitoring**: DeepSeek account balance and OpenCode Go usage (rolling / weekly / monthly)
- **Multiple widget sizes**: Standard, compact, and usage overview layouts
- **Background refresh**: WorkManager periodic updates plus tap-to-refresh
- **Secure storage**: API keys encrypted with Tink before DataStore persistence
- **Customizable theme**: Light / dark / system, plus a custom accent color
- **Background survival tips**: Battery optimization guidance and [DontKillMyApp](https://dontkillmyapp.com/) OEM references
- **In-app update checks**: Optionally check GitHub Releases on launch

## Supported platforms

| Platform | Displays | Widgets |
|----------|----------|---------|
| **DeepSeek** | API account balance | Standard · Compact |
| **OpenCode Go** | Rolling / weekly / monthly remaining usage | Standard · Compact · Overview |

## Download

Grab the latest APK from [Releases](https://github.com/657kbps/quota-widget/releases/latest).

> Package: `com.kuyermqi.quotawidget`

## Quick start

1. Install the app and disable battery optimization when prompted (strongly recommended)
2. Expand a platform on the home screen, configure an API key or sign in, then save
3. Long-press the home screen → Widgets → add a Quota Widget
4. Tap the refresh icon on a widget for an immediate update

Some OEM ROMs still restrict background work after disabling battery optimization. See [DontKillMyApp](https://dontkillmyapp.com/) for vendor-specific allowlists.

## Build from source

```bash
./gradlew :androidApp:assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/`.

Local Release (R8) verification with the debug keystore (do not distribute):

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### Modules

| Module | Role |
|--------|------|
| `shared` | KMP shared domain, networking, encrypted settings |
| `androidApp` | Compose settings UI, Glance widgets, WorkManager, Manifest |

Stack: Kotlin · Jetpack Compose · Glance AppWidget · Ktor · DataStore · Tink · WorkManager

## Release signing

Release APKs are signed with a dedicated keystore (never the debug keystore).

1. Create a keystore once and keep a secure backup:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias quota-widget \
  -keyalg RSA -keysize 2048 -validity 10000
```

2. Add GitHub repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|--------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` (macOS/Linux) or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` (PowerShell) |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | e.g. `quota-widget` |
| `RELEASE_KEY_PASSWORD` | key password |

3. Push a tag to publish: `git tag v1.0.0 && git push origin v1.0.0`

Local release builds need the same env vars:

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## Contributing

Issues and pull requests are welcome. See [AGENTS.md](AGENTS.md) for coding conventions and module boundaries.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/), for example:

```
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## Acknowledgements

- [DontKillMyApp](https://dontkillmyapp.com/) — OEM background survival guides
- Open APIs from DeepSeek, OpenCode, and related platforms

---

<p align="center">
  If this project helps you, a Star is appreciated ⭐
</p>
