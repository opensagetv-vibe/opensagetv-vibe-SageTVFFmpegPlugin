#!/usr/bin/env python3
"""Validate release archive boundaries and rendered SageTV manifests."""

from __future__ import annotations

import argparse
import hashlib
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def md5(path: Path) -> str:
    return hashlib.md5(path.read_bytes()).hexdigest()


def members(path: Path) -> set[str]:
    with zipfile.ZipFile(path) as archive:
        return set(archive.namelist())


def manifest_packages(path: Path) -> list[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return [
        (package.findtext("Location", ""), package.findtext("MD5", ""))
        for package in root.findall("Package")
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--plugin-version", required=True)
    parser.add_argument("--mim-version", required=True)
    parser.add_argument("--linux-runtime", required=True)
    parser.add_argument("--windows-runtime", required=True)
    args = parser.parse_args()
    root = Path(args.root).resolve()
    out = root / "output" / "packages"
    version = args.plugin_version
    jar_zip = out / f"SageTVFFmpegPlugin-jar-{version}.zip"
    linux_zip = out / f"SageTVFFmpegPlugin-system-linux-{version}.zip"
    windows_zip = out / f"SageTVFFmpegPlugin-system-windows-x64-{version}.zip"
    stvi_zip = out / f"SageTVFFmpegPlugin-STVI-{version}.zip"

    assert members(jar_zip) == {"SageTVFFmpegPlugin.jar"}
    assert members(linux_zip) == {
        "SageTVTranscoder", "plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder"
    }
    assert members(windows_zip) == {
        "SageTVTranscoder.exe", "plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder.exe"
    }
    assert members(stvi_zip) == {"SageTVFFmpegPlugin.stvi"}
    with zipfile.ZipFile(jar_zip) as outer:
        jar_bytes = outer.read("SageTVFFmpegPlugin.jar")
    temporary = out / ".verify-plugin.jar"
    temporary.write_bytes(jar_bytes)
    try:
        with zipfile.ZipFile(temporary) as plugin_jar:
            names = plugin_jar.namelist()
            assert any(name.startswith("org/opensagetv/vibe/ffmpeg/") for name in names)
            assert not any(name.startswith("sage/") for name in names)
    finally:
        temporary.unlink(missing_ok=True)

    expected = {
        f"SageTVFFmpegPlugin-jar-{version}.zip": md5(jar_zip),
        f"SageTVFFmpegPlugin-system-linux-{version}.zip": md5(linux_zip),
        f"SageTVFFmpegPlugin-system-windows-x64-{version}.zip": md5(windows_zip),
        f"SageTVFFmpegPlugin-STVI-{version}.zip": md5(stvi_zip),
        f"OpenSageTVVibeMIMRuntimeLinux-{args.mim_version}.zip": md5(Path(args.linux_runtime)),
        f"OpenSageTVVibeMIMRuntimeWindowsx64-{args.mim_version}.zip": md5(Path(args.windows_runtime)),
    }
    for manifest in out.glob("*.xml"):
        text = manifest.read_text(encoding="utf-8")
        assert "@" not in text
        assert "github.com/opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin" in text
        if "STVI" in manifest.name:
            root_element = ET.parse(manifest).getroot()
            assert root_element.findtext("PluginType") == "STVI"
            assert root_element.findtext("STVImport") == "SageTVFFmpegPlugin.stvi"
        for location, checksum in manifest_packages(manifest):
            name = location.rsplit("/", 1)[-1]
            assert re.fullmatch(r"[0-9a-f]{32}", checksum)
            assert expected[name] == checksum, (manifest, name, checksum, expected[name])

    sums = (out / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    assert sums
    for line in sums:
        checksum, name = line.split("  ", 1)
        assert hashlib.sha256((out / name).read_bytes()).hexdigest() == checksum
    report = (root / "output" / "BUILD_REPORT.md").read_text(encoding="utf-8")
    assert f"Plugin version: {version}" in report
    assert f"MIM runtime version: {args.mim_version}" in report
    assert "Deterministic package validation: PASS" in report
    print("PASS: deterministic plugin archives, manifests, and checksums")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
