# OpenSageTV Vibe FFmpeg Plugin handoff

## 0.1.3 release candidate (2026-09-29)

Version 0.1.3 is paired with MIM 0.4.10. The affected release gate passed
stock Sage.jar and STVi contracts, caption side-channel and Direct HTTP/session
tests, optional DVD Copy/Transcode provider tests, Linux and Windows launcher
builds, deterministic archives, rendered manifests, and checksums. Package
SHA-256 values are:

- JAR: `04cf63ac241a240e3d3cfda59024c09f4b687d4ba2731be1a54bd8ec2da6dea7`
- STVi: `0476cc8555a5d43d9af9880ed631f357289317455d04700dfd7dc08788439158`
- Linux system: `492724f0d22e0affe9895b32386988a3ab9269c993ccb8240cbd4589a2ffb8c6`
- Windows x64 system: `3cc6091d5260dde2dc177a8ddc2790fc76812b1060528728b2b9ee4edadab68c`

Per the workspace release policy, only gates affected by this plugin/runtime
change were rerun. The broader deferred physical matrix remains visible in
`TASKS.md`.

## Direct deinterlace/full-hardware checkpoint (2026-09-28)

The current plugin/MIM contract accepts `auto`, `on`, and `off` for each Direct
Transcode session. Linux `.232` reports `full_gpu` with VAAPI for all three.
Stock Windows `.185` reports `full_gpu` with QSV when deinterlacing is Off;
Auto/On correctly retain the mixed fallback because Haswell QSV VPP rejects
the interlaced surface contract. Non-Pro `.25` passed startup, hardware Android
decode, seek, pause/resume, crash checks, and synchronized event-225 CEA with
Off on both platforms. Linux CC1/CC2/Off visual cycling also proved one SageTV
renderer after the Android ownership correction.

The rebuilt plugin passed `dev.cmd all`; packaged JAR SHA-256 is
`52db1548af64f1cac50459278059526b54088582d88e804961e3ecff318cc2b8`.
The rebuilt Linux MIM deployed to `.232` is
`79640cb17e6fd25a81920b73721cd417c8761ef50933a525a004c167481943ee`.
The server ended with zero caption/Direct sessions and no MIM/FFmpeg process.
Windows AC standby was restored to ten minutes and the two verified temporary
deinterlace deployment files were removed. MIM-FIXED-003 remains open for the
remaining DVD, growing/transition, legacy-client, and failure-fallback rows.

## Caption side channel and Direct transport checkpoint (2026-09-28)

MIM-FIXED-001 and MIM-FIXED-002 are complete. The Standard plugin provides a
tokenless, LAN-scoped capability/session API with opaque bounded handles,
source authorization through SageTV, explicit Direct Copy and Direct
Transcode policies, status/restart/media/teardown operations, and no stock
`Sage.jar` or stock `ffmpeg` replacement.

The Direct service uses FFmpeg's segment muxer with MPEG-TS segments and an
M3U8 list. Unlike the HLS muxer, it does not force DVB or Teletext subtitle
streams through WebVTT, so video, audio, CEA, Teletext, DVB bitmap, language,
and timing metadata survive. The live playlist is bounded and a monitor
removes segments that are no longer listed after the retention interval.

The corrected JAR is deployed on `.232` with SHA-256
`35352767f1954c49782d23646f1f38da907c8ec4adab9709ed05e16e7eb473f9`.
Non-Pro `.25` passed Copy, full-GPU Transcode, CEA, Teletext, DVB, pause,
restart/reconnect, and teardown through the Android client. Stock `.175`
without the plugin reported the optional path unavailable and continued on
ordinary stock Fixed/Push without a crash. MIM-FIXED-003 remains open for the
broader stock-Windows, plugin-version/failure, legacy-client, and cross-player
matrix.

The final `dev.cmd all` gate passed stock-STV contracts, stock-`Sage.jar`
source contracts, Java/plugin/caption/Direct HTTP and session tests, Linux and
Windows launcher builds, and deterministic plugin archives/manifests. Its
packaged release-candidate JAR SHA-256 is
`465a992bf34a6abe0befba289980dcd9930ff54dd0f82f44f7bc3babaeae80b8`;
the physically tested deployed `.232` JAR remains the separately recorded
build above because this final gate did not redeploy or change the server.

## LAN-scoped caption and MIM Direct API (2026-09-27)

The caption/MIM Direct HTTP interface no longer has a bearer-token setting or
authentication header. It is intended only for a trusted LAN. Direct starts
remain limited by SageTV library/source authorization, and short-lived opaque
reservation/session identifiers still scope caption, status, restart, media,
and teardown operations. The regenerated STVi exposes only enable, bind
address, and port controls.

All plugin Java, STVi, launcher, caption, and MIM Direct tests pass. The
tokenless JAR SHA-256 is
`6865b7c503a496a209d938d61cec7f683d787a139c9ed9b64922e199c8cd961a`.
It is deployed on `.232`; unauthenticated capability, caption reservation, and
teardown calls passed with the service reporting `ready` and MIM Direct
available.

## Windows stock-server acceptance (2026-09-26)

The current development plugin was commissioned on the stock Windows SageTV
server at `.185` and physically validated with non-Pro Fire TV `.25`. The
stock `Sage.jar` remained unchanged at SHA-256
`d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`.
Commissioned component hashes are:

- Standard-plugin JAR: `5edb191e7bb5e2812bcb2e054e84e69b58cc7dc661ed765f71c8db1d2d4209c7`
- categorized STVi: `671c0b4d33b880c946477e54169b16671a2d4ea1bb85ffa6dfb47184de9e4679`
- root Windows launcher: `c585ab11d3da30364de9e5da7e6f4de2654ba6db9d464c8d540b807bdd3a095e`
- Windows MIM: `b188e33cb9a85c831bb74dcdac3569209afbad31ec220b877cd454f0008e15e1`
- plugin FFmpeg runtime: `9f2b8005e96226c0ce36e41b5a53bdb8f4050a3c45dfef23812cfae4f9101d46`
- restored runtime INI: `c951637c96fa8651afcf01a3746525c606a571c6c3eba237f14a86845256ea54`

The exported synthetic hardware report is
`plugins/SageTVFFmpegPlugin/reports/OpenSageTV-Vibe-Hardware-Test-20260926-163603.json`
(SHA-256 `fbdd7a91c4f54359e845d647c36729e4579bb7b464946a48d50baf8b2bd65284`).
Hardware Fixed playback used QSV; an explicit temporary `backend=software`
gate selected `libx264`, passed real MPEG-TS/AAC playback, FF/REW, and
pause/resume, then restored the INI byte-for-byte. STOP left zero launcher,
MIM, or FFmpeg processes and Android reported no crash signature.

Physical evidence under the Android project's `artifacts/firetv` includes:

- `windows185-fixed-full-matrix-20260926.log`
- `windows185-fixed-software-fallback-20260926.log`
- `windows185-fixed-cea-stv-legacy-20260926.log`
- `windows185-fixed-teletext-local-20260926.log`
- `windows185-fixed-dvb-bitmap-20260926.log`
- `windows185-stock-native-authored-dvd-20260926-retry.json`
- `windows185-stock-native-aladdin-boundary-20260926.json`
- `windows185-stock-native-aladdin-8m-20260926-retry.json`

The authored disc passed native startup/menu/title, pause/play, FF/REW,
chapter, audio, and subtitle selection. Real ALADDIN passed the supported
native MPEG-2/AC-3 cadence and navigation gates. Stock Windows Core accepted
the public 480,000 ms DVD `Seek(long)` request but did not land accurately in
two bounded attempts; this is a stock Core/DVD-reader limitation. Fixed file
transcoding and native disc playback are intentionally separate on stock Core,
which has no transformed-DVD provider SPI.

The startup dump shows the registered STVi as `state=Failed`, but this is a
stock diagnostic formatting defect: state 4 (`PLUGIN_STATE_PASSIVE`) falls
through to the word `Failed` in `PluginWrapper.toString()`. The real failure
API tests only state 3. Every client connection successfully imported the
commissioned STVi and ran its auto-cleanup hook with no import exception, so
the server Core was not patched for this cosmetic log output.

## Categorized stock-STV interface (unreleased)

The generated STVi now presents seven focused category editors plus an All
Settings fallback instead of one long stock plugin list. Generation remains
deterministic against the stock SageTV7 STV. On 2026-09-26 the generated STVi
passed structural and Java smoke tests and was commissioned on stock Windows
server `.185`; the non-Pro Fire TV physically rendered Playback & Transcoding
and Hardware Setup, Back returned to the category list, and direct category
transitions no longer retained the previous title. The commissioned STVi
SHA-256 is
`671c0b4d33b880c946477e54169b16671a2d4ea1bb85ffa6dfb47184de9e4679`.

The recoverable pre-deployment copy is under
`/mnt/user/appdata/sagetv-vibe-server-u26-gpu-j11/.component-backups/stvi-self-contained-20260926-105903`.

## Hardware diagnostic and export (unreleased)

Development version 0.1.3 adds explicit **Run Hardware Test** and **Export
Hardware Report** plugin actions. The test calls MIM's bounded synthetic
`--mim-hardware-test`; it does not read recordings or edit the live INI. The
exporter writes credential-free timestamped JSON plus
`plugins/SageTVFFmpegPlugin/reports/OpenSageTV-Vibe-Hardware-Test-latest.json`
and exposes the timestamped server path through `hardwareTest.reportPath`.

On 2026-09-26 this development build and the matching rebuilt Linux MIM 0.4.9
runtime were installed on the isolated `.232` server. SageTV logged
`started version=0.1.3-dev`; the container remained healthy and MIM had no
active job after the test. Exact commissioned SHA-256 values are:

- plugin JAR: `5edb191e7bb5e2812bcb2e054e84e69b58cc7dc661ed765f71c8db1d2d4209c7`
- MIM: `b4b3ec83010f90c6a923245b8dfda94cbb1795eb33d9f15918c24f92f95a959b`
- root/canonical launcher: `4d9a5669795efb9003f270d01d439ffe9cd6c102d0b857da2eaaab1d741e9667`
- preserved stock FFmpeg: `bdf6aabffdba7411edff8d36c389d695257fcdf823d196020176e117612862f6`
- preserved live INI: `70141e19d0d68d7540f6c8eac85e11efeaac1b47e587bcad9eaef927f3aa38a3`

The synthetic hardware test passed complete decode/filter/encode/pipeline
stages for VAAPI and QSV plus the software fallback. NVENC was correctly
unavailable because this host has no NVIDIA runtime/device. The recoverable
pre-update backup is
`/mnt/user/appdata/sagetv-vibe-server-u26-gpu-j11/.component-backups/ffmpeg-plugin-20260926-152331`.

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

### Detailed Setup-style STVi repair - 2026-09-26

The custom bare category grid was replaced after physical non-Pro Fire TV
testing showed that its labels rendered but did not expose a usable remote
selection path. `scripts/build_stvi.py` now derives each category row and its
focused artwork from stock SageTV7 Detailed Setup, uses an explicit two-pane
layout that does not depend on imported Theme inheritance, and routes Select
through an action to the stock-derived configuration editor. Editors use five
rows per page, explicit foreground/focus styling, and an opaque content pane.
`scripts/test_stvi.py` rejects a return to the bare grid and requires every
category to have a focus widget and activation target. Choice and Multichoice
dialogs now have explicit layout, text, background, and focus rendering rather
than relying on external stock theme/panel references. On `.185` with non-Pro
Fire TV `.25`, category Select opened Playback & Transcoding and Hardware
Setup; the first Boolean control toggled through the stock Plugin API and was
restored to its original `true` value. Audio Mode rendered `copy`, `ac3`, and
`inherit`, remote focus moved between choices, and Back canceled without
changing the original `copy` value. Hardware Backend likewise rendered its
available choices. The heading repaint guard prevented the prior category
title from remaining under the next editor title.

On 2026-09-27 the same generated STVi was installed atomically on Vibe test
server `.232`. Its deployed SHA-256 is
`160fdd47cd205ca4ff12dbe9da98ed09c61e769b29dd4e595597b6465a8544f8`;
the already-installed JAR and Linux launcher matched the current package at
`5edb191e7bb5e2812bcb2e054e84e69b58cc7dc661ed765f71c8db1d2d4209c7`
and `4d9a5669795efb9003f270d01d439ffe9cd6c102d0b857da2eaaab1d741e9667`.
The previous STVi remains recoverable under
`.component-backups/ffmpeg-stvi-detailed-setup-20260927-1`. After one
controlled restart to enable the authenticated Core MCP LAN listener, `.232`
returned healthy, the non-Pro Fire TV `.25` reconnected, the category menu and
Audio Mode choices rendered correctly, and Back left `MIM Enabled=true` and
`Audio Mode=copy` unchanged. `.232` is now the default server in the ignored
local Android commissioning configuration.

The `.232` commissioning was then repeated after converting the category UI
to a single Detailed Setup-style screen. All seven categories render as stable
stock-derived rows in the compact left rail. Select keeps that rail visible
and redraws the matching settings in the right pane. The stable rows are
intentional: recycled table cells retained stale setup-area context on remote
MiniClients. Generation fails if a future ninth category is added until a
physical-device-tested overflow rail is supplied, rather than silently
clipping it. Native focus artwork, the stock dialog background, and private
choice pagination render correctly without stock references `6454`-`6461`.

The final 2026-09-27 non-Pro Fire TV `.25` gate selected `Hardware Setup` and
`Hardware Availability`; each changed the right pane in place, while the full
category rail remained visible. The Hardware Backend choice dialog opened in
the stock Detailed Setup style and Back closed it without changing `auto`.
The deployed STVi SHA-256 is
`2114b7bd0a7fcdf1efb590cc840f88c87a994f2ed055387e044f04865eb87b6c`.
Its immediately preceding version remains recoverable under
`.component-backups/ffmpeg-stvi-stock-selection-20260927-0625`.

The last physical comparison used stock Detailed Setup's focused value cell as
the reference. The plugin value cell now begins at the same horizontal bound,
has the same broad selection glow, and centers `True`, `auto`, `copy`, and
other values instead of focusing only their text width. The title and editor
labels no longer inherit the dark text shadow. The compact Choice dialog,
translucent dark-gray content, light-gray separators, and right-hand scrollbar
were also visible on `.25`. Back canceled the Deinterlace dialog without
changing `auto`; the observed `MIM Enabled=true` and `Audio Mode=copy` values
also remained unchanged. Generated validation aliases were removed after the
gate; only the installed canonical STVi and its recoverable component backup
remain.

Explicit publication approval was received. The public source/release is at
`opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin`, the required MIM 0.4.9
runtime assets are published separately, and the four beta catalog manifests
are submitted to `OpenSageTV/sagetv-plugin-repo` as pull request #124.

The final `.232`/non-Pro refinement on 2026-09-27 made focus movement update
the right pane without Select, changed the chrome to the same darker
translucent Detailed Setup palette, widened the stable left rail enough for
every category, and converted Hardware Test results and Runtime Status into
compact read-only label/value tables. Repeated row help was removed, runtime
labels were shortened, and the FFmpeg banner is normalized to its version
token for display. The complete local suite passed before deployment. The
installed JAR SHA-256 is
`c69603afc5b8346972ec93db2cfce85c30e0f99abe255c2c7231edc98cf8f1c1`;
the STVi SHA-256 is
`819fa90a60ab737415ed5caedb303edb03527fc7508fed905de49860e650ef7c`.
The prior pair remains recoverable under
`.component-backups/ffmpeg-ui-20260927-132418`.
Run, Export, and every read-only hardware pipeline result now share the single
Hardware Test category. The separate Hardware Test Results rail entry was
removed because only Run and Export are actionable.
On the final non-Pro gate, remote focus changed categories without Select,
right-arrow skipped every read-only status cell, Select did not open the
Android keypad, and Back returned to Setup. Running Hardware Test from the TV
completed successfully and displayed `2 HW passed`; the result table showed
VAAPI and QSV `pass`, QSV fallback `unsupported`, NVENC `fail` because the
container has no CUDA device/library, and AMF `unsupported`. The NVENC value is
the authoritative MIM synthetic-pipeline result rather than a UI inference.

The final two-column dashboard refinement was rebuilt and commissioned on
`.232` / non-Pro Fire TV `.25` on 2026-09-27. Hardware Test and Runtime Status
now use separate synchronized label/value text objects, both left aligned;
they no longer depend on padding glyphs or `gFontNameClock`. Runtime Status
uses its full content height and visibly includes VAAPI, QSV, QSV Fallback,
NVENC, AMF, D3D12VA, and Software without clipping or ellipses. Hardware Test
physically passed Run (`2 HW PASS`), Export, non-focusable result navigation,
and Back to Setup. The exported files include
`OpenSageTV-Vibe-Hardware-Test-20260927-202754.json` and the stable
`OpenSageTV-Vibe-Hardware-Test-latest.json`. The installed JAR SHA-256 is
`b2ef272786df93f6bafa1f955cd5a28ddba67342dadaa476c146408af02a16d1` and
the STVi SHA-256 is
`6229120c578f2f88cf8524186600315a39b1f893155846c7aa125d826ecf2c59`.
The immediately preceding pair remains recoverable under
`.component-backups/ffmpeg-ui-20260927-152043`.

### Windows catalog commissioning - 2026-09-22

Pull request #124 was merged, but the official aggregate
`SageTVPluginsV9.xml` and MD5 had not been regenerated since 2024. Stock
servers therefore could not discover the merged Windows manifests. Pull
request #125 refreshes the aggregate with the five source entries that were
missing, including both Windows FFmpeg plugin entries.

Windows server `.212` was commissioned without changing `Sage.jar` or stock
`ffmpeg.exe`: Sagex wrote a temporary `SageTVPluginsDev.xml` containing only
the Windows x64 Standard/STVi manifests, refreshed the plugin catalog, and set
the stock STV beta-version filter to show. SageTV then resolved both version
0.1.2 plugins as compatible. All referenced release assets downloaded, their
MD5 values matched, and their ZIP layouts were verified. Remove the temporary
development catalog after the official aggregate contains version 0.1.2.

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
