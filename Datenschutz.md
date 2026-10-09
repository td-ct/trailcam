# Datenschutzerklärung für TrailCam

Stand: 09.10.2026

## 1. Verantwortlicher

Verantwortlich für die Datenverarbeitung in der App „TrailCam" (nachfolgend
„die App") ist:

td-ct (Inhaber des GitHub-Projekts `td-ct/trailcam`)
Kontakt: über GitHub – https://github.com/td-ct

Kontaktaufnahme bezüglich datenschutzrechtlicher Fragen erfolgt ausschließlich
über den o. g. GitHub-Kontakt (Issue oder Diskussion im Repository).

## 2. Bezug über Google Play

Sofern die App über Google Play (Google Ireland Ltd., Gordon House, Barrow
Street, Dublin 4, Irland) bezogen wird, ist Google für die Datenverarbeitung
im Zusammenhang mit dem Bezug der App (z. B. Download-Statistiken,
Gerätekennung, Kaufabwicklung) nach der Play-Store-Datenschutzerklärung
verantwortlich. Diese Erklärung betrifft ausschließlich die Verarbeitung
innerhalb der App selbst. Maßgeblich für den Play Store ist die
Datenschutzerklärung von Google: https://policies.google.com/privacy

## 3. Grundsatz: Keine Erhebung personenbezogener Daten

Die App verarbeitet und speichert **keinerlei personenbezogene Daten**. Es
gibt keine Server, keine Cloud-Anbindung, keine Benutzerkonten, keine
Analyse- oder Tracking-Dienste, keine Werbenetzwerke und keine
Drittanbieter-SDKs in der App.

Im Einzelnen gilt für die von der App genutzten Berechtigungen und
Verarbeitungen:

### 3.1 Kamera (Berechtigung „Kamera")

Die App benötigt die Kameraberechtigung, um das Live-Kamerabild auf dem
Bildschirm darzustellen und darin farbige Trail-Markierungen hervorzuheben.

- Die Kamerabilder werden **ausschließlich lokal und live** im Arbeitsspeicher
  des Geräts verarbeitet (Grafik-Shader zur Farberkennung, HSV-Filter).
- Kamerabilder, Einzelbilder oder Videoaufnahmen werden **nicht gespeichert**,
  **nicht auf die Festplatte/den Gerätespeicher geschrieben** und **nicht
  an Dritte oder Server übertragen**.
- Die Kameraverarbeitung endet unmittelbar, sobald die App geschlossen oder
  die Kamera-Oberfläche verlassen wird.
- Es findet keinerlei Gesichtserkennung, Objekterkennung mit Personenbezug
  oder biometrische Verarbeitung statt. Farberkennung bedeutet hier
  ausschließlich die mathematische Farbanalyse einzelner Bildpixel
  (Farbton/Sättigung/Helligkeit) ohne Bezug zu Personen.

Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an der
Funktionsfähigkeit der App – Hervorhebung von Trail-Markierungen). Die
Berechtigung wird zudem über die Android-Systemabfrage (Einwilligung,
Art. 6 Abs. 1 lit. a DSGVO) erteilt und kann jederzeit in den
Android-Einstellungen widerrufen werden.

### 3.2 Lokaler Log (Gerätespeicher)

Die App schreibt auf dem Gerät eine technische Logdatei (`trailcam.log`
bzw. `crash.log`), um technisch notwendige Abläufe (Kamerastatus,
Fehlersuche) zu protokollieren. Diese Datei kann vereinzelt das
**Gerätemodell und die Android-Version** enthalten (z. B. „Samsung Galaxy
S23, Android 14") — dies ist die einzige näherungsweise gerätebezogene
Information; personenbezogene Daten im Sinne der DSGVO (z. B. Namen,
Standorte, IP-Adressen, Kamerabilder) werden darin **nicht** gespeichert.

- Speicherung: ausschließlich im App-spezifischen Speicher des Geräts
  (`Android/data/de.tdct.trailcam/files`), kein automatischer Upload.
- Zweck: Fehlersuche und Sicherstellung der Funktionsfähigkeit der App.
- Löschung: automatisch beim Deinstallieren der App; manuell durch
  Löschen der Dateien möglich.
- Übermittlung an Dritte: findet nicht statt.

Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an der
Sicherstellung des technisch fehlerfreien Betriebs und der Fehlersuche).

### 3.3 Einstellungen (SharedPreferences)

Die App speichert die zuletzt gewählten Farb-Einstellungen (gewählte Farbe,
Schieberegler-Werte für Farbton/Sättigung/Helligkeit, zuletzt mit der
Pipette gewählte Farben) lokal auf dem Gerät (Android SharedPreferences).
Diese Daten sind **nicht personenbezogen** (es handelt sich um
Farbwerteinstellungen ohne Personenbezug) und verlassen das Gerät nicht.
Sie werden beim Deinstallieren der App automatisch gelöscht.

### 3.4 Externer Link (PayPal/Unterstützungslink)

Sofern die App einen Link zu einer externen Website (z. B. PayPal für
Spenden) anbietet, gilt: Beim Antippen des Links verlässt der Nutzer die App
und es öffnet sich der Browser bzw. die Ziel-App. Ab diesem Zeitpunkt gilt
die Datenschutzerklärung des jeweiligen Anbieters (z. B. PayPal), nicht
mehr diese Erklärung. Die App selbst übermittelt dabei keine Daten an die
Ziel-Website; sie ruft ausschließlich einen Link auf.

### 3.5 Keine weiteren Berechtigungen

Die App fordert **keine** der folgenden Berechtigungen an: Internet, Standort,
Mikrofon, Kontakte, SMS, Speicher (Photos/Media), Telefonstatus, Fitness,
Kalender. Die volle Liste der angeforderten Berechtigungen umfasst
ausschließlich:

- `android.permission.CAMERA` (siehe 3.1)

## 4. Datenminimierung und Löschung

Da keine personenbezogenen Daten erhoben oder übertragen werden, ist eine
Aufbewahrungsfrist für solche Daten nicht einschlägig. Sämtliche lokale
App-Daten (Logdateien, Einstellungen) werden bei Deinstallation vollständig
entfernt.

## 5. Betroffenenrechte

Da keine personenbezogenen Daten verarbeitet werden, sind die
Betroffenenrechte (Auskunft, Berichtigung, Löschung, Einschränkung,
Datenübertragbarkeit, Widerspruch, Widerruf, Beschwerderecht bei einer
Aufsichtsbehörde – Art. 15–21 DSGVO) für die App selbst nicht einschlägig.
Dennoch können Anliegen jeder Art über den unter Ziff. 1 genannten
GitHub-Kontakt vorgebracht werden.

## 6. Datensicherheit

Die App nutzt die vom Android-Betriebssystem bereitgestellten
Sicherheitsmechanismen (App-Sandbox, Berechtigungssystem). Es werden keine
Netzwerkverbindungen von der App aufgebaut, kein externer Speicherzugriff
über das System-Berechtigungssystem angefordert und keine Daten mit
Drittanbietern ausgetauscht.

## 7. Änderungen dieser Datenschutzerklärung

Diese Datenschutzerklärung kann angepasst werden, um gesetzliche Änderungen
umzusetzen oder die tatsächliche Datenverarbeitung korrekt abzubilden. Die
jeweils aktuelle Version ist im GitHub-Repository hinterlegt:

https://github.com/td-ct/trailcam/blob/main/Datenschutz.md

## 8. Kontakt

Fragen oder Anliegen zu dieser Datenschutzerklärung bitte als Issue oder
Diskussion im Repository https://github.com/td-ct/trailcam einbringen.
