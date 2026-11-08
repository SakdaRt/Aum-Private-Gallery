# POCO X7 Pro / Android16 manual acceptance — NOT RUN

Record OS build, 4 KiB/16 KiB page size (`adb shell getconf PAGE_SIZE`), APK checksum, crypt/Drive backend, codec and result for each step. Use a dedicated test vault and no existing personal media.

- [ ] Fresh install; app has separate name/icon/package; Round Sync still opens alongside it. Verify remotes, browser, SAF and sync screens retain baseline behavior.
- [ ] Empty gallery explains creating Drive + crypt. Plain Drive remote is not selectable.
- [ ] Create own Google OAuth client ID; authenticate, revoke access, reauthorize; test insufficient scope/permission and expired token. Never save tokens in test reports.
- [ ] Configure crypt with standard encrypted filenames/directories; import a short MP4. Raw Drive shows ciphertext, not title/thumbnail/JSON. Compare decrypted video size/content using trusted local rclone.
- [ ] Gallery shows thumbnail/title/duration; refresh fetches metadata/JPEG only, not full video. Measure data use and refresh latency separately; no target numbers claimed.
- [ ] Kill/reopen, airplane mode; encrypted cached catalog and local media remain usable. Missing thumbnail falls back; corrupt metadata retains previous list and disables imports.
- [ ] Play cloud video with Media3; seek near middle/end; collect aggregate byte counts without URLs/credentials. Test codec errors, network loss and retry. Verify no full video is stored by streaming.
- [ ] Enumerate loopback while playing: bind only127.0.0.1; no/wrong auth returns401; correct authenticated Range returns206. Background/close player; port closes. Test failed startup/occupied port.
- [ ] Explicit offline download, insufficient space, cancellation, reconnect/retry, app killed mid-download; .part is not playable. Completed size matches metadata. Play in airplane mode after reopening.
- [ ] Remove offline copy/clear local copies; raw cloud objects survive. Clear cached encrypted JPEGs/catalog as well; check no late thumbnail task recreates cleared data.
- [ ] Device A and B refresh same vault, independently import different clips, then refresh both: both clips remain. No new write to legacy catalog.json. Wrong/malformed metadata must not trigger cloud overwrite/delete.
- [ ] Interrupt import at video, thumbnail and metadata stages: no broken catalog publication; report orphan objects without deleting them automatically.
- [ ] Inspect app sandbox (authorized debug access): catalog/JPEG cache encrypted, offline video plaintext only after explained opt-in, no plaintext temporary thumbnail; backups excluded. Check rclone config/logs contain no unintended exports. Verify FLAG_SECURE behavior.
- [ ] Rotate/resume/close gallery during transfer and playback; no leaked server or indefinite worker. Transfers are Activity-bound; retry is manual.
- [ ] Test Unicode/Thai titles, duplicate source names, unknown extension/MIME, large video, malformed metadata paths, search, tablet/landscape grid and accessibility font scaling.
- [ ] Inspect debug signature and native ABI; run on an actual16 KiB device if available. A desktop ELF inspection alone does not establish Android compatibility.
