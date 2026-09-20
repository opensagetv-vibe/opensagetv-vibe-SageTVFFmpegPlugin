# Common project workflow

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `install`, or
`all`. Every command delegates to the one sibling
`opensagetv-vibe-build-env` image/container. `install` only reports the
artifact boundary; physical SageTV commissioning is an explicit guarded step.

The build requires an unmodified stock `Sage.jar` through `SAGETV_JAR` or the
ignored `.deps/stock/Sage.jar`. Compile-only API classes must never be packaged.
The plugin runtime is produced separately by `opensagetv-vibe-ffmpeg-mim` and
is installed under `plugins/SageTVFFmpegPlugin/runtime/`.

Do not publish a plugin manifest, runtime archive, GitHub repository, or
release until all stock-server and physical-device gates pass and the user
gives explicit final approval.
