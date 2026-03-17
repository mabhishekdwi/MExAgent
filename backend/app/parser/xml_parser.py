"""
Converts Appium XML page source into a clean ScreenContext
containing only human-relevant, visible, interactable elements.
"""
import xml.etree.ElementTree as ET
from typing import List, Optional, Dict, Any
from app.schemas.action import UIElement, ScreenContext

# Maps Android class names to friendly element types
_CLASS_MAP = {
    "android.widget.Button":      "button",
    "android.widget.ImageButton": "button",
    "android.widget.EditText":    "input",
    "android.widget.TextView":    "text",
    "android.widget.CheckBox":    "checkbox",
    "android.widget.RadioButton": "radio",
    "android.widget.Switch":      "switch",
    "android.widget.Spinner":     "dropdown",
    "android.widget.SeekBar":     "slider",
    "android.widget.ImageView":   "image",
    "android.widget.ToggleButton": "toggle",
    "androidx.recyclerview.widget.RecyclerView": "list",
    "android.widget.ListView":    "list",
}

# Container types we skip (unless they have text)
_CONTAINER_TYPES = {"layout", "scroll", "view"}


def _get_type(class_name: str) -> str:
    for key, val in _CLASS_MAP.items():
        if key in class_name:
            return val
    return "view"


def _is_relevant(node: ET.Element) -> bool:
    if node.get("displayed", "false") != "true":
        return False
    clickable = node.get("clickable", "false") == "true"
    has_text  = bool(node.get("text", "").strip())
    has_desc  = bool(node.get("content-desc", "").strip())
    is_input  = "EditText" in node.get("class", "")
    return clickable or has_text or has_desc or is_input


def _best_label(node: ET.Element) -> Optional[str]:
    text = node.get("text", "").strip()
    desc = node.get("content-desc", "").strip()
    rid  = node.get("resource-id", "")
    if rid and "/" in rid:
        rid = rid.split("/")[-1]
    return text or desc or rid or None


def _walk(node: ET.Element, elements: List[UIElement], counter: List[int], depth: int = 0):
    if depth > 20:
        return
    if _is_relevant(node):
        etype = _get_type(node.get("class", ""))
        label = _best_label(node)

        # Skip unlabelled containers — just recurse into their children
        if etype in _CONTAINER_TYPES and not label:
            for child in node:
                _walk(child, elements, counter, depth + 1)
            return

        counter[0] += 1
        elements.append(UIElement(
            type=etype,
            label=label,
            text=node.get("text", "").strip() or None,
            resource_id=node.get("resource-id", "") or None,
            content_desc=node.get("content-desc", "").strip() or None,
            bounds=node.get("bounds") or None,
            clickable=node.get("clickable", "false") == "true",
            enabled=node.get("enabled", "true") == "true",
            focused=node.get("focused", "false") == "true",
            index=counter[0],
        ))

    for child in node:
        _walk(child, elements, counter, depth + 1)


def parse_xml_to_screen_context(
    xml_string: str,
    package: Optional[str] = None,
    activity: Optional[str] = None,
) -> ScreenContext:
    """Parse Appium page source XML into a ScreenContext."""
    try:
        root = ET.fromstring(xml_string)
    except ET.ParseError:
        return ScreenContext(screen="Unknown", package=package,
                             activity=activity, elements=[])

    elements: List[UIElement] = []
    counter = [0]
    _walk(root, elements, counter)

    # Derive a human-readable screen name
    screen_name = "Unknown"
    if activity:
        part = activity.split("/")[-1].lstrip(".")
        screen_name = part.replace("Activity", "").replace("Fragment", "") or activity
    elif elements:
        for e in elements:
            if e.text and len(e.text) > 2:
                screen_name = e.text[:40]
                break

    return ScreenContext(
        screen=screen_name,
        package=package,
        activity=activity,
        elements=elements,
    )


def screen_context_to_dict(ctx: ScreenContext) -> Dict[str, Any]:
    """Produce a compact dict suitable for the LLM prompt."""
    return {
        "screen": ctx.screen,
        "activity": ctx.activity or "N/A",
        "elements": [
            {
                "index":     e.index,
                "type":      e.type,
                "label":     e.label or e.text or e.content_desc or f"{e.type}_{e.index}",
                "clickable": e.clickable,
                "enabled":   e.enabled,
                **({"current_value": e.text}
                   if e.text and e.type == "input" else {}),
            }
            for e in ctx.elements
            if e.enabled
        ],
    }
