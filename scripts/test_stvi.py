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
    module_ids = [value for node in module.iter() if (value := node.attrib.get("ID"))]
    assert len(module_ids) == len(set(module_ids)), "STVi contains duplicate widget IDs"
    assert "VIBEFFMPEG-IMPORT-HOOK" in module_symbols
    assert "VIBEFFMPEG-SETUP-ITEM" in module_symbols
    assert "VIBEFFMPEG-CATEGORY-MENU" in module_symbols
    assert "VIBEFFMPEG-CONFIG-PANEL" in module_symbols
    assert not any(value.startswith("VIBEFFMPEG-CONFIG-MENU-") for value in module_symbols), (
        "category settings must render in the right pane, not separate menus"
    )
    assert "OPUS4A-125847" in stock_symbols, "stock Setup insertion point changed"
    assert "BASE-52048" in stock_symbols, "stock Detailed Setup menu changed"
    assert "BASE-72605" in stock_symbols, "stock Detailed Setup row changed"
    assert any(
        node.attrib.get("ID") == "4873" and node.attrib.get("Name") == "Focused"
        for node in stock.iter()
    ), "stock Detailed Setup focused-row widget changed"
    assert "NFLX1-1589546" in stock_symbols, "stock Plugin Configure menu changed"
    text = args.stvi.read_text(encoding="utf-8")
    assert "AutoCleanupSTVImportedHook" in text
    assert "GetWidgetParent(Child" in text, "reimport duplicate guard missing"
    assert "SageTVFFmpegPluginLinux" in text and "SageTVFFmpegPluginWinx64" in text
    assert text.count('AddStaticContext(&quot;Plugin&quot;, Plugin)') == 2
    assert text.count('Ref="910000" Name="OpenSageTV Vibe FFmpeg Plugin"') == 2
    category_menu = next(
        node for node in module.iter()
        if node.tag.endswith("Menu") and node.attrib.get("Sym") == "VIBEFFMPEG-CATEGORY-MENU"
    )
    category_items = [
        node for node in category_menu.iter()
        if node.tag.endswith("Item")
        and node.attrib.get("Sym", "").startswith("VIBEFFMPEG-CATEGORY-ROW")
    ]
    assert len(category_items) == 5, "category rail must use five stable Detailed Setup rows"
    assert 'Ref="22607" Name="LeftButtonPanelTheme 7"' in text
    assert 'Name="SetupAreas" Sym="VIBEFFMPEG-CATEGORY-DETAILS"' in text
    assert "VIBEFFMPEG-CATEGORY-ROW-01-FOCUS-HOOK" in text
    assert 'AddGlobalContext(&quot;CurrSetupArea&quot;, SetupArea)' not in text
    assert 'If(NeedLostCleanup==null,false,NeedLostCleanup)' in text
    assert "VIBEFFMPEG-CATEGORY-ROW-SELECT" not in text
    assert text.count("-FOCUS-REFRESH") == 5
    assert text.count("-FOCUS-CHANGED") == 5
    assert text.count("-ACTIVATE-REFRESH") == 5
    assert text.count('AddGlobalContext(&quot;CurrSetupArea&quot;, &quot;xVibeFFmpegCategory') >= 6
    assert "VIBEFFMPEG-CATEGORY-ROW-RECYCLED-REFRESH" not in text
    assert "VIBEFFMPEG-CATEGORY-PAGINATION" not in text
    assert 'Ref="6454"' not in text and 'Ref="6461"' not in text
    assert 'Sym="VIBEFFMPEG-CATEGORY-TABLE"' not in text
    assert "VIBEFFMPEG-CATEGORY-GRID" not in text, "bare non-focusable category grid returned"
    for item in category_items:
        assert not any(child.tag.endswith("Menu") for child in item.iter()), (
            "category row must update the same screen rather than opening a menu"
        )
        assert any(
            child.tag.endswith("Conditional") and child.attrib.get("Name") == "Focused"
            for child in item.iter()
        ), f"category row has no focused highlight: {item.attrib.get('Name')}"
        assert any(
            child.tag.endswith("Attribute") and child.attrib.get("Name") == "AreaName"
            for child in item
        ), f"category row is not derived from Detailed Setup: {item.attrib.get('Name')}"
    for title in (
        "Playback &amp; Transcoding", "Hardware Setup", "Hardware Test", "Runtime Status",
        "Logging &amp; Maintenance",
    ):
        assert title in text, f"missing category: {title}"
    assert text.count('GetAvailablePluginForID(&quot;SageTVFFmpegPluginLinux&quot;)') >= 2
    assert text.count('GetAvailablePluginForID(&quot;SageTVFFmpegPluginWinx64&quot;)') >= 2
    assert text.count("<NumRows>5</NumRows>") == 1
    assert text.count("-EDITOR-") > 0, "configuration controls lack focused-row artwork"
    assert "PluginValue = GetPluginConfigValue(Plugin, Setting)" not in text
    assert "Enter the new value for" not in text, "read-only text row still opens an editor"
    assert "GetPluginConfigHelpText(Plugin, Setting)" not in text, (
        "generic per-setting help still reserves an empty second line"
    )
    text_editor = next(
        node for node in module.iter()
        if node.tag.endswith("Item") and node.attrib.get("Name") == "Text"
    )
    assert any(
        child.tag.endswith("FocusableCondition") and (child.text or "").strip() == "false"
        for child in text_editor
    ), "read-only text row remains focusable"
    assert "VIBEFFMPEG-CATEGORY-RIGHT-TITLE" not in module_symbols
    assert "VIBEFFMPEG-HARDWARE-DASHBOARD" in module_symbols
    assert "VIBEFFMPEG-RUNTIME-DASHBOARD" in module_symbols
    assert "VIBEFFMPEG-AVAILABILITY-DASHBOARD" not in module_symbols
    assert "VIBEFFMPEG-HARDWARE-DASHBOARD-RESULTS-TEXT" in module_symbols
    assert "VIBEFFMPEG-RUNTIME-DASHBOARD-RESULTS-TEXT" in module_symbols
    assert "VIBEFFMPEG-HARDWARE-DASHBOARD-RESULTS-LABEL-TEXT" in module_symbols
    assert "VIBEFFMPEG-RUNTIME-DASHBOARD-RESULTS-LABEL-TEXT" in module_symbols
    assert "All Settings" not in text
    assert "BACKEND:" in text and "QSV Fallback:" in text
    assert "Platform:" in text and "GPU Decode:" in text
    assert "=gFontNameClock" not in text
    assert "\u2800" not in text, "dashboard alignment must not depend on padding glyphs"
    assert text.count("Label Column") == 2 and text.count("Value Column") == 2
    for sym in (
        "VIBEFFMPEG-HARDWARE-DASHBOARD-RESULTS-LABEL-TEXT",
        "VIBEFFMPEG-RUNTIME-DASHBOARD-RESULTS-LABEL-TEXT",
    ):
        label_text = next(node for node in module.iter() if node.attrib.get("Sym") == sym)
        alignment = next(
            child for child in label_text if child.tag.endswith("TextAlignment")
        )
        assert (alignment.text or "").strip() == "0.0", f"{sym} must be left aligned"
    runtime_block = next(
        node for node in module.iter()
        if node.attrib.get("Sym") == "VIBEFFMPEG-RUNTIME-DASHBOARD-RESULTS-BLOCK"
    )
    runtime_height = next(
        child for child in runtime_block if child.tag.endswith("FixedHeight")
    )
    assert (runtime_height.text or "").strip() == "1.0", (
        "Runtime Status must use its full pane so all backend rows remain visible"
    )
    assert text.count('Name="RUN TEST"') == 2
    assert text.count('Name="EXPORT TEST"') == 2
    assert 'STATE:' in text and 'hardwareTest.state' in text
    assert 'MIM Version:' in text and 'status.mimVersion' in text
    assert 'hardwareTest.reportPath' not in selected_settings_expression_text(text), (
        "Hardware Test dashboard must not expose report path as a paged setting row"
    )
    assert "VIBEFFMPEG-CONFIG-PAGINATION" in text
    assert 'Ref="6572"' not in text
    assert "VIBEFFMPEG-CATEGORY-BACKGROUND" not in text
    assert "0x242424" in text and "0x303030" in text
    assert '<FixedWidth>0.38</FixedWidth>' in text
    assert '<FixedWidth>0.62</FixedWidth>' in text
    assert "gSettingsBackgroundImage" in text
    assert "VIBEFFMPEG-DETAILED-SETUP-MODE" in text
    assert "VIBEFFMPEG-HELP-HEIGHT" in text
    assert 'CurrSetupArea == &quot;xVibeFFmpegCategory1&quot;' in text
    choice_dialogs = [
        node for node in module.iter()
        if node.tag.endswith("OptionsMenu")
        and node.attrib.get("Name") == "Plugin Choice"
    ]
    assert len(choice_dialogs) == 2, "right pane needs Choice and Multichoice dialogs"
    for dialog in choice_dialogs:
        properties = {
            child.tag.rsplit("}", 1)[-1]: (child.text or "").strip()
            for child in dialog
        }
        assert properties.get("Layout") == "Vertical"
        assert properties.get("FixedWidth") == "0.62"
        assert properties.get("FixedHeight") == "0.68"
        assert properties.get("IgnoreThemeProps") == "true"
        assert not any(
            child.tag.endswith("Theme") and child.attrib.get("Ref") == "2131"
            for child in dialog
        ), "choice dialog still depends on stock theme inheritance"
        assert any(
            child.tag.endswith("Action")
            and child.attrib.get("Name") == '"REM Draw the Dialog BG"'
            and "-CHOICE-" in child.attrib.get("Sym", "")
            for child in dialog
        ), "choice dialog has no cloned stock dialog background"
        assert any(
            child.tag.endswith("Action") and child.attrib.get("Name") == "gDialogBG"
            for child in dialog.iter()
        ), "choice dialog background no longer matches Detailed Setup"
        assert any(
            child.tag.endswith("Text")
            and "-CHOICE-" in child.attrib.get("Sym", "")
            for child in dialog.iter()
        ), "choice dialog has no self-contained option text"
        assert any(
            child.tag.endswith("Conditional")
            and child.attrib.get("Name") == "Focused"
            and "-CHOICE-" in child.attrib.get("Sym", "")
            for child in dialog.iter()
        ), "choice dialog has no focused option artwork"
        assert not any(
            child.tag.endswith("Panel") and child.attrib.get("Ref") in {"2661", "4611"}
            for child in dialog.iter()
        ), "choice rows still depend on stock radio/checkmark panels"
    assert text.count('Sym="VIBEFFMPEG-SETUP-ITEM"') == 1
    stock_ids = {value for node in stock.iter() if (value := node.attrib.get("ID"))}
    module_id_set = set(module_ids)
    assert not (module_id_set & stock_ids), "STVi widget IDs collide with stock STV"
    for widget in module.iter():
        reference = widget.attrib.get("Ref")
        assert reference is None or reference in module_id_set or reference in stock_ids, (
            f"unresolved widget reference: {reference}"
        )
    print("PASS: stock SageTV7 STVi category/configuration contract")
    return 0


def selected_settings_expression_text(text: str) -> str:
    """Return only the generated category-setting resolver for focused checks."""
    marker = 'Sym="VIBEFFMPEG-CONFIG-SETTINGS"'
    position = text.find(marker)
    if position < 0:
        return ""
    start = text.rfind("<Action", 0, position)
    end = text.find(">", position)
    return text[start:end + 1]


if __name__ == "__main__":
    raise SystemExit(main())
