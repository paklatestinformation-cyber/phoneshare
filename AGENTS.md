# NexLink (phoneshare) — AGENTS.md

Android app: turns the phone into a WiFi file server. Files added on phone are browsable/downloadable from any device on the same WiFi via browser at `http://<phone-ip>:8080`.

- Path: `/data/data/com.termux/files/home/phoneshare`
- Package: `com.zyvo.phoneshare`
- Repo: `paklatestinformation-cyber/phoneshare`
- Stack: Java, NanoHTTPD 2.3.1, Material3, AndroidX. Gradle 8.x, compileSdk 34, minSdk 26.
- Key files: `MainActivity.java`, `FileServer.java`, `ThumbCache.java`, `ShareService.java` (foreground service), `assets/index.html` (browser UI).

## Current state (v2.1, 28 Nov 2026)
- Foreground service keeps server alive during sleep (wake lock + wifi lock + notification with Stop action).
- Browser auto-refreshes every 3s via polling with signature-diff (no flicker).
- Digital cyan theme; professional "two linked devices" adaptive icon.
- In-icon thumbnails for image / video / PDF, generated on the phone by `ThumbCache.java` and served at `/thumb/<relPath>`; browser `<img>` and phone RecyclerView both use it.
- Browser upload fixed in v2.1: quoted `'3gp':'3GP'` key (unquoted key killed the whole inline script), upload status line, keyboard activation, re-select support; CI runs `node --check` on extracted inline JS.
- `resolveSafe()` blocks path traversal on /view, /download, /thumb.
- Stable release signing: `app/nexlink.jks.b64` (committed) → decoded in CI → signed `app-release.apk` attached as `NexLink.apk` to release `v<versionName>`.

## Build / updates
GitHub Actions `build.yml`: push to main → `node --check` inline browser JS → decode keystore → `gradle assembleRelease` → release `v<versionName>` with `NexLink.apk`.
**Never change `applicationId`, signing key/alias/password, or lower `versionCode`** — Android allows in-place updates (data preserved) only with same signature + higher versionCode.
Current: versionCode 6 / versionName 2.1. Stable cert DER SHA-256: `1f49d772bc647b5a143c8439b6678ba4160db0c273139d321f08af06137ac69e`.

## Migration caveat (one time)
Pre-v2.0 builds used runner-generated debug keys (earlier hash notes were parser misreads). Existing v1.x installs must be uninstalled once before v2.0/v2.1; user should tap Download on each shared file first (files live in internal `files/shared`, not accessible from Termux). From v2.0 onward, updates install over the top with no data loss.

## Notes
- OEM battery optimization can kill the foreground service on sleep — user must disable optimization for NexLink.
- AndroidManifest declares FOREGROUND_SERVICE_DATA_SYNC; service type dataSync.
- Developer phone in app + browser footer: +92 313 5275771 (fixed from the old wrong number).