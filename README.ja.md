<h1 align="center">クォータウィジェット · Quota Widget</h1>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.zh-Hans.md">简体中文</a> ·
  <a href="README.zh-Hant.md">繁體中文</a> ·
  <strong>日本語</strong> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  AI 関連サービスの API 残高およびサブスクリプション利用枠をホーム画面で一目で確認できる、オープンソースの Android アプリおよびウィジェット集です。
</p>

<p align="center">
  <a href="https://github.com/657kbps/quota-widget/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/657kbps/quota-widget?style=flat-square"></a>
  <a href="https://github.com/657kbps/quota-widget/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/657kbps/quota-widget/total?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <img src="docs/screenshots/app-home.png" alt="アプリのホーム画面" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-dark.png" alt="ダークテーマのウィジェット" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="ライトテーマのウィジェット" width="30%" />
</p>

## 主な機能

- **マルチプロバイダー対応**：DeepSeek、OpenCode Go、Codex、NewAPI、Magpie、Kimi Code、Zhipu Coding Plan、WorkBuddy、WorkBuddy インターナショナル、Cline Pass、Command Code、Antigravity のアカウント残高およびクォータ使用状況を一元管理できます。
- **柔軟なウィジェットレイアウト**：標準・コンパクト・概要の 3 種類のレイアウトに加え、使用量／残量の表示切替、プログレスバースタイル、リセット時刻の表示、アプリ内ライブプレビューに対応しています。
- **安定したバックグラウンド更新**：WorkManager により設定した間隔（15 分〜24 時間）で自動更新されるほか、ウィジェット上の更新ボタンから即座に手動更新できます。
- **認証情報の暗号化保存**：API キーやセッショントークンは Google Tink で暗号化したうえで DataStore Preferences に保存されます。
- **テーマのカスタマイズ**：ライト／ダーク／システムに従うモードに加え、ダイナミックシステムカラーまたはカスタムアクセントカラーを選択できます。
- **多言語対応**：日本語、English、简体中文、繁體中文、Deutsch、Français、Русский の 7 言語に対応しています。初期設定ではシステムの表示言語に従い（未対応言語の場合は英語にフォールバック）、設定画面から手動で切り替えることも可能です。
- **バックグラウンド動作の案内**：バッテリー最適化の無効化ガイドおよび [DontKillMyApp](https://dontkillmyapp.com/) に基づくメーカー別の設定案内を備えています。
- **アップデート確認**：アプリ起動時に GitHub Releases の最新バージョンを確認できるほか、設定画面からの手動確認にも対応しています。

## 対応プラットフォーム

<details>
<summary><strong>Antigravity</strong></summary>

- **表示内容**：5 時間・週間のモデルクォータ
- **認証方法**：アプリ内 Google OAuth ログイン（非公式連携）
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>Cline Pass</strong></summary>

- **表示内容**：5 時間・週間・月間の使用状況
- **認証方法**：アプリ内 WorkOS ログインまたは API キー
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>Codex</strong></summary>

- **表示内容**：週間・月間のサブスクリプションクォータ（使用量または残量を選択可能；概要では 5 時間・週間・月間を表示）
- **認証方法**：アプリ内 OAuth ログイン
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>Command Code</strong></summary>

- **表示内容**：5 時間・週間・月間の上限および月間クレジット
- **認証方法**：アプリ内 Studio ログインまたは API キー
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>DeepSeek</strong></summary>

- **表示内容**：API アカウント残高（CNY／USD の表示通貨を選択可能）
- **利用可能なウィジェット**：標準 · コンパクト

</details>

<details>
<summary><strong>Kimi Code</strong></summary>

- **表示内容**：5 時間／週間／月間のクォータ（プランに応じて取得；使用量または残量を選択可能）
- **設定項目**：API キーおよび中国サイト／グローバルサイトの切替
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>Magpie</strong></summary>

- **表示内容**：セルフホスト型 Magpie ゲートウェイにおける各プロバイダーのサブスクリプション利用枠（5 時間・週間・月間；同一プロバイダーの複数アカウントを合算表示；表示対象プロバイダーを選択可能、初期値はゲートウェイが最初に返すプロバイダー）
- **設定項目**：ゲートウェイ URL とゲートウェイ API キー
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>NewAPI</strong></summary>

- **表示内容**：トークン残高（USD）およびクォータの使用率／残量パーセンテージ
- **設定項目**：Base URL、API キー、クォータ換算レート
- **利用可能なウィジェット**：残高（標準） · 残高（コンパクト） · 使用状況

</details>

<details>
<summary><strong>OpenCode Go</strong></summary>

- **表示内容**：ローリング／週間／月間のクォータ（使用量または残量を選択可能）
- **認証方法**：アプリ内 Web ログイン（ワークスペース選択対応）または API キー
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>WorkBuddy</strong></summary>

- **表示内容**：月間クレジット利用枠
- **認証方法**：アプリ内ブラウザログイン
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>WorkBuddy インターナショナル</strong></summary>

- **表示内容**：月間クレジット利用枠（インターナショナル版）
- **認証方法**：アプリ内ブラウザログイン
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

<details>
<summary><strong>Zhipu Coding Plan</strong></summary>

- **表示内容**：5 時間・週間のクォータ（使用量または残量を選択可能）
- **設定項目**：API キーおよび中国サイト／グローバルサイトの切替
- **利用可能なウィジェット**：標準 · コンパクト · 概要

</details>

> **アプリ内ログインについて**
> OpenCode Go、Codex、WorkBuddy、WorkBuddy インターナショナル、Cline Pass、Command Code、Antigravity のブラウザ認証はアプリ内の WebView で完結します。認証後のコールバックは自動的に取得されるため、コールバック URL を手動でコピーする必要はありません。

> **Antigravity に関するご注意**
> Antigravity は非公式 API による連携であり、Google 側で関連アカウントに対して制限やリスク判定が行われる場合があります。あらかじめ公式の Antigravity デスクトップクライアントでログインおよびプロジェクトの初期化を済ませたうえでご利用いただき、リスクはご自身の判断で評価してください。

## インストールとクイックスタート

### ダウンロード

[GitHub Releases](https://github.com/657kbps/quota-widget/releases/latest) から最新の署名済み APK をダウンロードしてインストールしてください。

- **リリース版パッケージ名**：`com.kuyermqi.quotawidget`
- **対応 Android バージョン**：Android 8.0（API レベル 26）以上

### 初期設定

1. **クォータウィジェット**をインストールして起動し、案内に従ってバッテリー最適化を無効化してください（バックグラウンド定期更新の安定動作のため強く推奨されます）。
2. ホーム画面から設定したいプラットフォームを選択し、API キーの入力またはアカウントログインを行い、表示オプションを調整して保存します。
3. ホーム画面の空き領域を長押しし、**ウィジェット**一覧から目的のウィジェットを配置します。
4. ウィジェット上の更新アイコンをタップすると、即座に最新データへ更新されます。

> 一部の端末メーカー製カスタム ROM では、標準のバッテリー最適化を無効にしてもバックグラウンド処理が制限される場合があります。自動起動やバックグラウンド実行の許可手順については、[DontKillMyApp](https://dontkillmyapp.com/) の機種別ガイドをご参照ください。

## ソースからのビルド

### Debug ビルド

```bash
./gradlew :androidApp:assembleDebug
```

生成された APK は `androidApp/build/outputs/apk/debug/` に出力されます。

Debug 版のパッケージ名は `com.kuyermqi.quotawidget.debug`（ランチャー表示名：**Quota Widget Debug**）となっており、リリース版と共存してインストールできます。

### ローカルでの Release 検証（R8）

デバッグ用キーストアを用いて R8 の難読化およびリソース圧縮をローカル検証する場合は、以下のコマンドを実行します（検証専用であり、配布には使用しないでください）。

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### モジュール構成と技術スタック

| モジュール | 役割 |
|------------|------|
| `shared` | Kotlin Multiplatform（KMP）共通モジュール。ドメインモデル、各プロバイダーの API クライアント、更新ユースケース、暗号化設定リポジトリを提供 |
| `androidApp` | Jetpack Compose UI、アプリ内 WebView ログインフロー、Glance AppWidget、WorkManager ワーカー、Android リソース |

**主要技術**：Kotlin (KMP) · Jetpack Compose · Glance AppWidget · Ktor · kotlinx.serialization · DataStore · Tink · WorkManager · Material Kolor

## リリース署名と公開手順

本番配布用の APK には、デバッグ用ではなく専用のリリースキーストアで署名する必要があります。

1. **キーストアの作成**（初回のみ作成し、ファイルとパスワードを厳重に保管してください）：

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias quota-widget \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **GitHub Actions Secrets の登録**（*Settings → Secrets and variables → Actions*）：

   | Secret 名 | 内容 |
   |-----------|------|
   | `RELEASE_KEYSTORE_BASE64` | Base64 エンコード済みキーストア：Linux/macOS では `base64 -w0 release.keystore`、PowerShell では `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` を実行 |
   | `RELEASE_STORE_PASSWORD` | キーストアのパスワード |
   | `RELEASE_KEY_ALIAS` | キーエイリアス（例：`quota-widget`） |
   | `RELEASE_KEY_PASSWORD` | キーのパスワード |

3. **バージョンタグのプッシュによるリリース公開**：

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

ローカル環境で署名済みリリース APK をビルドする場合は、以下の環境変数を設定してください。

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## コントリビューション

Issue の報告や Pull Request の提出を歓迎します。作業を始める前に [AGENTS.md](AGENTS.md) を確認し、コーディング規約・設計方針・モジュール境界に従ってください。

コミットメッセージは [Conventional Commits](https://www.conventionalcommits.org/) に準拠する必要があります（例）：

```text
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## 謝辞

- [DontKillMyApp](https://dontkillmyapp.com/) — 各端末メーカーのバックグラウンド動作維持ガイド
- [LINUX DO](https://linux.do/) — コミュニティでの議論とフィードバック
- DeepSeek、OpenCode をはじめとする各対応プラットフォームの公開 API とサービス

---

<p align="center">
  このプロジェクトが役に立ちましたら、ぜひ Star をお寄せください ⭐
</p>
