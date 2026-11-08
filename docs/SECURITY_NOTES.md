# Security and data model

## Cloud

Select a crypt remote, never the underlying Drive remote. All gallery objects go under that crypt remote's `PrivateLibrary/`: `videos/<UUID>.<ext>`, `thumbnails/<UUID>.jpg`, `metadata/<UUID>.json`. Use standard filename encryption and directory name encryption when configuring crypt. Keys/passwords must match on every device. An incorrect password can make ciphertext unreadable; this application cannot recover lost passwords.

The old v1 `catalog.json` is read without modification. New v2 metadata records contain one validated video and are published only after successful video/thumbnail uploads. Different devices generate independent UUIDs, so refreshing unions their additions without writing a shared index. UUID allocation is not a cloud transaction or conditional-create primitive; no editing/deletion/automatic garbage collection is implemented. Failed/interrupted imports can leave unreferenced objects, intentionally preserved. Do not modify a vault concurrently using an older overlay that still rewrites catalog.json.

Catalog limits: 10,000 entries, 8 MiB aggregate JSON, 256 KiB per record, 2 MiB per JPEG, 1024-character title. Malformed records disable imports until repaired; cached list is retained. Refresh lists metadata then reuses encrypted per-record cache when size/modification time match; changed/missing records are fetched sequentially. This is not rollback protection. Very large libraries need pagination in a future version. It never downloads full media to browse the gallery.

## Device

Catalog and thumbnail bytes are encrypted using AES-GCM with a non-exportable Android Keystore key; authenticated filenames prevent renaming one encrypted object into another. AtomicFile prevents exposing half-written cache objects. No biometric authentication is required by this key, and hardware backing has NOT been verified. Offline videos are plaintext in app-private no-backup storage, with an explicit explanation before download. Android file-based encryption and the application sandbox provide the remaining protection. This is not a fully encrypted local vault and is not marketed as full E2EE for local storage.

The gallery/player use FLAG_SECURE; backups are disabled. The original Round Sync configuration remains in app-private `rclone.conf` using upstream credential handling; rclone obscuring is not password encryption. Keep logging disabled for private use, do not export configuration/logs to public storage, and protect the phone with a screen lock. A rooted/compromised/unlocked device or debug access can expose media/credentials. Debug APKs are for private verification. Do not rename/repoint an existing crypt remote while retaining local copies; clear local copies first, then reopen the gallery.

Streaming exposes only the videos subtree on 127.0.0.1 with an unpredictable port/path and random user/password. Media3 supplies Authorization headers (credentials are absent from URLs). The process stops when player stops; no VFS media disk cache is enabled. A port reservation race can cause startup failure; authenticated readiness checks fail closed. Root, same-process attackers and OS compromise remain outside this protection. HTTP Basic is acceptable here only because transport stays on device loopback; never bind this flow to external interfaces. Server directory listing is still available to the authenticated session, not to unauthenticated callers.

Uploads stream from SAF; rclone may spool on a backend without streaming support. TMPDIR is app-private; Google Drive streaming behavior and temporary file cleanup must be checked on device. Cancel requests interrupt the worker and destroy child processes when I/O returns; network timeout is 60 seconds and cancellation is not guaranteed instantaneous. Activity recreation stops jobs; no resumable foreground transfer service yet. Retry by reopening/refreshing and selecting the file again; this can leave orphans but never deliberately deletes a cloud file.

## References checked during development

- https://rclone.org/crypt/
- https://rclone.org/drive/#making-your-own-client-id — shared client ID retires during 2026; configure your own.
- https://rclone.org/commands/rclone_serve_http/
- https://developer.android.com/media/media3/exoplayer/hello-world
- https://developer.android.com/privacy-and-security/keystore

No account credentials or user Drive data were used during development.
