# Codex engineering handoff — Aum Private Gallery

You are the lead Android engineer taking over an experimental fork of Round Sync. Work autonomously, edit the repository, run tests, fix build errors, and produce a verifiable installable ARM64 APK if the available environment permits it. The user can operate from a phone, so also provide a GitHub Actions build path. **Do not declare success without build evidence.** Report to the user in Thai; technical code and commit messages may be in English.

## Inputs and authoritative baseline

- Attached archive: `Aum-Private-Gallery-Source-Patch.zip`. Locate it in the workspace or ask for a reupload ONLY if truly absent. It is a PATCH package, **not a full Round Sync checkout**.
- Upstream: `https://github.com/newhinton/Round-Sync`, pin **tag `v2.5.6`** initially. If you propose migrating to a later upstream version, explain migration and compatibility trade-offs and do not blindly apply old anchor-based patches.
- Archive structure: `Aum-Private-Gallery-Patch/scripts/apply_patch.py`, `overlay/.../PrivateGalleryActivity.java`, `PrivateGalleryPlayerActivity.java`, `.github/workflows/build-apk.yml`, `README_TH.md`, `TEST_REPORT.md`, `COPYING`.
- **Current state:** the previous author created Java overlay and patch scripts, but has NOT successfully compiled an APK or validated the implementation on a real Android device. Treat every feature claim as unverified until tested.
- Target: POCO X7 Pro / Android 16 / `arm64-v8a`. Maintain broad compatibility with supported devices when practical. No backend server; Google Drive is object storage; encryption/decryption happens on-device via **rclone crypt**.
- Keep original work credited, GPLv3 obligations, and separate application ID / signature. Never hardcode OAuth tokens, crypt passwords, `rclone.conf`, keystore secrets, or user data.

## User experience / definition of done

A private video library with a fast gallery. The user signs in to Google Drive, configures a dedicated `crypt` remote, imports videos, and can later browse a catalog with title, thumbnail, duration, tags, size, and cloud/offline state **without downloading full videos**. Catalog and thumbnails are individual encrypted objects in Google Drive (encryption via the `crypt` remote). A tap on a cloud-only video streams / seeks / decrypts on device on demand; an optional explicitly requested download supports offline playback. The same vault should be usable across multiple devices without silent data loss. The user should not need a separate always-on server.

## Phase 0 — audit and reproduce

1. Inventory workspace. Read patch source, `README_TH.md`, `TEST_REPORT.md`, upstream Gradle/Go/NDK settings and relevant Android classes. Determine exact current state instead of trusting README claims.
2. Extract patch safely. Clone upstream pinned tag. Apply using `python3 scripts/apply_patch.py <upstream-directory>`. Keep an unmodified upstream reference for diffing. If anchor matching fails, fix compatibility carefully.
3. Identify compile issues, functional bugs, design pitfalls, privacy leaks, race conditions, dangerous filesystem paths and lifecycle issues. Write a concise initial audit in `docs/AUDIT.md` with severity and evidence.
4. Run `./gradlew assembleOssDebug --stacktrace` with the required JDK 17, Go 1.23, Android SDK platform 34 and NDK 25.2.9519653 (verify actual versions against upstream source). If direct local build is impossible, repair the included GitHub Actions workflow, document the missing prerequisites, and leave a reproducible build path; do not fabricate a successful APK.

## Phase 1 — deliver a stable secure MVP

Implement and verify these features in the order below, using existing rclone integration wherever practical:

**A. Remote and crypt safety**
- Only use a verified `crypt` remote for all vault operations. Never accidentally write `catalog.json`, thumbnail, or MP4 to unencrypted Google Drive directly. Create a dedicated vault folder. Handle missing permissions, expired/revoked OAuth, rate limits, offline state, wrong password and revoked tokens gracefully.
- Implement account/remote selection, empty-state onboarding, and refresh status. Make Google OAuth setup work with the fork's distinct application identity; do not reuse another publisher's signing identity or secrets. Prefer least-privilege scopes when compatible. Investigate rclone documentation about its retiring shared Drive client ID in 2026; provide user-owned Client ID configuration if needed.

**B. Catalog, import, thumbnails**
- Ensure importing an MP4 or other supported video from Android SAF produces an encrypted video object, an independently encrypted thumbnail object, and an encrypted catalog entry with stable ID, title, duration, MIME/extension, size and timestamps.
- Generate previews from the original SAF content URI during import, not by redownloading a full cloud video on gallery open. Use safe names/IDs, validate MIME and extensions, enforce payload limits, handle malformed JSON, duplicate filenames, partial uploads and failures. Avoid temporary plaintext video copies where possible.
- Gallery should show catalog and cached thumbnails quickly, with lazy thumbnail fetch, search, refresh, progress, empty/error states, and clear cloud/offline badges. Handle missing thumbnails without blocking other items. Never accidentally show demo content as user's videos.
- Make catalog updates crash-safe and avoid silently overwriting additions from a second device. At minimum detect conflicts / preserve a backup and disable unsafe writes; ideally implement a documented conflict-resolution or append-only/transaction-safe index scheme. Provide migration/versioning of catalog schema.

**C. Streaming / player**
- Tap on cloud-only item: play by reading/decrypting requested ranges without downloading whole video. Verify seek support and Range behavior with evidence. Retain rclone crypt's authenticated chunked encryption rather than inventing a new custom cipher.
- Prefer AndroidX Media3 ExoPlayer for robust playback, pause, seek, rotation and error handling. Avoid unnecessary full-media caching. Test MP4/H.264/AAC first; show a helpful unsupported-codec error for formats not supported by Android decoders.
- Audit/replace the existing unauthenticated `rclone serve http` loopback flow. Bind ONLY to 127.0.0.1, use authenticated and session-scoped access where practical, randomize ports/tokens, disable unnecessary directory listings, sanitize paths, and terminate the server on player close/background as appropriate. Do not expose a broadly accessible local URL containing decrypted videos. Prefer an in-process custom DataSource if it materially improves isolation and maintainability.

**D. Offline/cache/privacy**
- Explicit download for offline viewing; reliable `.part` files, verify completion, cancel/retry, quota/free-space checks, delete-offline without deleting cloud copy.
- Existing patch caches local catalog, thumbnails and offline videos as plaintext in app-private storage (Android OS file-based encryption is NOT an additional app-level E2EE vault). Decide and implement a stronger at-rest protection approach for local caches or clearly label a deliberate limitation; use Android Keystore for key management if applicable. Avoid backups leaking decrypted metadata. Lock/clear sensitive memory and temporary files when feasible.
- Do not silently re-upload unencrypted files, leak tokens in logs, or store passwords in tracked files. Do not call the finished app E2EE unless threat model and implementation support that claim.

**E. Compatibility and UX**
- Work as a separate installable app named `Aum Private Gallery`, with its own package ID, signing identity, icon, permissions and a clear GPLv3 notice. Keep the original Round Sync functionality intact unless removal is justified.
- Use a responsive Android gallery UI (2-column thumbnails on phone, adaptive tablet layout), Thai/English copy where feasible, accessible controls, consistent error messages and basic privacy settings.
- Avoid unnecessary cloud services, analytics, ads and telemetry.

## Phase 2 — automated and manual tests

Write tests where feasible for catalog parsing, path validation, migration, conflict detection, cache behavior and error handling. Test patch idempotency and independent `crypt` remote behavior. Define a device test checklist for Android 16:
- Fresh install, no remote, OAuth login, encrypted vault setup; reject accidental plaintext remote.
- Import short video; confirm ciphertext and encrypted filenames in raw Drive; thumbnail/title visible with video not downloaded.
- Close/reopen, airplane mode, refresh, expired OAuth, corrupted catalog, missing thumbnail.
- Stream a larger video and seek to middle/end without full download; report bytes downloaded and limitations if measurable.
- Toggle offline, close app, play offline, delete offline; verify cloud copy remains.
- Simulate interrupted upload and concurrent writes from two devices; ensure original catalog survives.
- Inspect local storage, logcat, `rclone.conf`, backups and localhost HTTP exposure for leaks.

No fake tests or fabricated performance numbers. If lacking a phone, explicitly mark device tests `NOT RUN` and provide instructions for the user.

## Build / deliverables

1. Keep the complete fork source (not only overlay) in a clean, maintainable repository with `README_TH.md`, build prerequisites, license notices and reproducible build scripts. Remove unsafe/demo/test secrets.
2. Make GitHub Actions run lint, available unit tests and `assembleOssDebug` (or an appropriately configured signed distribution variant), then upload **the actual ARM64 APK** as an artifact. Check that output path matches Gradle split-APK naming. No signing key embedded in source; debug signed APK is acceptable for private testing, label it clearly.
3. If build succeeds, provide exact APK filename and path, build command, SHA-256, variant, supported ABI, and evidence from build logs. Do not claim installation or runtime testing unless it occurred.
4. Produce `docs/AUDIT.md`, `docs/TEST_REPORT.md`, `docs/SECURITY_NOTES.md`, `docs/BUILD_ANDROID.md`, and a concise changelog.
5. In final Thai report: explain implemented and deferred items, files changed, actual test results, remaining security limitations, APK link/artifact location, and exact steps to install and set up Google Drive/crypt on the phone. If blocked, report the precise error and deliver best possible source improvements.

## Working style / safety constraints

- Inspect before editing; make coherent changes and run the relevant checks iteratively. If a command fails, fix its root cause rather than suppressing errors or disabling checks.
- Preserve existing user data. **Never delete/overwrite existing Drive files during development**; use an isolated test vault. Never print credentials, upload secrets to CI, or ask the user to paste them into chat.
- Keep a short progress log in `docs/PROGRESS.md`. Prefer a working secure MVP over broad untested features. Clearly distinguish IMPLEMENTED, BUILT, TESTED ON DEVICE, and PLANNED.
- Do not stop after writing an implementation plan. Continue with coding, testing and build to the limits of the accessible environment.

Start by locating the ZIP, listing its actual contents, cloning the pinned upstream tag, applying the patch and attempting the first build. Then proceed based on the evidence.
