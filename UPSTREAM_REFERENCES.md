# Upstream references used for this seed

Verified on 2026-09-20:

- `opensagetv-vibe/opensagetv-vibe-ffmpeg-mim` main tree: `f5802b4433a8d49c92ea75331077d6a8da7b334a`
  - MIM source at that snapshot reports `MIM_VERSION = 0.4.9`.
  - Existing commands include `--mim-version`, `--mim-status`, and a minimal `--mim-capabilities`.
  - Existing code includes capability cache, Linux GPU vendor checks, bounded hardware encode preflight and backend selection.
- `google/sagetv` master tree: `2e5a892703eaf1f56b1efb7f509021377e65ab69`
  - `FFMPEGTranscoder.getTranscoderPath()` checks `SageTVTranscoder` before `ffmpeg`.
  - Stock plugin API supports Standard and STVI plugins plus get/set plugin configuration calls.
- `jvl711/MediaFormatParserPlugin` master tree: `597579a726818152e7def04d4e31b68dca6906d5`
  - Used only as a reference for a stock SageTV Standard plugin/manifest pattern. It is not a dependency.

Codex should refresh upstream before final merge and rebase the proposed MIM patch if current `main` has advanced.
