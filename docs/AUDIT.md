# Initial audit and remediation

Baseline: Round Sync `v2.5.6`, commit `b0d1edbd67217fa6bdc10f0323b4739ff5beec4b`.
Input overlay was unbuilt; observations below are source findings, not device test results.

| Severity | Evidence in supplied patch | Remediation |
| --- | --- | --- |
| High | `apply_patch.py` matches application label in more than one Manifest location; stops after partial mutation | Narrowed application anchor; check already-patched block before original substring; clean/repeated apply regression test |
| High | Import rewrites `PrivateLibrary/catalog.json` from a stale local list | New imports publish unique `metadata/<UUID>.json` last. Legacy catalog read-only; independent additions merge on refresh |
| High | Player supplies no HTTP user/password | Media3 sends random per-session Basic auth header; random loopback port and prefix; only videos subtree exposed; server destroyed on stop |
| High | Catalog, JPEGs and offline files are plaintext | Catalog/JPEG cache now Android Keystore AES-GCM + AtomicFile; offline video remains an explicit, explained app-private plaintext option |
| High | Android backup enabled with credentials/config in app files | Disabled backup for this fork; gallery data kept in no-backup directory |
| Medium | `waitFor()` before draining process stderr can hang on a full pipe | Gallery commands discard stderr concurrently, never enable verbose logs; checked reads verify process exit |
| Medium | Listing debug line includes entire environment, including configured proxy credentials | Removed environment log line |
| Medium | Catalog parsing silently drops malformed entries and partially replaces previous state | Validate complete snapshot before replacing list; reject duplicates, unknown schema, invalid paths; payload limits |
| Medium | Unknown extension is renamed `.mp4` | Reject unknown formats/non-video MIME; store actual MIME and size |
| Medium | Offline download lacks size/free-space validation | 64 MiB reserve, expected length check, `.part` + rename, cancellation/retry; local removal never calls cloud deletion |
| Medium | Upstream workflow builds release but uploads debug paths; Go version never exported | One fork CI builds debug with JDK17/Go/NDK, runs tests + lint, fails on missing ARM64 artifact, emits checksum |
| Medium | Thumbnail fetched to plaintext temporary JPEG, potentially unbounded decode | Bounded bytes, encrypted atomic cache; image dimensions checked before decode |

Unresolved: app-private rclone config contains credentials/obscured crypt passwords under upstream storage model; no biometric gate. Offline videos have no extra app encryption. Authentication isolates ordinary other apps, not root or a compromised OS/app. Transfer lifecycle remains Activity-bound. Full Android16, OAuth, large-library latency, 16 KiB device page-size and codec behavior require device tests. Upstream dependencies/rclone remain pinned to the requested historical baseline; a separate upgrade/security review is needed before broad distribution.

Old dependency/translation/lint workflows were consolidated into `android.yml`; no automatic publishing, external translation action, release keystore or upstream signing identity is used.

Go warned that baseline transitive `github.com/gabriel-vasile/mimetype` v1.4.4 was retracted over a test file triggering antivirus. The fork pins that dependency to v1.4.5 (upstream release removes problematic test fixtures), while retaining Round Sync v2.5.6 and rclone1.68.0. This narrow dependency change is not an upgrade of the rclone crypt format/API. It does not establish that every historical transitive dependency is free of vulnerabilities. Reference: https://github.com/gabriel-vasile/mimetype/releases/tag/v1.4.5
