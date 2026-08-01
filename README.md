# Quota Widget

Android home-screen widget that monitors DeepSeek API balance.

## Modules

- `shared` — KMP shared domain, networking, and encrypted settings
- `androidApp` — Compose settings UI, Glance widget, WorkManager

## Build

```bash
./gradlew :androidApp:assembleDebug
```

Package: `com.kuyermqi.quotawidget`

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

Local release builds need the same env vars pointing at your keystore file:

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```
