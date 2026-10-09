# Changelog

- 2026-10-08 commissioning: MIM-DIRECT-005 stock-plugin recovery qualifies on
  tablet/stock175 and232 with Media3/legacy/both GSY delegates, latest intent,
  canceled/expired tickets, readable2s CEA, seek/pause and unavailable fallback.
  Actual232 VAAPI decode+encode and zero-orphan/settings restoration verified;
  CPU175500ms stress readability remains an explicit unproven limitation.
  Test candidates are not a new published Linux/Windows release.

## 0.1.5 - 2026-10-09

- Published10 public assets and independently verified their GitHub SHA256s,
  ZIP integrity and catalog MD5 values. Four Standard/STVi manifest updates
  submitted in plugin-repo#127; ordinary catalog availability awaits merge.
  Post-release ledger closure is documentation-only; tag/runtime unchanged.

- Prepare the approved beta update with qualified bounded watch recovery and
  explicit owned-Direct feature negotiation, paired with MIM0.4.11. Stock-JAR
  Java8/JDK11, STVi, snapshot/ticket/coordinator/HTTP/session/caption/launcher
  checks pass. An initial2s loopback timeout occurred during concurrent builds;
  the complete JDK11 repeat passes without a production code change.
  Known500ms caption stress and new Windows-wrapper physical limits remain
  explicit; no unrelated hardware matrix or server restart.

- Integrate bounded stock-API watch-recovery coordination and optional HTTP
  routes with exact source/client/intent, expiry, cancellation and replay guards.
  Defer paused intent until asynchronous Watch has independently loaded.
  Stock-API/Java8 compile and JDK8/11/17 loopback tests pass; plugin-only175
  deployment preserves stock Core/FFmpeg. The bounded recovery physical scope
  is qualified above; asynchronous Watch acceptance alone is not playback proof.
- Require MIM's explicit Boolean ownedDirectStreams capability before
  advertising Direct. Older wrappers safely retain ordinary Fixed instead of
  forwarding unsupported private options into FFmpeg; missing/false/untyped
  and unrelated-feature Java gates pass.

- Protect unfinished plugin-owned Direct segments from retention cleanup:
  only aged segments below the published playlist window are retired. This
  corrects a proven missing-segment404 after Copy seeking without changing
  codecs, timestamps, user INI, stock Core or ordinary Fixed playback.
  Focused Java tests and original MiniMX Copy gates pass in both Exo families;
  clean CEA hardware Transcode and zero-orphan teardown also pass on `.232`.

- Close MIM-FIXED-003 after a replacement HD200 passed the final stock-server
  legacy row for MPEG-2/AC-3/CEA, H.264 transitions, authored DVD, STOP/Home,
  and growing channel change. The optional plugin left the extender path
  unaffected. Stock Teletext/DVB absence was verified as the server's old
  FFmpeg limitation; the observed power-offs came from Automatic Power Off
  1.0.7 and ceased after its removal.

- Completed the remaining current-plugin stock-Windows `.185` MIM Direct
  lifecycle rows with non-Pro `.25`. Direct Transcode and Direct Copy each
  retained owned HTTP playback through seek, pause, repeated source start,
  Stop/exact rewatch, and clean teardown. A real `SageTV64` restart cleared the
  active session; the plugin returned ready, and a fresh strict Direct
  Transcode session recovered sustained A/V. Final capability and process
  checks showed zero active sessions and no launcher, MIM, or FFmpeg process.
- Corrected the optional Fixed/MIM Direct CEA-608 side channel's Linux
  loopback tap by requesting a bounded 4 MiB UDP receive queue before bind.
  Also prevent the caption cursor from advancing past a not-yet-due record
  when MPEG B-frame PTS arrives out of decode order. Focused Java tests and
  an equal-duration 0.5-second/2-second caption fixture comparison pass;
  a test-only class overlay on `.232` visibly restores clean post-seek STV
  text. The broader legacy/lifecycle matrix and publication remain open.

## 0.1.4 - 2026-09-29

- Fixed plugin-owned Direct Transcode startup and seeking for active/growing
  recordings. The active-file hint now reaches MIM, a stale SageTV active hint
  gets one bounded completed-file retry, readiness requires a listed nonempty
  FFprobe-readable segment, and repeated MPEG-TS program/global FFprobe records
  are accepted. Out-of-range live seeks are bounded to the currently available
  source duration with a four-second preroll and return the effective timeline
  anchor to the client.
- Physically validated the affected Linux `.232` / non-Pro `.25` path with
  hardware H.264/AC-3 output, a 24-hour live-edge request clamped to playable
  media, REW/FF recovery, two channel transitions, retained Direct Transcode
  ownership, clean teardown, and restoration of all 103 client settings.

## 0.1.3 - 2026-09-29

- Completed the Linux/Windows Direct deinterlace policy gate. Linux `.232`
  reports full-GPU VAAPI for Auto, On, and Off; stock Windows `.185` reports
  full-GPU QSV for Off and the expected mixed fallback for Auto/On. The Off
  policy passed non-Pro Android startup, seek, pause/resume, hardware-client
  decode, synchronized STV captions, clean teardown, and zero-orphan checks.
- Corrected the Android STV-authority boundary exposed by this gate: when the
  plugin's Fixed caption side channel is actively forwarding CEA through event
  225, the preserved Direct CEA track is no longer rendered locally as a
  duplicate. CC1/CC2/Off cycling passed on `.232` with one renderer.
- Completed the stock-compatible caption side channel and optional MIM Direct
  media service without modifying `Sage.jar` or replacing stock `ffmpeg`.
  Direct Copy leaves video/audio compressed and remuxes only when required;
  Direct Transcode exposes the actual negotiated hardware or fallback stage.
- Changed Direct live output from FFmpeg's HLS muxer to bounded MPEG-TS
  segmentation with an M3U8 list. This avoids the HLS WebVTT conversion crash
  on DVB/Teletext inputs and preserves CEA, Teletext, DVB bitmap, language, and
  timing data in every segment. A monitor removes unlisted stale segments.
- Deployed the corrected plugin to `.232` and passed tokenless capability and
  session APIs, Direct Copy, full-GPU Transcode, caption continuity,
  restart/reconnect, teardown, and Android settings-restoration gates. Stock
  `.175` without the plugin safely retained ordinary Fixed/Push playback.

- Removed the caption/MIM Direct bearer-token setting and authorization
  requirement. Port `31910` is now an explicitly LAN-scoped interface; direct
  media starts still require SageTV-authorized source paths, and all subsequent
  caption/media operations retain bounded opaque reservation/session IDs.
  The STVi no longer displays or stores an API token. Tokenless caption
  reserve/teardown and MIM Direct capability gates passed after deployment to
  `.232`.

- Rebuilt the FFmpeg STVi as one Detailed Setup-style two-pane screen.
  All five categories are stable stock-derived Detailed Setup rows in the
  compact left rail. Moving focus redraws the chosen category's stock-derived
  settings editor on the right without requiring Select or opening another
  full-screen menu.
  Using stable rows avoids stale setup-area context from recycled table cells;
  generation now fails rather than silently clipping if a future sixth category
  is added without a deliberately tested overflow design. Choice and
  Multichoice dialogs use the stock dialog background plus private
  option/focus/pagination widgets, so imported modules do not depend on
  unresolved theme references. This fixes the prior bare grid, separate
  category screens, blank choices, stale right pane, and inconsistent dialog
  appearance on remote MiniClients.
  The content chrome now matches stock Detailed Setup's translucent dark-gray
  panel, white text, light-gray row separators, and scrollbar. Value controls
  use the same measured broad right-hand focus cell and centered label as the
  stock screen instead of a text-width underline. This final layout and its
  choice dialog were physically verified on Vibe server `.232` and non-Pro
  Fire TV `.25` without changing saved playback values.
  Hardware Test results and Runtime Status now use synchronized left-aligned
  label and value text columns, omit repeated explanatory prose, and use short
  labels plus a normalized FFmpeg version. Runtime Status uses the complete
  content height, so VAAPI, QSV, QSV fallback, NVENC, AMF, D3D12VA, and
  Software all remain visible without ellipses. Read-only values are
  non-focusable and carry no edit action, preventing Select from opening
  Android's text-entry keyboard. An unused hardware result consistently shows
  `Not Run` rather than the former lowercase variant.
  Run, Export, and all read-only pipeline results now share the single
  Hardware Test category; the redundant Hardware Test Results rail entry was
  removed.
  The exact `.232` / non-Pro `.25` build physically passed Run (`2 HW PASS`),
  Export, read-only focus skipping, complete Runtime rendering, and Back to
  Setup. Export created both a timestamped report and the stable latest JSON.

- Completed Windows x64 commissioning against the stock SageTV server on
  `.185` and non-Pro Fire TV `.25`. Hardware QSV and forced software/libx264
  Fixed MPEG-TS playback passed audio, pause/resume, FF/REW, large skip,
  Comskip, restart, captions, and clean teardown gates. CEA callbacks,
  Teletext text, and DVB bitmap overlays were physically verified.

- Added deterministic Windows process containment to both launcher layers.
  The root `SageTVTranscoder.exe` owns MIM in a kill-on-close Job Object and
  MIM owns `ffmpeg.real.exe`, so stock SageTV seek/recovery replacement and
  STOP no longer leave orphaned encoder processes.

- Verified authored and real ALADDIN discs through stock-Core native DVD
  playback. Menus, title selection, pause/play, chapter, audio, subtitle, and
  sustained MPEG-2/AC-3 output pass. Stock Windows Core accepting but not
  accurately landing an exact public `Seek(long)` request on the real disc is
  recorded as a Core/DVD-reader boundary, not a plugin failure.

- Confirmed that stock SageTV's startup dump labels normal passive STVi state
  as `Failed` in `PluginWrapper.toString()`. The actual categorized STVi was
  enabled and imported successfully on every client connection, with no import
  errors; no `Sage.jar` change was made.

- Replaced the stock STV's single long plugin configuration list with compact
  Playback, Hardware, Runtime, and Maintenance category screens. Each editor
  resolves the installed Linux/Windows plugin independently, displays five
  relevant rows per page, and retains an All Settings compatibility entry.

- Added explicit **Run Hardware Test** and **Export Hardware Report** actions.
  The bounded MIM diagnostic uses synthetic MPEG-2 input and reports decode,
  filtering, H.264 encode, complete pipeline, and the Windows HD 4600
  D3D11VA/QSV compatibility fallback independently. Export writes
  credential-free timestamped and stable-latest JSON files under the plugin
  reports directory without changing the live INI. Tests are refused while a
  MIM transcode job is active.

- Commissioned development version 0.1.3 on the isolated `.232` Vibe server.
  SageTV loaded the new Standard-plugin JAR, stock `ffmpeg` and the live MIM
  INI remained byte-identical, and the rebuilt Linux MIM runtime passed its
  bounded hardware test with complete VAAPI, QSV, and software pipelines.

- Documented Windows beta-plugin visibility and the safe
  `SageTVPluginsDev.xml` commissioning fallback. The source manifests were
  merged upstream in pull request #124, but the stale aggregate catalog kept
  the plugin invisible to stock SageTV servers; aggregate refresh pull request
  #125 was submitted with validated Windows package URLs, MD5 values, and ZIP
  layouts.

## 0.1.2 - 2026-09-20

- Published the public source repository and beta v0.1.2 release with Linux,
  Windows x64, STVi, rendered manifest, and checksum assets. Submitted the four
  beta catalog entries to `OpenSageTV/sagetv-plugin-repo` in pull request #124.

- Added the optional `DVDStreamTransformProvider` service for updated SageTV
  Core builds. The provider advertises the generic `dvd_mpegts_v1` transport
  and owns the MIM capability probe, command line, process lifetime, bounded
  output queue, and teardown. Its implementation is loaded lazily, so the same
  Standard-plugin JAR remains usable on an unmodified stock `Sage.jar`.
- Added compile-only SPI stubs and a fake-MIM round-trip test. The stubs are
  excluded from the plugin JAR, preventing duplicate `sage.*` classes.

## 0.1.1 - 2026-09-20

- Replaced the Linux C++ launcher with an ABI-neutral POSIX bridge so the
  plugin runs on stock SageTV containers with glibc 2.31 and newer hosts.
- Strengthened launcher health checks to execute the bridge and verify that it
  reaches the packaged MIM runtime, rather than checking only files and hashes.

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
