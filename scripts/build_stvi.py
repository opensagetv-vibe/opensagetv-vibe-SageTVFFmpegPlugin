#!/usr/bin/env python3
"""Build the stock-STV-compatible FFmpeg plugin category UI.

The setting editor is derived from SageTV's own Plugin Configure menu so its
Boolean, Choice, Button, and read-only behavior stays identical to the stock
STV. Vibe presents that editor in the right side of one Detailed Setup-style
screen while a paged category table remains visible on the left.
"""

from __future__ import annotations

import argparse
import copy
from pathlib import Path
import xml.etree.ElementTree as ET


NS = "urn:tv.sage/stv"
ET.register_namespace("", NS)

CATEGORY_MENU_ID = "910000"
STOCK_DETAILED_SETUP_MENU_ID = "863"


def q(name: str) -> str:
    return f"{{{NS}}}{name}"


def node(kind: str, name: str | None = None, sym: str | None = None, **attrs: str) -> ET.Element:
    values = dict(attrs)
    if name is not None:
        values["Name"] = name
    if sym is not None:
        values["Sym"] = sym
    return ET.Element(q(kind), values)


def child(parent: ET.Element, kind: str, text: str | None = None, **attrs: str) -> ET.Element:
    result = ET.SubElement(parent, q(kind), attrs)
    result.text = text
    return result


def add_action(parent: ET.Element, expression: str, sym: str) -> ET.Element:
    action = node("Action", expression, sym)
    parent.append(action)
    return action


def add_panel_background(
    parent: ET.Element,
    sym: str,
    alpha: str = "190",
    color: str = "0x404040",
) -> ET.Element:
    shape = node("Shape", "Background", sym)
    child(shape, "ForegroundColor", color)
    child(shape, "ForegroundAlpha", alpha)
    child(shape, "FixedWidth", "1.0")
    child(shape, "FixedHeight", "1.0")
    child(shape, "ShapeType", "Rectangle")
    child(shape, "ShapeFill", "true")
    child(shape, "BackgroundComponent", "true")
    parent.append(shape)
    return shape


def add_horizontal_rule(parent: ET.Element, sym: str, y: str) -> ET.Element:
    """Add the light-gray separator used by Detailed Setup chrome."""
    shape = node("Shape", "Separator", sym)
    child(shape, "ForegroundColor", "0xB8B8B8")
    child(shape, "ForegroundAlpha", "170")
    child(shape, "AnchorX", "0.0")
    child(shape, "AnchorY", y)
    child(shape, "AnchorPointX", "0.0")
    child(shape, "AnchorPointY", "0.5")
    child(shape, "FixedWidth", "1.0")
    child(shape, "FixedHeight", "0.004")
    child(shape, "ShapeType", "Rectangle")
    child(shape, "ShapeFill", "true")
    child(shape, "BackgroundComponent", "true")
    parent.append(shape)
    return shape


def add_row_separator(parent: ET.Element, sym: str) -> ET.Element:
    """Add the subtle row divider used by stock Detailed Setup lists."""
    shape = node("Shape", "Row Separator", sym)
    child(shape, "ForegroundColor", "0xB8B8B8")
    child(shape, "ForegroundAlpha", "105")
    child(shape, "AnchorX", "0.5")
    child(shape, "AnchorY", "1.0")
    child(shape, "AnchorPointX", "0.5")
    child(shape, "AnchorPointY", "1.0")
    child(shape, "FixedWidth", "0.92")
    child(shape, "FixedHeight", "0.003")
    child(shape, "ShapeType", "Rectangle")
    child(shape, "ShapeFill", "true")
    child(shape, "BackgroundComponent", "true")
    parent.append(shape)
    return shape


def clone_focus_widget(
    focus_template: ET.Element,
    id_base: int,
    sym_prefix: str,
) -> ET.Element:
    """Clone the stock SageTV focused-row artwork with private IDs/symbols."""
    focus = copy.deepcopy(focus_template)
    focus_ids = [value.attrib["ID"] for value in focus.iter() if "ID" in value.attrib]
    focus_id_map = {
        old: str(id_base + offset) for offset, old in enumerate(focus_ids, 1)
    }
    symbol_index = 0
    for value in focus.iter():
        if "ID" in value.attrib:
            value.attrib["ID"] = focus_id_map[value.attrib["ID"]]
        if value.attrib.get("Ref") in focus_id_map:
            value.attrib["Ref"] = focus_id_map[value.attrib["Ref"]]
        if "Sym" in value.attrib:
            symbol_index += 1
            value.attrib["Sym"] = f"{sym_prefix}-{symbol_index:03d}"
    return focus


def clone_private_widget(
    template: ET.Element,
    id_base: int,
    sym_prefix: str,
) -> ET.Element:
    """Clone a stock widget subtree with STVi-private IDs and symbols."""
    result = copy.deepcopy(template)
    internal_ids = [value.attrib["ID"] for value in result.iter() if "ID" in value.attrib]
    id_map = {old: str(id_base + offset) for offset, old in enumerate(internal_ids, 1)}
    symbol_index = 0
    for value in result.iter():
        if "ID" in value.attrib:
            value.attrib["ID"] = id_map[value.attrib["ID"]]
        if value.attrib.get("Ref") in id_map:
            value.attrib["Ref"] = id_map[value.attrib["Ref"]]
        if "Sym" in value.attrib:
            symbol_index += 1
            value.attrib["Sym"] = f"{sym_prefix}-{symbol_index:03d}"
    return result


def set_widget_property(widget: ET.Element, name: str, value: str) -> None:
    """Set a direct UI property without leaving conflicting duplicates."""
    existing = widget.find(q(name))
    if existing is None:
        child(widget, name, value)
    else:
        existing.text = value


def clone_stock_pagination(
    stock: ET.Element,
    template: ET.Element,
    id_base: int = 1300000,
    sym_prefix: str = "VIBEFFMPEG-CATEGORY-PAGINATION",
) -> ET.Element:
    """Clone the complete stock vertical scrollbar without fragile panel refs."""
    result = clone_private_widget(
        template,
        id_base,
        sym_prefix,
    )
    expand_stock_pagination_refs(
        result,
        stock,
        id_base + 10000,
        sym_prefix,
    )
    return result


def expand_stock_pagination_refs(
    result: ET.Element,
    stock: ET.Element,
    id_base: int,
    sym_prefix: str,
) -> None:
    """Replace every stock scrollbar reference with a private cloned widget.

    Imported STV modules cannot assume the stock widget references remain
    available in every theme/context. This applies to both the category list
    and the setting-choice lists opened from the right-hand editor.
    """
    shared_ids = ("6454", "6455", "6456", "6457", "6458", "6459", "6460", "6461")
    stock_by_id = {
        value.attrib["ID"]: value
        for value in stock.iter()
        if value.attrib.get("ID") in shared_ids
    }
    if set(stock_by_id) != set(shared_ids):
        raise RuntimeError("stock vertical table pagination dependencies changed")
    replacement_number = 0
    for parent in list(result.iter()):
        for position, value in enumerate(list(parent)):
            reference = value.attrib.get("Ref")
            if reference not in stock_by_id:
                continue
            replacement_number += 1
            replacement_index = shared_ids.index(reference)
            replacement = clone_private_widget(
                stock_by_id[reference],
                id_base + replacement_number * 2000 + replacement_index * 100,
                f"{sym_prefix}-{replacement_number:02d}-{replacement_index + 1}",
            )
            parent.remove(value)
            parent.insert(position, replacement)


def style_choice_dialogs(
    result: ET.Element,
    stock: ET.Element,
    category_index: int,
    focus_template: ET.Element,
    dialog_background_template: ET.Element,
) -> None:
    """Make stock plugin Choice dialogs self-contained after STVi import.

    SageTV's stock Plugin Configure menu delegates the entire choice-row
    presentation to Theme 2131 and to shared panels 2661/4611. Those external
    references can import successfully while still failing to inherit their
    layout on a remote MiniClient. Preserve the stock actions and configuration
    API calls, but give each cloned dialog its own compact layout, background,
    text rows, and focused-row artwork.
    """
    dialogs = [value for value in result.iter(q("OptionsMenu"))
               if value.attrib.get("Name") == "Plugin Choice"]
    for dialog_index, dialog in enumerate(dialogs, 1):
        for theme in list(dialog.findall(q("Theme"))):
            if theme.attrib.get("Ref") == "2131":
                dialog.remove(theme)

        for name, value in (
            ("Layout", "Vertical"),
            ("AnchorX", "0.5"),
            ("AnchorY", "0.5"),
            ("AnchorPointX", "0.5"),
            ("AnchorPointY", "0.5"),
            ("FixedWidth", "0.62"),
            ("FixedHeight", "0.68"),
            ("Insets", "0.035,0.045,0.035,0.045"),
            ("PadY", "0.015"),
            ("IgnoreThemeProps", "true"),
        ):
            set_widget_property(dialog, name, value)

        background = clone_private_widget(
            dialog_background_template,
            1080000 + category_index * 10000 + dialog_index * 100,
            f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-BACKGROUND",
        )
        dialog.insert(0, background)

        title_text = next((
            value for value in dialog.iter(q("Text"))
            if value.get("Sym", "").endswith("-0049")
            or value.get("Sym", "").endswith("-0080")
        ), None)
        if title_text is not None:
            for name, value in (
                ("FixedWidth", "0.94"),
                ("FixedHeight", "0.13"),
                ("TextAlignment", "0.5"),
                ("Wrap", "true"),
                ("FontSize", "22"),
                ("ForegroundColor", "0xF2F2F2"),
                ("ForegroundSelectedColor", "0xFFFFFF"),
            ):
                set_widget_property(title_text, name, value)

        choices_table = next((value for value in dialog.iter(q("Table"))
                              if value.attrib.get("Name") == "Choices"), None)
        if choices_table is None:
            raise RuntimeError("stock Plugin Configure choice table contract changed")
        for name, value in (
            ("AnchorX", "0.5"),
            ("AnchorY", "0.5"),
            ("AnchorPointX", "0.5"),
            ("AnchorPointY", "0.5"),
            ("FixedWidth", "0.92"),
            ("FixedHeight", "0.74"),
            ("NumRows", "6"),
            ("HorizontalAlignment", "0.0"),
            ("VerticalAlignment", "0.0"),
        ):
            set_widget_property(choices_table, name, value)
        expand_stock_pagination_refs(
            dialog,
            stock,
            1500000 + category_index * 100000 + dialog_index * 20000,
            f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-PAGINATION",
        )

        choice_component = next((
            value for value in choices_table.iter(q("TableComponent"))
            if value.attrib.get("Name") == "Choice"
        ), None)
        if choice_component is None:
            raise RuntimeError("stock Plugin Configure choice-row contract changed")
        set_widget_property(choice_component, "FixedWidth", "1.0")

        choice_item = next((value for value in choice_component
                            if value.tag == q("Item")), None)
        if choice_item is None:
            raise RuntimeError("stock Plugin Configure choice item contract changed")
        set_widget_property(choice_item, "FixedWidth", "1.0")
        set_widget_property(choice_item, "FixedHeight", "1.0")

        rendered_choice = next((
            value for value in choice_item.iter(q("Action"))
            if value.attrib.get("Name") == "ButtonText = Choice"
        ), None)
        if rendered_choice is None:
            raise RuntimeError("stock Plugin Configure choice label contract changed")
        for inherited_panel in list(rendered_choice.findall(q("Panel"))):
            rendered_choice.remove(inherited_panel)
        choice_text = node(
            "Text",
            "",
            f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-TEXT",
        )
        for name, value in (
            ("FixedWidth", "0.9"),
            ("FixedHeight", "1.0"),
            ("TextAlignment", "0.0"),
            ("Insets", "0.035,0.01,0.035,0.01"),
            ("FontSize", "22"),
            ("ForegroundColor", "0xF2F2F2"),
            ("ForegroundSelectedColor", "0xFFFFFF"),
        ):
            child(choice_text, name, value)
        rendered_choice.append(choice_text)
        choice_item.append(clone_focus_widget(
            focus_template,
            1100000 + category_index * 10000 + dialog_index * 1000,
            f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-FOCUS",
        ))

        # Multichoice dialogs have an explicit commit/close item. It also
        # depended on the omitted stock theme, so render it directly.
        close_item = next((value for value in dialog
                           if value.tag == q("Item")
                           and value.attrib.get("Name") == "Close"), None)
        if close_item is not None:
            close_label_action = node(
                "Action",
                '"Apply and close"',
                f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-CLOSE-VALUE",
            )
            close_label = node(
                "Text",
                "",
                f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-CLOSE-TEXT",
            )
            child(close_label, "FixedWidth", "0.92")
            child(close_label, "FixedHeight", "0.10")
            child(close_label, "TextAlignment", "0.5")
            child(close_label, "FontSize", "20")
            child(close_label, "ForegroundColor", "0xF2F2F2")
            child(close_label, "ForegroundSelectedColor", "0xFFFFFF")
            close_label_action.append(close_label)
            close_item.insert(0, close_label_action)
            close_item.append(clone_focus_widget(
                focus_template,
                1200000 + category_index * 10000 + dialog_index * 1000,
                f"VIBEFFMPEG-CONFIG-{category_index}-CHOICE-{dialog_index}-CLOSE-FOCUS",
            ))


def add_setup_hook(module: ET.Element) -> None:
    hook = node("Hook", "STVImported", "VIBEFFMPEG-IMPORT-HOOK")
    module.append(hook)
    find_parent = add_action(hook, 'Parent = FindWidgetBySymbol("OPUS4A-125847")',
                             "VIBEFFMPEG-FIND-SETUP-PARENT")
    conditional = node("Conditional", "Parent != null", "VIBEFFMPEG-HAS-SETUP-PARENT")
    find_parent.append(conditional)
    yes = node("Branch", "true", "VIBEFFMPEG-HAS-SETUP-PARENT-TRUE")
    conditional.append(yes)
    find_child = add_action(yes, 'Child = FindWidgetBySymbol("VIBEFFMPEG-SETUP-ITEM")',
                            "VIBEFFMPEG-FIND-SETUP-ITEM")
    needs_link = node(
        "Conditional",
        'Child != null && GetWidgetParent(Child, "Panel", "Secondary Menu Items Area") == null',
        "VIBEFFMPEG-NEEDS-SETUP-LINK",
    )
    find_child.append(needs_link)
    link_yes = node("Branch", "true", "VIBEFFMPEG-NEEDS-SETUP-LINK-TRUE")
    needs_link.append(link_yes)
    add_action(link_yes, "AddWidgetChild(Parent, Child)", "VIBEFFMPEG-ADD-SETUP-LINK")
    no = node("Branch", "false", "VIBEFFMPEG-HAS-SETUP-PARENT-FALSE")
    conditional.append(no)
    add_action(
        no,
        'DebugLog("OpenSageTV Vibe FFmpeg Plugin: stock SageTV7 Setup insertion point OPUS4A-125847 is unavailable")',
        "VIBEFFMPEG-SETUP-PARENT-ERROR",
    )
    add_action(hook, 'ReturnValue = "AutoCleanupSTVImportedHook"', "VIBEFFMPEG-IMPORT-CLEANUP")


def add_setup_item(module: ET.Element) -> None:
    item = node("Item", "OpenSageTV Vibe FFmpeg Plugin", "VIBEFFMPEG-SETUP-ITEM")
    module.append(item)
    linux = add_action(item, 'Plugin = GetAvailablePluginForID("SageTVFFmpegPluginLinux")',
                       "VIBEFFMPEG-FIND-LINUX-PLUGIN")
    installed = node("Conditional", "Plugin != null && IsPluginInstalled(Plugin)",
                     "VIBEFFMPEG-LINUX-PLUGIN-INSTALLED")
    linux.append(installed)
    yes = node("Branch", "true", "VIBEFFMPEG-LINUX-PLUGIN-TRUE")
    installed.append(yes)
    context = add_action(yes, 'AddStaticContext("Plugin", Plugin)',
                         "VIBEFFMPEG-SET-LINUX-PLUGIN-CONTEXT")
    context.append(ET.Element(q("Menu"), {
        "Ref": CATEGORY_MENU_ID,
        "Name": "OpenSageTV Vibe FFmpeg Plugin",
    }))
    no = node("Branch", "false", "VIBEFFMPEG-LINUX-PLUGIN-FALSE")
    installed.append(no)
    windows = add_action(no, 'Plugin = GetAvailablePluginForID("SageTVFFmpegPluginWinx64")',
                         "VIBEFFMPEG-FIND-WINDOWS-PLUGIN")
    win_installed = node("Conditional", "Plugin != null && IsPluginInstalled(Plugin)",
                         "VIBEFFMPEG-WINDOWS-PLUGIN-INSTALLED")
    windows.append(win_installed)
    win_yes = node("Branch", "true", "VIBEFFMPEG-WINDOWS-PLUGIN-TRUE")
    win_installed.append(win_yes)
    win_context = add_action(win_yes, 'AddStaticContext("Plugin", Plugin)',
                             "VIBEFFMPEG-SET-WINDOWS-PLUGIN-CONTEXT")
    win_context.append(ET.Element(q("Menu"), {
        "Ref": CATEGORY_MENU_ID,
        "Name": "OpenSageTV Vibe FFmpeg Plugin",
    }))
    win_no = node("Branch", "false", "VIBEFFMPEG-WINDOWS-PLUGIN-FALSE")
    win_installed.append(win_no)
    add_action(win_no,
               'DebugLog("OpenSageTV Vibe FFmpeg Plugin: installed Standard plugin was not found")',
               "VIBEFFMPEG-PLUGIN-MISSING")
    for name, value, sym in (
        ("ButtonText", '"OpenSageTV Vibe FFmpeg Plugin"', "VIBEFFMPEG-SETUP-BUTTON-TEXT"),
        ("ThisItem", '"xItemOpenSageTVVibeFFmpegPlugin"', "VIBEFFMPEG-SETUP-THIS-ITEM"),
        ("SubMenuType", "null", "VIBEFFMPEG-SETUP-SUBMENU-TYPE"),
    ):
        attribute = node("Attribute", name, sym)
        child(attribute, "Value", value)
        item.append(attribute)


CATEGORIES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("Playback & Transcoding", (
        "general.enabled", "filters.deinterlace", "audio.mode",
        "closed_captions.preserve_a53cc", "seek.fast_seek",
        "caption_side_channel.enabled", "caption_side_channel.bind_address",
        "caption_side_channel.api_port",
    )),
    ("Hardware Setup", (
        "hardware.backend", "hardware.hardware_decode",
        "hardware.active_file_hardware_decode", "capabilities.selectedBackend",
        "capabilities.ffmpegVersion",
    )),
    ("Hardware Test", (
        "action.runHardwareTest", "action.exportHardwareReport",
    )),
    ("Runtime Status", (
        "status.mimVersion", "status.platform", "status.last.state",
        "status.last.backend", "status.last.encoder",
        "status.last.hardwareDecode", "health.launcher", "health.runtime",
        "capabilities.vaapi.usable", "capabilities.qsv.usable",
        "capabilities.nvenc.usable", "capabilities.amf.usable",
        "capabilities.d3d12va.usable", "capabilities.software.usable",
        "captionSideChannel.status",
    )),
    ("Logging & Maintenance", (
        "logging.enabled", "logging.level", "ini.path", "ini.lastModified",
        "action.reload", "action.repairLauncher", "action.restoreDefaultIni",
        "action.last",
    )),
)

CATEGORY_HELP: tuple[str, ...] = (
    "Configure normal playback, transcoding, audio, captions, and seek behavior.",
    "Choose the preferred hardware backend and hardware decode policy.",
    "Run, export, and review the hardware-acceleration test for this server.",
    "Review the active MIM runtime, latest transcode, and available hardware.",
    "Configure diagnostics or repair and reload the FFmpeg runtime.",
)


def settings_expression(settings: tuple[str, ...]) -> str:
    quoted = ", ".join(f'"{value}"' for value in settings)
    return f"CreateArray({quoted})"


def selected_settings_expression() -> str:
    """Return the settings for the selected category."""
    expression = settings_expression(CATEGORIES[0][1])
    for index in range(len(CATEGORIES), 0, -1):
        condition = f'CurrSetupArea == "xVibeFFmpegCategory{index}"'
        if index == 1:
            condition = f"(CurrSetupArea == null || {condition})"
        expression = (
            f"If({condition}, "
            f"{settings_expression(CATEGORIES[index - 1][1])}, {expression})"
        )
    return expression


def selected_title_expression() -> str:
    """Return a Sage expression resolving a category number to its title."""
    expression = f'"{CATEGORIES[0][0]}"'
    for index in range(len(CATEGORIES), 0, -1):
        title = CATEGORIES[index - 1][0]
        condition = f'CurrSetupArea == "xVibeFFmpegCategory{index}"'
        if index == 1:
            condition = f"(CurrSetupArea == null || {condition})"
        expression = f'If({condition}, "{title}", {expression})'
    return expression


def selected_footer_help_expression() -> str:
    """Return the Detailed Setup footer help for the active category."""
    expression = f'"{CATEGORY_HELP[0]}"'
    for index in range(len(CATEGORY_HELP), 0, -1):
        condition = f'CurrSetupArea == "xVibeFFmpegCategory{index}"'
        if index == 1:
            condition = f"(CurrSetupArea == null || {condition})"
        expression = f'If({condition}, "{CATEGORY_HELP[index - 1]}", {expression})'
    return expression


def selected_help_expression(variable: str = "VibeFFmpegSelectedCategory") -> str:
    """Return a Sage expression resolving a category number to help text."""
    expression = f'"{CATEGORY_HELP[0]}"'
    for index in range(len(CATEGORY_HELP), 0, -1):
        help_text = CATEGORY_HELP[index - 1]
        expression = f'If({variable} == {index}, "{help_text}", {expression})'
    return expression


def category_index_expression(variable: str = "CategoryTitle") -> str:
    """Return the 1-based category index for a table-provided title."""
    expression = "1"
    for index in range(len(CATEGORIES), 0, -1):
        title = CATEGORIES[index - 1][0]
        expression = f'If({variable} == "{title}", {index}, {expression})'
    return expression


def category_area_expression(variable: str = "CategoryTitle") -> str:
    """Return the Detailed Setup area token for a table-provided title."""
    expression = '"xVibeFFmpegCategory1"'
    for index in range(len(CATEGORIES), 0, -1):
        title = CATEGORIES[index - 1][0]
        expression = (
            f'If({variable} == "{title}", "xVibeFFmpegCategory{index}", '
            f"{expression})"
        )
    return expression


def category_help_expression(variable: str = "CategoryTitle") -> str:
    """Return Detailed Setup help text for a table-provided title."""
    expression = f'"{CATEGORY_HELP[0]}"'
    for index in range(len(CATEGORY_HELP), 0, -1):
        title = CATEGORIES[index - 1][0]
        expression = f'If({variable} == "{title}", "{CATEGORY_HELP[index - 1]}", {expression})'
    return expression


def clone_detailed_setup_button(
    template: ET.Element,
    focus_template: ET.Element,
    focus_hook_template: ET.Element,
    title: str,
    category_index: int,
    help_text: str,
) -> ET.Element:
    """Clone one native Detailed Setup row with a stable literal area."""
    result = copy.deepcopy(template)
    result.attrib["Name"] = title

    id_base = 911000 + category_index * 1000
    internal_ids = [value.attrib["ID"] for value in result.iter() if "ID" in value.attrib]
    id_map = {old: str(id_base + offset) for offset, old in enumerate(internal_ids, 1)}
    symbol_index = 0
    for value in result.iter():
        if "ID" in value.attrib:
            value.attrib["ID"] = id_map[value.attrib["ID"]]
        if value.attrib.get("Ref") in id_map:
            value.attrib["Ref"] = id_map[value.attrib["Ref"]]
        if "Sym" in value.attrib:
            symbol_index += 1
            value.attrib["Sym"] = (
                f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-{symbol_index:03d}"
            )

    result.attrib["Sym"] = f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}"
    child(result, "FixedWidth", "1.0")
    child(result, "FixedHeight", "0.125")
    child(result, "Insets", "0.012,0.018,0.012,0.018")
    child(result, "HorizontalAlignment", "0.0")
    for attribute in result.findall(q("Attribute")):
        value = attribute.find(q("Value"))
        if value is None:
            continue
        if attribute.attrib.get("Name") == "SetupArea":
            value.text = f'"xVibeFFmpegCategory{category_index}"'
        elif attribute.attrib.get("Name") == "AreaName":
            value.text = f'"{title}"'
        elif attribute.attrib.get("Name") == "HelpText":
            value.text = f'"{help_text}"'

    current_section = next((
        value for value in result.iter(q("Conditional"))
        if value.attrib.get("Name") == "(CurrSetupArea == SetupArea) && !Focused"
    ), None)
    if current_section is None:
        raise RuntimeError("stock Detailed Setup selected-row contract changed")
    current_section.attrib["Name"] = "(CurrSetupArea == SetupArea) && !Focused"

    for text_widget in result.iter(q("Text")):
        child(text_widget, "FixedWidth", "1.0")
        child(text_widget, "FixedHeight", "1.0")
        child(text_widget, "TextAlignment", "0.0")
        child(text_widget, "AnchorX", "0.0")
        child(text_widget, "AnchorPointX", "0.0")
        child(text_widget, "FontSize", "19")
        child(text_widget, "ForegroundColor", "0xFFFFFF")
        child(text_widget, "ForegroundSelectedColor", "0xFFFFFF")
        child(text_widget, "ForegroundAlpha", "255")

    add_row_separator(
        result,
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-SEPARATOR",
    )

    # The stock row receives its focused highlight through a parent Theme.
    # Imported STVi references do not reliably inherit that Theme on remote
    # UIs, so copy the stock Focused widget into each row and remap its IDs.
    result.append(clone_focus_widget(
        focus_template,
        id_base + 40,
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-FOCUS",
    ))

    for inherited_hook in list(result.findall(q("Hook"))):
        if inherited_hook.attrib.get("Ref") == "22543":
            result.remove(inherited_hook)

    focus_hook = clone_private_widget(
        focus_hook_template,
        id_base + 70,
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-FOCUS-HOOK",
    )
    # Keep the stock Detailed Setup focus contract, but replace the inherited
    # row-local SetupArea lookup with this row's literal category. Imported
    # STVi focus events on remote MiniClients do not reliably preserve the
    # cloned row context. Refresh only when the category actually changed so a
    # refresh-generated FocusGained event cannot loop.
    set_current_area = next((
        value for value in focus_hook.iter(q("Action"))
        if value.attrib.get("Name") == 'AddGlobalContext("CurrSetupArea", SetupArea)'
    ), None)
    if set_current_area is None:
        raise RuntimeError("stock Detailed Setup FocusGained update contract changed")
    literal_area = f'"xVibeFFmpegCategory{category_index}"'
    set_current_area.attrib["Name"] = (
        f'AddGlobalContext("CurrSetupArea", {literal_area})'
    )
    changed_category = node(
        "Conditional",
        f"OldSetupArea != {literal_area}",
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-FOCUS-CHANGED",
    )
    add_action(
        changed_category,
        "Refresh()",
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-FOCUS-REFRESH",
    )
    set_current_area.append(changed_category)
    result.append(focus_hook)

    # Remote MiniClients do not consistently propagate a cloned FocusGained
    # hook's row-local context into an imported STVi. Keep the stock hook for
    # native Detailed Setup behavior, and make Select an explicit, literal
    # activation path. This is a normal Item action (not a listener), so SageTV
    # invokes it when Enter/Select is pressed and then redraws this same menu.
    activate = add_action(
        result,
        f'AddGlobalContext("CurrSetupArea", "xVibeFFmpegCategory{category_index}")',
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-ACTIVATE",
    )
    add_action(
        activate,
        "Refresh()",
        f"VIBEFFMPEG-CATEGORY-ROW-{category_index:02d}-ACTIVATE-REFRESH",
    )

    return result


def add_category_menu(module: ET.Element, stock: ET.Element) -> None:
    detailed_setup = next((
        value for value in stock.iter()
        if value.tag == q("Menu") and value.attrib.get("ID") == STOCK_DETAILED_SETUP_MENU_ID
    ), None)
    if detailed_setup is None:
        raise RuntimeError("stock Detailed Setup menu BASE-52048 was not found")
    button_template = next((
        value for value in detailed_setup.iter()
        if value.tag == q("Item") and value.attrib.get("Sym") == "BASE-72605"
    ), None)
    if button_template is None:
        raise RuntimeError("stock Detailed Setup General row BASE-72605 was not found")
    focus_template = next((
        value for value in stock.iter()
        if value.tag == q("Conditional") and value.attrib.get("ID") == "4873"
    ), None)
    if focus_template is None:
        raise RuntimeError("stock Detailed Setup focused-row widget 4873 was not found")
    focus_hook_template = next((
        value for value in stock.iter()
        if value.tag == q("Hook") and value.attrib.get("ID") == "22543"
    ), None)
    if focus_hook_template is None:
        raise RuntimeError("stock Detailed Setup FocusGained hook 22543 was not found")
    pagination_template = next((
        value for value in stock.iter()
        if value.tag == q("Panel") and value.attrib.get("ID") == "6572"
    ), None)
    if pagination_template is None:
        raise RuntimeError("stock vertical table pagination panel 6572 was not found")

    menu = node(
        "Menu",
        "OpenSageTV Vibe FFmpeg Plugin",
        "VIBEFFMPEG-CATEGORY-MENU",
        ID=CATEGORY_MENU_ID,
    )
    module.append(menu)
    load_hook = node("Hook", "AfterMenuLoad", "VIBEFFMPEG-CATEGORY-AFTER-LOAD")
    add_action(
        load_hook,
        'AddGlobalContext("CurrSetupArea", "xVibeFFmpegCategory1")',
        "VIBEFFMPEG-CATEGORY-DEFAULT",
    )
    menu.append(load_hook)

    # Imported modules cannot rely on every stock theme reference resolving on
    # a remote MiniClient. Render the Detailed Setup chrome privately: the
    # settings background image, translucent dark-gray content, white title
    # and footer text, and light-gray separator rules.
    background_action = add_action(
        menu,
        "gSettingsBackgroundImage",
        "VIBEFFMPEG-DETAILED-BACKGROUND-VALUE",
    )
    background_image = node("Image", "Detailed Setup Background", "VIBEFFMPEG-DETAILED-BACKGROUND")
    child(background_image, "FixedWidth", "1.0")
    child(background_image, "FixedHeight", "1.0")
    child(background_image, "PreserveAspectRatio", "true")
    child(background_image, "ResizeImage", "true")
    child(background_image, "CropToFill", "true")
    child(background_image, "BGLoad", "true")
    child(background_image, "BackgroundComponent", "true")
    background_action.append(background_image)

    header = node("Panel", "Detailed Setup Header", "VIBEFFMPEG-DETAILED-HEADER")
    child(header, "AnchorX", "0.03")
    child(header, "AnchorY", "0.025")
    child(header, "AnchorPointX", "0.0")
    child(header, "AnchorPointY", "0.0")
    child(header, "FixedWidth", "0.94")
    child(header, "FixedHeight", "0.065")
    add_panel_background(header, "VIBEFFMPEG-DETAILED-HEADER-BACKGROUND", "215", "0x303030")
    add_horizontal_rule(header, "VIBEFFMPEG-DETAILED-HEADER-RULE", "1.0")
    header_title_action = add_action(
        header,
        '"OpenSageTV Vibe FFmpeg Plugin"',
        "VIBEFFMPEG-DETAILED-HEADER-TITLE-VALUE",
    )
    header_title = node("Text", "", "VIBEFFMPEG-DETAILED-HEADER-TITLE")
    child(header_title, "Insets", "0.012,0.0,0.012,0.0")
    child(header_title, "FixedWidth", "1.0")
    child(header_title, "FixedHeight", "1.0")
    child(header_title, "TextAlignment", "0.0")
    child(header_title, "VerticalAlignment", "0.5")
    child(header_title, "FontSize", "26")
    child(header_title, "FontStyle", "1")
    child(header_title, "ForegroundColor", "0xFFFFFF")
    child(header_title, "ForegroundSelectedColor", "0xFFFFFF")
    child(header_title, "ForegroundAlpha", "255")
    # Detailed Setup uses clean white headings.  An inherited text shadow made
    # this imported heading look dark/outlined on MiniClients even though its
    # foreground color was white.
    child(header_title, "TextShadow", "false")
    header_title_action.append(header_title)
    menu.append(header)

    container = node("Panel", "MenuContainer", "VIBEFFMPEG-CATEGORY-CONTAINER")
    menu.append(container)
    child(container, "Layout", "Horizontal")
    child(container, "AnchorX", "0.03")
    child(container, "AnchorY", "0.09")
    child(container, "AnchorPointX", "0.0")
    child(container, "AnchorPointY", "0.0")
    child(container, "FixedWidth", "0.94")
    child(container, "FixedHeight", "0.77")
    child(container, "HorizontalAlignment", "0.0")
    child(container, "VerticalAlignment", "0.0")
    add_panel_background(container, "VIBEFFMPEG-DETAILED-CONTENT-BACKGROUND", "225", "0x242424")

    buttons = node("Panel", "Buttons", "VIBEFFMPEG-CATEGORY-BUTTONS")
    container.append(buttons)
    child(buttons, "Layout", "Vertical")
    child(buttons, "FixedWidth", "0.38")
    child(buttons, "FixedHeight", "1.0")
    child(buttons, "Insets", "0.018,0.012,0.012,0.012")
    child(buttons, "VerticalAlignment", "0.0")
    child(buttons, "WrapVNav", "true")
    needs_cleanup = node("Attribute", "NeedLostCleanup", "VIBEFFMPEG-CATEGORY-CLEANUP")
    # This self-preserving value is part of the stock Detailed Setup redraw
    # contract. A literal false prevents the old selected row from requesting
    # its one required cleanup refresh when focus changes.
    child(needs_cleanup, "Value", "If(NeedLostCleanup==null,false,NeedLostCleanup)")
    buttons.append(needs_cleanup)
    # Detailed Setup uses stable Item widgets rather than recycled table rows.
    # Keeping that structure is important: each FocusGained hook receives the
    # correct literal SetupArea and the right pane changes without a delayed,
    # full-screen redraw. The current five rows fit the viewport. If enough
    # category is added, this guard fails the build so the left rail can gain a
    # deliberately tested stock scrollbar rather than silently clipping it.
    category_rows = [
        (title, index, CATEGORY_HELP[index - 1])
        for index, (title, _settings) in enumerate(CATEGORIES, 1)
    ]
    if len(category_rows) > 8:
        raise RuntimeError("FFmpeg category rail exceeds the tested Detailed Setup viewport")
    for title, category_index, help_text in category_rows:
        buttons.append(clone_detailed_setup_button(
            button_template,
            focus_template,
            focus_hook_template,
            title,
            category_index,
            help_text,
        ))
    buttons.append(ET.Element(q("Theme"), {
        "Ref": "22607", "Name": "LeftButtonPanelTheme 7",
    }))

    details = node("Panel", "SetupAreas", "VIBEFFMPEG-CATEGORY-DETAILS")
    container.append(details)
    child(details, "Layout", "Vertical")
    child(details, "AnchorX", "1.0")
    child(details, "AnchorPointX", "1.0")
    child(details, "FixedWidth", "0.62")
    child(details, "FixedHeight", "1.0")
    child(details, "Insets", "0.018,0.025,0.018,0.018")
    child(details, "WrapHNav", "false")
    child(details, "WrapVNav", "false")
    child(details, "IgnoreThemeProps", "true")
    child(details, "BackgroundComponent", "false")
    child(details, "MouseTransparency", "false")
    dashboard_switch = node(
        "Conditional",
        'CurrSetupArea == "xVibeFFmpegCategory3"',
        "VIBEFFMPEG-DASHBOARD-SWITCH",
    )
    hardware_branch = node("Branch", "true", "VIBEFFMPEG-HARDWARE-DASHBOARD-BRANCH")
    hardware_branch.append(build_hardware_test_dashboard(stock))
    dashboard_switch.append(hardware_branch)
    other_branch = node("Branch", "else", "VIBEFFMPEG-DASHBOARD-OTHER-BRANCH")
    runtime_switch = node(
        "Conditional",
        'CurrSetupArea == "xVibeFFmpegCategory4"',
        "VIBEFFMPEG-RUNTIME-DASHBOARD-SWITCH",
    )
    runtime_branch = node("Branch", "true", "VIBEFFMPEG-RUNTIME-DASHBOARD-BRANCH")
    runtime_branch.append(build_runtime_dashboard())
    runtime_switch.append(runtime_branch)
    config_branch = node("Branch", "else", "VIBEFFMPEG-CONFIG-DASHBOARD-BRANCH")
    config_branch.append(clone_config_panel(stock))
    runtime_switch.append(config_branch)
    other_branch.append(runtime_switch)
    dashboard_switch.append(other_branch)
    details.append(dashboard_switch)

    footer = node("Panel", "Detailed Setup Footer", "VIBEFFMPEG-DETAILED-FOOTER")
    child(footer, "AnchorX", "0.03")
    child(footer, "AnchorY", "0.86")
    child(footer, "AnchorPointX", "0.0")
    child(footer, "AnchorPointY", "0.0")
    child(footer, "FixedWidth", "0.94")
    child(footer, "FixedHeight", "0.105")
    add_panel_background(footer, "VIBEFFMPEG-DETAILED-FOOTER-BACKGROUND", "215", "0x303030")
    add_horizontal_rule(footer, "VIBEFFMPEG-DETAILED-FOOTER-RULE", "0.0")
    footer_help_action = add_action(
        footer,
        selected_footer_help_expression(),
        "VIBEFFMPEG-DETAILED-FOOTER-HELP-VALUE",
    )
    footer_help = node("Text", "", "VIBEFFMPEG-DETAILED-FOOTER-HELP")
    child(footer_help, "Insets", "0.025,0.01,0.025,0.01")
    child(footer_help, "FixedWidth", "1.0")
    child(footer_help, "FixedHeight", "1.0")
    child(footer_help, "TextAlignment", "0.5")
    child(footer_help, "VerticalAlignment", "0.5")
    child(footer_help, "Wrap", "true")
    child(footer_help, "FontSize", "20")
    child(footer_help, "ForegroundColor", "0xFFFFFF")
    child(footer_help, "ForegroundSelectedColor", "0xFFFFFF")
    child(footer_help, "ForegroundAlpha", "255")
    footer_help_action.append(footer_help)
    menu.append(footer)

    menu.append(ET.Element(q("Listener"), {"Ref": "30270", "Name": "Left-MainMenu"}))
    help_height = node("Attribute", "HelpTextAreaHeight", "VIBEFFMPEG-HELP-HEIGHT")
    child(help_height, "Value", "0.15")
    menu.append(help_height)
    title = node("Attribute", "MenuTitle", "VIBEFFMPEG-CATEGORY-TITLE")
    child(title, "Value", '"OpenSageTV Vibe FFmpeg Plugin"')
    menu.append(title)
    background = node("Attribute", "CurMenuBG", "VIBEFFMPEG-CATEGORY-MENU-BACKGROUND")
    child(background, "Value", "gSettingsBackgroundImage")
    menu.append(background)
    detailed = node("Attribute", "IsDetailedSetupMenu", "VIBEFFMPEG-DETAILED-SETUP-MODE")
    child(detailed, "Value", "true")
    menu.append(detailed)


def resolve_plugin_panel(content: ET.Element, sym_prefix: str) -> ET.Element:
    """Resolve either platform plugin and retain it around a custom dashboard."""
    resolve_linux = node(
        "Action",
        'Plugin = GetAvailablePluginForID("SageTVFFmpegPluginLinux")',
        f"{sym_prefix}-RESOLVE-LINUX",
    )
    resolve_windows = node(
        "Action",
        'Plugin = If(Plugin != null && IsPluginInstalled(Plugin), Plugin, '
        'GetAvailablePluginForID("SageTVFFmpegPluginWinx64"))',
        f"{sym_prefix}-RESOLVE-WINDOWS",
    )
    resolve_windows.append(content)
    resolve_linux.append(resolve_windows)
    return resolve_linux


def add_dashboard_button(
    parent: ET.Element,
    stock: ET.Element,
    label: str,
    setting: str,
    index: int,
) -> None:
    """Add one stock-focused action row to a compact dashboard."""
    focus_template = next((
        value for value in stock.iter()
        if value.tag == q("Conditional") and value.attrib.get("ID") == "4873"
    ), None)
    if focus_template is None:
        raise RuntimeError("stock focused-row widget 4873 was not found")
    item = node("Item", label, f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}")
    for name, value in (
        ("FixedWidth", "0.94"),
        ("FixedHeight", "0.095"),
        ("HorizontalAlignment", "0.0"),
        ("VerticalAlignment", "0.5"),
    ):
        child(item, name, value)
    invoke = add_action(
        item,
        f'SetConfigResult = SetPluginConfigValue(Plugin, "{setting}", '
        f'GetPluginConfigValue(Plugin, "{setting}"))',
        f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}-INVOKE",
    )
    add_action(
        invoke,
        "Refresh()",
        f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}-REFRESH",
    )
    text_widget = node("Text", label, f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}-TEXT")
    for name, value in (
        ("FixedWidth", "1.0"),
        ("FixedHeight", "1.0"),
        ("Insets", "0.025,0.005,0.025,0.005"),
        ("FontSize", "16"),
        ("TextAlignment", "0.0"),
        ("VerticalAlignment", "0.5"),
        ("ForegroundColor", "0xFFFFFF"),
        ("ForegroundSelectedColor", "0xFFFFFF"),
        ("TextShadow", "false"),
    ):
        child(text_widget, name, value)
    item.append(text_widget)
    item.append(clone_focus_widget(
        focus_template,
        1800000 + index * 100,
        f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}-FOCUS",
    ))
    add_row_separator(item, f"VIBEFFMPEG-DASHBOARD-BUTTON-{index}-SEPARATOR")
    parent.append(item)


def add_dashboard_columns(
    parent: ET.Element,
    fields: tuple[tuple[str, str], ...],
    sym_prefix: str,
    height: str,
    blank_after: int = -1,
    font_size: str = "15",
) -> None:
    """Render synchronized, non-focusable label and value text columns.

    SageTV's expression evaluator trims padding from string literals and the
    font exposed as ``gFontNameClock`` is not consistently monospaced across
    MiniClient platforms/themes.  Two geometrically fixed columns therefore
    provide substantially more reliable alignment than padding a single text
    object with spaces or nominally blank glyphs.
    """
    block = node("Panel", "Read-Only Dashboard", f"{sym_prefix}-BLOCK")
    for name, property_value in (
        ("Layout", "Horizontal"),
        ("FixedWidth", "0.94"),
        ("FixedHeight", height),
        ("HorizontalAlignment", "0.0"),
        ("VerticalAlignment", "0.0"),
        ("FocusableCondition", "false"),
    ):
        child(block, name, property_value)

    label_lines: list[str] = []
    value_parts: list[str] = []
    for index, (label, setting) in enumerate(fields):
        label_lines.append(label + ":")
        part = f'GetPluginConfigValue(Plugin, "{setting}")'
        if index < len(fields) - 1:
            separator = "\\n \\n" if index == blank_after else "\\n"
            part += f' + "{separator}"'
        value_parts.append(part)

    # Keep the optional visual group break in both columns so all later rows
    # remain on the same baseline.
    if 0 <= blank_after < len(label_lines) - 1:
        label_lines[blank_after] += "\\n "

    def append_column(
        width: str,
        expression: str,
        suffix: str,
        alignment: str,
        insets: str,
    ) -> None:
        column = node("Panel", f"{suffix.title()} Column", f"{sym_prefix}-{suffix}-COLUMN")
        for name, property_value in (
            ("Layout", "Vertical"),
            ("FixedWidth", width),
            ("FixedHeight", "1.0"),
            ("HorizontalAlignment", "0.0"),
            ("VerticalAlignment", "0.0"),
            ("FocusableCondition", "false"),
        ):
            child(column, name, property_value)
        action_sym = f"{sym_prefix}-VALUE" if suffix == "VALUE" else f"{sym_prefix}-{suffix}-VALUE"
        text_sym = f"{sym_prefix}-TEXT" if suffix == "VALUE" else f"{sym_prefix}-{suffix}-TEXT"
        value = add_action(column, expression, action_sym)
        text_widget = node("Text", "", text_sym)
        for name, property_value in (
            ("FixedWidth", "1.0"),
            ("FixedHeight", "1.0"),
            ("Insets", insets),
            ("FontSize", font_size),
            ("TextAlignment", alignment),
            ("VerticalAlignment", "0.0"),
            ("WrapText", "false"),
            ("ForegroundColor", "0xFFFFFF"),
            ("ForegroundSelectedColor", "0xFFFFFF"),
            ("TextShadow", "false"),
            ("FocusableCondition", "false"),
        ):
            child(text_widget, name, property_value)
        value.append(text_widget)
        block.append(column)

    label_expression = '"' + "\\n".join(label_lines) + '"'
    append_column("0.38", label_expression, "LABEL", "0.0", "0.01,0.006,0.012,0.004")
    append_column(
        "0.62",
        " + ".join(value_parts),
        "VALUE",
        "0.0",
        "0.012,0.006,0.01,0.004",
    )
    parent.append(block)


def build_hardware_test_dashboard(stock: ET.Element) -> ET.Element:
    """Build the one-page Hardware Test actions and read-only result block."""
    panel = node("Panel", "Hardware Test Dashboard", "VIBEFFMPEG-HARDWARE-DASHBOARD")
    for name, value in (
        ("Layout", "Vertical"),
        ("FixedWidth", "1.0"),
        ("FixedHeight", "1.0"),
        ("PadY", "0.004"),
        ("HorizontalAlignment", "0.0"),
        ("VerticalAlignment", "0.0"),
        ("WrapVNav", "true"),
    ):
        child(panel, name, value)
    add_dashboard_button(panel, stock, "RUN TEST", "action.runHardwareTest", 1)
    add_dashboard_button(panel, stock, "EXPORT TEST", "action.exportHardwareReport", 2)
    fields = (
        ("STATE", "hardwareTest.state"),
        ("SUMMARY", "hardwareTest.summary"),
        ("BACKEND", "hardwareTest.selectedBackend"),
        ("VAAPI", "hardwareTest.vaapi.pipeline"),
        ("QSV", "hardwareTest.qsv.pipeline"),
        ("QSV Fallback", "hardwareTest.qsv.fallbackPipeline"),
        ("NVENC", "hardwareTest.nvenc.pipeline"),
        ("AMF", "hardwareTest.amf.pipeline"),
        ("D3D12VA", "hardwareTest.d3d12va.pipeline"),
        ("Software", "hardwareTest.software.pipeline"),
    )
    add_dashboard_columns(
        panel,
        fields,
        "VIBEFFMPEG-HARDWARE-DASHBOARD-RESULTS",
        "0.78",
        blank_after=2,
    )
    return resolve_plugin_panel(panel, "VIBEFFMPEG-HARDWARE-DASHBOARD")


def build_runtime_dashboard() -> ET.Element:
    """Build Runtime Status as one compact non-focusable text object."""
    panel = node("Panel", "Runtime Status Dashboard", "VIBEFFMPEG-RUNTIME-DASHBOARD")
    for name, value in (
        ("Layout", "Vertical"),
        ("FixedWidth", "1.0"),
        ("FixedHeight", "1.0"),
        ("HorizontalAlignment", "0.0"),
        ("VerticalAlignment", "0.0"),
    ):
        child(panel, name, value)
    fields = (
        ("MIM Version", "status.mimVersion"),
        ("Platform", "status.platform"),
        ("State", "status.last.state"),
        ("Backend", "status.last.backend"),
        ("Encoder", "status.last.encoder"),
        ("GPU Decode", "status.last.hardwareDecode"),
        ("Launcher", "health.launcher"),
        ("Runtime", "health.runtime"),
        ("VAAPI", "capabilities.vaapi.usable"),
        ("QSV", "capabilities.qsv.usable"),
        ("QSV Fallback", "hardwareTest.qsv.fallbackPipeline"),
        ("NVENC", "capabilities.nvenc.usable"),
        ("AMF", "capabilities.amf.usable"),
        ("D3D12VA", "capabilities.d3d12va.usable"),
        ("Software", "capabilities.software.usable"),
    )
    add_dashboard_columns(
        panel,
        fields,
        "VIBEFFMPEG-RUNTIME-DASHBOARD-RESULTS",
        "1.0",
        blank_after=7,
        font_size="10",
    )
    return resolve_plugin_panel(panel, "VIBEFFMPEG-RUNTIME-DASHBOARD")


def clone_config_panel(stock: ET.Element) -> ET.Element:
    """Clone the stock plugin editor as the category screen's right pane."""
    source = next((value for value in stock.iter()
                   if value.tag == q("Menu") and value.attrib.get("Sym") == "NFLX1-1589546"), None)
    if source is None:
        raise RuntimeError("stock Plugin Configure menu NFLX1-1589546 was not found")
    source_panel = next((
        value for value in source
        if value.tag == q("Panel") and value.attrib.get("Name") == "MainAiringListingArea"
    ), None)
    if source_panel is None:
        raise RuntimeError("stock Plugin Configure main panel contract changed")
    result = clone_private_widget(source_panel, 920000, "VIBEFFMPEG-CONFIG")
    result.attrib["Name"] = "FFmpegSettings"
    result.attrib["Sym"] = "VIBEFFMPEG-CONFIG-PANEL"
    for name, value in (
        ("Layout", "Vertical"),
        ("FixedWidth", "1.0"),
        ("FixedHeight", "1.0"),
        ("HorizontalAlignment", "0.0"),
        ("VerticalAlignment", "0.0"),
    ):
        set_widget_property(result, name, value)

    config_list = next((
        value for value in result.iter(q("Panel"))
        if value.attrib.get("Name") == "Config list area"
    ), None)
    if config_list is None:
        raise RuntimeError("stock Plugin Configure list panel contract changed")
    set_widget_property(config_list, "FixedWidth", "1.0")
    set_widget_property(config_list, "FixedHeight", "1.0")
    set_widget_property(config_list, "WrapVNav", "true")

    setting_component = next((
        value for value in result.iter()
        if value.tag == q("TableComponent") and value.attrib.get("Name") == "Setting"
    ), None)
    if setting_component is None:
        raise RuntimeError("stock Plugin Configure setting-row contract changed")
    settings_table = next((
        value for value in result.iter(q("Table"))
        if setting_component in list(value)
    ), None)
    if settings_table is None:
        raise RuntimeError("stock Plugin Configure settings table contract changed")
    set_widget_property(settings_table, "WrapVNav", "true")
    for text_widget in setting_component.iter(q("Text")):
        set_widget_property(text_widget, "ForegroundColor", "0xFFFFFF")
        set_widget_property(text_widget, "ForegroundSelectedColor", "0xFFFFFF")
        set_widget_property(text_widget, "ForegroundAlpha", "255")
        set_widget_property(text_widget, "TextShadow", "false")

    # Match Detailed Setup's compact label/help hierarchy rather than the
    # larger standalone Plugin Configure typography.
    label_action = next((
        value for value in setting_component.iter(q("Action"))
        if value.attrib.get("Name") == "GetPluginConfigLabel(Plugin, Setting)"
    ), None)
    help_action = next((
        value for value in setting_component.iter(q("Action"))
        if value.attrib.get("Name") == "GetPluginConfigHelpText(Plugin, Setting)"
    ), None)
    if label_action is None or help_action is None:
        raise RuntimeError("stock Plugin Configure label/help contract changed")
    for text_widget in label_action.iter(q("Text")):
        set_widget_property(text_widget, "FontSize", "19")
        set_widget_property(text_widget, "WrapText", "false")
    help_parent = next((
        value for value in setting_component.iter()
        if help_action in list(value)
    ), None)
    if help_parent is None:
        raise RuntimeError("stock Plugin Configure help parent contract changed")
    help_parent.remove(help_action)

    setting_row = next((
        value for value in list(setting_component)
        if value.tag == q("Panel")
    ), None)
    if setting_row is None:
        raise RuntimeError("stock Plugin Configure row panel contract changed")
    add_row_separator(setting_row, "VIBEFFMPEG-CONFIG-ROW-SEPARATOR")

    focus_template = next((
        value for value in stock.iter()
        if value.tag == q("Conditional") and value.attrib.get("ID") == "4873"
    ), None)
    if focus_template is None:
        raise RuntimeError("stock focused-row widget 4873 was not found")
    dialog_theme = next((
        value for value in stock.iter(q("Theme"))
        if value.attrib.get("ID") == "2131"
    ), None)
    if dialog_theme is None:
        raise RuntimeError("stock choice dialog theme 2131 was not found")
    dialog_background = next((
        value for value in dialog_theme.iter(q("Action"))
        if value.attrib.get("ID") == "1523"
    ), None)
    if dialog_background is None:
        raise RuntimeError("stock choice dialog background 1523 was not found")
    style_choice_dialogs(result, stock, 0, focus_template, dialog_background)
    type_switch = next((
        value for value in setting_component.iter(q("Conditional"))
        if value.attrib.get("Name") == "GetPluginConfigType(Plugin, Setting)"
    ), None)
    if type_switch is None:
        raise RuntimeError("stock Plugin Configure type selector contract changed")

    # This plugin has no editable CONFIG_TEXT settings: every text-valued row
    # is a virtual status/result labeled Read-Only. Stock Plugin Configure's
    # fallback Text branch opens a keyboard and calls SetPluginConfigValue,
    # which misleadingly makes those rows appear editable. Keep the value Item
    # and its focus artwork, but remove only that edit action.
    text_branch = next((
        branch for branch in type_switch.findall(q("Branch"))
        if branch.attrib.get("Name") == "else"
    ), None)
    text_editor = next((
        value for value in (list(text_branch) if text_branch is not None else [])
        if value.tag == q("Item") and value.attrib.get("Name") == "Text"
    ), None)
    if text_editor is None:
        raise RuntimeError("stock Plugin Configure text editor contract changed")
    set_widget_property(text_editor, "FocusableCondition", "false")
    removed_text_editors = 0
    for value in list(text_editor):
        if (value.tag == q("Action") and
                value.attrib.get("Name") == "PluginValue = GetPluginConfigValue(Plugin, Setting)"):
            text_editor.remove(value)
            removed_text_editors += 1
    if removed_text_editors != 1:
        raise RuntimeError("stock Plugin Configure text edit action contract changed")

    for editor_index, branch in enumerate(type_switch.findall(q("Branch")), 1):
        editor = next((child for child in branch if child.tag == q("Item")), None)
        if editor is None:
            continue
        # Match the physical stock Detailed Setup value cell, including its
        # left edge and centered label.  The cloned plugin editor applies
        # additional row insets, so 0.90 of its value panel is the measured
        # equivalent of stock's 38%-of-row value slot on a 1920-wide UI.
        # Without this explicit width SageTV shrinks the Item to its text and
        # renders only a short underline instead of the stock selection glow.
        set_widget_property(editor, "AnchorX", "1.0")
        set_widget_property(editor, "AnchorY", "0.5")
        set_widget_property(editor, "AnchorPointX", "1.0")
        set_widget_property(editor, "AnchorPointY", "0.5")
        set_widget_property(editor, "FixedWidth", "0.90")
        set_widget_property(editor, "FixedHeight", "0.78")
        set_widget_property(editor, "HorizontalAlignment", "0.5")
        set_widget_property(editor, "VerticalAlignment", "0.5")
        for text_widget in editor.iter(q("Text")):
            set_widget_property(text_widget, "FontSize", "19")
            set_widget_property(text_widget, "TextAlignment", "0.5")
            set_widget_property(text_widget, "ForegroundColor", "0xFFFFFF")
            set_widget_property(text_widget, "ForegroundSelectedColor", "0xFFFFFF")
            set_widget_property(text_widget, "ForegroundAlpha", "255")
        editor.append(clone_focus_widget(
            focus_template,
            980000 + editor_index * 20,
            f"VIBEFFMPEG-CONFIG-EDITOR-{editor_index}-FOCUS",
        ))

    replaced_settings = 0
    replaced_paging = 0
    dynamic_settings = selected_settings_expression()
    for value in result.iter():
        name = value.attrib.get("Name")
        if value.tag == q("Action") and name == "GetPluginConfigSettings(Plugin)":
            # Make every editor self-contained. Imported STV menus do not
            # reliably retain static contexts across a second menu hop on all
            # SageTV7 builds. Resolve the installed Standard plugin here and
            # provide the category's setting list literally before rendering
            # the stock configuration table.
            original_children = list(value)
            for original_child in original_children:
                value.remove(original_child)
            value.attrib["Name"] = (
                'Plugin = GetAvailablePluginForID("SageTVFFmpegPluginLinux")'
            )
            resolve_windows = node(
                "Action",
                'Plugin = If(Plugin != null && IsPluginInstalled(Plugin), Plugin, '
                'GetAvailablePluginForID("SageTVFFmpegPluginWinx64"))',
                "VIBEFFMPEG-CONFIG-RESOLVE-PLUGIN",
            )
            category_settings = node(
                "Action",
                dynamic_settings,
                "VIBEFFMPEG-CONFIG-SETTINGS",
            )
            for original_child in original_children:
                category_settings.append(original_child)
            resolve_windows.append(category_settings)
            value.append(resolve_windows)
            replaced_settings += 1
        elif value.tag == q("Conditional") and name == "Size(GetPluginConfigSettings(Plugin)) > 5":
            value.attrib["Name"] = f"Size({dynamic_settings}) > 5"
            replaced_paging += 1
    if replaced_settings != 1 or replaced_paging != 1:
        raise RuntimeError("stock Plugin Configure menu contract changed")

    # A Panel Ref inside an imported STVi is not guaranteed to resolve on a
    # remote UI. Expand the stock settings scrollbar privately so any category
    # with more than five settings shows the same arrows, thumb, and page
    # behavior as Detailed Setup -> Customize.
    pagination_template = next((
        value for value in stock.iter()
        if value.tag == q("Panel") and value.attrib.get("ID") == "6572"
    ), None)
    if pagination_template is None:
        raise RuntimeError("stock plugin settings pagination panel 6572 was not found")
    replaced_scrollbar = 0
    for parent in result.iter():
        for offset, value in enumerate(list(parent)):
            if value.tag == q("Panel") and value.attrib.get("Ref") == "6572":
                parent.remove(value)
                parent.insert(offset, clone_stock_pagination(
                    stock,
                    pagination_template,
                    1600000,
                    "VIBEFFMPEG-CONFIG-PAGINATION",
                ))
                replaced_scrollbar += 1
    if replaced_scrollbar != 1:
        raise RuntimeError("stock Plugin Configure scrollbar contract changed")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stock-stv", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    stock = ET.parse(args.stock_stv).getroot()
    module = ET.Element(q("Module"), {
        "Name": "OpenSageTV Vibe FFmpeg Plugin",
        "PersistentPrimaryRefs": "true",
    })
    add_setup_hook(module)
    add_setup_item(module)
    add_category_menu(module, stock)
    tree = ET.ElementTree(module)
    ET.indent(tree, space=" ")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Write bytes explicitly so Windows does not translate LF to CRLF. The
    # committed STVi must compare byte-for-byte with Linux release builds.
    args.output.write_bytes(ET.tostring(module, encoding="UTF-8", xml_declaration=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
