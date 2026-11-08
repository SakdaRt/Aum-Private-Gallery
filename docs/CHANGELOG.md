# Fork changes

## 2.5.6-aum-gallery.1 (experimental)

- Separate application identity `com.aum.privategallery` (debug suffix `.debug`), launcher icon and label.
- Private gallery drawer, crypt-only selection, SAF video import, independent previews, searchable/adaptive grid, duration and cloud/offline indicators.
- Append-only version2 per-video metadata with read-only version1 catalog support.
- Media3 range playback through authenticated, session-scoped loopback rclone server.
- Android Keystore encrypted metadata/JPEG cache; explicit offline plaintext policy, download verification and local cleanup.
- ARM64 build option, native task output tracking, testable catalog/path layer, reproducible CI and build documentation.
