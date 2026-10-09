# Agent instructions

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

Read `HANDOFF.md` first, then `README.md`, `docs/STVI_DESIGN.md`, and the sibling handoff folder `../ffmpeg-mim-changes` when present.

Do not modify stock SageTV `Sage.jar`. Do not replace stock SageTV `ffmpeg`. Do not move MIM source into this repository. Keep `ffmpeg.real.ini` authoritative.

Release validation is impact-based: rerun only gates the release changes could
affect. Do not repeat unrelated completed gates. Run the full gate suite only
when the user explicitly requests it or a broad dependency/architecture change
requires it, and document that reason and scope.

The user's standing rule is to fix and test plugin defects required by the
active task, including test-server plugin updates, without asking again solely
because the fix crosses a Vibe repository boundary. Record the linked task,
proven cause and focused before/after gates. On stock `.175`, installation
changes are limited to plugins: never alter stock Sage.jar, stock FFmpeg,
Core binaries or server installation/configuration files. Preserve settings;
unrelated publication, destructive actions and interrupting recordings/other
users still require their own authority.

If a proven production fix cannot be expressed in the client or this stock-
compatible plugin, the user permits necessary non-stock `.232` Core fixes and
affected tests as a last resort. Document the API gap, preserve optional
negotiation/stock fallback and older-client compatibility. This does not allow
Core/server changes on stock `.175` or Core patches just for testing.


## Stock-server test-control policy

- For any new testing, commissioning, diagnostic, or automation control, first
  implement or extend the stock-compatible `opensagetv-vibe-core-MCP-Plugin`
  using supported `sage.SageTV.api`/`apiUI` calls and verify it against an
  unmodified stock SageTV server.
- Do not patch `Sage.jar`, add private MiniClient events, or change Core merely
  to make a test easier. Existing public APIs, the bounded MCP bridge, and
  external test tooling are the required first option.
- Change Core only when the required production runtime behavior cannot be
  expressed through the stock plugin/API boundary. Document the proven API
  gap, keep the extension optional and negotiated with a safe stock fallback,
  and verify older clients and installations remain unaffected.

## Pre-commit task-list maintenance

Immediately before every repository commit, clean `TASKS.md`: move every
completed `[x]` item out of the active task sections and into
`## Checklist change ledger`. Preserve stable IDs, acceptance evidence, order,
and enough source/parent context to understand the result. Never delete
completion history. Active task sections must contain unchecked work only;
checked boxes may appear only inside the checklist change ledger. Regenerate
the project manifest when the repository tracks one.
