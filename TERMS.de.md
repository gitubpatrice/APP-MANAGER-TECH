# Nutzungsbedingungen — App Manager Tech

_Übersetzung der Fassung vom 9. Oktober 2026._ · 🇬🇧 [English](TERMS.md) · 🇫🇷 [Français](TERMS.fr.md) · 🇮🇹 [Italiano](TERMS.it.md) · 🇪🇸 [Español](TERMS.es.md)

> Diese Übersetzung wurde vom Entwickler maschinengestützt erstellt und noch nicht von einer
> Muttersprachlerin oder einem Muttersprachler geprüft. **Bei Abweichungen gilt die
> [französische Fassung](TERMS.fr.md).**

## Herausgeber

App Manager Tech (`com.filestech.appmanager`) gehört zur **Files Tech**-Reihe, herausgegeben von
**Patrice Haltaya**. Kontakt: **contact@files-tech.com**.

## Lizenz

App Manager Tech ist freie Software, veröffentlicht unter der **Apache License 2.0**. Sie dürfen sie
unter den Bedingungen dieser Lizenz nutzen, verändern und weitergeben. Der vollständige Text steht
in der Datei `LICENSE` des Quellcode-Repositorys (https://github.com/gitubpatrice/APP-MANAGER-TECH).
Maßgeblich ist allein der englische Text der Lizenz.

## Nutzung

Die App wird **kostenlos** zur Verfügung gestellt und **wie besehen, ohne Gewährleistung jeglicher
Art** bereitgestellt (Apache License 2.0, Abschnitt 7). Sie hilft Ihnen, die auf Ihrem Telefon
installierten Apps zu untersuchen und Aktionen an ihnen auszuführen; die Entscheidungen, die Sie auf
dieser Grundlage treffen — deinstallieren, deaktivieren, unter Quarantäne stellen —, bleiben Ihre
eigenen.

Mit der Nutzung bestätigen Sie, dass Sie Eigentümer des Telefons oder zu seiner Verwaltung
berechtigt sind. Das Kopieren der Installationsdatei (APK) einer App gibt Ihnen keinerlei Rechte an
dieser App: Verbreiten Sie das APK nicht weiter, wenn die Lizenz der App dies verbietet.

## Einschränkungen

- **Aktionen laufen über Android.** App Manager Tech deinstalliert und deaktiviert selbst keine App
  und leert selbst keinen App-Cache: Die App öffnet das Deinstallationsfenster von Android oder die
  „App-Info“-Seite der App in den Android-Einstellungen, und Android selbst fragt nach der
  Bestätigung und führt die Aktion aus. App Manager Tech umgeht diese Bestätigungen nie.
- **„Beenden erzwingen“ beendet nur Hintergrundprozesse.** Android erlaubt einer App nicht, eine
  andere zu beenden, die sichtbar ist oder einen Vordergrunddienst ausführt.
- **Die Tracker-Erkennung ist kein Sicherheitsaudit.** Sie vergleicht die Komponenten, die jede App
  deklariert, mit einer Liste, die zum Datum der jeweiligen Version festgeschrieben ist: Sie kann
  einen Tracker übersehen, der in dieser Liste fehlt, umbenannt oder anders eingebunden ist, und
  einen melden, der zwar vorhanden ist, aber nie verwendet wird.
- **Der Datenschutz-Score ist eine Schätzung.** Er wird aus den deklarierten Berechtigungen,
  bestimmten speziellen Zugriffen und der Installationsquelle berechnet. Er misst den Umfang der
  Zugriffe einer App, nicht ihre Absichten: Ein niedriger Score bedeutet nicht, dass es sich um
  Schadsoftware handelt, ein hoher Score garantiert nichts.
- **Die Warnung „kritische App“ ist nicht vollständig.** Vor einer Aktion an einer App, die sie als
  kritisch erkennt oder die Sie geschützt haben, verlangt die App ein drei Sekunden langes
  Gedrückthalten. Die App erkennt nicht alle kritischen Apps: Das Deaktivieren oder Deinstallieren
  einer System-App kann das Telefon instabil machen.
- Ohne den „Zugriff auf Nutzungsdaten“ werden die Größen mit 0 und die Apps als „Nie verwendet“ angezeigt. Android kann Benachrichtigungen verzögern oder blockieren; die App kann ihre Zustellung
  nicht garantieren.

## Datenverlust

- **Das Deinstallieren einer App oder das Löschen ihrer Daten ist unumkehrbar.** Android selbst
  führt dies aus, nach seiner eigenen Bestätigungsabfrage; App Manager Tech hat keine Möglichkeit,
  es rückgängig zu machen.
- **Der Papierkorb deinstalliert nichts**, solange Sie ihn nicht leeren: Das Leeren startet die
  Deinstallation jeder App, die er enthält.
- **Eine Quarantäne mit Sicherung bewahrt nur das APK auf.** Die Daten der App — Konten,
  Einstellungen, Dateien — gehen bei ihrer Deinstallation verloren, und die Wiederherstellung
  installiert die App ohne Daten neu. Die Wiederherstellung läuft über das Installationsprogramm von
  Android, das Sie um Ihre Zustimmung bittet und beim ersten Mal um die Erlaubnis, Apps über
  App Manager Tech zu installieren.
  Für eine App aus mehreren APK-Dateien (App Bundle) und für eine System-App wird die Quarantäne
  mit Sicherung nicht angeboten: Eine Sicherung allein ihrer Hauptdatei könnte sie nicht neu installieren.
- **Eine veränderte Sicherung wird nie wiederhergestellt.** Beim Sichern speichert App Manager Tech
  den SHA-256-Fingerabdruck der Datei; beim Wiederherstellen kopiert es die Datei erneut in seinen
  privaten Speicher, prüft, dass die Kopie genau diesen Fingerabdruck hat, und installiert diese
  geprüfte Kopie, andernfalls lehnt es ab. Eine Sicherung aus einer Version vor 0.5.1 hat keinen
  Fingerabdruck, kann nicht geprüft werden und wird daher von der App nicht wiederhergestellt: Die
  Datei bleibt in Ihrem Ordner.
- Wenn Sie App Manager Tech deinstallieren, werden die Verläufe, der Papierkorb und die Quarantänen
  der App gelöscht. Die gesicherten APKs und die Exporte bleiben in den Ordnern, in die Sie sie
  gelegt haben.

## Daten

Alles, was die App speichert, bleibt **auf Ihrem Telefon** — siehe die
[Datenschutzerklärung](PRIVACY.de.md). App Manager Tech sendet nichts über das Internet und hat
keine technische Berechtigung dazu (keine Android-Berechtigung `INTERNET`). Aus demselben Grund
kann niemand die App aus der Ferne deaktivieren oder von Ihrem Telefon entfernen.

## Updates

Updates werden im offiziellen GitHub-Repository veröffentlicht, signiert mit demselben Schlüssel wie
jede vorherige Version. Die App aktualisiert sich nie selbst und prüft nie, ob es ein Update gibt:
Die Schaltfläche „Nach Updates suchen“ öffnet die Seite der Releases in Ihrem Browser, und eine neue
Version zu installieren liegt bei Ihnen.

## Haftung

Soweit gesetzlich zulässig, haftet der Herausgeber nicht für direkte oder indirekte Schäden, die aus
der Nutzung der App entstehen (Apache License 2.0, Abschnitt 8). Insbesondere liegt **jeder
Datenverlust infolge einer Deinstallation, einer Datenlöschung, einer Deaktivierung, einer
Quarantäne oder einer Wiederherstellung, die Sie ausgelöst haben, allein in der Verantwortung des
Nutzers**. Nichts in diesen Bedingungen beschränkt eine Haftung, deren Beschränkung das Gesetz
nicht erlaubt.

## Anwendbares Recht

Diese Bedingungen unterliegen **französischem Recht**, unbeschadet der zwingenden
Verbraucherschutzvorschriften Ihres Wohnsitzlandes. Soweit gesetzlich zulässig, sind bei
Streitigkeiten die französischen Gerichte zuständig.

## Änderungen

Diese Bedingungen können sich mit der App weiterentwickeln; das Datum oben im Dokument gibt die
letzte Überarbeitung an, und die Historie ist in diesem Repository öffentlich. Die Fassung vom
23. Mai 2026 kündigte einen Vertrieb über F-Droid an: Das war falsch, App Manager Tech ist dort
nicht veröffentlicht.

## Kontakt

**contact@files-tech.com** · [Issues des Repositorys](https://github.com/gitubpatrice/APP-MANAGER-TECH/issues)
· [files-tech.com](https://files-tech.com/app-manager-tech.php)
