<h1 align="center">配额监控 · Quota Widget</h1>

<p align="center">
  <a href="README.md">English</a> ·
  <strong>简体中文</strong> ·
  <a href="README.zh-Hant.md">繁體中文</a> ·
  <a href="README.ja.md">日本語</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  一款开源的 Android 应用与桌面小组件工具，用于在主屏幕上直观查看各类 AI API 账户余额与订阅配额使用情况。
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
  <img src="docs/screenshots/widgets-dark.png" alt="深色主题小组件" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="浅色主题小组件" width="30%" />
</p>

## 功能特性

- **多平台配额监控**：支持查看 DeepSeek、OpenCode Go、Codex、NewAPI、Magpie、Kimi Code、Zhipu Coding Plan、WorkBuddy、WorkBuddy 国际版、Cline Pass、Command Code 及 Antigravity 的账户余额与订阅用量。
- **多样化小组件布局**：提供标准、紧凑与总览三种布局形式，支持自定义用量/余量展示、进度条样式、重置时间显示以及应用内实时预览。
- **可靠的后台刷新**：基于 WorkManager 按设定间隔（15 分钟至 24 小时）自动更新数据，同时支持在小组件上点按手动刷新。
- **加密凭据存储**：API Key 与会话令牌在写入 DataStore Preferences 前均通过 Google Tink 进行加密保护。
- **主题外观定制**：支持浅色、深色与跟随系统模式，并可选择动态系统配色或自定义主题色。
- **多语言界面**：内置简体中文、繁體中文、English、日本語、Deutsch、Français 与 Русский 七种界面语言，默认跟随系统语言（不支持时回退为英语），亦可在设置中手动切换。
- **后台保活指引**：提供关闭电池优化的引导提示，并附带 [DontKillMyApp](https://dontkillmyapp.com/) 的各厂商后台设置参考。
- **版本更新检查**：支持在启动时自动检查 GitHub Releases 新版本，也可在设置页手动检查。

## 支持的平台

<details>
<summary><strong>Antigravity</strong></summary>

- **展示内容**：5 小时与每周模型配额
- **认证方式**：应用内 Google OAuth 登录（非官方接入）
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>Cline Pass</strong></summary>

- **展示内容**：5 小时、每周与每月用量
- **认证方式**：应用内 WorkOS 登录或 API Key
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>Codex</strong></summary>

- **展示内容**：每周与每月订阅配额（可配置显示用量或余量；总览同时展示 5 小时、每周与每月）
- **认证方式**：应用内 OAuth 登录
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>Command Code</strong></summary>

- **展示内容**：5 小时、每周与每月限额及月度积分
- **认证方式**：应用内 Studio 登录或 API Key
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>DeepSeek</strong></summary>

- **展示内容**：API 账户余额（可配置 CNY / USD 显示币种）
- **可用小组件**：标准 · 紧凑

</details>

<details>
<summary><strong>Kimi Code</strong></summary>

- **展示内容**：5 小时、每周与每月配额（视套餐返回而定，可配置用量或余量）
- **配置项**：API Key 与国内版/海外版站点切换
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>Magpie</strong></summary>

- **展示内容**：自部署 Magpie 网关各供应商的订阅额度（5 小时、每周与每月；自动汇总同供应商下的多个账号；可选择展示的供应商，默认取网关返回的首个）
- **配置项**：网关地址与网关 API Key
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>NewAPI</strong></summary>

- **展示内容**：令牌余额（USD）与配额用量/余量百分比
- **配置项**：Base URL、API Key 与配额换算比例
- **可用小组件**：金额标准 · 金额紧凑 · 使用情况

</details>

<details>
<summary><strong>OpenCode Go</strong></summary>

- **展示内容**：滚动、每周与每月配额（可配置显示用量或余量）
- **认证方式**：应用内网页登录（可选工作区）或 API Key
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>WorkBuddy</strong></summary>

- **展示内容**：每月积分额度
- **认证方式**：应用内浏览器登录
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>WorkBuddy 国际版</strong></summary>

- **展示内容**：每月积分额度（国际版）
- **认证方式**：应用内浏览器登录
- **可用小组件**：标准 · 紧凑 · 总览

</details>

<details>
<summary><strong>Zhipu Coding Plan</strong></summary>

- **展示内容**：5 小时与每周配额（可配置用量或余量）
- **配置项**：API Key 与国内版/海外版站点切换
- **可用小组件**：标准 · 紧凑 · 总览

</details>

> **关于应用内登录**
> OpenCode Go、Codex、WorkBuddy、WorkBuddy 国际版、Cline Pass、Command Code 与 Antigravity 的浏览器登录流程均在应用内 WebView 中完成，授权回调会自动捕获并回填，无需手动复制回调 URL。

> **关于 Antigravity 的风险提示**
> Antigravity 采用非官方接口接入，Google 可能对相关账号实施风控或限制。建议先在官方 Antigravity 桌面客户端中登录并完成项目初始化，再于本应用中登录，并请自行评估使用风险。

## 安装与快速开始

### 下载安装

前往 [GitHub Releases](https://github.com/657kbps/quota-widget/releases/latest) 下载最新的已签名 APK 进行安装。

- **正式版包名**：`com.kuyermqi.quotawidget`
- **最低系统要求**：Android 8.0（API 26）

### 使用步骤

1. 安装并打开**配额监控**，根据提示关闭电池优化，以确保后台定时刷新能够正常执行。
2. 在主页选择需要监控的平台，填写 API Key 或完成账号登录，按需调整显示偏好后保存。
3. 长按桌面空白处，打开**小组件**面板，将所需的配额监控小组件添加至主屏幕。
4. 点按小组件上的刷新图标，即可立即触发一次数据更新。

> 部分第三方定制 Android 系统（ROM）在关闭标准电池优化后仍可能限制后台任务。如需进一步放行自启动或后台运行权限，请参考 [DontKillMyApp](https://dontkillmyapp.com/) 的对应厂商说明。

## 从源码构建

### Debug 构建

```bash
./gradlew :androidApp:assembleDebug
```

构建产物位于 `androidApp/build/outputs/apk/debug/`。

Debug 版本包名为 `com.kuyermqi.quotawidget.debug`（桌面应用名称为 **Quota Widget Debug**），可与正式版共存安装。

### 本地 Release 验证（R8）

如需在本地使用调试密钥验证 R8 代码混淆与资源压缩效果（仅限开发测试，切勿用于正式分发）：

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### 模块架构与技术栈

| 模块 | 职责说明 |
|------|----------|
| `shared` | Kotlin Multiplatform（KMP）共享层，包含领域模型、各平台 API 客户端、刷新用例及加密设置仓库 |
| `androidApp` | Jetpack Compose 界面、应用内 WebView 登录流程、Glance 桌面小组件、WorkManager 后台任务与 Android 资源 |

**核心技术栈**：Kotlin (KMP) · Jetpack Compose · Glance AppWidget · Ktor · kotlinx.serialization · DataStore · Tink · WorkManager · Material Kolor

## Release 签名与发布

正式发布的 APK 必须使用独立的 Release Keystore 进行签名，切勿使用 Debug Keystore。

1. **生成 Keystore**（仅需执行一次，请妥善保管密钥文件及密码）：

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias quota-widget \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **配置 GitHub Actions Secrets**（路径：*Settings → Secrets and variables → Actions*）：

   | Secret | 说明 |
   |--------|------|
   | `RELEASE_KEYSTORE_BASE64` | Base64 编码后的 keystore 内容：Linux/macOS 可运行 `base64 -w0 release.keystore`，PowerShell 可运行 `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` |
   | `RELEASE_STORE_PASSWORD` | Keystore 密码 |
   | `RELEASE_KEY_ALIAS` | 密钥别名（例如 `quota-widget`） |
   | `RELEASE_KEY_PASSWORD` | Key 密码 |

3. **推送标签以触发发布**：

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

如需在本地构建正式签名 APK，请预先导出以下环境变量：

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## 参与贡献

欢迎提交 Issue 与 Pull Request。在贡献代码前，请先阅读 [AGENTS.md](AGENTS.md)，了解项目的编码规范、架构约定与模块边界。

提交信息须遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范，例如：

```text
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## 致谢

- [DontKillMyApp](https://dontkillmyapp.com/) — 提供各 Android 厂商后台保活指引
- [LINUX DO](https://linux.do/) — 社区讨论与建议反馈
- DeepSeek、OpenCode 等相关平台提供的开放接口与服务

---

<p align="center">
  如果这个项目对你有所帮助，欢迎点亮 Star 支持 ⭐
</p>
