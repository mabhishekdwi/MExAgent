"""
Shared mutable state for the visual highlight overlay.
The explorer writes here; the /highlight endpoint reads it.
"""
from typing import Optional
from dataclasses import dataclass, field


@dataclass
class HighlightInfo:
    active: bool = False
    x: int = 0
    y: int = 0
    width: int = 0
    height: int = 0
    label: str = ""
    element_type: str = ""
    status: str = "INFO"   # ACT | PASS | FAIL | INFO
    screen: str = ""


_current: HighlightInfo = HighlightInfo()


def set_highlight(
    bounds_str: Optional[str],
    label: str,
    element_type: str,
    status: str,
    screen: str,
) -> None:
    """Parse '[x1,y1][x2,y2]' bounds string and update state."""
    global _current
    if not bounds_str:
        _current = HighlightInfo(active=False)
        return
    try:
        # Format: [x1,y1][x2,y2]
        parts = bounds_str.replace("][", ",").strip("[]").split(",")
        x1, y1, x2, y2 = int(parts[0]), int(parts[1]), int(parts[2]), int(parts[3])
        _current = HighlightInfo(
            active=True,
            x=x1,
            y=y1,
            width=x2 - x1,
            height=y2 - y1,
            label=label,
            element_type=element_type,
            status=status,
            screen=screen,
        )
    except Exception:
        _current = HighlightInfo(active=False)


def clear_highlight() -> None:
    global _current
    _current = HighlightInfo(active=False)


def get_highlight() -> HighlightInfo:
    return _current
