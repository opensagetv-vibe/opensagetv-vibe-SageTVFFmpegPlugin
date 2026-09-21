# OpenSageTV Vibe FFmpeg Plugin handoff

## Provider-neutral DVD transform integration (2026-09-20)

Updated SageTV Core exposes a small optional `DVDStreamTransformProvider` SPI.
This repository supplies `MimDVDStreamTransformProvider` through
`META-INF/services`, advertises `dvd_mpegts_v1`, and owns the MIM capability
probe, custom command, child process, bounded output queue, and teardown. Core
does not resolve or execute MIM/FFmpeg itself.

The provider class is separate from the Standard-plugin entry class. On stock
Core the SPI types do not exist, the service is never loaded, and established
stock-compatible plugin behavior remains available. Compile-only SPI stubs
under `tools/dvd-spi-stubs` are never packaged. Missing, unavailable, or failed
providers cause updated Core to retain or restore native DVD playback.

The complete `dev.cmd all` gate passes: stock-Sage.jar source contracts,
fake-MIM provider byte round-trip, launcher tests, Linux/Windows builds,
deterministic packages, manifests, and checksums. The development plugin JAR
SHA-256 is
`2d87c5f949290331b5249e6b70d5cd7c919162510f1dd4e7ebb9765070f7a7c5`.

## Goal

The GitHub repository under the `opensagetv-vibe` organization is named:

`opensagetv-vibe-SageTVFFmpegPlugin`

Full repository: `opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin`

The public SageTV plugin name is **OpenSageTV Vibe FFmpeg Plugin**. MIM remains an internal/runtime implementation detail supplied by the separate FFmpeg/MIM repository.

Use the contents of this directory as the starting tree. Do **not** merge this repository into `opensagetv-vibe-ffmpeg-mim`.

The existing FFmpeg/MIM repository stays independent and remains the owner of FFmpeg, MIM, its INI schema/defaults, runtime hardware detection, transcoding policy and native tests.

## Hard requirements

1. **Stock SageTV only.** No patched/custom `Sage.jar` may be required.
2. **Never replace stock SageTV `ffmpeg`.** SageTV updates must be free to replace their own ffmpeg without breaking this plugin.
3. Use the stock `FFMPEGTranscoder.getTranscoderPath()` behavior: SageTV prefers `SageTVTranscoder` before `ffmpeg`. The plugin owns a small bridge named `SageTVTranscoder[.exe]`.
4. The bridge delegates to the plugin-owned MIM runtime under `plugins/SageTVFFmpegPlugin/runtime`.
5. `ffmpeg.real.ini` is the single source of truth. Do not mirror all MIM configuration into Sage.properties/plugin config storage.
6. Manual INI edits must remain supported. STVi/Standard-plugin changes modify only the selected key and preserve comments, blank lines, ordering, unknown keys and future MIM settings.
7. On upgrade, replace `ffmpeg.real.ini.default` only. Never overwrite an existing live `ffmpeg.real.ini`.
8. STVi is a client of the server-side Standard plugin. It must not directly execute MIM or edit the server INI from a remote UI.

## What is implemented in this seed

- `SageTVFFmpegPlugin`: SageTV Standard plugin implementation using only the stock plugin interface.
- `IniDocument`: line-preserving INI read/update + `.bak` + atomic save.
- `MimRuntime`: server-side execution/cache for `--mim-status` and `--mim-capabilities`.
- `MiniJson`: dependency-free parser for MIM JSON.
- `LauncherRepair`: verifies and repairs the root bridge from the plugin-owned canonical copy.
- `launcher/SageTVTranscoder`: ABI-neutral POSIX bridge used by Linux packages.
- `SageTVTranscoderLauncher.cpp`: native Windows bridge source.
- API stubs used only for local compilation when a real Sage.jar is not supplied.
- Linux and Windows manifest templates.
- STVi manifest and a starter `.stvi` module.

Run `./scripts/build.sh` immediately. It must pass before further work.

## Phase 1 — create repository and CI

1. Create `opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin`.
2. Commit this seed unchanged first so provenance is clear.
3. Add GitHub Actions using the Vibe shared build environment where practical.
4. Release artifacts from this repo must include:
   - `SageTVFFmpegPlugin-jar-VERSION.zip`
   - `SageTVFFmpegPlugin-system-linux-VERSION.zip`
   - `SageTVFFmpegPlugin-system-windows-x64-VERSION.zip`
   - `SageTVFFmpegPlugin-STVI-VERSION.zip`
   - rendered plugin XML manifests and checksums.
5. Compile the Java plugin against an unmodified stock Sage.jar. Compile-only stubs must never be packaged.
6. Build the Windows launcher in the same/common Vibe builder if MinGW is available.

## Phase 2 — finish the STVi

The starter STVi intentionally does not guess a stock SageTV7 base-widget symbol. Open the current stock SageTV7 STV in Studio and create/export the final module.

Required screen behavior:

- Add `OpenSageTV Vibe FFmpeg Plugin` under Setup/Detailed Setup (or another stable Setup location).
- Resolve the Standard plugin from `GetInstalledPlugins()` + `GetPluginIdentifier()`.
- Read/write values with `GetPluginConfigValue()` / `SetPluginConfigValue()`.
- Show common INI settings and monitoring values described in `docs/STVI_DESIGN.md`.
- Refresh status while the screen is open; do not cause FFmpeg/MIM process execution from the UI client. Calls must route through the server Standard plugin.
- Add confirmation before `action.restoreDefaultIni`.
- Include `Reload`, `Repair launcher`, `Hardware details`, `Current/last transcode`, and raw diagnostic views.
- Validate importing/uninstalling/reimporting the STVi against stock SageTV7/SageTV9 STV without duplicate menu entries.

## Phase 3 — FFmpeg/MIM repository changes

The sibling handoff folder `ffmpeg-mim-changes/` contains the requested upstream changes. Apply those to:

`opensagetv-vibe/opensagetv-vibe-ffmpeg-mim`

Do this as a **separate PR/branch in that repository**. Do not copy FFmpeg/MIM source into the plugin repo.

Required MIM changes:

1. Expand the existing `--mim-capabilities` JSON. It currently only reports protocol/compatibility flags. It must become the authoritative hardware capability response for the STVi.
2. Preserve the existing keys for compatibility.
3. Report at minimum:
   - `mimVersion`
   - `platform`
   - `ffmpegVersion`
   - `selectedBackend`
   - per backend (`vaapi`, `qsv`, `nvenc`, `amf`, `d3d12va`, `software`): compiled encoder, device/detection state where available, preflight result where available, and `usable`.
4. Reuse existing MIM code (`load_capabilities`, vendor detection, hardware preflight, backend selection) rather than implementing a second detector.
5. Continue honoring `SAGETV_FFMPEG_MIM_INI` so the plugin queries the live user INI.
6. Add native tests for the JSON schema and software fallback.
7. Add plugin-runtime release packages:
   - `OpenSageTVVibeMIMRuntimeLinux-VERSION.zip`
   - `OpenSageTVVibeMIMRuntimeWindowsx64-VERSION.zip`

Runtime ZIP layout must be relative to SageTV home and contain **only plugin-owned files**:

```text
plugins/SageTVFFmpegPlugin/runtime/
  ffmpeg_MIM[.exe]
  ffmpeg.real[.exe]
  ffprobe[.exe]
  ffmpeg.real.ini.default
  THIRD_PARTY_NOTICES.md (optional/recommended)
```

For Windows, rename/copy the MIM wrapper artifact from its current `SageTVTranscoder.exe` build name to `ffmpeg_MIM.exe` in this runtime package. The root `SageTVTranscoder.exe` belongs to the plugin repo and is only the bridge.

The runtime package must **not** contain `ffmpeg.real.ini`; the Standard plugin creates that live file from `.default` only when it does not already exist.

## Phase 4 — wire plugin manifests to the MIM release

The plugin repo manifest must reference the MIM runtime asset directly from the separate FFmpeg/MIM GitHub release. Do not vendor/copy the MIM binaries into this Git repository.

The release pipeline should receive/pin:

- `PLUGIN_VERSION`
- `MIM_VERSION`
- MIM runtime URL/MD5 (or generate XML with the release URL + checksum)

A plugin release and MIM release do not need the same version number.

## Phase 5 — update/repair behavior

At startup the Standard plugin must:

1. Leave stock `ffmpeg` untouched.
2. Ensure a live INI exists; copy `.default` only if the live file is absent.
3. Check the canonical plugin bridge exists.
4. Check the root `SageTVTranscoder[.exe]` bridge exists and has the same SHA-256.
5. Repair the root bridge if missing/different.
6. Query capabilities and expose health to the STVi.

Important: a SageTV update may overwrite/remove the root bridge. The canonical copy under `plugins/.../launcher` survives, and the Standard plugin repairs the root bridge on the next startup. Never repair by replacing stock ffmpeg.

Automation must wait for the Standard plugin's
`startup launcher repair: OK` log event (or poll for the expected bridge hash),
not merely for the container health check. On the commissioned `.232` server,
SageTV became healthy before delayed plugin initialization completed.

## Phase 6 — tests that must pass

Automate where possible:

1. Start with stock SageTV `ffmpeg`; save SHA-256.
2. Install plugin.
3. Confirm stock ffmpeg SHA-256 is unchanged.
4. Confirm root `SageTVTranscoder` points/delegates to plugin MIM.
5. Confirm plugin-created live INI equals default on first install.
6. Add comments/custom/unknown keys manually to the INI, change one setting through plugin API, and verify all unrelated lines are byte/line-preserved except intended edit and backup creation.
7. Replace stock ffmpeg to simulate a SageTV update; verify MIM still works.
8. Remove/corrupt root launcher; restart Standard plugin; verify repair from canonical copy.
9. Upgrade MIM default INI; verify live INI is not overwritten.
10. `--mim-capabilities` and `--mim-status` values appear through plugin virtual config keys.
11. Uninstall plugin; verify stock SageTV still has its original ffmpeg path and can fall back normally.
12. Run existing FFmpeg/MIM non-Android suite after the capabilities/package change.

## Merge order

Use this order to avoid publishing a plugin that references missing runtime assets:

1. **FFmpeg/MIM PR first**: capabilities expansion + runtime package build + tests.
2. Build/test FFmpeg/MIM release candidate and obtain exact runtime asset names/checksums.
3. **Plugin repo PR**: finish STVi, CI, Windows system package and manifest rendering.
4. Point plugin manifests at the exact MIM release/runtime checksums.
5. Test via `SageTVPluginsDev.d` on an isolated stock SageTV server.
6. Publish MIM release.
7. Publish plugin release.
8. Only after isolated + physical playback commissioning, submit the rendered plugin XML entries to `OpenSageTV/sagetv-plugin-repo`.

## Do not do these things

- Do not patch Sage.jar.
- Do not create a plugin-specific Sage.jar.
- Do not make MediaFormatParserPlugin a dependency.
- Do not replace `/opt/sagetv/server/ffmpeg` or Windows stock ffmpeg.
- Do not store all MIM settings in Sage.properties.
- Do not overwrite `ffmpeg.real.ini` during a MIM/plugin upgrade.
- Do not move FFmpeg/MIM source into this repo.
- Do not claim QSV/VAAPI/NVENC works based only on an encoder name; use MIM's real device/preflight logic.

## Current upstream facts used by this design

- Stock SageTV checks `SageTVTranscoder` first and falls back to `ffmpeg` in `FFMPEGTranscoder.getTranscoderPath()`.
- Stock SageTV supports Standard plugins, STVI plugins, and plugin config API calls.
- Current MIM already has `--mim-status`, an existing minimal `--mim-capabilities`, capability caching, Linux vendor detection and hardware encode preflight.

Continue from this handoff; do not restart architecture discovery unless upstream behavior has changed.

## Commissioned state - 2026-09-20

The implementation phases above are complete and version 0.1.2 is published
as a GitHub prerelease. The repository includes the finished Standard plugin, stock-STV
STVi, ABI-neutral Linux/native Windows launchers, deterministic release packaging, rendered
manifests, checksums, CI, and the common Vibe update/handoff interface.

Verified results:

- The complete `dev.cmd all` suite passes against an unmodified stock
  `Sage.jar`; compile-only API stubs never enter the plugin JAR.
- Linux and Windows packages are deterministic. Current SHA-256 values are
  recorded in `output/packages/SHA256SUMS` after each build.
- `.232` passed clean install, upgrade, customized-INI preservation, STVI
  import/reload/uninstall/reimport without duplicate controls, root-launcher
  deletion and startup repair, no-GPU software fallback, uninstall/restart,
  stock fallback, and clean reinstall.
- The final post-playback repair repeat also passed. The container reached its
  health gate before plugin initialization, then logged
  `startup launcher repair: OK`; the restored executable was mode `755` and
  byte-identical to the canonical bridge.
- The stock server `ffmpeg` remained byte-identical with SHA-256
  `bdf6aabffdba7411edff8d36c389d695257fcdf823d196020176e117612862f6`.
- The installed canonical/root bridge SHA-256 was
  `cde13e4ef390fc090b81fbfb2e00bc6454c972282de3096ef559d14c1a468af0`.
- Non-Pro Fire TV `.25` passed Media3 hardware playback through Fixed/MIM for
  the generated 1080i MPEG-2/AC-3 fixture, including FF/REW, large jumps,
  pause/resume, repeated start, stop/restart, and crash gates.
- Live Fixed/MIM passed channel `2.1`, a change to `5.1`, and a change back to
  `2.1`. MIM 0.4.9 reported `backend=vaapi`, `encoder=h264_vaapi`, hardware
  encode enabled, stopped state, and no active jobs after each session.
- HDMI evidence is retained in the Android repository at
  `artifacts/firetv/ffmpeg-plugin-fixed-hdmi-20260920.mp4`. FFprobe reports
  25.0 seconds, 1920x1080 H.264 video, and 48 kHz stereo AAC audio. FFmpeg
  reported no qualifying freeze, black interval, or silence.
- The build environment's eleven-repository workflow contract and isolated
  handoff apply/test/validate/build/install self-test pass with this component
  ordered after `opensagetv-vibe-ffmpeg-mim`.
- Stock server `.175` passed a native PluginAPI install and restart-persistence
  gate with plugin `0.1.1` enabled, `devmode=false`, and no plugin failure.
  Its Ubuntu 20.04-era container uses glibc 2.31, which exposed and verified
  the need for the ABI-neutral Linux launcher. The launcher reached MIM 0.4.9,
  reported healthy software fallback, and completed a generated 1080i
  MPEG-2/AC-3 to H.264/AAC MPEG-TS smoke transcode. The output probed as
  3.029 seconds with both audio and video before its verified cleanup.
- On `.175`, stock `Sage.jar` remained SHA-256
  `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`
  and stock `ffmpeg` remained SHA-256
  `ff4289cd6aeb9808dc5928e16cac8880c0f5493f88adc8df1f6bc7dc0b097448`.
  The installed root and canonical POSIX launchers are byte-identical at
  SHA-256 `4d9a5669795efb9003f270d01d439ffe9cd6c102d0b857da2eaaab1d741e9667`.

Two Android diagnostic assertions remain separate from this plugin: live-edge
recovery worked without emitting the expected clamp marker, and live rewind
moved the timeline back about 41 seconds with healthy A/V but did not increment
the newer `serverSeekSequence` counter. Neither caused playback or transcoder
failure.

Explicit publication approval was received. The public source/release is at
`opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin`, the required MIM 0.4.9
runtime assets are published separately, and the four beta catalog manifests
are submitted to `OpenSageTV/sagetv-plugin-repo` as pull request #124.

### Historical optional DVD MIM integration proof

The first physical proof used a Core-owned `MiniDVDStreamTranscoder`. That
implementation has now been replaced by the provider-neutral SPI above. The
evidence remains useful for the MIM media path, but Core now has no MIM/FFmpeg
dependency and this plugin owns the complete transform implementation.
Ordinary prerecorded and live Fixed/MIM still work with stock Core; optional
transformed DVD playback needs an updated Core because stock Core has no
provider discovery hook.

Non-Pro Fire TV `.25` passed the generated authored DVD with `video/avc`,
MediaTek hardware AVC decoding, 92,659,936 bytes pushed, 1.002x measured
cadence, zero video drops, and recovered pause/play, FF, REW, chapter-up, and
STOP. The active server job reported `backend=vaapi`,
`encoder=h264_vaapi`, and `hardwareEncode=true`; no active job remained after
teardown. Android evidence is retained as
`artifacts/firetv/ffmpeg-plugin-dvd-mim-main-feature-20260920.json` and the
generated-content HDMI proof as
`artifacts/firetv/ffmpeg-plugin-dvd-mim-hdmi-20260920.mp4`.
