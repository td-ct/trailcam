# TrailCam

Android-App, die die Handykamera nutzt, um Trail-Markierungen (markierte Pfähle,
farbige Bänder in Büschen und Bäumen, ...) hervorzuheben.

## Features

- **Normalmodus**: Live-Kamerabild in voller Farbe.
- **Markierungsmodus**:
  1. Farbe wählen (Rot, Orange, Gelb, Grün, Cyan, Blau, Magenta, Weiß).
  2. Das Gesamtbild wird deutlich blasser (entsättigt und abgedunkelt) dargestellt.
  3. Über einen Schwellenwert-Slider (0–100) wird bestimmt, welche Pixel noch zur
     gewählten Farbe passen: alle Pixel, deren HSV-Distanz zur Zielfarbe unterhalb
     des Schwellenwerts liegt, werden stark fluoreszierend in der jeweiligen Farbe
     hervorgehoben.
- **Pinch-Zoom**: Zwei-Finger-Zoom ist in beiden Modi jederzeit möglich
  (Shader-Zoom, 1x–5x).

Die Farberkennung läuft in einem OpenGL-ES-Shader in Echtzeit (HSV-Distanz mit
Hue-Umbruchbehandlung), sodass keine Frames verworfen werden und der Zoom in
beiden Modi immer verfügbar bleibt.

## Geplant

- Formerkennung (Stangen, Steinhaufen, ...) zusätzlich zur Farbe.

## Build

```bash
gradle assembleDebug     # Debug-APK
gradle assembleRelease   # Release-APK (signiert, wenn Keystore vorhanden)
```

Benötigt JDK 17 und ein Android SDK (compileSdk 34). Umgebungsvariablen für das
Release-Signieren: `TRAILCAM_KEYSTORE`, `TRAILCAM_STORE_PASS`, `TRAILCAM_KEY_ALIAS`,
`TRAILCAM_KEY_PASS`.

## Installation

Die APK (`app-release.apk`) auf das Smartphone übertragen und installieren
("Unbekannte Quellen" muss erlaubt sein). Mindestens Android 7.0 (API 24).

## Release-Signierung

Der CI-Workflow signiert Release-APKs automatisch. Der Signatur-Keystore wird als
Base64 in den GitHub-Actions-Secrets `CI_KEYSTORE_BASE64`, `CI_STORE_PASS`,
`CI_KEY_ALIAS`, `CI_KEY_PASS` konfiguriert (ein Backup des Keystores liegt sicher
außerhalb des Repos). Fehlen die Secrets, erzeugt die CI einen temporären
Key -- dann sind keine Updates ueber bestehende Installationen moeglich.
