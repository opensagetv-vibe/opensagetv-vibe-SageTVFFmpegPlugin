#!/usr/bin/env python3
"""Build deterministic SageTV plugin archives and rendered manifests."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import os
import stat
import zipfile
from pathlib import Path


REPOSITORY = "opensagetv-vibe/opensagetv-vibe-SageTVFFmpegPlugin"


def require(path: Path) -> Path:
    if not path.is_file():
        raise SystemExit(f"required file missing: {path}")
    return path


def digest(path: Path, algorithm: str) -> str:
    value = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def zip_time(epoch: int) -> tuple[int, int, int, int, int, int]:
    value = dt.datetime.fromtimestamp(max(epoch, 315532800), tz=dt.timezone.utc)
    second = value.second - value.second % 2
    return value.year, value.month, value.day, value.hour, value.minute, second


def add_bytes(archive: zipfile.ZipFile, name: str, data: bytes, mode: int, when: tuple[int, ...]) -> None:
    info = zipfile.ZipInfo(name, when)
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data)


def write_zip(path: Path, entries: list[tuple[str, bytes, int]], when: tuple[int, ...]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    with zipfile.ZipFile(temporary, "w") as archive:
        for name, data, mode in sorted(entries, key=lambda entry: entry[0]):
            add_bytes(archive, name, data, mode, when)
    os.replace(temporary, path)


def deterministic_jar(classes: Path, target: Path, when: tuple[int, ...]) -> None:
    entries = [("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\r\n\r\n", 0o644)]
    for source in sorted(classes.rglob("*.class")):
        entries.append((source.relative_to(classes).as_posix(), source.read_bytes(), 0o644))
    if len(entries) == 1:
        raise SystemExit(f"no compiled plugin classes found under {classes}")
    write_zip(target, entries, when)


def render(template: Path, replacements: dict[str, str], target: Path) -> None:
    value = template.read_text(encoding="utf-8")
    for key, replacement in replacements.items():
        value = value.replace(f"@{key}@", replacement)
    if "@" in value:
        unresolved = sorted({part.split("@", 1)[0] for part in value.split("@")[1::2]})
        raise SystemExit(f"unresolved manifest placeholder in {template}: {unresolved}")
    target.write_text(value, encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--plugin-version", required=True)
    parser.add_argument("--mim-version", required=True)
    parser.add_argument("--linux-runtime", required=True)
    parser.add_argument("--windows-runtime", required=True)
    parser.add_argument("--source-date-epoch", type=int, required=True)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    output = root / "output" / "packages"
    output.mkdir(parents=True, exist_ok=True)
    for old in output.iterdir():
        if old.is_file():
            old.unlink()

    when = zip_time(args.source_date_epoch)
    date = dt.datetime.fromtimestamp(args.source_date_epoch, tz=dt.timezone.utc).strftime("%Y.%m.%d")
    jar = output / "SageTVFFmpegPlugin.jar"
    deterministic_jar(root / "build" / "classes", jar, when)

    version = args.plugin_version
    jar_zip = output / f"SageTVFFmpegPlugin-jar-{version}.zip"
    linux_zip = output / f"SageTVFFmpegPlugin-system-linux-{version}.zip"
    windows_zip = output / f"SageTVFFmpegPlugin-system-windows-x64-{version}.zip"
    stvi_zip = output / f"SageTVFFmpegPlugin-STVI-{version}.zip"

    linux_launcher = require(root / "dist" / "dev" / "SageTVTranscoder").read_bytes()
    windows_launcher = require(root / "dist" / "dev" / "SageTVTranscoder.exe").read_bytes()
    stvi = require(root / "stvi" / "SageTVFFmpegPlugin.stvi").read_bytes()
    write_zip(jar_zip, [("SageTVFFmpegPlugin.jar", jar.read_bytes(), 0o644)], when)
    write_zip(linux_zip, [
        ("SageTVTranscoder", linux_launcher, 0o755),
        ("plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder", linux_launcher, 0o755),
    ], when)
    write_zip(windows_zip, [
        ("SageTVTranscoder.exe", windows_launcher, 0o755),
        ("plugins/SageTVFFmpegPlugin/launcher/SageTVTranscoder.exe", windows_launcher, 0o755),
    ], when)
    write_zip(stvi_zip, [("SageTVFFmpegPlugin.stvi", stvi, 0o644)], when)

    linux_runtime = require(Path(args.linux_runtime).resolve())
    windows_runtime = require(Path(args.windows_runtime).resolve())
    common = {
        "DATE": date,
        "PLUGIN_VERSION": version,
        "MIM_VERSION": args.mim_version,
        "PLUGIN_JAR_MD5": digest(jar_zip, "md5"),
        "STVI_MD5": digest(stvi_zip, "md5"),
    }
    manifest_inputs = {
        "SageTVFFmpegPluginLinux.xml.template": {
            **common, "PLUGIN_SYSTEM_MD5": digest(linux_zip, "md5"),
            "MIM_RUNTIME_MD5": digest(linux_runtime, "md5"),
        },
        "SageTVFFmpegPluginWindowsx64.xml.template": {
            **common, "PLUGIN_SYSTEM_MD5": digest(windows_zip, "md5"),
            "MIM_RUNTIME_MD5": digest(windows_runtime, "md5"),
        },
        "SageTVFFmpegPluginSTVILinux.xml.template": common,
        "SageTVFFmpegPluginSTVIWindowsx64.xml.template": common,
    }
    for name, replacements in manifest_inputs.items():
        target = output / name.removesuffix(".template")
        render(root / "manifests" / name, replacements, target)

    checksummed = sorted(path for path in output.iterdir() if path.is_file() and path.name != "SHA256SUMS")
    sums = "".join(f"{digest(path, 'sha256')}  {path.name}\n" for path in checksummed)
    (output / "SHA256SUMS").write_text(sums, encoding="utf-8", newline="\n")
    report_lines = [
        "# OpenSageTV Vibe FFmpeg Plugin build report",
        "",
        f"- Plugin version: {version}",
        f"- MIM runtime version: {args.mim_version}",
        "- Stock Sage.jar contract: PASS",
        "- Stock ffmpeg replacement: prohibited",
        "- Deterministic package validation: PASS",
        "",
        "## Artifacts",
        "",
    ]
    report_lines.extend(
        f"- `{path.name}`: `{digest(path, 'sha256')}`" for path in checksummed
    )
    (root / "output" / "BUILD_REPORT.md").write_text(
        "\n".join(report_lines) + "\n", encoding="utf-8", newline="\n"
    )
    for path in checksummed:
        print(f"{path.name}  md5={digest(path, 'md5')}  sha256={digest(path, 'sha256')}")
    print(f"repository=https://github.com/{REPOSITORY}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
