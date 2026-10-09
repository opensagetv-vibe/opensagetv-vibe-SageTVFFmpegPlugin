# OpenSageTV Vibe FFmpeg Plugin tasks

> **Pre-commit task maintenance:** Immediately before every repository commit, move
> completed `[x]` items out of active sections and into
> `## Checklist change ledger`. Preserve IDs, evidence, and context; never
> discard completion history. Active sections contain unchecked work only.

This is the only authoritative backlog for this repository. Completed work is
retained with checked boxes in the checklist change ledger and is also recorded
in `CHANGELOG.md` and `HANDOFF.md`.

The Windows `.185` stock-server validation is complete and recorded in
`CHANGELOG.md` and `HANDOFF.md`.

## Active tasks

- [ ] **PLUGIN-RELEASE-001 - Publish approved beta0.1.5 and catalog update.**
  Pair Linux/Windows manifests with verified MIM0.4.11; preserve stock files,
  existing physical qualifications and explicit limits. Affected Java8/JDK11,
  recovery/HTTP/session/caption/launcher/STVi gates pass. Packaging, exact-HEAD
  green CI, public digest/MD5 verification and upstream catalog submission
  remain; submission is not a merged/live plugin catalog.

No active MIM-DIRECT-005 commissioning work remains. Release packaging and
publication remain separately gated by WORKFLOW.md.

## Checklist change ledger

- 2026-10-09 pre-commit publication review:0.1.5 Java8/JDK11 affected suites,
  stock linkage/STVi/launchers and Linux/Windows manifests paired with0.4.11
  pass. Initial concurrent-build2s HTTP timeout does not recur in the complete
  unchanged JDK11 repeat. Public verification/catalog submission pending;
  no completed active checkoffs, workspace publication priority reviewed.

- 2026-10-08 pre-commit source-sync review: stock-JAR Java8 recovery/identity/
  ticket/HTTP/session/caption/launcher tests and source validation pass. Preserve
  MIM-DIRECT-005 per-device scope and explicit caption limits; no new paired
  runtime or catalog release under Android RELEASE-001. Completed entries
  remain ledger-only and workspace execution order reviewed305.

- [x] **MIM-DIRECT-005 qualified closure,2026-10-08.** Historical staged
  notes below are superseded by integrated production recovery qualification.
  Android DEVICE-002/TABLET-MIM-001/GSY-CAP-001 pass their supported scope on
  stock175 first and GPU232. Fresh Watch -> actual new video -> final Seek;
  never claim atomic Watch or replay ambiguous mutations. Current FFplugin
  efe2bb7b/CoreMCP6fff42f0/MIM6e80a2d9 and APKc62911ae commissioning is recorded
  in Android artifacts/results/DEVICE-002/owned-watch-recovery.json.
  Both GSY real-failure61.328/64.402s, owned readable2s86.354/77.837s and
  unavailable69.333/68.573s pass. Media3/legacy, latest nonzero intent,
  playing/paused public boundary, real canceled Watch409 and false/old/untyped
  MIM feature guards pass; zero jobs/contexts and stock file hashes intact.
 232 fresh VAAPI hardware decode+encode confirmed; GPU load unmeasured.
 175500ms CEA visual stress remains malformed, not a sustained-readability PASS;
 2s control readable. Exact prefs/power/borrowed captionfalse restored and
  temporary fixture/import retired. Completed raw recoverable in rootdeleteme.
  No Core/private events, normal runtime MCP dependency, canonical new Windows
  package or publication. Paired release packages retain their existing gate.

- [x] **MIM-DIRECT-005 - Stock-compatible watch recovery for MPEG-2-less
  owned-Transcode clients.** Dependency of Android TABLET-MIM-001/DEVICE-002.
  Safe ordinary Fixed is verified, but full owned Transcode is not supported
  on SM-P610. Prove a bounded optional plugin-owned recovery transaction through
  public api/apiUI only: capture the exact client's typed current MediaFile,
  watch/paused state and coordinate before disconnect; restore only that same
  commissioned context/media on a fresh ordinary Fixed connection. Reject
  expired, canceled, mismatched, ambiguous or superseded tickets and avoid
  duplicate Watch/Seek. No normal runtime MCP dependency, Core/private event
  change, arbitrary source path or change to older clients.
  Source review2026-10-08: MiniPlayer.java793-807 ties Pull to container and
  advertised native video/audio support. Non-DVD GetMediaTime is airing/wall-
  clock based; GetRawMediaTime is current-file-segment-relative. Do not
  interchange these coordinates. GetUIContextNames/apiUI, GetCurrentMediaFile,
  Watch, Seek and Pause are the public boundary to prove. Core MCP already
  exposes both clocks for testing; no test-only Core change is required.
  Current revision301: recovery is integrated/advertised and deployed only as
  a candidate on175/232; stock Core/root FFmpeg hashes remain unchanged.
  Missing/false/untyped MIM ownedDirectStreams refuses Direct safely. Stock175
  double-startup-failure68.390s passes fresh H.264 A/V/final Seek/visible generated
  frame. Normal owned78.483s passes A/V, FF/REW and pause/resume after Android
  moved HTTP outside its UI progress lock; picture/caption and232 GPU remain.
  Direct-only real audio-only fallback still failed134.299s, so that track
  trigger is under affected qualification, not a full recovery PASS. Gate invalid/
  replayed tickets and older/missing plugin before negotiated Android enablement.
  Physically prove actual owned H.264/readable captions, seeks/pause/STOP and
  forced creation/replacement failure -> real ordinary Fixed output, preserved
  settings/identity and zero orphan producers. Stock175 first (CPU/no-GPU),232
  for actual GPU decode/encode. Do not repeat unrelated completed matrices.
  First stage implemented locally: DirectWatchSnapshot captures only an exact,
  unique12-hex MiniClient context, typed MediaFile/ID, distinct absolute/raw
  clocks and actual playing value. Reject loading/DVD/missing clocks/API
  failures and observed source/context replacement. Snapshot capture performs
  no Watch/Seek/Pause and is not wired to production negotiation. Compile against stock
  JARd76ded98 at Java8 bytecode level; deterministic tests pass on JDK8/11/17
  and script syntax passes. Production HTTP wiring, client fresh-session
  integration and forced-failure physical gates remain. Core MCP175
  discovery confirms the existing test bridge; no server binaries/configuration/
  service changed. No atomic Watch guarantee or full runtime support claimed.

MIM-DIRECT-005 ticket custody sub-stage is also implemented: at most4
server-local snapshots,120s monotonic leases, opaque handles, exact context/
media/intent matching, one-use claim, cancellation, newer-intent supersession
and fail-closed clock regression. Deterministic tests pass on JDK8/11/17 and
all plugin Java compiles against stock API/Java8 in isolated staging. No
production endpoint/restore yet. Claim is custody transfer, not atomic playback
proof; cancellation after claim/latest intent guards each staged API call.
Core review confirms time/state reads cross the decoder/socket: never call
settled-player capture synchronously from OPENURL/SEEK/replacement.
Initial-failure source/intent capture is now implemented without those reads:
exact current UI MediaFile must match the library-resolved typed source, with
single-segment/non-DVD guards, explicit requested playing/relative target,
overflow checks and a separate intent-vs-observation flag. Zero targets stay
zero. Source review verifies GetCurrentMediaFile directly reads currFile and
GetFileStartTime is global recording/file metadata; no decoder clock/state API
is called by captureIntent. Wrong source, replacement, multi-segment/DVD,
negative offset/overflow and no-decoder-query tests pass on JDK8/11/17. This
remains unexposed in production and cannot by itself prove restoration.
Coordinator plus injectable candidate HTTP routes now pass local stock-API
Java8 compilation/JDK8/11/17 tests. Pause waits until independently witnessed
replacement readiness: stock Watch returns an AsyncWatch task marker. Source,
context, cancel-after-claim/Pause, one-use Watch/Seek, expiry, closed listener
and old-constructor/no-route guards are tested. Typed source/path/clock stay
private. Existing production construction still supplies no recovery service,
capabilities do not advertise it, and no deployed JAR/client changed.
Stock175/tablet644596ae public Watch/Seek boundary passes91.843s playing and
98.834s paused with actual Exynos H.264 A/V, visible source PTS43.710/42.042
paused/46.947 resumed and15preferences/power restored. These are existing
MCP-controlled API proofs, not the candidate endpoint or automatic fallback
PASS. Stock Fixed resets the Android decoder epoch after FLUSH; independent
source-burned labels/public raw clock verify source position. Paused readiness
must await the fresh FLUSH, not accept the preceding player's clock.
Next: production recovery wiring and latest-intent-aware Android fresh-session
integration, then normal/forced-failure captions/control/cleanup gates on175
and GPU232. No enabled input capability or full owned-support claim yet.

- [x] MIM-DIRECT-005 staged restore/candidate HTTP/API-boundary sub-stage,
  2026-10-08: stock-API/Java8 compilation and JDK8/11/17 coordinator/HTTP guards
  pass, existing Direct HTTP regression passes. Pause is deferred until ready;
  cancel/new source blocks stale Seek, listener shutdown revokes late custody.
  Stock175/tablet public fresh Watch/42s Seek playing91.843s and paused98.834s
  have reviewed source labels and15prefs/exact power restoration. Parent open
  for Android integration/deployment/forced failure; no production route,
  negotiation, Core change, server restart, normal-runtime MCP dependency or
  full owned Transcode PASS. Compact report in Android DEVICE-002 results.

- [x] MIM-DIRECT-005 initial-failure capture sub-stage2026-10-08: read-only
  metadata/explicit intent uses exact UI current MediaFile/library identity,
  single-segment/non-DVD and separate coordinate/provenance checks; no decoder
  state/clock API calls. Zero target, mismatch/replacement, multi-segment,
  DVD, invalid offset/overflow tests pass on JDK8/11/17 against stock API.
  No HTTP exposure, input enablement, restore or physical fallback PASS yet.

- [x] MIM-DIRECT-005 ticket-custody sub-stage2026-10-08: capacity4, monotonic
 120s expiry, opaque exact owner/source/intent handles, one-use claim, cancel
  and newer-intent replacement pass deterministic JDK8/11/17 checks. Whole
  plugin source compile against stock API/Java8 passes in isolated staging.
  Coordinator/latest-intent-after-claim and no decoder reads at startup remain.

- [x] MIM-DIRECT-005 read-only snapshot sub-stage2026-10-08: stock API compile
  against Sage.jar d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0
  and JDK8/11/17 deterministic exact-identity/coordinate/race/unavailable tests
  PASS. Source stays server-local/typed; no replay or public endpoint.
  Parent remains active for bounded ticket/cancellation/restore/physical proof.

- [x] **MIM-DIRECT-004 - Protect unfinished Direct segments during cleanup.**
  Dependency of Android DEVICE-003 / MINIMX-MIM-001. On232, a read-only
  open-file witness proves segment9 is unlinked before publication after a
  Copy seek; later M3U8 references the missing file and Android receives404
  media_not_ready. Cleanup must retire only old segments below the lowest
  published sequence, preserving newer writers, referenced files, retention
  grace and empty-playlist safety without platform-specific open-file probes.
  Focused Java session/HTTP and stock-plugin tests pass. Deploy only changed
  Direct-service classes on232 with recoverable backup and unchanged Core,
  runtime and INI; verify the original Copy seek/captions in both Exo families,
  Direct Transcode smoke and zero-orphan cleanup. Do not reopen unrelated
  completed legacy/device matrices or publish before required release gates.
  Closed2026-10-08: focused stock-Sage.jar plugin/session/HTTP/caption/launcher
  tests PASS. Minimal232 overlaye62bee77 changes only outer/Session class bytes;
  Core/stock FFmpeg/INI hashes unchanged, recoverable original backup retained.
  Original longer Copy Media3224.062s and legacy247.007s PASS:20s Off-On14
  new cues each, FF/REW5439/10571 and5937/11648ms, actual pause/resume/ownership.
  Provider18 listed/no missing/segment9 exists. Clean2s CEA Direct Transcode
  200.781s passes readable CC1 initial/post-seek/Off and controls with fresh
  VAAPI hardware decode+encode. CC2 blank is expected for one608channel/708svc1.
  Final APIs have zero caption/Direct sessions and no FFmpeg/MIM/transcoder
  process. All32 app prefs/server CC/power restored. Portable unit tests cover
  Windows semantics; this patch has no new physical Windows claim/publication.

- [x] **MIM-FIXED-003 - Stock and legacy compatibility matrix (2026-10-05).**
  Closed after the final physical legacy-extender row passed on a replacement
  HD200 against unmodified stock `.175`. MPEG-2/AC-3/CEA, generated H.264 codec
  transitions, authored-DVD menus/title/chapter/pause/audio/subpicture/return,
  STOP/Home reconnect, and a growing 5.1-to-7.1 transition retained advancing
  A/V and the same extender UI context. The optional plugin does not alter the
  HD200 path. UK H.264/AC-3 samples also remained stable; missing Teletext/DVB
  rendering is the proven 2010-era stock FFmpeg boundary, not a regression.
  The apparent shutdown regression was independently traced to Automatic
  Power Off 1.0.7 and ceased after the user removed that plugin. Earlier
  Android cross-player, missing/disabled/old/failed-plugin, Linux and Windows
  Copy/Transcode, completed/growing, codec/caption, seek/fallback/restart, and
  zero-orphan evidence completes the matrix. Stock `Sage.jar` was unchanged.
  Evidence: workspace `artifacts/mimfix003-hd200/RESULTS.md`.

- [x] **MIM-FIXED-003 stock-Windows lifecycle sub-gate (2026-10-05).** On
  unmodified stock Windows `.185` and non-Pro `.25`, Media3 Fixed/MIM Direct
  Transcode and Direct Copy each retained plugin-owned HTTP playback through
  deterministic seek, FF/REW recovery, pause/play, repeated source start,
  Stop/exact rewatch, and crash checks. Transcode delivered hardware H.264 and
  AC-3; Copy preserved hardware MPEG-2 and AC-3. A real `SageTV64` restart
  terminated the active Direct session; the API returned `ready` with zero
  sessions, and a fresh post-restart strict Direct Transcode session again
  reached sustained A/V. Final API and process checks found zero caption/Direct
  sessions and no launcher, MIM, or FFmpeg process. The service-account
  persistent `V:` mapping survived restart, and all 110 Android settings plus
  sleep policy were restored. Existing Windows QSV/software, CEA/Teletext/DVB,
  and post-seek caption evidence was retained rather than rerun. Stock
  `Sage.jar` was unchanged; no commit or publication occurred.

- [x] **MIM-FIXED-003 caption-after-seek sub-gate (2026-10-03).** Equal-duration
  0.5-second/2-second generated CEA-608 fixtures were compared on stock
  `.175` Pull, stock Windows `.185` Direct, and Vibe `.232` Direct with non-Pro
  `.25`. After FF, the 2-second Direct cue rows visibly read 30/32/34 at
  video ~35 seconds on both Windows and Linux; the fast fixture also displays
  correctly spelled text, though individual stills may hit short gaps. Linux
  Direct's malformed rows correlated with missing caption pairs from its
  default ~208 KiB loopback UDP receive queue. A bounded 4 MiB receive request
  restored complete raw records; a decode-order cursor fix prevents skipping
  future-PTS records. `dev.cmd test` passes, including deterministic cursor
  coverage. A minimal class overlay, not a published plugin build, remains
  installed on `.232` with its original JAR recoverable; `.185` and `.175`
  binaries stayed unchanged. Temporary fixture imports/files were removed and
  rescanned. The parent matrix remains open.


### Archived completed checklist items (2026-09-30)

These completed items were moved from active task sections immediately
before commit. Stable IDs, acceptance evidence, and source context are
preserved; active sections contain unchecked work only.

#### From `# OpenSageTV Vibe FFmpeg Plugin tasks`

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


#### From `# OpenSageTV Vibe FFmpeg Plugin tasks`

Parent context: `- [ ] **MIM-FIXED-003 - Stock and legacy compatibility matrix.** Validate both`

  - [x] Prove Direct Transcode deinterlacing Off uses full-GPU VAAPI on Linux
    and full-GPU QSV on Windows, with physical non-Pro startup, seek,
    pause/resume, hardware-client decode, STV CEA callback, teardown, and
    zero-orphan evidence. Linux Auto/On also use full-GPU VAAPI; Windows
    Auto/On truthfully report the Haswell QSV VPP mixed fallback.

  - [x] Prove Linux active/growing Direct Transcode startup, bounded live-edge
    seek, server-owned REW/FF recovery, and two channel transitions on non-Pro
    `.25` / `.232`, with retained ownership, clean teardown, and all 103
    settings restored.

The explicitly approved v0.1.3 GitHub prerelease is published with all four
verified plugin/runtime packages. Version 0.1.4 packages the completed
active/growing Direct-session correction and is pending publication. The
SageTV catalog update is submitted in OpenSageTV/sagetv-plugin-repo pull
request 126. MIM-FIXED-003 is complete; the final legacy-extender evidence is
recorded in the checklist ledger above.
