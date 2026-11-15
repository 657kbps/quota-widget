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

- **Multi-platform monitoring**: DeepSeek balance, OpenCode Go usage, Codex subscription usage (weekly / monthly), NewAPI token balance / usage, Magpie gateway allowances, and Kimi Code / Zhipu GLM Coding Plan subscription quotas
- **Multiple widget sizes**: Standard, compact, and usage overview layouts
- **Background refresh**: WorkManager periodic updates plus tap-to-refresh
- **DeepSeek peak/off-peak**: the standard widget shows the current price tier, next switch time, and a countdown when space allows
- **Secure storage**: API keys / OAuth tokens encrypted with Tink before DataStore persistence
- **Customizable theme**: Light / dark / system, plus a custom accent color
- **Background survival tips**: Battery optimization guidance and [DontKillMyApp](https://dontkillmyapp.com/) OEM references
- **In-app update checks**: Optionally check GitHub Releases on launch

## Supported platforms

| Platform | Displays | Widgets |
|----------|----------|---------|
| **DeepSeek** | API account balance · standard-widget peak/off-peak tier and countdown | Standard · Compact |
| **OpenCode Go** | Rolling / weekly / monthly usage or remaining (configurable) | Standard · Compact · Overview |
| **Codex** | Weekly / monthly usage or remaining (in-app OAuth login) | Standard · Compact · Overview |
| **NewAPI** | Token balance (USD) and usage/remaining percent (configurable Base URL + API Key) | Balance standard · Balance compact · Usage |
| **Magpie** | Subscription allowances per provider of a self-hosted gateway (accounts of the same provider merged; 5h / week / month; pick the provider, defaults to the first one the gateway returns; configurable gateway URL + gateway key) | Standard · Compact |
| **Kimi Code** | 5h / weekly / monthly usage or remaining (depending on plan; CN / global site) | Standard · Compact · Overview |
| **Zhipu GLM Coding Plan** | 5h / weekly usage or remaining (CN / global site) | Standard · Compact · Overview |

## Download

Grab the latest APK from [Releases](https://github.com/657kbps/quota-widget/releases/latest).

> Package: `com.kuyermqi.quotawidget`

## Quick start

1. Install the app and disable battery optimization when prompted (strongly recommended)
2. Expand a platform on the home screen, configure an API key or sign in, then save
3. Long-press the home screen → Widgets → add a Quota Widget
4. Tap the refresh icon on a widget for an immediate update

Some OEM ROMs still restrict background work after disabling battery optimization. See [DontKillMyApp](https://dontkillmyapp.com/) for vendor-specific allowlists.

## DeepSeek peak / off-peak

The standard DeepSeek widget can show the current price tier, the next switch time, and a countdown when it is tall enough. The compact widget is unchanged.

- Times are evaluated in Beijing time: weekdays 09:00–12:00 and 14:00–18:00 are peak; all other instants are off-peak. The widget labels peak as “梁文峰 · 原价” and off-peak as “梁文谷 · 半价”.
- Saturdays and Sundays are always off-peak, including make-up workdays. Official holiday spans from State Council notices are off-peak for the whole period.
- Bundled 2026 and 2027 calendars come from [holiday-cn](https://github.com/NateScarlet/holiday-cn) (MIT) and were checked against the State Council notices. The settings page shows covered years, update status, and a manual update action. While enabled, the app tries a weekly network update for the current and next year; failures keep the last valid cache and surface a status.
- A missing API key, balance error, or offline state does not stop the time area. Toggling peak/off-peak never re-saves the API key and never triggers a balance refresh.

Refresh limits: balances keep their existing WorkManager interval. The peak/off-peak area is recalculated at the target switch with an inexact `AlarmManager`, plus a low-power local refresh at most every 30 minutes. Doze, OEM limits, or system delays can make a static widget countdown lag; the accurate Beijing-time target is shown alongside it as the source of truth. Background countdown refreshes never request balances.

## Build from source

```bash
./gradlew :androidApp:assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/`.

The debug applicationId is `com.kuyermqi.quotawidget.debug`, so it can be installed alongside the release package (`com.kuyermqi.quotawidget`). The launcher label is “配额监控 Debug”.

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
- [holiday-cn](https://github.com/NateScarlet/holiday-cn) — bundled holiday calendar data (MIT; license at `androidApp/src/main/assets/holiday-cn/LICENSE`)
- [LINUX DO](https://linux.do/) — community discussion and feedback
- Open APIs from DeepSeek, OpenCode, and related platforms

---

<p align="center">
  If this project helps you, a Star is appreciated ⭐
</p>
