<h1 align="center">配额监控 · Quota Widget</h1>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a>
</p>

<p align="center">
  在 Android 桌面一眼查看 API 余额与用量的开源小组件
</p>

<p align="center">
  <a href="https://github.com/657kbps/quota-widget/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/657kbps/quota-widget?style=flat-square"></a>
  <a href="https://github.com/657kbps/quota-widget/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/657kbps/quota-widget/total?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <img src="docs/screenshots/app-home.png" alt="应用主页" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-dark.png" alt="深色小组件" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="浅色小组件" width="30%" />
</p>

## 功能亮点

- **多平台监控**：DeepSeek 账户余额、OpenCode Go 用量、Codex 订阅用量（每周 / 每月）
- **多种小组件尺寸**：标准、紧凑、用量总览，适配不同桌面布局
- **后台自动刷新**：WorkManager 按设定间隔更新，支持手动点按刷新
- **安全存储**：API Key / OAuth Token 经 Tink 加密后写入 DataStore
- **主题可定制**：浅色 / 深色 / 跟随系统，以及自定义主题色
- **后台保活提示**：引导关闭电池优化，并提供 [DontKillMyApp](https://dontkillmyapp.com/) 厂商设置参考
- **应用内更新检查**：启动时可检查 GitHub Releases 新版本

## 支持的平台


| 平台 | 展示内容 | 小组件 |
|------|----------|--------|
| **DeepSeek** | API 账户余额 | 标准 · 紧凑 |
| **OpenCode Go** | 滚动 / 每周 / 每月用量或余量（可配置） | 标准 · 紧凑 · 总览 |
| **Codex** | 每周 / 每月用量或余量（可配置；应用内 OAuth 登录） | 标准 · 紧凑 · 总览 |

## 下载

前往 [Releases](https://github.com/657kbps/quota-widget/releases/latest) 下载最新 APK 安装即可。

> 包名：`com.kuyermqi.quotawidget`

## 快速开始

1. 安装并打开应用，按提示关闭电池优化（强烈建议）
2. 在主页展开对应平台，配置 API Key 或登录账号后保存
3. 长按桌面空白处 → 小组件 → 添加「配额监控」相关组件
4. 点按小组件上的刷新按钮即可立即更新

部分国产 ROM 即使关闭电池优化仍可能限制后台；可参考 [DontKillMyApp](https://dontkillmyapp.com/) 为应用放行自启动 / 后台运行。

## 从源码构建

```bash
./gradlew :androidApp:assembleDebug
```

产物位于 `androidApp/build/outputs/apk/debug/`。

Debug 包名为 `com.kuyermqi.quotawidget.debug`，可与正式包（`com.kuyermqi.quotawidget`）同时安装；桌面显示为「配额监控 Debug」。

本地验证 Release（R8）可用调试签名（勿用于正式分发）：

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### 模块结构

| 模块 | 说明 |
|------|------|
| `shared` | KMP 共享领域模型、网络客户端、加密设置 |
| `androidApp` | Compose 设置页、Glance 小组件、WorkManager、Manifest |

技术栈：Kotlin · Jetpack Compose · Glance AppWidget · Ktor · DataStore · Tink · WorkManager

## Release 签名

正式发布的 APK 使用独立 keystore 签名（切勿使用 debug keystore）。

1. 一次性创建 keystore 并妥善备份：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias quota-widget \
  -keyalg RSA -keysize 2048 -validity 10000
```

2. 在 GitHub 仓库配置 Secrets（Settings → Secrets and variables → Actions）：

| Secret | 值 |
|--------|-----|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore`（macOS/Linux）或 PowerShell：`[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 例如 `quota-widget` |
| `RELEASE_KEY_PASSWORD` | key 密码 |

3. 打 tag 发布：`git tag v1.0.0 && git push origin v1.0.0`

本地 Release 构建需设置相同环境变量：

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## 贡献

欢迎 Issue 与 Pull Request。编码约定与模块边界见 [AGENTS.md](AGENTS.md)。

提交信息请遵循 [Conventional Commits](https://www.conventionalcommits.org/)，例如：

```
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## 致谢

- [DontKillMyApp](https://dontkillmyapp.com/) — 各厂商后台保活设置指南
- [LINUX DO](https://linux.do/) — 社区讨论与反馈
- DeepSeek、OpenCode 等平台提供的开放接口

---

<p align="center">
  如果这个项目对你有帮助，欢迎 Star ⭐
</p>
