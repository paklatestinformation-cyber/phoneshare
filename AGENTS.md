# NexLink (phoneshare) — AGENTS.md

Android app: turns the phone into a WiFi file server. Files added on phone are browsable/downloadable from any device on the same WiFi via browser at `http://<phone-ip>:8080`.

- Path: `/data/data/com.termux/files/home/phoneshare`
- Package: `com.zyvo.phoneshare`
- Repo: `paklatestinformation-cyber/phoneshare`
- Stack: Java, NanoHTTPD 2.3.1, Material3, AndroidX. Gradle 8.x, compileSdk 34, minSdk 26.
- Key files: `MainActivity.java`, `FileServer.java`, `ShareService.java` (foreground service), `assets/index.html` (browser UI).

## Current state (v1.2)
- Foreground service keeps server alive during sleep (wake lock + wifi lock + notification with Stop action).
- Browser auto-refreshes every 3s via polling with signature-diff (no flicker).
- Digital cyan theme, monospace IP/status, new vector icon (N monogram).

## Build
GitHub Actions `build.yml` → `gradle assembleDebug` → artifact `app-debug.apk`. Push to main triggers build. Releases created manually via API with APK attached.

## Notes
- OEM battery optimization can kill the foreground service on sleep — user must disable optimization for NexLink.
- AndroidManifest declares FOREGROUND_SERVICE_DATA_SYNC; service type dataSync.