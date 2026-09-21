# SageTVFFmpegPlugin

OpenSageTV Vibe SageTV Standard plugin and companion STVi that utilizes the separate `opensagetv-vibe/opensagetv-vibe-ffmpeg-mim` FFmpeg runtime.

User-facing plugin name: **OpenSageTV Vibe FFmpeg Plugin**.

## Non-negotiable architecture

- No changes to stock `Sage.jar`.
- Never replace or patch SageTV's stock `ffmpeg`.
- MIM runtime stays owned/released by `opensagetv-vibe-ffmpeg-mim`.
- `ffmpeg.real.ini` remains the authoritative configuration and is user-editable.
- The plugin edits individual INI keys while preserving comments/order/unknown settings.
- The plugin installs/repairs a small `SageTVTranscoder` bridge because stock SageTV already prefers that executable before its `ffmpeg` fallback.
- The STVi talks to the server-side Standard plugin; it never executes MIM locally on a remote UI.
- Updated Core builds may discover the optional, provider-neutral DVD stream
  transform through Java `ServiceLoader`. The plugin, not Core, owns all MIM
  capability probing and process management. Stock Core never loads the absent
  SPI and continues to use the same Standard plugin normally.

## Current implementation status

Implemented and commissioned:

- Java 8-compatible Standard plugin source.
- Line-preserving INI editor with `.bak` and atomic replacement.
- First-run creation of `ffmpeg.real.ini` from `ffmpeg.real.ini.default` without overwriting user config on upgrades.
- MIM `--mim-status` and `--mim-capabilities` query/cache layer.
- Virtual status/capability values for the STVi.
- ABI-neutral POSIX `SageTVTranscoder` bridge for Linux and a native Windows
  bridge source.
- Startup launcher health/repair logic.
- Linux/Windows plugin manifest templates.
- Stock SageTV7/SageTV9 STVi setup screen, status views, common settings,
  launcher repair, reload, and restore-default confirmation actions.
- Deterministic release packages, rendered Linux/Windows/STVi plugin manifests,
  SHA-256 checksums, and the common Vibe update/handoff workflow.
- Hardware capability/status reporting from MIM with safe software fallback.
- Install, upgrade, user-INI preservation, root-launcher repair, uninstall, and
  stock-FFmpeg fallback validation on an unmodified SageTV server.
- Separate FFmpeg/MIM patch/merge instructions in the handoff package.
- Optional `dvd_mpegts_v1` DVD transform provider for updated Core, with
  native-DVD fallback when the provider is absent or fails.

The `.232` commissioning server and non-Pro Fire TV validation passed recorded
and live Fixed/MIM playback through VAAPI (`h264_vaapi`), including real
video/audio output and HDMI continuity. Version 0.1.2 is published as a beta;
the SageTV plugin-catalog entry is submitted for upstream review.

## Build

Linux development build:

```bash
./scripts/build.sh
./scripts/package-dev.sh
```

Use a real stock SageTV jar for release compilation:

```bash
SAGETV_JAR=/path/to/Sage.jar ./scripts/build.sh
```

Release builds require `SAGETV_JAR` or `.deps/stock/Sage.jar`. Isolated source
checks may explicitly set `ALLOW_SAGETV_STUBS=true` to use the compile-only API
stubs under `tools/sagetv-stubs`; those stubs are never included in the plugin
JAR.

Windows launcher from the common Linux builder:

```bash
./scripts/build-windows-launcher.sh
```

## Runtime layout

```text
SAGE_HOME/
  Sage.jar                         stock, untouched
  ffmpeg                           stock, untouched
  SageTVTranscoder[.exe]           tiny plugin bridge
  plugins/
    SageTVFFmpegPlugin/
      launcher/
        SageTVTranscoder[.exe]     canonical repair copy
      runtime/
        ffmpeg_MIM[.exe]
        ffmpeg.real[.exe]
        ffprobe[.exe]
        ffmpeg.real.ini.default
        ffmpeg.real.ini            user-owned live config
        cache/
```

See `HANDOFF.md` for the exact Codex continuation and merge order.

## Standard workflow

Use the same location-independent commands as the other Vibe projects:

```text
dev.cmd test|validate|build|install|all
update.cmd
create_ai_handoff_zip.cmd
```

`install` is intentionally artifact-only. Physical SageTV installation is an
explicit commissioning action through SageTV's plugin manager.
