# SageTVFFmpegPlugin STVi design

The STVi is a UI client of the Standard plugin. It must not parse/write the INI directly and it must not execute FFmpeg locally on extenders/Placeshifter clients.

Use the stock Plugin API:

1. `GetInstalledPlugins()`
2. Select the plugin where `GetPluginIdentifier(Plugin)` is `SageTVFFmpegPluginLinux` or `SageTVFFmpegPluginWinx64`.
3. Read/write normal settings with `GetPluginConfigValue()` / `SetPluginConfigValue()`.
4. Read virtual status values with the same API. These are not stored in Sage.properties.

Suggested status fields:

- `ini.path`
- `ini.lastModified`
- `health.launcher`
- `health.runtime`
- `status.mimVersion`
- `status.platform`
- `status.last.state`
- `status.last.backend`
- `status.last.encoder`
- `status.last.hardwareDecode`
- `status.raw`
- `capabilities.ffmpegVersion`
- `capabilities.selectedBackend`
- `capabilities.vaapi.usable`
- `capabilities.qsv.usable`
- `capabilities.nvenc.usable`
- `capabilities.amf.usable`
- `capabilities.d3d12va.usable`
- `capabilities.software.usable`
- `capabilities.raw`

Suggested actions (call `SetPluginConfigValue(plugin, actionName, "1")`):

- `action.reload`
- `action.repairLauncher`
- `action.restoreDefaultIni` (must ask for confirmation in STVi)

Suggested screens:

- Overview: plugin/MIM/FFmpeg version, launcher/runtime health, current/last job.
- Hardware: each backend, compiled/device/preflight/usable state, selected backend.
- Common settings: selected keys from `ffmpeg.real.ini`.
- Advanced: INI path, last modified, view raw status/capabilities, reload, repair.
- Logs: show path and optionally tail the MIM log through a future server-plugin virtual value.

Do not make STVi the source of truth. Manual edits to `ffmpeg.real.ini` must appear on the next refresh.
