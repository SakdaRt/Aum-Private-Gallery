# Reproducible Android build

Baseline: v2.5.6 / `b0d1edbd67217fa6bdc10f0323b4739ff5beec4b`. This checkout is the complete fork; do not reapply the old overlay over these changes.

## Prerequisites

- JDK17 (Gradle8.2 cannot run under the Windows machine's Java25).
- Go1.23 (local verification uses 1.23.12); rclone1.68.0 comes from gradle.properties.
- Transitive mimetype1.4.5 is explicitly pinned to replace the retracted baseline1.4.4; see AUDIT.md.
- Android SDK platform34, build-tools34.0.0, NDK25.2.9519653.
- SDK commandline tools, network access to Google Maven/Maven Central/JitPack/Go proxy, adequate memory/disk.

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:/path/to/go/bin:$PATH"
sdkmanager --licenses
sdkmanager 'platforms;android-34' 'build-tools;34.0.0' 'ndk;25.2.9519653'
bash scripts/build-android.sh
```

The script runs `testOssDebugUnitTest lintOssDebug assembleOssDebug -PaumArm64`, verifies exactly one ARM64 APK exists, and writes SHA256SUMS. `-PaumArm64` builds only the ARM64 native binary and split; omit it to retain upstream's other ABIs and universal APK. No release keystore is required for ossDebug. Java source/target17, minSDK23, compile/targetSDK34 are retained; Android16 behavior is a separate device test, not implied by compilation.

Windows users can use Ubuntu WSL with these Linux toolchains. Set `AUM_RCLONE_CACHE` to a Linux directory (e.g. `$HOME/.cache/aum-rclone`) when source is under `/mnt/d`; Go module extraction on NTFS is very slow. This optional path contains generated Go module/cache files, never user rclone configuration. Don't use a user vault directory as the build cache. `gradlew`/shell scripts must have LF line endings. A machine with limited RAM may need `--max-workers=1`, Go `GOFLAGS=-p=1` and a smaller Gradle heap.

Local toolchains were installed under `/home/aum/aum-toolchain` in Ubuntu WSL and system JDK17. The original Windows JAVA_HOME was not changed. The small WSL VM could compile native/Java/Kotlin on NTFS, but dexing failed with a filesystem `Cannot allocate memory` error. Final verification uses a byte-identical source snapshot in `/home/aum/aum-build` on Linux storage, with disk-backed TMPDIR, one Gradle worker, a 1 GiB Gradle heap and Kotlin compilation in process. Build evidence is in docs/*.log, distributed separately from source where necessary. They contain build/test output, not user configuration.

## GitHub Actions

The current published source stores the workflow template at `ci/android.yml`. GitHub CLI's already authenticated account has repository access but lacks the `workflow` scope, so Actions is not enabled by this upload. The user chose to use the existing login. A source snapshot is published without historical active workflow files; upstream history remains in the local `development-history` branch and upstream tags.

When workflow-authorized access is available, move `ci/android.yml` to `.github/workflows/android.yml`, commit/push, then open Actions -> **Aum Private Gallery Android** -> Run workflow. It reads Go/NDK versions, runs unit tests/lint/build, then uploads actual ARM64 debug bytes and checksum; artifact upload fails if APK is missing. Test/lint reports upload even on failure. CI has not been remotely run; a template is not proof of a successful CI run. The locally verified APK and reports are available in GitHub Releases.

## Isolated rclone contract test

```bash
python3 scripts/test-rclone-transport.py /path/to/rclone-1.68.0-linux-amd64
```

Uses a temporary local crypt vault and generated credentials; never opens a default user config or a Drive account. Checks encrypted filenames/content header, 401 without valid auth, authenticated HEAD and exact byte ranges. This verifies the rclone protocol contract, not Android Media3/device behavior or Google Drive throughput.
