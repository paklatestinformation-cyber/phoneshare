# GOAL
NexLink v2.0: file icons (browser + phone app) show real previews of JPG/PNG/video/PDF; every future APK update installs in place (no uninstall, no data loss); new professional app icon.

Plan: this file is the plan registry.

- [x] 1. Inspect project, memory, credentials, installed-app state
- [x] 2. Create stable signing keystore on GitHub runner, commit as base64 (`app/nexlink.jks.b64`)
- [x] 3. Add ThumbCache (image/video/PDF) + `/thumb/` endpoint + path guard
- [x] 4. Browser UI: previews inside file icons with graceful fallback
- [x] 5. Phone app UI: previews inside file icons (background load + cache)
- [x] 6. New professional adaptive icon + matching browser mark + correct dev phone
- [x] 7. Version 5 / 2.0, signed release build workflow (stable signature forever)
- [x] 8. Push, watch build green (run 46111796723), publish release v2.0
- [ ] 9. Verify signature stability with a rebuild, update AGENTS.md, save reusable skill/vault note

## Decisions
- Thumbnails generated ON THE PHONE via BitmapFactory / MediaMetadataRetriever / PdfRenderer → works offline in any PC browser.
- One signing key committed as base64 + a fixed signingConfig → all future builds share one signature, so Android updates keep app data.
- Keeping package `com.zyvo.phoneshare` and bumping versionCode is mandatory for in-place updates.
- One-time caveat: pre-v2.0 APKs used runner-generated debug keys; the installed v1.x must be uninstalled once. User copies files to Downloads via the existing Download buttons first. Verified cert hashes: v1.3 `1cc94d6d…`, v2.0 `fc70833f…`.

## Build fixes (recorded)
- `PdfRenderer` lives in `android.graphics.pdf`, not `android.graphics`.
- `generate()` must declare `throws Exception` for `ParcelFileDescriptor.open` / `new PdfRenderer`.
- Do not use `loading="lazy"` on thumbnails hidden with `display:none` — Chrome may never load them.

## NOW
Step 9 — rebuild verification + memory/skill bookkeeping.
