# Agent instructions

Read `HANDOFF.md` first, then `README.md`, `docs/STVI_DESIGN.md`, and the sibling handoff folder `../ffmpeg-mim-changes` when present.

Do not modify stock SageTV `Sage.jar`. Do not replace stock SageTV `ffmpeg`. Do not move MIM source into this repository. Keep `ffmpeg.real.ini` authoritative.


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
