# OpenSageTV Vibe FFmpeg Plugin tasks

This is the only authoritative backlog for this repository. Completed work is
retained with checked boxes and is also recorded in `CHANGELOG.md` and
`HANDOFF.md`.

The Windows `.185` stock-server validation is complete and recorded in
`CHANGELOG.md` and `HANDOFF.md`.

- [x] **MIM-FIXED-001 - Stock-compatible caption side channel.** Extend the
  Standard plugin and MIM session boundary without modifying `Sage.jar` so an
  opted-in Vibe Android Fixed session can obtain synchronized CEA-608/708 data
  extracted from the original compressed video while the primary video uses
  full hardware decode/deinterlace/encode. Use LAN-scoped access with bounded,
  opaque session identifiers; expose capability, caption, clock/status, and teardown endpoints;
  never expose media paths or credentials; and leave the established single
  SageTV Fixed output unchanged for every older client. Absence, old version,
  endpoint failure, caption failure, or MIM failure must automatically retain
  ordinary stock Fixed playback. Prove CEA Off/On, seek, pause/resume, source
  replacement, live/growing input, teardown, and no orphan processes on an
  unmodified stock server before enabling Auto selection.
- [x] **MIM-FIXED-002 - Optional MIM Direct playback transport.** After the
  caption-only side channel passes, add a separately selectable Standard-plugin
  transport in which the plugin owns the MIM transcode session and exposes
  synchronized media, caption/subtitle, status, seek, reconnect, and teardown
  endpoints to Vibe Android. Preserve SageTV UI, OSD, watched state, and control
  authority; require explicit capability/version negotiation; bind every
  request to the active client/media session; and provide two explicit output
  policies: `Direct Copy`, which performs no video/audio decode or encode and
  only remuxes when the negotiated wire container requires it, and `Direct
  Transcode`, with bounded full-GPU -> GPU-encode/software-decode -> software
  fallback. Direct Copy must preserve original video, audio, CEA, Teletext,
  DVB, language, and timing metadata; an incompatible copy contract must be
  reported and fall back rather than silently transcoding unless the user
  explicitly selected an Auto policy that permits it. Do not patch Core or add
  private MiniClient events. Missing/failed support must fall back to ordinary
  SageTV Fixed without interrupting playback.
- [ ] **MIM-FIXED-003 - Stock and legacy compatibility matrix.** Validate both
  optional modes against unmodified `.175` and a clean stock Windows server,
  with the plugin installed, absent, disabled, old, and deliberately failed.
  Confirm old Android MiniClient/extender behavior is byte-for-byte unchanged;
  cover Direct Copy and Direct Transcode with completed and growing
  MPEG-2/H.264 input, AC-3/AAC, CEA-608/708,
  Teletext, DVB bitmap subtitles, seek/Comskip, channel/program transitions,
  reconnect, STOP, server restart, and process cleanup. Record full-GPU and
  each fallback stage truthfully rather than inferring success from an encoder
  name.
  - [x] Prove Direct Transcode deinterlacing Off uses full-GPU VAAPI on Linux
    and full-GPU QSV on Windows, with physical non-Pro startup, seek,
    pause/resume, hardware-client decode, STV CEA callback, teardown, and
    zero-orphan evidence. Linux Auto/On also use full-GPU VAAPI; Windows
    Auto/On truthfully report the Haswell QSV VPP mixed fallback.

The explicitly approved v0.1.3 GitHub prerelease is published with all four
verified plugin/runtime packages. The SageTV catalog update is submitted in
OpenSageTV/sagetv-plugin-repo pull request 126. MIM-FIXED-003 remains open only
for the deferred compatibility rows above.
