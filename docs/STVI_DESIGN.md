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
- `hardwareTest.state`
- `hardwareTest.summary`
- `hardwareTest.<backend>.decode`
- `hardwareTest.<backend>.filter`
- `hardwareTest.<backend>.encode`
- `hardwareTest.<backend>.pipeline`
- `hardwareTest.qsv.fallbackPipeline`
- `hardwareTest.reportPath`
- `hardwareTest.raw`
- `captionSideChannel.status`

Suggested actions (call `SetPluginConfigValue(plugin, actionName, "1")`):

- `action.reload`
- `action.runHardwareTest`
- `action.exportHardwareReport`
- `action.repairLauncher`
- `action.restoreDefaultIni` (must ask for confirmation in STVi)

Implemented screens:

- Playback & Transcoding
- Hardware Setup
- Hardware Test (Run, Export, and read-only results)
- Runtime Status
- Logging & Maintenance

The STVi uses one self-contained two-pane screen generated from SageTV7's
Detailed Setup and Plugin Configure widgets. The compact category rail stays
on the left. The stable rows retain Detailed Setup's native `SetupArea`,
`CurrSetupArea`, and `NeedLostCleanup` contract. Each row's focus hook applies
its literal setup area and redraws the chosen category's setting subset in the
editor on the right; Select is not required and does not open another
full-screen category menu. The
editor resolves the installed Linux or
Windows plugin directly, so no plugin context has to survive a menu transition.
Opening Hardware Test never starts GPU work, and exporting never silently
reruns a test.

Playback & Transcoding also exposes the optional Fixed caption side-channel
enable switch, bind address, and API port. The service treats the local LAN as
its trust boundary and therefore has no user token to configure. Media starts
remain restricted to SageTV-authorized source paths, while caption and media
operations use short-lived, opaque reservation/session identifiers. The
listener pool is not advertised to MIM until a Vibe client creates a
short-lived reservation, so installing or enabling the plugin does not alter
an older client's Fixed command.

The five current categories fit in the left viewport using native Detailed
Setup row artwork. Generation deliberately fails if a sixth category is
added; overflow must first receive a separately tested stock-style scrolling
rail instead of being silently clipped. Do not replace this rail with bare `Item` widgets in an
un-themed grid; those can render as text on a MiniClient without a focus target
or visible selection state. The right editor retains stock five-row pagination
so labels, help text, and controls do not overlap on remote 1920x1080 UIs.

Read-only Hardware Test results and Runtime Status suppress per-row help text
and render as two synchronized text columns: a fixed left-aligned label column
and a fixed left-aligned value column. Their category footer carries the shared
explanation. Neither column is focusable or contains an edit action, so a
remote cannot open Android's keyboard for a status value. Do not depend on
padding characters or a nominally monospaced font for alignment. Keep
displayed values short enough to remain useful on a 1920x1080 MiniClient; for
example, display the normalized FFmpeg version rather than the complete FFmpeg
banner. Runtime Status must use the complete content height so every backend
through Software is visible without ellipses.

Each value editor is explicitly right-anchored and occupies the physically
measured stock Detailed Setup value-cell bounds. Its centered value and cloned
stock focus artwork must span the complete selection cell; allowing an `Item`
to shrink to its text produces a short underline that does not match stock.
All cloned configuration text disables inherited text shadow so an explicit
white foreground is not rendered as a dark outlined label on a MiniClient.

Choice and Multichoice dialogs keep the stock configuration actions and stock
dialog background, but own their compact layout, title, option text,
focused-row artwork, and pagination dependencies. Do not restore dependencies
on stock Theme `2131`, shared radio/checkmark panels `2661`/`4611`, or shared
pagination references `6454`-`6461`: those can import without error while
drawing an empty or incorrectly themed option list on a remote MiniClient.

Do not make STVi the source of truth. Manual edits to `ffmpeg.real.ini` must appear on the next refresh.
