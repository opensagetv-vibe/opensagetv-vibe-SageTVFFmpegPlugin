# Common project workflow

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `install`, or
`all`. Every command delegates to the one sibling
`opensagetv-vibe-build-env` image/container. `install` only reports the
artifact boundary; physical SageTV commissioning is an explicit guarded step.

When a gate proves a plugin defect needed for the active user task, fix and
test it, then update the test-server plugin as part of that task without asking
again solely for cross-repository permission. Preserve settings and unrelated
completed gates. On stock `.175`, only plugin installation/update is allowed;
never modify stock Core, Sage.jar, stock FFmpeg or server installation/config
files. This standing rule does not authorize unrelated publication, destructive
cleanup or interruption of recordings/other users.

The build requires an unmodified stock `Sage.jar` through `SAGETV_JAR` or the
ignored `.deps/stock/Sage.jar`. Compile-only API classes must never be packaged.
The plugin runtime is produced separately by `opensagetv-vibe-ffmpeg-mim` and
is installed under `plugins/SageTVFFmpegPlugin/runtime/`.

Do not publish a plugin manifest, runtime archive, GitHub repository, or
release until all stock-server and physical-device gates pass and the user
gives explicit final approval.
