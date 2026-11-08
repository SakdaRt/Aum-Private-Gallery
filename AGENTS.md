# Codex project instructions — Aum Private Gallery

## Goal
Produce an installable, secure Android ARM64 private encrypted video gallery based on Round Sync v2.5.6. Use `docs/SPECIFICATION.md` as the original task specification and `docs/RESOURCES.md` for reference links. This repository is the complete fork; the original input archive was only a patch.

## Truthfulness and verification
- Never mark APK built without successful Gradle/CI logs and verified artifact bytes.
- Always state implementation vs build vs runtime test status separately.
- Don't claim E2EE for plaintext local caches; keep threat-model constraints explicit.
- Write progress and test reports to `docs/`.

## Security / data
- Never expose secrets or rclone.conf in logs, commits, CI artifacts, assistant chat.
- Never touch non-test Drive data, delete files or silently overwrite an existing encrypted catalog.
- Review localhost HTTP streaming authentication and decrypted cache handling.

## Development
- Baseline clone and patch application are already complete. Do not reapply the original overlay over the repaired fork. Continue from docs/PROGRESS.md and real build/test evidence.
- Prefer retaining upstream rclone crypt rather than implementing custom cryptography.
- Follow project's Java/Gradle conventions; use Media3 for streaming if suitable.
- Make tests, build scripts, README and docs reproducible.
- Deliver final status and instructions in Thai.
