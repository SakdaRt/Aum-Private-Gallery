# Progress

- Extracted handoff and original overlay; read requirements separately from archive claims.
- Cloned pinned tag v2.5.6; created local branch `aum-private-gallery`.
- First Windows Gradle attempt failed: Java25 / unsupported class file major version 69.
- Patch application reproduced ambiguous Manifest anchor failure. Repaired installer and verified repeated application is byte-identical on a pristine tag checkout.
- Prepared JDK17, Go1.23.12, SDK34/build-tools34 and NDK25.2.9519653 in Ubuntu WSL. Converted gradlew to LF.
- First WSL attempt was deliberately interrupted during slow Go module extraction on NTFS; moved native module cache to Linux filesystem via AUM_RCLONE_CACHE and resumed.
- Implemented append-only metadata, strict catalog/path validation, Media3 authenticated playback, Keystore small-object cache, checked process I/O, offline size/storage checks, cancellation, cache removal and backup/screenshot protections.
- Local isolated rclone1.68 crypt/HTTP contract test passed: ciphertext, auth rejection, exact 206 ranges at start/middle/end; no Drive account involved.
- Replaced upstream workflows with one Android verification/artifact workflow. CI has not been dispatched remotely.
- Native NDK linker initially failed with signal/alarm diagnostic. A single linker thread and lower-memory local build succeeded; ARM64 rclone binary generated. Added 16 KiB max/common page linker alignment for subsequent final build.
- Java/Kotlin compilation passed; fixed upstream German/Chinese positional formatting and API23-compatible process/list helpers.
- Catalog refresh now uses encrypted per-record cache keyed by listed size/modification time, avoiding repeated downloads of unchanged metadata.

Final build and verification results are recorded in TEST_REPORT.md.

- Linux snapshot full build passed `testOssDebugUnitTest lintOssDebug assembleOssDebug` (81 tasks, 6m4s). Unit XML reports: 22 tests, zero failures/errors/skips.
- First APK was 44,830,071 bytes. Static ZIP/ABI/ELF inspection passed both native libraries after correcting the inspector to allow RELRO padding beyond LOAD memory (actual writable bytes must not be protected by rounding).
- WSL command launches later timed out (`Wsl/Service/0x8007274c`); source/artifacts remained accessible through wsl.localhost. Restarted the idle WSL runtime after confirming no active application processes, restoring command execution.
- Final rerun includes the explicitly pinned mimetype1.4.5 and the corrected artifact inspector; final artifact checksum appears in TEST_REPORT.md.
- GitHub publication uses the already authenticated SakdaRt account at the user's request. Its token lacks workflow scope; the inactive template is stored at ci/android.yml. A clean source snapshot avoids publishing active workflows/history requiring that scope; local development history and upstream tags are retained.
