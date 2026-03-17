from fastapi import APIRouter
from app.utils.highlight_state import get_highlight

router = APIRouter()


@router.get("/highlight")
async def get_current_highlight():
    h = get_highlight()
    return {
        "active": h.active,
        "x": h.x,
        "y": h.y,
        "width": h.width,
        "height": h.height,
        "label": h.label,
        "type": h.element_type,
        "status": h.status,
        "screen": h.screen,
    }
