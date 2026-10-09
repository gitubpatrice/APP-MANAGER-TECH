# Datenschutzerklärung — App Manager Tech

_Übersetzung der Fassung vom 9. Oktober 2026._ · 🇬🇧 [English](PRIVACY.md) · 🇫🇷 [Français](PRIVACY.fr.md) · 🇮🇹 [Italiano](PRIVACY.it.md) · 🇪🇸 [Español](PRIVACY.es.md)

> Diese Übersetzung wurde vom Entwickler maschinengestützt erstellt und noch nicht von einer
> Muttersprachlerin oder einem Muttersprachler geprüft. **Bei Abweichungen gilt die
> [französische Fassung](PRIVACY.fr.md).**

App Manager Tech (`com.filestech.appmanager`) ist ein **vollständig lokaler** App-Manager: Er untersucht die auf Ihrem Telefon installierten Apps unmittelbar auf diesem Gerät und übermittelt nichts nach außen.

## Kurz gesagt

- **Keine Datenerhebung, keine Datenübertragung.** Die App deklariert die Berechtigung `INTERNET`
  nicht: Sie ist technisch nicht in der Lage, irgendetwas über ein Netzwerk zu senden. Eine
  automatische Prüfung kontrolliert dies am veröffentlichten APK selbst, bei jedem Lauf der
  kontinuierlichen Integration.
- **Kein Konto, keine Registrierung, keine Kennung.**
- **Keine Werbung, keine Tracker, keine Analysewerkzeuge**, kein Versand von Absturzberichten.
- **Keine Cloud-Sicherung**: `allowBackup=false`, und sowohl die Datenbank als auch die
  Einstellungen sind von den automatischen Sicherungen von Android und von der Übertragung zwischen
  Geräten ausgeschlossen.

## Welche Daten, und wo

Die App liest, was Android über die installierten Apps bereits weiß, und bewahrt eine Kopie davon
in ihrem **privaten Speicher** auf, auf den keine andere App zugreifen kann. Diese Datenbank ist
nicht verschlüsselt: Sie beschreibt Ihre Apps, nicht Ihre Inhalte.

| Daten | Woher sie stammen | Wie lange |
|---|---|---|
| Liste der Apps: Name, Version, Größen, Datum der Installation, der Aktualisierung und der letzten Nutzung, Herkunfts-Store, Zustand (aktiviert, im Ruhezustand) | Android (`PackageManager`, `StorageStatsManager`, `UsageStatsManager`) | Bei jedem Scan ersetzt |
| Erkannte Tracker, Datenschutz-Score | Auf dem Telefon berechnet (siehe unten) | Bei jeder Anzeige neu berechnet |
| Verlauf der Berechtigungen, die jeder App erteilt wurden — **standardmäßig deaktiviert** | Regelmäßige Erfassung, wenn Sie den Verlauf aktivieren | Standardmäßig 90 Tage, einstellbar von 7 bis 365 |
| Verlauf der Installationen, Updates und Deinstallationen, mit dem SHA-256-Fingerabdruck jedes APK und dem Deinstallationsgrund, den Sie freiwillig angeben — **standardmäßig deaktiviert** | Meldungen von Android, die eingehen, während die App läuft, wenn Sie den Verlauf aktivieren | Standardmäßig 180 Tage, einstellbar von 30 bis 365 |
| Protokoll der Aktionen, die in App Manager Tech ausgelöst wurden — **standardmäßig deaktiviert** | Ihre Aktionen, wenn Sie das Protokoll aktivieren | Standardmäßig 180 Tage, einstellbar von 30 bis 365 |
| Papierkorb und Quarantänen (mit dem SHA-256-Fingerabdruck jedes gesicherten APK) | Ihre Aktionen | Bis Sie sie leeren oder wiederherstellen |
| Einstellungen: Design, Schwellenwerte, ausgeschlossene oder geschützte Apps, Tags, und die Zugriffsberechtigung für den von Ihnen gewählten Sicherungsordner | Sie | Bis zur Deinstallation |

**Tracker** werden erkannt, indem die Namen der Komponenten, die jede App gegenüber Android
deklariert, mit einer in App Manager Tech integrierten Liste verglichen werden, die aus der
öffentlichen Datenbank von Exodus Privacy stammt. Diese Liste wird mit der App aktualisiert; sie
wird nie heruntergeladen.

Wenn Sie App Manager Tech deinstallieren oder die Daten der App in den Android-Einstellungen
löschen, wird alles oben Genannte gelöscht. Dateien, die **Sie** an anderer Stelle haben schreiben
lassen (siehe „Weitergabe“), bleiben dort, wo Sie sie abgelegt haben.

Der Entwickler hat **keinen Zugriff** auf diese Daten und erhält **keine Kopie** davon.

## Angeforderte Berechtigungen, und wozu

Diese Liste ist **vollständig**: Es handelt sich um die zwölf Berechtigungen, die im veröffentlichten APK deklariert und in dessen Manifest aufgeführt sind. Sie umfasst daher auch die, die keine Zeile
unseres Codes anfordert, sondern die eine Bibliothek mitgebracht hat. Eine automatische Prüfung
lehnt jeden Build ab, dessen APK von dieser Liste abweichen würde
(`tools/check-manifest-permissions.py`, bei jedem Lauf der kontinuierlichen Integration
ausgeführt).

### Von der App deklariert

| Berechtigung | Zweck | Netzwerk? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Alle installierten Apps sehen: Das ist die eigentliche Funktion der App. | Nein |
| `PACKAGE_USAGE_STATS` | Spezieller Zugriff „Zugriff auf Nutzungsdaten“, den Sie selbst in den Android-Einstellungen erteilen. Er liefert die Größen und das Datum der letzten Nutzung jeder App. Wird der Zugriff verweigert, werden die Größen mit 0 und die Apps als „Nie verwendet“ angezeigt; alles andere funktioniert weiter. | Nein |
| `GET_PACKAGE_SIZE` | Die Größe der Apps lesen. | Nein |
| `REQUEST_DELETE_PACKAGES` | Das Deinstallationsfenster von Android öffnen. Android selbst fragt nach der Bestätigung und deinstalliert. | Nein |
| `REQUEST_INSTALL_PACKAGES` | Eine unter Quarantäne gestellte App wiederherstellen: das gesicherte APK an das Installationsprogramm von Android übergeben. Android verlangt zusätzlich, dass Sie App Manager Tech selbst erlauben, Apps zu installieren, und fragt bei jeder Installation nach einer Bestätigung. Sie dient zu nichts anderem. | Nein |
| `KILL_BACKGROUND_PROCESSES` | Die Hintergrundprozesse einer App beenden, auf Ihren Wunsch. | Nein |
| `POST_NOTIFICATIONS` | Drei optionale Benachrichtigungen: Cache-Schwellenwert erreicht, Berechtigungen geändert, Ende einer Quarantäne. Wird angefordert, wenn Sie eine davon aktivieren (Android 13 und höher), und kann verweigert werden. | Nein |

### Von den verwendeten Bibliotheken mitgebracht

| Berechtigung | Stammt aus | Wozu sie hier **tatsächlich** dient | Netzwerk? |
|---|---|---|---|
| `WAKE_LOCK` | `androidx.work` | Wird kurz gehalten, während eine Hintergrundaufgabe läuft: automatischer Scan, Erfassung der Berechtigungen, Bereinigung der Verläufe, Quarantäne-Erinnerung — jene, die Sie aktiviert haben. | Nein |
| `RECEIVE_BOOT_COMPLETED` | `androidx.work` | Diese Aufgaben nach einem Neustart wieder einplanen. | Nein |
| `FOREGROUND_SERVICE` | `androidx.work` | **Nichts.** `androidx.work` deklariert sie für Aufgaben vom Typ „expedited“; die App plant keine solche ein. | Nein |
| `ACCESS_NETWORK_STATE` | `androidx.work` | **Nichts.** `androidx.work` deklariert sie für Aufgaben, die auf ein Netzwerk warten; alle Aufgaben der App werden ohne Netzwerkbedingung eingeplant. Sie würde nur erkennen lassen, ob ein Netzwerk vorhanden ist: Ohne `INTERNET` ist es unmöglich, es zu nutzen. | Nein |
| `com.filestech.appmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | Eine **selbst erteilte** Berechtigung auf Signaturebene: Nur eine mit unserem Schlüssel signierte App kann sie erhalten. Sie verhindert, dass andere Apps den Empfänger ansprechen, den die App registriert, um die Installationen zu verfolgen. | Nein |

Die App fordert **nie** Zugriff auf das Internet, den Standort, die Kontakte, SMS, den Kalender, das
Mikrofon, die Kamera oder Ihre Dateien an (`MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`).

## Weitergabe an Dritte

**Keine.** Es werden keine Daten an irgendjemanden weitergegeben, verkauft oder übertragen — die
App hat dazu keine technischen Mittel.

Die einzigen möglichen Übergaben sind die, die **Sie** auslösen, und sie bleiben auf Ihrem Telefon:

- **Export (JSON, CSV, PDF)**: ein Bericht über Ihre Apps, geschrieben an den Ort, den Sie in der
  Dateiauswahl von Android wählen. Er verrät, welche Apps auf Ihrem Telefon installiert sind; was
  danach mit der Datei geschieht, hängt allein von Ihnen ab, auch wenn Sie sie in einem Ordner
  speichern, der mit einer Cloud synchronisiert wird.
- **Quarantäne mit Sicherung**: Die App kopiert die Installationsdatei (APK) einer App in den von
  Ihnen gewählten Ordner, bevor diese deinstalliert wird. Ein APK enthält die App, **nie ihre
  Daten**. Dieser Ordner wird nicht gelöscht, wenn Sie App Manager Tech deinstallieren.
- **Webseiten** („Nach Updates suchen“, Quellcode, ein Problem melden, Lizenz, diese Erklärung):
  Die App bittet den Browser Ihres Telefons, eine feste Adresse zu öffnen. Die Verbindung stellt
  der Browser selbst her, und dafür gilt seine eigene Datenschutzerklärung; App Manager Tech
  übermittelt ihm nur diese Adresse.
- **Android-Bildschirme** (Deinstallieren, „App-Info“-Seite einer App, Einstellungen): Die App
  übergibt ihnen den Namen des betreffenden Pakets und sonst nichts.

## Logs

Die veröffentlichten Versionen schreiben **keine einzige Log-Zeile**: Jede Meldung wird verworfen,
bevor sie geschrieben wird. Nur Entwicklungsversionen schreiben welche, und diese sind
ausschließlich über ADB sichtbar.

## Ihre Rechte (DSGVO)

Da die App keine personenbezogenen Daten außerhalb Ihres Geräts verarbeitet, gibt es keine solche Verarbeitung, hinsichtlich deren Sie Ihre Rechte auf Auskunft, Berichtigung oder Löschung ausüben könnten. Sie behalten
die volle Kontrolle: Das Deaktivieren eines Verlaufs, das Leeren des Papierkorbs oder das
Deinstallieren der App löscht die entsprechenden Daten vom Gerät. Der Export gibt Ihnen eine
lesbare Kopie dessen, was die App über Ihre Apps weiß.

## Kinder

Die App erhebt keine Daten und ist für alle Altersgruppen geeignet.

## Änderungen

Diese Erklärung kann sich mit der App weiterentwickeln; das Datum oben im Dokument gibt die letzte
Überarbeitung an, und die Historie ist in diesem Repository öffentlich. In der Fassung vom
23. Mai 2026 fehlten die Verläufe und ihre Aufbewahrungsfristen, die außerhalb der App
geschriebenen Dateien und die Hälfte der Berechtigungen des APK. Die Fassung vom 9. Oktober 2026
fügt `REQUEST_INSTALL_PACKAGES` hinzu, ohne die Android jede Wiederherstellung aus einer Quarantäne
verweigerte.

## Herausgeber und Kontakt

App Manager Tech wird von **Patrice Haltaya** (Frankreich) herausgegeben, dem Verantwortlichen im
Sinne der DSGVO — auch wenn, wie oben erklärt, nie Daten bei ihm ankommen. Kontakt:
**contact@files-tech.com**.

Fragen oder Meldungen: Eröffnen Sie ein [Issue](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
im Repository, oder erreichen Sie uns über [files-tech.com](https://files-tech.com). Zur Sicherheit
siehe [SECURITY.md](SECURITY.md).
