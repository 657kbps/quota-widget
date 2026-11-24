<h1 align="center">Quota Widget</h1>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.zh-Hans.md">简体中文</a> ·
  <a href="README.zh-Hant.md">繁體中文</a> ·
  <a href="README.ja.md">日本語</a> ·
  <strong>Deutsch</strong> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  Eine Open-Source-Android-App mit Startbildschirm-Widgets zur übersichtlichen Überwachung von KI-API-Guthaben und Abonnement-Kontingenten.
</p>

<p align="center">
  <a href="https://github.com/657kbps/quota-widget/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/657kbps/quota-widget?style=flat-square"></a>
  <a href="https://github.com/657kbps/quota-widget/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/657kbps/quota-widget/total?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <img src="docs/screenshots/app-home.png" alt="Startbildschirm der App" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-dark.png" alt="Widgets im dunklen Design" width="30%" />
  &nbsp;
  <img src="docs/screenshots/widgets-light.png" alt="Widgets im hellen Design" width="30%" />
</p>

## Funktionsumfang

- **Anbieterübergreifende Überwachung**: Verfolgen Sie Kontoguthaben und Nutzungszeiträume für DeepSeek, OpenCode Go, Codex, NewAPI, Magpie, Kimi Code, Zhipu Coding Plan, WorkBuddy, WorkBuddy International, Cline Pass, Command Code und Antigravity.
- **Flexible Widget-Layouts**: Wählen Sie zwischen den Layouts Standard, Kompakt und Übersicht – mit konfigurierbarer Anzeige von verbrauchten oder verbleibenden Werten, Fortschrittsbalken-Stilen, Rücksetzzeiten und Live-Vorschau in der App.
- **Zuverlässige Hintergrundaktualisierung**: Automatische Aktualisierung über WorkManager in konfigurierbaren Intervallen (15 Minuten bis 24 Stunden) sowie sofortige manuelle Aktualisierung direkt per Tippen auf das Widget.
- **Verschlüsselte Zugangsdatenspeicherung**: API-Schlüssel und Sitzungs-Tokens werden vor der Speicherung in DataStore Preferences mit Google Tink verschlüsselt.
- **Anpassbares Design**: Unterstützt die Modi Hell, Dunkel und Systemeinstellung folgen sowie dynamische Systemfarben oder eine benutzerdefinierte Akzentfarbe.
- **Mehrsprachige Benutzeroberfläche**: Verfügbar auf Deutsch, English, 简体中文, 繁體中文, 日本語, Français und Русский. Standardmäßig wird die Systemsprache verwendet (mit Englisch als Fallback); ein manueller Wechsel ist jederzeit in den Einstellungen möglich.
- **Hinweise zur Hintergrundausführung**: Enthält Hinweise zum Deaktivieren der Akku-Optimierung sowie herstellerspezifische Anleitungen über [DontKillMyApp](https://dontkillmyapp.com/).
- **Update-Prüfung**: Optionale Überprüfung auf neue GitHub Releases beim App-Start sowie manuelle Prüfung in den Einstellungen.

## Unterstützte Anbieter

<details>
<summary><strong>Antigravity</strong></summary>

- **Angezeigte Daten**: 5-Stunden- und Wochen-Modellkontingente
- **Anmeldung**: Google-OAuth-Anmeldung in der App (inoffizielle Integration)
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>Cline Pass</strong></summary>

- **Angezeigte Daten**: 5-Stunden-, Wochen- und Monatsnutzung
- **Anmeldung**: WorkOS-Anmeldung in der App oder API-Schlüssel
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>Codex</strong></summary>

- **Angezeigte Daten**: Wöchentliches und monatliches Abonnement-Kontingent (verbraucht oder verbleibend; 5 Stunden, wöchentlich und monatlich in der Übersicht)
- **Anmeldung**: OAuth-Anmeldung in der App
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>Command Code</strong></summary>

- **Angezeigte Daten**: 5-Stunden-, Wochen- und Monatslimits sowie monatliche Credits
- **Anmeldung**: Studio-Anmeldung in der App oder API-Schlüssel
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>DeepSeek</strong></summary>

- **Angezeigte Daten**: API-Kontoguthaben (konfigurierbare Anzeigewährung CNY / USD)
- **Verfügbare Widgets**: Standard · Kompakt

</details>

<details>
<summary><strong>Kimi Code</strong></summary>

- **Angezeigte Daten**: 5-Stunden-, Wochen- und Monatskontingent (tarifabhängig; verbraucht oder verbleibend)
- **Konfiguration**: API-Schlüssel sowie Auswahl zwischen China- und Global-Standort
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>Magpie</strong></summary>

- **Angezeigte Daten**: Abonnement-Kontingente je Anbieter für ein selbst gehostetes Magpie-Gateway (5 Stunden, wöchentlich und monatlich; fasst mehrere Konten desselben Anbieters zusammen; Anbieter wählbar, standardmäßig der erste zurückgegebene)
- **Konfiguration**: Gateway-URL und Gateway-API-Schlüssel
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>NewAPI</strong></summary>

- **Angezeigte Daten**: Token-Guthaben (USD) und Verbrauchs-/Restprozentsatz
- **Konfiguration**: Basis-URL, API-Schlüssel und Kontingent-Umrechnungsfaktor
- **Verfügbare Widgets**: Guthaben (Standard) · Guthaben (Kompakt) · Nutzung

</details>

<details>
<summary><strong>OpenCode Go</strong></summary>

- **Angezeigte Daten**: Rollierendes, wöchentliches und monatliches Kontingent (verbraucht oder verbleibend)
- **Anmeldung**: Web-Anmeldung in der App (mit Workspace-Auswahl) oder API-Schlüssel
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>WorkBuddy</strong></summary>

- **Angezeigte Daten**: Monatliches Credit-Kontingent
- **Anmeldung**: Browser-Anmeldung in der App
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>WorkBuddy International</strong></summary>

- **Angezeigte Daten**: Monatliches Credit-Kontingent (internationale Version)
- **Anmeldung**: Browser-Anmeldung in der App
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

<details>
<summary><strong>Zhipu Coding Plan</strong></summary>

- **Angezeigte Daten**: 5-Stunden- und Wochenkontingent (verbraucht oder verbleibend)
- **Konfiguration**: API-Schlüssel sowie Auswahl zwischen China- und Global-Standort
- **Verfügbare Widgets**: Standard · Kompakt · Übersicht

</details>

> **Anmeldung innerhalb der App**
> Browserbasierte Anmeldevorgänge (OpenCode Go, Codex, WorkBuddy, WorkBuddy International, Cline Pass, Command Code und Antigravity) laufen vollständig in einer integrierten WebView ab. Autorisierungs-Callbacks werden automatisch erfasst, sodass keine Callback-URLs manuell kopiert werden müssen.

> **Hinweis zu Antigravity**
> Antigravity basiert auf einer inoffiziellen Schnittstelle. Google kann zugehörige Konten überprüfen oder einschränken. Melden Sie sich idealerweise zunächst im offiziellen Antigravity-Desktop-Client an und richten Sie dort ein Projekt ein, bevor Sie die Anmeldung in dieser App durchführen, und wägen Sie das Risiko eigenverantwortlich ab.

## Installation & Schnelleinstieg

### Download

Laden Sie die aktuelle signierte APK-Datei aus den [GitHub Releases](https://github.com/657kbps/quota-widget/releases/latest) herunter.

- **Paket-ID (Release)**: `com.kuyermqi.quotawidget`
- **Erforderliche Android-Version**: Android 8.0 (API-Level 26) oder höher

### Einrichtung

1. Installieren und öffnen Sie **Quota Widget** und deaktivieren Sie bei entsprechender Aufforderung die Akku-Optimierung, damit Hintergrundaktualisierungen zuverlässig ausgeführt werden.
2. Wählen Sie auf dem Startbildschirm der App einen Anbieter aus, hinterlegen Sie Ihre Zugangsdaten oder schließen Sie die Anmeldung ab, passen Sie bei Bedarf die Darstellungsoptionen an und speichern Sie.
3. Halten Sie eine freie Stelle auf dem Startbildschirm gedrückt, öffnen Sie **Widgets** und fügen Sie das gewünschte Quota-Widget-Layout hinzu.
4. Tippen Sie auf das Aktualisieren-Symbol eines Widgets, um jederzeit manuell neue Daten abzurufen.

> Einige herstellerspezifische Android-Oberflächen schränken Hintergrundprozesse selbst nach dem Deaktivieren der regulären Akku-Optimierung weiter ein. Auf [DontKillMyApp](https://dontkillmyapp.com/) finden Sie gerätespezifische Hinweise zu Autostart- und Hintergrundberechtigungen.

## Aus dem Quellcode erstellen

### Debug-Build

```bash
./gradlew :androidApp:assembleDebug
```

Die erzeugte APK-Datei wird unter `androidApp/build/outputs/apk/debug/` abgelegt.

Die Debug-Variante verwendet die Paket-ID `com.kuyermqi.quotawidget.debug` (App-Name im Launcher: **Quota Widget Debug**) und kann parallel zur Release-Version installiert werden.

### Lokale Release-Prüfung (R8)

Um R8-Code-Minimierung und Ressourcenbereinigung lokal mit dem Debug-Keystore zu überprüfen (nur für Tests; nicht zur Weitergabe bestimmt):

```bash
./gradlew :androidApp:assembleRelease -PallowDebugReleaseSigning=true
```

### Architektur & Technologie-Stack

| Modul | Aufgabenbereich |
|-------|-----------------|
| `shared` | Gemeinsame Kotlin-Multiplatform-Schicht (KMP) mit Domänenmodellen, Anbieter-API-Clients, Aktualisierungslogik und verschlüsseltem Einstellungs-Repository |
| `androidApp` | Jetpack-Compose-Oberfläche, integrierte WebView-Anmeldeflows, Glance-AppWidgets, WorkManager-Worker und Android-Ressourcen |

**Verwendete Technologien**: Kotlin (KMP) · Jetpack Compose · Glance AppWidget · Ktor · kotlinx.serialization · DataStore · Tink · WorkManager · Material Kolor

## Release-Signierung & Veröffentlichung

Produktive APKs müssen mit einem dedizierten Release-Keystore signiert werden (niemals mit dem Debug-Keystore).

1. **Keystore erstellen** (einmaliger Vorgang; Datei und Zugangsdaten sicher aufbewahren):

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias quota-widget \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **GitHub-Actions-Secrets konfigurieren** (*Settings → Secrets and variables → Actions*):

   | Secret | Beschreibung |
   |--------|--------------|
   | `RELEASE_KEYSTORE_BASE64` | Base64-kodierter Keystore: `base64 -w0 release.keystore` (Linux/macOS) bzw. `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` (PowerShell) |
   | `RELEASE_STORE_PASSWORD` | Keystore-Passwort |
   | `RELEASE_KEY_ALIAS` | Schlüssel-Alias (z. B. `quota-widget`) |
   | `RELEASE_KEY_PASSWORD` | Schlüssel-Passwort |

3. **Release veröffentlichen** durch Pushen eines Versions-Tags:

   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

Für lokale signierte Release-Builds setzen Sie dieselben Werte als Umgebungsvariablen:

```bash
export RELEASE_STORE_FILE=/path/to/release.keystore
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=quota-widget
export RELEASE_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

## Mitwirken

Issues und Pull Requests sind herzlich willkommen. Bitte lesen Sie vorab [AGENTS.md](AGENTS.md), um sich mit den Code-Konventionen, Architekturvorgaben und Modulgrenzen vertraut zu machen.

Commit-Nachrichten müssen den [Conventional Commits](https://www.conventionalcommits.org/) entsprechen, beispielsweise:

```text
feat: add 2x1 compact DeepSeek balance widget
fix: keep WorkManager Room constructors under R8 full mode
```

## Danksagung

- [DontKillMyApp](https://dontkillmyapp.com/) — Herstellerspezifische Anleitungen zur Hintergrundausführung
- Die offenen APIs und Dienste von DeepSeek, OpenCode und den weiteren unterstützten Plattformen

---

<p align="center">
  Wenn Ihnen dieses Projekt weiterhilft, freuen wir uns über einen Star ⭐
</p>
