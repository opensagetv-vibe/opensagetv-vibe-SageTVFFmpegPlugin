# Changelog

## 0.1.0-dev - 2026-09-20

- Established public repository/plugin identity as `SageTVFFmpegPlugin` / **OpenSageTV Vibe FFmpeg Plugin**.
- Initial project scaffold.
- Added stock-Sage.jar-compatible Standard plugin.
- Kept `ffmpeg.real.ini` authoritative and manually editable.
- Added line-preserving INI edits, backup and atomic save.
- Added MIM status/capability monitoring bridge.
- Added self-repairing `SageTVTranscoder` launcher design/source.
- Added Linux/Windows manifest templates and STVi starter.
- Completed the stock SageTV7/SageTV9 STVi setup and monitoring interface.
- Added safe startup repair of the root `SageTVTranscoder` bridge while leaving
  stock `ffmpeg` byte-identical.
- Added bridge fallback to stock `ffmpeg` when the plugin runtime is absent,
  disabled, partially installed, or uninstalled.
- Added line-preserving live-INI edits, backup creation, user customization
  preservation, and default-only upgrade behavior.
- Added expanded MIM status/capability exposure and real software fallback when
  hardware initialization is unavailable.
- Added deterministic Java, Linux, Windows, STVI, manifest, and checksum
  packaging plus current GitHub Actions source/package checks.
- Added the common Vibe root workflow, update runner, changed-files handoff, and
  eleven-project build-environment integration.
- Passed clean install, upgrade, duplicate-free STVI reimport, launcher repair,
  uninstall, restart, stock fallback, and clean reinstall on `.232` without a
  modified `Sage.jar`.
- Passed non-Pro Fire TV recorded Fixed/MIM playback, FF/REW, large jumps,
  pause/resume, repeated start, stop/restart, two live channel changes, and
  crash checks using VAAPI `h264_vaapi` server encoding.
- Captured 25 seconds of 1920x1080 HDMI evidence with H.264 video and 48 kHz
  stereo AAC audio; FFmpeg detected no two-second freeze, one-second black
  interval, or two-second silence.
- Passed explicit DVD MIM main-feature playback on isolated `.232` with the
  optional updated Vibe Core resolver. The generated authored DVD used the
  plugin bridge without replacing stock `ffmpeg`; MIM selected VAAPI
  `h264_vaapi`, Android selected a hardware AVC decoder, cadence measured
  1.002x with zero dropped frames, and pause/play, FF, REW, chapter, STOP, and
  teardown recovered cleanly. Ordinary recorded/live plugin use remains
  compatible with an unmodified stock `Sage.jar`; DVD MIM specifically needs
  the optional Core DVD transform integration.
