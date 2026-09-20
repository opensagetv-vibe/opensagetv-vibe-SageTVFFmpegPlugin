# Codex handoff — create opensagetv-vibe/SageTVFFmpegPlugin

## Goal

Create a new GitHub repository under the `opensagetv-vibe` organization named:

`SageTVFFmpegPlugin`

Full repository: `opensagetv-vibe/SageTVFFmpegPlugin`

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
- `SageTVTranscoderLauncher.cpp`: Linux/Windows bridge source.
- API stubs used only for local compilation when a real Sage.jar is not supplied.
- Linux and Windows manifest templates.
- STVi manifest and a starter `.stvi` module.

Run `./scripts/build.sh` immediately. It must pass before further work.

## Phase 1 — create repository and CI

1. Create `opensagetv-vibe/SageTVFFmpegPlugin`.
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
