# Resources — Aum Private Gallery / Codex

Verified against public project and documentation pages on 2026-11-08; independently recheck version numbers and breaking changes at build time.

## Existing code

- Round Sync source (upstream): https://github.com/newhinton/Round-Sync
- Pinned tag for supplied patch (`v2.5.6`): https://github.com/newhinton/Round-Sync/tree/v2.5.6
- Releases: https://github.com/newhinton/Round-Sync/releases
- Project license and credits: https://github.com/newhinton/Round-Sync/blob/v2.5.6/LICENSE
- Local attachment: `Aum-Private-Gallery-Source-Patch.zip` (patch, not complete source); see `README_TH.md` and `TEST_REPORT.md` inside.

## Encryption / Cloud

- rclone crypt (format, chunked authenticated encryption, encrypted names, config password): https://rclone.org/crypt/
- Google Drive backend and custom OAuth Client ID (important: rclone is retiring the shared client ID in 2026): https://rclone.org/drive/
- rclone HTTP serve / authentication / binding to loopback: https://rclone.org/commands/rclone_serve_http/
- Google Drive OAuth API scope classification: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Drive API partial download / Range requests: https://developers.google.com/workspace/drive/api/guides/manage-downloads

## Android app engineering

- Media3 ExoPlayer quickstart: https://developer.android.com/media/media3/exoplayer/hello-world
- Media3 custom DataSource and network handling: https://developer.android.com/media/media3/exoplayer/customization
- Android app-private storage and protections: https://developer.android.com/training/data-storage/app-specific

## Build / release / phone use

- GitHub Actions Gradle builds: https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle
- GitHub Actions workflow artifacts: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts
- GitHub Actions manual workflow runs: https://docs.github.com/en/actions/how-tos/manage-workflow-runs
- On phone: open the GitHub repository in Chrome -> Actions -> Build workflow -> download artifact ZIP -> extract ARM64 APK. GitHub Codespaces can be used to edit code from a browser, but actual APK success depends on its build setup.

## Current known gaps (DO NOT assume implemented)

1. The supplied patch has not passed a real Android/Gradle build.
2. Google Drive login after a changed application ID is unverified.
3. HTTP localhost server in patch lacks authentication; review before using with sensitive media.
4. Catalog/thumbnail/offline caches are locally plaintext in app-private directories, beyond Android OS storage encryption; this may not meet user's threat model.
5. Catalog is a single JSON file; concurrent editing across devices is unsafe without protection.
6. Media playback/seek behavior has not been measured, and the device codec support is unknown.
7. ZIP contains patch overlay and workflow, not a compiled signed APK.

## Expected upstream build tools from v2.5.6's `gradle.properties`

- Java/JDK 17
- Go 1.23
- Android SDK platform 34
- Android NDK 25.2.9519653

Codex should verify these versions against the actual checkout and CI image, not blindly assume availability.
