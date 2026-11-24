<h1 align="center">Quota Widget</h1>

<p align="center">
  <strong>English</strong> ·
  <a href="README.zh-Hans.md">简体中文</a> ·
  <a href="README.zh-Hant.md">繁體中文</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  An open-source Android app and home-screen widget suite for monitoring AI API balances and subscription quotas at a glance.
</p>

<p align="center">
  <a href="https://657kbps.github.io/quota-widget/"><strong>657kbps.github.io/quota-widget</strong></a>
</p>

<p align="center">
  <a href="https://github.com/657kbps/quota-widget/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/657kbps/quota-widget?style=flat-square"></a>
  <a href="https://github.com/657kbps/quota-widget/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/657kbps/quota-widget/total?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <img src="docs/screenshots/app-home.png" alt="App home screen" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-dark.png" alt="Widgets in dark theme" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="Widgets in light theme" width="30%" />
</p>

## Features

- **Multi-Provider Monitoring**: Track account balances and subscription usage windows across DeepSeek, OpenCode Go, Codex, NewAPI, Magpie, Kimi Code, Zhipu Coding Plan, WorkBuddy, WorkBuddy International, Cline Pass, Command Code, and Antigravity.
- **Flexible Widget Layouts**: Choose from Standard, Compact, and Overview layouts with customizable used/remaining values, progress bar styles, reset time indicators, and live in-app previews.
- **Reliable Background Refresh**: Automatically updates via WorkManager at configurable intervals (15 minutes to 24 hours), alongside one-tap manual refresh directly from any widget.
- **Encrypted Credential Storage**: API keys and session tokens are encrypted with Google Tink before being persisted in DataStore Preferences.
- **Customizable Appearance**: Supports Light, Dark, and Follow System themes, with dynamic system colors or a custom accent color.
- **Localized Interface**: Available in English, Simplified Chinese, Traditional Chinese, Japanese, German, French, and Russian. Follows the system locale by default (falling back to English) or can be set manually in Settings.
- **Background Execution Guidance**: Includes battery optimization prompts and OEM-specific guidance via [DontKillMyApp](https://dontkillmyapp.com/).
- **Update Notifications**: Optional check against GitHub Releases on app launch, plus manual update checks in Settings.

## Supported Providers

<details>
<summary><strong>Antigravity</strong></summary>

- **Displays**: 5-hour and weekly model quotas
- **Authentication**: In-app Google OAuth (unofficial integration)
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>Cline Pass</strong></summary>

- **Displays**: 5-hour, weekly, and monthly usage
- **Authentication**: In-app WorkOS sign-in or API key
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>Codex</strong></summary>

- **Displays**: Weekly and monthly subscription quota (used or remaining; 5-hour, weekly, and monthly in Overview)
- **Authentication**: In-app OAuth sign-in
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>Command Code</strong></summary>

- **Displays**: 5-hour, weekly, and monthly limits plus monthly credits
- **Authentication**: In-app Studio sign-in or API key
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>DeepSeek</strong></summary>

- **Displays**: API account balance (configurable CNY / USD display currency)
- **Widgets**: Standard · Compact

</details>

<details>
<summary><strong>Kimi Code</strong></summary>

- **Displays**: 5-hour, weekly, and monthly quota (used or remaining, depending on plan)
- **Configuration**: API key and China / Global region selection
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>Magpie</strong></summary>

- **Displays**: Per-provider subscription allowances on a self-hosted Magpie gateway (5-hour, weekly, and monthly; aggregates multiple accounts per provider; selectable provider, defaulting to the first returned)
- **Configuration**: Gateway URL and gateway API key
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>NewAPI</strong></summary>

- **Displays**: Token balance (USD) and usage/remaining percentage
- **Configuration**: Base URL, API key, and quota conversion rate
- **Widgets**: Balance Standard · Balance Compact · Usage

</details>

<details>
<summary><strong>OpenCode Go</strong></summary>

- **Displays**: Rolling, weekly, and monthly quota (used or remaining)
- **Authentication**: In-app web sign-in (with workspace selection) or API key
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>WorkBuddy</strong></summary>

- **Displays**: Monthly credit allowance
- **Authentication**: In-app browser sign-in
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>WorkBuddy International</strong></summary>

- **Displays**: Monthly credit allowance (international edition)
- **Authentication**: In-app browser sign-in
- **Widgets**: Standard · Compact · Overview

</details>

<details>
<summary><strong>Zhipu Coding Plan</strong></summary>

- **Displays**: 5-hour and weekly quota (used or remaining)
- **Configuration**: API key and China / Global region selection
- **Widgets**: Standard · Compact · Overview

</details>

> **In-App Authentication**
> Browser-based sign-in flows (OpenCode Go, Codex, WorkBuddy, WorkBuddy International, Cline Pass, Command Code, and Antigravity) run inside an embedded WebView and capture authorization callbacks automatically, removing the need to copy callback URLs manually.

> **Antigravity Notice**
> Antigravity relies on an unofficial integration, and Google may flag or restrict associated accounts. To reduce risk, sign in to the official Antigravity desktop client and initialize a project before authenticating in this app, and evaluate the risk at your own discretion.

## Installation & Quick Start

### Download

Download the latest signed APK from [GitHub Releases](https://github.com/657kbps/quota-widget/releases/latest).

- **Release Package ID**: `com.kuyermqi.quotawidget`
- **Minimum Android Version**: Android 8.0 (API level 26)

### Setup

1. Install and open **Quota Widget**, then disable battery optimization when prompted so background refreshes can run reliably.
2. Select a provider on the home screen, enter your credentials or complete sign-in, adjust display preferences if needed, and save.
3. Long-press an empty area on your home screen, open **Widgets**, and place the desired Quota Widget layout.
4. Tap the refresh button on any widget to fetch updated data immediately.

> Some OEM Android distributions restrict background execution even after standard battery optimization is disabled. Consult [DontKillMyApp](https://dontkillmyapp.com/) for device-specific autostart and background allowlist instructions.

## Building from Source

### Debug Build

```bash
./gradlew :androidApp:assembleDebug
```

The generated APK is written to `androidApp/build/outputs/apk/debug/`.

The debug variant uses the package ID `com.kuyermqi.quotawidget.debug` (launcher label: **Quota Widget Debug**), allowing it to be installed side by side with the release build.

### Local Release Verification (R8)

To verify R8 minification and resource shrinking locally using the debug keystore (for testing only; do not distribute):

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### Architecture & Tech Stack

| Module | Responsibility |
|--------|----------------|
| `shared` | Kotlin Multiplatform (KMP) domain models, provider API clients, refresh interactor, and encrypted settings repository |
| `androidApp` | Jetpack Compose UI, in-app WebView sign-in flows, Glance AppWidgets, WorkManager workers, and Android resources |

**Core Technologies**: Kotlin (KMP) · Jetpack Compose · Glance AppWidget · Ktor · kotlinx.serialization · DataStore · Tink · WorkManager · Material Kolor

## Release Signing & Publishing

Production APKs must be signed with a dedicated release keystore rather than the debug keystore.

1. **Generate a keystore** (one-time setup; store the file and credentials securely):

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias quota-widget \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure GitHub Actions secrets** (*Settings → Secrets and variables → Actions*):

   | Secret | Description |
   |--------|-------------|
   | `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore: `base64 -w0 release.keystore` (Linux/macOS) or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` (PowerShell) |
   | `RELEASE_STORE_PASSWORD` | Keystore password |
   | `RELEASE_KEY_ALIAS` | Key alias (e.g., `quota-widget`) |
   | `RELEASE_KEY_PASSWORD` | Key password |

3. **Publish a release** by pushing a version tag:

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

To build a signed release APK locally, export the following environment variables:

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## Contributing

Issues and pull requests are welcome. Before contributing, please review [AGENTS.md](AGENTS.md) for coding conventions, architecture guidelines, and module boundaries.

Commit messages must follow [Conventional Commits](https://www.conventionalcommits.org/), for example:

```text
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## Acknowledgements

- [DontKillMyApp](https://dontkillmyapp.com/) — OEM background execution guidance
- Open APIs and services provided by DeepSeek, OpenCode, and other supported platforms

---

<p align="center">
  If you find this project useful, please consider giving it a star ⭐
</p>
