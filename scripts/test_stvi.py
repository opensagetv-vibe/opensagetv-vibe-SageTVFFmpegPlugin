#!/usr/bin/env python3
"""Static stock-STV contract checks for the FFmpeg plugin STVi."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path


def symbols(root: ET.Element) -> list[str]:
    return [value for node in root.iter() if (value := node.attrib.get("Sym"))]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stvi", type=Path, required=True)
    parser.add_argument("--stock-stv", type=Path, required=True)
    args = parser.parse_args()
    module = ET.parse(args.stvi).getroot()
    stock = ET.parse(args.stock_stv).getroot()
    module_symbols = symbols(module)
    stock_symbols = set(symbols(stock))
    assert len(module_symbols) == len(set(module_symbols)), "STVi contains duplicate symbols"
    assert "VIBEFFMPEG-IMPORT-HOOK" in module_symbols
    assert "VIBEFFMPEG-SETUP-ITEM" in module_symbols
    assert "OPUS4A-125847" in stock_symbols, "stock Setup insertion point changed"
    assert "NFLX1-1589546" in stock_symbols, "stock Plugin Configure menu changed"
    text = args.stvi.read_text(encoding="utf-8")
    assert "AutoCleanupSTVImportedHook" in text
    assert "GetWidgetParent(Child" in text, "reimport duplicate guard missing"
    assert "SageTVFFmpegPluginLinux" in text and "SageTVFFmpegPluginWinx64" in text
    assert text.count('AddStaticContext(&quot;Plugin&quot;, Plugin)') == 2
    assert "LaunchMenuWidget" in text
    assert text.count('Sym="VIBEFFMPEG-SETUP-ITEM"') == 1
    print("PASS: stock SageTV7 STVi insertion/configuration contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
