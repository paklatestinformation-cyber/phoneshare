# GOAL
NexLink v2.0: file icons (browser + phone app) show real previews of JPG/PNG/video/PDF; every future APK update installs in place (no uninstall, no data loss); new professional app icon.

Plan: see TASK.md (this file is the plan registry).

- [x] 1. Inspect project, memory, credentials, installed-app state
- [ ] 2. Create stable signing keystore on GitHub runner, commit as base64
- [ ] 3. Add ThumbCache (image/video/PDF) + `/thumb/` endpoint + path guard
- [ ] 4. Browser UI: previews inside file icons with graceful fallback
- [ ] 5. Phone app UI: previews inside file icons (background load + cache)
- [ ] 6. New professional adaptive icon + matching browser mark + correct dev phone
- [ ] 7. Version 5 / 2.0, signed release build workflow (stable signature forever)
- [ ] 8. Push, watch build green, publish release apk, report to user
- [ ] 9. Update AGENTS.md + save reusable skill/vault note

## Decisions
- Thumbnails generated ON THE PHONE via BitmapFactory / MediaMetadataRetriever / PdfRenderer → works offline in any PC browser.
- One signing key committed as base64 + a fixed signingConfig → all future builds share one signature, so Android updates keep app data.
- Keeping package `com.zyvo.phoneshare` and versionCode bumping is mandatory for in-place updates.
- One-time caveat: the currently installed APK was built with a runner-generated debug key; switching to the stable key requires one manual reinstall. User copies files to Downloads via the existing Download buttons first.

## NOW
Step 2 — generating the stable keystore through a temporary GitHub Actions workflow.
