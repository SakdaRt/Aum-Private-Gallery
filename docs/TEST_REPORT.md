# ผลการตรวจสอบ Aum Private Gallery

สถานะ: **สร้าง APK จริงสำเร็จ แต่ยังไม่ได้ทดสอบบนอุปกรณ์/บัญชี Google Drive จริง**

## APK ที่ส่งมอบ

- File: `roundsync_v2.5.6-aum-gallery.1-oss-arm64-v8a-debug.apk`
- Workspace: `D:\Codex\Aum Private Gallery\delivery\roundsync_v2.5.6-aum-gallery.1-oss-arm64-v8a-debug.apk`
- Size: **44,830,071 bytes**
- Variant/ABI: `ossDebug` / `arm64-v8a` only
- Application ID: `com.aum.privategallery.debug`
- Version: `2.5.6-aum-gallery.1-DEBUG`, versionCode `417`
- minSDK23, compile/targetSDK34; Android16 runtime remains NOT TESTED.
- Signing: Android Debug RSA2048; apksigner verifies v1 and v2. No release signing key or private key is included in source/delivery.

SHA-256:

```text
aebebcf48c49c9e3373a721db64b471d2d71a87d5b3c51e10880e2ab4adfaa7e
```

## ผลที่รันจริง

| การตรวจ | ผล |
| --- | --- |
| Clean patch application to pristine v2.5.6 | PASS |
| Apply supplied installer a second time | PASS: source hashes unchanged |
| Catalog/path standalone JUnit | PASS: 8 tests |
| Gradle app unit suite | PASS: 22 tests, 0 failures/errors/skips (8 gallery, 13 upstream provider, 1 upstream example) |
| `lintOssDebug` | PASS against retained upstream baseline; report has 0 errors, 458 warnings, 1 information. This is not a warning-free/security-certified project |
| `assembleOssDebug -PaumArm64` | PASS: initial complete Linux build 81 tasks/6m4s; final dependency-pin rerun 51s, 5 tasks executed/76 up-to-date |
| APK ZIP integrity/ABI | PASS: actual bytes inspected, only arm64-v8a native entries |
| ELF LOAD and RELRO rounding | PASS for librclone.so and libdatastore_shared_counter.so; LOAD alignment 0x4000. Static check only; no16KiB device test |
| APK signature | PASS: v1/v2 verified; v3/v4 not enabled. Tool emits v1 META-INF-entry warnings, not a v2 verification failure |
| Isolated rclone1.68 local crypt transport | PASS: roundtrip, ciphertext names/header, no/wrong auth401, HEAD200, exact206 ranges at start/middle/end (1024 response bytes each) |
| `git diff --check` | PASS (Git also reports expected CRLF-to-LF normalization notices) |
| Connected Android device | None listed by `adb devices -l` |
| GitHub Actions remote run | NOT RUN; inactive template at ci/android.yml. Existing CLI login lacks workflow scope; source/APK upload uses repository/release access |

The transport test uses an official Linux rclone1.68 binary and a generated temporary **local** crypt vault. It does not measure Google Drive traffic, Android Media3 decoding, actual seek latency, or a full-video-download threshold on the phone. The final source pins transitive mimetype1.4.5 to remove the retracted1.4.4 from the generated Go module; that dependency does not change this APK's native output bytes in this Android build.

## Build evidence

Pinned baseline commit: `b0d1edbd67217fa6bdc10f0323b4739ff5beec4b`.
JDK17, Go1.23.12, SDK34, build-tools34.0.0, NDK25.2.9519653; native max/common page alignment16384, one linker thread.

Final local command in Linux source snapshot:

```bash
export AUM_RCLONE_CACHE=/home/aum/aum-toolchain/rclone-cache
export TMPDIR=/home/aum/aum-toolchain/tmp
export GOMAXPROCS=2 GOMEMLIMIT=512MiB GOFLAGS=-p=1
bash gradlew -PaumArm64 '-Dorg.gradle.jvmargs=-Xmx1024m' \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1 \
  testOssDebugUnitTest lintOssDebug assembleOssDebug --no-daemon --stacktrace
```

Build ran in `/home/aum/aum-build`, a snapshot of the current source; the editable fork remains in `D:\Codex\Aum Private Gallery\app`. Initial Windows build failed under Java25 (`Unsupported class file major version69`). The supplied installer initially failed an ambiguous Manifest anchor. Early WSL builds encountered an NDK linker alarm and an NTFS filesystem allocation error; logs were retained rather than hidden. Low-memory settings and a Linux filesystem build overcame these failures. A later WSL service timeout was cleared by restarting the idle runtime.

Evidence ZIP includes actual build/test logs, JUnit XML/HTML, lint HTML/XML, output-metadata.json, signature/badging and ELF inspection output. Source ZIP includes the whole fork, license, build/test scripts, workflow and per-file source hash manifest. No generated rclone.conf, keystore or SDK/build cache is packaged.

## Implemented / not verified / deferred

Implemented in source and compiled: crypt-only gallery, existing upstream remote/OAuth configuration, SAF imports, thumbnail generation, separate append-only metadata, encrypted small-object cache, search/duration/cloud-offline states, Media3 authenticated loopback playback, checked offline download/removal, cancellation and local-cache clearing.

**NOT TESTED ON DEVICE:** OAuth with the fork identity/custom client ID, real Drive encryption/permission errors, Android Keystore cache behavior, codec support, Media3 seeking/bytes used, airplane-mode playback, lifecycle interruption, actual two-device synchronization, storage pressure,16KiB runtime and retained Round Sync feature regression. Use DEVICE_TEST_CHECKLIST.md with an isolated test vault before real data.

Deliberate limitations: offline videos have no additional app-level encryption; rclone config retains upstream app-private credential handling; no biometric gate; Activity-bound transfers/manual retry; cloud orphans preserved; no cloud video deletion/title/tag editing or shared-record merge transactions. Refresh reuses encrypted metadata cache by remote size/mtime; first load/large libraries are not benchmarked. This delivery is an experimental private debug build, not a production security claim.
