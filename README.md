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

## Current scaffold status

Implemented in this handoff:

- Java 8-compatible Standard plugin source.
- Line-preserving INI editor with `.bak` and atomic replacement.
- First-run creation of `ffmpeg.real.ini` from `ffmpeg.real.ini.default` without overwriting user config on upgrades.
- MIM `--mim-status` and `--mim-capabilities` query/cache layer.
- Virtual status/capability values for the STVi.
- Cross-platform native `SageTVTranscoder` bridge source.
- Startup launcher health/repair logic.
- Linux/Windows plugin manifest templates.
- STVi plugin manifest and starter module.
- Separate FFmpeg/MIM patch/merge instructions in the handoff package.

The STVi visual screen/menu insertion is intentionally a Phase-2 Studio task; the starter module is included and the server plugin API is ready for it.

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

Without `SAGETV_JAR`, the build uses compile-only API stubs under `tools/sagetv-stubs`; those stubs are never included in the plugin JAR.

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
