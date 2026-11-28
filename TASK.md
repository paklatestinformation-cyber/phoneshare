# GOAL
NexLink v2.1: file icons (browser + phone app) show real previews of JPG/PNG/video/PDF; every future APK update installs in place (no uninstall, no data loss); new professional app icon; browser upload works with visible status.

Plan: this file is the plan registry.

- [x] 1. Inspect project, memory, credentials, installed-app state
- [x] 2. Create stable signing keystore on GitHub runner, commit as base64 (`app/nexlink.jks.b64`)
- [x] 3. Add ThumbCache (image/video/PDF) + `/thumb/` endpoint + path guard
- [x] 4. Browser UI: previews inside file icons with graceful fallback
- [x] 5. Phone app UI: previews inside file icons (background load + cache)
- [x] 6. New professional adaptive icon + matching browser mark + correct dev phone
- [x] 7. Version 5 / 2.0, signed release build workflow (stable signature forever)
- [x] 8. Push, watch build green (run 46111796723), publish release v2.0
- [x] 9. Verify signature stability with a rebuild, update AGENTS.md, save reusable skill/vault note
- [x] 10. Fix browser upload: quote numeric-leading object key `'3gp'` (inline JS parse error killed ALL handlers)
- [x] 11. Add upload status line, keyboard activation, re-select same-file support; add `node --check` in CI
- [x] 12. Version 6 / 2.1; push, build green (run 46113974698), release v2.1; verify same cert DER hash

## Decisions
- Thumbnails generated ON THE PHONE via BitmapFactory / MediaMetadataRetriever / PdfRenderer → works offline in any PC browser.
- One signing key committed as base64 + a fixed signingConfig → all future builds share one signature, so Android updates keep app data.
- Keeping package `com.zyvo.phoneshare` and bumping versionCode is mandatory for in-place updates.
- One-time caveat: pre-v2.0 APKs used runner-generated debug keys; the installed v1.x must be uninstalled once. User copies files to Downloads via the existing Download buttons first. Final correct v2.x cert DER SHA-256: `1f49d772bc647b5a143c8439b6678ba4160db0c273139d321f08af06137ac69e` (earlier hash notes were parser misreads).
- Browser upload regression (v2.0): `3gp:'3GP'` is invalid JavaScript (numeric-leading unquoted object key) → the whole inline `<script>` failed to parse → click/upload/polling handlers never attached. Fix: `'3gp':'3GP'`; validate inline JS with esprima locally and `node --check` in CI before every release.

## Build fixes (recorded)
- `PdfRenderer` lives in `android.graphics.pdf`, not `android.graphics`.
- `generate()` must declare `throws Exception` for `ParcelFileDescriptor.open` / `new PdfRenderer`.
- Do not use `loading="lazy"` on thumbnails hidden with `display:none` — Chrome may never load them.

## Verification
Two independent releases (v2.0 run 46111796723, v2.1 run 46113974698) produce the same X.509 cert DER hash `1f49d772bc647b5a143c8439b6678ba4160db0c273139d321f08af06137ac69e`.
APK v2 signer-block nesting: magic `APK Sig Block 42` → block size uint64 → pair (uint64 len, uint32 id `0x7109871a`) → length-prefixed sequence → length-prefixed signer → length-prefixed signedData → length-prefixed certificates → first DER cert.
Browser JS inside the signed APK parses cleanly (esprima + `node --check`) and contains the quoted `'3gp':'3GP'` key plus upload-status handler.

## NOW
Done — v2.1 shipped with working browser upload; user installs over v2.1 for all future updates.
