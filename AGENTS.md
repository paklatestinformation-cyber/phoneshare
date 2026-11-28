# NexLink (phoneshare) — AGENTS.md

Android app: turns the phone into a WiFi file server. Files added on phone are browsable/downloadable from any device on the same WiFi via browser at `http://<phone-ip>:8080`.

- Path: `/data/data/com.termux/files/home/phoneshare`
- Package: `com.zyvo.phoneshare`
- Repo: `paklatestinformation-cyber/phoneshare`
- Stack: Java, NanoHTTPD 2.3.1, Material3, AndroidX. Gradle 8.x, compileSdk 34, minSdk 26.
- Key files: `MainActivity.java`, `FileServer.java`, `ThumbCache.java`, `ShareService.java` (foreground service), `assets/index.html` (browser UI).

## Current state (v2.0, 28 Nov 2026)
- Foreground service keeps server alive during sleep (wake lock + wifi lock + notification with Stop action).
- Browser auto-refreshes every 3s via polling with signature-diff (no flicker).
- Digital cyan theme; professional "two linked devices" adaptive icon.
- In-icon thumbnails for image / video / PDF, generated on the phone by `ThumbCache.java` and served at `/thumb/<relPath>`; browser `<img>` and phone RecyclerView both use it.
- `resolveSafe()` blocks path traversal on /view, /download, /thumb.
- Stable release signing: `app/nexlink.jks.b64` (committed) → decoded in CI → signed `app-release.apk` attached as `NexLink.apk` to release `v<versionName>`.

## Build / updates
GitHub Actions `build.yml`: push to main → decode keystore → `gradle assembleRelease` → release `v<versionName>` with `NexLink.apk`.
**Never change `applicationId`, signing key/alias/password, or lower `versionCode`** — Android allows in-place updates (data preserved) only with same signature + higher versionCode.

## Migration caveat (one time)
Pre-v2.0 builds used runner-generated debug keys (v1.3 cert hash `1cc94d6d…`, v2.0 `fc70833f…`). Existing v1.x installs must be uninstalled once before v2.0; user should tap Download on each shared file first (files live in internal `files/shared`, not accessible from Termux). From v2.0 onward, updates install over the top with no data loss.

## Notes
- OEM battery optimization can kill the foreground service on sleep — user must disable optimization for NexLink.
- AndroidManifest declares FOREGROUND_SERVICE_DATA_SYNC; service type dataSync.
- Developer phone in app + browser footer: +92 313 5275771 (fixed from the old wrong number).