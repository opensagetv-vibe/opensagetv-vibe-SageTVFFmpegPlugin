#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${CXX:=x86_64-w64-mingw32-g++}"
mkdir -p "$ROOT/dist/dev"
"$CXX" -std=c++17 -O2 -static -static-libgcc -static-libstdc++ "$ROOT/launcher/SageTVTranscoderLauncher.cpp" -lshell32 -o "$ROOT/dist/dev/SageTVTranscoder.exe"
echo "Built dist/dev/SageTVTranscoder.exe"
