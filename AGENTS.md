# [AGENTS.md](http://AGENTS.md)

本文件供编码 Agent 与贡献者遵循。修改代码前先阅读；保持改动小而聚焦，并与现有风格一致。

## 项目概览

多平台 API 余额桌面小组件（Android）；当前支持 DeepSeek、OpenCode Go、Codex、NewAPI。


| 项       | 值                                          |
| ------- | ------------------------------------------ |
| Package | `com.kuyermqi.quotawidget`                 |
| 语言      | Kotlin                                     |
| 结构      | KMP `shared` + Android `androidApp`        |
| UI      | Jetpack Compose（设置页）+ Glance AppWidget（桌面） |
| 网络      | Ktor Client + kotlinx.serialization        |
| 存储      | DataStore Preferences + Tink 加密 API Key    |
| 后台      | WorkManager 周期性刷新                          |
| SDK     | min 26 / target & compile 36 / JVM 11      |
| 版本目录    | `gradle/libs.versions.toml`                |


## 模块边界

- `shared` — 领域模型、DeepSeek / OpenCode / Codex / NewAPI 客户端、刷新用例、平台设置接口与 Android 实现（`commonMain` / `androidMain`）。可复用逻辑放这里；不要依赖 Android UI / Glance / WorkManager。
- `androidApp` — `Application` 装配、Compose 设置页、Glance 小组件、Worker、Manifest / 资源 / ProGuard。

包结构按职责划分，例如：`domain`、`deepseek`、`settings`、`refresh`、`widget`、`worker`、`ui`。新增代码放入对应包，避免扁平堆叠。

## 架构约定

- **单向数据流**：设置与余额状态经 `PlatformSettingsRepository`；刷新经 `BalanceRefreshInteractor`；小组件经 Glance Preferences 镜像仓库状态（见 `WidgetGlanceState`）。
- **状态类型**：用 `sealed interface` / `data class` / `enum`（如 `WidgetDisplayState`），避免散落的布尔与裸字符串状态。
- **依赖装配**：当前在 `QuotaWidgetApp` 手动构造；未引入 Hilt/Koin，不要擅自加 DI 框架。
- **协程**：IO 放 `Dispatchers.IO`；用结构化作用域（`SupervisorJob`、Worker 协程）。禁止 `GlobalScope` 与生产路径上的 `runBlocking`。
- **平台期望**：`expect`/`actual` 仅用于必要的平台差异（如时间、HttpClient 引擎）。



## Kotlin / Compose 风格

遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)：

- 4 空格缩进；优先 `val`；公开 API 写清可空性。
- 行宽约 ≤120；Compose / Glance 的 `Modifier` 链每调用一行。
- Composable 尽量无状态，状态上提；Glance 布局保持轻量，避免复杂嵌套。
- 用户可见文案放 `res/values/strings.xml`（及必要的 locale），不要在 UI 里硬编码大段文案。
- 命名：类型 PascalCase，函数/属性 camelCase，常量 SCREAMING_SNAKE 或顶层 `const val`。
- 仅在非显而易见的公开 API 上写 KDoc；不写叙述性注释。



## 依赖管理

- **所有版本**集中在 `gradle/libs.versions.toml`；模块 `build.gradle.kts` 使用 `libs.`* / `projects.*`，禁止硬编码版本号。
- 新增依赖：先加 catalog 条目，再在模块引用；Compose 走 BOM。
- 升级时按相关组批量改动（如 AGP + Kotlin），并跑通 debug（触及 R8 时再跑 release）。



## 常用命令

```bash
# Debug APK
./gradlew :androidApp:assembleDebug

# Release（需签名环境变量，见 README）
./gradlew :androidApp:assembleRelease

# 本地可用 debug 签名打 release（仅开发验证 R8，勿发布）
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

Windows 可用 `.\gradlew.bat` 替代 `./gradlew`。

## 安全

- **禁止**提交 API Key、密码、`local.properties`、keystore、`*.jks` / `*.keystore`、secrets 明文。
- API Key 仅经加密设置存储（Tink + DataStore）；日志中不要打印完整 Key。
- Release 签名只用环境变量 / GitHub Secrets（`RELEASE_STORE_*` 等）；仓库内 keystore 文件已被 `.gitignore` 忽略，勿强制加入版本库。
- 修改网络、加密或 ProGuard 时，确认 release 仍可装配且关键路径可用。



## ProGuard / R8

- Release 已开启 minify + shrink resources（AGP 9 / R8 full mode）。
- 规则在 `androidApp/proguard-rules.pro`；改 reflection、序列化、Worker、Glance `ActionCallback`、Tink 后必须验证 `:androidApp:assembleRelease`。
- WorkManager / Room 的 no-arg 构造 keep 是已知 full-mode  workaround，删除前需有充分依据。



## Git：Conventional Commits

提交信息必须符合 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```



### Type


| Type       | 用途                                            |
| ---------- | --------------------------------------------- |
| `feat`     | 新功能                                           |
| `fix`      | Bug 修复                                        |
| `build`    | 构建系统 / 外部依赖（Gradle、AGP）                       |
| `ci`       | CI 配置与脚本                                      |
| `chore`    | 杂项（含 `chore(release): bump version to x.y.z`） |
| `docs`     | 仅文档                                           |
| `refactor` | 无行为变化的重构                                      |
| `perf`     | 性能                                            |
| `style`    | 格式（不影响含义）                                     |
| `test`     | 测试                                            |




### 规则

- **description**：祈使语气、现在时；英文小写开头、不加句号；简洁说明「为什么 / 结果」。
- **scope**（可选）：如 `widget`、`settings`、`release`、`deps`。
- **破坏性变更**：`feat!:` / `fix!:`，或 footer `BREAKING CHANGE:`。
- **一条提交一件事**；勿把无关重构与功能混在同一 commit。
- 仅在用户明确要求时创建 commit / push；不要擅自改 git config 或强推。



### 示例（与本仓库历史一致）

```
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
build: enable R8 minify and resource shrinking for release
ci: bump Actions to Node 24 compatible versions
chore(release): bump version to 1.1.2
docs: document release signing secrets
refactor(widget): extract glance state sync helper
```



## 版本与发布

- `versionName` / `versionCode` 默认在 `androidApp/build.gradle.kts`；CI 可用 `-PversionName` / `-PversionCode` 覆盖。
- 发版：`chore(release): bump version to x.y.z`，再打 tag `vX.Y.Z` 并 push；`.github/workflows/release.yml` 构建签名 APK 并创建 GitHub Release。
- 版本号语义化：patch = 修复，minor = 功能，major = 不兼容或重大改版。



## Agent 工作清单

**改之前**

- [ ] 确认改动落在正确模块与包
- [ ] 搜索现有抽象，避免重复实现
- [ ] 不扩大范围（无无关重构 / 无多余文档）

**改的过程中**

- [ ] 匹配周围代码风格与命名
- [ ] 用户可见字符串走资源；密钥不进仓库与日志
- [ ] 依赖只通过 version catalog 引入

**改之后**

- [ ] `./gradlew :androidApp:assembleDebug` 通过
- [ ] 触及 R8 / Worker / 序列化 / Tink 时验证 release 装配
- [ ] 行为或发布流程变化时同步 README / 本文件
- [ ] 若用户要求提交：信息符合 Conventional Commits



## 原则

- 清晰优于巧妙；增量改动优于大爆炸重写。
- 共享逻辑进 `shared`，Android 壳保持瘦。
- 桌面小组件路径要可靠：失败可降级展示，刷新指示器不能卡死。
- 架构方向不清或有数据丢失 / 安全风险时，先停下来说明风险，再改代码。

